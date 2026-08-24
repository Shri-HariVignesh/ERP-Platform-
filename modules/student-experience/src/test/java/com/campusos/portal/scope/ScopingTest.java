package com.campusos.portal.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.engine.IllegalTransitionException;
import com.campusos.portal.service.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AREA 2 — scoping. Both halves: a different tenant, and a different student inside the
 * SAME tenant (which is the leak a tenant-only check would miss).
 */
@Tag("security")
class ScopingTest extends EngineTestBase {

    @Test
    @DisplayName("a student in another tenant cannot read the request")
    void crossTenantReadReturnsEmpty() {
        Fixture hari = fixture("hari");
        Fixture meera = fixture("meera");

        Request his = machine.create(hari.scope(), RequestType.DOCUMENT, document(DocType.BONAFIDE));

        assertThat(requests.findByIdAndTenantIdAndStudentId(
                his.id, meera.scope().tenantId(), meera.scope().studentId()))
                .as("empty result, not an exception leak").isEmpty();

        assertThat(requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc(
                meera.scope().tenantId(), meera.scope().studentId()))
                .as("her tracker shows only her own rows").isEmpty();

        assertThat(requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc(
                hari.scope().tenantId(), hari.scope().studentId())).hasSize(1);
    }

    @Test
    @DisplayName("a student in another tenant cannot act on the request")
    void crossTenantTransitionIsBlocked() {
        Fixture hari = fixture("hari");
        Fixture meera = fixture("meera");

        Request his = machine.create(hari.scope(), RequestType.DOCUMENT, document(DocType.TRANSCRIPT));
        assertThat(his.state).isEqualTo(RequestState.APPROVAL);

        assertThatThrownBy(() -> machine.transition(
                meera.scope(), his.id, Event.APPROVE, Actor.OFFICE, null))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("not visible in this tenant+student scope");

        assertThat(requests.findByIdAndTenantIdAndStudentId(
                his.id, hari.scope().tenantId(), hari.scope().studentId()).orElseThrow().state)
                .as("the blocked attempt changed nothing").isEqualTo(RequestState.APPROVAL);
    }

    @Test
    @DisplayName("a classmate in the SAME tenant is still blocked — the scope is tenant AND student")
    void sameTenantDifferentStudentIsBlocked() {
        Fixture owner = fixture("owner");

        Student classmate = new Student();
        classmate.id = owner.scope().studentId() + "_mate";
        classmate.tenantId = owner.scope().tenantId();
        classmate.rollNo = owner.student().rollNo + "B";
        classmate.name = "Classmate";
        classmate.email = "mate@example.edu";
        classmate.program = owner.student().program;
        classmate.department = owner.student().department;
        classmate.semester = 5;
        classmate.section = "A";
        classmate.active = true;
        classmate.advisorName = "Advisor";
        classmate.hodName = "HOD";
        students.save(classmate);
        Scope mate = new Scope(classmate.tenantId, classmate.id);

        Request his = machine.create(owner.scope(), RequestType.DOCUMENT, document(DocType.TRANSCRIPT));

        assertThat(requests.findByIdAndTenantIdAndStudentId(his.id, mate.tenantId(), mate.studentId()))
                .as("same tenant, different student — still invisible").isEmpty();
        assertThatThrownBy(() -> machine.transition(mate, his.id, Event.APPROVE, Actor.OFFICE, null))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("not visible in this tenant+student scope");
    }

    @Test
    @DisplayName("side-effect rows are scoped too — attendance, records, documents, history")
    void sideEffectRowsAreScoped() {
        Fixture hari = fixture("hari");
        Fixture meera = fixture("meera");

        Request leave = machine.create(hari.scope(), RequestType.LEAVE,
                leave(hari.futureClassDays().get(0), hari.futureClassDays().get(1)));
        leave = machine.transition(hari.scope(), leave.id, Event.APPROVE, Actor.FACULTY, null);
        leave = machine.transition(hari.scope(), leave.id, Event.APPROVE, Actor.HOD, null);

        Request intern = machine.create(hari.scope(), RequestType.INTERNSHIP, internship("cert.pdf"));
        intern = machine.transition(hari.scope(), intern.id, Event.VERIFY, Actor.FACULTY, null);
        machine.transition(hari.scope(), intern.id, Event.APPROVE, Actor.INSTITUTION, null);

        assertThat(academic.findByTenantIdAndStudentIdOrderByRecordedAtDesc(
                meera.scope().tenantId(), meera.scope().studentId())).isEmpty();
        assertThat(documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(
                meera.scope().tenantId(), meera.scope().studentId())).isEmpty();
        assertThat(histories.findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(
                leave.id, meera.scope().tenantId(), meera.scope().studentId()))
                .as("even the audit trail is scoped").isEmpty();
        assertThat(attendance.findByTenantIdAndStudentId(
                meera.scope().tenantId(), meera.scope().studentId()))
                .noneMatch(a -> a.status == AttendanceRecord.Status.APPROVED_LEAVE);

        assertThat(histories.findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(
                leave.id, hari.scope().tenantId(), hari.scope().studentId())).isNotEmpty();
    }

    @Test
    @DisplayName("a Scope cannot be constructed with half the key")
    void scopeRequiresBothIds() {
        assertThatThrownBy(() -> new Scope("t_x", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("studentId missing");
        assertThatThrownBy(() -> new Scope("", "s_x"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tenantId missing");
    }
}
