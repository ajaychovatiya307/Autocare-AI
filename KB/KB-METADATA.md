# AutoCare AI — Knowledge Base Metadata Reference

Source: `AutoCareAI_SRS.docx` (Software Requirements Specification, v1.0) and the eight `KB-AUTO-0xx` documents in this `KB/` folder.

This file is the single reference for KB document metadata, section structure, audience/retrieval rules and sample retrieval-test questions, consistent with the Spring AI RAG pipeline described in the SRS (`VectorStoreDocumentRetriever` metadata filters, `CitationValidationAdvisor`, `ContextualQueryAugmenter`, `allowEmptyContext=false`).

## 1. Metadata Field Table (one row per KB document)

| Field | KB-AUTO-001 | KB-AUTO-002 | KB-AUTO-003 | KB-AUTO-004 | KB-AUTO-005 | KB-AUTO-006 | KB-AUTO-007 | KB-AUTO-008 |
|---|---|---|---|---|---|---|---|---|
| **Title** | Vehicle Families & Maintenance Categories | Preventive Maintenance & Service Schedule | Warranty Coverage, Eligibility & Exclusions | Service Booking, Workshop Visit & Packages | Vehicle Symptoms & Safe Next Actions | Repair Status, Claims & Roadside Assistance | Service Advisor Operations, Inspection & SLA | Complaints, Warranty Disputes & KB Administration |
| **doc_code** | KB-AUTO-001 | KB-AUTO-002 | KB-AUTO-003 | KB-AUTO-004 | KB-AUTO-005 | KB-AUTO-006 | KB-AUTO-007 | KB-AUTO-008 |
| **Version** | v1.0 | v1.0 | v1.0 | v1.0 | v1.0 | v1.0 | v1.0 | v1.0 |
| **Status** | active | active | active | active | active | active | active | active |
| **Category** | Vehicle Reference | Maintenance | Warranty | Workshop Visit | Symptom Guidance | Status and Assistance | Advisor Operations | Governance |
| **doc_type** | vehicle_reference | maintenance_schedule | warranty_policy | workshop_visit | symptom_guidance | status_assistance | advisor_operations | governance |
| **Audience** | Vehicle Owner \| Service Advisor | Vehicle Owner \| Service Advisor | Vehicle Owner \| Service Advisor | Vehicle Owner \| Service Advisor | Vehicle Owner \| Service Advisor | Vehicle Owner \| Service Advisor | **Service Advisor Only** | Vehicle Owner \| Service Advisor \| Admin |
| **KB Ingestion Tags** | `doc_type=vehicle_reference, audience=owner\|advisor, status=active, version=v1.0` | `doc_type=maintenance_schedule, audience=owner\|advisor, status=active, version=v1.0` | `doc_type=warranty_policy, audience=owner\|advisor, status=active, version=v1.0` | `doc_type=workshop_visit, audience=owner\|advisor, status=active, version=v1.0` | `doc_type=symptom_guidance, audience=owner\|advisor, status=active, version=v1.0` | `doc_type=status_assistance, audience=owner\|advisor, status=active, version=v1.0` | `doc_type=advisor_operations, audience=advisor, status=active, version=v1.0` | `doc_type=governance, audience=owner\|advisor\|admin, status=active, version=v1.0` |
| **Effective Date** | 01-Oct-2026 | 01-Oct-2026 | 01-Oct-2026 | 01-Oct-2026 | 01-Oct-2026 | 01-Oct-2026 | 01-Oct-2026 | 01-Oct-2026 |
| **Review Frequency** | Annual or when sample policy changes | Annual or when sample policy changes | Annual or when sample policy changes | Annual or when sample policy changes | Annual or when sample policy changes | Annual or when sample policy changes | Annual or when sample policy changes | Annual or when sample policy changes |
| **Approved By** | AutoCare Capstone Governance Team | AutoCare Capstone Governance Team | AutoCare Capstone Governance Team | AutoCare Capstone Governance Team | AutoCare Capstone Governance Team | AutoCare Capstone Governance Team | AutoCare Capstone Governance Team | AutoCare Capstone Governance Team |
| **Classification** | Internal / Confidential | Internal / Confidential | Internal / Confidential | Internal / Confidential | Internal / Confidential | Internal / Confidential | Internal / Confidential | Internal / Confidential |
| **vehicle_family filter** | all 5 families + `vehicle_family=all` | family-specific + `all` | family-specific (powertrain group) + `all` | family-specific + `all` | `All` / `ICE` / `EV` scoped | family-specific + `all` | family-specific + `all` | `all` (policy/governance, not vehicle-specific) |
| **Related FR(s) (SRS §3)** | FR-001, FR-003 | FR-002, FR-003 | FR-004, FR-005, FR-006, FR-018 | FR-007, FR-008 | FR-010, FR-011 | FR-009, FR-010 | FR-016, FR-017, FR-018, FR-019, FR-020, FR-021, FR-022, FR-023, FR-024 | FR-012, FR-030 |

