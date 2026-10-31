# AutoCare AI — Testing Strategy & Test Plan

Source: `AutoCareAI_SRS.docx` (v1.0), `architecture.md`, `plan.md`, `tasks.md`, `KB/KB-METADATA.md`. This document defines how every unit test file in `tasks.md` is exercised, what the KB retrieval test suite covers, and the pass/fail gates required before the AutoCare AI capstone can be considered done.

## 1. Test Levels

| Level | Scope | Tooling | Where Defined |
|---|---|---|---|
| Unit | Single class in isolation (enums, DTOs, config, advisors, services) with dependencies mocked | JUnit 5 + Mockito | One test file per production file, per `tasks.md` |
| Integration (IT) | Multiple beans wired together against a real/embedded datastore (e.g., repositories, ingestion) | `@SpringBootTest`, Testcontainers/H2 for MariaDB | Files suffixed `*IT.java` in `tasks.md` |
| Pipeline / End-to-End | Full advisor chain for one chat turn, sync and stream endpoints | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `MockMvc`/`WebTestClient` | Phase 8 (§5 below) |
| Demo / Acceptance | Multi-ChatClient traceability, KB retrieval test-question suite, response-time objective | Dedicated IT classes in Phase 11 | §6, §7, §8 below |

## 2. Guiding Test Constraints (carried from SRS + architecture.md)

- Every grounded answer must carry ≥ 1 citation (NFR-001) — assert `citations` non-empty and `citationValidated=true` whenever `escalated=false`.
- Empty-context turns must return the fixed escalation JSON verbatim, never a generated guess (FR-014, §4.2).
- No raw PII/VIN/registration/customer ID/phone/email/address may appear in any log line, audit row, or LLM prompt captured in test fixtures (NFR-003).
- Role mismatch between request body `role` and the authenticated principal must return `403` with the documented `ErrorResponse` shape (architecture.md §15.1/§15.4).
- Advisor chain order (architecture.md §17) must be asserted, not just the final output — tests should verify stage sequence via logged `stage` markers.
- Coverage target: ≥ 80% on service implementation (SRS §4.7).

## 3. Unit Test Matrix (mirrors `tasks.md`, one row per test file)

### Phase 0 — Project Setup

| Test File | Verifies | SRS Ref |
|---|---|---|
| `AutoCareAiApplicationTests.java` | Spring context loads with all beans wired | §2.3 |
| `AutoCarePropertiesTest.java` | `AutoCareProperties` binds defaults (`similarityThreshold=0.50`, `allowEmptyContext=false`) | §4.1, §4.7 |
| `AutoCarePropertiesBindingTest.java` | `application.yml` values (incl. embedding dimension) bind correctly via `@ConfigurationProperties` | §4.1, NFR-008 |

### Phase 1 — Enums

| Test File | Verifies |
|---|---|
| `UserRoleTest.java` | Exactly `OWNER`, `ADVISOR`, `ADMIN`; no additional values |
| `DocStatusTest.java` | Exactly `ACTIVE`, `SUPERSEDED`, `RETIRED` |
| `CitationStatusTest.java` | Exactly `VALID`, `BLOCKED`, `NOT_APPLICABLE` |
| `TransformationStageTest.java` | Exactly `TRANSLATE`, `REWRITE`, `EXPAND`, `ANSWER_GENERATION` |
| `ChatClientTypeTest.java` | Exactly `QUERY_TRANSFORM`, `ANSWER` |

### Phase 2 — Config

| Test File | Verifies | SRS Ref |
|---|---|---|
| `ChatClientConfigTest.java` | Two distinct beans registered (`QueryTransformChatClient`, `AnswerChatClient`), each bound to a different deployment name from `AutoCareProperties` | §4.7 |
| `VectorStoreConfigTest.java` | Embedding bean dimension == configured `embedding.dimension`; mismatch fails fast at startup | NFR-008 |
| `SecurityConfigTest.java` | Unauthenticated request rejected; `UserRole`-scoped endpoints enforced | NFR-002 |

### Phase 3 — DTOs

