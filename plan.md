# AutoCare AI — Implementation Plan

Source: `AutoCareAI_SRS.docx` (v1.0), `architecture.md`, `KB/KB-METADATA.md`. This plan sequences the build of the AutoCare AI Spring AI RAG assistant into phases with concrete, verifiable tasks, each traced back to SRS requirements (FR/NFR) and the architecture decisions already documented.

## 0. Guiding Constraints (carried from SRS + architecture.md)

- No invented service intervals, warranty terms, prices, SLA values or claim decisions — every policy answer must cite retrieved KB content (FR-013, NFR-001).
- `allowEmptyContext=false`; empty-context turns return the fixed escalation JSON, never a generated guess (FR-014, §4.2).
- Role-based retrieval: KB-AUTO-007 is advisor-only; KB-AUTO-008 has section-level audience splits; only `status=active` + current `version` chunks are retrievable (FR-024, NFR-002, NFR-008).
- PII/vehicle identifiers redacted before any logging or LLM call (NFR-003, §4.6).
- Two distinct `ChatClient`s (`QueryTransformChatClient`, `AnswerChatClient`) with explicit routing and traceable logs (SRS §4.7, mandatory).
- Fixed advisor execution order (architecture.md §17) must not be reordered.
- 80% code coverage target for service implementation (SRS §4.7 "Test cases").

## 1. Phase Overview

| Phase | Goal | Primary Architecture Refs |
|---|---|---|
| 1. Foundation & Project Setup | Scaffold `com.autocare.ai`, base config, dependencies | architecture.md §3, §13 |
| 2. Data & KB Ingestion | Vector store schema, KB ingestion pipeline, load 8 KB docs | architecture.md §5, §9; KB-METADATA.md |
| 3. Security & Redaction | Role resolution, PII redaction patterns | architecture.md §15.1, §7 |
| 4. Advisor Chain & RAG Pipeline | Implement advisors/transformers in fixed order | architecture.md §4, §14, §17 |
| 5. Multi-ChatClient Configuration | Wire `QueryTransformChatClient` / `AnswerChatClient` | architecture.md §12 |
| 6. Chat API & Contracts | Controllers, DTOs, error handling | architecture.md §8, §15 |
| 7. Audit & Observability | Audit table, correlation IDs, diagnostic logging | architecture.md §16, §7 |
| 8. Testing & Quality Gates | Unit/integration tests, retrieval test suite, coverage | SRS §4.7, NFR-010, KB-METADATA.md §6 |
| 9. Documentation & Demo Readiness | API docs, multi-model traceability demo, final review | SRS §2.3, §4.7 |

## 2. Phase 1 — Foundation & Project Setup

| Task | Description | Trace |
|---|---|---|
| 1.1 | Initialize Spring Boot 3.x project with Java 21+, add Spring AI, Spring Data JPA, Springdoc OpenAPI dependencies | architecture.md §3 |
| 1.2 | Create package skeleton exactly per `com.autocare.ai` structure (config, controller, advisor, transform, retrieval, service, model.dto, model.enums, audit, security, exception) | architecture.md §13 |
| 1.3 | Configure MariaDB 11.8+ datasource with vector column support | architecture.md §3, §5 |
| 1.4 | Configure Azure OpenAI credentials/deployments (one for `QueryTransformChatClient`, one for `AnswerChatClient`) via externalized properties | architecture.md §12 |
| 1.5 | Add Springdoc OpenAPI / Swagger UI configuration | architecture.md §3 |

**Exit criteria:** Application boots, connects to MariaDB, Swagger UI reachable, no business logic yet.

## 3. Phase 2 — Data & KB Ingestion

| Task | Description | Trace |
|---|---|---|
| 2.1 | Design vector store schema: embedding column + metadata columns (`doc_code`, `version`, `status`, `audience`, `vehicle_family`, `section`, `doc_type`) | architecture.md §5 |
| 2.2 | Implement `KbIngestionService`: chunk, embed, tag metadata per KB-AUTO-008 §4 ingestion steps | architecture.md §9, §13 |
| 2.3 | Implement `KbIngestController` (`POST /kb/ingest`, Admin only) | architecture.md §8, §13 |
| 2.4 | Ingest all 8 KB documents (`KB-AUTO-001`–`008`) with metadata from `KB-METADATA.md` §1 (doc_code, version=v1.0, status=active, audience, doc_type, effective_date) | KB-METADATA.md §1, §5 |
| 2.5 | Apply section-level audience overrides for KB-AUTO-008 per KB-METADATA.md §3 table (admin-only sections 4–6, 10–16) | KB-METADATA.md §3 |
| 2.6 | Implement version activation/retirement logic: activating a new version retires the prior one for the same `doc_code` | architecture.md §9, KB-METADATA.md §5 |

