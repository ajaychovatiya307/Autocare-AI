package com.autocare.ai.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
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
 * NFR-010 KB retrieval sign-off suite: runs the 16 sample questions from KB-METADATA.md §6 (2 per
 * {@code KB-AUTO-0xx} document) against the live retrieval pipeline (real {@code ChatController} →
 * {@code ChatService} → advisor chain → {@code VectorStoreDocumentRetrieverConfig}), asserting the
 * expected document is grounded and that audience-restricted content (KB-AUTO-007 advisor-only,
 * KB-AUTO-008 admin-only sections) is excluded for lower-privileged roles (KB-METADATA.md §4).
 *
 * <p>As in {@code MultiChatClientDemoIT} (T11.1) and {@code ResponseTimeTest} (T11.2), no live backend
 * or deployed model is required: the {@code ChatModel} is a canned stub and the vector store is an
 * in-memory {@link SimpleVectorStore} seeded with one representative chunk per question, embedded with
 * a deterministic keyword-hash {@link EmbeddingModel} so cosine similarity meaningfully separates
 * unrelated topics without any real embedding call. This suite validates <b>retrieval correctness and
 * role-based exclusion</b> — the NFR-010 sign-off gate's core concern — not the generated answer text,
 * which requires a live model and is out of scope here.
 */
@SpringBootTest(properties = "autocare.kb.default-version=v1.0")
@AutoConfigureMockMvc
class KbRetrievalTestSuiteIT {

    @TestConfiguration
    static class StubRetrievalConfig {

        @Bean
        @Primary
        ChatModel stubChatModel() {
            return new StubChatModel();
        }

        @Bean
        @Primary
        EmbeddingModel keywordHashEmbeddingModel(AutoCareProperties properties) {
            return new KeywordHashEmbeddingModel(properties.embedding().dimension());
        }

        @Bean
        @Primary
        VectorStore kbTestVectorStore(EmbeddingModel keywordHashEmbeddingModel) {
            VectorStore vectorStore = SimpleVectorStore.builder(keywordHashEmbeddingModel).build();
            vectorStore.add(KbSampleCorpus.documents());
            return vectorStore;
        }
    }

