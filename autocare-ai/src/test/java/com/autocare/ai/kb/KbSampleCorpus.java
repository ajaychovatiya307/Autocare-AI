package com.autocare.ai.kb;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;

/**
 * One representative chunk per KB-METADATA.md §6 sample retrieval question (16 total, 2 per
 * {@code KB-AUTO-0xx} document), tagged with the metadata {@code VectorStoreDocumentRetrieverConfig}
 * filters on: {@code doc_code}, {@code version}, {@code status}, {@code vehicle_family}, and a boolean
 * {@code audience_<role>} flag per role permitted to retrieve it (KB-METADATA.md §4).
 */
final class KbSampleCorpus {

    private static final String VERSION = "v1.0";
    private static final String STATUS = "ACTIVE";

    private KbSampleCorpus() {
    }

    static List<Document> documents() {
        return List.of(
                chunk(
                        "KB-AUTO-001",
                        "Supported vehicle families and codes\n"
                                + "Code | Vehicle Family | Powertrain | Supported Usage\n"
                                + "AC-HB-P | CityHatch P | Petrol | Normal | Severe\n"
                                + "AC-SD-P | MetroSedan P | Petrol | Normal | Severe\n"
                                + "AC-SV-P | TrailSUV P | Petrol | Normal | Severe\n"
                                + "AC-SV-D | TrailSUV D | Diesel | Normal | Severe\n"
                                + "AC-EV-1 | EonEV | Battery Electric | Normal | High-Usage",
                        "2",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-001",
                        "Unsupported hybrid vehicle service guidance\n"
                                + "Hybrid vehicles are not supported in AutoCare's sample service schedules. Only "
                                + "general vehicle-family guidance applies; contact a service advisor for help.",
                        "9",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-002",
                        "MetroSedan 20,000 km service due calculation\n"
                                + "For MetroSedan P (AC-SD-P), Standard Service B is due at 20,000 km or 24 months, "
                                + "whichever comes first. At 20,300 km and 19 months, the 20,000 km distance "
                                + "trigger is reached, so Standard Service B is due now.",
                        "7.3",
                        "AC-SD-P",
                        true, true, true),
                chunk(
                        "KB-AUTO-002",
                        "EonEV engine oil change and EV Annual service\n"
                                + "EonEV (AC-EV-1) has no engine oil system and does not need an engine-oil change. "
                                + "The EV Annual service is due at 15,000 km or 12 months, whichever comes first.",
                        "5",
                        "AC-EV-1",
                        true, true, true),
                chunk(
                        "KB-AUTO-003",
                        "EV traction battery warranty 70% diagnostic pre-check\n"
                                + "For the EV Traction Battery (AC-EV-1), capacity below the 70% sample threshold may "
                                + "qualify only when diagnostic testing confirms the capacity loss and the "
                                + "coverage eligibility conditions are met. A dashboard reading alone is not "
                                + "confirmation. Result: advisor review required; this is preliminary guidance only.",
                        "11",
                        "AC-EV-1",
                        true, true, true),
                chunk(
                        "KB-AUTO-003",
                        "Warranty claim approval and preliminary pre-check\n"
                                + "AutoCare AI cannot approve or reject a warranty claim. It provides only a preliminary "
                                + "pre-check; the authorized service advisor makes the final claim decision.",
                        "5",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-004",
                        "EV Care package inclusions and exclusions\n"
                                + "EV Care is available for AC-EV-1. Included tasks: charging-port inspection, diagnostic "
                                + "scan, tyre inspection, brake inspection and cabin-filter review. Typically "
                                + "not included: high-voltage repair and traction-battery replacement. Cabin-filter "
                                + "replacement and any repair need separate authorization.",
                        "10",
                        "AC-EV-1",
                        true, true, true),
                chunk(
                        "KB-AUTO-004",
                        "Appointment no-show and service charge\n"
                                + "A no-show for an appointment closes the booking without a service charge. Rebooking is required. Do not "
                                + "invent a fee.",
                        "5",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-005",
                        "Brake pedal failure driving safety Critical\n"
                                + "A brake pedal that loses resistance or reaches the floor is Critical. Stop in a safe place, do not continue "
                                + "driving, and request roadside assistance to the workshop.",
                        "2",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-005",
                        "Car vibrating Inspection Required guidance\n"
                                + "For a car vibrating or general vehicle vibration, use the Inspection Required / Caution guidance. Do not "
                                + "name a failed component or diagnose the cause. Ask one short question about "
                                + "when the vibration occurs (speed, braking, acceleration, or idle).",
                        "6.5",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-006",
                        "PARTS_PENDING status definition\n"
                                + "PARTS_PENDING means a required part is not currently allocated. The service advisor "
                                + "provides an update from the service system. Do not infer an arrival or "
                                + "completion date.",
                        "5.3",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-006",
                        "Breakdown roadside assistance steps\n"
                                + "For a breakdown requiring roadside assistance: switch on hazard lights, steer to a safe place if possible, stop, "
                                + "and request assistance. Provide your location and vehicle details. Do not "
                                + "promise an arrival time.",
                        "7",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-007",
                        "Vehicle intake checklist\n"
                                + "At vehicle intake, capture the supported vehicle family, odometer reading, agreed usage "
                                + "category (Normal, Severe or High-Usage EV), owner concerns in their own words, "
                                + "visible warning indicators and prior repair or modification disclosures.",
                        "2",
                        "all",
                        false, true, true),
                chunk(
                        "KB-AUTO-007",
                        "Advisor internal SLA targets\n"
                                + "Internal operational SLA targets and breach thresholds are restricted to service "
                                + "advisors. They must not be returned to a vehicle owner.",
                        "4",
                        "all",
                        false, true, true),
                chunk(
                        "KB-AUTO-008",
                        "Warranty decision dispute process\n"
                                + "For an owner who disagrees with a warranty decision: confirm the claim is recorded as DECLINED; quote the claim "
                                + "reference and documented decision reason; submit missing or corrective "
                                + "evidence; an independent reviewer re-evaluates evidence against the active "
                                + "policy; record the outcome, reviewer, timestamp and cited policy section; "
                                + "escalate unresolved interpretation disputes to the sample governance panel. "
                                + "Do not predict the outcome.",
                        "9",
                        "all",
                        true, true, true),
                chunk(
                        "KB-AUTO-008",
                        "Admin Knowledge Base document ingestion\n"
                                + "Knowledge Base document ingestion and version administration are restricted to the "
                                + "admin role.",
                        "4",
                        "all",
                        false, false, true));
    }

    private static Document chunk(
            String docCode,
            String text,
            String section,
            String vehicleFamily,
            boolean owner,
            boolean advisor,
            boolean admin) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(
                        "doc_code", docCode,
                        "version", VERSION,
                        "status", STATUS,
                        "effective_date", "2026-10-01",
                        "section", section,
                        "vehicle_family", vehicleFamily,
                        "audience_owner", String.valueOf(owner),
                        "audience_advisor", String.valueOf(advisor),
                        "audience_admin", String.valueOf(admin)))
                .build();
    }
}
