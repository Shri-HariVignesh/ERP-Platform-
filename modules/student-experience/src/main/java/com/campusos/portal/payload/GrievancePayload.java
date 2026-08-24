package com.campusos.portal.payload;

import com.campusos.portal.domain.RequestType;
import java.util.ArrayList;
import java.util.List;

public class GrievancePayload implements RequestPayload {
    public enum Category { ACADEMIC, HOSTEL, EXAM, FEES, OTHER }

    /* ---- frozen contract fields ---- */
    public Category category;
    public String subject;
    public String description;
    public boolean anonymous;

    /* ---- system-derived ---- */
    public Sys sys = new Sys();

    public static class Sys {
        public String routedTo;
    }

    @Override public RequestType type() { return RequestType.GRIEVANCE; }

    @Override public String title() { return subject; }

    @Override public String subtitle() {
        return category.name().charAt(0) + category.name().substring(1).toLowerCase()
                + (sys.routedTo == null ? "" : " · " + sys.routedTo)
                + (anonymous ? " · anonymous" : "");
    }

    @Override public List<Artifact> artifacts() { return new ArrayList<>(); }

    /** The routed desk, not the generic matrix actor — "Hostel Warden Office", not "Class Advisor". */
    @Override public String handledBy() {
        return sys.routedTo == null || sys.routedTo.isBlank() ? null : sys.routedTo;
    }

    @Override public void validate() {
        if (category == null) throw new IllegalArgumentException("category is required");
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
    }
}
