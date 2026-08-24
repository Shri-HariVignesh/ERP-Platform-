package com.campusos.portal.payload;

import com.campusos.portal.domain.RequestType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LeavePayload implements RequestPayload {
    public enum LeaveType { MEDICAL, PERSONAL, EVENT }

    /* ---- frozen contract fields ---- */
    public LeaveType leaveType;
    public String from;
    public String to;
    public String reason;

    /* ---- system-derived ---- */
    public Sys sys = new Sys();

    public static class Sys {
        public int dayCount;
        public int balanceAtSubmit;
        public Double attendanceBefore;
        public Double attendanceAfter;
        public List<String> datesMutated = new ArrayList<>();
        public String validation;
    }

    @Override public RequestType type() { return RequestType.LEAVE; }

    @Override public String title() {
        return leaveType.name().charAt(0) + leaveType.name().substring(1).toLowerCase() + " leave";
    }

    @Override public String subtitle() {
        return from + " → " + to + " · " + sys.dayCount + " day(s) · " + reason;
    }

    @Override public List<Artifact> artifacts() {
        List<Artifact> out = new ArrayList<>();
        if (sys.attendanceAfter != null) {
            out.add(Artifact.of("ATTENDANCE", "Attendance",
                    sys.attendanceBefore + "% → " + sys.attendanceAfter + "%"));
            out.add(Artifact.of("DAYS", "Days marked APPROVED_LEAVE",
                    String.valueOf(sys.datesMutated.size())));
        }
        return out;
    }

    @Override public void validate() {
        if (leaveType == null) throw new IllegalArgumentException("leaveType is required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        LocalDate a = LocalDate.parse(from), b = LocalDate.parse(to);
        if (b.isBefore(a)) throw new IllegalArgumentException("to-date is before from-date");
        if (a.plusDays(30).isBefore(b)) throw new IllegalArgumentException("leave longer than 30 days");
    }
}