**Exit criteria:** All 8 KB documents ingested, queryable with correct metadata, only one `active` version per `doc_code`.

## 4. Phase 3 — Security & Redaction

| Task | Description | Trace |
|---|---|---|
| 3.1 | Implement `UserRole` enum (`OWNER`, `ADVISOR`, `ADMIN`) and `SecurityConfig` auth filter chain | architecture.md §13, §15.1 |
| 3.2 | Implement `RoleContext` to resolve the authenticated principal's role (e.g., JWT claim) independent of request-body `role` | architecture.md §15.1 |
| 3.3 | Enforce role-match check: reject (`403`) when request body `role` ≠ authenticated role | architecture.md §15.1, §15.4 |
| 3.4 | Implement `RedactionPatterns` for VIN, registration number, customer ID, phone, email, address per the redaction token map | architecture.md §7 |
| 3.5 | Implement `PiiVehicleRedactionAdvisor` as the first stage of every turn | architecture.md §17 step 1 |

**Exit criteria:** No raw VIN/registration/phone/email/customer ID/address can reach logs or the LLM; role mismatch returns the documented error contract.

## 5. Phase 4 — Advisor Chain & RAG Pipeline

Implement strictly in the fixed order from architecture.md §17; each task below corresponds to one pipeline stage.

| Task | Stage | Description | Trace |
|---|---|---|---|
| 4.1 | 1 | `PiiVehicleRedactionAdvisor` (depends on Phase 3) | §17.1 |
| 4.2 | 2, 13 | `ConversationAuditAdvisor` pre-call (open record) and post-call (finalize record) | §17.2, §17.13 |
| 4.3 | 3, 14 | Enable `SimpleLoggerAdvisor` for request/response diagnostics | §17.3, §17.14 |
| 4.4 | 4 | `LanguageDetectionService` | §17.4 |
| 4.5 | 5 | `TranslationQueryTransformerConfig` — triggers only when detected language ≠ KB retrieval language | §14, §17.5 |
| 4.6 | 6 | `RewriteQueryTransformerConfig` — triggers only for unclear/dependent follow-ups; preserves intent, adds no facts | §14, §17.6 |
| 4.7 | 7 | `MultiQueryExpanderConfig` — triggers only for broad/ambiguous queries; no new symptoms/entitlements | §14, §17.7 |
| 4.8 | 8 | `VectorStoreDocumentRetrieverConfig` + `MetadataFilterBuilder` — filter by `status=active`, current `version`, `audience` (from `RoleContext`), `vehicle_family`, similarity ≥ 0.50 | §5, §17.8 |
| 4.9 | 9 | `ContextualQueryAugmenterConfig` — `allowEmptyContext=false`; raises `NoMatchingContextException` on empty context | §17.9 |
| 4.10 | 10 | `RetrievalAugmentationConfig` — wires stages 5–9 into `RetrievalAugmentationAdvisor` | §17.10 |
| 4.11 | 12 | `CitationValidationAdvisor` — blocks unsupported policy claims | §17.12 |

**Exit criteria:** A query run through the pipeline produces either a cited answer or the fixed escalation JSON, with every applicable stage logged in order.

## 6. Phase 5 — Multi-ChatClient Configuration

| Task | Description | Trace |
|---|---|---|
| 5.1 | Define `ChatClientConfig` registering `QueryTransformChatClient` bean (smaller/faster deployment) | architecture.md §12, §13 |
| 5.2 | Define `ChatClientConfig` registering `AnswerChatClient` bean (primary GPT-4+ deployment) | architecture.md §12, §13 |
| 5.3 | Route `TranslationQueryTransformer`, `RewriteQueryTransformer`, `MultiQueryExpander` exclusively through `QueryTransformChatClient` | architecture.md §12 |
| 5.4 | Route the final LLM generation stage exclusively through `AnswerChatClient` | architecture.md §12 |
| 5.5 | Log `chatClient` + `chatModel` + `stage` + correlation ID on every model invocation | architecture.md §12, §16 |

**Exit criteria:** Logs demonstrably show two distinct `ChatClient`/`ChatModel` pairs handling different stages of the same request (satisfies SRS §4.7).

## 7. Phase 6 — Chat API & Contracts

