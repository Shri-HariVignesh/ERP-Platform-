package com.campusos.portal.domain;

/**
 * A role a staff member holds. Each constant maps to EXACTLY ONE Actor of the frozen matrix
 * (see StaffScope#actorFor). There is deliberately no role that maps to Actor.STUDENT or
 * Actor.SYSTEM — those two are unreachable from any authenticated principal, by construction.
 */
public enum StaffRole {
    FACULTY(Actor.FACULTY, "Faculty"),
    HOD(Actor.HOD, "Head of Department"),
    INSTITUTION(Actor.INSTITUTION, "Institution"),
    OFFICE(Actor.OFFICE, "Examination Office");

    private final Actor actor;
    private final String display;

    StaffRole(Actor actor, String display) { this.actor = actor; this.display = display; }

    /** The one Actor this role may ever present to RequestStateMachine.transition(). */
    public Actor actor() { return actor; }

    public String display() { return display; }
}