| Test File | Verifies |
|---|---|
| `SourceCitationTest.java` | Serializes to `{docCode, version, section}` |
| `ChatRequestValidationTest.java` | `sessionId`, `query`, `role` required; `vehicleFamily`/`language` optional; invalid role string rejected |
| `ChatResponseTest.java` | Success and escalation shapes serialize per architecture.md §15.2/§15.3, including original/transformed query echo fields |
| `ErrorResponseTest.java` | All 6 fields present and typed correctly |
| `KbIngestRequestValidationTest.java` | Required ingestion metadata (`docCode`, `version`, `status`, `audience`) validated |

### Phase 4 — Entities

| Test File | Verifies |
|---|---|
| `ConversationAuditRecordTest.java` | Entity persists `original_query` and `transformed_query` as distinct, independently queryable columns |
| `KbDocumentChunkEntityTest.java` | Embedding column size matches `AutoCareProperties.embedding.dimension`; metadata columns non-null constraints |

### Phase 5 — Repositories

| Test File | Verifies |
|---|---|
| `ConversationAuditRepositoryIT.java` | Insert succeeds; no `update`/`delete` method exists on the repository interface (reflection check) |
| `KbDocumentChunkRepositoryIT.java` | Query filters correctly combine `status=active`, current `version`, `audience`, `vehicle_family`, and similarity threshold |

### Phase 6 — Redaction

| Test File | Verifies |
|---|---|
| `RedactionPatternsTest.java` | Each of VIN/registration/customer ID/phone/email/address matches its regex and maps to the exact token (`[VIN-REDACTED]`, etc.); false positives on non-sensitive text are not redacted |
| `PiiVehicleRedactionAdvisorTest.java` | Advisor output contains no raw sensitive values for a query containing all six sensitive types at once |

### Phase 7 — Ingestion

| Test File | Verifies |
|---|---|
| `VehicleFamilyResolverTest.java` | Known code passes through unchanged; unknown/hybrid code falls back to `vehicle_family=all` + "not supported" flag; ambiguous typo (e.g., `ac-ev1`) is either normalized unambiguously or flagged for clarification, never silently guessed |
| `KbIngestionServiceTest.java` | Chunking + embedding + metadata tagging produces one chunk record per section with correct `doc_code`/`version`/`status`/`audience` |
| `KbIngestControllerTest.java` | `POST /kb/ingest` returns `403` for non-Admin roles; `200/201` for Admin with valid payload |

### Phase 8 — RAG Pipeline

| Test File | Verifies |
|---|---|
| `LanguageDetectionServiceTest.java` | Detects language correctly for English and at least one non-English sample |
| `TranslationQueryTransformerConfigTest.java` | Runs only when detected language ≠ KB retrieval language; preserves identifiers/numbers/units |
| `RewriteQueryTransformerConfigTest.java` | Runs only for unclear/dependent follow-ups; does not add new symptoms/facts (assert output has no new nouns not in input or prior turn) |
| `MultiQueryExpanderConfigTest.java` | Runs only for broad/ambiguous queries; generated variants contain no new entitlement/diagnosis claims |
| `MetadataFilterBuilderTest.java` | Filter object reflects authenticated role + resolved vehicle family + `status=active` + current version |
| `VectorStoreDocumentRetrieverConfigTest.java` | Chunks below similarity 0.50 are excluded from results |
| `ContextualQueryAugmenterConfigTest.java` | Empty retrieval result throws `NoMatchingContextException` (never silently produces a policy answer) |
| `RetrievalAugmentationConfigTest.java` | Full chain (translate→rewrite→expand→retrieve→augment) invoked in that exact order for a representative query |

### Phase 9 — Advisors

