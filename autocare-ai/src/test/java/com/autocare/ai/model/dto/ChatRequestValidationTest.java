package com.autocare.ai.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.autocare.ai.model.enums.UserRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChatRequestValidationTest {

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

    @Test
    void passesValidationWhenRequiredFieldsPresent() {
        ChatRequest request = new ChatRequest(
                "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10",
                UserRole.ADVISOR,
                "AC-SV-D",
                "What is the SLA for warranty evidence review?",
                "en");

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void passesValidationWhenOptionalFieldsOmitted() {
        ChatRequest request = new ChatRequest(
                "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10",
                UserRole.OWNER,
                null,
                "What is the oil change interval?",
                null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void failsValidationWhenSessionIdBlank() {
        ChatRequest request = new ChatRequest("", UserRole.ADVISOR, null, "query", null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(
                violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("sessionId"));
    }

    @Test
    void failsValidationWhenRoleMissing() {
        ChatRequest request = new ChatRequest("session-1", null, null, "query", null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(
                violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("role"));
    }

    @Test
    void failsValidationWhenQueryBlank() {
        ChatRequest request = new ChatRequest("session-1", UserRole.ADVISOR, null, "   ", null);

        Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(
                violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("query"));
    }
}