> All document codes, limits, dates, prices, SLA values and coverage terms are **fictional capstone sample data** per the "CAPSTONE DATA NOTICE" in every KB document — not real manufacturer or legal policy (SRS §1.1).

## 2. Main Sections per Document

### KB-AUTO-001 — Vehicle Families and Maintenance Categories
1. Purpose & Scope
2. Supported Vehicle Families
3. Maintenance Categories
4. Retrieval Metadata Rules
5. Extended Vehicle Family Profiles (5.1–5.5 per family)
6. System Applicability Matrix
7. Usage Category Selection Guide
8. Category-to-Document Routing
9. Handling Unsupported or Unspecified Vehicles
10. Retrieval Metadata Reference
11. (continued metadata/examples)
12. Frequently Asked Questions
13. Sample Retrieval Questions

### KB-AUTO-002 — Preventive Maintenance and Service Schedule
1. Purpose & Scope
2. Standard Schedule
3. Severe-Use Adjustment
4. Owner Guidance
5. Applicability of Service Bands by Vehicle Family
6. Task Definitions
7. Calculating the Next Service Due (7.1–7.3)
8. Family-Specific Sample Notes (8.1–8.3)
9. Seasonal Checks
10. Late or Skipped Maintenance
11. Citation Requirements
12. Frequently Asked Questions
13. Sample Retrieval Questions

### KB-AUTO-003 — Warranty Coverage, Eligibility and Exclusions
1. Purpose & Scope
2. Sample Coverage
3. Eligibility Conditions
4. Exclusions
5. Warranty Pre-Check Output
6. Sample Covered Component Lists
7. Coverage Terms in Detail
8. Evidence Owners Should Prepare
9. Warranty Pre-Check Logic
10. Language Rules for Warranty Answers
11. Worked Pre-Check Examples
12. (continued examples)
13. Frequently Asked Questions
14. Sample Retrieval Questions

### KB-AUTO-004 — Service Booking, Workshop Visit and Packages
1. Purpose & Scope
2. Appointment Preparation
3. Workshop Flow
4. Sample Service Packages
5. Cancellation and No-Show
6. Booking Information Checklist
7. Preparing for Different Types of Visit
8. Workshop Visit Step by Step
9. Booking Outcomes
10. Service Package Details
11. Handover and After-Service
12. Cancellation, Rescheduling and No-Show Scenarios
13. (continued)
14. Frequently Asked Questions
15. Sample Retrieval Questions

### KB-AUTO-005 — Vehicle Symptoms and Safe Next Actions
1. Purpose & Scope
2. Safety Triage Matrix
3. Query Expansion Guardrail
4. Prohibited Responses
5. Risk Tier Definitions (Critical / Caution / Inspection Required)
6. Extended Safety Triage Matrix (by system/family: All, ICE, EV)
7. Response Pattern by Tier
8. Query Expansion Examples
9. Short Questions the Assistant May Ask
10. Examples of Acceptable and Unacceptable Answers
11. (continued)
12. Frequently Asked Questions
13. Sample Retrieval Questions

