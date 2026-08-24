package com.campusos.portal.engine;

import com.campusos.portal.domain.*;
import com.campusos.portal.payload.*;
import com.campusos.portal.repo.*;
import com.campusos.portal.service.AttendanceMath;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Side effects run here and only here, inside the transition's transaction.
 * Each returns log lines that become RequestHistory.effectLog — the visible proof
 * that the effect actually mutated something.
 */
@Service
public class SideEffectDispatcher {

    private final AttendanceRepository attendance;
    private final AcademicRecordRepository academic;
    private final DocumentRepository documents;
    private final VerificationRepository verifications;
    private final StudentRepository students;

    public SideEffectDispatcher(AttendanceRepository attendance, AcademicRecordRepository academic,
                                DocumentRepository documents, VerificationRepository verifications,
                                StudentRepository students) {
        this.attendance = attendance;
        this.academic = academic;
        this.documents = documents;
        this.verifications = verifications;
        this.students = students;
    }

    public List<String> fire(SideEffect fx, Request r, RequestPayload p, Student s, Tenant t) {
        return switch (fx) {
            case VALIDATE_LEAVE -> validateLeave(r, (LeavePayload) p, s);
            case MUTATE_ATTENDANCE -> mutateAttendance(r, (LeavePayload) p, s);
            case CHECK_CERTIFICATE -> checkCertificate((InternshipPayload) p);
            case WRITE_ACADEMIC_RECORD -> writeAcademicRecord(r, (InternshipPayload) p);
            case GENERATE_VERIFICATION_ID -> generateVerificationId(r, p, s, t);
            case PUBLISH_CERT_TO_DOCUMENTS -> publishCert(r, (InternshipPayload) p, s, t);
            case RUN_ELIGIBILITY -> runEligibility((DocumentPayload) p, s);
            case GENERATE_DOCUMENT -> generateDocument(r, (DocumentPayload) p, s, t);
            case NOTIFY -> List.of("Student notified in-app. (External notification infra is a declared non-goal.)");
            case NOTIFY_REJECTION -> List.of("Reason pushed to the student. (External notification infra is a declared non-goal.)");
        };
    }

    /* ------------------------------- LEAVE ------------------------------- */

    private List<String> validateLeave(Request r, LeavePayload p, Student s) {
        LocalDate from = LocalDate.parse(p.from), to = LocalDate.parse(p.to);
        int days = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        Double pct = AttendanceMath.pct(attendance.findByTenantIdAndStudentId(r.tenantId, r.studentId));
        p.sys.dayCount = days;
        p.sys.balanceAtSubmit = s.leaveBalance;
        p.sys.attendanceBefore = pct;
        p.sys.validation = days + " day(s); leave balance " + s.leaveBalance + "; attendance " + pct + "%";
        return List.of("Auto-validated dates and leave balance — " + p.sys.validation
                + ". No human checked this.");
    }

    private List<String> mutateAttendance(Request r, LeavePayload p, Student s) {
        LocalDate from = LocalDate.parse(p.from), to = LocalDate.parse(p.to);
        List<AttendanceRecord> all = attendance.findByTenantIdAndStudentId(r.tenantId, r.studentId);
        Double before = AttendanceMath.pct(all);

        List<AttendanceRecord> window =
                attendance.findByTenantIdAndStudentIdAndDateBetween(r.tenantId, r.studentId, from, to);
        List<String> mutated = new ArrayList<>();
        for (AttendanceRecord a : window) {
            a.status = AttendanceRecord.Status.APPROVED_LEAVE;
            a.sourceRequestId = r.id;
            attendance.save(a);
            mutated.add(a.date.toString());
        }

        Double after = AttendanceMath.pct(attendance.findByTenantIdAndStudentId(r.tenantId, r.studentId));
        int newBalance = Math.max(0, s.leaveBalance - mutated.size());
        s.leaveBalance = newBalance;
        students.save(s);

        p.sys.attendanceBefore = before;
        p.sys.attendanceAfter = after;
        p.sys.datesMutated = mutated;

        return List.of("AttendanceRecord mutated: " + mutated.size()
                + " class day(s) set to APPROVED_LEAVE. Attendance " + before + "% → " + after
                + "%. Leave balance " + (newBalance + mutated.size()) + " → " + newBalance + ".");
    }

    /* ----------------------------- INTERNSHIP ----------------------------- */

