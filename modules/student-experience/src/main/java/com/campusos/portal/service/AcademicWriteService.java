package com.campusos.portal.service;

import com.campusos.portal.domain.*;
import com.campusos.portal.repo.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CATEGORY B — FACULTY ACADEMIC AUTHORING. The counterpart of RequestStateMachine, and the
 * ONLY path by which attendance or marks are mutated.
 *
 * These are not requests: nobody approves a register. So they do not go near the engine — but
 * they carry the same three disciplines it does:
 *   1. SCOPED   — every write is authorized by a TeachingAssignment, never by a role alone.
 *   2. AUDITED  — one AcademicAudit row per affected student, in the same transaction.
 *   3. COHERENT — writes land on the SAME AttendanceRecord / SemesterResult rows the student
 *                 already reads, so there is no second copy of anything.
 */
@Service
public class AcademicWriteService {

    private final AttendanceRepository attendance;
    private final SubjectMarkRepository marks;
    private final SemesterResultRepository results;
    private final TeachingAssignmentRepository assignments;
    private final AcademicAuditRepository audits;
    private final StaffScopeResolver staffScopes;

    public AcademicWriteService(AttendanceRepository attendance, SubjectMarkRepository marks,
                                SemesterResultRepository results,
                                TeachingAssignmentRepository assignments,
                                AcademicAuditRepository audits, StaffScopeResolver staffScopes) {
        this.attendance = attendance;
        this.marks = marks;
        this.results = results;
        this.assignments = assignments;
        this.audits = audits;
        this.staffScopes = staffScopes;
    }

    /* ================================ ATTENDANCE ================================ */

    public record AttendanceWrite(int marked, int leaveProtected, Map<String, Double> pctByStudent) {}

    /**
     * Marks one class day. There is no subject dimension on AttendanceRecord — the subject is
     * what AUTHORIZES the write and what the audit records; the row itself stays one-per-day,
     * so the percentage keeps meaning "share of class days attended".
     */
    @Transactional
    public AttendanceWrite markAttendance(StaffScope staff, ClassKey clazz, String subjectCode,
                                          LocalDate date, Map<String, AttendanceRecord.Status> input) {
        if (!staff.teaches(clazz, subjectCode)) {
            throw new StaffAccessException("not a subject you teach for this class");
        }
        if (date == null || date.isAfter(LocalDate.now())) {
            throw new StaffAccessException("a class day in the future cannot be marked");
        }

        List<Student> roster = staffScopes.classRoster(staff, clazz);
        Set<String> allowed = new HashSet<>();
        for (Student s : roster) allowed.add(s.id);

        int marked = 0, leaveProtected = 0;
        Map<String, Double> pct = new LinkedHashMap<>();

        for (Map.Entry<String, AttendanceRecord.Status> e : input.entrySet()) {
            String studentId = e.getKey();
            AttendanceRecord.Status status = e.getValue();
            if (status == null) continue;
            if (!allowed.contains(studentId)) {
                throw new StaffAccessException("student not in a class you teach");
            }
            // A faculty register records presence. APPROVED_LEAVE belongs to the leave
            // workflow and SCHEDULED to the timetable; neither is a faculty's to author.
            if (status != AttendanceRecord.Status.PRESENT && status != AttendanceRecord.Status.ABSENT) {
                throw new StaffAccessException("only present or absent may be marked");
            }

            AttendanceRecord row = attendance
                    .findByTenantIdAndStudentIdAndDate(staff.tenantId(), studentId, date)
                    .orElseGet(() -> new AttendanceRecord(
                            staff.tenantId(), studentId, date, AttendanceRecord.Status.SCHEDULED));

            // INTEGRITY: an HOD-approved leave day is not a faculty member's to overwrite.
            // Silently flipping it would undo a completed workflow with no audit of the undo.
            if (row.status == AttendanceRecord.Status.APPROVED_LEAVE) {
                leaveProtected++;
                continue;
            }

            row.status = status;
            row.markedByStaffId = staff.staffId();
            attendance.save(row);
            marked++;

            Double now = AttendanceMath.pct(
                    attendance.findByTenantIdAndStudentId(staff.tenantId(), studentId));
            pct.put(studentId, now);

            audits.save(new AcademicAudit(staff.tenantId(), studentId, staff.staffId(), staff.name(),
                    AcademicAudit.Kind.ATTENDANCE, subjectCode,
                    "Marked " + status.name().toLowerCase() + " for " + date
                            + " (" + clazz.label() + "). Attendance now " + now + "%."));
        }
        return new AttendanceWrite(marked, leaveProtected, pct);
    }

    /* =================================== MARKS =================================== */

    public record MarkInput(int internal, int external) {}

    public record MarksWrite(int saved, List<String> recomputed) {}

