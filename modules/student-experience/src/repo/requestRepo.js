import { db } from '../db/db.js';

export const requestRepo = {
  findByIdAndTenantIdAndStudentId(id, tenantId, studentId) {
    return db.prepare('SELECT * FROM requests WHERE id=? AND tenantId=? AND studentId=?')
      .get(id, tenantId, studentId) ?? null;
  },

  findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId) {
    return db.prepare('SELECT * FROM requests WHERE tenantId=? AND studentId=? ORDER BY createdAt DESC')
      .all(tenantId, studentId);
  },

  findByTenantIdAndStudentIdAndTypeOrderByCreatedAtDesc(tenantId, studentId, type) {
    return db.prepare(`SELECT * FROM requests WHERE tenantId=? AND studentId=? AND type=?
      ORDER BY createdAt DESC`).all(tenantId, studentId, type);
  },

  /**
   * THE STAFF INBOX. studentIds are resolved from the staff member's own scope BEFORE this
   * runs, so the query is bounded by the same two dimensions as every other finder.
   */
  findByTenantIdAndStudentIdInAndStateInOrderByCreatedAtDesc(tenantId, studentIds, states) {
    if (studentIds.length === 0 || states.length === 0) return [];
    const sPh = studentIds.map(() => '?').join(',');
    const stPh = states.map(() => '?').join(',');
    return db.prepare(`SELECT * FROM requests WHERE tenantId=? AND studentId IN (${sPh})
      AND state IN (${stPh}) ORDER BY createdAt DESC`).all(tenantId, ...studentIds, ...states);
  },

  /** One request, bounded by the staff member's OWN roster. Outside it: empty, not a leak. */
  findByIdAndTenantIdAndStudentIdIn(id, tenantId, studentIds) {
    if (studentIds.length === 0) return null;
    const ph = studentIds.map(() => '?').join(',');
    return db.prepare(`SELECT * FROM requests WHERE id=? AND tenantId=? AND studentId IN (${ph})`)
      .get(id, tenantId, ...studentIds) ?? null;
  },

  findByTenantIdAndStudentIdInAndTypeOrderByCreatedAtDesc(tenantId, studentIds, type) {
    if (studentIds.length === 0) return [];
    const ph = studentIds.map(() => '?').join(',');
    return db.prepare(`SELECT * FROM requests WHERE tenantId=? AND studentId IN (${ph}) AND type=?
      ORDER BY createdAt DESC`).all(tenantId, ...studentIds, type);
  },

  save(r) {
    // createdAt is in the UPDATE SET too: every normal fire()/transition() call re-saves the
    // SAME unchanged createdAt it read, so this is a no-op for them, and it's what lets
    // seed.js backdate a request's createdAt for SLA/aging demo data by re-saving over an
    // existing id — an `INSERT ... ON CONFLICT DO UPDATE` that didn't touch createdAt would
    // silently ignore that.
    db.prepare(`INSERT INTO requests (id, tenantId, studentId, type, state, payload, createdAt, updatedAt)
      VALUES (@id,@tenantId,@studentId,@type,@state,@payload,@createdAt,@updatedAt)
      ON CONFLICT(id) DO UPDATE SET state=@state, payload=@payload, createdAt=@createdAt, updatedAt=@updatedAt`).run(r);
    return r;
  },
};
