package com.campusos.portal.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.security.StaffPrincipal;
import com.campusos.portal.service.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * FREEZE CONDITION 1 — ATTENDANCE COHERENCE, pinned.
 *
 * The model chosen is class-day only: ONE AttendanceRecord per student per date, with no
 * subject dimension. These tests pin what the percentage counts, so that faculty marking can
 * never quietly change what the student's number means.
 */
@Tag("security")
class AttendanceSemanticsTest extends EngineTestBase {

    @Autowired StaffScopeResolver staffScopes;
    @Autowired AcademicWriteService writes;
    @Autowired AcademicService academic;

    private static final ClassKey CSE_5A =
            new ClassKey("Computer Science & Engineering", 5, "A");

    private StaffScope scopeOf(String username) {
        StaffPrincipal p = principal(username);
        Authentication auth = new UsernamePasswordAuthenticationToken(p, "n/a", p.getAuthorities());
        return staffScopes.current(auth);
    }

    /** A past class day that no other test in this class touches. */
    private LocalDate pastDay(String studentId, int nthFromOldest) {
        List<LocalDate> past = attendance.findByTenantIdAndStudentId("t_snit", studentId).stream()
                .filter(a -> a.status != AttendanceRecord.Status.SCHEDULED)
                .map(a -> a.date).sorted().toList();
        return past.get(nthFromOldest);
    }

    /* --------------------------- the semantics themselves --------------------------- */

    @Test
    @DisplayName("THE DEFINITION: the percentage is attended class days over counted class days")
    void percentageSemanticsArePinned() {
        List<AttendanceRecord> rows = attendance.findByTenantIdAndStudentId("t_snit", "s_hari");

        long counted = rows.stream().filter(AttendanceRecord::counted).count();
        long attended = rows.stream().filter(AttendanceRecord::counted)
                .filter(AttendanceRecord::attended).count();

        assertThat(AttendanceMath.pct(rows))
                .as("PRESENT + APPROVED_LEAVE over every non-SCHEDULED day, and nothing else")
                .isEqualTo(Math.round(attended * 1000.0 / counted) / 10.0);

        assertThat(rows).allSatisfy(r -> assertThat(r.date).isNotNull());
    }

    @Test
    @DisplayName("there is exactly ONE attendance row per student per date — no parallel series")
    void oneRowPerStudentPerDay() {
        StaffScope anjali = scopeOf("anjali.menon");
        LocalDate day = pastDay("s_hari", 2);

        writes.markAttendance(anjali, CSE_5A, "CS501", day,
                Map.of("s_hari", AttendanceRecord.Status.ABSENT));
        writes.markAttendance(anjali, CSE_5A, "CS502", day,
                Map.of("s_hari", AttendanceRecord.Status.PRESENT));

        long rowsForThatDay = attendance.findByTenantIdAndStudentId("t_snit", "s_hari").stream()
                .filter(r -> r.date.equals(day)).count();

        assertThat(rowsForThatDay)
                .as("marking a second subject on the same day must UPDATE the class day, not "
                        + "add a second row — two rows would make the percentage average two "
                        + "different things")
                .isEqualTo(1);

        assertThat(attendance.findByTenantIdAndStudentIdAndDate("t_snit", "s_hari", day)
                .orElseThrow().status)
                .as("the later marking wins").isEqualTo(AttendanceRecord.Status.PRESENT);
    }

    @Test
    @DisplayName("faculty marking recomputes the SAME percentage the student reads")
    void facultyMarkingMovesTheStudentsOwnNumber() {
        StaffScope anjali = scopeOf("anjali.menon");
        Scope hari = new Scope("t_snit", "s_hari");
        LocalDate day = pastDay("s_hari", 5);

        // Force a known starting point, then flip it and watch the student's figure move.
        writes.markAttendance(anjali, CSE_5A, "CS501", day,
                Map.of("s_hari", AttendanceRecord.Status.PRESENT));
        double before = academic.attendancePct(hari);

        AcademicWriteService.AttendanceWrite w = writes.markAttendance(anjali, CSE_5A, "CS501", day,
                Map.of("s_hari", AttendanceRecord.Status.ABSENT));
        double after = academic.attendancePct(hari);

        assertThat(after).as("one present day became absent").isLessThan(before);
        assertThat(w.pctByStudent().get("s_hari"))
                .as("what the write service reports IS what the student's Academic view shows")
                .isEqualTo(after);
        assertThat(after)
                .as("and it is still AttendanceMath over the same rows — no second calculation")
                .isEqualTo(AttendanceMath.pct(
                        attendance.findByTenantIdAndStudentId("t_snit", "s_hari")));
    }

    /* ------------------------------ integrity guards ------------------------------ */

