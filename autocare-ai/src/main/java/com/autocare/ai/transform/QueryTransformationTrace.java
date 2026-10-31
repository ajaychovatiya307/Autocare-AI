package com.autocare.ai.transform;

import com.autocare.ai.advisor.ConversationAuditAdvisor;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.ai.rag.Query;

final class QueryTransformationTrace {

    private static final String QUERY_TRANSFORM_CHAT_CLIENT = "QueryTransformChatClient";

    private QueryTransformationTrace() {
    }

    static Query recordLanguage(Query query, String language) {
        Map<String, Object> context = contextWith(query, Map.of(
                ConversationAuditAdvisor.CTX_LANGUAGE_DETECTED, language));
        return context == query.context() ? query : query.mutate().context(context).build();
    }

    static Query recordTransformation(Query query, String appliedKey, String transformedText) {
        Map<String, Object> context = contextWith(query, Map.of(
                appliedKey, true,
                ConversationAuditAdvisor.CTX_QUERY_CHAT_CLIENT, QUERY_TRANSFORM_CHAT_CLIENT,
                ConversationAuditAdvisor.CTX_TRANSFORMED_QUERY, transformedText));
        return query.mutate().text(transformedText).context(context).build();
    }

    static List<Query> recordExpansion(Query query, List<Query> expandedQueries) {
        Map<String, Object> context = contextWith(query, Map.of(
                ConversationAuditAdvisor.CTX_EXPANSION_APPLIED, true,
                ConversationAuditAdvisor.CTX_QUERY_CHAT_CLIENT, QUERY_TRANSFORM_CHAT_CLIENT,
                ConversationAuditAdvisor.CTX_TRANSFORMED_QUERY, query.text(),
                ConversationAuditAdvisor.CTX_EXPANDED_QUERIES,
                expandedQueries.stream().map(Query::text).distinct().toList()));
        return expandedQueries.stream()
                .map(expandedQuery -> expandedQuery.mutate().context(context).build())
                .toList();
    }

    private static Map<String, Object> contextWith(Query query, Map<String, Object> updates) {
        Map<String, Object> context = query.context();
        try {
            context.putAll(updates);
            return context;
        } catch (UnsupportedOperationException exception) {
            Map<String, Object> mutableContext = new HashMap<>(context);
            mutableContext.putAll(updates);
            return mutableContext;
        }
    }
}