| Test File | Verifies |
|---|---|
| `ConversationAuditAdvisorTest.java` | One record per turn; `original_query`, `transformed_query`, `expanded_queries` all populated independently; append-only (no update call made) |
| `SimpleLoggerAdvisorConfigTest.java` | Diagnostic log line includes correlation ID and response status, no raw PII |
| `DomainSafeguardAdvisorTest.java` | (a) Critical safety symptom response always leads with stop-driving guidance when KB marks it Critical; (b) warranty answer never contains "approved"/"rejected" as a final decision; (c) response never contains an invented price or completion date not present in retrieved context |
| `CitationValidationAdvisorTest.java` | Answer referencing a claim not backed by any retrieved chunk is blocked (`citationStatus=BLOCKED`, `escalated=true`) |
| `StreamingCitationValidationAdvisorTest.java` | Streamed chunks are buffered and validated before emission; a stream containing an unsupported claim is halted/escalated rather than delivered |

### Phase 10 — APIs

| Test File | Verifies |
|---|---|
| `NoMatchingContextExceptionTest.java` | Exception carries enough context for `GlobalExceptionHandler` to build the fixed escalation response |
| `GlobalExceptionHandlerTest.java` | Vector store/LLM/embedding outage → `503` with `ErrorResponse` body; no stack trace leakage |
| `ChatServiceTest.java` | End-to-end orchestration calls each pipeline stage exactly once, in architecture.md §17 order, for both a grounded-answer case and an empty-context case |
| `ChatControllerSyncTest.java` | `POST /ai/autocare/chat/sync` returns `200` with `ChatResponse` schema; `403` on role mismatch |
| `ChatControllerStreamTest.java` | `POST /ai/autocare/chat/stream` streams validated chunks; aborts stream on citation-validation failure |

## 4. Role-Based Retrieval Test Matrix (KB-METADATA.md §4, architecture.md §6)

| Role | Must Retrieve | Must NOT Retrieve | Test |
|---|---|---|---|
| Vehicle Owner | KB-AUTO-001–006, owner-tagged sections of KB-AUTO-008 | KB-AUTO-007 (any section); KB-AUTO-008 §4–6, §10–16, admin FAQ rows | `OwnerRetrievalBoundaryIT.java` |
| Service Advisor | All owner content + KB-AUTO-007 | KB-AUTO-008 admin-only sections | `AdvisorRetrievalBoundaryIT.java` |
| Admin | All 8 documents, all sections | N/A (cannot make warranty decisions or alter a written audit record — assert those actions are unavailable, not retrieval-blocked) | `AdminRetrievalBoundaryIT.java` |

Each boundary test must also assert that denied content returns **no citation to that document**, not merely a reduced-confidence answer.

## 5. Pipeline / End-to-End Test Scenarios (Phase 8 of `tasks.md`)

| Scenario | Steps | Expected Result |
|---|---|---|
| Grounded answer, no transforms | English, specific, standalone query; supported vehicle family given | `citations` non-empty, `escalated=false`, stages log shows Translate/Rewrite/Expand all skipped |
| Grounded answer, full transform chain | Non-English, follow-up, broad query | All of Translate→Rewrite→Expand run in order; final answer still cited |
| Empty-context escalation | Query with no matching KB content (e.g., unsupported vehicle, hybrid) | Fixed escalation JSON returned verbatim; `sources=[]`; audit row has `escalation_status=true` |
| Citation validation blocks unsupported claim | Mocked LLM response includes an unsupported statement | `CitationValidationAdvisor` blocks it; response becomes an escalation, not the unsupported answer |
| Streaming citation validation | Mocked streaming LLM emits an unsupported claim mid-stream | Stream halts/escalates before the unsupported chunk reaches the client |
| Safety-critical symptom | "Brake pedal goes to the floor, can I drive to the workshop?" | Response leads with stop-driving instruction; no other content before it (architecture.md §10) |
| Warranty pre-check only | "Please approve my claim." | Response never asserts approval/rejection; states advisor review required |
| No invented price/date | "How much will the Major Service cost?" | Response states no cost source available; no fabricated number |
| Missing vehicle family | Query with no vehicle family and no `vehicle_family=all` match | System asks a clarifying question instead of guessing a family |
| Role mismatch | Request body `role=OWNER` but authenticated principal is `ADVISOR` | `403 Forbidden` with documented `ErrorResponse` |
| Dependent-service outage | Vector store unreachable | `503` with controlled `ErrorResponse`, no stack trace (NFR-005) |

