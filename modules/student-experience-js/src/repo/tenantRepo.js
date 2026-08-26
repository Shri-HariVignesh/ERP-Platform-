import { db } from '../db/db.js';

/** The tenant is the scope root — see repo/README.md. */
export const tenantRepo = {
  findById(id) {
    return db.prepare('SELECT * FROM tenants WHERE id = ?').get(id) ?? null;
  },
  save(t) {
    db.prepare(`INSERT INTO tenants (id, name, shortName, city, accent) VALUES (@id,@name,@shortName,@city,@accent)
      ON CONFLICT(id) DO UPDATE SET name=@name, shortName=@shortName, city=@city, accent=@accent`).run(t);
    return t;
  },
};
