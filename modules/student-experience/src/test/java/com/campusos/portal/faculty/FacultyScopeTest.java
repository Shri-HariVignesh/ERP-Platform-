package com.campusos.portal.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.security.StaffPrincipal;
import com.campusos.portal.service.*;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * GATE 2 OF THE DEFINITION OF DONE — authorization.
 *
 * Role authz, department scope, tenant isolation, and the property the whole module turns on:
 * the Actor is derived from the identity, so a role you do not hold is not a role you can fire.
 */
@Tag("security")
class FacultyScopeTest extends EngineTestBase {

    @Autowired StaffScopeResolver staffScopes;
    @Autowired FacultyService faculty;

    private StaffScope scopeOf(String username) {
        StaffPrincipal p = principal(username);
        Authentication auth = new UsernamePasswordAuthenticationToken(p, "n/a", p.getAuthorities());
        return staffScopes.current(auth);
    }

    private String freshLeave(String tenantId, String studentId) {
        var future = attendance.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(a -> a.status == AttendanceRecord.Status.SCHEDULED)
                .map(a -> a.date).sorted().toList();
        return machine.create(new Scope(tenantId, studentId), RequestType.LEAVE,
                leave(future.get(0), future.get(1))).id;
    }

    /* ------------------------------- role -> actor ------------------------------- */

    @Test
    @DisplayName("each role maps to exactly one Actor, and never to STUDENT or SYSTEM")
    void roleActorMapIsExact() {
        assertThat(StaffRole.FACULTY.actor()).isEqualTo(Actor.FACULTY);
        assertThat(StaffRole.HOD.actor()).isEqualTo(Actor.HOD);
        assertThat(StaffRole.INSTITUTION.actor()).isEqualTo(Actor.INSTITUTION);
        assertThat(StaffRole.OFFICE.actor()).isEqualTo(Actor.OFFICE);

        for (StaffRole r : StaffRole.values()) {
            assertThat(r.actor())
                    .as("%s must not be able to impersonate the student or the automation", r)
                    .isNotIn(Actor.STUDENT, Actor.SYSTEM);
        }
        assertThat(scopeOf("anjali.menon").actors()).containsExactly(Actor.FACULTY);
    }

    @Test
    @DisplayName("a FACULTY cannot fire an HOD event — the stage is not theirs")
    void facultyCannotFireHodEvent() {
        StaffScope anjali = scopeOf("anjali.menon");
        String id = freshLeave("t_snit", "s_divya");

        // Move it to HOD_PENDING legitimately, as the faculty member she is.
        faculty.act(anjali, id, Event.APPROVE, null);
        assertThat(requests.findByIdAndTenantIdAndStudentId(id, "t_snit", "s_divya")
                .orElseThrow().state).isEqualTo(RequestState.HOD_PENDING);

        // The same person, the same request, the next stage — which belongs to the HOD.
        assertThatThrownBy(() -> faculty.act(anjali, id, Event.APPROVE, null))
                .isInstanceOf(StaffAccessException.class)
                .hasMessageContaining("no role of yours");

        assertThat(requests.findByIdAndTenantIdAndStudentId(id, "t_snit", "s_divya")
                .orElseThrow().state)
                .as("the refused attempt changed nothing").isEqualTo(RequestState.HOD_PENDING);
    }

    @Test
    @DisplayName("a FACULTY cannot fire an OFFICE or INSTITUTION event either")
    void facultyCannotFireOfficeOrInstitutionEvents() {
        StaffScope anjali = scopeOf("anjali.menon");

        Request doc = machine.create(new Scope("t_snit", "s_divya"), RequestType.DOCUMENT,
                document(DocType.TRANSCRIPT));
        assertThat(doc.state).isEqualTo(RequestState.APPROVAL);   // waiting on OFFICE
        assertThatThrownBy(() -> faculty.act(anjali, doc.id, Event.APPROVE, null))
                .isInstanceOf(StaffAccessException.class);

        Request intern = machine.create(new Scope("t_snit", "s_divya"), RequestType.INTERNSHIP,
                internship("cert.pdf"));
        faculty.act(anjali, intern.id, Event.VERIFY, null);        // hers to do
        assertThat(requests.findByIdAndTenantIdAndStudentId(intern.id, "t_snit", "s_divya")
                .orElseThrow().state).isEqualTo(RequestState.INSTITUTION_APPROVAL);
        assertThatThrownBy(() -> faculty.act(anjali, intern.id, Event.APPROVE, null))
                .as("INSTITUTION_APPROVAL is not a faculty stage")
                .isInstanceOf(StaffAccessException.class);
    }

