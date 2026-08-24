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

    private static final String FALLBACK = "/requests";

    static String resolve(String back) {
        return back != null && ALLOWED.contains(back) ? back : FALLBACK;
    }
}
