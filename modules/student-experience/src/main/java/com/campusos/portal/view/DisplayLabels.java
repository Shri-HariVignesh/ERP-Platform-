package com.campusos.portal.view;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.RequestState;
import com.campusos.portal.domain.RequestType;
import com.campusos.portal.domain.SideEffect;
import com.campusos.portal.payload.GrievancePayload;
import com.campusos.portal.payload.LeavePayload;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * THE one place enum constants become English. Display only — nothing here is read by the
 * engine, the matrix or the guard, and no enum value changes. Every card goes through this,
 * which is how the templates stay free of both raw enums and per-type branching.
 */
public final class DisplayLabels {

    private DisplayLabels() {}

    private static final Map<RequestState, String> STATES = Map.ofEntries(
            Map.entry(RequestState.SUBMITTED, "Submitted"),
            Map.entry(RequestState.FACULTY_PENDING, "With faculty"),
            Map.entry(RequestState.HOD_PENDING, "With HOD"),
            Map.entry(RequestState.ATTENDANCE_MUTATED, "Attendance updated"),
            Map.entry(RequestState.NOTIFIED, "Approved"),
            Map.entry(RequestState.REJECTED, "Rejected"),
            Map.entry(RequestState.FACULTY_VERIFICATION, "With faculty for verification"),
            Map.entry(RequestState.INSTITUTION_APPROVAL, "With the institution"),
            Map.entry(RequestState.ACADEMIC_RECORD_MUTATED, "Added to academic record"),
            Map.entry(RequestState.VERIFICATION_ID_GENERATED, "Verified"),
            Map.entry(RequestState.RETURNED, "Returned for correction"),
            Map.entry(RequestState.APPROVAL, "With the office"),
            Map.entry(RequestState.DOCUMENT_GENERATED, "Document ready"),
            Map.entry(RequestState.ASSIGNED, "Assigned to a desk"),
            Map.entry(RequestState.UNDER_REVIEW, "Under review"),
            Map.entry(RequestState.RESOLVED, "Resolved"));

    private static final Map<SideEffect, String> EFFECTS = Map.of(
            SideEffect.VALIDATE_LEAVE, "Validated leave",
            SideEffect.MUTATE_ATTENDANCE, "Updated attendance",
            SideEffect.CHECK_CERTIFICATE, "Checked certificate",
            SideEffect.WRITE_ACADEMIC_RECORD, "Wrote academic record",
            SideEffect.GENERATE_VERIFICATION_ID, "Issued verification ID",
            SideEffect.PUBLISH_CERT_TO_DOCUMENTS, "Published certificate",
            SideEffect.RUN_ELIGIBILITY, "Checked eligibility",
            SideEffect.GENERATE_DOCUMENT, "Generated document",
            SideEffect.NOTIFY, "Notified you",
            SideEffect.NOTIFY_REJECTION, "Sent you the reason");

    /**
     * The one-line status a student reads on the COLLAPSED card. Replaces the engine's
     * effect-log prose; the structured artifact bullets carry the detail cleanly.
     */
    private static final Map<RequestState, String> STATUS = Map.ofEntries(
            Map.entry(RequestState.NOTIFIED, "Approved — attendance updated."),
            Map.entry(RequestState.DOCUMENT_GENERATED, "Ready to download."),
            Map.entry(RequestState.VERIFICATION_ID_GENERATED, "Verified and added to your record."),
            Map.entry(RequestState.ACADEMIC_RECORD_MUTATED, "Adding to your academic record."),
            Map.entry(RequestState.ATTENDANCE_MUTATED, "Updating your attendance."),
            Map.entry(RequestState.RETURNED, "Returned — see the reason below."),
            Map.entry(RequestState.REJECTED, "Rejected — see the reason below."),
            Map.entry(RequestState.RESOLVED, "Resolved."));

    private static final Map<LeavePayload.LeaveType, String> LEAVE_TYPES = Map.of(
            LeavePayload.LeaveType.MEDICAL, "Medical",
            LeavePayload.LeaveType.PERSONAL, "Personal",
            LeavePayload.LeaveType.EVENT, "Event / On-duty");

    private static final Map<GrievancePayload.Category, String> CATEGORIES = Map.of(
            GrievancePayload.Category.ACADEMIC, "Academic",
            GrievancePayload.Category.EXAM, "Examination",
            GrievancePayload.Category.FEES, "Fees",
            GrievancePayload.Category.HOSTEL, "Hostel",
            GrievancePayload.Category.OTHER, "Other");

    private static final Map<RequestType, String> TYPES = Map.of(
            RequestType.LEAVE, "Leave",
            RequestType.INTERNSHIP, "Internship",
            RequestType.DOCUMENT, "Document",
            RequestType.GRIEVANCE, "Grievance");

    public static String state(RequestState s) {
        return s == null ? "" : STATES.getOrDefault(s, sentenceCase(s.name()));
    }

    /** "Submitted → With faculty", or just the destination for the first entry. */
    public static String transition(RequestState from, RequestState to) {
        return from == null ? state(to) : state(from) + " → " + state(to);
    }