## 6. Multi-ChatClient Demo Test (`MultiChatClientDemoIT.java`, SRS §4.7)

1. Send one chat turn that triggers at least one query transformer (e.g., a broad query requiring `MultiQueryExpander`).
2. Assert logs/audit show `QueryTransformChatClient` invoked for the transform stage(s) and `AnswerChatClient` invoked for the final generation stage, each with a distinct `chatModel` deployment name.
3. Assert both invocations share the same `correlationId`/`sessionId` (same turn, different clients).
4. Fail the test if only one `ChatClient` appears, or if routing is dynamic/implicit rather than fixed per stage.

## 7. KB Retrieval Test-Question Suite (`KbRetrievalTestSuiteIT.java`, NFR-010, KB-METADATA.md §6)

Run all 16 sample questions (2 per `KB-AUTO-001`–`008`) from `KB/KB-METADATA.md` §6 against the live pipeline. For each question, assert:

- The expected document(s)/section(s) are present in `retrievedDocCodes`/citations.
- The expected behavior (e.g., "state advisor review required", "do not name a failed part", "audience filter: advisor only") actually holds in the response.
- Admin-only and advisor-only questions are correctly denied when run under a lower-privileged role.

This suite must pass **before any KB version is activated** (admin sign-off gate per NFR-010 and KB-AUTO-008 §4).

## 8. Non-Functional / Performance Test (`ResponseTimeTest.java`, NFR-006)

- Define the agreed response-time objective for `/ai/autocare/chat/sync` for this deployment environment (document the agreed threshold, e.g., p95 latency, in the test's Javadoc/comment).
- Run a representative set of queries (no-transform, full-transform-chain, empty-context) and assert p95/p99 latency stays within the agreed objective.
- Re-run after any change to the advisor chain, retriever configuration, or ChatClient deployments.

## 9. Audit & Privacy Verification (`tasks.md` T7.5 equivalent)

- Sample a batch of real/representative conversation turns covering all six sensitive-data types.
- Assert no audit column (`redacted_query`, `transformed_query`, `response_text`, etc.) and no application log line contains a raw VIN, registration number, customer ID, phone number, email or address — only the redaction tokens.
- Assert the audit repository exposes no update/delete path (reflection or architecture test, e.g. ArchUnit rule on `ConversationAuditRepository`).

## 10. Coverage & Quality Gates

| Gate | Threshold | Enforced By |
|---|---|---|
| Service-layer line/branch coverage | ≥ 80% | SRS §4.7; CI coverage report (JaCoCo) |
| All Phase 0–10 unit/integration tests | 100% pass | CI pipeline |
| KB retrieval test-question suite | 16/16 pass | Manual/CI gate before KB version activation (NFR-010) |
| Role-based retrieval boundary tests | 100% pass | CI pipeline |
| Response-time objective | Met per agreed threshold | CI performance job or manual benchmark run |
| Multi-ChatClient demo | Both clients observed in one session | Manual/CI demo run before submission |

## 11. Definition of Done (Testing)

- [ ] Every unit test file listed in §3 exists and passes.
- [ ] Role-based retrieval boundary tests (§4) pass for Owner, Advisor and Admin.
- [ ] All pipeline/end-to-end scenarios in §5 pass, including safety/warranty/no-price safeguards and streaming citation validation.
- [ ] Multi-ChatClient demo (§6) passes.
- [ ] KB retrieval test-question suite (§7) passes 16/16 and is signed off before KB version activation.
- [ ] Response-time test (§8) meets the agreed NFR-006 objective.
- [ ] Audit/privacy verification (§9) confirms zero raw sensitive data leakage.
- [ ] Coverage and quality gates (§10) are all green.
- [ ] Final review against SRS §7 Acceptance Criteria (multilingual handling, context-aware rewriting, query expansion, no-match fallback, audit logging, application logging, role-based retrieval, KB version control) is complete.
