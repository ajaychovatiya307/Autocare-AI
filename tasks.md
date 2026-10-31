# AutoCare AI — Task Checklist (File-Level, Compile-Ordered)

Rewritten from `plan.md` / `architecture.md` / `KB/KB-METADATA.md` / `AutoCareAI_SRS.docx`. Each task produces **one file**. Order is corrected so every task only depends on files already created by an earlier task (enums before anything that references them; config before services that inject it; entities before repositories; etc.) — this differs slightly from a literal "project setup → config → dtos → enums…" reading, because `SecurityConfig`, DTOs and entities all reference the role/status enums and would not compile if enums came later.

Each task lists: **File**, **SRS Ref**, **Unit Test File**, **Done**.

## Phase 0 — Project Setup

- [x] **T0.1** `pom.xml` — dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-ai-bom` + Azure OpenAI starter, Spring AI MariaDB vector store starter, `mariadb-java-client`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test`, `mockito-core`, `junit-jupiter` — SRS §2.3 — Test: N/A, verify with `mvn -q -DskipTests package` — [x] Done
- [x] **T0.2** `src/main/resources/application.yml` — base scaffold: server port, datasource placeholder, logging levels — SRS §2.3 — Test: N/A (loaded by T0.6 binding test) — [x] Done
- [x] **T0.3** `src/main/java/com/autocare/ai/AutoCareAiApplication.java` — Spring Boot entry point — SRS §2.3 — Unit Test: `src/test/java/com/autocare/ai/AutoCareAiApplicationTests.java` (context-load smoke test) — [x] Done
- [x] **T0.4** `src/main/java/com/autocare/ai/config/AutoCareProperties.java` — **(missing SRS item: AutoCare properties binding)** `@ConfigurationProperties(prefix="autocare")`: `rag.similarityThreshold=0.50`, `rag.allowEmptyContext=false`, `embedding.dimension`, `chat.queryTransformDeployment`, `chat.answerDeployment`, `kb.defaultVersion` — SRS §4.1, §4.7 — Unit Test: `src/test/java/com/autocare/ai/config/AutoCarePropertiesTest.java` — [x] Done
- [x] **T0.5** `src/main/resources/application.yml` (update) — add `autocare.*` values bound by T0.4, including **embedding dimension** matching the vector column — SRS §4.1, NFR-008 — Unit Test: `src/test/java/com/autocare/ai/config/AutoCarePropertiesBindingTest.java` — [x] Done

## Phase 1 — Enums

- [x] **T1.1** `src/main/java/com/autocare/ai/model/enums/UserRole.java` — `OWNER`, `ADVISOR`, `ADMIN` — SRS §2.2 — Unit Test: `src/test/java/com/autocare/ai/model/enums/UserRoleTest.java` — [x] Done
- [x] **T1.2** `src/main/java/com/autocare/ai/model/enums/DocStatus.java` — `ACTIVE`, `SUPERSEDED`, `RETIRED` — NFR-008 — Unit Test: `src/test/java/com/autocare/ai/model/enums/DocStatusTest.java` — [x] Done
- [x] **T1.3** `src/main/java/com/autocare/ai/model/enums/CitationStatus.java` — `VALID`, `BLOCKED`, `NOT_APPLICABLE` — NFR-001 — Unit Test: `src/test/java/com/autocare/ai/model/enums/CitationStatusTest.java` — [x] Done
- [x] **T1.4** `src/main/java/com/autocare/ai/model/enums/TransformationStage.java` — `TRANSLATE`, `REWRITE`, `EXPAND`, `ANSWER_GENERATION` — SRS §4.4, §4.7 — Unit Test: `src/test/java/com/autocare/ai/model/enums/TransformationStageTest.java` — [x] Done
- [x] **T1.5** `src/main/java/com/autocare/ai/model/enums/ChatClientType.java` — `QUERY_TRANSFORM`, `ANSWER` — SRS §4.7 — Unit Test: `src/test/java/com/autocare/ai/model/enums/ChatClientTypeTest.java` — [x] Done

## Phase 2 — Config (both ChatClients + vector store + security)