    @Test
    @DisplayName("an approved-leave day is not the register's to overwrite")
    void approvedLeaveIsProtectedFromTheRegister() {
        StaffScope anjali = scopeOf("anjali.menon");
        Scope divya = new Scope("t_snit", "s_divya");

        // Drive a real leave to approval so the engine writes APPROVED_LEAVE itself.
        List<LocalDate> future = attendance.findByTenantIdAndStudentId("t_snit", "s_divya").stream()
                .filter(a -> a.status == AttendanceRecord.Status.SCHEDULED)
                .map(a -> a.date).sorted().toList();
        LocalDate leaveDay = future.get(8);
        Request r = machine.create(divya, RequestType.LEAVE, leave(leaveDay, leaveDay));
        machine.transition(divya, r.id, Event.APPROVE, Actor.FACULTY, null);
        machine.transition(divya, r.id, Event.APPROVE, Actor.HOD, null);
        assertThat(attendance.findByTenantIdAndStudentIdAndDate("t_snit", "s_divya", leaveDay)
                .orElseThrow().status).isEqualTo(AttendanceRecord.Status.APPROVED_LEAVE);

        // The leave day is in the future, so marking it is refused on those grounds alone.
        assertThatThrownBy(() -> writes.markAttendance(anjali, CSE_5A, "CS501", leaveDay,
                Map.of("s_divya", AttendanceRecord.Status.ABSENT)))
                .isInstanceOf(StaffAccessException.class)
                .hasMessageContaining("future");

        // Now the same protection where the date is markable: back-date an approved-leave row.
        LocalDate past = pastDay("s_divya", 7);
        AttendanceRecord row = attendance
                .findByTenantIdAndStudentIdAndDate("t_snit", "s_divya", past).orElseThrow();
        row.status = AttendanceRecord.Status.APPROVED_LEAVE;
        row.sourceRequestId = r.id;
        attendance.save(row);

        AcademicWriteService.AttendanceWrite w = writes.markAttendance(anjali, CSE_5A, "CS501", past,
                Map.of("s_divya", AttendanceRecord.Status.ABSENT));

        assertThat(w.leaveProtected()).isEqualTo(1);
        assertThat(w.marked()).isZero();
        assertThat(attendance.findByTenantIdAndStudentIdAndDate("t_snit", "s_divya", past)
                .orElseThrow().status)
                .as("a completed HOD approval is not undone by a register")
                .isEqualTo(AttendanceRecord.Status.APPROVED_LEAVE);
    }

    @Test
    @DisplayName("a future class day cannot be marked")
    void futureDaysAreRefused() {
        StaffScope anjali = scopeOf("anjali.menon");
        assertThatThrownBy(() -> writes.markAttendance(anjali, CSE_5A, "CS501",
                LocalDate.now().plusDays(3), Map.of("s_hari", AttendanceRecord.Status.PRESENT)))
                .isInstanceOf(StaffAccessException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("only present or absent may be marked — the workflow statuses are not on offer")
    void engineOwnedStatusesCannotBeAuthored() {
        StaffScope anjali = scopeOf("anjali.menon");
        LocalDate day = pastDay("s_hari", 8);

        for (AttendanceRecord.Status forbidden : List.of(
                AttendanceRecord.Status.APPROVED_LEAVE, AttendanceRecord.Status.SCHEDULED)) {
            assertThatThrownBy(() -> writes.markAttendance(anjali, CSE_5A, "CS501", day,
                    Map.of("s_hari", forbidden)))
                    .isInstanceOf(StaffAccessException.class)
                    .hasMessageContaining("present or absent");
        }
    }

    /* -------------------------------- scope guards -------------------------------- */

    @Test
    @DisplayName("a faculty cannot mark a class they do not teach")
    void cannotMarkAClassNotTaught() {
        StaffScope anjali = scopeOf("anjali.menon");                 // CSE 5 A
        ClassKey ece = new ClassKey("Electronics & Communication", 5, "A");

        assertThatThrownBy(() -> writes.markAttendance(anjali, ece, "EC501", pastDay("s_nikhil", 3),
                Map.of("s_nikhil", AttendanceRecord.Status.ABSENT)))
                .isInstanceOf(StaffAccessException.class)
                .hasMessageContaining("not a subject you teach");
    }

    @Test
    @DisplayName("a faculty cannot mark a SUBJECT they do not teach, even in their own class")
    void cannotMarkASubjectNotTaught() {
        StaffScope anjali = scopeOf("anjali.menon");    // teaches CS501-503, not CS504
        assertThatThrownBy(() -> writes.markAttendance(anjali, CSE_5A, "CS504", pastDay("s_hari", 9),
                Map.of("s_hari", AttendanceRecord.Status.ABSENT)))
                .isInstanceOf(StaffAccessException.class);
    }

    @Test
    @DisplayName("a student outside the class cannot be smuggled into the register")
    void cannotMarkAStudentOutsideTheClass() {
        StaffScope anjali = scopeOf("anjali.menon");
        assertThatThrownBy(() -> writes.markAttendance(anjali, CSE_5A, "CS501", pastDay("s_hari", 10),
                Map.of("s_nikhil", AttendanceRecord.Status.ABSENT)))
                .isInstanceOf(StaffAccessException.class)
                .hasMessageContaining("not in a class you teach");
    }

    @Test
    @DisplayName("every attendance write leaves an audit row naming the staff member")
    void attendanceIsAudited() {
        StaffScope anjali = scopeOf("anjali.menon");
        LocalDate day = pastDay("s_hari", 11);

        long before = academicAudits.findByTenantIdAndStudentIdOrderByAtDesc("t_snit", "s_hari")
                .stream().filter(a -> a.kind == AcademicAudit.Kind.ATTENDANCE).count();

        writes.markAttendance(anjali, CSE_5A, "CS502", day,
                Map.of("s_hari", AttendanceRecord.Status.PRESENT));

        var audits = academicAudits.findByTenantIdAndStudentIdOrderByAtDesc("t_snit", "s_hari")
                .stream().filter(a -> a.kind == AcademicAudit.Kind.ATTENDANCE).toList();
        assertThat(audits.size()).isEqualTo(before + 1);
        assertThat(audits.get(0).staffId).isEqualTo("st_anjali");
        assertThat(audits.get(0).subjectCode).isEqualTo("CS502");
        assertThat(audits.get(0).detail).contains(day.toString());
    }
}
