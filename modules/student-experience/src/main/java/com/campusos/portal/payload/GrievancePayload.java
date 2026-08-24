package com.campusos.portal.payload;

import com.campusos.portal.domain.RequestType;
import com.campusos.portal.view.DisplayLabels;
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
        return DisplayLabels.category(category) + " · " + DisplayLabels.desk(category)
                + (anonymous ? " · anonymous" : "");
    }

    @Override public List<Artifact> artifacts() { return new ArrayList<>(); }

    /**
     * The desk, not the generic matrix actor — "Hostel Warden Office", never "Class Advisor".
     *
     * Derived from the category at render time rather than read from sys.routedTo. The stored
     * value is written once at submit and cannot be trusted to match: nothing in the engine
     * maintains it (AUTO_ASSIGN declares no side effects), and older rows were seeded with a
     * single desk regardless of category. Deriving keeps the badge, the subtitle and the
     * "Currently with" line saying the same thing for every grievance, old rows included.
     */
    @Override public String handledBy() {
        return DisplayLabels.desk(category);
    }

    @Override public void validate() {
        if (category == null) throw new IllegalArgumentException("category is required");
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
    }
}
