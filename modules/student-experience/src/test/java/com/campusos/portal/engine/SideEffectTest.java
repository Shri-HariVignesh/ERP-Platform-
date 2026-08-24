package com.campusos.portal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusos.portal.domain.*;
import com.campusos.portal.payload.DocumentPayload;
import com.campusos.portal.payload.InternshipPayload;
import com.campusos.portal.payload.LeavePayload;
import com.campusos.portal.service.AttendanceMath;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AREA 3 — the side effects. Not state names: the actual rows the workflow rewrote.
 */
class SideEffectTest extends EngineTestBase {

    /* -------------------------------- LEAVE -------------------------------- */

    @Test
    @DisplayName("HOD approval rewrites the attendance rows, recomputes the percentage and spends leave balance")
    void leaveApprovalMutatesAttendance() {
        Fixture f = fixture("leavefx");
        LocalDate from = f.futureClassDays().get(0);
        LocalDate to = f.futureClassDays().get(1);

        double before = AttendanceMath.pct(
                attendance.findByTenantIdAndStudentId(f.scope().tenantId(), f.scope().studentId()));
        int balanceBefore = students.findByIdAndTenantId(
                f.scope().studentId(), f.scope().tenantId()).orElseThrow().leaveBalance;

        Request r = machine.create(f.scope(), RequestType.LEAVE, leave(from, to));
        assertThat(r.state).isEqualTo(RequestState.FACULTY_PENDING);

        // nothing has been mutated yet — approval is what writes attendance
        assertThat(attendance.findByTenantIdAndStudentIdAndDateBetween(
                f.scope().tenantId(), f.scope().studentId(), from, to))
                .allMatch(a -> a.status == AttendanceRecord.Status.SCHEDULED);

        r = machine.transition(f.scope(), r.id, Event.APPROVE, Actor.FACULTY, null);
        r = machine.transition(f.scope(), r.id, Event.APPROVE, Actor.HOD, null);
        assertThat(r.state).isEqualTo(RequestState.NOTIFIED);
        final String requestId = r.id;

        // 1. the rows themselves changed, and point back at the request that changed them
        List<AttendanceRecord> window = attendance.findByTenantIdAndStudentIdAndDateBetween(
                f.scope().tenantId(), f.scope().studentId(), from, to);
        assertThat(window).hasSize(2);
        assertThat(window).allMatch(a -> a.status == AttendanceRecord.Status.APPROVED_LEAVE);
        assertThat(window).allMatch(a -> requestId.equals(a.sourceRequestId));

        // 2. the percentage really moved, and the payload records both sides of the move
        double after = AttendanceMath.pct(
                attendance.findByTenantIdAndStudentId(f.scope().tenantId(), f.scope().studentId()));
        assertThat(after).isGreaterThan(before);

        LeavePayload p = (LeavePayload) payloadOf(f, r);
        assertThat(p.sys.attendanceBefore).isEqualTo(before);
        assertThat(p.sys.attendanceAfter).isEqualTo(after);
        assertThat(p.sys.datesMutated).containsExactly(from.toString(), to.toString());

        // 3. leave balance was spent
        int balanceAfter = students.findByIdAndTenantId(
                f.scope().studentId(), f.scope().tenantId()).orElseThrow().leaveBalance;
        assertThat(balanceAfter).isEqualTo(balanceBefore - 2);

        // 4. the effect is auditable, not just observable
        assertThat(historyOf(f, r))
                .anyMatch(h -> h.effects.contains(SideEffect.MUTATE_ATTENDANCE.name())
                        && h.effectLog.contains("AttendanceRecord mutated"));
    }

    @Test
    @DisplayName("a rejected leave mutates no attendance row at all")
    void rejectedLeaveMutatesNothing() {
        Fixture f = fixture("leavereject");
        LocalDate from = f.futureClassDays().get(0);
        LocalDate to = f.futureClassDays().get(1);

        Request r = machine.create(f.scope(), RequestType.LEAVE, leave(from, to));
        r = machine.transition(f.scope(), r.id, Event.REJECT, Actor.FACULTY, "clashes with internals");

        assertThat(r.state).isEqualTo(RequestState.REJECTED);
        assertThat(attendance.findByTenantIdAndStudentIdAndDateBetween(
                f.scope().tenantId(), f.scope().studentId(), from, to))
                .allMatch(a -> a.status == AttendanceRecord.Status.SCHEDULED);
        assertThat(students.findByIdAndTenantId(f.scope().studentId(), f.scope().tenantId())
                .orElseThrow().leaveBalance).isEqualTo(12);
    }

    /* ------------------------------ DOCUMENTS ------------------------------ */

