package com.campusos.portal.view;

/**
 * One already-humanized row of the audit trail. The template prints these strings verbatim,
 * so no enum constant can reach a student's screen.
 */
public record TrailEntry(
        String transition,
        String actor,
        String note,
        String effects,
        String proof,
        String at) {

    public boolean hasNote() { return note != null && !note.isBlank(); }

    public boolean hasEffects() { return effects != null && !effects.isBlank(); }

    public boolean hasProof() { return proof != null && !proof.isBlank(); }
}
