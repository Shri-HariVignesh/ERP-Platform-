import { db } from '../db/db.js';

function withRoles(u) {
  if (!u) return null;
  const roles = db.prepare('SELECT role FROM staff_user_roles WHERE staffId=?').all(u.id).map((r) => r.role);
  return { ...u, active: !!u.active, roles };
}

export const staffUserRepo = {
  /** The login lookup. Not tenant-scoped: authentication is what ESTABLISHES the tenant. */
  findByUsername(username) {
    return withRoles(db.prepare('SELECT * FROM staff_users WHERE username=?').get(username));
  },

  findByIdAndTenantId(id, tenantId) {
    return withRoles(db.prepare('SELECT * FROM staff_users WHERE id=? AND tenantId=?').get(id, tenantId));
  },

  save(u) {
    db.prepare(`INSERT INTO staff_users (id, tenantId, username, passwordHash, name, email, department, active)
      VALUES (@id,@tenantId,@username,@passwordHash,@name,@email,@department,@active)`)
      .run({ ...u, active: u.active ? 1 : 0 });
    const ins = db.prepare('INSERT INTO staff_user_roles (staffId, role) VALUES (?, ?)');
    for (const role of u.roles) ins.run(u.id, role);
    return u;
  },
};
