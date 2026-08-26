import { db } from '../db/db.js';

export const subjectMarkRepo = {
  findByTenantIdAndStudentIdOrderBySemesterAscSubjectCodeAsc(tenantId, studentId) {
    return db.prepare(`SELECT * FROM subject_marks WHERE tenantId=? AND studentId=?
      ORDER BY semester ASC, subjectCode ASC`).all(tenantId, studentId);
  },

  findByTenantIdAndStudentIdAndSemesterOrderBySubjectCodeAsc(tenantId, studentId, semester) {
    return db.prepare(`SELECT * FROM subject_marks WHERE tenantId=? AND studentId=? AND semester=?
      ORDER BY subjectCode ASC`).all(tenantId, studentId, semester);
  },

  /** The upsert key: one row per (tenant, student, semester, subject). */
  findByTenantIdAndStudentIdAndSemesterAndSubjectCode(tenantId, studentId, semester, subjectCode) {
    return db.prepare(`SELECT * FROM subject_marks WHERE tenantId=? AND studentId=? AND semester=? AND subjectCode=?`)
      .get(tenantId, studentId, semester, subjectCode) ?? null;
  },

  save(m) {
    if (m.id) {
      db.prepare(`UPDATE subject_marks SET subjectName=@subjectName, internal=@internal, external=@external,
        credits=@credits, status=@status, enteredByStaffId=@enteredByStaffId, updatedAt=@updatedAt WHERE id=@id`).run(m);
    } else {
      const info = db.prepare(`INSERT INTO subject_marks
        (tenantId, studentId, semester, subjectCode, subjectName, internal, external, credits, status, enteredByStaffId, updatedAt)
        VALUES (@tenantId,@studentId,@semester,@subjectCode,@subjectName,@internal,@external,@credits,@status,@enteredByStaffId,@updatedAt)`)
        .run(m);
      m.id = info.lastInsertRowid;
    }
    return m;
  },
};