| Task | Description | Trace |
|---|---|---|
| 6.1 | Implement `ChatRequest` DTO (`sessionId`, `role`, `vehicleFamily`, `query`, `language`) | architecture.md §15.1 |
| 6.2 | Implement `ChatResponse` DTO (success + escalation shapes) | architecture.md §15.2, §15.3 |
| 6.3 | Implement `ErrorResponse` DTO and `GlobalExceptionHandler` | architecture.md §15.4 |
| 6.4 | Implement `ChatController`: `POST /ai/autocare/chat/sync` (synchronous) | architecture.md §8 |
| 6.5 | Implement `ChatController`: `POST /ai/autocare/chat/stream` (streaming) | architecture.md §8 |
| 6.6 | Map dependent-service outages (vector store, LLM, embedding) to controlled `503` error body | architecture.md §15.4, NFR-005 |

**Exit criteria:** Both endpoints return schema-conformant JSON for success, escalation and error cases; Swagger UI documents all three shapes.

## 8. Phase 7 — Audit & Observability

| Task | Description | Trace |
|---|---|---|
| 7.1 | Create `ConversationAuditRecord` JPA entity matching the audit table column list exactly | architecture.md §16 |
| 7.2 | Create `ConversationAuditRepository` (append-only; no update/delete operations exposed) | architecture.md §16 |
| 7.3 | Populate all audit columns per turn (role, redacted query, transformations applied, retrieved doc codes, chat clients used, citation/escalation status, correlation ID) | architecture.md §16 |
| 7.4 | Add correlation ID generation/propagation across advisor chain, logs and API response | architecture.md §15, §16 |
| 7.5 | Verify no raw PII/VIN/registration ever appears in any audit column or log line | architecture.md §7, §16 |

**Exit criteria:** Every chat turn produces exactly one append-only audit row with full traceability and zero raw sensitive data.

## 9. Phase 8 — Testing & Quality Gates

| Task | Description | Trace |
|---|---|---|
| 8.1 | Unit tests for `PiiVehicleRedactionAdvisor`, `MetadataFilterBuilder`, `CitationValidationAdvisor` | NFR-001, NFR-002, NFR-003 |
| 8.2 | Unit tests for conditional transformer triggers (translate/rewrite/expand) per architecture.md §14 | architecture.md §14 |
| 8.3 | Integration tests covering the full pipeline order (architecture.md §17) for a grounded answer and for an empty-context escalation | architecture.md §17, §4.2 |
| 8.4 | Role-based retrieval tests: Owner cannot retrieve KB-AUTO-007 or admin-only KB-AUTO-008 sections; Advisor can retrieve KB-AUTO-007; Admin can retrieve everything | KB-METADATA.md §4 |
| 8.5 | Run the KB-METADATA.md §6 retrieval test-question set (2 per document, 16 total) against the live pipeline before activating any KB version | KB-METADATA.md §6, NFR-010 |
| 8.6 | Verify audit table population and redaction on a sample of real conversation turns | architecture.md §16 |
| 8.7 | Achieve ≥ 80% code coverage on service implementation | SRS §4.7 |

**Exit criteria:** All test suites pass; coverage threshold met; retrieval test suite signed off per NFR-010 before any KB version activation.

## 10. Phase 9 — Documentation & Demo Readiness

| Task | Description | Trace |
|---|---|---|
| 9.1 | Finalize Springdoc/OpenAPI documentation for `/ai/autocare/chat/sync`, `/ai/autocare/chat/stream`, `/kb/ingest` | architecture.md §8 |
| 9.2 | Prepare a traceable demo showing `QueryTransformChatClient` and `AnswerChatClient` both invoked within one session, with logs identifying each stage | SRS §4.7 |
| 9.3 | Document KB version-control procedure (activate validated version, retire superseded version) | KB-AUTO-008 §4, KB-METADATA.md §5 |
| 9.4 | Final review against SRS Acceptance Criteria (multilingual handling, context-aware rewriting, query expansion, no-match fallback, audit logging, application logging, role-based retrieval, KB version control) | SRS §7 |

**Exit criteria:** All SRS Acceptance Criteria demonstrably satisfied; documentation complete; ready for capstone submission/demo.

## 11. Traceability Summary

| Requirement Group | Covered By Phase(s) |
|---|---|
| FR-001–FR-012 (owner-facing guidance) | 2, 4, 6 |
| FR-013, FR-014 (citations, empty-context fallback) | 4, 6 |
| FR-015 (multilingual) | 4 (task 4.5) |
| FR-016–FR-024 (advisor operations, audience restriction) | 2 (task 2.5), 3, 4 |
| FR-025–FR-029 (system/RAG components) | 4, 5 |
| FR-030 (KB ingestion/versioning) | 2 |
| NFR-001–NFR-010 | 3, 4, 5, 7, 8 |
| SRS §4.7 (multi-ChatModel demo) | 5, 9 |