    @Test
    @DisplayName("a staff member holding two roles acts across both of them")
    void multiRoleStaffActsAcrossEveryRoleTheyHold() {
        StaffScope krishna = scopeOf("krishnakumar");      // HOD + FACULTY
        assertThat(krishna.actors()).containsExactlyInAnyOrder(Actor.HOD, Actor.FACULTY);

        String id = freshLeave("t_snit", "s_divya");

        // FACULTY stage, then HOD stage — one login, two roles, two legitimate moves.
        faculty.act(krishna, id, Event.APPROVE, null);
        assertThat(requests.findByIdAndTenantIdAndStudentId(id, "t_snit", "s_divya")
                .orElseThrow().state).isEqualTo(RequestState.HOD_PENDING);

        faculty.act(krishna, id, Event.APPROVE, null);
        assertThat(requests.findByIdAndTenantIdAndStudentId(id, "t_snit", "s_divya")
                .orElseThrow().state)
                .as("the leave workflow runs on to completion")
                .isEqualTo(RequestState.NOTIFIED);

        List<RequestHistory> trail = histories
                .findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(id, "t_snit", "s_divya");
        assertThat(trail).extracting(h -> h.actor)
                .as("the trail records WHICH role made each move, not just who was logged in")
                .contains(Actor.FACULTY, Actor.HOD);
    }

    /* ---------------------------- department scope ---------------------------- */

    @Test
    @DisplayName("a FACULTY sees only their own class — not another department in the same tenant")
    void facultySeesOnlyTheirOwnDepartment() {
        StaffScope anjali = scopeOf("anjali.menon");        // CSE, Sem 5, Section A

        assertThat(staffScopes.roster(anjali))
                .as("the roster is her class, and nothing else in the institution")
                .extracting(s -> s.id)
                .contains("s_hari", "s_divya")
                .doesNotContain("s_nikhil")     // same tenant, ECE
                .doesNotContain("s_meera");     // other tenant

        Student nikhil = students.findByIdAndTenantId("s_nikhil", "t_snit").orElseThrow();
        assertThat(anjali.canSee(nikhil)).isFalse();
        assertThatThrownBy(() -> staffScopes.studentInScope(anjali, "s_nikhil"))
                .isInstanceOf(StaffAccessException.class);
    }

    @Test
    @DisplayName("the inbox itself is department-scoped — another department's leave never appears")
    void inboxExcludesOtherDepartments() {
        StaffScope anjali = scopeOf("anjali.menon");
        String hers = freshLeave("t_snit", "s_divya");
        String theirs = freshLeave("t_snit", "s_nikhil");    // ECE

        assertThat(faculty.inbox(anjali)).extracting(t -> t.card().id())
                .contains(hers)
                .doesNotContain(theirs);

        StaffScope babu = scopeOf("suresh.babu");            // ECE faculty, same tenant
        assertThat(faculty.inbox(babu)).extracting(t -> t.card().id())
                .contains(theirs)
                .doesNotContain(hers);
    }

    @Test
    @DisplayName("a FACULTY cannot act on another department's request even knowing its id")
    void facultyCannotActOnAnotherDepartment() {
        StaffScope anjali = scopeOf("anjali.menon");
        String theirs = freshLeave("t_snit", "s_nikhil");

        assertThatThrownBy(() -> faculty.act(anjali, theirs, Event.APPROVE, null))
                .isInstanceOf(StaffAccessException.class)
                .hasMessageContaining("not in scope");

        assertThat(requests.findByIdAndTenantIdAndStudentId(theirs, "t_snit", "s_nikhil")
                .orElseThrow().state).isEqualTo(RequestState.FACULTY_PENDING);
    }

