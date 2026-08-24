package com.campusos.portal.engine;

import static com.campusos.portal.domain.Actor.*;
import static com.campusos.portal.domain.Event.*;
import static com.campusos.portal.domain.RequestState.*;
import static com.campusos.portal.domain.SideEffect.*;

import com.campusos.portal.domain.DocType;
import com.campusos.portal.domain.RequestState;
import com.campusos.portal.domain.RequestType;
import com.campusos.portal.payload.DocumentPayload;
import java.util.List;
import java.util.Map;

/**
 * THE FROZEN STATE CONTRACT, transcribed. This file is the single source of truth for
 * what may happen to a Request. Nothing else in the codebase decides a next state.
 */
public final class TransitionMatrix {

    private TransitionMatrix() {}

    /* ------------------------------ LEAVE ------------------------------ */

    private static final WorkflowSpec LEAVE = new WorkflowSpec(
            "Leave",
            SUBMITTED,
            List.of(
                    new WorkflowSpec.Step(SUBMITTED, "Submitted"),
                    new WorkflowSpec.Step(FACULTY_PENDING, "Faculty review"),
                    new WorkflowSpec.Step(HOD_PENDING, "HOD approval"),
                    new WorkflowSpec.Step(ATTENDANCE_MUTATED, "Attendance updated"),
                    new WorkflowSpec.Step(NOTIFIED, "Approved")),
            Map.of(REJECTED, new WorkflowSpec.OffPath("Rejected", "danger")),
            Map.of(NOTIFIED, "success", REJECTED, "danger"),
            Map.of(
                    SUBMITTED, "Validating",
                    FACULTY_PENDING, "With faculty",
                    HOD_PENDING, "With HOD",
                    ATTENDANCE_MUTATED, "Updating attendance",
                    NOTIFIED, "Approved",
                    REJECTED, "Rejected"),
            Map.of(
                    SUBMITTED, List.of(
                            Transition.of(AUTO_VALIDATE, SYSTEM, FACULTY_PENDING, List.of(VALIDATE_LEAVE))),
                    FACULTY_PENDING, List.of(
                            Transition.human(APPROVE, FACULTY, HOD_PENDING, List.of(NOTIFY),
                                    false, "Faculty approves", "success"),
                            Transition.human(REJECT, FACULTY, REJECTED, List.of(NOTIFY_REJECTION),
                                    true, "Faculty rejects", "danger")),
                    HOD_PENDING, List.of(
                            Transition.human(APPROVE, HOD, ATTENDANCE_MUTATED, List.of(),
                                    false, "HOD approves", "success"),
                            Transition.human(REJECT, HOD, REJECTED, List.of(NOTIFY_REJECTION),
                                    true, "HOD rejects", "danger")),
                    ATTENDANCE_MUTATED, List.of(
                            Transition.of(APPLY, SYSTEM, NOTIFIED, List.of(MUTATE_ATTENDANCE, NOTIFY))),
                    NOTIFIED, List.of(),
                    REJECTED, List.of()));

    /* ---------------------------- INTERNSHIP ---------------------------- */

    private static final WorkflowSpec INTERNSHIP = new WorkflowSpec(
            "Internship",
            SUBMITTED,
            List.of(
                    new WorkflowSpec.Step(SUBMITTED, "Submitted"),
                    new WorkflowSpec.Step(FACULTY_VERIFICATION, "Faculty verification"),
                    new WorkflowSpec.Step(INSTITUTION_APPROVAL, "Institution approval"),
                    new WorkflowSpec.Step(ACADEMIC_RECORD_MUTATED, "Added to record"),
                    new WorkflowSpec.Step(VERIFICATION_ID_GENERATED, "Verified")),
            Map.of(
                    RETURNED, new WorkflowSpec.OffPath("Returned for correction", "action"),
                    REJECTED, new WorkflowSpec.OffPath("Rejected", "danger")),
            Map.of(VERIFICATION_ID_GENERATED, "success", REJECTED, "danger"),
            Map.of(
                    SUBMITTED, "Checking certificate",
                    FACULTY_VERIFICATION, "With faculty",
                    INSTITUTION_APPROVAL, "With institution",
                    ACADEMIC_RECORD_MUTATED, "Writing to record",
                    VERIFICATION_ID_GENERATED, "Verified",
                    RETURNED, "Returned to you",
                    REJECTED, "Rejected"),
            Map.of(
                    SUBMITTED, List.of(
                            Transition.of(AUTO_CHECK, SYSTEM, FACULTY_VERIFICATION, List.of(CHECK_CERTIFICATE))),
                    FACULTY_VERIFICATION, List.of(
                            Transition.human(VERIFY, FACULTY, INSTITUTION_APPROVAL, List.of(),
                                    false, "Faculty verifies certificate", "success"),
                            Transition.human(RETURN, FACULTY, RETURNED, List.of(NOTIFY_REJECTION),
                                    true, "Faculty returns for correction", "action")),
                    INSTITUTION_APPROVAL, List.of(
                            Transition.human(APPROVE, INSTITUTION, ACADEMIC_RECORD_MUTATED, List.of(),
                                    false, "Institution approves", "success"),
                            Transition.human(REJECT, INSTITUTION, REJECTED, List.of(NOTIFY_REJECTION),
                                    true, "Institution rejects", "danger")),
                    ACADEMIC_RECORD_MUTATED, List.of(
                            Transition.of(WRITE_RECORD, SYSTEM, VERIFICATION_ID_GENERATED,
                                    List.of(WRITE_ACADEMIC_RECORD, GENERATE_VERIFICATION_ID,
                                            PUBLISH_CERT_TO_DOCUMENTS, NOTIFY))),
                    RETURNED, List.of(
                            Transition.human(RESUBMIT, STUDENT, SUBMITTED, List.of(),
                                            false, "Fix & resubmit", "action")
                                    .withInput("Corrected certificate filename")),
                    VERIFICATION_ID_GENERATED, List.of(),
                    REJECTED, List.of()));