- [x] **T2.1** `src/main/java/com/autocare/ai/config/ChatClientConfig.java` — registers `QueryTransformChatClient` and `AnswerChatClient` beans from `AutoCareProperties` deployment names (depends on T0.4) — SRS §4.7 (mandatory) — Unit Test: `src/test/java/com/autocare/ai/config/ChatClientConfigTest.java` — [x] Done
- [x] **T2.2** `src/main/java/com/autocare/ai/config/VectorStoreConfig.java` — MariaDB vector store bean + embedding model bean; **embedding dimension** read from `AutoCareProperties.embedding.dimension` and asserted equal to the vector column size (missing SRS item) — SRS §2.3, NFR-008 — Unit Test: `src/test/java/com/autocare/ai/config/VectorStoreConfigTest.java` — [x] Done
- [x] **T2.3** `src/main/java/com/autocare/ai/config/SecurityConfig.java` — role-based auth filter chain using `UserRole` (depends on T1.1) — NFR-002 — Unit Test: `src/test/java/com/autocare/ai/config/SecurityConfigTest.java` — [x] Done

## Phase 3 — DTOs

- [x] **T3.1** `src/main/java/com/autocare/ai/model/dto/SourceCitation.java` — `{docCode, version, section}` — NFR-001 — Unit Test: `src/test/java/com/autocare/ai/model/dto/SourceCitationTest.java` — [x] Done
- [x] **T3.2** `src/main/java/com/autocare/ai/model/dto/ChatRequest.java` — `sessionId`, `role` (uses `UserRole`, T1.1), `vehicleFamily`, `query`, `language` — architecture.md §15.1 — Unit Test: `src/test/java/com/autocare/ai/model/dto/ChatRequestValidationTest.java` — [x] Done
- [x] **T3.3** `src/main/java/com/autocare/ai/model/dto/ChatResponse.java` — depends on `SourceCitation` (T3.1), `CitationStatus` (T1.3); includes **both original and transformed query echo fields** for audit traceability — architecture.md §15.2/§15.3 — Unit Test: `src/test/java/com/autocare/ai/model/dto/ChatResponseTest.java` — [x] Done
- [x] **T3.4** `src/main/java/com/autocare/ai/model/dto/ErrorResponse.java` — `timestamp`, `status`, `error`, `message`, `correlationId`, `path` — architecture.md §15.4 — Unit Test: `src/test/java/com/autocare/ai/model/dto/ErrorResponseTest.java` — [x] Done
- [x] **T3.5** `src/main/java/com/autocare/ai/model/dto/KbIngestRequest.java` — depends on `DocStatus` (T1.2), `UserRole` (T1.1) — FR-030 — Unit Test: `src/test/java/com/autocare/ai/model/dto/KbIngestRequestValidationTest.java` — [x] Done

## Phase 4 — Entities

- [x] **T4.1** `src/main/java/com/autocare/ai/audit/ConversationAuditRecord.java` — JPA entity; columns per architecture.md §16 **plus explicit `original_query` and `transformed_query` columns** (missing SRS item — both the pre-transform and post-transform query must be independently retained, not just the final one) — SRS §4.3, §4.4 — Unit Test: `src/test/java/com/autocare/ai/audit/ConversationAuditRecordTest.java` — [x] Done
- [x] **T4.2** `src/main/java/com/autocare/ai/retrieval/KbDocumentChunkEntity.java` — embedding vector column sized to `AutoCareProperties.embedding.dimension` (depends on T2.2), `doc_code`, `version`, `status` (T1.2), `audience`, `vehicle_family`, `section`, `doc_type` — NFR-008 — Unit Test: `src/test/java/com/autocare/ai/retrieval/KbDocumentChunkEntityTest.java` — [x] Done

## Phase 5 — Repositories

