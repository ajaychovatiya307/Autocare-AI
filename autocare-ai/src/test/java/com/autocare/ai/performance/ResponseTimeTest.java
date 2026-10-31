package com.autocare.ai.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autocare.ai.config.AutoCareProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NFR-006 response-time objective for {@code POST /ai/autocare/chat/sync}.
 *
 * <p><b>Agreed objective</b> (documented here per testing.md §8, since there is no live Azure OpenAI
 * deployment wired up for this environment): with the LLM call itself replaced by an in-memory stub
 * (no network round-trip), the AutoCare pipeline's own overhead — redaction, audit persistence,
 * retrieval, citation validation and domain safeguards — must stay within <b>p95 ≤ 500 ms</b> and
 * <b>p99 ≤ 1000 ms</b> per turn. This isolates the pipeline's own latency contribution from live model
 * latency, which must be measured separately once a real deployment is connected.
 *
 * <p>Three representative scenarios are exercised per testing.md §5/§8: a specific query requiring no
 * query transform, a broad query triggering {@code MultiQueryExpander}, and an empty-context
 * escalation (unsupported vehicle family). The real {@code ChatModel} is replaced with a recording stub
 * and the real vector store/embedding model with deterministic in-memory equivalents, exactly as in
 * {@code MultiChatClientDemoIT} (T11.1), so this test requires no live backend or deployed model.
 */
@SpringBootTest(properties = {
    "autocare.kb.default-version=v1.0",
    "autocare.rate-limit.chat-requests-per-minute=100"
})
@AutoConfigureMockMvc
class ResponseTimeTest {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASURED_ITERATIONS = 20;
    private static final long P95_OBJECTIVE_MILLIS = 500;
    private static final long P99_OBJECTIVE_MILLIS = 1000;

    @TestConfiguration
    static class StubModelConfig {

        @Bean
        @Primary
        ChatModel stubChatModel() {
            return new StubChatModel();
        }

        @Bean
        @Primary
        EmbeddingModel constantEmbeddingModel(AutoCareProperties properties) {
            return new ConstantEmbeddingModel(properties.embedding().dimension());
        }

        @Bean
        @Primary
        VectorStore inMemoryVectorStore(EmbeddingModel constantEmbeddingModel) {
            VectorStore vectorStore = SimpleVectorStore.builder(constantEmbeddingModel).build();
            vectorStore.add(List.of(Document.builder()
                    .text("Standard Service B covers oil, filters and a 20-point inspection at 20,000 km.")
                    .metadata(Map.of(
                            "doc_code", "KB-AUTO-002",
                            "version", "v1.0",
                            "status", "ACTIVE",
                            "audience_owner", "true",
                            "effective_date", java.time.LocalDate.now().toString(),
                            "vehicle_family", "AC-SV-D"))
                    .build()));
            return vectorStore;
        }
    }

    /** Replaces the real Azure OpenAI {@code ChatModel} so this test needs no live credentials. */
    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("Standard Service B is due at 20,000 km, per KB-AUTO-002."))));
        }
    }

    /** Deterministic, zero-network embedding stand-in; every text embeds to the same constant vector. */
    static class ConstantEmbeddingModel implements EmbeddingModel {
        private final int dimension;

        ConstantEmbeddingModel(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(vector(), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector();
        }

        private float[] vector() {
            float[] vector = new float[dimension];
            java.util.Arrays.fill(vector, 1.0f);
            return vector;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "OWNER")
    void groundedQueryWithNoTransformsMeetsResponseTimeObjective() throws Exception {
        assertMeetsObjective(payload("AC-SV-D", "What does Standard Service B include?"), 200);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void broadQueryTriggeringMultiQueryExpanderMeetsResponseTimeObjective() throws Exception {
        assertMeetsObjective(payload("AC-SV-D", "car vibrating"), 200);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void emptyContextEscalationMeetsResponseTimeObjective() throws Exception {
        assertMeetsObjective(payload("AC-EV-1", "What does Standard Service B include?"), 404);
    }

    private void assertMeetsObjective(Map<String, Object> body, int expectedStatus) throws Exception {
        String json = objectMapper.writeValueAsString(body);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            performSync(json, expectedStatus);
        }

        List<Long> elapsedMillis = new ArrayList<>(MEASURED_ITERATIONS);
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            performSync(json, expectedStatus);
            elapsedMillis.add((System.nanoTime() - start) / 1_000_000);
        }

        long p95 = percentile(elapsedMillis, 95);
        long p99 = percentile(elapsedMillis, 99);

        assertThat(p95)
                .as("p95 latency (ms) over %d measured turns: %s", MEASURED_ITERATIONS, elapsedMillis)
                .isLessThanOrEqualTo(P95_OBJECTIVE_MILLIS);
        assertThat(p99)
                .as("p99 latency (ms) over %d measured turns: %s", MEASURED_ITERATIONS, elapsedMillis)
                .isLessThanOrEqualTo(P99_OBJECTIVE_MILLIS);
    }

    private void performSync(String json, int expectedStatus) throws Exception {
        mockMvc.perform(post("/ai/autocare/chat/sync").contentType("application/json").content(json))
                .andExpect(status().is(expectedStatus));
    }

    private long percentile(List<Long> samples, int percentile) {
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(index, 0));
    }

    private Map<String, Object> payload(String vehicleFamily, String query) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", java.util.UUID.randomUUID().toString());
        body.put("role", "OWNER");
        body.put("vehicleFamily", vehicleFamily);
        body.put("query", query);
        body.put("language", "en");
        return body;
    }
}
