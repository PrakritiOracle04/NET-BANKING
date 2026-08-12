package com.oracle.banking.workflow.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.banking.workflow.dto.WorkflowDtos.OpenAccountRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OpenAccountRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void initialDepositIsMandatoryAndPositive() {
        OpenAccountRequest missing = new OpenAccountRequest("SAVINGS", "ORCL0000001", null);
        OpenAccountRequest zero = new OpenAccountRequest("SAVINGS", "ORCL0000001", BigDecimal.ZERO);
        OpenAccountRequest valid = new OpenAccountRequest(
                "SAVINGS", "ORCL0000001", new BigDecimal("25000.00"));

        assertThat(validator.validate(missing))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("initialDeposit"));
        assertThat(validator.validate(zero))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("initialDeposit"));
        assertThat(validator.validate(valid)).isEmpty();
    }
}