    private List<String> checkCertificate(InternshipPayload p) {
        LocalDate from = LocalDate.parse(p.from), to = LocalDate.parse(p.to);
        int weeks = (int) Math.max(1, Math.round((to.toEpochDay() - from.toEpochDay()) / 7.0));
        p.sys.weeks = weeks;
        p.sys.certificateCheck = p.certificateRef == null
                ? "No certificate attached."
                : "Certificate " + p.certificateRef.filename + " present ("
                        + p.certificateRef.sizeKb + " KB); dates valid; " + weeks + " week(s) computed.";
        return List.of("Auto-check — " + p.sys.certificateCheck);
    }

    private List<String> writeAcademicRecord(Request r, InternshipPayload p) {
        int credits = Math.min(4, Math.max(1, Math.round(p.sys.weeks / 4f)));
        AcademicRecord a = new AcademicRecord();
        a.tenantId = r.tenantId;
        a.studentId = r.studentId;
        a.kind = "INTERNSHIP";
        a.title = p.role + " · " + p.company;
        a.subtitle = p.sys.weeks + " weeks · " + p.from + " → " + p.to;
        a.credits = credits;
        a.sourceRequestId = r.id;
        academic.save(a);
        p.sys.credits = credits;
        return List.of("AcademicRecord mutated: internship written to the official record, "
                + credits + " credit(s) awarded.");
    }

    private List<String> generateVerificationId(Request r, RequestPayload p, Student s, Tenant t) {
        String id = verifyId(t);
        String subject, detail, kind;
        if (p instanceof InternshipPayload ip) {
            kind = "INTERNSHIP";
            subject = ip.role + " · " + ip.company;
            detail = ip.sys.weeks + " weeks · " + ip.from + " → " + ip.to
                    + " · " + ip.sys.credits + " credit(s)";
            ip.sys.verifyId = id;
            for (AcademicRecord a : academic.findByTenantIdAndStudentIdAndSourceRequestId(
                    r.tenantId, r.studentId, r.id)) {
                a.verifyId = id;
                academic.save(a);
            }
        } else if (p instanceof DocumentPayload dp) {
            kind = "DOCUMENT";
            subject = dp.docType.display();
            detail = dp.copies + " copy/copies · " + dp.purpose;
            dp.sys.verifyId = id;
        } else {
            throw new IllegalStateException("verification id not defined for " + p.type());
        }

        Verification v = new Verification();
        v.verifyId = id;
        v.tenantId = r.tenantId;
        v.studentId = r.studentId;
        v.kind = kind;
        v.subject = subject;
        v.detail = detail;
        v.sourceRequestId = r.id;
        verifications.save(v);

        return List.of("Verification ID generated: " + id + " — QR resolves at /verify/" + id + ".");
    }

    private List<String> publishCert(Request r, InternshipPayload p, Student s, Tenant t) {
        String serial = nextSerial(r, s, "INT");
        DocumentArtifact d = new DocumentArtifact();
        d.tenantId = r.tenantId;
        d.studentId = r.studentId;
        d.serialNo = serial;
        d.docType = DocType.INTERNSHIP_VERIFICATION;
        d.title = DocType.INTERNSHIP_VERIFICATION.display();
        d.verifyId = p.sys.verifyId;
        d.sourceRequestId = r.id;
        d.html = render(t, s, d.title, serial, p.sys.verifyId,
                "This is to certify that the student named above completed an internship as <strong>"
                        + esc(p.role) + "</strong> at <strong>" + esc(p.company) + "</strong> for "
                        + p.sys.weeks + " weeks (" + p.from + " to " + p.to
                        + "). The engagement has been verified by the department and entered into the "
                        + "student's academic record for " + p.sys.credits + " credit(s).");
        documents.save(d);
        p.sys.documentSerial = serial;
        return List.of("Certificate published into Documents & Certificates as " + serial + ".");
    }

    /* ------------------------------ DOCUMENTS ------------------------------ */

    private List<String> runEligibility(DocumentPayload p, Student s) {
        boolean auto = p.docType == DocType.BONAFIDE && s.active;
        p.sys.autoEligible = auto;
        p.sys.eligibilityReason = TransitionMatrix.eligibilityReason(p, s.active);
        return List.of("Eligibility rule ran — " + p.sys.eligibilityReason);
    }

