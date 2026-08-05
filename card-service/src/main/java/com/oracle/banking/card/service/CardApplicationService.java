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
    private final CardEventPublisher events;
    private final Map<CardProduct, BigDecimal> minimumIncome;
    private final Map<CardProduct, BigDecimal> defaultLimits;

    public CardApplicationService(
            CardApplicationRepository repository,
            CardService cardService,
            CardEventPublisher events,
            @Value("${card.products.classic.minimum-annual-income}") BigDecimal classicMinimumIncome,
            @Value("${card.products.gold.minimum-annual-income}") BigDecimal goldMinimumIncome,
            @Value("${card.products.platinum.minimum-annual-income}") BigDecimal platinumMinimumIncome,
            @Value("${card.products.classic.default-daily-limit}") BigDecimal classicDefaultLimit,
            @Value("${card.products.gold.default-daily-limit}") BigDecimal goldDefaultLimit,
            @Value("${card.products.platinum.default-daily-limit}") BigDecimal platinumDefaultLimit) {
        this.repository = repository;
        this.cardService = cardService;
        this.events = events;
        this.minimumIncome = Map.of(
                CardProduct.CLASSIC, classicMinimumIncome,
                CardProduct.GOLD, goldMinimumIncome,
                CardProduct.PLATINUM, platinumMinimumIncome);
        this.defaultLimits = Map.of(
                CardProduct.CLASSIC, classicDefaultLimit,
                CardProduct.GOLD, goldDefaultLimit,
                CardProduct.PLATINUM, platinumDefaultLimit);
    }

    public List<CardProductResponse> products() {
        return List.of(CardProduct.CLASSIC, CardProduct.GOLD, CardProduct.PLATINUM).stream()
                .map(product -> new CardProductResponse(product, label(product), minimumIncome.get(product), defaultLimits.get(product)))
                .toList();
    }

    @Transactional
    public CardApplicationResponse apply(String customerUserId, CardApplicationRequest request) {
        AccountValidationResponse account = cardService.validateAccount(request.accountId());
        if (!account.active()) throw new BadRequest("Card can be requested only for an active account");
        if (!customerUserId.equals(account.customerUserId())) throw new BadRequest("Customer user ID does not own the account");
        if (cardService.hasNonExpiredCard(request.accountId())) throw new Conflict("A non-expired card already exists for this account");
        if (repository.existsByCustomerUserIdAndAccountIdAndStatusIn(customerUserId, request.accountId(), OPEN_STATUSES)) {
            throw new Conflict("A pending card application already exists for this account");
        }
        validateProductEligibility(request.cardProduct(), request.annualIncome());
        if (request.requestedDailyLimit() != null) cardService.validateLimit(request.requestedDailyLimit());

        CardApplication application = new CardApplication();
        application.setCustomerUserId(customerUserId);
        application.setAccountId(request.accountId());
        application.setCardType(CardType.DEBIT);
        application.setCardProduct(request.cardProduct());
        application.setAnnualIncome(request.annualIncome());
        application.setOccupation(trimToNull(request.occupation()));
        application.setDeliveryAddress(request.deliveryAddress().trim());
        application.setRequestedDailyLimit(request.requestedDailyLimit());
        CardApplication saved = repository.save(application);
        events.publishApplication(
                "card-application-submitted",
                saved,
                "Your debit card application has been submitted for review.",
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
        BigDecimal approvedLimit = request.approvedDailyLimit() == null
                ? defaultLimits.get(application.getCardProduct())
                : request.approvedDailyLimit();
        cardService.validateLimit(approvedLimit);
        CardResponse card = cardService.issue(new CardIssueRequest(
                application.getCustomerUserId(),
                application.getAccountId(),
                application.getCardType(),
                application.getCardProduct(),
                approvedLimit));
        application.approve(adminUserId, card.cardId(), approvedLimit, trimToNull(request.notes()));
        CardApplication saved = repository.save(application);
        events.publishApplication(
                "card-application-approved",
                saved,
                "Your debit card application was approved.",
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
                "Your debit card application was rejected.",
                "CARD_APPLICATION_REJECTED");
        return CardApplicationResponse.from(saved);
    }

    private void validateProductEligibility(CardProduct product, BigDecimal annualIncome) {
        BigDecimal requiredIncome = minimumIncome.get(product);
        if (requiredIncome != null && annualIncome.compareTo(requiredIncome) < 0) {
            throw new BadRequest("Annual income does not meet the minimum requirement for " + label(product));
        }
    }

    private String label(CardProduct product) {
        return switch (product) {
            case CLASSIC -> "Classic Debit Card";
            case GOLD -> "Gold Debit Card";
            case PLATINUM -> "Platinum Debit Card";
        };
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