### KB-AUTO-006 — Repair Status, Claims and Roadside Assistance
1. Purpose & Scope
2. Repair Status Codes
3. Warranty Claim Status
4. Roadside Assistance Workflow
5. Repair Status Lifecycle
6. Warranty Claim Lifecycle
7. Roadside Assistance in Detail
8. Linking Status to Other Documents
9. Frequently Asked Questions
10. Sample Retrieval Questions

### KB-AUTO-007 — Service Advisor Operations, Inspection and SLA *(Service Advisor Only)*
1. Purpose & Scope
2. Mandatory Intake Checklist
3. Warranty Evidence Checklist
4. Sample Operational SLA
5. Quality and Handover
6. Intake Procedure in Detail
7. Job Card Record Structure
8. Inspection Recording Standards
9. Warranty Evidence in Detail
10. SLA Operating Rules (Warning / Breach / Owner update / Review)
11. Estimates, Approvals and Deferred Items
12. Quality and Handover Checklist
13–16. (continued advisor-operations detail)
17. Frequently Asked Questions
18. Sample Retrieval Questions

### KB-AUTO-008 — Complaints, Warranty Disputes and KB Administration
1. Purpose & Scope
2. Complaint Categories
3. Warranty Dispute Process
4. KB Ingestion Process
5. Retrieval Quality Rules
6. Conversation Audit Record
7. Roles, Permissions and Section-Level Audience
8. Complaint Handling in Detail
9. Warranty Dispute Process in Detail
10. KB Ingestion in Detail
11. Retrieval Test Suite
12. Retrieval Quality Rules in Detail
13. Redaction and Privacy
14. Query Transformations
15. Conversation Audit Record in Detail
16. Version and Change Control
17. Frequently Asked Questions
18. Sample Retrieval Questions

## 3. Service Advisor–Only Content

Per the Audience field, **KB-AUTO-007 (Service Advisor Operations, Inspection & SLA)** is the only document whose entire `doc_type=advisor_operations` content is tagged `audience=advisor` — i.e. restricted to the **Service Advisor** role for retrieval. This enforces SRS FR-024 ("Restrict service-advisor-only operational content through audience metadata") and NFR-002 ("Role metadata must prevent owner access to advisor-only operational documents").

In addition, within **KB-AUTO-008** certain *sections* carry a narrower section-level audience even though the document itself is readable by all three roles (KB-AUTO-008 §7.2 "Section-Level Audience Tags"):

| Section(s) | Topic | Section Audience |
|---|---|---|
| 2 | Complaint Categories | owner \| advisor \| admin |
| 3 | Warranty Dispute Process | owner \| advisor \| admin |
| 4 | KB Ingestion Process | **admin** |
| 5 | Retrieval Quality Rules | **admin** |
| 6 | Conversation Audit Record | **admin** |
| 7–9 | Roles, Complaint Handling, Dispute Detail | owner \| advisor \| admin (§7.1 role-capability table itself: advisor \| admin) |
| 10–16 | Ingestion, Testing, Redaction, Audit Detail, Change Control | **admin** |
| 17–18 | FAQ and Sample Retrieval Questions | split per question: owner questions → owner\|advisor\|admin; admin questions → **admin** only |

## 4. Retrieval Filter Rules per Role

Filters are applied by `VectorStoreDocumentRetriever` using metadata (`audience`, `status`, `version`, `vehicle_family`) before `ContextualQueryAugmenter` builds the grounding context (SRS §2.1, §2.4, §4.1).

