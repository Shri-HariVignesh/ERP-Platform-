package com.campusos.portal.config;

import com.campusos.portal.domain.*;
import com.campusos.portal.payload.*;
import com.campusos.portal.repo.*;
import com.campusos.portal.service.RequestService;
import com.campusos.portal.service.Scope;
import com.campusos.portal.service.SgpaMath;
import com.campusos.portal.view.DisplayLabels;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final StudentAccountRepository studentAccounts;
    private final StaffUserRepository staff;
    private final TeachingAssignmentRepository assignments;
    private final SubjectMarkRepository marks;
    private final PasswordEncoder encoder;

    public DemoSeeder(TenantRepository tenants, StudentRepository students,
                      AttendanceRepository attendance, SemesterResultRepository results,
                      ExamTermRepository terms, RequestService requests,
                      StudentAccountRepository studentAccounts,
                      StaffUserRepository staff, TeachingAssignmentRepository assignments,
                      SubjectMarkRepository marks, PasswordEncoder encoder) {
        this.tenants = tenants;
        this.students = students;
        this.attendance = attendance;
        this.results = results;
        this.terms = terms;
        this.requests = requests;
        this.studentAccounts = studentAccounts;
        this.staff = staff;
        this.assignments = assignments;
        this.marks = marks;
        this.encoder = encoder;
    }

    /**
     * One login per seeded student. The username is derived from the roll number, which is
     * already unique inside a tenant and carries the institution prefix, so it cannot collide
     * with a staff username (those are person-name based) — PortalLoginTest asserts the two
     * sets stay disjoint rather than leaving that to luck.
     */
    private StudentAccount account(String tenantId, String studentId) {
        Student s = students.findByIdAndTenantId(studentId, tenantId).orElseThrow();
        StudentAccount a = new StudentAccount();
        a.id = "sa_" + studentId.replaceFirst("^s_", "");
        a.tenantId = tenantId;
        a.studentId = studentId;
        a.username = s.rollNo.toLowerCase();
        a.passwordHash = encoder.encode(DEMO_PASSWORD);
        a.active = true;
        return studentAccounts.save(a);
    }

    /** Demo password for every seeded staff account and student login. BCrypt hash only. */
    private static final String DEMO_PASSWORD = "campus123";

    private static final String CSE = "Computer Science & Engineering";
    private static final String ECE = "Electronics & Communication";

    @Override
    @Transactional
    public void run(String... args) {
        /* ---- the tenant and student named in the brief ---- */
        Tenant snit = tenants.save(new Tenant("t_snit",
                "Sree Narayana Institute of Technology", "SNIT", "Kollam, Kerala", "#3b6fd4"));
        Student hari = student("s_hari", snit.id, "SNIT21CS042", "Hari Prasad",
                "hari.prasad@snit.ac.in", "B.Tech Computer Science", "Computer Science & Engineering",
                5, "A", 12500, "Prof. Anjali Menon", "Dr. R. Krishnakumar");
        account(snit.id, hari.id);

        List<LocalDate> classDays = seedAttendance(snit.id, hari.id);
        seedMarksAndResults(snit.id, hari.id, "CS", 5);
        seedTerm(snit.id);
        seedRequests(new Scope(snit.id, hari.id), classDays);

        /* ---- a classmate, so a class register has more than one row ---- */
        Student divya = student("s_divya", snit.id, "SNIT21CS051", "Divya Rajan",
                "divya.rajan@snit.ac.in", "B.Tech Computer Science", CSE,
                5, "A", 0, "Prof. Anjali Menon", "Dr. R. Krishnakumar");
        account(snit.id, divya.id);
        List<LocalDate> divyaDays = seedAttendance(snit.id, divya.id);
        seedMarksAndResults(snit.id, divya.id, "CS", 5);
        requests.create(new Scope(snit.id, divya.id), RequestType.LEAVE,
                leave(LeavePayload.LeaveType.MEDICAL, divyaDays.get(3), divyaDays.get(4),
                        "Dengue — hospital advice attached"));

        /* ---- ANOTHER DEPARTMENT IN THE SAME TENANT. This is the one that proves a faculty
               member's scope is their own classes, not the whole institution. ---- */
        Student nikhil = student("s_nikhil", snit.id, "SNIT21EC017", "Nikhil Varma",
                "nikhil.varma@snit.ac.in", "B.Tech Electronics", ECE,
                5, "A", 0, "Prof. Suresh Babu", "Dr. Geetha Menon");
        account(snit.id, nikhil.id);
        List<LocalDate> nikhilDays = seedAttendance(snit.id, nikhil.id);
        seedMarksAndResults(snit.id, nikhil.id, "EC", 5);
        requests.create(new Scope(snit.id, nikhil.id), RequestType.LEAVE,
                leave(LeavePayload.LeaveType.PERSONAL, nikhilDays.get(5), nikhilDays.get(6),
                        "Cousin's wedding in Alappuzha"));

        seedSnitStaff(snit.id);

        /* ---- a second tenant, purely to prove isolation ---- */
        Tenant ace = tenants.save(new Tenant("t_ace",
                "Amrita College of Engineering", "ACE", "Coimbatore, Tamil Nadu", "#a2452f"));
        Student meera = student("s_meera", ace.id, "ACE22EC118", "Meera Nair",
                "meera.nair@ace.ac.in", "B.Tech Electronics", "Electronics & Communication",
                3, "B", 0, "Prof. S. Ravi", "Dr. Latha Iyer");
        account(ace.id, meera.id);
        List<LocalDate> aceDays = seedAttendance(ace.id, meera.id);
        seedMarksAndResults(ace.id, meera.id, "EC", 3);
        seedTerm(ace.id);
        seedAceStaff(ace.id);
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

    private record Subj(String code, String name, int credits) {}

    private static final String[] SUBJECT_NAMES = {
            "Mathematics", "Data Structures", "Digital Systems", "Signals & Systems",
            "Professional Communication"};

    private List<Subj> subjectsFor(String prefix, int semester) {
        List<Subj> out = new ArrayList<>();
        for (int i = 0; i < SUBJECT_NAMES.length; i++) {
            out.add(new Subj(prefix + semester + "0" + (i + 1),
                    SUBJECT_NAMES[i] + " " + toRoman(semester), i == 4 ? 2 : 4));
        }
        return out;
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV";
            case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; default -> String.valueOf(n);
        };
    }

    /**
     * CONSISTENCY BY CONSTRUCTION. The published SemesterResult is not a hand-typed SGPA — it
     * is DERIVED from the seeded SubjectMark rows through SgpaMath, the very same function
     * AcademicWriteService uses when a faculty member finalizes marks.
     *
     * That matters: a hard-coded SGPA with no marks behind it would be recomputed from partial
     * data the first time anyone finalized a single subject, silently overwriting it. Here the
     * marks ARE the source, so a recompute reproduces the same number.
     *
     * Only COMPLETED semesters are seeded. The current semester is left empty on purpose, so
     * the draft -> finalized path can be demonstrated end to end.
     */
    private void seedMarksAndResults(String tenantId, String studentId, String prefix,
                                     int currentSemester) {
        for (int sem = 1; sem < currentSemester; sem++) {
            List<SubjectMark> published = new ArrayList<>();
            for (Subj sub : subjectsFor(prefix, sem)) {
                SubjectMark m = new SubjectMark();
                m.tenantId = tenantId;
                m.studentId = studentId;
                m.semester = sem;
                m.subjectCode = sub.code();
                m.subjectName = sub.name();
                m.credits = sub.credits();
                // Deterministic, so a rebuild produces the same transcript every time.
                int spread = Math.floorMod((studentId + sub.code()).hashCode(), 26);
                m.internal = 28 + (spread % 11);          // 28..38 of 40
                m.external = 38 + (spread % 19);          // 38..56 of 60
                m.status = MarkStatus.FINALIZED;
                m.enteredByStaffId = "seed";
                published.add(marks.save(m));
            }
            results.save(new SemesterResult(tenantId, studentId, sem,
                    SgpaMath.sgpa(published), SgpaMath.credits(published)));
        }
    }

    /* --------------------------------- staff --------------------------------- */

    private StaffUser staff(String id, String tenantId, String username, String name, String email,
                            String department, StaffRole... roles) {
        StaffUser u = new StaffUser();
        u.id = id;
        u.tenantId = tenantId;
        u.username = username;
        u.passwordHash = encoder.encode(DEMO_PASSWORD);
        u.name = name;
        u.email = email;
        u.department = department;
        u.active = true;
        u.roles = new java.util.LinkedHashSet<>(Arrays.asList(roles));
        return staff.save(u);
    }

    /** Gives one staff member every subject of one class, so a class has a real timetable. */
    private void teaches(String tenantId, String staffId, String department, int semester,
                         String section, String prefix, Set<String> only) {
        for (Subj sub : subjectsFor(prefix, semester)) {
            if (only != null && !only.contains(sub.code())) continue;
            assignments.save(new TeachingAssignment(tenantId, staffId, department, semester,
                    section, sub.code(), sub.name(), sub.credits()));
        }
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

    /**
     * SNIT staff. Between them they cover every Actor of the frozen matrix, plus the two cases
     * the scope rules exist for: a faculty member in a DIFFERENT department, and a staff member
     * holding TWO roles at once.
     */
    private void seedSnitStaff(String tenantId) {
        // FACULTY of CSE Sem 5 A — Hari's and Divya's class advisor.
        StaffUser anjali = staff("st_anjali", tenantId, "anjali.menon", "Prof. Anjali Menon",
                "anjali.menon@snit.ac.in", CSE, StaffRole.FACULTY);
        teaches(tenantId, anjali.id, CSE, 5, "A", "CS", Set.of("CS501", "CS502", "CS503"));

        // A SECOND faculty on the same class, so "every subject finalized" is a real gate and
        // not something one person can trivially satisfy alone.
        StaffUser suresh = staff("st_suresh", tenantId, "suresh.kumar", "Prof. Suresh Kumar",
                "suresh.kumar@snit.ac.in", CSE, StaffRole.FACULTY);
        teaches(tenantId, suresh.id, CSE, 5, "A", "CS", Set.of("CS504", "CS505"));

        // HOD of CSE who ALSO teaches — two roles, two breadths, one login.
        StaffUser krishna = staff("st_krishna", tenantId, "krishnakumar", "Dr. R. Krishnakumar",
                "hod.cse@snit.ac.in", CSE, StaffRole.HOD, StaffRole.FACULTY);
        teaches(tenantId, krishna.id, CSE, 5, "A", "CS", Set.of("CS503"));

        // FACULTY of ECE — same tenant, different department. Must never see Hari.
        StaffUser babu = staff("st_babu", tenantId, "suresh.babu", "Prof. Suresh Babu",
                "suresh.babu@snit.ac.in", ECE, StaffRole.FACULTY);
        teaches(tenantId, babu.id, ECE, 5, "A", "EC", Set.of("EC501", "EC502"));

        // Tenant-wide desks: no department, no teaching assignment, therefore no academic
        // authoring rights at all — only the requests their Actor is the decision-maker for.
        staff("st_registrar", tenantId, "registrar.snit", "Dr. Latha Pillai (Registrar)",
                "registrar@snit.ac.in", null, StaffRole.INSTITUTION);
        staff("st_exam", tenantId, "exam.office", "Examination Office",
                "exams@snit.ac.in", null, StaffRole.OFFICE);
    }

    /** ACE staff, so cross-tenant isolation has a real staff member on the other side. */
    private void seedAceStaff(String tenantId) {
        StaffUser latha = staff("st_latha", tenantId, "latha.iyer", "Dr. Latha Iyer",
                "hod.ece@ace.ac.in", ECE, StaffRole.HOD, StaffRole.FACULTY);
        teaches(tenantId, latha.id, ECE, 3, "B", "EC", null);
        staff("st_ace_office", tenantId, "office.ace", "ACE Examination Office",
                "exams@ace.ac.in", null, StaffRole.OFFICE, StaffRole.INSTITUTION);
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
        p.sys.routedTo = DisplayLabels.desk(category);
        return p;
    }
}
