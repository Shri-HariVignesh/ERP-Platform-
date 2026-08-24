package com.campusos.portal.config;

import com.campusos.portal.domain.*;
import com.campusos.portal.payload.*;
import com.campusos.portal.repo.*;
import com.campusos.portal.service.DemoIdentity;
import com.campusos.portal.service.RequestService;
import com.campusos.portal.service.Scope;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo data by driving the REAL state machine — every seeded request has genuine
 * RequestHistory rows and genuinely fired side effects. Nothing here writes a state directly.
 */
@Component
public class DemoSeeder implements CommandLineRunner {

    private final TenantRepository tenants;
    private final StudentRepository students;
    private final AttendanceRepository attendance;
    private final SemesterResultRepository results;
    private final ExamTermRepository terms;
    private final RequestService requests;
    private final DemoIdentity identities;

    public DemoSeeder(TenantRepository tenants, StudentRepository students,
                      AttendanceRepository attendance, SemesterResultRepository results,
                      ExamTermRepository terms, RequestService requests, DemoIdentity identities) {
        this.tenants = tenants;
        this.students = students;
        this.attendance = attendance;
        this.results = results;
        this.terms = terms;
        this.requests = requests;
        this.identities = identities;
    }

    @Override
    @Transactional
    public void run(String... args) {
        /* ---- the tenant and student named in the brief ---- */
        Tenant snit = tenants.save(new Tenant("t_snit",
                "Sree Narayana Institute of Technology", "SNIT", "Kollam, Kerala", "#3b6fd4"));
        Student hari = student("s_hari", snit.id, "SNIT21CS042", "Hari Prasad",
                "hari.prasad@snit.ac.in", "B.Tech Computer Science", "Computer Science & Engineering",
                5, "A", 12500, "Prof. Anjali Menon", "Dr. R. Krishnakumar");
        identities.register(snit.id, hari.id, "Hari Prasad · SNIT (CSE, Sem 5)");

        List<LocalDate> classDays = seedAttendance(snit.id, hari.id);
        seedResults(snit.id, hari.id);
        seedTerm(snit.id);
        seedRequests(new Scope(snit.id, hari.id), classDays);

        /* ---- a second tenant, purely to prove isolation ---- */
        Tenant ace = tenants.save(new Tenant("t_ace",
                "Amrita College of Engineering", "ACE", "Coimbatore, Tamil Nadu", "#a2452f"));
        Student meera = student("s_meera", ace.id, "ACE22EC118", "Meera Nair",
                "meera.nair@ace.ac.in", "B.Tech Electronics", "Electronics & Communication",
                3, "B", 0, "Prof. S. Ravi", "Dr. Latha Iyer");
        identities.register(ace.id, meera.id, "Meera Nair · ACE (ECE, Sem 3) — isolation check");
        List<LocalDate> aceDays = seedAttendance(ace.id, meera.id);
        seedResults(ace.id, meera.id);
        seedTerm(ace.id);
        Scope aceScope = new Scope(ace.id, meera.id);
        requests.create(aceScope, RequestType.DOCUMENT, doc(DocType.BONAFIDE, "Passport application", 1));
        requests.create(aceScope, RequestType.LEAVE,
                leave(LeavePayload.LeaveType.MEDICAL, aceDays.get(2), aceDays.get(3), "Viral fever"));
    }

    /* ------------------------------- fixtures ------------------------------- */

    private Student student(String id, String tenantId, String rollNo, String name, String email,
                            String program, String department, int semester, String section,
                            int feeDues, String advisor, String hod) {
        Student s = new Student();
        s.id = id;
        s.tenantId = tenantId;
        s.rollNo = rollNo;
        s.name = name;
        s.email = email;
        s.program = program;
        s.department = department;
        s.semester = semester;
        s.section = section;
        s.feeDues = feeDues;
        s.active = true;
        s.leaveBalance = 12;
        s.advisorName = advisor;
        s.hodName = hod;
        return students.save(s);
    }

    /** Returns the FUTURE class days, which is what a leave request can legitimately target. */
    private List<LocalDate> seedAttendance(String tenantId, String studentId) {
        List<LocalDate> future = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int i = 0;
        for (LocalDate d = today.minusDays(75); !d.isAfter(today.plusDays(30)); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            AttendanceRecord.Status status;
            if (d.isAfter(today)) {
                status = AttendanceRecord.Status.SCHEDULED;
                future.add(d);
            } else {
                status = (i % 9 == 4) ? AttendanceRecord.Status.ABSENT : AttendanceRecord.Status.PRESENT;
            }
            attendance.save(new AttendanceRecord(tenantId, studentId, d, status));
            i++;
        }
        return future;
    }

    private void seedResults(String tenantId, String studentId) {
        results.save(new SemesterResult(tenantId, studentId, 1, 8.12, 22));
        results.save(new SemesterResult(tenantId, studentId, 2, 8.46, 24));
        results.save(new SemesterResult(tenantId, studentId, 3, 8.91, 23));
        results.save(new SemesterResult(tenantId, studentId, 4, 9.04, 25));
    }

    private void seedTerm(String tenantId) {
        ExamTerm t = new ExamTerm();
        t.tenantId = tenantId;
        t.name = "End Semester Examinations, Nov–Dec";
        t.startDate = LocalDate.now().plusDays(21);
        t.endDate = LocalDate.now().plusDays(35);
        t.hallTicketReleased = true;
        terms.save(t);
    }

