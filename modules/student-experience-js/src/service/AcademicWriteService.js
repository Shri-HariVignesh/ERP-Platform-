import { attendanceRepo } from '../repo/attendanceRepo.js';
import { subjectMarkRepo } from '../repo/subjectMarkRepo.js';
import { semesterResultRepo } from '../repo/semesterResultRepo.js';
import { teachingAssignmentRepo } from '../repo/teachingAssignmentRepo.js';
import { academicAuditRepo } from '../repo/academicAuditRepo.js';
import { StaffScopeResolver } from './StaffScopeResolver.js';
import { AttendanceMath } from './AttendanceMath.js';
import { SgpaMath } from './SgpaMath.js';
import { AttendanceStatus, MarkStatus, AcademicAuditKind } from '../domain/enums.js';
import { StaffAccessException } from './errors.js';
import { db } from '../db/db.js';

/**
 * CATEGORY B — FACULTY ACADEMIC AUTHORING. The counterpart of RequestStateMachine, and the
 * ONLY path by which attendance or marks are mutated. Not requests — nobody approves a
 * register — but they carry the same three disciplines: SCOPED, AUDITED, COHERENT.
 */
export const AcademicWriteService = {
  /* ================================ ATTENDANCE ================================ */

  /**
   * Marks one class day. There is no subject dimension on the attendance row — the subject is
   * what AUTHORIZES the write; the row itself stays one-per-day.
   */
  markAttendance(staff, clazz, subjectCode, date, input) {
    return db.transaction(() => {
      if (!staff.teachesSubject(clazz, subjectCode)) {
        throw new StaffAccessException('not a subject you teach for this class');
      }
      if (!date || date > new Date().toISOString().slice(0, 10)) {
        throw new StaffAccessException('a class day in the future cannot be marked');
      }

      const roster = StaffScopeResolver.classRoster(staff, clazz);
      const allowed = new Set(roster.map((s) => s.id));

      let marked = 0, leaveProtected = 0;
      const pct = {};

      for (const [studentId, status] of Object.entries(input)) {
        if (!status) continue;
        if (!allowed.has(studentId)) throw new StaffAccessException('student not in a class you teach');
        // A faculty register records presence. APPROVED_LEAVE belongs to the leave workflow.
        if (status !== AttendanceStatus.PRESENT && status !== AttendanceStatus.ABSENT) {
          throw new StaffAccessException('only present or absent may be marked');
        }

        let row = attendanceRepo.findByTenantIdAndStudentIdAndDate(staff.tenantId, studentId, date);
        if (!row) row = { tenantId: staff.tenantId, studentId, date, status: AttendanceStatus.SCHEDULED, sourceRequestId: null, markedByStaffId: null };

        // INTEGRITY: an HOD-approved leave day is not a faculty member's to overwrite.
        if (row.status === AttendanceStatus.APPROVED_LEAVE) { leaveProtected++; continue; }

        row.status = status;
        row.markedByStaffId = staff.staffId;
        attendanceRepo.save(row);
        marked++;

        const now = AttendanceMath.pct(attendanceRepo.findByTenantIdAndStudentId(staff.tenantId, studentId));
        pct[studentId] = now;

        academicAuditRepo.save({
          tenantId: staff.tenantId, studentId, staffId: staff.staffId, staffName: staff.name,
          kind: AcademicAuditKind.ATTENDANCE, subjectCode,
          detail: `Marked ${status.toLowerCase()} for ${date} (${clazz.label()}). Attendance now ${now}%.`,
          at: new Date().toISOString(),
        });
      }
      return { marked, leaveProtected, pctByStudent: pct };
    })();
  },

  /* =================================== MARKS =================================== */

  /**
   * Saves a subject's marks for a class, as DRAFT or FINALIZED. DRAFT is invisible to the
   * student and never touches SemesterResult. FINALIZED publishes.
   */
  saveMarks(staff, clazz, subjectCode, input, target) {
    return db.transaction(() => {
      if (!staff.teachesSubject(clazz, subjectCode)) {
        throw new StaffAccessException('not a subject you teach for this class');
      }
      const mine = staff.subjectsIn(clazz).find((a) => a.subjectCode === subjectCode);
      if (!mine) throw new StaffAccessException('not a subject you teach for this class');

      const roster = StaffScopeResolver.classRoster(staff, clazz);
      const allowed = new Set(roster.map((s) => s.id));

      let saved = 0;
      const recomputed = [];

      for (const [studentId, mark] of Object.entries(input)) {
        if (!mark) continue;
        if (!allowed.has(studentId)) throw new StaffAccessException('student not in a class you teach');
        if (mark.internal < 0 || mark.internal > SgpaMath.MAX_INTERNAL
          || mark.external < 0 || mark.external > SgpaMath.MAX_EXTERNAL) {
          throw new StaffAccessException('marks out of range');
        }

        let row = subjectMarkRepo.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
          staff.tenantId, studentId, clazz.semester, subjectCode);
        if (!row) {
          row = { tenantId: staff.tenantId, studentId, semester: clazz.semester, subjectCode,
            subjectName: null, internal: 0, external: 0, credits: 0, status: MarkStatus.DRAFT, enteredByStaffId: null };
        }

        // FINALIZED is one-way. A correction is a new FINALIZED value, audited as such.
        if (row.status === MarkStatus.FINALIZED && target === MarkStatus.DRAFT) {
          throw new StaffAccessException('a finalized mark cannot be returned to draft');
        }

        row.subjectName = mine.subjectName;
        row.credits = mine.credits;
        row.internal = mark.internal;
        row.external = mark.external;
        row.status = target;
        row.enteredByStaffId = staff.staffId;
        row.updatedAt = new Date().toISOString();
        subjectMarkRepo.save(row);
        saved++;

        academicAuditRepo.save({
          tenantId: staff.tenantId, studentId, staffId: staff.staffId, staffName: staff.name,
          kind: target === MarkStatus.FINALIZED ? AcademicAuditKind.MARKS_FINALIZED : AcademicAuditKind.MARKS_DRAFT,
          subjectCode,
          detail: `${target === MarkStatus.FINALIZED ? 'Finalized' : 'Saved draft'} ${subjectCode} `
            + `${row.internal + row.external}/100 for semester ${clazz.semester} (${clazz.label()}).`,
          at: new Date().toISOString(),
        });

        if (target === MarkStatus.FINALIZED && recomputeIfComplete(staff.tenantId, studentId, clazz)) {
          recomputed.push(studentId);
        }
      }
      return { saved, recomputed };
    })();
  },
};

