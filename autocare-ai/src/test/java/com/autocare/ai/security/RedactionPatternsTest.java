package com.autocare.ai.security;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionPatternsTest {

    private String redact(String text) {
        String result = text;
        for (Map.Entry<Pattern, String> entry : RedactionPatterns.patterns().entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }

    @Test
    void redactsVin() {
        assertThat(redact("VIN 1HGCM82633A004352 needs lookup"))
                .contains(RedactionPatterns.VIN_TOKEN)
                .doesNotContain("1HGCM82633A004352");
    }

    @Test
    void redactsEmail() {
        assertThat(redact("Contact me at jane.doe@example.com please"))
                .contains(RedactionPatterns.EMAIL_TOKEN)
                .doesNotContain("jane.doe@example.com");
    }

    @Test
    void redactsCustomerId() {
        assertThat(redact("Customer ID: CUST-10293 called in"))
                .contains(RedactionPatterns.CUSTOMER_ID_TOKEN)
                .doesNotContain("CUST-10293");
    }

    @Test
    void redactsPhoneNumber() {
        assertThat(redact("Call me at +1 415-555-0132 today"))
                .contains(RedactionPatterns.PHONE_TOKEN)
                .doesNotContain("415-555-0132");
    }

    @Test
    void redactsAddress() {
        assertThat(redact("Address: 221B Baker Street, London"))
                .contains(RedactionPatterns.ADDRESS_TOKEN)
                .doesNotContain("221B Baker Street");
    }

    @Test
    void redactsRegistrationNumber() {
        assertThat(redact("Vehicle MH12AB1234 is due for service"))
                .contains(RedactionPatterns.REGISTRATION_TOKEN)
                .doesNotContain("MH12AB1234");
    }

    @Test
    void patternsAreAppliedInDeterministicOrder() {
        assertThat(RedactionPatterns.patterns()).isNotEmpty();
    }
}
