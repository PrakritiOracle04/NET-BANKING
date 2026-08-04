package com.oracle.banking.loan.service;

import com.oracle.banking.loan.dto.LoanDtos.CalculateEmiRequest;
import com.oracle.banking.loan.dto.LoanDtos.EmiCalculationResponse;
import com.oracle.banking.loan.dto.LoanDtos.EmiPreview;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmiCalculator {
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 12;
    private static final MathContext MC = new MathContext(24, RoundingMode.HALF_UP);
    private final int maxTenureMonths;

    public EmiCalculator(@Value("${loan.max-tenure-months}") int maxTenureMonths) {
        this.maxTenureMonths = maxTenureMonths;
    }

    public EmiCalculationResponse calculate(CalculateEmiRequest request) {
        BigDecimal principal = money(request.loanAmount());
        BigDecimal annualRate = request.annualInterestRate();
        int months = request.tenureMonths();
        LocalDate start = request.startDate() == null ? LocalDate.now() : request.startDate();
        validate(principal, annualRate, months);

        BigDecimal emi = monthlyEmi(principal, annualRate, months);
        List<EmiPreview> preview = schedule(principal, annualRate, months, start, emi);
        BigDecimal totalRepayment = preview.stream()
                .map(EmiPreview::totalDue)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new EmiCalculationResponse(
                emi,
                totalRepayment.subtract(principal).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                totalRepayment,
                preview);
    }

    public List<EmiPreview> schedule(BigDecimal principal, BigDecimal annualRate, int months, LocalDate start, BigDecimal emi) {
        validate(principal, annualRate, months);
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal balance = money(principal);
        List<EmiPreview> rows = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            BigDecimal opening = balance;
            BigDecimal interest = monthlyRate.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                    : money(opening.multiply(monthlyRate, MC));
            BigDecimal principalDue = i == months
                    ? opening
                    : money(emi.subtract(interest).max(BigDecimal.ZERO));
            if (principalDue.compareTo(opening) > 0) principalDue = opening;
            BigDecimal totalDue = principalDue.add(interest).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            balance = opening.subtract(principalDue).max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            rows.add(new EmiPreview(i, start.plusMonths(i), opening, principalDue, interest, totalDue, balance));
        }
        return rows;
    }

    public BigDecimal monthlyEmi(BigDecimal principal, BigDecimal annualRate, int months) {
        validate(principal, annualRate, months);
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP);
        }
        double monthlyRate = annualRate.doubleValue() / 1200.0;
        double factor = Math.pow(1.0 + monthlyRate, months);
        double emi = principal.doubleValue() * monthlyRate * factor / (factor - 1.0);
        return BigDecimal.valueOf(emi).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void validate(BigDecimal principal, BigDecimal annualRate, int months) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Loan amount must be positive");
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Interest rate cannot be negative");
        if (months <= 0 || months > maxTenureMonths) throw new IllegalArgumentException("Tenure months must be between 1 and " + maxTenureMonths);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
