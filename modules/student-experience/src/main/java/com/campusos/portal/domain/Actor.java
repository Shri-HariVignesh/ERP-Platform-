package com.campusos.portal.domain;

public enum Actor {
    SYSTEM("CampusOS"),
    STUDENT("Student"),
    FACULTY("Class Advisor"),
    HOD("Head of Department"),
    INSTITUTION("Institution"),
    OFFICE("Examination Office");

    private final String display;

    Actor(String display) { this.display = display; }

    public String display() { return display; }
}