    @Test
    @DisplayName("BONAFIDE reaches DOCUMENT_GENERATED with zero human transitions")
    void bonafideSelfApproves() {
        Fixture f = fixture("bonafide");
        Request r = machine.create(f.scope(), RequestType.DOCUMENT, document(DocType.BONAFIDE));

        assertThat(r.state).isEqualTo(RequestState.DOCUMENT_GENERATED);

        var trail = historyOf(f, r);
        assertThat(trail).extracting(h -> h.actor)
                .as("only the submitting student and the system ever touched it")
                .containsOnly(Actor.STUDENT, Actor.SYSTEM);
        assertThat(trail).noneMatch(h -> h.toState == RequestState.APPROVAL);
        assertThat(trail.stream().filter(h -> h.actor == Actor.SYSTEM).count())
                .as("exactly one system transition: SUBMITTED -> DOCUMENT_GENERATED").isEqualTo(1);

        DocumentPayload p = (DocumentPayload) payloadOf(f, r);
        assertThat(p.sys.autoEligible).isTrue();
        assertThat(p.sys.serialNo).isNotNull();
        assertThat(p.sys.verifyId).isNotNull();
        assertThat(p.sys.documentId).isNotNull();

        assertThat(documents.findByIdAndTenantIdAndStudentId(
                p.sys.documentId, f.scope().tenantId(), f.scope().studentId()))
                .get().satisfies(d -> {
                    assertThat(d.docType).isEqualTo(DocType.BONAFIDE);
                    assertThat(d.html).contains(f.student().name);
                });
        assertThat(verifications.findByVerifyId(p.sys.verifyId)).isPresent();
    }

    @Test
    @DisplayName("a non-auto-eligible document stops at APPROVAL and generates nothing yet")
    void transcriptRoutesToOffice() {
        Fixture f = fixture("transcript");
        Request r = machine.create(f.scope(), RequestType.DOCUMENT, document(DocType.TRANSCRIPT));

        assertThat(r.state).isEqualTo(RequestState.APPROVAL);

        DocumentPayload p = (DocumentPayload) payloadOf(f, r);
        assertThat(p.sys.autoEligible).isFalse();
        assertThat(p.sys.eligibilityReason).contains("routed to the office");
        assertThat(p.sys.serialNo).isNull();
        assertThat(p.sys.documentId).isNull();
        assertThat(documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(
                f.scope().tenantId(), f.scope().studentId())).isEmpty();

        r = machine.transition(f.scope(), r.id, Event.APPROVE, Actor.OFFICE, null);
        assertThat(r.state).isEqualTo(RequestState.DOCUMENT_GENERATED);
        assertThat(((DocumentPayload) payloadOf(f, r)).sys.serialNo).isNotNull();
    }

    /* ------------------------------ INTERNSHIP ------------------------------ */

    @Test
    @DisplayName("a verified internship writes the academic record, mints a verifyId and publishes a certificate")
    void internshipWritesRecordAndVerification() {
        Fixture f = fixture("internfx");
        Request r = machine.create(f.scope(), RequestType.INTERNSHIP, internship("cert.pdf"));
        assertThat(r.state).isEqualTo(RequestState.FACULTY_VERIFICATION);
        assertThat(academic.findByTenantIdAndStudentIdOrderByRecordedAtDesc(
                f.scope().tenantId(), f.scope().studentId())).isEmpty();

        r = machine.transition(f.scope(), r.id, Event.VERIFY, Actor.FACULTY, null);
        r = machine.transition(f.scope(), r.id, Event.APPROVE, Actor.INSTITUTION, null);
        assertThat(r.state).isEqualTo(RequestState.VERIFICATION_ID_GENERATED);
        final String requestId = r.id;

        InternshipPayload p = (InternshipPayload) payloadOf(f, r);
        assertThat(p.sys.verifyId).isNotNull();
        assertThat(p.sys.credits).isNotNull().isPositive();
        assertThat(p.sys.documentSerial).isNotNull();

        // 1. AcademicRecord row, carrying the verifyId
        var records = academic.findByTenantIdAndStudentIdOrderByRecordedAtDesc(
                f.scope().tenantId(), f.scope().studentId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).kind).isEqualTo("INTERNSHIP");
        assertThat(records.get(0).credits).isEqualTo(p.sys.credits);
        assertThat(records.get(0).verifyId).isEqualTo(p.sys.verifyId);
        assertThat(records.get(0).sourceRequestId).isEqualTo(requestId);

        // 2. the QR target resolves
        assertThat(verifications.findByVerifyId(p.sys.verifyId)).get().satisfies(v -> {
            assertThat(v.kind).isEqualTo("INTERNSHIP");
            assertThat(v.studentId).isEqualTo(f.scope().studentId());
            assertThat(v.sourceRequestId).isEqualTo(requestId);
        });

        // 3. the certificate was published into Documents
        assertThat(documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(
                f.scope().tenantId(), f.scope().studentId()))
                .singleElement().satisfies(d -> {
                    assertThat(d.docType).isEqualTo(DocType.INTERNSHIP_VERIFICATION);
                    assertThat(d.serialNo).isEqualTo(p.sys.documentSerial);
                    assertThat(d.verifyId).isEqualTo(p.sys.verifyId);
                });
    }

    @Test
    @DisplayName("a rejected internship writes no record and mints no verification")
    void rejectedInternshipWritesNothing() {
        Fixture f = fixture("internreject");
        Request r = machine.create(f.scope(), RequestType.INTERNSHIP, internship("cert.pdf"));
        r = machine.transition(f.scope(), r.id, Event.VERIFY, Actor.FACULTY, null);
        r = machine.transition(f.scope(), r.id, Event.REJECT, Actor.INSTITUTION, "not an approved partner");

        assertThat(r.state).isEqualTo(RequestState.REJECTED);
        assertThat(academic.findByTenantIdAndStudentIdOrderByRecordedAtDesc(
                f.scope().tenantId(), f.scope().studentId())).isEmpty();
        assertThat(documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(
                f.scope().tenantId(), f.scope().studentId())).isEmpty();
        assertThat(((InternshipPayload) payloadOf(f, r)).sys.verifyId).isNull();
    }
}
