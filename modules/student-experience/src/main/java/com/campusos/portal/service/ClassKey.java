package com.campusos.portal.service;

import com.campusos.portal.domain.Student;
import com.campusos.portal.domain.TeachingAssignment;

/**
 * A class: one section of one semester of one department. This is the unit a faculty member
 * is assigned to teach, and the unit a student belongs to — Student already carries all three
 * columns, so the scope rule joins on data that exists.
 */
public record ClassKey(String department, int semester, String section) {

    public ClassKey {
        if (department == null || department.isBlank())
            throw new IllegalStateException("department missing — class scope refused");
        if (section == null || section.isBlank())
            throw new IllegalStateException("section missing — class scope refused");
    }

    public static ClassKey of(Student s) {
        return new ClassKey(s.department, s.semester, s.section);
    }

    public static ClassKey of(TeachingAssignment a) {
        return new ClassKey(a.department, a.semester, a.section);
    }

    public boolean matches(Student s) {
        return department.equals(s.department) && semester == s.semester && section.equals(s.section);
    }

    /** Stable round-trippable form for a form field, so no free-text class ever reaches a query. */
    public String token() {
        return department + "|" + semester + "|" + section;
    }

    public static ClassKey parse(String token) {
        String[] p = token == null ? new String[0] : token.split("\\|");
        if (p.length != 3) throw new IllegalStateException("unreadable class token");
        try {
            return new ClassKey(p[0], Integer.parseInt(p[1]), p[2]);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("unreadable class token");
        }
    }

    public String label() {
        return department + " · Semester " + semester + " · Section " + section;
    }
}
