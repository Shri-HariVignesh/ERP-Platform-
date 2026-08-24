package com.campusos.portal.domain;

public enum DocType {
    BONAFIDE("Bonafide Certificate"),
    HALL_TICKET("Hall Ticket"),
    FEE_RECEIPT("Fee Receipt"),
    TRANSCRIPT("Transcript"),
    CONDUCT_CERTIFICATE("Conduct Certificate"),
    INTERNSHIP_VERIFICATION("Internship Verification Certificate");

    private final String display;

    DocType(String display) { this.display = display; }

    public String display() { return display; }
}