- [x] **T5.1** `src/main/java/com/autocare/ai/audit/ConversationAuditRepository.java` — append-only; no update/delete methods exposed (depends on T4.1) — NFR-004 — Unit Test: `src/test/java/com/autocare/ai/audit/ConversationAuditRepositoryIT.java` — [x] Done
- [x] **T5.2** `src/main/java/com/autocare/ai/retrieval/KbDocumentChunkRepository.java` — query methods filtering by `status`, `version`, `audience`, `vehicle_family`, similarity (depends on T4.2) — architecture.md §5 — Unit Test: `src/test/java/com/autocare/ai/retrieval/KbDocumentChunkRepositoryIT.java` — [x] Done

## Phase 6 — Redaction

- [x] **T6.1** `src/main/java/com/autocare/ai/security/RedactionPatterns.java` — regex + token map: VIN → `[VIN-REDACTED]`, registration → `[REG-NO-REDACTED]`, customer ID → `[CUSTOMER-ID-REDACTED]`, phone → `[PHONE-REDACTED]`, email → `[EMAIL-REDACTED]`, address → `[ADDRESS-REDACTED]` — SRS §4.6 — Unit Test: `src/test/java/com/autocare/ai/security/RedactionPatternsTest.java` — [x] Done
- [x] **T6.2** `src/main/java/com/autocare/ai/advisor/PiiVehicleRedactionAdvisor.java` — pipeline stage 1, depends on T6.1 — SRS §2.1, §4.6, NFR-003 — Unit Test: `src/test/java/com/autocare/ai/advisor/PiiVehicleRedactionAdvisorTest.java` — [x] Done

## Phase 7 — Ingestion

- [x] **T7.1** `src/main/java/com/autocare/ai/retrieval/VehicleFamilyResolver.java` — **(missing SRS item: vehicle-family handling)** resolves/normalizes `vehicle_family` codes; falls back to `vehicle_family=all` when missing or unsupported; flags ambiguous codes for clarification per KB-AUTO-001 §9 — FR-001, KB-AUTO-001 §4/§9 — Unit Test: `src/test/java/com/autocare/ai/retrieval/VehicleFamilyResolverTest.java` — [x] Done
- [x] **T7.2** `src/main/java/com/autocare/ai/service/KbIngestionService.java` — chunk, embed (dimension-matched per T2.2), tag metadata, activate/retire versions (depends on T4.2, T5.2) — FR-030, KB-AUTO-008 §4 — Unit Test: `src/test/java/com/autocare/ai/service/KbIngestionServiceTest.java` — [x] Done
- [x] **T7.3** `src/main/java/com/autocare/ai/controller/KbIngestController.java` — `POST /kb/ingest`, Admin only (depends on T3.5, T7.2, T2.3) — SRS §6 — Unit Test: `src/test/java/com/autocare/ai/controller/KbIngestControllerTest.java` — [x] Done

## Phase 8 — RAG Pipeline

- [x] **T8.1** `src/main/java/com/autocare/ai/service/LanguageDetectionService.java` — SRS §2.1 stage 4 — Unit Test: `src/test/java/com/autocare/ai/service/LanguageDetectionServiceTest.java` — [x] Done
- [x] **T8.2** `src/main/java/com/autocare/ai/transform/TranslationQueryTransformerConfig.java` — conditional on language mismatch (depends on T8.1, T2.1) — SRS §4.4 — Unit Test: `src/test/java/com/autocare/ai/transform/TranslationQueryTransformerConfigTest.java` — [x] Done
- [x] **T8.3** `src/main/java/com/autocare/ai/transform/RewriteQueryTransformerConfig.java` — conditional on unclear/dependent follow-up — SRS §4.4 — Unit Test: `src/test/java/com/autocare/ai/transform/RewriteQueryTransformerConfigTest.java` — [x] Done
- [x] **T8.4** `src/main/java/com/autocare/ai/transform/MultiQueryExpanderConfig.java` — conditional on broad/ambiguous query — SRS §4.4 — Unit Test: `src/test/java/com/autocare/ai/transform/MultiQueryExpanderConfigTest.java` — [x] Done
- [x] **T8.5** `src/main/java/com/autocare/ai/retrieval/MetadataFilterBuilder.java` — builds `status`/`version`/`audience`/`vehicle_family` filters from `RoleContext`; uses `VehicleFamilyResolver` (T7.1) for missing/unsupported family fallback — architecture.md §5 — Unit Test: `src/test/java/com/autocare/ai/retrieval/MetadataFilterBuilderTest.java` — [x] Done
- [x] **T8.6** `src/main/java/com/autocare/ai/retrieval/VectorStoreDocumentRetrieverConfig.java` — similarity threshold ≥ 0.50 from `AutoCareProperties` (depends on T8.5, T5.2) — SRS §4.1 — Unit Test: `src/test/java/com/autocare/ai/retrieval/VectorStoreDocumentRetrieverConfigTest.java` — [x] Done
- [x] **T8.7** `src/main/java/com/autocare/ai/retrieval/ContextualQueryAugmenterConfig.java` — `allowEmptyContext=false`; raises `NoMatchingContextException` — SRS §4.1, §4.2 — Unit Test: `src/test/java/com/autocare/ai/retrieval/ContextualQueryAugmenterConfigTest.java` — [x] Done
- [x] **T8.8** `src/main/java/com/autocare/ai/config/RetrievalAugmentationConfig.java` — wires T8.2–T8.7 into `RetrievalAugmentationAdvisor` — SRS §2.4 — Unit Test: `src/test/java/com/autocare/ai/config/RetrievalAugmentationConfigTest.java` — [x] Done

