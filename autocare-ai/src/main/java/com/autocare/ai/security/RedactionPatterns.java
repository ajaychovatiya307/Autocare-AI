package com.autocare.ai.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Regex-to-token map for PII/vehicle-identifier redaction, per architecture.md §4.6.
 * Patterns are evaluated in insertion order so more specific patterns (e.g. VIN) run before
 * looser ones (e.g. generic alphanumeric registration numbers) to avoid double-matching.
 */
public final class RedactionPatterns {

    public static final String VIN_TOKEN = "[VIN-REDACTED]";
    public static final String REGISTRATION_TOKEN = "[REG-NO-REDACTED]";
    public static final String CUSTOMER_ID_TOKEN = "[CUSTOMER-ID-REDACTED]";
    public static final String PHONE_TOKEN = "[PHONE-REDACTED]";
    public static final String EMAIL_TOKEN = "[EMAIL-REDACTED]";
    public static final String ADDRESS_TOKEN = "[ADDRESS-REDACTED]";

    private static final Map<Pattern, String> PATTERNS = buildPatterns();

    private RedactionPatterns() {
    }

    public static Map<Pattern, String> patterns() {
        return PATTERNS;
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<Pattern, String> entry : PATTERNS.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }

    private static Map<Pattern, String> buildPatterns() {
        Map<Pattern, String> patterns = new LinkedHashMap<>();

        // 17-char VIN: letters/digits excluding I, O, Q (ISO 3779), word-bounded.
        patterns.put(Pattern.compile("\\b[A-HJ-NPR-Z0-9]{17}\\b"), VIN_TOKEN);

        // Email before phone/registration so '@' tokens aren't partially consumed by digit patterns.
        patterns.put(Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"), EMAIL_TOKEN);

        // Customer ID: explicit label followed by an alphanumeric identifier, e.g. "Customer ID: CUST-1234".
        patterns.put(
                Pattern.compile("(?i)\\bcustomer\\s*id\\s*[:#]?\\s*[A-Za-z0-9-]+"), CUSTOMER_ID_TOKEN);

        // Phone: optional country code, 9-15 digits with common separators.
        patterns.put(Pattern.compile("\\+?\\d[\\d().\\s-]{8,14}\\d"), PHONE_TOKEN);

        // Address: explicit label followed by the remainder of the line.
        patterns.put(Pattern.compile("(?i)\\baddress\\s*[:#]?\\s*[^.;\\n]+"), ADDRESS_TOKEN);

        // Vehicle registration/number plate, e.g. "MH12AB1234" or "KA-05-MN-1234":
        // 2-3 letters, 1-2 digits, 1-3 letters, 1-4 digits, with optional separators between groups.
        patterns.put(
                Pattern.compile("\\b[A-Z]{2,3}[- ]?\\d{1,2}[- ]?[A-Z]{1,3}[- ]?\\d{1,4}\\b"), REGISTRATION_TOKEN);

        return patterns;
    }
}
