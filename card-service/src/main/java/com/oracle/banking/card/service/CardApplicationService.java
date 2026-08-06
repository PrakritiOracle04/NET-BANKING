package com.oracle.banking.card.service;

import com.oracle.banking.card.dto.CardDtos.AccountValidationResponse;
import com.oracle.banking.card.dto.CardDtos.CardApplicationApprovalRequest;
import com.oracle.banking.card.dto.CardDtos.CardApplicationRejectionRequest;
import com.oracle.banking.card.dto.CardDtos.CardApplicationRequest;
import com.oracle.banking.card.dto.CardDtos.CardApplicationResponse;
import com.oracle.banking.card.dto.CardDtos.CardIssueRequest;
import com.oracle.banking.card.dto.CardDtos.CardProductResponse;
import com.oracle.banking.card.dto.CardDtos.CardResponse;
import com.oracle.banking.card.entity.CardApplication;
import com.oracle.banking.card.entity.CardApplicationStatus;
import com.oracle.banking.card.entity.CardProduct;
import com.oracle.banking.card.entity.CardType;
import com.oracle.banking.card.event.CardEventPublisher;
import com.oracle.banking.card.exception.CardExceptions.BadRequest;
import com.oracle.banking.card.exception.CardExceptions.Conflict;
import com.oracle.banking.card.exception.CardExceptions.NotFound;
import com.oracle.banking.card.repository.CardApplicationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardApplicationService {
    private static final List<CardApplicationStatus> OPEN_STATUSES = List.of(CardApplicationStatus.PENDING);

    private final CardApplicationRepository repository;
    private final CardService cardService;
    private final CreditCardAccountService creditAccounts;
    private final CardEventPublisher events;
    private final Map<CardProduct, BigDecimal> debitMinimumIncome;
    private final Map<CardProduct, BigDecimal> creditMinimumIncome;
    private final Map<CardProduct, BigDecimal> defaultDailyLimits;
    private final Map<CardProduct, BigDecimal> defaultCreditLimits;
    private final int defaultBillingCycleDay;

    public CardApplicationService(
            CardApplicationRepository repository,
            CardService cardService,
            CreditCardAccountService creditAccounts,
            CardEventPublisher events,
            @Value("${card.products.classic.minimum-annual-income}") BigDecimal classicMinimumIncome,
            @Value("${card.products.gold.minimum-annual-income}") BigDecimal goldMinimumIncome,
            @Value("${card.products.platinum.minimum-annual-income}") BigDecimal platinumMinimumIncome,
            @Value("${card.products.classic.default-daily-limit}") BigDecimal classicDefaultLimit,
            @Value("${card.products.gold.default-daily-limit}") BigDecimal goldDefaultLimit,
            @Value("${card.products.platinum.default-daily-limit}") BigDecimal platinumDefaultLimit,
            @Value("${card.credit-products.classic.minimum-annual-income}") BigDecimal classicCreditMinimumIncome,
            @Value("${card.credit-products.gold.minimum-annual-income}") BigDecimal goldCreditMinimumIncome,
            @Value("${card.credit-products.platinum.minimum-annual-income}") BigDecimal platinumCreditMinimumIncome,
            @Value("${card.credit-products.classic.default-credit-limit}") BigDecimal classicCreditLimit,
            @Value("${card.credit-products.gold.default-credit-limit}") BigDecimal goldCreditLimit,
            @Value("${card.credit-products.platinum.default-credit-limit}") BigDecimal platinumCreditLimit,
            @Value("${card.credit-products.default-billing-cycle-day}") int defaultBillingCycleDay) {
        this.repository = repository;
        this.cardService = cardService;
        this.creditAccounts = creditAccounts;
        this.events = events;
        this.debitMinimumIncome = Map.of(
                CardProduct.CLASSIC, classicMinimumIncome,
                CardProduct.GOLD, goldMinimumIncome,
                CardProduct.PLATINUM, platinumMinimumIncome);
        this.creditMinimumIncome = Map.of(
                CardProduct.CLASSIC, classicCreditMinimumIncome,
                CardProduct.GOLD, goldCreditMinimumIncome,
                CardProduct.PLATINUM, platinumCreditMinimumIncome);
        this.defaultDailyLimits = Map.of(
                CardProduct.CLASSIC, classicDefaultLimit,
                CardProduct.GOLD, goldDefaultLimit,
                CardProduct.PLATINUM, platinumDefaultLimit);
        this.defaultCreditLimits = Map.of(
                CardProduct.CLASSIC, classicCreditLimit,
                CardProduct.GOLD, goldCreditLimit,
                CardProduct.PLATINUM, platinumCreditLimit);
        this.defaultBillingCycleDay = defaultBillingCycleDay;
    }

    public List<CardProductResponse> products() {
        List<CardProductResponse> products = new ArrayList<>();
        for (CardProduct product : List.of(CardProduct.CLASSIC, CardProduct.GOLD, CardProduct.PLATINUM)) {
            products.add(new CardProductResponse(
                    CardType.DEBIT,
                    product,
                    label(CardType.DEBIT, product),
                    debitMinimumIncome.get(product),
                    defaultDailyLimits.get(product),
                    null));
            products.add(new CardProductResponse(
                    CardType.CREDIT,
                    product,
                    label(CardType.CREDIT, product),
                    creditMinimumIncome.get(product),
                    defaultDailyLimits.get(product),
                    defaultCreditLimits.get(product)));
        }
        return products;
    }

    @Transactional
    public CardApplicationResponse apply(String customerUserId, CardApplicationRequest request) {
        CardType cardType = request.cardType() == null ? CardType.DEBIT : request.cardType();
        AccountValidationResponse account = cardService.validateAccount(request.accountId());
        if (!account.active()) throw new BadRequest("Card can be requested only for an active account");
        if (!customerUserId.equals(account.customerUserId())) throw new BadRequest("Customer user ID does not own the account");
        if (cardService.hasNonExpiredCard(request.accountId(), cardType)) {
            throw new Conflict("A non-expired " + cardType.name().toLowerCase() + " card already exists for this account");
        }
        if (repository.existsByCustomerUserIdAndAccountIdAndCardTypeAndStatusIn(customerUserId, request.accountId(), cardType, OPEN_STATUSES)) {
            throw new Conflict("A pending " + cardType.name().toLowerCase() + " card application already exists for this account");
        }
        validateProductEligibility(cardType, request.cardProduct(), request.annualIncome());
        if (request.requestedDailyLimit() != null) cardService.validateLimit(request.requestedDailyLimit());

        CardApplication application = new CardApplication();
        application.setCustomerUserId(customerUserId);
        application.setAccountId(request.accountId());
        application.setCardType(cardType);
        application.setCardProduct(request.cardProduct());
        application.setAnnualIncome(request.annualIncome());
        application.setOccupation(trimToNull(request.occupation()));
        application.setDeliveryAddress(request.deliveryAddress().trim());
        application.setRequestedDailyLimit(request.requestedDailyLimit());
        CardApplication saved = repository.save(application);
        events.publishApplication(
                "card-application-submitted",
                saved,
                "Your " + cardType.name().toLowerCase() + " card application has been submitted for review.",
                "CARD_APPLICATION_RECEIVED");
        return CardApplicationResponse.from(saved);
    }

    public List<CardApplicationResponse> myApplications(String customerUserId) {
        return repository.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId).stream()
                .map(CardApplicationResponse::from)
                .toList();
    }

    public CardApplicationResponse application(String applicationId, String customerUserId, boolean admin) {
        CardApplication application = admin
                ? repository.findById(applicationId).orElseThrow(() -> new NotFound("Card application not found"))
                : repository.findByApplicationIdAndCustomerUserId(applicationId, customerUserId)
                .orElseThrow(() -> new NotFound("Card application not found"));
        return CardApplicationResponse.from(application);
    }

    public List<CardApplicationResponse> search(String customerUserId, CardApplicationStatus status, int page, int size) {
        Specification<CardApplication> spec = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("customerUserId"), customerUserId));
        }
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(CardApplicationResponse::from)
                .toList();
    }

    @Transactional
    public CardApplicationResponse approve(String applicationId, String adminUserId, CardApplicationApprovalRequest request) {
        CardApplication application = repository.findWithLockingByApplicationId(applicationId)
                .orElseThrow(() -> new NotFound("Card application not found"));
        if (application.getStatus() != CardApplicationStatus.PENDING) {
            throw new Conflict("Only a pending card application can be approved");
        }
        BigDecimal approvedDailyLimit = request.approvedDailyLimit() == null
                ? defaultDailyLimits.get(application.getCardProduct())
                : request.approvedDailyLimit();
        cardService.validateLimit(approvedDailyLimit);
        BigDecimal approvedCreditLimit = application.getCardType() == CardType.CREDIT
                ? defaultCreditLimits.get(application.getCardProduct())
                : null;
        CardResponse card = cardService.issue(new CardIssueRequest(
                application.getCustomerUserId(),
                application.getAccountId(),
                application.getCardType(),
                application.getCardProduct(),
                approvedDailyLimit));
        if (application.getCardType() == CardType.CREDIT) {
            creditAccounts.create(
                    card.cardId(),
                    card.customerUserId(),
                    card.accountId(),
                    card.cardProduct(),
                    approvedCreditLimit,
                    defaultBillingCycleDay);
        }
        application.approve(adminUserId, card.cardId(), approvedDailyLimit, approvedCreditLimit, trimToNull(request.notes()));
        CardApplication saved = repository.save(application);
        events.publishApplication(
                "card-application-approved",
                saved,
                "Your " + application.getCardType().name().toLowerCase() + " card application was approved.",
                "CARD_APPLICATION_APPROVED");
        return CardApplicationResponse.from(saved);
    }

    @Transactional
    public CardApplicationResponse reject(String applicationId, String adminUserId, CardApplicationRejectionRequest request) {
        CardApplication application = repository.findWithLockingByApplicationId(applicationId)
                .orElseThrow(() -> new NotFound("Card application not found"));
        if (application.getStatus() != CardApplicationStatus.PENDING) {
            throw new Conflict("Only a pending card application can be rejected");
        }
        application.reject(adminUserId, request.reason().trim());
        CardApplication saved = repository.save(application);
        events.publishApplication(
                "card-application-rejected",
                saved,
                "Your " + application.getCardType().name().toLowerCase() + " card application was rejected.",
                "CARD_APPLICATION_REJECTED");
        return CardApplicationResponse.from(saved);
    }

    private void validateProductEligibility(CardType cardType, CardProduct product, BigDecimal annualIncome) {
        BigDecimal requiredIncome = cardType == CardType.CREDIT
                ? creditMinimumIncome.get(product)
                : debitMinimumIncome.get(product);
        if (requiredIncome != null && annualIncome.compareTo(requiredIncome) < 0) {
            throw new BadRequest("Annual income does not meet the minimum requirement for " + label(cardType, product));
        }
    }

    private String label(CardType cardType, CardProduct product) {
        String suffix = cardType == CardType.CREDIT ? "Credit Card" : "Debit Card";
        return switch (product) {
            case CLASSIC -> "Classic " + suffix;
            case GOLD -> "Gold " + suffix;
            case PLATINUM -> "Platinum " + suffix;
        };
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
