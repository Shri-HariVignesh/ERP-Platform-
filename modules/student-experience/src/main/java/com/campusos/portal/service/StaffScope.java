package com.campusos.portal.service;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.StaffRole;
import com.campusos.portal.domain.Student;
import com.campusos.portal.domain.TeachingAssignment;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * THE STAFF EQUIVALENT OF Scope. Built only from the authenticated principal — nothing in it
 * is ever read from a request parameter, which is what retires the client-supplied actor.
 *
 * Two different breadths live here, deliberately:
 *
 *   canSee(student)  — REQUEST breadth. Widens with role: a class for FACULTY, a department
 *                      for HOD, the tenant for INSTITUTION/OFFICE.
 *   teaches(class,subject) — ACADEMIC-WRITE breadth. Teaching assignment ONLY, whatever roles
 *                      are held. An HOD who teaches nothing may author nothing.
 */
public record StaffScope(String tenantId, String staffId, String name, String department,
                         Set<StaffRole> roles, List<TeachingAssignment> assignments) {

    public StaffScope {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalStateException("tenantId missing — staff query refused");
        if (staffId == null || staffId.isBlank())
            throw new IllegalStateException("staffId missing — staff query refused");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    /* ------------------------------- roles -> actors ------------------------------- */

    public boolean hasRole(StaffRole r) { return roles.contains(r); }

    /** The Actors this principal may ever present to the engine. Never STUDENT, never SYSTEM. */
    public Set<Actor> actors() {
        Set<Actor> out = new LinkedHashSet<>();
        for (StaffRole r : roles) out.add(r.actor());
        return out;
    }

    public boolean mayAct(Actor a) { return actors().contains(a); }

    /* ------------------------------- request breadth ------------------------------- */

    /**
     * REQUEST breadth. Tenant equality is a precondition of every branch — there is no path
     * through this method that returns true for a student of another tenant.
     */
    public boolean canSee(Student s) {
        if (s == null || !tenantId.equals(s.tenantId)) return false;
        if (hasRole(StaffRole.INSTITUTION) || hasRole(StaffRole.OFFICE)) return true;
        if (hasRole(StaffRole.HOD) && department != null && department.equals(s.department)) return true;
        if (hasRole(StaffRole.FACULTY)) {
            for (TeachingAssignment a : assignments) {
                if (ClassKey.of(a).matches(s)) return true;
            }
        }
        return false;
    }

    /* ---------------------------- academic-write breadth ---------------------------- */

    /** True iff this staff member personally teaches this subject to this class. */
    public boolean teaches(ClassKey c, String subjectCode) {
        for (TeachingAssignment a : assignments) {
            if (ClassKey.of(a).equals(c) && a.subjectCode.equals(subjectCode)) return true;
        }
        return false;
    }

    /** True iff this staff member teaches at least one subject to this class. */
    public boolean teaches(ClassKey c) {
        for (TeachingAssignment a : assignments) {
            if (ClassKey.of(a).equals(c)) return true;
        }
        return false;
    }

    /** The classes this staff member teaches — the ONLY values the class picker is built from. */
    public List<ClassKey> classes() {
        LinkedHashSet<ClassKey> out = new LinkedHashSet<>();
        for (TeachingAssignment a : assignments) out.add(ClassKey.of(a));
        return List.copyOf(out);
    }

    public List<TeachingAssignment> subjectsIn(ClassKey c) {
        return assignments.stream().filter(a -> ClassKey.of(a).equals(c)).toList();
    }

    public boolean authors() { return !assignments.isEmpty(); }
}
