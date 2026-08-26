import { db } from '../db/db.js';

export const verificationRepo = {
  /** Public QR target — deliberately unscoped. See repo/README.md. */
  findByVerifyId(verifyId) {
    return db.prepare('SELECT * FROM verifications WHERE verifyId=?').get(verifyId) ?? null;
  },

  save(v) {
    db.prepare(`INSERT INTO verifications (verifyId, tenantId, studentId, kind, subject, detail, sourceRequestId, issuedAt)
      VALUES (@verifyId,@tenantId,@studentId,@kind,@subject,@detail,@sourceRequestId,@issuedAt)`).run(v);
    return v;
  },
};