    /* ------------------- requests, spanning different states ------------------- */

    private void seedRequests(Scope s, List<LocalDate> future) {
        // 1. LEAVE — full happy path. Attendance really moves.
        Request approved = requests.create(s, RequestType.LEAVE,
                leave(LeavePayload.LeaveType.EVENT, future.get(1), future.get(2),
                        "Represented college at the inter-college hackathon"));
        requests.transition(s, approved.id, Event.APPROVE, Actor.FACULTY, "Verified with the event convenor");
        requests.transition(s, approved.id, Event.APPROVE, Actor.HOD, null);

        // 2. LEAVE — parked mid-flow, waiting on the HOD.
        Request midway = requests.create(s, RequestType.LEAVE,
                leave(LeavePayload.LeaveType.PERSONAL, future.get(6), future.get(8),
                        "Sister's wedding at Thrissur"));
        requests.transition(s, midway.id, Event.APPROVE, Actor.FACULTY, "Dates clash with no internals");

        // 3. LEAVE — the rejection path.
        Request rejected = requests.create(s, RequestType.LEAVE,
                leave(LeavePayload.LeaveType.PERSONAL, future.get(11), future.get(15),
                        "Family trip to Ooty"));
        requests.transition(s, rejected.id, Event.REJECT, Actor.FACULTY,
                "Overlaps with the Series-II internal exams. Reapply after 30 Nov.");

        // 4. INTERNSHIP — awaiting faculty verification.
        requests.create(s, RequestType.INTERNSHIP, internship("Zoho Corporation", "Backend Intern",
                LocalDate.now().minusDays(120), LocalDate.now().minusDays(60),
                "Worked on the invoicing microservice; Java, Spring Boot, MySQL.",
                "zoho-internship-certificate.pdf"));

        // 5. INTERNSHIP — the RETURNED path (rejection path for this workflow).
        Request returned = requests.create(s, RequestType.INTERNSHIP,
                internship("Tata Elxsi", "Embedded Systems Intern",
                        LocalDate.now().minusDays(200), LocalDate.now().minusDays(150),
                        "Firmware validation for an automotive ECU.", "tata-elxsi-scan.pdf"));
        requests.transition(s, returned.id, Event.RETURN, Actor.FACULTY,
                "Certificate scan is unreadable and the end date does not match the offer letter. "
                        + "Upload a clear copy.");

        // 6. INTERNSHIP — fully verified: academic record written, verifyId + QR issued.
        Request verified = requests.create(s, RequestType.INTERNSHIP,
                internship("Infosys", "Full-Stack Intern",
                        LocalDate.now().minusDays(330), LocalDate.now().minusDays(240),
                        "Built an internal dashboard with React and Spring Boot.",
                        "infosys-completion-certificate.pdf"));
        requests.transition(s, verified.id, Event.VERIFY, Actor.FACULTY, "Certificate matches the offer letter");
        requests.transition(s, verified.id, Event.APPROVE, Actor.INSTITUTION, null);

        // 7. DOCUMENT — bonafide. Zero human touches; already READY.
        requests.create(s, RequestType.DOCUMENT, doc(DocType.BONAFIDE, "Passport application", 2));

        // 8. DOCUMENT — transcript. Routed to the office, still waiting.
        requests.create(s, RequestType.DOCUMENT, doc(DocType.TRANSCRIPT, "MS application, Germany", 1));

        // 9. DOCUMENT — hall ticket, approved by the office so the Academic card is populated.
        Request hall = requests.create(s, RequestType.DOCUMENT,
                doc(DocType.HALL_TICKET, "End semester examinations", 1));
        requests.transition(s, hall.id, Event.APPROVE, Actor.OFFICE, "Dues cleared for the exam term");

        // 10. GRIEVANCE — auto-assigned to a desk.
        requests.create(s, RequestType.GRIEVANCE, grievance(GrievancePayload.Category.HOSTEL,
                "No hot water in Block C for six days",
                "The heater on the second floor of Block C has been down since last Tuesday. "
                        + "Two complaints in the register have gone unanswered."));
    }

    /* ------------------------------- payloads ------------------------------- */

    private LeavePayload leave(LeavePayload.LeaveType type, LocalDate from, LocalDate to, String reason) {
        LeavePayload p = new LeavePayload();
        p.leaveType = type;
        p.from = from.toString();
        p.to = to.toString();
        p.reason = reason;
        return p;
    }

    private InternshipPayload internship(String company, String role, LocalDate from, LocalDate to,
                                         String details, String certificate) {
        InternshipPayload p = new InternshipPayload();
        p.company = company;
        p.role = role;
        p.from = from.toString();
        p.to = to.toString();
        p.details = details;
        p.certificateRef = new InternshipPayload.CertificateRef(certificate, "application/pdf", 248);
        return p;
    }

    private DocumentPayload doc(DocType type, String purpose, int copies) {
        DocumentPayload p = new DocumentPayload();
        p.docType = type;
        p.purpose = purpose;
        p.copies = copies;
        return p;
    }

    private GrievancePayload grievance(GrievancePayload.Category category, String subject, String body) {
        GrievancePayload p = new GrievancePayload();
        p.category = category;
        p.subject = subject;
        p.description = body;
        p.anonymous = false;
        p.sys.routedTo = "Hostel Warden Office";
        return p;
    }
}
