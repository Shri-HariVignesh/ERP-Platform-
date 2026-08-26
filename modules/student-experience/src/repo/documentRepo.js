import { db } from '../db/db.js';

export const documentRepo = {
  findByTenantIdAndStudentIdOrderByIssuedAtDesc(tenantId, studentId) {
    return db.prepare('SELECT * FROM documents WHERE tenantId=? AND studentId=? ORDER BY issuedAt DESC')
      .all(tenantId, studentId);
  },

  findByIdAndTenantIdAndStudentId(id, tenantId, studentId) {
    return db.prepare('SELECT * FROM documents WHERE id=? AND tenantId=? AND studentId=?')
      .get(id, tenantId, studentId) ?? null;
  },

  /** Serial sequence is student-scoped, so the count stays inside the scope rule. */
  countByTenantIdAndStudentId(tenantId, studentId) {
    return db.prepare('SELECT COUNT(*) n FROM documents WHERE tenantId=? AND studentId=?')
      .get(tenantId, studentId).n;
  },

  save(d) {
    const info = db.prepare(`INSERT INTO documents
      (tenantId, studentId, serialNo, docType, title, html, verifyId, sourceRequestId, issuedAt)
      VALUES (@tenantId,@studentId,@serialNo,@docType,@title,@html,@verifyId,@sourceRequestId,@issuedAt)`)
      .run(d);
    d.id = info.lastInsertRowid;
    return d;
  },
};
