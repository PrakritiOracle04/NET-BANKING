package com.oracle.banking.loan.service;

import com.oracle.banking.loan.dto.LoanDtos.AccountValidationResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationApprovalRequest;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationRejectionRequest;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationRequest;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanDetailsResponse;
import com.oracle.banking.loan.dto.LoanDtos.RegisterLoanRequest;
import com.oracle.banking.loan.entity.LoanApplication;
import com.oracle.banking.loan.entity.LoanApplicationStatus;
import com.oracle.banking.loan.entity.LoanType;
import com.oracle.banking.loan.event.LoanEventPublisher;
import com.oracle.banking.loan.exception.LoanExceptions.BadRequest;
import com.oracle.banking.loan.exception.LoanExceptions.Conflict;
import com.oracle.banking.loan.exception.LoanExceptions.DownstreamFailure;
import com.oracle.banking.loan.exception.LoanExceptions.NotFound;
import com.oracle.banking.loan.repository.LoanApplicationRepository;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class LoanApplicationService {
    private static final List<LoanApplicationStatus> OPEN_STATUSES = List.of(LoanApplicationStatus.PENDING);

    private final LoanApplicationRepository repository;
    private final LoanService loanService;
    private final LoanEventPublisher events;
    private final RestClient accountClient;
    private final String internalApiKey;
    private final BigDecimal minimumMonthlyIncome;
    private final int maxTenureMonths;
    private final Map<LoanType, BigDecimal> maximumAmounts;

    public LoanApplicationService(
            LoanApplicationRepository repository,
            LoanService loanService,
            LoanEventPublisher events,
            RestClient.Builder builder,
            @Value("${services.account-service-url}") String accountServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey,
            @Value("${loan.eligibility.minimum-monthly-income}") BigDecimal minimumMonthlyIncome,
            @Value("${loan.max-tenure-months}") int maxTenureMonths,
            @Value("${loan.eligibility.max-amount.home}") BigDecimal homeMaxAmount,
            @Value("${loan.eligibility.max-amount.vehicle}") BigDecimal vehicleMaxAmount,
            @Value("${loan.eligibility.max-amount.personal}") BigDecimal personalMaxAmount,
            @Value("${loan.eligibility.max-amount.education}") BigDecimal educationMaxAmount,
            @Value("${loan.eligibility.max-amount.business}") BigDecimal businessMaxAmount) {
        this.repository = repository;
        this.loanService = loanService;
        this.events = events;
        this.accountClient = builder.baseUrl(accountServiceUrl).build();
        this.internalApiKey = internalApiKey;
        this.minimumMonthlyIncome = minimumMonthlyIncome;
        this.maxTenureMonths = maxTenureMonths;
        this.maximumAmounts = Map.of(
                LoanType.HOME, homeMaxAmount,
                LoanType.VEHICLE, vehicleMaxAmount,
                LoanType.PERSONAL, personalMaxAmount,
                LoanType.EDUCATION, educationMaxAmount,
                LoanType.BUSINESS, businessMaxAmount);
    }

    @Transactional
    public LoanApplicationResponse apply(String customerUserId, LoanApplicationRequest request) {
        validateEligibility(request.loanType(), request.requestedAmount(), request.tenureMonths(), request.monthlyIncome());
        AccountValidationResponse account = validateAccount(request.linkedAccountId());
        if (!account.active()) throw new BadRequest("Loan can be requested only for an active account");
        if (!customerUserId.equals(account.customerUserId())) throw new BadRequest("Customer user ID does not own the account");
        if (repository.existsByCustomerUserIdAndLinkedAccountIdAndStatusIn(customerUserId, request.linkedAccountId(), OPEN_STATUSES)) {
            throw new Conflict("A pending loan application already exists for this account");
        }

        LoanApplication application = new LoanApplication();
        application.setCustomerUserId(customerUserId);
        application.setLinkedAccountId(request.linkedAccountId());
        application.setLoanType(request.loanType());
        application.setRequestedAmount(money(request.requestedAmount()));
        application.setRequestedTenureMonths(request.tenureMonths());
        application.setMonthlyIncome(money(request.monthlyIncome()));
        application.setEmploymentType(request.employmentType());
        application.setPurpose(request.purpose().trim());
        LoanApplication saved = repository.save(application);
        events.loanApplicationSubmitted(saved);
        return LoanApplicationResponse.from(saved);
    }

    public List<LoanApplicationResponse> myApplications(String customerUserId) {
        return repository.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId).stream()
                .map(LoanApplicationResponse::from)
                .toList();
    }

    public LoanApplicationResponse application(String applicationId, String customerUserId, boolean admin) {
        LoanApplication application = admin
                ? repository.findById(applicationId).orElseThrow(() -> new NotFound("Loan application not found"))
                : repository.findByApplicationIdAndCustomerUserId(applicationId, customerUserId)
                .orElseThrow(() -> new NotFound("Loan application not found"));
        return LoanApplicationResponse.from(application);
    }

    public List<LoanApplicationResponse> search(String customerUserId, LoanApplicationStatus status, LoanType loanType, int page, int size) {
        Specification<LoanApplication> spec = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("customerUserId"), customerUserId));
        }
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (loanType != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("loanType"), loanType));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(LoanApplicationResponse::from)
                .toList();
    }

    @Transactional
    public LoanApplicationResponse approve(String applicationId, String adminUserId, LoanApplicationApprovalRequest request) {
        LoanApplication application = repository.findWithLockingByApplicationId(applicationId)
                .orElseThrow(() -> new NotFound("Loan application not found"));
        if (application.getStatus() != LoanApplicationStatus.PENDING) {
            throw new Conflict("Only a pending loan application can be approved");
        }
        validateEligibility(application.getLoanType(), request.approvedAmount(), request.tenureMonths(), application.getMonthlyIncome());
        LoanDetailsResponse loan = loanService.register(new RegisterLoanRequest(
                application.getCustomerUserId(),
                application.getLinkedAccountId(),
                application.getLoanType(),
                money(request.approvedAmount()),
                request.annualInterestRate(),
                request.tenureMonths(),
                request.startDate()));
        application.approve(
                adminUserId,
                loan.loanId(),
                money(request.approvedAmount()),
                request.annualInterestRate().setScale(4, RoundingMode.HALF_UP),
                request.tenureMonths(),
                trimToNull(request.notes()));
        LoanApplication saved = repository.save(application);
        events.loanApplicationApproved(saved);
        return LoanApplicationResponse.from(saved);
    }

    @Transactional
    public LoanApplicationResponse reject(String applicationId, String adminUserId, LoanApplicationRejectionRequest request) {
        LoanApplication application = repository.findWithLockingByApplicationId(applicationId)
                .orElseThrow(() -> new NotFound("Loan application not found"));
        if (application.getStatus() != LoanApplicationStatus.PENDING) {
            throw new Conflict("Only a pending loan application can be rejected");
        }
        application.reject(adminUserId, request.reason().trim());
        LoanApplication saved = repository.save(application);
        events.loanApplicationRejected(saved);
        return LoanApplicationResponse.from(saved);
    }

    private void validateEligibility(LoanType loanType, BigDecimal amount, int tenureMonths, BigDecimal monthlyIncome) {
        if (tenureMonths < 1 || tenureMonths > maxTenureMonths) {
            throw new BadRequest("Loan tenure must be between 1 and " + maxTenureMonths + " months");
        }
        if (money(monthlyIncome).compareTo(minimumMonthlyIncome) < 0) {
            throw new BadRequest("Monthly income does not meet the minimum loan application requirement");
        }
        BigDecimal maxAmount = maximumAmounts.get(loanType);
        if (maxAmount != null && money(amount).compareTo(maxAmount) > 0) {
            throw new BadRequest("Requested amount exceeds the maximum allowed for " + loanType + " loan");
        }
    }

    private AccountValidationResponse validateAccount(String accountId) {
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

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