## Phase 9 — Advisors

- [x] **T9.1** `src/main/java/com/autocare/ai/advisor/ConversationAuditAdvisor.java` — pre-call (open record) / post-call (finalize); persists **original query, transformed query, and expanded queries** separately (depends on T4.1, T5.1) — SRS §4.3 — Unit Test: `src/test/java/com/autocare/ai/advisor/ConversationAuditAdvisorTest.java` — [x] Done
- [x] **T9.2** `src/main/java/com/autocare/ai/advisor/SimpleLoggerAdvisorConfig.java` — enables/configures framework diagnostic logging — SRS §2.4 — Unit Test: `src/test/java/com/autocare/ai/advisor/SimpleLoggerAdvisorConfigTest.java` — [x] Done
- [x] **T9.3** `src/main/java/com/autocare/ai/advisor/DomainSafeguardAdvisor.java` — **(missing SRS item: safety / warranty / no-price rules)** enforces: (a) safety-critical stop-driving guidance only when present in retrieved KB content, (b) warranty answers are preliminary pre-check only, never approved/rejected, (c) no unsupported repair cost or completion date may be generated — SRS §4.5, KB-AUTO-003 §5, KB-AUTO-005 — Unit Test: `src/test/java/com/autocare/ai/advisor/DomainSafeguardAdvisorTest.java` — [x] Done
- [x] **T9.4** `src/main/java/com/autocare/ai/advisor/CitationValidationAdvisor.java` — synchronous validation; blocks unsupported policy claims (depends on T1.3) — NFR-001 — Unit Test: `src/test/java/com/autocare/ai/advisor/CitationValidationAdvisorTest.java` — [x] Done
- [x] **T9.5** `src/main/java/com/autocare/ai/advisor/StreamingCitationValidationAdvisor.java` — **(missing SRS item: streaming citation validation)** buffers/validates citations against emitted chunks for `/ai/autocare/chat/stream` before any partial answer is released; blocks/escalates the stream if an unsupported claim appears (depends on T9.4) — NFR-001 applied to streaming — Unit Test: `src/test/java/com/autocare/ai/advisor/StreamingCitationValidationAdvisorTest.java` — [x] Done

## Phase 10 — APIs