    @Test
    @DisplayName("an HOD sees their whole department but still not another one")
    void hodSeesTheDepartmentNotTheInstitution() {
        StaffScope krishna = scopeOf("krishnakumar");        // HOD of CSE
        assertThat(staffScopes.roster(krishna)).extracting(s -> s.id)
                .contains("s_hari", "s_divya")
                .doesNotContain("s_nikhil");
    }

    @Test
    @DisplayName("a tenant-wide desk sees the tenant, and only the tenant")
    void officeSeesTheTenantOnly() {
        StaffScope office = scopeOf("exam.office");
        assertThat(staffScopes.roster(office)).extracting(s -> s.id)
                .contains("s_hari", "s_divya", "s_nikhil")
                .doesNotContain("s_meera");
    }

    /* ------------------------------ tenant isolation ------------------------------ */

    @Test
    @DisplayName("cross-tenant staff can neither read nor act, and the page leaks nothing")
    void crossTenantStaffIsBlocked() throws Exception {
        StaffScope latha = scopeOf("latha.iyer");            // ACE
        assertThat(staffScopes.roster(latha)).extracting(s -> s.id)
                .containsExactly("s_meera");

        String snitRequest = freshLeave("t_snit", "s_divya");
        assertThatThrownBy(() -> faculty.act(latha, snitRequest, Event.APPROVE, null))
                .isInstanceOf(StaffAccessException.class);
        assertThatThrownBy(() -> staffScopes.studentInScope(latha, "s_hari"))
                .isInstanceOf(StaffAccessException.class);

        String page = mvc.perform(get("/faculty/students").with(user(principal("latha.iyer"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(page)
                .as("no SNIT student may appear on an ACE staff member's page")
                .doesNotContain("Hari Prasad").doesNotContain("Divya Rajan")
                .doesNotContain("Nikhil Varma").doesNotContain("SNIT21");

        assertThat(mvc.perform(get("/faculty/students/s_hari").with(user(principal("latha.iyer"))))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("a refusal says nothing about whether the thing exists")
    void refusalIsNotAnOracle() throws Exception {
        String real = mvc.perform(get("/faculty/students/s_hari")
                        .with(user(principal("latha.iyer"))))
                .andReturn().getResponse().getContentAsString();
        String imaginary = mvc.perform(get("/faculty/students/s_does_not_exist")
                        .with(user(principal("latha.iyer"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(real)
                .as("a real student and an invented one must be indistinguishable")
                .isEqualTo(imaginary);
        assertThat(real).doesNotContain("Hari").doesNotContain("com.campusos");
    }

    /* ------------------------- the surface follows the role ------------------------- */

    @Test
    @DisplayName("the action buttons offered are the ones this principal may actually fire")
    void offeredActionsMatchTheRolesHeld() {
        String id = freshLeave("t_snit", "s_divya");

        StaffScope anjali = scopeOf("anjali.menon");
        var atFacultyStage = faculty.inbox(anjali).stream()
                .filter(t -> t.card().id().equals(id)).findFirst().orElseThrow();
        assertThat(atFacultyStage.actions()).extracting(a -> a.event())
                .containsExactlyInAnyOrder("APPROVE", "REJECT");

        faculty.act(anjali, id, Event.APPROVE, null);       // now HOD_PENDING

        assertThat(faculty.inbox(anjali)).extracting(t -> t.card().id())
                .as("an HOD stage is not in a faculty member's inbox at all")
                .doesNotContain(id);
        assertThat(faculty.inbox(scopeOf("krishnakumar"))).extracting(t -> t.card().id())
                .as("it is in the HOD's")
                .contains(id);
    }

    @Test
    @DisplayName("a desk with no teaching assignment gets no academic-authoring surface")
    void tenantWideDeskCannotAuthorAcademics() throws Exception {
        StaffScope office = scopeOf("exam.office");
        assertThat(office.authors()).isFalse();
        assertThat(office.classes()).isEmpty();

        String page = mvc.perform(get("/faculty").with(user(principal("exam.office"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(page)
                .as("no attendance or marks tab for someone who teaches nothing")
                .doesNotContain("/faculty/attendance")
                .doesNotContain("/faculty/marks");
    }
}