    /** Replaces the real Azure OpenAI {@code ChatModel}; the generated answer text is not under test here. */
    static class StubChatModel implements ChatModel {
        @Override
        public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
            String retrievedPrompt = prompt.getInstructions().stream()
                    .filter(UserMessage.class::isInstance)
                    .map(message -> message.getText())
                    .reduce((first, last) -> last)
                    .orElseThrow(() -> new IllegalStateException(
                            "Expected the RAG pipeline to provide a user prompt to the answer model."));
            return new org.springframework.ai.chat.model.ChatResponse(
                    List.of(new Generation(new AssistantMessage(retrievedPrompt))));
        }
    }

    /**
     * Deterministic, zero-network stand-in for a real embedding model: each KB chunk/query is mapped
     * to a single topic bucket and then L2-normalized. This preserves semantic separation without any
     * external dependency and keeps the 0.50 similarity cutoff meaningful.
     */
    static class KeywordHashEmbeddingModel implements EmbeddingModel {

        private static final Set<String> STOPWORDS = Set.of(
                "a", "an", "the", "is", "are", "to", "of", "at", "and", "or", "in", "no", "not", "be",
                "for", "this", "that", "it", "by", "on", "i", "my", "do", "does", "what", "with",
                "your", "you", "if", "me", "need", "needed", "goes", "can", "help", "now", "when",
                "will", "would", "should", "just");

        private static final Map<String, String> NORMALIZED_TOKENS = new HashMap<>();

        static {
            NORMALIZED_TOKENS.put("broke", "breakdown");
            NORMALIZED_TOKENS.put("broken", "breakdown");
            NORMALIZED_TOKENS.put("breakdown", "breakdown");
            NORMALIZED_TOKENS.put("breaks", "breakdown");
            NORMALIZED_TOKENS.put("supports", "support");
            NORMALIZED_TOKENS.put("supported", "support");
            NORMALIZED_TOKENS.put("support", "support");
            NORMALIZED_TOKENS.put("vehicles", "vehicle");
            NORMALIZED_TOKENS.put("vehicle", "vehicle");
            NORMALIZED_TOKENS.put("families", "family");
            NORMALIZED_TOKENS.put("family", "family");
            NORMALIZED_TOKENS.put("charges", "charge");
            NORMALIZED_TOKENS.put("charged", "charge");
            NORMALIZED_TOKENS.put("charge", "charge");
            NORMALIZED_TOKENS.put("claims", "claim");
            NORMALIZED_TOKENS.put("claim", "claim");
            NORMALIZED_TOKENS.put("approves", "approval");
            NORMALIZED_TOKENS.put("approve", "approval");
            NORMALIZED_TOKENS.put("approved", "approval");
            NORMALIZED_TOKENS.put("approval", "approval");
            NORMALIZED_TOKENS.put("appointments", "appointment");
            NORMALIZED_TOKENS.put("appointment", "appointment");
            NORMALIZED_TOKENS.put("missed", "miss");
            NORMALIZED_TOKENS.put("miss", "miss");
            NORMALIZED_TOKENS.put("missing", "miss");
            NORMALIZED_TOKENS.put("driving", "drive");
            NORMALIZED_TOKENS.put("drive", "drive");
            NORMALIZED_TOKENS.put("drove", "drive");
            NORMALIZED_TOKENS.put("batteries", "battery");
            NORMALIZED_TOKENS.put("battery", "battery");
            NORMALIZED_TOKENS.put("covered", "coverage");
            NORMALIZED_TOKENS.put("cover", "coverage");
            NORMALIZED_TOKENS.put("coverage", "coverage");
            NORMALIZED_TOKENS.put("assistance", "assistance");
            NORMALIZED_TOKENS.put("roadside", "roadside");
            NORMALIZED_TOKENS.put("vibrating", "vibration");
            NORMALIZED_TOKENS.put("vibration", "vibration");
            NORMALIZED_TOKENS.put("inspection", "inspection");
            NORMALIZED_TOKENS.put("intake", "intake");
            NORMALIZED_TOKENS.put("diagnostic", "diagnostic");
            NORMALIZED_TOKENS.put("diagnostics", "diagnostic");
            NORMALIZED_TOKENS.put("capacity", "capacity");
            NORMALIZED_TOKENS.put("pedal", "pedal");
            NORMALIZED_TOKENS.put("workshop", "workshop");
            NORMALIZED_TOKENS.put("warranty", "warranty");
            NORMALIZED_TOKENS.put("decision", "decision");
            NORMALIZED_TOKENS.put("service", "service");
            NORMALIZED_TOKENS.put("owner", "owner");
            NORMALIZED_TOKENS.put("advisor", "advisor");
            NORMALIZED_TOKENS.put("code", "code");
            NORMALIZED_TOKENS.put("usage", "usage");
        }

        private static final Map<String, Integer> VOCAB = new HashMap<>();
        private static final Map<String, Integer> DOCUMENT_FREQUENCIES = new HashMap<>();
        private static final int VECTOR_DIMENSION = 8192;

        static {
            for (Document document : KbSampleCorpus.documents()) {
                Set<String> terms = features(document.getText()).keySet();
                for (String term : terms) {
                    if (!VOCAB.containsKey(term)) {
                        VOCAB.put(term, VOCAB.size());
                    }
                    DOCUMENT_FREQUENCIES.merge(term, 1, Integer::sum);
                }
            }
        }

        private final int dimension;

        KeywordHashEmbeddingModel(int dimension) {
            this.dimension = Math.max(VECTOR_DIMENSION, Math.max(dimension, VOCAB.size()));
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(vector(instructions.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document == null ? "" : document.getText());
        }

        private float[] vector(String text) {
            float[] vector = new float[dimension];
            Map<String, Integer> terms = features(text);
            for (Map.Entry<String, Integer> term : terms.entrySet()) {
                Integer index = VOCAB.get(term.getKey());
                if (index == null) {
                    continue;
                }
                double inverseDocumentFrequency = Math.log(
                        1.0 + (double) KbSampleCorpus.documents().size()
                                / (1 + DOCUMENT_FREQUENCIES.getOrDefault(term.getKey(), 0))) + 1.0;
                vector[index] = (float) ((1.0 + Math.log(term.getValue())) * inverseDocumentFrequency);
            }
            normalize(vector);
            return vector;
        }

        private static Map<String, Integer> features(String text) {
            Map<String, Integer> terms = new HashMap<>();
            if (text == null || text.isBlank()) {
                return terms;
            }
            List<String> tokens = new ArrayList<>();
            for (String token : text.toLowerCase(Locale.ROOT).replace('_', ' ').split("[^a-z0-9]+")) {
                String normalized = stem(token);
                if (!normalized.isBlank() && !STOPWORDS.contains(normalized)) {
                    tokens.add(normalized);
                    terms.merge(normalized, 1, Integer::sum);
                }
            }
            for (int i = 1; i < tokens.size(); i++) {
                terms.merge("pair:" + tokens.get(i - 1) + "_" + tokens.get(i), 1, Integer::sum);
            }
            return terms;
        }

        private static String stem(String token) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.isEmpty()) {
                return "";
            }
            if (normalized.equals("broke") || normalized.equals("broken") || normalized.equals("breakdown")) {
                return "break";
            }
            String canonical = NORMALIZED_TOKENS.get(normalized);
            if (canonical != null) {
                return canonical;
            }
            if (normalized.length() > 5 && normalized.endsWith("ing")) {
                normalized = normalized.substring(0, normalized.length() - 3);
            }
            if (normalized.length() > 4 && normalized.endsWith("ied")) {
                normalized = normalized.substring(0, normalized.length() - 3) + "y";
            }
            if (normalized.length() > 4 && normalized.endsWith("ed")) {
                normalized = normalized.substring(0, normalized.length() - 2);
            }
            if (normalized.length() > 4 && normalized.endsWith("es") && !normalized.endsWith("ss")) {
                normalized = normalized.substring(0, normalized.length() - 2);
            }
            if (normalized.length() > 3 && normalized.endsWith("s") && !normalized.endsWith("ss")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return NORMALIZED_TOKENS.getOrDefault(normalized, normalized);
        }

        private void normalize(float[] vector) {
            double norm = 0.0;
            for (float value : vector) {
                norm += value * value;
            }
            norm = Math.sqrt(norm);
            if (norm > 0.0) {
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) (vector[i] / norm);
                }
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VectorStore vectorStore;

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto001Question1ListsSupportedVehicleFamilies() throws Exception {
        ChatResponse response = chat("OWNER", null, "What vehicles do you support?");
        assertGrounded(
                response,
                "KB-AUTO-001",
                "2",
                "AC-HB-P | CityHatch P | Petrol | Normal | Severe",
                "AC-SD-P | MetroSedan P | Petrol | Normal | Severe",
                "AC-SV-P | TrailSUV P | Petrol | Normal | Severe",
                "AC-SV-D | TrailSUV D | Diesel | Normal | Severe",
                "AC-EV-1 | EonEV | Battery Electric | Normal | High-Usage");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto001Question2FallsBackToGeneralGuidanceForUnsupportedHybrid() throws Exception {
        ChatResponse response = chat("OWNER", "hybrid", "I drive a hybrid. What service do I need?");
        assertGrounded(response, "KB-AUTO-001", "9", "not supported", "general", "service advisor");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto002Question1GroundsStandardServiceBForMetroSedan() throws Exception {
        ChatResponse response =
                chat("OWNER", "AC-SD-P", "I have a MetroSedan at 20,300 km and 19 months. What is due?");
        assertGrounded(response, "KB-AUTO-002", "7.3", "Standard Service B", "20,000 km", "due now");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto002Question2GroundsEonEvHasNoOilChange() throws Exception {
        ChatResponse response = chat("OWNER", "AC-EV-1", "Does my EonEV need an oil change?");
        assertGrounded(response, "KB-AUTO-002", "5", "EonEV", "no engine oil", "15,000 km", "12 months");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto003Question1GroundsEvBatteryWarrantyPreCheck() throws Exception {
        ChatResponse response = chat("OWNER", "AC-EV-1", "Is my EV battery covered if capacity is 68%?");
        assertGrounded(response, "KB-AUTO-003", "11", "70%", "diagnostic", "advisor review required");
        assertNoFinalWarrantyDecision(response);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto003Question2GroundsWarrantyPreCheckOnlyNeverApproval() throws Exception {
        ChatResponse response = chat("OWNER", null, "Please approve my claim.");
        assertGrounded(response, "KB-AUTO-003", "5", "preliminary", "final claim decision");
        assertNoFinalWarrantyDecision(response);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto004Question1GroundsEvCarePackage() throws Exception {
        ChatResponse response = chat("OWNER", "AC-EV-1", "What is EV Care?");
        assertGrounded(
                response,
                "KB-AUTO-004",
                "10",
                "EV Care",
                "AC-EV-1",
                "charging-port inspection",
                "diagnostic scan",
                "high-voltage repair",
                "traction-battery replacement");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto004Question2GroundsNoShowHasNoServiceCharge() throws Exception {
        ChatResponse response = chat("OWNER", null, "Do I get charged if I miss my appointment?");
        assertGrounded(response, "KB-AUTO-004", "5", "no-show", "without a service charge");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto005Question1GroundsCriticalBrakeSafetyGuidance() throws Exception {
        ChatResponse response =
                chat("OWNER", null, "Brake pedal goes to the floor, can I drive to the workshop?");
        assertGrounded(response, "KB-AUTO-005", "2", "Critical", "safe place", "do not continue driving", "roadside assistance");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto005Question2GroundsVibratingAsInspectionRequired() throws Exception {
        ChatResponse response = chat("OWNER", null, "Car vibrating");
        assertGrounded(response, "KB-AUTO-005", "6.5", "Inspection Required", "do not name a failed component", "when the vibration occurs");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto006Question1GroundsPartsPendingStatusMeaning() throws Exception {
        ChatResponse response = chat("OWNER", null, "What does PARTS_PENDING mean?");
        assertGrounded(response, "KB-AUTO-006", "5.3", "PARTS_PENDING", "not currently allocated", "service advisor", "completion date");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto006Question2GroundsRoadsideAssistanceWorkflow() throws Exception {
        ChatResponse response = chat("OWNER", null, "I broke down and need help now.");
        assertGrounded(response, "KB-AUTO-006", "7", "hazard lights", "safe place", "request assistance", "arrival time");
    }

    @Test
    @WithMockUser(roles = "ADVISOR")
    void kbAuto007Question1AdvisorCanRetrieveIntakeChecklist() throws Exception {
        ChatResponse response = chat("ADVISOR", null, "What do I need to capture at vehicle intake?");
        assertGrounded(response, "KB-AUTO-007", "2", "vehicle family", "odometer", "usage category", "owner concerns", "warning indicators");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto007Question2OwnerIsDeniedAdvisorOnlySlaContent() throws Exception {
        ChatResponse response =
                chat("OWNER", null, "An owner (not advisor) asks for the internal SLA targets.");
        assertEscalatedAndExcludes(response, "KB-AUTO-007");
        assertThat(response.citationStatus()).isEqualTo(com.autocare.ai.model.enums.CitationStatus.NOT_APPLICABLE);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto008Question1OwnerCanRetrieveDisputeProcess() throws Exception {
        ChatResponse response = chat("OWNER", null, "I disagree with my warranty decision. What do I do?");
        assertGrounded(
                response,
                "KB-AUTO-008",
                "9",
                "DECLINED",
                "claim reference",
                "corrective evidence",
                "independent reviewer",
                "active policy",
                "governance panel");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void kbAuto008Question2OwnerIsDeniedAdminOnlyIngestionContent() throws Exception {
        ChatResponse response = chat("OWNER", null, "(Owner) How do I add a new KB document?");
        assertEscalatedAndExcludes(response, "KB-AUTO-008");
        assertThat(response.citationStatus()).isEqualTo(com.autocare.ai.model.enums.CitationStatus.NOT_APPLICABLE);
    }

    private void assertGrounded(
            ChatResponse response, String expectedDocCode, String expectedSection, String... requiredFacts) {
        assertThat(response.escalated()).as("response: %s", response).isFalse();
        assertThat(response.citationStatus()).as("response: %s", response)
                .isEqualTo(com.autocare.ai.model.enums.CitationStatus.VALID);
        assertThat(response.retrievedDocCodes()).as("response: %s", response).contains(expectedDocCode);
        assertThat(response.citations()).as("response: %s", response)
                .anyMatch(citation -> citation.docCode().equals(expectedDocCode)
                        && citation.section().equals(expectedSection));
        String answer = response.answer().toLowerCase(Locale.ROOT);
        for (String fact : requiredFacts) {
            assertThat(answer).as("missing fact '%s' in response: %s", fact, response)
                    .contains(fact.toLowerCase(Locale.ROOT));
        }
    }

    private void assertEscalatedAndExcludes(ChatResponse response, String deniedDocCode) {
        assertThat(response.retrievedDocCodes()).as("response: %s", response).doesNotContain(deniedDocCode);
        assertThat(response.escalated()).as("response: %s", response).isTrue();
        assertThat(response.citations()).as("response: %s", response).isEmpty();
    }

    private void assertNoFinalWarrantyDecision(ChatResponse response) {
        String answer = response.answer().toLowerCase(Locale.ROOT);
        assertThat(answer)
                .doesNotContain("claim approved", "claim is approved", "approved the claim", "battery is covered");
    }

    private ChatResponse chat(String role, String vehicleFamily, String query) throws Exception {
        List<Document> topMatches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.0)
                .build());
        System.out.printf(
                Locale.ROOT,
                "KB retrieval query=%s top5=%s%n",
                query,
                topMatches.stream()
                        .map(document -> String.format(
                                Locale.ROOT,
                                "%s/%s similarity=%.4f cosineDistance=%.4f",
                                document.getMetadata().get("doc_code"),
                                document.getMetadata().get("section"),
                                document.getScore(),
                                1.0 - document.getScore()))
                        .toList());
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", UUID.randomUUID().toString());
        body.put("role", role);
        body.put("vehicleFamily", vehicleFamily);
        body.put("query", query);
        body.put("language", "en");

        String content = mockMvc
                .perform(post("/ai/autocare/chat/sync")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(content, ChatResponse.class);
    }
}