    /* ----------------------------- DOCUMENTS ----------------------------- */

    private static final WorkflowSpec DOCUMENT = new WorkflowSpec(
            "Document",
            SUBMITTED,
            List.of(
                    new WorkflowSpec.Step(SUBMITTED, "Submitted"),
                    new WorkflowSpec.Step(APPROVAL, "Office approval"),
                    new WorkflowSpec.Step(DOCUMENT_GENERATED, "Ready")),
            Map.of(REJECTED, new WorkflowSpec.OffPath("Rejected", "danger")),
            Map.of(DOCUMENT_GENERATED, "success", REJECTED, "danger"),
            Map.of(
                    SUBMITTED, "Running eligibility",
                    APPROVAL, "With office",
                    DOCUMENT_GENERATED, "Ready",
                    REJECTED, "Rejected"),
            Map.of(
                    SUBMITTED, List.of(
                            // Digital Razor: the system can answer "is this an active student?" itself.
                            Transition.of(AUTO_ELIGIBILITY, SYSTEM, DOCUMENT_GENERATED,
                                            List.of(RUN_ELIGIBILITY, GENERATE_DOCUMENT))
                                    .guardedBy(TransitionMatrix::autoEligible),
                            Transition.of(AUTO_ELIGIBILITY, SYSTEM, APPROVAL, List.of(RUN_ELIGIBILITY))
                                    .guardedBy(c -> !autoEligible(c))),
                    APPROVAL, List.of(
                            Transition.human(APPROVE, OFFICE, DOCUMENT_GENERATED, List.of(GENERATE_DOCUMENT),
                                    false, "Office approves", "success"),
                            Transition.human(REJECT, OFFICE, REJECTED, List.of(NOTIFY_REJECTION),
                                    true, "Office rejects", "danger")),
                    DOCUMENT_GENERATED, List.of(),
                    REJECTED, List.of()));

    /** The auto-eligibility razor: BONAFIDE self-approves for an active student. */
    public static boolean autoEligible(TransitionContext c) {
        if (!(c.payload() instanceof DocumentPayload d)) return false;
        return d.docType == DocType.BONAFIDE && c.student().active;
    }

    public static String eligibilityReason(DocumentPayload d, boolean active) {
        if (d.docType != DocType.BONAFIDE) {
            return d.docType.display() + " carries an institutional attestation — routed to the office.";
        }
        if (!active) return "Enrolment is not active — routed to the office.";
        return "Bonafide only asks 'is this an active student?'. The system already knows. "
                + "Auto-generated with zero human touches.";
    }

    /* ----------------------------- GRIEVANCE ----------------------------- */

    private static final WorkflowSpec GRIEVANCE = new WorkflowSpec(
            "Grievance",
            SUBMITTED,
            List.of(
                    new WorkflowSpec.Step(SUBMITTED, "Submitted"),
                    new WorkflowSpec.Step(ASSIGNED, "Assigned"),
                    new WorkflowSpec.Step(UNDER_REVIEW, "Under review"),
                    new WorkflowSpec.Step(RESOLVED, "Resolved")),
            Map.of(),
            Map.of(RESOLVED, "success"),
            Map.of(
                    SUBMITTED, "Routing",
                    ASSIGNED, "Assigned to desk",
                    UNDER_REVIEW, "Under review",
                    RESOLVED, "Resolved"),
            Map.of(
                    SUBMITTED, List.of(
                            Transition.of(AUTO_ASSIGN, SYSTEM, ASSIGNED, List.of())),
                    ASSIGNED, List.of(
                            Transition.human(START_REVIEW, FACULTY, UNDER_REVIEW, List.of(),
                                    false, "Desk starts review", "pending")),
                    UNDER_REVIEW, List.of(
                            Transition.human(RESOLVE, FACULTY, RESOLVED, List.of(NOTIFY),
                                    true, "Desk resolves", "success")),
                    RESOLVED, List.of()));

    private static final Map<RequestType, WorkflowSpec> SPECS = Map.of(
            RequestType.LEAVE, LEAVE,
            RequestType.INTERNSHIP, INTERNSHIP,
            RequestType.DOCUMENT, DOCUMENT,
            RequestType.GRIEVANCE, GRIEVANCE);

    public static WorkflowSpec spec(RequestType type) {
        return SPECS.get(type);
    }

    public static RequestState initial(RequestType type) {
        return SPECS.get(type).initial();
    }
}