- [x] **T10.1** `src/main/java/com/autocare/ai/exception/NoMatchingContextException.java` — raised by T8.7 — SRS §4.2 — Unit Test: `src/test/java/com/autocare/ai/exception/NoMatchingContextExceptionTest.java` — [x] Done
- [x] **T10.2** `src/main/java/com/autocare/ai/exception/GlobalExceptionHandler.java` — maps exceptions (incl. T10.1, dependent-service outages) to `ErrorResponse` (T3.4); returns `503` for vector store/LLM/embedding outages — NFR-005, architecture.md §15.4 — Unit Test: `src/test/java/com/autocare/ai/exception/GlobalExceptionHandlerTest.java` — [x] Done
- [x] **T10.3** `src/main/java/com/autocare/ai/service/ChatService.java` — orchestrates the full advisor/RAG pipeline in fixed order (T6.2 → T9.1 → T9.2 → T8.1 → T8.2–T8.4 → T8.6 → T8.7 → T8.8 → AnswerChatClient → T9.4/T9.5 → T9.3 → T9.1 finalize) — SRS §2.1 — Unit Test: `src/test/java/com/autocare/ai/service/ChatServiceTest.java` — [x] Done
- [x] **T10.4** `src/main/java/com/autocare/ai/controller/ChatController.java` (sync method) — `POST /ai/autocare/chat/sync` (depends on T3.2, T3.3, T10.3) — SRS §6 — Unit Test: `src/test/java/com/autocare/ai/controller/ChatControllerSyncTest.java` — [x] Done
- [x] **T10.5** `src/main/java/com/autocare/ai/controller/ChatController.java` (stream method) — `POST /ai/autocare/chat/stream`, wires `StreamingCitationValidationAdvisor` (T9.5) — SRS §6 — Unit Test: `src/test/java/com/autocare/ai/controller/ChatControllerStreamTest.java` — [x] Done

## Phase 11 — Demo & NFR Verification

- [x] **T11.1** `src/test/java/com/autocare/ai/demo/MultiChatClientDemoIT.java` — proves `QueryTransformChatClient` and `AnswerChatClient` are both invoked and logged within one session — SRS §4.7 (mandatory) — Unit Test: itself (integration test) — [x] Done
- [x] **T11.2** `src/test/java/com/autocare/ai/performance/ResponseTimeTest.java` — **(missing SRS item: response-time test)** asserts the agreed response-time objective for `/ai/autocare/chat/sync` under test load — NFR-006 — Unit Test: itself — [x] Done
- [ ] **T11.3** `src/test/java/com/autocare/ai/kb/KbRetrievalTestSuiteIT.java` — checks 16 KB-METADATA.md §6 questions for required facts, citation doc/section and status — NFR-010 — Test: itself — [ ] Done; corrected the `vehicleFamily=all` filter and confirmed all 201 source chunks have effective dates. The full suite still fails 12/16 because its local TF-IDF test embedder scores several pairs below 0.50; these are synthetic-corpus results, not production ada-002 top-5 scores. Live RAG validation is blocked by Azure `DeploymentNotFound` on the configured chat deployment, so NFR-010 remains unsigned.

## Phase 12 — Requested KB Governance and Coverage Gaps

- [x] **T12.1** `KbVersionManagementService`, `KbValidationQuestionCatalog`, and `KbManagementController` — candidate versions start as `RETIRED`; SRS validation questions load from bundled `KB-METADATA.md`; activation requires a passing candidate validation; activation and rollback preserve `SUPERSEDED` history and append actor/timestamp records to the existing conversation audit — focused tests pass. No new status, table, or schema was added.
- [x] **T12.2** `src/main/resources/static/admin.html` and `js/admin.js` — same-origin admin ingestion, version listing, validation, activation and rollback screen; guarded APIs remain admin-only. Static asset requests return HTTP 200; unauthenticated/OWNER management requests return 401/403; JavaScript syntax checks pass.
- [x] **T12.3** `pom.xml` — JaCoCo line coverage report and an 80% `com.autocare.ai.service` package gate — focused service tests and the configured JaCoCo check pass.
- [ ] **T12.4** Live administrator validation/activation/rollback — all eight KBs were ingested through `POST /kb/ingest` as 201 `RETIRED` chunks with metadata-derived effective dates and section/audience tags. Validation returns HTTP 503 because the configured Azure chat deployment is `DeploymentNotFound`; activation remains correctly blocked (409), and no version was activated or rolled back. Do not bypass the validation gate or substitute a different model.

## Compile-Order Rationale (summary)