/**
 * THE GATE ON SemesterResult. Only republished once the student has a FINALIZED mark for
 * EVERY subject taught to their class that semester — else a half-entered semester would
 * overwrite a published result with partial data.
 */
function recomputeIfComplete(tenantId, studentId, clazz) {
  const expected = new Set(teachingAssignmentRepo
    .findByTenantIdAndDepartmentAndSemesterAndSection(tenantId, clazz.department, clazz.semester, clazz.section)
    .map((a) => a.subjectCode));
  if (expected.size === 0) return false;

  const semester = subjectMarkRepo.findByTenantIdAndStudentIdAndSemesterOrderBySubjectCodeAsc(tenantId, studentId, clazz.semester);
  const finalized = new Set(semester.filter((m) => m.status === MarkStatus.FINALIZED).map((m) => m.subjectCode));
  for (const code of expected) if (!finalized.has(code)) return false;

  const published = semester.filter((m) => m.status === MarkStatus.FINALIZED);
  let r = semesterResultRepo.findByTenantIdAndStudentIdAndSemester(tenantId, studentId, clazz.semester);
  if (!r) r = { tenantId, studentId, semester: clazz.semester, sgpa: 0, credits: 0 };
  r.sgpa = SgpaMath.sgpa(published);
  r.credits = SgpaMath.credits(published);
  semesterResultRepo.save(r);
  return true;
}
