package com.autocare.ai.exception;

/**
 * Raised when no KB chunk passes the retrieval filters (status/version/audience/vehicle_family/
 * similarity ≥ 0.50), per architecture.md §4.2/§5: the system must escalate rather than generate
 * an unsupported answer ({@code allowEmptyContext=false}, FR-014).
 */
public class NoMatchingContextException extends RuntimeException {

    public static final String ESCALATION_MESSAGE =
            "No matching vehicle service or warranty information was found in the AutoCare "
                    + "Knowledge Base. Please contact an authorized service advisor.";

    public NoMatchingContextException() {
        super(ESCALATION_MESSAGE);
    }

    public NoMatchingContextException(String message) {
        super(message);
    }
}
