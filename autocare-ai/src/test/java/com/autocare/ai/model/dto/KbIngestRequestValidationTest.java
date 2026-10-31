package com.autocare.ai.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class KbIngestRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    private static KbIngestRequest validRequest() {
        return new KbIngestRequest(
                "KB-AUTO-007",
                "v1.0",
                LocalDate.of(2026, 10, 1),
                DocStatus.ACTIVE,
                "advisor",
                "all",
                "4",
                "advisor_operations",
                "Warranty evidence completeness review SLA is 1 business day.",
                UserRole.ADMIN);
    }

    @Test
    void passesValidationWhenRequiredFieldsPresent() {
        Set<ConstraintViolation<KbIngestRequest>> violations = validator.validate(validRequest());

        assertThat(violations).isEmpty();
    }

    @Test
    void passesValidationWhenVehicleFamilyOmitted() {
        KbIngestRequest request = new KbIngestRequest(
                "KB-AUTO-008",
                "v1.0",
                LocalDate.of(2026, 10, 1),
                DocStatus.ACTIVE,
                "admin",
                null,
                "2",
                "governance",
                "content",
                UserRole.ADMIN);

        Set<ConstraintViolation<KbIngestRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void failsValidationWhenDocCodeBlank() {
        KbIngestRequest request = new KbIngestRequest(
                "",
                "v1.0",
                LocalDate.of(2026, 10, 1),
                DocStatus.ACTIVE,
                "advisor",
                "all",
                "4",
                "advisor_operations",
                "content",
                UserRole.ADMIN);

        Set<ConstraintViolation<KbIngestRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(
                violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("docCode"));
    }

    @Test
    void failsValidationWhenStatusMissing() {
        KbIngestRequest request = new KbIngestRequest(
                "KB-AUTO-007",
                "v1.0",
                LocalDate.of(2026, 10, 1),
                null,
                "advisor",
                "all",
                "4",
                "advisor_operations",
                "content",
                UserRole.ADMIN);

        Set<ConstraintViolation<KbIngestRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(
                violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("status"));
    }

    @Test
    void failsValidationWhenRequesterRoleMissing() {
        KbIngestRequest request = new KbIngestRequest(
                "KB-AUTO-007",
                "v1.0",
                LocalDate.of(2026, 10, 1),
                DocStatus.ACTIVE,
                "advisor",
                "all",
                "4",
                "advisor_operations",
                "content",
                null);

        Set<ConstraintViolation<KbIngestRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(
                violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("requesterRole"));
    }

    @Test
    void failsValidationWhenEffectiveDateMissing() {
        KbIngestRequest request = new KbIngestRequest(
                "KB-AUTO-007",
                "v1.0",
                null,
                DocStatus.ACTIVE,
                "advisor",
                "all",
                "4",
                "advisor_operations",
                "content",
                UserRole.ADMIN);

        Set<ConstraintViolation<KbIngestRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(
                violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("effectiveDate"));
    }
}