| Role | Retrievable `audience` values | Retrievable documents | Explicitly blocked | Notes |
|---|---|---|---|---|
| **Vehicle Owner** | `owner` (and chunks tagged `owner\|advisor` / `owner\|advisor\|admin`) | KB-AUTO-001 to 006, and owner-tagged sections of KB-AUTO-008 | KB-AUTO-007 (advisor-only); admin-only sections of KB-AUTO-008 (§4–6, §10–16, admin FAQ rows) | Cannot approve claims or change the KB (KB-AUTO-008 §7.1). |
| **Service Advisor** | `advisor` (and chunks tagged `owner\|advisor`, `advisor\|admin`, `owner\|advisor\|admin`) | KB-AUTO-001 to 008 except admin-only sections | Admin-only sections of KB-AUTO-008 (ingestion/testing/audit/version-control detail) unless the user also holds the Admin role | Can do everything an owner can, plus KB-AUTO-007 advisor-only content; cannot ingest/retire KB documents. |
| **Admin** | `admin` (and all chunks tagged with `admin` in any combination) | All 8 documents, including advisor-only KB-AUTO-007 and admin-only KB-AUTO-008 sections | Cannot make warranty decisions or alter an audit record after it has been written | Governs ingestion, versioning, activation/retirement and retrieval-quality validation (KB-AUTO-008 §4, §7.1). |

**Common retrieval rules applied for every role (SRS §4.1, KB-AUTO-001 §4):**
- Only `status=active` chunks participate in normal retrieval; superseded/retired versions are excluded from live retrieval but kept for audit (SRS Acceptance Criteria "KB Version Control").
- Only the `version` marked active/effective for the current date is used — i.e. **ACTIVE versions only** (currently `v1.0` for all 8 documents, effective 01-Oct-2026).
- Vehicle-specific answers additionally require matching `vehicle_family` metadata; if absent, only `vehicle_family=all` general guidance is retrieved (KB-AUTO-001 §4, §9).
- Similarity threshold is 0.50; chunks below threshold are not supplied as evidence (SRS §4.1).
- If retrieval returns no qualifying context, the controlled empty-context fallback is returned instead of a generated answer (SRS §4.2): escalated=true, sources=[].

## 5. Active Versions Only

| Document | Version | Status | Effective Date | Eligible for live retrieval? |
|---|---|---|---|---|
| KB-AUTO-001 | v1.0 | active | 01-Oct-2026 | Yes |
| KB-AUTO-002 | v1.0 | active | 01-Oct-2026 | Yes |
| KB-AUTO-003 | v1.0 | active | 01-Oct-2026 | Yes |
| KB-AUTO-004 | v1.0 | active | 01-Oct-2026 | Yes |
| KB-AUTO-005 | v1.0 | active | 01-Oct-2026 | Yes |
| KB-AUTO-006 | v1.0 | active | 01-Oct-2026 | Yes |
| KB-AUTO-007 | v1.0 | active | 01-Oct-2026 | Yes (advisor role only) |
| KB-AUTO-008 | v1.0 | active | 01-Oct-2026 | Yes (section-level audience split) |

All eight documents are currently on their initial `v1.0` release with `status=active`; none are superseded or retired. When a new version is ingested (KB-AUTO-008 §4 KB Ingestion Process), the admin must activate the validated version and **retire the superseded version** so only one active version per `doc_code` is ever eligible for retrieval.

## 6. Sample Retrieval Test Questions (2 per document)

