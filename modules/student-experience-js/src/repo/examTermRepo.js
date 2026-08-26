import { db } from '../db/db.js';

export const examTermRepo = {
  /** Institution-level data with no student dimension at all. See repo/README.md. */
  findByTenantId(tenantId) {
    return db.prepare('SELECT * FROM exam_terms WHERE tenantId=?').all(tenantId)
      .map((t) => ({ ...t, hallTicketReleased: !!t.hallTicketReleased }));
  },

  save(t) {
    const info = db.prepare(`INSERT INTO exam_terms (tenantId, name, startDate, endDate, hallTicketReleased)
      VALUES (@tenantId,@name,@startDate,@endDate,@hallTicketReleased)`)
      .run({ ...t, hallTicketReleased: t.hallTicketReleased ? 1 : 0 });
    t.id = info.lastInsertRowid;
    return t;
  },
};