1. **Enums before DTOs/Entities/Config** — `UserRole`, `DocStatus`, `CitationStatus` are referenced by `SecurityConfig`, every DTO and both entities.
2. **Config before Services** — `ChatService` (T10.3) injects `ChatClientConfig` beans (T2.1) and `AutoCareProperties` (T0.4).
3. **Entities before Repositories** — repositories are typed on the entity classes.
4. **Repositories before Ingestion/RAG services** that query or persist through them.
5. **Redaction before Advisors/ChatService** — `PiiVehicleRedactionAdvisor` must exist before it can be wired as pipeline stage 1.
6. **RAG Pipeline pieces before `RetrievalAugmentationConfig`** — the wiring config references each transformer/retriever/augmenter bean.
7. **Advisors before APIs** — `ChatController`/`ChatService` depend on the advisor beans.
8. **Demo/NFR tests last** — they exercise the fully assembled application.

## Requirement Coverage Checklist (incl. previously missing items)

- [x] pom dependencies (T0.1)
- [x] AutoCare properties binding (T0.4, T0.5)
- [x] Embedding dimension configured and matched to vector column (T2.2, T4.2)
- [x] Original + transformed query both retained in audit (T4.1, T9.1)
- [x] Vehicle-family handling incl. missing/unsupported fallback (T7.1, T8.5)
- [x] Safety / warranty / no-price domain safeguards (T9.3)
- [x] Streaming citation validation (T9.5, T10.5)
- [x] Response-time (NFR-006) test (T11.2; p95 ≤ 500 ms, p99 ≤ 1000 ms, stubbed-model pipeline)
- [x] Two ChatClients wired, routed and demoed (T2.1, T11.1)
- [ ] FR-001–FR-030, NFR-001–NFR-010 — coverage needs final end-to-end sign-off after remaining open checks below.

## Phase 13 — SRS Gaps Implemented

- [x] Resolved all four merge-conflict regions in `frontend/app.js`, preserving both credential entry/default flows and the JSON `Accept` header; JavaScript syntax checked.
- [x] Moved answer, translation, rewrite, and expansion prompts to `src/main/resources/prompts/*.st` and added load/render tests.
- [x] Added redacting JDBC-backed Spring AI chat memory, session-keyed `MessageChatMemoryAdvisor`, configurable 20-message window, and Flyway V4 tables. The migration was added only after the user approved its schema.
- [x] Unified sync/async service advisor composition, attached mode/latency/advisor/memory trace values, added stream error events and standard JSON 401/403 responses.
- [x] Added advisor-only review queue APIs, approval/edit/reject decisions, append-only reviewer decision audit, and owner reviewed-answer lookup.
- [x] Added configurable per-user chat/KB fixed-window limits and safe 429 responses with `Retry-After`.
- [x] Added rolling console/file logs and request metadata logging with no request/response body logging.
- [x] Added Sync/Async chat toggle, fetch/ReadableStream SSE handling, trace chips, friendly errors, and advisor review page.
- [x] Focused Maven tests passed: 78 tests, 0 failures, 0 errors, 0 skipped.
- [x] Final `mvn clean verify`: 235 unit tests and 7 other integration tests passed; the known `KbRetrievalTestSuiteIT` was excluded.
- [x] Started the final app with `mvn spring-boot:run` on 8080; MariaDB Flyway validated and applied V4; home page and advisor review page returned HTTP 200.
- [x] Live auth, advisor pending-queue, chat/KB rate limit, and rolling-log checks passed; the 11th chat request and 11th admin KB request returned 429 with `Retry-After`, and request/rate-limit records reached `logs/autocare-ai.log`.
- [x] Live chat UI toggle was exercised in both modes. Sync returned the friendly 503 message; async produced a structured SSE error event with status 503.
- [ ] Live grounded-answer, multi-turn answer-memory, and HITL queue/decision flow remain blocked by Azure configuration: the prior endpoint returns `DeploymentNotFound` for `gpt-5-1`; the supplied endpoint rejects the currently configured key with `401 Unauthorized`. Keep the model name unchanged and align the endpoint/key pair. Memory window/isolation/redaction and HITL decision paths are covered by passing automated tests, but not live against a successful model response.
