package com.campusos.portal.view;

/**
 * A button a staff member may actually press, derived from the matrix edge INTERSECTED with
 * the roles the authenticated principal holds.
 *
 * Note what this record does NOT have: an actor field. ActionButton (the student-side record)
 * carries one because the retired /sim hook posted it back. Here there is nothing to post —
 * the server derives the Actor from the session, so a tampered form has no actor to tamper with.
 */
public record StaffAction(String label, String event, String tone, boolean requiresNote) {}
