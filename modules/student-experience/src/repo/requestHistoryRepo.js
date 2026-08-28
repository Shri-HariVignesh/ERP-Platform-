import { db } from '../db/db.js';

export const requestHistoryRepo = {
  findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(requestId, tenantId, studentId) {
    return db.prepare(`SELECT * FROM request_history WHERE requestId=? AND tenantId=? AND studentId=?
      ORDER BY id ASC`).all(requestId, tenantId, studentId);
  },

  findByTenantIdAndStudentIdOrderByIdDesc(tenantId, studentId) {
    return db.prepare('SELECT * FROM request_history WHERE tenantId=? AND studentId=? ORDER BY id DESC')
      .all(tenantId, studentId);
  },

  /** Staff-scoped activity feed. Backs Notifications and Home's recent activity. */
  findByTenantIdAndStudentIdInOrderByIdDesc(tenantId, studentIds) {
    if (studentIds.length === 0) return [];
    const ph = studentIds.map(() => '?').join(',');
    return db.prepare(`SELECT * FROM request_history WHERE tenantId=? AND studentId IN (${ph})
      ORDER BY id DESC`).all(tenantId, ...studentIds);
  },

  /** Every transition a specific staff member has personally fired, most recent first — backs
   * FacultyService.recentlyClosed(). */
  findByTenantIdAndActedByStaffIdOrderByAtDesc(tenantId, staffId) {
    return db.prepare(`SELECT * FROM request_history WHERE tenantId=? AND actedByStaffId=?
      ORDER BY at DESC`).all(tenantId, staffId);
  },

  save(h) {
    const info = db.prepare(`INSERT INTO request_history
      (requestId, tenantId, studentId, fromState, toState, actor, at, note, effects, effectLog,
       actedByStaffId, actedByStaffName)
      VALUES (@requestId,@tenantId,@studentId,@fromState,@toState,@actor,@at,@note,@effects,@effectLog,
       @actedByStaffId,@actedByStaffName)`)
      .run({ actedByStaffId: null, actedByStaffName: null, ...h });
    h.id = info.lastInsertRowid;
    return h;
  },
};
