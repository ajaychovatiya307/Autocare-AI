package com.autocare.ai.transform;

import com.autocare.ai.advisor.ConversationAuditAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class RewriteQueryTransformerConfig {

    private static final Pattern CONTEXT_REFERENCE = Pattern.compile(
            "\\b(it|its|they|them|their|this|that|these|those|there|same|former|latter)\\b"
                    + "|\\b(what|how)\\s+about\\b"
                    + "|\\b(and|also)\\s+(it|that|this|they|them|those|these)\\b",
            Pattern.CASE_INSENSITIVE);

    @Bean
    public QueryTransformer rewriteQueryTransformer(
            @Qualifier("queryTransformChatClient") ChatClient queryTransformChatClient,
            @Qualifier("rewritePromptTemplate") PromptTemplate rewritePromptTemplate) {
        RewriteQueryTransformer rewriter = RewriteQueryTransformer.builder()
                .chatClientBuilder(queryTransformChatClient.mutate())
                .promptTemplate(rewritePromptTemplate)
                .targetSearchSystem("AutoCare Knowledge Base")
                .build();

        return query -> rewriteIfContextDependent(query, rewriter);
    }

    private Query rewriteIfContextDependent(Query query, RewriteQueryTransformer rewriter) {
        Assert.notNull(query, "query must not be null");
        if (query.history().isEmpty() || !CONTEXT_REFERENCE.matcher(query.text()).find()) {
            return query;
        }

        String history = query.history().stream()
                .filter(message -> message.getText() != null && !message.getText().isBlank())
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
        if (history.isBlank()) {
            return query;
        }

        Query rewriteInput = new Query("Conversation history:\n"
                + history
                + "\nFollow-up query:\n"
                + query.text());
        Query rewrittenQuery = rewriter.transform(rewriteInput);
        if (rewrittenQuery == rewriteInput) {
            return query;
        }
        String rewrittenText = rewrittenQuery.text();
        return QueryTransformationTrace.recordTransformation(
                query, ConversationAuditAdvisor.CTX_REWRITE_APPLIED, rewrittenText);
    }

    private String formatMessage(Message message) {
        String speaker = message.getMessageType().getValue().toLowerCase(Locale.ROOT);
        return speaker + ": " + message.getText();
    }
}