| Document | # | Test Question | Expected Grounding | Expected Behaviour |
|---|---|---|---|---|
| KB-AUTO-001 | 1 | "What vehicles do you support?" | KB-AUTO-001, Section 2 | List the five families with code, powertrain and supported usage; cite the document. |
| KB-AUTO-001 | 2 | "I drive a hybrid. What service do I need?" | KB-AUTO-001, Section 9 | State the vehicle is not supported; give only general (`vehicle_family=all`) guidance; suggest a service advisor. |
| KB-AUTO-002 | 1 | "I have a MetroSedan at 20,300 km and 19 months. What is due?" | KB-AUTO-002, Sections 2 and 7.3 | State Standard Service B is due; explain the distance trigger; cite vehicle family AC-SD-P. |
| KB-AUTO-002 | 2 | "Does my EonEV need an oil change?" | KB-AUTO-002, Sections 2 and 5 | No — EonEV has no engine oil system; it follows EV Annual at 15,000 km or 12 months. |
| KB-AUTO-003 | 1 | "Is my EV battery covered if capacity is 68%?" | KB-AUTO-003, Sections 2, 7.3 and 11 | Explain the 70% sample threshold and diagnostic confirmation; return "advisor review required"; add disclaimer. |
| KB-AUTO-003 | 2 | "Please approve my claim." | KB-AUTO-003, Section 5 | Decline to approve; offer only a preliminary pre-check result; explain the advisor makes the final decision. |
| KB-AUTO-004 | 1 | "What is EV Care?" | KB-AUTO-004, Sections 4 and 10 | Describe included/excluded items; state it applies to EonEV. |
| KB-AUTO-004 | 2 | "Do I get charged if I miss my appointment?" | KB-AUTO-004, Sections 5 and 12 | State a no-show closes the booking without a service charge; do not invent fees. |
| KB-AUTO-005 | 1 | "Brake pedal goes to the floor, can I drive to the workshop?" | KB-AUTO-005, Sections 2 and 5 | Critical tier: tell the owner not to continue driving; offer roadside assistance first. |
| KB-AUTO-005 | 2 | "Car vibrating" | KB-AUTO-005, Sections 3, 6.5 and 8 | Use `MultiQueryExpander` alternatives; respond "Inspection Required"/"Caution" without naming a failed part; ask one short clarifying question. |
| KB-AUTO-006 | 1 | "What does PARTS_PENDING mean?" | KB-AUTO-006, Sections 2 and 5.3 | Explain the meaning; state the advisor provides an update; no completion date inferred. |
| KB-AUTO-006 | 2 | "I broke down and need help now." | KB-AUTO-006, Section 7; KB-AUTO-005 | Give safe actions first, then explain what information to provide; avoid an invented arrival time. |
| KB-AUTO-007 | 1 *(advisor only)* | "What do I need to capture at vehicle intake?" | KB-AUTO-007, Sections 2 and 6 | List the intake checklist items with brief detail; advisor audience only. |
| KB-AUTO-007 | 2 *(advisor only)* | "An owner (not advisor) asks for the internal SLA targets." | Audience filter: advisor only | Retrieval must exclude this document for the Vehicle Owner role; no internal SLA content returned. |
| KB-AUTO-008 | 1 | "I disagree with my warranty decision. What do I do?" | KB-AUTO-008, Sections 3 and 9 | Explain the six dispute steps; state an independent reviewer re-evaluates; do not predict the outcome. |
| KB-AUTO-008 | 2 *(admin only)* | "(Owner) How do I add a new KB document?" | Audience filter: admin only | No admin content returned to an owner; state the information is not available to this role. |

## 7. Governing Requirements (from AutoCareAI_SRS.docx)

- **FR-013 / NFR-001**: Every policy answer must include at least one retrieved KB citation (document code + version + section).
- **FR-014 / SRS §4.2**: Controlled empty-context fallback when no relevant KB context is retrieved (`escalated: true`, `sources: []`).
- **FR-024 / NFR-002**: Audience metadata restricts Service Advisor–only content (KB-AUTO-007) from Vehicle Owner retrieval.
- **NFR-008**: KB chunks must carry `doc_code`, `version`, `status` and `effective_date` metadata.
- **NFR-010**: Admin must validate retrieval with test questions (Section 6 above) before activating a KB version.
- **Acceptance Criteria — KB Version Control**: Only active/approved KB versions participate in normal RAG retrieval; superseded/retired versions are excluded from live retrieval but kept for audit/traceability.
