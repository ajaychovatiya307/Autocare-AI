package com.autocare.ai.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoMatchingContextExceptionTest {

    @Test
    void defaultConstructorCarriesTheFixedEscalationMessage() {
        NoMatchingContextException exception = new NoMatchingContextException();

        assertThat(exception.getMessage()).isEqualTo(NoMatchingContextException.ESCALATION_MESSAGE);
        assertThat(exception.getMessage())
                .contains("No matching vehicle service or warranty information")
                .contains("authorized service advisor");
    }

    @Test
    void customMessageOverridesTheDefault() {
        NoMatchingContextException exception = new NoMatchingContextException("custom detail");

        assertThat(exception.getMessage()).isEqualTo("custom detail");
    }

    @Test
    void isAnUncheckedException() {
        assertThat(new NoMatchingContextException()).isInstanceOf(RuntimeException.class);
    }
}
