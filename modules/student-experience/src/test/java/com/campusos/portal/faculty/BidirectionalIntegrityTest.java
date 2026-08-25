package com.campusos.portal.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.service.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

/**
 * THE BIDIRECTIONAL INTEGRITY CLAIM, end to end over HTTP.
 *
 * Both directions, both through the real screens:
 *   student submits  -> it appears in the right faculty member's My Tasks, and on the
 *                       student's profile in the Students view;
 *   faculty authors  -> it appears on the student's own Academic view, on the SAME
 *                       AttendanceRecord / SemesterResult rows the student already read.
 */
@Tag("security")
class BidirectionalIntegrityTest extends EngineTestBase {

    @Autowired AcademicService academic;
    @Autowired com.campusos.portal.service.DemoIdentity identities;

    private static final ClassKey CSE_5A =
            new ClassKey("Computer Science & Engineering", 5, "A");

    private Student fixtureStudent() {
        Student s = new Student();
        s.id = "s_bidi_" + System.nanoTime();
        s.tenantId = "t_snit";
        s.rollNo = "SNITBIDI" + (System.nanoTime() % 10000);
        s.name = "Bidi Test " + (System.nanoTime() % 1000);
        s.email = s.id + "@snit.ac.in";
        s.program = "B.Tech Computer Science";
        s.department = CSE_5A.department();
        s.semester = CSE_5A.semester();
        s.section = CSE_5A.section();
        s.active = true;
        s.leaveBalance = 12;
        s.advisorName = "Prof. Anjali Menon";
        s.hodName = "Dr. R. Krishnakumar";
        students.save(s);
        identities.register(s.tenantId, s.id, s.name);

        // A class-day series, so the attendance percentage means something.
        LocalDate today = LocalDate.now();
        for (LocalDate d = today.minusDays(30); !d.isAfter(today.plusDays(10)); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) continue;
            attendance.save(new AttendanceRecord(s.tenantId, s.id, d,
                    d.isAfter(today) ? AttendanceRecord.Status.SCHEDULED
                                     : AttendanceRecord.Status.PRESENT));
        }
        return s;
    }

    private MockHttpSession studentSession(String studentId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/switch").with(csrf()).param("studentId", studentId).session(session));
        return session;
    }

    /* --------------------- direction 1: student -> faculty --------------------- */

    @Test
    @DisplayName("a leave a student submits lands in their own faculty member's My Tasks")
    void studentSubmissionReachesTheRightFacultyInbox() throws Exception {
        Student s = fixtureStudent();
        MockHttpSession session = studentSession(s.id);

        List<LocalDate> future = attendance.findByTenantIdAndStudentId("t_snit", s.id).stream()
                .filter(a -> a.status == AttendanceRecord.Status.SCHEDULED)
                .map(a -> a.date).sorted().toList();

        mvc.perform(post("/leave").with(csrf()).session(session)
                .param("leaveType", "MEDICAL")
                .param("from", future.get(0).toString())
                .param("to", future.get(1).toString())
                .param("reason", "Bidirectional integrity probe"));

        String facultyInbox = mvc.perform(get("/faculty/tasks")
                        .with(user(principal("anjali.menon"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(facultyInbox)
                .as("her own student's submission is in her inbox")
                .contains(s.name)
                .contains("Bidirectional integrity probe");

        String otherDepartment = mvc.perform(get("/faculty/tasks")
                        .with(user(principal("suresh.babu"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(otherDepartment)
                .as("and nowhere near another department's inbox")
                .doesNotContain("Bidirectional integrity probe");

        String profile = mvc.perform(get("/faculty/students/" + s.id)
                        .with(user(principal("anjali.menon"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(profile)
                .as("the same submission is on the student's profile in the Students view")
                .contains("Bidirectional integrity probe");
    }

    @Test
    @DisplayName("a faculty approval on the staff screen is the same record the student tracks")
    void facultyDecisionAppearsOnTheStudentsOwnTracker() throws Exception {
        Student s = fixtureStudent();
        MockHttpSession session = studentSession(s.id);
        List<LocalDate> future = attendance.findByTenantIdAndStudentId("t_snit", s.id).stream()
                .filter(a -> a.status == AttendanceRecord.Status.SCHEDULED)
                .map(a -> a.date).sorted().toList();

        mvc.perform(post("/leave").with(csrf()).session(session)
                .param("leaveType", "EVENT")
                .param("from", future.get(0).toString())
                .param("to", future.get(0).toString())
                .param("reason", "Inter-college hackathon"));

        Request submitted = requests
                .findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", s.id).get(0);
        assertThat(submitted.state).isEqualTo(RequestState.FACULTY_PENDING);

        mvc.perform(post("/faculty/requests/" + submitted.id + "/act").with(csrf())
                .with(user(principal("anjali.menon")))
                .param("event", "APPROVE").param("note", "Verified with the convenor")
                .param("back", "/faculty/tasks"));

        String tracker = mvc.perform(get("/requests").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(tracker)
                .as("the student sees the faculty member's decision and their words")
                .contains("With HOD")
                .contains("Verified with the convenor");

        assertThat(requests.findByIdAndTenantIdAndStudentId(submitted.id, "t_snit", s.id)
                .orElseThrow().state)
                .as("one record, moved by the engine — not a staff-side copy")
                .isEqualTo(RequestState.HOD_PENDING);
    }

    /* --------------------- direction 2: faculty -> student --------------------- */

    @Test
    @DisplayName("attendance marked on the register moves the student's own percentage")
    void facultyAttendanceReachesTheStudentsAcademicView() throws Exception {
        Student s = fixtureStudent();
        Scope studentScope = new Scope("t_snit", s.id);
        MockHttpSession session = studentSession(s.id);

        double before = academic.attendancePct(studentScope);
        LocalDate day = attendance.findByTenantIdAndStudentId("t_snit", s.id).stream()
                .filter(a -> a.status == AttendanceRecord.Status.PRESENT)
                .map(a -> a.date).sorted().toList().get(0);

        mvc.perform(post("/faculty/attendance").with(csrf())
                        .with(user(principal("anjali.menon")))
                        .param("clazz", CSE_5A.token()).param("subject", "CS501")
                        .param("date", day.toString())
                        .param("status_" + s.id, "ABSENT"))
                .andReturn();

        double after = academic.attendancePct(studentScope);
        assertThat(after).as("the register moved the number").isLessThan(before);

        String academicPage = mvc.perform(get("/academic").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(academicPage)
                .as("and the student's own Academic page shows exactly that figure")
                .contains(after + "%");

        assertThat(attendance.findByTenantIdAndStudentIdAndDate("t_snit", s.id, day).orElseThrow())
                .satisfies(row -> {
                    assertThat(row.status).isEqualTo(AttendanceRecord.Status.ABSENT);
                    assertThat(row.markedByStaffId)
                            .as("the same row the student reads, stamped with who marked it")
                            .isEqualTo("st_anjali");
                });
    }

    @Test
    @DisplayName("finalized marks reach the student's Academic view; drafts never do")
    void facultyMarksReachTheStudentsAcademicView() throws Exception {
        Student s = fixtureStudent();
        MockHttpSession session = studentSession(s.id);

        // Draft first, over HTTP, exactly as the screen posts it.
        mvc.perform(post("/faculty/marks").with(csrf()).with(user(principal("anjali.menon")))
                .param("clazz", CSE_5A.token()).param("subject", "CS501")
                .param("action", "draft")
                .param("internal_" + s.id, "12").param("external_" + s.id, "13"));

        String beforePublish = mvc.perform(get("/academic").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(beforePublish)
                .as("a draft is invisible on the student's page")
                .doesNotContain("CS501");
        assertThat(subjectMarks.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
                "t_snit", s.id, 5, "CS501").orElseThrow().status).isEqualTo(MarkStatus.DRAFT);

        // Now finalize the same subject.
        mvc.perform(post("/faculty/marks").with(csrf()).with(user(principal("anjali.menon")))
                .param("clazz", CSE_5A.token()).param("subject", "CS501")
                .param("action", "finalize")
                .param("internal_" + s.id, "36").param("external_" + s.id, "54"));

        String afterPublish = mvc.perform(get("/academic").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(afterPublish)
                .as("finalizing publishes it to the student, grade and all")
                .contains("CS501").contains("90").contains("S");
    }

    @Test
    @DisplayName("neither side has a private copy — one row backs both views")
    void thereIsNoParallelDataset() {
        Student s = fixtureStudent();
        Scope studentScope = new Scope("t_snit", s.id);

        // Attendance: the student's own list IS the list the register writes to.
        List<AttendanceRecord> rows = attendance.findByTenantIdAndStudentId("t_snit", s.id);
        assertThat(academic.attendancePct(studentScope)).isEqualTo(AttendanceMath.pct(rows));
        assertThat(rows).extracting(r -> r.date).doesNotHaveDuplicates();

        // Marks: SemesterResult is derived from SubjectMark, never stored independently.
        for (SemesterResult r : semesterResults
                .findByTenantIdAndStudentIdOrderBySemesterAsc("t_snit", "s_hari")) {
            List<SubjectMark> behind = subjectMarks
                    .findByTenantIdAndStudentIdAndSemesterOrderBySubjectCodeAsc(
                            "t_snit", "s_hari", r.semester)
                    .stream().filter(SubjectMark::finalized).toList();
            assertThat(r.sgpa).isEqualTo(SgpaMath.sgpa(behind));
        }
    }
}
