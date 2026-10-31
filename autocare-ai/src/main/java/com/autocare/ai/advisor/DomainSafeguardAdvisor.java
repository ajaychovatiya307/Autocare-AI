package com.autocare.ai.advisor;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Advisor chain stage after LLM generation and citation validation (SRS §4.5, KB-AUTO-003 §5,
 * KB-AUTO-005): a final content guard over the candidate answer, independent of citation grounding.
 * Enforces three rules regardless of what {@code CitationValidationAdvisor} (T9.4) allowed through:
 *
 * <ol>
 *   <li>Safety-critical stop-driving guidance is only trustworthy when the turn actually retrieved
 *       {@code KB-AUTO-005} content; otherwise it is treated as hallucinated and blocked.</li>
 *   <li>Warranty answers may only ever be a preliminary pre-check — final approval/rejection wording
 *       is always blocked (KB-AUTO-003 §5).</li>
 *   <li>No repair cost or completion date may ever be generated, since the KB never supplies either.</li>
 * </ol>
 *
 * <p>On any violation, the candidate answer is replaced with a fixed fallback and {@link
 * ConversationAuditAdvisor#CTX_ESCALATED} is set so the audit record reflects the controlled failure.
 * Ordered after {@code CitationValidationAdvisor} (T9.4) so its {@code after()} runs first.
 */
@Component
public class DomainSafeguardAdvisor implements BaseAdvisor {

    public static final int ORDER = SimpleLoggerAdvisorConfig.ORDER + 200;
    private static final Logger logger = LoggerFactory.getLogger(DomainSafeguardAdvisor.class);

    private static final String SAFETY_DOC_CODE = "KB-AUTO-005";

    private static final Pattern COST_PATTERN = Pattern.compile(
            "(?i)[$€£]\\s?\\d|\\b(cost|price|charge)s?\\s+(of|is|will be|would be)\\b|\\b(usd|eur|gbp|inr)\\b|\\brs\\.?\\s?\\d");

    private static final Pattern COMPLETION_DATE_PATTERN = Pattern.compile(
            "(?i)\\b(ready|completed|completion|done)\\b[^.]{0,20}\\b(by|on)\\b|\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b|"
                    + "\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\b\\s+\\d{1,2}");

    private static final Pattern WARRANTY_DECISION_PATTERN = Pattern.compile(
            "(?i)\\bwarranty\\b[^.]{0,40}\\b(approved|denied|rejected|is covered|is not covered|will be covered|will not be covered)\\b|"
                    + "\\b(approved|denied|rejected)\\b[^.]{0,40}\\bwarranty\\b");

    private static final Pattern SAFETY_GUIDANCE_PATTERN = Pattern.compile(
            "(?i)\\bstop driving\\b|\\bpull over\\b|\\bdo not drive\\b|\\bseek (immediate )?(assistance|help)\\b|"
                    + "\\bimmediately (stop|pull over)\\b");

    private static final Pattern UNSUPPORTED_ACTION_PATTERN =
            Pattern.compile("(?i)\\b(?:I|we)\\s+(?:can|will)\\s+(?:arrange|book|dispatch|call|contact|schedule)\\b");

    private static final String COST_OR_DATE_FALLBACK =
            "AutoCare AI cannot provide repair cost estimates or completion dates. Please contact an authorized "
                    + "service advisor for cost and scheduling information.";

    private static final String WARRANTY_DECISION_FALLBACK =
            "AutoCare AI can only provide a preliminary warranty pre-check, never a final approval or rejection. "
                    + "Please contact an authorized service advisor for a final warranty decision.";

    private static final String UNGROUNDED_SAFETY_FALLBACK =
            "No matching safety guidance was found in the AutoCare Knowledge Base for this situation. If you "
                    + "believe this is safety-critical, stop driving and contact an authorized service advisor immediately.";

    private static final String UNSUPPORTED_ACTION_FALLBACK =
            "AutoCare AI cannot book, call, or dispatch assistance. Please use the roadside-assistance contact "
                    + "provided by AutoCare or contact an authorized service advisor.";

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            logger.debug("advisor={} action=skipped reason=no_chat_response", getName());
            return response;
        }
        String answerText = response.chatResponse().getResult().getOutput().getText();
        if (answerText == null || answerText.isBlank()) {
            logger.debug("advisor={} action=skipped reason=empty_answer", getName());
            return response;
        }

        String fallback = violationFallback(answerText, response.context());
        if (fallback == null) {
            logger.info("advisor={} action=safeguard_passed", getName());
            return response;
        }

        logger.info("advisor={} action=answer_blocked reason=domain_policy_violation", getName());
        return response.mutate()
                .chatResponse(escalatedChatResponse(fallback))
                .context(ConversationAuditAdvisor.CTX_ESCALATED, true)
                .build();
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain advisorChain) {
        return advisorChain.nextStream(request)
                .collectList()
                .flatMapMany(responses -> {
                    if (responses.isEmpty()) {
                        return Flux.empty();
                    }
                    StringBuilder answer = new StringBuilder();
                    for (ChatClientResponse response : responses) {
                        if (response.chatResponse() != null
                                && response.chatResponse().getResult() != null) {
                            String text = response.chatResponse().getResult().getOutput().getText();
                            if (text != null) {
                                answer.append(text);
                            }
                        }
                    }
                    ChatClientResponse completeResponse = ChatClientResponse.builder()
                            .chatResponse(new ChatResponse(List.of(new Generation(
                                    new AssistantMessage(answer.toString()), ChatGenerationMetadata.NULL))))
                            .context(responses.get(responses.size() - 1).context())
                            .build();
                    ChatClientResponse safeguardedResponse = after(completeResponse, null);
                    if (safeguardedResponse == completeResponse) {
                        return Flux.fromIterable(responses);
                    }
                    return Flux.just(safeguardedResponse);
                });
    }

    @Override
    public String getName() {
        return DomainSafeguardAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private String violationFallback(String answerText, Map<String, Object> context) {
        if (COST_PATTERN.matcher(answerText).find() || COMPLETION_DATE_PATTERN.matcher(answerText).find()) {
            return COST_OR_DATE_FALLBACK;
        }
        if (WARRANTY_DECISION_PATTERN.matcher(answerText).find()) {
            return WARRANTY_DECISION_FALLBACK;
        }
        if (UNSUPPORTED_ACTION_PATTERN.matcher(answerText).find()) {
            return UNSUPPORTED_ACTION_FALLBACK;
        }
        if (SAFETY_GUIDANCE_PATTERN.matcher(answerText).find() && !retrievedFromSafetyDoc(context)) {
            return UNGROUNDED_SAFETY_FALLBACK;
        }
        return null;
    }

    private boolean retrievedFromSafetyDoc(Map<String, Object> context) {
        Object docCodes = context.get(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES);
        return docCodes instanceof List<?> codes && codes.contains(SAFETY_DOC_CODE);
    }

    private ChatResponse escalatedChatResponse(String fallbackText) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(fallbackText), ChatGenerationMetadata.NULL)));
    }
}
