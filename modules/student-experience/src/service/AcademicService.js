import { attendanceRepo } from '../repo/attendanceRepo.js';
import { academicRecordRepo } from '../repo/academicRecordRepo.js';
import { semesterResultRepo } from '../repo/semesterResultRepo.js';
import { examTermRepo } from '../repo/examTermRepo.js';
import { documentRepo } from '../repo/documentRepo.js';
import { subjectMarkRepo } from '../repo/subjectMarkRepo.js';
import { AttendanceMath } from './AttendanceMath.js';
import { SgpaMath } from './SgpaMath.js';
import { DocType, AttendanceStatus, MarkStatus } from '../domain/enums.js';

function markRow(m) {
  const total = m.internal + m.external;
  return {
    semester: m.semester, subjectCode: m.subjectCode, subjectName: m.subjectName,
    internal: m.internal, external: m.external, total, grade: SgpaMath.grade(total),
    credits: m.credits, status: m.status === MarkStatus.FINALIZED ? 'Finalized' : 'Draft',
    finalized: m.status === MarkStatus.FINALIZED,
  };
}

export const AcademicService = {
  /**
   * THE STUDENT-VISIBLE MARKS LIST. Filtered to FINALIZED — that filter lives here, the only
   * service the student's Academic view reads, rather than in a template.
   */
  publishedMarks(s) {
    return subjectMarkRepo.findByTenantIdAndStudentIdOrderBySemesterAscSubjectCodeAsc(s.tenantId, s.studentId)
      .filter((m) => m.status === MarkStatus.FINALIZED)
      .map(markRow);
  },

  attendancePct(s) { return AttendanceMath.pct(attendanceRepo.findByTenantIdAndStudentId(s.tenantId, s.studentId)); },

  approvedLeaveDays(s) {
    return attendanceRepo.findByTenantIdAndStudentId(s.tenantId, s.studentId)
      .filter((a) => a.status === AttendanceStatus.APPROVED_LEAVE).length;
  },

  records(s) { return academicRecordRepo.findByTenantIdAndStudentIdOrderByRecordedAtDesc(s.tenantId, s.studentId); },

  results(s) { return semesterResultRepo.findByTenantIdAndStudentIdOrderBySemesterAsc(s.tenantId, s.studentId); },

  cgpa(s) {
    const rs = this.results(s);
    if (rs.length === 0) return 0;
    const credits = rs.reduce((sum, r) => sum + r.credits, 0);
    if (credits === 0) return 0;
    const weighted = rs.reduce((sum, r) => sum + r.sgpa * r.credits, 0);
    return Math.round((weighted / credits) * 100) / 100;
  },

  currentTerm(tenantId) {
    const t = examTermRepo.findByTenantId(tenantId);
    return t.length ? t[0] : null;
  },

  latestHallTicket(s) {
    return documentRepo.findByTenantIdAndStudentIdOrderByIssuedAtDesc(s.tenantId, s.studentId)
      .find((d) => d.docType === DocType.HALL_TICKET) ?? null;
  },

  documents(s) { return documentRepo.findByTenantIdAndStudentIdOrderByIssuedAtDesc(s.tenantId, s.studentId); },
};
