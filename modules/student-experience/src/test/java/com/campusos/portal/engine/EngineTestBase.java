package com.campusos.portal.engine;

import com.campusos.portal.domain.*;
import com.campusos.portal.payload.*;
import com.campusos.portal.repo.*;
import com.campusos.portal.security.StaffPrincipal;
import com.campusos.portal.service.Scope;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared harness. Deliberately NOT @Transactional: RequestStateMachine.transition is itself
 * transactional, so an expected IllegalTransitionException would mark a surrounding test
 * transaction rollback-only and poison every later assertion in the same test. Instead each
 * test builds its own tenant + student, so nothing is shared and nothing needs rolling back.
 *
 * One @SpringBootTest configuration for every Spring test in the suite, so all of them share
 * a single cached context and a single in-memory database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class EngineTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected RequestStateMachine machine;
    @Autowired protected RequestRepository requests;
    @Autowired protected RequestHistoryRepository histories;
    @Autowired protected StudentRepository students;
    @Autowired protected TenantRepository tenants;
    @Autowired protected AttendanceRepository attendance;
    @Autowired protected AcademicRecordRepository academic;
    @Autowired protected DocumentRepository documents;
    @Autowired protected VerificationRepository verifications;
    @Autowired protected StaffUserRepository staffUsers;
    @Autowired protected TeachingAssignmentRepository teaching;
    @Autowired protected SubjectMarkRepository subjectMarks;
    @Autowired protected SemesterResultRepository semesterResults;
    @Autowired protected AcademicAuditRepository academicAudits;
    @Autowired protected MockMvc mvc;

    /**
     * The authenticated staff identity for a seeded username, for use with MockMvc's
     * .with(user(...)). Resolved from the database exactly as a real login would, so a test
     * cannot grant itself a role or a tenant the seed did not give it.
     */
    protected StaffPrincipal principal(String username) {
        return new StaffPrincipal(staffUsers.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("no seeded staff user " + username)));
    }

    /** One move through the guard. */
    public record Move(Event event, Actor actor, String note) {
        public static Move of(Event e, Actor a) { return new Move(e, a, null); }
        public static Move of(Event e, Actor a, String note) { return new Move(e, a, note); }
    }

    public record Fixture(Scope scope, Student student, List<LocalDate> futureClassDays,
                          List<LocalDate> pastClassDays) {}

    /* ------------------------------- fixtures ------------------------------- */

    protected Fixture fixture(String tag) {
        int n = SEQ.incrementAndGet();
        String tenantId = "t_" + tag + "_" + n;
        String studentId = "s_" + tag + "_" + n;

        tenants.save(new Tenant(tenantId, tag.toUpperCase() + " Institute of Technology",
                "T" + n, "Kollam", "#3b6fd4"));

        Student s = new Student();
        s.id = studentId;
        s.tenantId = tenantId;
        s.rollNo = "ROLL" + n;
        s.name = "Test Student " + n;
        s.email = "test" + n + "@example.edu";
        s.program = "B.Tech Computer Science";
        s.department = "Computer Science & Engineering";
        s.semester = 5;
        s.section = "A";
        s.feeDues = 0;
        s.active = true;
        s.leaveBalance = 12;
        s.advisorName = "Advisor";
        s.hodName = "HOD";
        students.save(s);

        List<LocalDate> future = new ArrayList<>();
        List<LocalDate> past = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int i = 0;
        for (LocalDate d = today.minusDays(40); !d.isAfter(today.plusDays(25)); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            AttendanceRecord.Status status;
            if (d.isAfter(today)) {
                status = AttendanceRecord.Status.SCHEDULED;
                future.add(d);
            } else {
                status = (i % 9 == 4) ? AttendanceRecord.Status.ABSENT : AttendanceRecord.Status.PRESENT;
                past.add(d);
            }
            attendance.save(new AttendanceRecord(tenantId, studentId, d, status));
            i++;
        }
        return new Fixture(new Scope(tenantId, studentId), s, future, past);
    }

    /* -------------------------------- payloads -------------------------------- */

    /** Flavours exist so one parameterized test can cover both Document guard branches. */
    protected RequestPayload payloadFor(String flavor, Fixture f) {
        return switch (flavor) {
            case "LEAVE" -> leave(f.futureClassDays().get(0), f.futureClassDays().get(1));
            case "INTERNSHIP" -> internship("cert.pdf");
            case "DOCUMENT_AUTO" -> document(DocType.BONAFIDE);
            case "DOCUMENT_MANUAL" -> document(DocType.TRANSCRIPT);
            case "GRIEVANCE" -> grievance();
            default -> throw new IllegalArgumentException("unknown flavor " + flavor);
        };
    }

    protected RequestType typeFor(String flavor) {
        return switch (flavor) {
            case "LEAVE" -> RequestType.LEAVE;
            case "INTERNSHIP" -> RequestType.INTERNSHIP;
            case "DOCUMENT_AUTO", "DOCUMENT_MANUAL" -> RequestType.DOCUMENT;
            case "GRIEVANCE" -> RequestType.GRIEVANCE;
            default -> throw new IllegalArgumentException("unknown flavor " + flavor);
        };
    }

    protected LeavePayload leave(LocalDate from, LocalDate to) {
        LeavePayload p = new LeavePayload();
        p.leaveType = LeavePayload.LeaveType.PERSONAL;
        p.from = from.toString();
        p.to = to.toString();
        p.reason = "Test leave";
        return p;
    }

    protected InternshipPayload internship(String certificate) {
        InternshipPayload p = new InternshipPayload();
        p.company = "Test Corp";
        p.role = "Intern";
        p.from = LocalDate.now().minusDays(120).toString();
        p.to = LocalDate.now().minusDays(60).toString();
        p.details = "Worked on tests";
        p.certificateRef = new InternshipPayload.CertificateRef(certificate, "application/pdf", 200);
        return p;
    }

    protected DocumentPayload document(DocType type) {
        DocumentPayload p = new DocumentPayload();
        p.docType = type;
        p.purpose = "Testing";
        p.copies = 1;
        return p;
    }

    protected GrievancePayload grievance() {
        return grievance(GrievancePayload.Category.HOSTEL);
    }

    protected GrievancePayload grievance(GrievancePayload.Category category) {
        GrievancePayload p = new GrievancePayload();
        p.category = category;
        p.subject = "Test grievance";
        p.description = "Something is broken";
        p.anonymous = false;
        p.sys.routedTo = com.campusos.portal.view.DisplayLabels.desk(category);
        return p;
    }

    /* --------------------------------- driving --------------------------------- */

    protected Request start(Fixture f, String flavor) {
        return machine.create(f.scope(), typeFor(flavor), payloadFor(flavor, f));
    }

    /** Drives a fresh request through the given moves and returns it. */
    protected Request drive(Fixture f, String flavor, Move... moves) {
        Request r = start(f, flavor);
        for (Move m : moves) {
            r = machine.transition(f.scope(), r.id, m.event(), m.actor(), m.note());
        }
        return r;
    }

    protected RequestPayload payloadOf(Fixture f, Request r) {
        return new PayloadCodec().read(r.type,
                requests.findByIdAndTenantIdAndStudentId(r.id, f.scope().tenantId(),
                        f.scope().studentId()).orElseThrow().payload);
    }

    protected List<RequestHistory> historyOf(Fixture f, Request r) {
        return histories.findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(
                r.id, f.scope().tenantId(), f.scope().studentId());
    }
}