    /**
     * Saves a subject's marks for a class, as DRAFT or FINALIZED.
     *
     * DRAFT is the working copy: stored and audited, never visible to the student, and it does
     * not touch SemesterResult. FINALIZED publishes — and only then, and only under the gate in
     * {@link #recomputeIfComplete}, does the student's SemesterResult move.
     */
    @Transactional
    public MarksWrite saveMarks(StaffScope staff, ClassKey clazz, String subjectCode,
                                Map<String, MarkInput> input, MarkStatus target) {
        if (!staff.teaches(clazz, subjectCode)) {
            throw new StaffAccessException("not a subject you teach for this class");
        }
        TeachingAssignment mine = staff.subjectsIn(clazz).stream()
                .filter(a -> a.subjectCode.equals(subjectCode)).findFirst()
                .orElseThrow(() -> new StaffAccessException("not a subject you teach for this class"));

        List<Student> roster = staffScopes.classRoster(staff, clazz);
        Set<String> allowed = new HashSet<>();
        for (Student s : roster) allowed.add(s.id);

        int saved = 0;
        List<String> recomputed = new ArrayList<>();

        for (Map.Entry<String, MarkInput> e : input.entrySet()) {
            String studentId = e.getKey();
            MarkInput in = e.getValue();
            if (in == null) continue;
            if (!allowed.contains(studentId)) {
                throw new StaffAccessException("student not in a class you teach");
            }
            if (in.internal() < 0 || in.internal() > SgpaMath.MAX_INTERNAL
                    || in.external() < 0 || in.external() > SgpaMath.MAX_EXTERNAL) {
                throw new StaffAccessException("marks out of range");
            }

            SubjectMark row = marks.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
                    staff.tenantId(), studentId, clazz.semester(), subjectCode).orElseGet(() -> {
                SubjectMark m = new SubjectMark();
                m.tenantId = staff.tenantId();
                m.studentId = studentId;
                m.semester = clazz.semester();
                m.subjectCode = subjectCode;
                return m;
            });

            // FINALIZED is one-way. If a published mark could be quietly returned to draft it
            // would vanish from the student's view with no trace, and "finalized" would mean
            // nothing. A correction is a new FINALIZED value, which audits as such.
            if (row.finalized() && target == MarkStatus.DRAFT) {
                throw new StaffAccessException("a finalized mark cannot be returned to draft");
            }

            row.subjectName = mine.subjectName;
            row.credits = mine.credits;
            row.internal = in.internal();
            row.external = in.external();
            row.status = target;
            row.enteredByStaffId = staff.staffId();
            row.updatedAt = Instant.now();
            marks.save(row);
            saved++;

            audits.save(new AcademicAudit(staff.tenantId(), studentId, staff.staffId(), staff.name(),
                    target == MarkStatus.FINALIZED
                            ? AcademicAudit.Kind.MARKS_FINALIZED : AcademicAudit.Kind.MARKS_DRAFT,
                    subjectCode,
                    (target == MarkStatus.FINALIZED ? "Finalized " : "Saved draft ")
                            + subjectCode + " " + row.total() + "/100 for semester "
                            + clazz.semester() + " (" + clazz.label() + ")."));

            if (target == MarkStatus.FINALIZED
                    && recomputeIfComplete(staff.tenantId(), studentId, clazz)) {
                recomputed.add(studentId);
            }
        }
        return new MarksWrite(saved, recomputed);
    }

    /**
     * THE GATE ON SemesterResult.
     *
     * A semester's SGPA is only republished once the student has a FINALIZED mark for EVERY
     * subject taught to their class that semester. Without this, finalizing the first of six
     * subjects would recompute an SGPA from one sixth of the data and overwrite a published
     * result with it.
     *
     * An EMPTY expected set means the timetable does not describe that semester — a past
     * semester, typically — and the safe answer there is to touch nothing at all.
     */
    private boolean recomputeIfComplete(String tenantId, String studentId, ClassKey clazz) {
        Set<String> expected = new HashSet<>();
        for (TeachingAssignment a : assignments.findByTenantIdAndDepartmentAndSemesterAndSection(
                tenantId, clazz.department(), clazz.semester(), clazz.section())) {
            expected.add(a.subjectCode);
        }
        if (expected.isEmpty()) return false;

        List<SubjectMark> semester = marks.findByTenantIdAndStudentIdAndSemesterOrderBySubjectCodeAsc(
                tenantId, studentId, clazz.semester());
        Set<String> finalized = new HashSet<>();
        for (SubjectMark m : semester) if (m.finalized()) finalized.add(m.subjectCode);
        if (!finalized.containsAll(expected)) return false;

        List<SubjectMark> published = semester.stream().filter(SubjectMark::finalized).toList();
        SemesterResult r = results.findByTenantIdAndStudentIdAndSemester(
                        tenantId, studentId, clazz.semester())
                .orElseGet(() -> new SemesterResult(tenantId, studentId, clazz.semester(), 0, 0));
        r.sgpa = SgpaMath.sgpa(published);
        r.credits = SgpaMath.credits(published);
        results.save(r);
        return true;
    }
}
