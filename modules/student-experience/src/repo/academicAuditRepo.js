import { db } from '../db/db.js';

export const academicAuditRepo = {
  findByTenantIdAndStudentIdOrderByAtDesc(tenantId, studentId) {
    return db.prepare('SELECT * FROM academic_audit WHERE tenantId=? AND studentId=? ORDER BY at DESC')
      .all(tenantId, studentId);
  },

  /** Staff-scoped feed: the writes this cohort of students received. Backs Notifications. */
  findByTenantIdAndStudentIdInOrderByAtDesc(tenantId, studentIds) {
    if (studentIds.length === 0) return [];
    const ph = studentIds.map(() => '?').join(',');
    return db.prepare(`SELECT * FROM academic_audit WHERE tenantId=? AND studentId IN (${ph}) ORDER BY at DESC`)
      .all(tenantId, ...studentIds);
  },

  save(a) {
    const info = db.prepare(`INSERT INTO academic_audit
      (tenantId, studentId, staffId, staffName, kind, subjectCode, detail, at)
      VALUES (@tenantId,@studentId,@staffId,@staffName,@kind,@subjectCode,@detail,@at)`).run(a);
    a.id = info.lastInsertRowid;
    return a;
  },
};
