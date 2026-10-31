package com.autocare.ai.transform;

import com.autocare.ai.advisor.ConversationAuditAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.List;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
public class MultiQueryExpanderConfig {

    private static final int NUMBER_OF_VARIANTS = 3;
    private static final Pattern BROAD_OR_AMBIGUOUS_QUERY = Pattern.compile(
            "\\b(?:car|vehicle)\\s+(?:is\\s+)?(?:vibrating|vibrates|shaking|making\\s+noise|"
                    + "has\\s+(?:a\\s+)?(?:problem|issue)|problem|issue|trouble|maintenance|service)\\b"
                    + "|\\bstrange\\s+(?:car\\s+)?noise\\b"
                    + "|\\b(?:car|vehicle)\\s+(?:warning\\s+light|trouble)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPECIFIC_QUERY_DETAILS = Pattern.compile(
            "\\b(?:schedule|interval|due|warranty|coverage|oil|brake|tire|tyre|"
                    + "error\\s+code|diagnostic\\s+code|part\\s+number|how\\s+to|"
                    + "when\\s+should|what\\s+does\\s+the\\s+manual)\\b",
            Pattern.CASE_INSENSITIVE);

    @Bean
    public QueryExpander multiQueryExpander(
            @Qualifier("queryTransformChatClient") ChatClient queryTransformChatClient,
            @Qualifier("expansionPromptTemplate") PromptTemplate expansionPromptTemplate) {
        MultiQueryExpander expander = MultiQueryExpander.builder()
                .chatClientBuilder(queryTransformChatClient.mutate())
                .promptTemplate(expansionPromptTemplate)
                .includeOriginal(true)
                .numberOfQueries(NUMBER_OF_VARIANTS)
                .build();

        return query -> shouldExpand(query)
                ? expandAndRecord(query, expander)
                : List.of(query);
    }

    private List<Query> expandAndRecord(Query query, MultiQueryExpander expander) {
        List<Query> expanded = expander.expand(query);
        List<Query> tracedQueries = QueryTransformationTrace.recordExpansion(query, expanded);
        return tracedQueries.isEmpty() ? expanded : tracedQueries;
    }

    private boolean shouldExpand(Query query) {
        Assert.notNull(query, "query must not be null");
        String text = query.text();
        long wordCount = text.codePoints().filter(Character::isWhitespace).count() + 1;
        return wordCount <= 8
                && !SPECIFIC_QUERY_DETAILS.matcher(text).find()
                && BROAD_OR_AMBIGUOUS_QUERY.matcher(text).find();
    }
}
