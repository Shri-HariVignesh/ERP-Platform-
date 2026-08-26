import { StaffRole } from '../domain/enums.js';
import { ClassKey } from './ClassKey.js';

/**
 * THE STAFF EQUIVALENT OF Scope. Built only from the authenticated principal — nothing in it
 * is ever read from a request parameter, which is what retires the client-supplied actor.
 *
 * Two different breadths live here:
 *   canSee(student)  — REQUEST breadth. Widens with role.
 *   teaches(class, subject) — ACADEMIC-WRITE breadth. Teaching assignment ONLY.
 */
export class StaffScope {
  constructor(tenantId, staffId, name, department, roles, assignments) {
    if (!tenantId || !tenantId.trim()) throw new Error('tenantId missing — staff query refused');
    if (!staffId || !staffId.trim()) throw new Error('staffId missing — staff query refused');
    this.tenantId = tenantId;
    this.staffId = staffId;
    this.name = name;
    this.department = department;
    this.roles = new Set(roles ?? []);
    this.assignments = assignments ?? [];
  }

  hasRole(r) { return this.roles.has(r); }

  /** The Actors this principal may ever present to the engine. Never STUDENT, never SYSTEM. */
  actors() { return [...this.roles].map((r) => StaffRole.actor(r)); }

  mayAct(a) { return this.actors().includes(a); }

  /**
   * Statutory committees required by UGC regulations. Deliberately NOT folded into canSee()/
   * roster(): those two govern the FULL student profile (attendance, marks, every other
   * request) and widening them institution-wide for, say, an Anti-Ragging Committee seat would
   * hand that committee everyone's academic record, not just the ragging complaints that are
   * actually theirs to see. Committee reach is instead a narrow, grievance-only, category-
   * gated path — see StaffScopeResolver.grievanceRoster() and GrievanceVisibility.js.
   */
  static COMMITTEE_ROLES = [
    StaffRole.ICC, StaffRole.ANTI_RAGGING, StaffRole.SC_ST_CELL,
    StaffRole.EQUAL_OPPORTUNITY_CELL, StaffRole.RTI_OFFICER, StaffRole.OMBUDSPERSON,
  ];

  isCommitteeMember() { return StaffScope.COMMITTEE_ROLES.some((r) => this.hasRole(r)); }

  /** REQUEST breadth. Tenant equality is a precondition of every branch. */
  canSee(s) {
    if (!s || this.tenantId !== s.tenantId) return false;
    if (this.hasRole(StaffRole.INSTITUTION) || this.hasRole(StaffRole.OFFICE)) return true;
    if (this.hasRole(StaffRole.HOD) && this.department && this.department === s.department) return true;
    if (this.hasRole(StaffRole.FACULTY)) {
      for (const a of this.assignments) if (ClassKey.of(a).matches(s)) return true;
    }
    return false;
  }

  /** True iff this staff member personally teaches this subject to this class. */
  teachesSubject(c, subjectCode) {
    return this.assignments.some((a) => ClassKey.of(a).equals(c) && a.subjectCode === subjectCode);
  }

  /** True iff this staff member teaches at least one subject to this class. */
  teaches(c) { return this.assignments.some((a) => ClassKey.of(a).equals(c)); }

  /** The classes this staff member teaches — the ONLY values the class picker is built from. */
  classes() {
    const seen = new Map();
    for (const a of this.assignments) {
      const k = ClassKey.of(a);
      if (!seen.has(k.token())) seen.set(k.token(), k);
    }
    return [...seen.values()];
  }

  subjectsIn(c) { return this.assignments.filter((a) => ClassKey.of(a).equals(c)); }

  authors() { return this.assignments.length > 0; }
}
