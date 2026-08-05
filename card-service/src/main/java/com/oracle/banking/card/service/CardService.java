package com.oracle.banking.card.service;

import com.oracle.banking.card.dto.CardDtos.AccountValidationResponse;
import com.oracle.banking.card.dto.CardDtos.CardBlockRequest;
import com.oracle.banking.card.dto.CardDtos.CardIssueRequest;
import com.oracle.banking.card.dto.CardDtos.CardLimitUpdateRequest;
import com.oracle.banking.card.dto.CardDtos.CardResponse;
import com.oracle.banking.card.dto.CardDtos.CardStatusResponse;
import com.oracle.banking.card.entity.BankCard;
import com.oracle.banking.card.entity.CardStatus;
import com.oracle.banking.card.event.CardEventPublisher;
import com.oracle.banking.card.exception.CardExceptions.BadRequest;
import com.oracle.banking.card.exception.CardExceptions.Conflict;
import com.oracle.banking.card.exception.CardExceptions.DownstreamFailure;
import com.oracle.banking.card.exception.CardExceptions.NotFound;
import com.oracle.banking.card.repository.CardRepository;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class CardService {
    private static final Logger log = LoggerFactory.getLogger(CardService.class);
    private static final List<CardStatus> NON_EXPIRED = List.of(CardStatus.INACTIVE, CardStatus.ACTIVE, CardStatus.BLOCKED);

    private final CardRepository repository;
    private final CardCrypto crypto;
    private final CardEventPublisher events;
    private final RestClient accountClient;
    private final String internalApiKey;
    private final BigDecimal maximumDailyLimit;
    private final int validityYears;
    private final SecureRandom random = new SecureRandom();

    public CardService(
            CardRepository repository,
            CardCrypto crypto,
            CardEventPublisher events,
            RestClient.Builder builder,
            @Value("${services.account-service-url}") String accountServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey,
            @Value("${card.maximum-daily-limit}") BigDecimal maximumDailyLimit,
            @Value("${card.validity-years}") int validityYears) {
        this.repository = repository;
        this.crypto = crypto;
        this.events = events;
        this.accountClient = builder.baseUrl(accountServiceUrl).build();
        this.internalApiKey = internalApiKey;
        this.maximumDailyLimit = maximumDailyLimit;
        this.validityYears = validityYears;
    }

    public List<CardResponse> cards(String userId, boolean admin, String customerUserId) {
        if (admin && customerUserId == null) return repository.findAllByOrderByCreatedAtDesc().stream().map(CardResponse::from).toList();
        String owner = admin && customerUserId != null ? customerUserId : userId;
        return repository.findByCustomerUserIdOrderByCreatedAtDesc(owner).stream().map(CardResponse::from).toList();
    }

    public CardResponse card(String id, String userId, boolean admin) {
        return CardResponse.from(findAccessible(id, userId, admin));
    }

    public CardStatusResponse status(String id, String userId, boolean admin) {
        return CardStatusResponse.from(findAccessible(id, userId, admin));
    }

    @Transactional
    public CardResponse issue(CardIssueRequest request) {
        validateLimit(request.dailyTransactionLimit());
        AccountValidationResponse account = validateAccount(request.accountId());
        if (!account.active()) throw new BadRequest("Card can be issued only for an active account");
        if (!request.customerUserId().equals(account.customerUserId())) {
            throw new BadRequest("Customer user ID does not own the account");
        }
        if (repository.existsByAccountIdAndStatusIn(request.accountId(), NON_EXPIRED)) {
            throw new Conflict("A non-expired card already exists for this account");
        }
        String cardNumber = generateUniqueCardNumber();
        YearMonth expiry = YearMonth.now().plusYears(validityYears);
        BankCard card = new BankCard();
        card.setCustomerUserId(request.customerUserId());
        card.setAccountId(request.accountId());
        card.setCardNumberEncrypted(crypto.encrypt(cardNumber));
        card.setCardNumberHash(crypto.fingerprint(cardNumber));
        card.setLastFourDigits(cardNumber.substring(cardNumber.length() - 4));
        card.setCardType(request.cardType());
        card.setCardProduct(request.cardProduct());
        card.setDailyTransactionLimit(request.dailyTransactionLimit());
        card.setExpiryMonth(expiry.getMonthValue());
        card.setExpiryYear(expiry.getYear());
        BankCard saved = repository.save(card);
        events.publish("card-issued", saved, "A new card ending " + saved.getLastFourDigits() + " was issued.");
        log.info("Issued card {} for account {}", saved.getCardId(), saved.getAccountId());
        return CardResponse.from(saved);
    }

    @Transactional
    public CardResponse activate(String id, String userId) {
        BankCard card = owned(id, userId);
        expireIfNecessary(card);
        if (card.getStatus() != CardStatus.INACTIVE) throw new Conflict("Only an inactive card can be activated");
        card.activate();
        BankCard saved = repository.save(card);
        events.publish("card-activated", saved, "Your card ending " + saved.getLastFourDigits() + " was activated.");
        log.info("Activated card {}", id);
        return CardResponse.from(saved);
    }

    @Transactional
    public CardResponse block(String id, String userId, boolean admin, CardBlockRequest request) {
        BankCard card = findAccessible(id, userId, admin);
        expireIfNecessary(card);
        if (card.getStatus() != CardStatus.ACTIVE && card.getStatus() != CardStatus.INACTIVE) {
            throw new Conflict("Card cannot be blocked from status " + card.getStatus());
        }
        String reason = request.reason() == null || request.reason().isBlank() ? "Cardholder request" : request.reason().trim();
        card.block(reason);
        BankCard saved = repository.save(card);
        events.publish("card-blocked", saved, "Your card ending " + saved.getLastFourDigits() + " was blocked.");
        log.info("Blocked card {}", id);
        return CardResponse.from(saved);
    }

    @Transactional
    public CardResponse unblock(String id, String userId, boolean admin) {
        BankCard card = findAccessible(id, userId, admin);
        expireIfNecessary(card);
        if (card.getStatus() != CardStatus.BLOCKED) throw new Conflict("Only a blocked card can be unblocked");
        card.activate();
        BankCard saved = repository.save(card);
        events.publish("card-unblocked", saved, "Your card ending " + saved.getLastFourDigits() + " was unblocked.");
        log.info("Unblocked card {}", id);
        return CardResponse.from(saved);
    }

    @Transactional
    public CardResponse updateLimit(String id, String userId, CardLimitUpdateRequest request) {
        validateLimit(request.dailyTransactionLimit());
        BankCard card = owned(id, userId);
        expireIfNecessary(card);
        if (card.getStatus() == CardStatus.EXPIRED) throw new Conflict("Expired card limit cannot be updated");
        card.setDailyTransactionLimit(request.dailyTransactionLimit());
        BankCard saved = repository.save(card);
        events.publish("card-limit-updated", saved, "Your card daily transaction limit was updated.");
        log.info("Updated daily limit for card {}", id);
        return CardResponse.from(saved);
    }

    void validateLimit(BigDecimal limit) {
        if (limit == null || limit.signum() <= 0 || limit.compareTo(maximumDailyLimit) > 0) {
            throw new BadRequest("Daily transaction limit must be positive and no greater than " + maximumDailyLimit);
        }
    }

    AccountValidationResponse validateAccount(String accountId) {
        try {
            AccountValidationResponse response = accountClient.get()
                    .uri("/internal/accounts/{id}/validate", accountId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(AccountValidationResponse.class);
            if (response == null) throw new DownstreamFailure("Account validation returned no data");
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new BadRequest("Account could not be validated");
            throw new DownstreamFailure("Account Service is unavailable");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Account Service is unavailable");
        }
    }

    boolean hasNonExpiredCard(String accountId) {
        return repository.existsByAccountIdAndStatusIn(accountId, NON_EXPIRED);
    }

    private BankCard findAccessible(String id, String userId, boolean admin) {
        BankCard card = admin ? repository.findById(id).orElseThrow(() -> new NotFound("Card not found")) : owned(id, userId);
        expireIfNecessary(card);
        return card;
    }

    private BankCard owned(String id, String userId) {
        return repository.findByCardIdAndCustomerUserId(id, userId).orElseThrow(() -> new NotFound("Card not found"));
    }

    private void expireIfNecessary(BankCard card) {
        YearMonth expiry = YearMonth.of(card.getExpiryYear(), card.getExpiryMonth());
        if (expiry.isBefore(YearMonth.now()) && card.getStatus() != CardStatus.EXPIRED) {
            card.expire();
            repository.save(card);
        }
    }

    private String generateUniqueCardNumber() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder number = new StringBuilder("453212");
            while (number.length() < 15) number.append(random.nextInt(10));
            number.append(luhnCheckDigit(number.toString()));
            String value = number.toString();
            if (!repository.existsByCardNumberHash(crypto.fingerprint(value))) return value;
        }
        throw new IllegalStateException("Unable to generate unique card number");
    }

    private int luhnCheckDigit(String partial) {
        int sum = 0;
        boolean doubleDigit = true;
        for (int index = partial.length() - 1; index >= 0; index--) {
            int digit = partial.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return (10 - (sum % 10)) % 10;
    }
}
