package com.campusos.portal.view;

/**
 * A conditional STUDENT action, declared on a matrix edge. No actor field: the student
 * endpoint hardcodes Actor.STUDENT, so there is nothing for a form to assert about identity.
 */
public record ActionButton(String label, String event, String tone,
                          boolean requiresNote, String inputLabel) {}
