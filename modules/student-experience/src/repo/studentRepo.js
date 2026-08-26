import { db } from '../db/db.js';

function row(r) { return r ? { ...r, active: !!r.active } : null; }

export const studentRepo = {
  /** Carries both dimensions already — the primary key IS the studentId. See repo/README.md. */
  findByIdAndTenantId(id, tenantId) {
    return row(db.prepare('SELECT * FROM students WHERE id = ? AND tenantId = ?').get(id, tenantId));
  },

  /** FACULTY breadth: exactly one class of one department. */
  findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc(tenantId, department, semester, section) {
    return db.prepare(`SELECT * FROM students WHERE tenantId=? AND department=? AND semester=? AND section=?
      ORDER BY rollNo ASC`).all(tenantId, department, semester, section).map(row);
  },

  /** HOD breadth: every class in their own department. */
  findByTenantIdAndDepartmentOrderByRollNoAsc(tenantId, department) {
    return db.prepare('SELECT * FROM students WHERE tenantId=? AND department=? ORDER BY rollNo ASC')
      .all(tenantId, department).map(row);
  },

  /** INSTITUTION / OFFICE breadth: the tenant, which is the scope root. */
  findByTenantIdOrderByRollNoAsc(tenantId) {
    return db.prepare('SELECT * FROM students WHERE tenantId=? ORDER BY rollNo ASC').all(tenantId).map(row);
  },

  save(s) {
    db.prepare(`INSERT INTO students (id, tenantId, rollNo, name, email, program, department, semester,
        section, feeDues, active, leaveBalance, advisorName, hodName, antiRaggingAffidavitAt)
      VALUES (@id,@tenantId,@rollNo,@name,@email,@program,@department,@semester,@section,@feeDues,
        @active,@leaveBalance,@advisorName,@hodName,@antiRaggingAffidavitAt)
      ON CONFLICT(id) DO UPDATE SET rollNo=@rollNo, name=@name, email=@email, program=@program,
        department=@department, semester=@semester, section=@section, feeDues=@feeDues,
        active=@active, leaveBalance=@leaveBalance, advisorName=@advisorName, hodName=@hodName,
        antiRaggingAffidavitAt=@antiRaggingAffidavitAt`)
      .run({ antiRaggingAffidavitAt: null, ...s, active: s.active ? 1 : 0 });
    return s;
  },
};
