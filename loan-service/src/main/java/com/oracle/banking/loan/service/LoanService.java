package com.oracle.banking.loan.service;

import com.oracle.banking.loan.dto.LoanDtos.CalculateEmiRequest;
import com.oracle.banking.loan.dto.LoanDtos.EmiCalculationResponse;
import com.oracle.banking.loan.dto.LoanDtos.EmiPreview;
import com.oracle.banking.loan.dto.LoanDtos.EmiScheduleResponse;
import com.oracle.banking.loan.dto.LoanDtos.InternalCompleteLoanRepaymentRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalCreateLoanRepaymentRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalFailLoanRepaymentRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalLoanMaintenanceRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalLoanMaintenanceResponse;
import com.oracle.banking.loan.dto.LoanDtos.InternalLoanValidationResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanBalanceResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanDetailsResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanRepaymentResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanSummaryResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanTypeOption;
import com.oracle.banking.loan.dto.LoanDtos.RegisterLoanRequest;
import com.oracle.banking.loan.dto.LoanDtos.UpdateLoanStatusRequest;
import com.oracle.banking.loan.entity.EmiSchedule;
import com.oracle.banking.loan.entity.EmiStatus;
import com.oracle.banking.loan.entity.Loan;
import com.oracle.banking.loan.entity.LoanRepayment;
import com.oracle.banking.loan.entity.LoanRepaymentStatus;
import com.oracle.banking.loan.entity.LoanStatus;
import com.oracle.banking.loan.entity.LoanType;
import com.oracle.banking.loan.event.LoanEventPublisher;
import com.oracle.banking.loan.exception.LoanExceptions.BadRequest;
import com.oracle.banking.loan.exception.LoanExceptions.Conflict;
import com.oracle.banking.loan.exception.LoanExceptions.Forbidden;
import com.oracle.banking.loan.exception.LoanExceptions.NotFound;
import com.oracle.banking.loan.repository.EmiScheduleRepository;
import com.oracle.banking.loan.repository.LoanRepaymentRepository;
import com.oracle.banking.loan.repository.LoanRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {
    private static final Logger log = LoggerFactory.getLogger(LoanService.class);
    private static final Set<EmiStatus> UNPAID = Set.of(EmiStatus.PENDING, EmiStatus.PARTIALLY_PAID, EmiStatus.OVERDUE);

    private final LoanRepository loans;
    private final EmiScheduleRepository schedules;
    private final LoanRepaymentRepository repayments;
    private final EmiCalculator calculator;
    private final LoanEventPublisher events;
    private final int reminderDaysBefore;

    public LoanService(
            LoanRepository loans,
            EmiScheduleRepository schedules,
            LoanRepaymentRepository repayments,
            EmiCalculator calculator,
            LoanEventPublisher events,
            @Value("${loan.maintenance.emi-reminder-days-before}") int reminderDaysBefore) {
        this.loans = loans;
        this.schedules = schedules;
        this.repayments = repayments;
        this.calculator = calculator;
        this.events = events;
        this.reminderDaysBefore = reminderDaysBefore;
    }

    @Transactional
    public LoanDetailsResponse register(RegisterLoanRequest request) {
        LocalDate start = request.startDate() == null ? LocalDate.now() : request.startDate();
        BigDecimal principal = money(request.principalAmount());
        BigDecimal emi = calculator.monthlyEmi(principal, request.annualInterestRate(), request.tenureMonths());
        Loan loan = loans.save(new Loan(
                request.customerUserId(),
                request.linkedAccountId(),
                loanNumber(),
                request.loanType(),
                principal,
                request.annualInterestRate().setScale(4, RoundingMode.HALF_UP),
                request.tenureMonths(),
                emi,
                start));
        List<EmiSchedule> rows = calculator.schedule(principal, request.annualInterestRate(), request.tenureMonths(), start, emi)
                .stream()
                .map(row -> new EmiSchedule(
                        loan,
                        row.installmentNumber(),
                        row.dueDate(),
                        row.openingBalance(),
                        row.principal(),
                        row.interest()))
                .toList();
        schedules.saveAll(rows);
        events.loanCreated(loan);
        log.info("Registered loan {} for customer {}", loan.getLoanNumber(), loan.getCustomerUserId());
        return LoanDetailsResponse.from(loan);
    }

    public List<LoanSummaryResponse> loans(String currentUserId, boolean admin, String customerUserId, LoanStatus status) {
        String owner = admin ? customerUserId : currentUserId;
        List<Loan> result;
        if (owner != null && status != null) result = loans.findByCustomerUserIdAndStatusOrderByCreatedAtDesc(owner, status);
        else if (owner != null) result = loans.findByCustomerUserIdOrderByCreatedAtDesc(owner);
        else if (status != null) result = loans.findByStatusOrderByCreatedAtDesc(status);
        else result = loans.findAllByOrderByCreatedAtDesc();
        return result.stream().map(LoanSummaryResponse::from).toList();
    }

    public LoanDetailsResponse details(String id, String currentUserId, boolean admin) {
        return LoanDetailsResponse.from(requireAccessible(id, currentUserId, admin));
    }

    public LoanBalanceResponse balance(String id, String currentUserId, boolean admin) {
        return LoanBalanceResponse.from(requireAccessible(id, currentUserId, admin));
    }

    public List<EmiScheduleResponse> schedule(String id, String currentUserId, boolean admin) {
        Loan loan = requireAccessible(id, currentUserId, admin);
        return schedules.findByLoanLoanIdOrderByInstallmentNumberAsc(loan.getLoanId())
                .stream()
                .map(EmiScheduleResponse::from)
                .toList();
    }

    public List<LoanRepaymentResponse> history(String id, String currentUserId, boolean admin) {
        Loan loan = requireAccessible(id, currentUserId, admin);
        return repayments.findByLoanLoanIdOrderByCreatedAtDesc(loan.getLoanId())
                .stream()
                .map(LoanRepaymentResponse::from)
                .toList();
    }

    @Transactional
    public LoanDetailsResponse updateStatus(String id, UpdateLoanStatusRequest request) {
        Loan loan = require(id);
        LoanStatus target = request.status();
        if (loan.getStatus() == LoanStatus.CLOSED) throw new BadRequest("Closed loans cannot be reopened in Phase 4");
        if (target == LoanStatus.CLOSED) throw new BadRequest("Loan closes automatically when outstanding balance reaches zero");
        if (target == loan.getStatus()) return LoanDetailsResponse.from(loan);
        if (!Set.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.DEFAULTED).contains(target)) {
            throw new BadRequest("Invalid loan status transition");
        }
        loan.updateStatus(target);
        return LoanDetailsResponse.from(loans.save(loan));
    }

    public EmiCalculationResponse calculate(CalculateEmiRequest request) {
        try {
            return calculator.calculate(request);
        } catch (IllegalArgumentException ex) {
            throw new BadRequest(ex.getMessage());
        }
    }

    public List<LoanTypeOption> loanTypes() {
        return List.of(
                new LoanTypeOption(LoanType.HOME.name(), "Home Loan"),
                new LoanTypeOption(LoanType.VEHICLE.name(), "Vehicle Loan"),
                new LoanTypeOption(LoanType.PERSONAL.name(), "Personal Loan"),
                new LoanTypeOption(LoanType.EDUCATION.name(), "Education Loan"),
                new LoanTypeOption(LoanType.BUSINESS.name(), "Business Loan"));
    }

    public InternalLoanValidationResponse validate(String id, String customerUserId, BigDecimal amount) {
        Loan loan = require(id);
        if (customerUserId != null && !loan.ownedBy(customerUserId)) throw new Forbidden("Loan does not belong to customer");
        if (loan.getStatus() == LoanStatus.CLOSED || loan.getStatus() == LoanStatus.DEFAULTED) {
            throw new BadRequest("Loan is not repayable in current status");
        }
        if (amount != null && money(amount).compareTo(loan.getOutstandingBalance()) > 0) {
            throw new BadRequest("Repayment amount exceeds outstanding balance");
        }
        return InternalLoanValidationResponse.from(loan);
    }

    @Transactional
    public LoanRepaymentResponse createPending(InternalCreateLoanRepaymentRequest request) {
        LoanRepayment existing = repayments.findByWorkflowReference(request.workflowReference()).orElse(null);
        if (existing != null) {
            requireSameRepayment(existing, request);
            return LoanRepaymentResponse.from(existing);
        }
        Loan loan = require(request.loanId());
        validate(loan.getLoanId(), request.customerUserId(), request.amount());
        LoanRepayment repayment = repayments.save(new LoanRepayment(
                loan,
                request.customerUserId(),
                request.sourceAccountId(),
                money(request.amount()),
                request.workflowReference()));
        return LoanRepaymentResponse.from(repayment);
    }

    @Transactional
    public LoanRepaymentResponse complete(String id, InternalCompleteLoanRepaymentRequest request) {
        LoanRepayment repayment = repayment(id);
        if (repayment.getStatus() == LoanRepaymentStatus.SUCCESS) return LoanRepaymentResponse.from(repayment);
        if (repayment.getStatus() != LoanRepaymentStatus.PENDING) throw new Conflict("Loan repayment is not pending");

        BigDecimal principalApplied = allocate(repayment.getLoan(), repayment.getAmount());
        repayment.getLoan().reduceOutstanding(principalApplied);
        repayment.getLoan().recalculateStatus(hasOverdue(repayment.getLoan().getLoanId()));
        repayment.complete(request.transactionId(), request.transactionReference(), principalApplied);
        return LoanRepaymentResponse.from(repayments.save(repayment));
    }

    @Transactional
    public LoanRepaymentResponse fail(String id, InternalFailLoanRepaymentRequest request) {
        LoanRepayment repayment = repayment(id);
        if (repayment.getStatus() == LoanRepaymentStatus.FAILED) return LoanRepaymentResponse.from(repayment);
        if (repayment.getStatus() == LoanRepaymentStatus.SUCCESS) throw new Conflict("Successful repayment must be reversed, not failed");
        repayment.fail(request.reason() == null ? "Repayment failed" : request.reason());
        return LoanRepaymentResponse.from(repayments.save(repayment));
    }

    @Transactional
    public LoanRepaymentResponse reverse(String id, InternalFailLoanRepaymentRequest request) {
        return reverseRepayment(repayment(id), request.reason());
    }

    @Transactional
    public LoanRepaymentResponse reverseByWorkflowReference(String reference, InternalFailLoanRepaymentRequest request) {
        LoanRepayment repayment = repayments.findByWorkflowReference(reference)
                .orElseThrow(() -> new NotFound("Loan repayment not found"));
        return reverseRepayment(repayment, request.reason());
    }

    @Transactional
    public InternalLoanMaintenanceResponse sendEmiReminders(InternalLoanMaintenanceRequest request) {
        LocalDate dueDate = request.businessDate().plusDays(reminderDaysBefore);
        int processed = 0;
        int published = 0;
        List<EmiSchedule> rows = schedules.findByStatusInAndDueDate(Set.of(EmiStatus.PENDING, EmiStatus.PARTIALLY_PAID), dueDate);
        for (EmiSchedule row : rows) {
            if (row.getReminderSentAt() != null) continue;
            processed++;
            String reference = "emi:" + row.getEmiScheduleId() + ":reminder";
            if (events.emiReminder(
                    reference,
                    row.getLoan().getCustomerUserId(),
                    row.getLoan().getLoanId(),
                    row.remainingDue(),
                    row.getDueDate())) {
                row.markReminderSent();
                published++;
            }
        }
        schedules.saveAll(rows);
        return new InternalLoanMaintenanceResponse("EMI_REMINDER_SCAN", request.businessDate(), processed, published);
    }

    @Transactional
    public InternalLoanMaintenanceResponse markOverdue(InternalLoanMaintenanceRequest request) {
        int processed = 0;
        int published = 0;
        List<EmiSchedule> rows = schedules.findByStatusInAndDueDateBefore(UNPAID, request.businessDate());
        for (EmiSchedule row : rows) {
            processed++;
            row.markOverdue();
            row.getLoan().recalculateStatus(true);
            if (row.getOverdueNotifiedAt() == null) {
                String reference = "emi:" + row.getEmiScheduleId() + ":overdue";
                if (events.loanOverdue(
                        reference,
                        row.getLoan().getCustomerUserId(),
                        row.getLoan().getLoanId(),
                        row.remainingDue(),
                        row.getDueDate())) {
                    row.markOverdueNotified();
                    published++;
                }
            }
        }
        schedules.saveAll(rows);
        return new InternalLoanMaintenanceResponse("LOAN_OVERDUE_SCAN", request.businessDate(), processed, published);
    }

    private LoanRepaymentResponse reverseRepayment(LoanRepayment repayment, String reason) {
        if (repayment.getStatus() == LoanRepaymentStatus.REVERSED) return LoanRepaymentResponse.from(repayment);
        if (repayment.getStatus() == LoanRepaymentStatus.PENDING) {
            repayment.cancel(reason == null ? "Repayment cancelled" : reason);
            return LoanRepaymentResponse.from(repayments.save(repayment));
        }
        if (repayment.getStatus() != LoanRepaymentStatus.SUCCESS) return LoanRepaymentResponse.from(repayment);

        reverseAllocation(repayment.getLoan(), repayment.getAmount());
        repayment.getLoan().increaseOutstanding(repayment.getPrincipalApplied());
        repayment.getLoan().recalculateStatus(hasOverdue(repayment.getLoan().getLoanId()));
        repayment.reverse(reason == null ? "Repayment reversed" : reason);
        return LoanRepaymentResponse.from(repayments.save(repayment));
    }

    private BigDecimal allocate(Loan loan, BigDecimal amount) {
        BigDecimal remaining = money(amount);
        BigDecimal principalApplied = BigDecimal.ZERO.setScale(2);
        List<EmiSchedule> rows = schedules.findByLoanLoanIdAndStatusInOrderByInstallmentNumberAsc(loan.getLoanId(), UNPAID);
        for (EmiSchedule row : rows) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal applied = remaining.min(row.remainingDue()).setScale(2, RoundingMode.HALF_UP);
            principalApplied = principalApplied.add(row.apply(applied)).setScale(2, RoundingMode.HALF_UP);
            remaining = remaining.subtract(applied).setScale(2, RoundingMode.HALF_UP);
        }
        schedules.saveAll(rows);
        return principalApplied;
    }

    private void reverseAllocation(Loan loan, BigDecimal amount) {
        BigDecimal remaining = money(amount);
        List<EmiSchedule> rows = schedules.findByLoanLoanIdOrderByInstallmentNumberAsc(loan.getLoanId())
                .stream()
                .filter(row -> row.getAmountPaid().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(EmiSchedule::getInstallmentNumber).reversed())
                .toList();
        for (EmiSchedule row : rows) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal reversed = remaining.min(row.getAmountPaid()).setScale(2, RoundingMode.HALF_UP);
            row.reverse(reversed);
            remaining = remaining.subtract(reversed).setScale(2, RoundingMode.HALF_UP);
        }
        schedules.saveAll(rows);
    }

    private boolean hasOverdue(String loanId) {
        return schedules.existsByLoanLoanIdAndStatus(loanId, EmiStatus.OVERDUE);
    }

    private Loan requireAccessible(String id, String currentUserId, boolean admin) {
        Loan loan = require(id);
        if (!admin && !loan.ownedBy(currentUserId)) throw new NotFound("Loan not found");
        return loan;
    }

    private Loan require(String id) {
        return loans.findById(id).orElseThrow(() -> new NotFound("Loan not found"));
    }

    private LoanRepayment repayment(String id) {
        return repayments.findById(id).orElseThrow(() -> new NotFound("Loan repayment not found"));
    }

    private void requireSameRepayment(LoanRepayment existing, InternalCreateLoanRepaymentRequest request) {
        if (!existing.getLoan().getLoanId().equals(request.loanId())
                || !existing.getCustomerUserId().equals(request.customerUserId())
                || !existing.getSourceAccountId().equals(request.sourceAccountId())
                || existing.getAmount().compareTo(money(request.amount())) != 0) {
            throw new Conflict("Workflow reference was already used with a different repayment request");
        }
    }

    private String loanNumber() {
        String value;
        do {
            value = "LN" + LocalDate.now().getYear() + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (loans.existsByLoanNumber(value));
        return value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
