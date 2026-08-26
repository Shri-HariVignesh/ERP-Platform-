import { db } from '../db/db.js';

export const studentAccountRepo = {
  /** The login lookup. Not tenant-scoped: authentication is what ESTABLISHES the tenant. */
  findByUsername(username) {
    return db.prepare('SELECT * FROM student_accounts WHERE username=?').get(username) ?? null;
  },

  findByIdAndTenantId(id, tenantId) {
    const r = db.prepare('SELECT * FROM student_accounts WHERE id=? AND tenantId=?').get(id, tenantId);
    return r ? { ...r, active: !!r.active } : null;
  },

  findByTenantIdAndStudentId(tenantId, studentId) {
    return db.prepare('SELECT * FROM student_accounts WHERE tenantId=? AND studentId=?')
      .get(tenantId, studentId) ?? null;
  },

  save(a) {
    db.prepare(`INSERT INTO student_accounts (id, tenantId, studentId, username, passwordHash, active)
      VALUES (@id,@tenantId,@studentId,@username,@passwordHash,@active)`)
      .run({ ...a, active: a.active ? 1 : 0 });
    return a;
  },
};
