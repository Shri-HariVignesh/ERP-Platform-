import { db } from '../db/db.js';

export const teachingAssignmentRepo = {
  /** Everything this staff member teaches — the root of academic-write authorization. */
  findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc(tenantId, staffId) {
    return db.prepare(`SELECT * FROM teaching_assignments WHERE tenantId=? AND staffId=?
      ORDER BY semester ASC, section ASC, subjectCode ASC`).all(tenantId, staffId);
  },

  /** Every subject taught to one class, across all staff — the "expected subject set". */
  findByTenantIdAndDepartmentAndSemesterAndSection(tenantId, department, semester, section) {
    return db.prepare(`SELECT * FROM teaching_assignments WHERE tenantId=? AND department=? AND semester=? AND section=?`)
      .all(tenantId, department, semester, section);
  },

  save(t) {
    const info = db.prepare(`INSERT INTO teaching_assignments
      (tenantId, staffId, department, semester, section, subjectCode, subjectName, credits)
      VALUES (@tenantId,@staffId,@department,@semester,@section,@subjectCode,@subjectName,@credits)`).run(t);
    t.id = info.lastInsertRowid;
    return t;
  },
};
