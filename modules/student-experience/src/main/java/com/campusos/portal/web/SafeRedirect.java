package com.campusos.portal.web;

import java.util.List;

/**
 * SECURITY (CWE-601): the `back` parameter used to be reflected straight into a
 * "redirect:" view name, so a crafted link could perform a real action and then bounce the
 * student to an attacker-controlled page. Only the app's own views are acceptable targets;
 * anything else falls back to the tracker.
 */
final class SafeRedirect {

    private SafeRedirect() {}

    private static final List<String> ALLOWED =
            List.of("/", "/requests", "/leave", "/internship", "/documents", "/academic", "/grievance");

    /**
     * The staff surface gets its OWN allow-list and its own fallback, resolved by a separate
     * method. Two lists, never one: a student `back` value must not be able to land a staff
     * member on a staff page, and a rejected staff value must fall back to a staff view rather
     * than dumping them on the student tracker.
     */
    private static final List<String> ALLOWED_STAFF =
            List.of("/faculty", "/faculty/tasks", "/faculty/leave", "/faculty/internship",
                    "/faculty/students", "/faculty/attendance", "/faculty/marks",
                    "/faculty/notifications");

    private static final String FALLBACK = "/requests";
    private static final String FALLBACK_STAFF = "/faculty/tasks";

    /** Student surface. Behaviour unchanged from the original hardening fix. */
    static String resolve(String back) {
        return back != null && ALLOWED.contains(back) ? back : FALLBACK;
    }

    /** Staff surface. Same rule, its own allow-list, its own fallback. */
    static String resolveStaff(String back) {
        return back != null && ALLOWED_STAFF.contains(back) ? back : FALLBACK_STAFF;
    }
}