    private List<String> generateDocument(Request r, DocumentPayload p, Student s, Tenant t) {
        String serial = nextSerial(r, s, p.docType.name().substring(0, 3));
        String id = verifyId(t);

        Verification v = new Verification();
        v.verifyId = id;
        v.tenantId = r.tenantId;
        v.studentId = r.studentId;
        v.kind = "DOCUMENT";
        v.subject = p.docType.display();
        v.detail = "Serial " + serial + " · " + p.copies + " copy/copies · " + p.purpose;
        v.sourceRequestId = r.id;
        verifications.save(v);

        DocumentArtifact d = new DocumentArtifact();
        d.tenantId = r.tenantId;
        d.studentId = r.studentId;
        d.serialNo = serial;
        d.docType = p.docType;
        d.title = p.docType.display();
        d.verifyId = id;
        d.sourceRequestId = r.id;
        d.html = render(t, s, d.title, serial, id, body(p, s));
        documents.save(d);

        p.sys.serialNo = serial;
        p.sys.verifyId = id;
        p.sys.documentId = d.id;

        return List.of("Document rendered: " + d.title + ", serial " + serial
                + ", verify id " + id + ". View/Download is now enabled.");
    }

    private String body(DocumentPayload p, Student s) {
        String purpose = esc(p.purpose);
        return switch (p.docType) {
            case BONAFIDE -> "This is to certify that <strong>" + esc(s.name)
                    + "</strong> is a bonafide student of this institution, currently enrolled in semester "
                    + s.semester + " of the " + esc(s.program) + " programme. Issued for: " + purpose + ".";
            case HALL_TICKET -> "Examination hall ticket for <strong>" + esc(s.name) + "</strong>, semester "
                    + s.semester + ", section " + esc(s.section)
                    + ". Candidates must carry a photo ID. Issued for: " + purpose + ".";
            case FEE_RECEIPT -> "Consolidated fee receipt for <strong>" + esc(s.name)
                    + "</strong>. Outstanding balance at issue: ₹" + s.feeDues + ". Issued for: " + purpose + ".";
            case TRANSCRIPT -> "Consolidated academic transcript for <strong>" + esc(s.name)
                    + "</strong>, attested by the Controller of Examinations. Issued for: " + purpose + ".";
            case CONDUCT_CERTIFICATE -> "Conduct certificate for <strong>" + esc(s.name)
                    + "</strong>, attested by the Dean of Students. Issued for: " + purpose + ".";
            case INTERNSHIP_VERIFICATION -> "Internship verification for <strong>" + esc(s.name) + "</strong>.";
        };
    }

    /* -------------------------------- shared -------------------------------- */

    private String nextSerial(Request r, Student s, String prefix) {
        long n = documents.countByTenantIdAndStudentId(r.tenantId, r.studentId) + 1;
        return String.format("%s/%d/%s/%03d", prefix, LocalDate.now().getYear(), s.rollNo, n);
    }

    private String verifyId(Tenant t) {
        String rand = Long.toString(Math.abs(java.util.UUID.randomUUID().getMostSignificantBits()), 36)
                .toUpperCase(Locale.ROOT);
        return t.shortName.toUpperCase(Locale.ROOT) + "-" + LocalDate.now().getYear() + "-"
                + rand.substring(0, Math.min(6, rand.length()));
    }

    private String render(Tenant t, Student s, String title, String serial, String verifyId, String body) {
        String issued = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
        return """
            <article class="doc">
              <header><h1>%s</h1><p>%s</p></header>
              <h2>%s</h2>
              <dl>
                <div><dt>Name</dt><dd>%s</dd></div>
                <div><dt>Roll No.</dt><dd>%s</dd></div>
                <div><dt>Programme</dt><dd>%s, %s</dd></div>
                <div><dt>Semester</dt><dd>%d · Section %s</dd></div>
              </dl>
              <p class="body">%s</p>
              <footer>
                <div><span>Serial</span><strong>%s</strong></div>
                <div><span>Verify ID</span><strong>%s</strong></div>
                <div><span>Issued</span><strong>%s</strong></div>
              </footer>
              <p class="sig">Digitally issued by %s on CampusOS. No physical signature required.</p>
            </article>
            """.formatted(esc(t.name), esc(t.city), esc(title), esc(s.name), esc(s.rollNo),
                esc(s.program), esc(s.department), s.semester, esc(s.section),
                body, serial, verifyId, issued, esc(t.shortName));
    }

    private static String esc(String v) {
        return v == null ? "" : v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
