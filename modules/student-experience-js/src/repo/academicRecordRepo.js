import { db } from '../db/db.js';

export const academicRecordRepo = {
  findByTenantIdAndStudentIdOrderByRecordedAtDesc(tenantId, studentId) {
    return db.prepare('SELECT * FROM academic_record WHERE tenantId=? AND studentId=? ORDER BY recordedAt DESC')
      .all(tenantId, studentId);
  },

  findByTenantIdAndStudentIdAndSourceRequestId(tenantId, studentId, sourceRequestId) {
    return db.prepare(`SELECT * FROM academic_record WHERE tenantId=? AND studentId=? AND sourceRequestId=?`)
      .all(tenantId, studentId, sourceRequestId);
  },

  save(a) {
    if (a.id) {
      db.prepare('UPDATE academic_record SET verifyId=@verifyId WHERE id=@id').run(a);
    } else {
      const info = db.prepare(`INSERT INTO academic_record
        (tenantId, studentId, kind, title, subtitle, credits, verifyId, sourceRequestId, recordedAt)
        VALUES (@tenantId,@studentId,@kind,@title,@subtitle,@credits,@verifyId,@sourceRequestId,@recordedAt)`)
        .run(a);
      a.id = info.lastInsertRowid;
    }
    return a;
  },
};