    /** SYSTEM reads as automation; the student reads as themselves; staff read as their role. */
    public static String actor(Actor a, String studentName) {
        if (a == null) return "";
        return switch (a) {
            case SYSTEM -> "Automated";
            case STUDENT -> studentName == null || studentName.isBlank() ? "You" : studentName;
            default -> a.display();
        };
    }

    /** RequestHistory stores effects comma-joined; turn that back into readable phrases. */
    public static String effects(String commaJoined) {
        if (commaJoined == null || commaJoined.isBlank()) return "";
        return Arrays.stream(commaJoined.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DisplayLabels::effect)
                .collect(Collectors.joining(" · "));
    }

    public static String effect(String name) {
        try {
            return EFFECTS.getOrDefault(SideEffect.valueOf(name), sentenceCase(name));
        } catch (IllegalArgumentException e) {
            return sentenceCase(name);
        }
    }

    /** Short human status for a state, or null when the card should say who has it. */
    public static String status(RequestState s) {
        return s == null ? null : STATUS.get(s);
    }

    /** SCREAMING_SNAKE tokens embedded in prose. Requires an underscore, so serials and
     *  verification ids (SNIT-2026-P64HVW, HAL/2026/...) are left alone. */
    private static final Pattern ENUM_TOKEN = Pattern.compile("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b");

    /** Reviewer-facing asides the engine writes into its effect log. Hidden from students at
     *  render time; the stored effect log is untouched, so the tests still see it verbatim. */
    private static final Pattern DEV_ASIDE = Pattern.compile("\\s*\\([^)]*declared non-goal[^)]*\\)");

    /**
     * The side-effect log is written by the engine for auditability, so it names internal
     * things. This cleans it for a student's screen without the engine knowing or caring.
     */
    /** Sentences and fragments that are engine bookkeeping, not information for a student. */
    private static final Pattern[] STRIP = {
            Pattern.compile("\\s*Document rendered:[^.]*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\s*View/Download is now enabled\\.?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\s*[-—]?\\s*QR resolves at\\s*\\S*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\s*\\S*/verify/\\S*"),
            Pattern.compile("\\s*\\b\\w+\\s+notified\\s+in-app\\.?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*Eligibility rule ran\\s*[-—]\\s*", Pattern.CASE_INSENSITIVE),
    };

    private static final Map<String, String> REPHRASE = Map.of(
            "attendance record mutated", "attendance updated",
            "academic record mutated", "added to your academic record",
            "verify id", "Verification ID");

    public static String proof(String text) {
        if (text == null || text.isBlank()) return text;
        String out = DEV_ASIDE.matcher(text).replaceAll("")
                .replace("AttendanceRecord", "Attendance record")
                .replace("AcademicRecord", "Academic record");
        for (Pattern p : STRIP) out = p.matcher(out).replaceAll("");
        for (Map.Entry<String, String> e : REPHRASE.entrySet()) {
            out = Pattern.compile(Pattern.quote(e.getKey()), Pattern.CASE_INSENSITIVE)
                    .matcher(out).replaceAll(Matcher.quoteReplacement(e.getValue()));
        }
        Matcher m = ENUM_TOKEN.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    m.group().replace('_', ' ').toLowerCase()));
        }
        m.appendTail(sb);
        String cleaned = sb.toString().replaceAll("\\s{2,}", " ").trim();
        // a rephrase can leave a lower-case word leading the line
        return cleaned.isEmpty() ? cleaned
                : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    /**
     * SECURITY: /verify is unauthenticated by design — the unguessable id is the capability.
     * A verifier needs to know WHAT was attested, not the student's roll number (embedded in
     * the serial) or why they asked for it. Strip both before rendering that page.
     */
    public static String publicDetail(String detail) {
        if (detail == null || detail.isBlank()) return detail;
        String out = detail.replaceAll("(?i)\\s*Serial\\s+\\S+\\s*·?", "");
        out = out.replaceAll("(?i)\\s*·?\\s*\\d+\\s+cop(?:y|ies)(?:/copies)?\\s*·.*$", "");
        return out.replaceAll("^\\s*·\\s*", "").replaceAll("\\s{2,}", " ").trim();
    }

    /** A readable name for an event, so no matrix constant reaches the screen. */
    public static String event(com.campusos.portal.domain.Event e) {
        return e == null ? "" : sentenceCase(e.name());
    }

    /** The credential wording shown on the public verification page. */
    public static String credentialKind(String kind) {
        if (kind == null) return "Credential";
        return switch (kind) {
            case "INTERNSHIP" -> "Internship record";
            case "DOCUMENT" -> "Institutional document";
            default -> sentenceCase(kind);
        };
    }

    public static String type(RequestType t) {
        return t == null ? "" : TYPES.getOrDefault(t, sentenceCase(t.name()));
    }

    /** Form option label. The submitted value stays the enum constant. */
    public static String leaveType(LeavePayload.LeaveType t) {
        return t == null ? "" : LEAVE_TYPES.getOrDefault(t, sentenceCase(t.name()));
    }

    /** Form option label. The submitted value stays the enum constant. */
    public static String category(GrievancePayload.Category c) {
        return c == null ? "" : CATEGORIES.getOrDefault(c, sentenceCase(c.name()));
    }

    /** Fallback so a newly added enum constant degrades to readable text, never to SCREAMING_SNAKE. */
    private static String sentenceCase(String enumName) {
        String words = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
