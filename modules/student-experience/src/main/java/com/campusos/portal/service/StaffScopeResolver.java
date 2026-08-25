package com.campusos.portal.service;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.Transition;
import com.campusos.portal.engine.TransitionMatrix;
import com.campusos.portal.repo.StaffUserRepository;
import com.campusos.portal.repo.StudentRepository;
import com.campusos.portal.repo.TeachingAssignmentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.campusos.portal.security.StaffPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * PRINCIPAL -> SCOPE -> ACTOR. The one place a staff identity becomes authority.
 *
 * Nothing here reads a request parameter. The tenantId, staffId, roles and teaching
 * assignments all come from the authenticated principal and the database, which is what makes
 * "the client never supplies the actor" true rather than merely intended.
 */
@Component
public class StaffScopeResolver {

    private final StaffUserRepository staff;
    private final TeachingAssignmentRepository assignments;
    private final StudentRepository students;

    public StaffScopeResolver(StaffUserRepository staff, TeachingAssignmentRepository assignments,
                              StudentRepository students) {
        this.staff = staff;
        this.assignments = assignments;
        this.students = students;
    }

    /* -------------------------------- the scope -------------------------------- */

    public StaffScope current(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof StaffPrincipal p)) {
            throw new StaffAccessException("not authenticated as staff");
        }
        StaffUser u = staff.findByIdAndTenantId(p.staffId(), p.tenantId())
                .orElseThrow(() -> new StaffAccessException("staff identity no longer valid"));
        if (!u.active) throw new StaffAccessException("staff identity is inactive");

        // Re-read per request: revoking a teaching assignment takes effect on the next click.
        List<TeachingAssignment> mine = assignments
                .findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc(u.tenantId, u.id);
        return new StaffScope(u.tenantId, u.id, u.name, u.department, u.roles, mine);
    }

    /* --------------------------------- rosters --------------------------------- */

    /**
     * REQUEST breadth, as one de-duplicated roster. Every branch queries by tenantId plus the
     * dimension that bounds that role, so there is no path that reads another tenant's rows.
     */
    public List<Student> roster(StaffScope scope) {
        Map<String, Student> out = new LinkedHashMap<>();

        if (scope.hasRole(StaffRole.INSTITUTION) || scope.hasRole(StaffRole.OFFICE)) {
            for (Student s : students.findByTenantIdOrderByRollNoAsc(scope.tenantId())) {
                out.put(s.id, s);
            }
        }
        if (scope.hasRole(StaffRole.HOD) && scope.department() != null) {
            for (Student s : students.findByTenantIdAndDepartmentOrderByRollNoAsc(
                    scope.tenantId(), scope.department())) {
                out.put(s.id, s);
            }
        }
        if (scope.hasRole(StaffRole.FACULTY)) {
            for (ClassKey c : scope.classes()) {
                for (Student s : students
                        .findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc(
                                scope.tenantId(), c.department(), c.semester(), c.section())) {
                    out.put(s.id, s);
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    public List<String> rosterIds(StaffScope scope) {
        return roster(scope).stream().map(s -> s.id).toList();
    }

    /** The class roster, for attendance and marks. Requires the staff member to teach it. */
    public List<Student> classRoster(StaffScope scope, ClassKey c) {
        if (!scope.teaches(c)) throw new StaffAccessException("not a class you teach");
        return students.findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc(
                scope.tenantId(), c.department(), c.semester(), c.section());
    }

    /** Resolves a studentId to a Student this staff member is actually allowed to see. */
    public Student studentInScope(StaffScope scope, String studentId) {
        Student s = students.findByIdAndTenantId(studentId, scope.tenantId())
                .orElseThrow(() -> new StaffAccessException("student not in scope"));
        if (!scope.canSee(s)) throw new StaffAccessException("student not in scope");
        return s;
    }

    /* ------------------------------ actor derivation ------------------------------ */

    /**
     * THE RETIREMENT OF THE CLIENT-SUPPLIED ACTOR.
     *
     * The caller supplies a request and an event — never an actor. The Actor is whatever the
     * FROZEN MATRIX says is the decision-maker for (this type, this state, this event),
     * intersected with the roles this principal actually holds. Exactly one survivor is
     * required: none means the move is not this staff member's to make.
     */
    public Actor actorFor(StaffScope scope, Request r, Event event) {
        List<Actor> candidates = new ArrayList<>();
        for (Transition t : TransitionMatrix.spec(r.type).from(r.state)) {
            if (t.event() != event) continue;
            if (t.actor() == Actor.SYSTEM || t.actor() == Actor.STUDENT) continue;
            if (!scope.mayAct(t.actor())) continue;
            if (!candidates.contains(t.actor())) candidates.add(t.actor());
        }
        if (candidates.size() != 1) {
            throw new StaffAccessException(
                    "no role of yours may take that action at this stage");
        }
        return candidates.get(0);
    }

    /** The staff actions on a card, filtered to the ones THIS principal may actually fire. */
    public List<Transition> permitted(StaffScope scope, Request r) {
        List<Transition> out = new ArrayList<>();
        for (Transition t : TransitionMatrix.spec(r.type).from(r.state)) {
            if (t.actor() == Actor.SYSTEM || t.actor() == Actor.STUDENT) continue;
            if (scope.mayAct(t.actor())) out.add(t);
        }
        return out;
    }
}
