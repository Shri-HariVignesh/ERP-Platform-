import { db } from '../db/db.js';

export const semesterResultRepo = {
  findByTenantIdAndStudentIdOrderBySemesterAsc(tenantId, studentId) {
    return db.prepare('SELECT * FROM semester_results WHERE tenantId=? AND studentId=? ORDER BY semester ASC')
      .all(tenantId, studentId);
  },

  /** The upsert key: one published aggregate per student per semester. */
  findByTenantIdAndStudentIdAndSemester(tenantId, studentId, semester) {
    return db.prepare('SELECT * FROM semester_results WHERE tenantId=? AND studentId=? AND semester=?')
      .get(tenantId, studentId, semester) ?? null;
  },

  save(r) {
    if (r.id) {
      db.prepare('UPDATE semester_results SET sgpa=@sgpa, credits=@credits WHERE id=@id').run(r);
    } else {
      const info = db.prepare(`INSERT INTO semester_results (tenantId, studentId, semester, sgpa, credits)
        VALUES (@tenantId,@studentId,@semester,@sgpa,@credits)`).run(r);
      r.id = info.lastInsertRowid;
    }
    return r;
  },
};
