package com.campusos.portal.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusos.portal.domain.*;
import com.campusos.portal.payload.InternshipPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AREA 4 — the paths that are not the happy path.
 * Leave rejects into a dead end; Internship returns into a loop that can still complete.
 */
class RejectionPathTest extends EngineTestBase {

    @Test
    @DisplayName("Leave rejected by Faculty is terminal, and carries the reason")
    void leaveRejectedByFacultyIsTerminal() {
        Fixture f = fixture("rejfac");
        Request r = machine.create(f.scope(), RequestType.LEAVE,
                leave(f.futureClassDays().get(0), f.futureClassDays().get(1)));

        r = machine.transition(f.scope(), r.id, Event.REJECT, Actor.FACULTY, "clashes with internals");

        assertThat(r.state).isEqualTo(RequestState.REJECTED);
        assertThat(TransitionMatrix.spec(RequestType.LEAVE).isTerminal(RequestState.REJECTED)).isTrue();
        assertThat(TransitionMatrix.spec(RequestType.LEAVE).from(RequestState.REJECTED)).isEmpty();

        var last = historyOf(f, r).get(historyOf(f, r).size() - 1);
        assertThat(last.actor).isEqualTo(Actor.FACULTY);
        assertThat(last.note).isEqualTo("clashes with internals");
        assertThat(last.effects).contains(SideEffect.NOTIFY_REJECTION.name());

        final String id = r.id;
        assertThatThrownBy(() -> machine.transition(f.scope(), id, Event.APPROVE, Actor.HOD, null))
                .isInstanceOf(IllegalTransitionException.class).hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("Leave rejected by the HOD is terminal too, from the later state")
    void leaveRejectedByHodIsTerminal() {
        Fixture f = fixture("rejhod");
        Request r = machine.create(f.scope(), RequestType.LEAVE,
                leave(f.futureClassDays().get(0), f.futureClassDays().get(1)));
        r = machine.transition(f.scope(), r.id, Event.APPROVE, Actor.FACULTY, null);
        assertThat(r.state).isEqualTo(RequestState.HOD_PENDING);

        r = machine.transition(f.scope(), r.id, Event.REJECT, Actor.HOD, "department quota exhausted");
        assertThat(r.state).isEqualTo(RequestState.REJECTED);

        final String id = r.id;
        assertThatThrownBy(() -> machine.transition(f.scope(), id, Event.APPROVE, Actor.HOD, null))
                .isInstanceOf(IllegalTransitionException.class).hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("a rejection with no reason is refused — REJECT declares requiresNote")
    void rejectionRequiresAReason() {
        Fixture f = fixture("rejnote");
        Request r = machine.create(f.scope(), RequestType.LEAVE,
                leave(f.futureClassDays().get(0), f.futureClassDays().get(1)));
        final String id = r.id;

        assertThatThrownBy(() -> machine.transition(f.scope(), id, Event.REJECT, Actor.FACULTY, "  "))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("requires a reason");

        assertThat(requests.findByIdAndTenantIdAndStudentId(
                id, f.scope().tenantId(), f.scope().studentId()).orElseThrow().state)
                .isEqualTo(RequestState.FACULTY_PENDING);
    }

    @Test
    @DisplayName("Internship RETURNED resubmits to SUBMITTED, re-runs the auto-check, and can still complete")
    void internshipReturnedThenCompletes() {
        Fixture f = fixture("returned");
        Request r = machine.create(f.scope(), RequestType.INTERNSHIP, internship("blurry-scan.pdf"));

        r = machine.transition(f.scope(), r.id, Event.RETURN, Actor.FACULTY, "scan is unreadable");
        assertThat(r.state).isEqualTo(RequestState.RETURNED);
        assertThat(TransitionMatrix.spec(RequestType.INTERNSHIP).isTerminal(RequestState.RETURNED))
                .as("RETURNED is a return state, not a dead end").isFalse();

        // the student edge, with the payload patch the portal applies
        r = machine.transition(f.scope(), r.id, Event.RESUBMIT, Actor.STUDENT, "rescanned", p -> {
            InternshipPayload ip = (InternshipPayload) p;
            ip.certificateRef = new InternshipPayload.CertificateRef("clean-scan.pdf", "application/pdf", 312);
            ip.sys.returnCount++;
        });

        // RESUBMIT lands on SUBMITTED, then autopilot re-runs the certificate check
        assertThat(historyOf(f, r)).anyMatch(h -> h.fromState == RequestState.RETURNED
                && h.toState == RequestState.SUBMITTED && h.actor == Actor.STUDENT);
        assertThat(r.state).isEqualTo(RequestState.FACULTY_VERIFICATION);

        InternshipPayload after = (InternshipPayload) payloadOf(f, r);
        assertThat(after.certificateRef.filename).isEqualTo("clean-scan.pdf");
        assertThat(after.sys.returnCount).isEqualTo(1);
        assertThat(after.sys.certificateCheck).contains("clean-scan.pdf");

        // and the corrected submission runs all the way through
        r = machine.transition(f.scope(), r.id, Event.VERIFY, Actor.FACULTY, null);
        r = machine.transition(f.scope(), r.id, Event.APPROVE, Actor.INSTITUTION, null);
        assertThat(r.state).isEqualTo(RequestState.VERIFICATION_ID_GENERATED);
        assertThat(((InternshipPayload) payloadOf(f, r)).sys.verifyId).isNotNull();
        assertThat(academic.findByTenantIdAndStudentIdOrderByRecordedAtDesc(
                f.scope().tenantId(), f.scope().studentId())).hasSize(1);
    }
}
