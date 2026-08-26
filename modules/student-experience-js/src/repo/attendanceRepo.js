import { db } from '../db/db.js';

export const attendanceRepo = {
  findByTenantIdAndStudentId(tenantId, studentId) {
    return db.prepare('SELECT * FROM attendance WHERE tenantId=? AND studentId=?').all(tenantId, studentId);
  },

  findByTenantIdAndStudentIdAndDateBetween(tenantId, studentId, from, to) {
    return db.prepare(`SELECT * FROM attendance WHERE tenantId=? AND studentId=? AND date>=? AND date<=?`)
      .all(tenantId, studentId, from, to);
  },

  /** The upsert key for faculty marking: one row per student per class day. */
  findByTenantIdAndStudentIdAndDate(tenantId, studentId, date) {
    return db.prepare('SELECT * FROM attendance WHERE tenantId=? AND studentId=? AND date=?')
      .get(tenantId, studentId, date) ?? null;
  },

  save(a) {
    if (a.id) {
      db.prepare(`UPDATE attendance SET status=@status, sourceRequestId=@sourceRequestId,
        markedByStaffId=@markedByStaffId WHERE id=@id`).run(a);
    } else {
      const info = db.prepare(`INSERT INTO attendance (tenantId, studentId, date, status, sourceRequestId, markedByStaffId)
        VALUES (@tenantId,@studentId,@date,@status,@sourceRequestId,@markedByStaffId)`).run(a);
      a.id = info.lastInsertRowid;
    }
    return a;
  },
};
