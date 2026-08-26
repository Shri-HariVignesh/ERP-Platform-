import { StaffRole, Actor } from '../domain/enums.js';
import { staffUserRepo } from '../repo/staffUserRepo.js';
import { teachingAssignmentRepo } from '../repo/teachingAssignmentRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { StaffScope } from './StaffScope.js';
import { StaffAccessException } from './errors.js';
import { TransitionMatrix } from '../engine/TransitionMatrix.js';

/**
 * PRINCIPAL -> SCOPE -> ACTOR. The one place a staff identity becomes authority. Nothing here
 * reads a request parameter — tenantId, staffId, roles and teaching assignments all come from
 * the authenticated principal and the database.
 */
export const StaffScopeResolver = {
  current(principal) {
    if (!principal || principal.kind !== 'staff') {
      throw new StaffAccessException('not authenticated as staff');
    }
    const u = staffUserRepo.findByIdAndTenantId(principal.staffId, principal.tenantId);
    if (!u) throw new StaffAccessException('staff identity no longer valid');
    if (!u.active) throw new StaffAccessException('staff identity is inactive');

    // Re-read per request: revoking a teaching assignment takes effect on the next click.
    const mine = teachingAssignmentRepo.findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc(u.tenantId, u.id);
    return new StaffScope(u.tenantId, u.id, u.name, u.department, u.roles, mine);
  },

  /** REQUEST breadth, as one de-duplicated roster. */
  roster(scope) {
    const out = new Map();
    if (scope.hasRole(StaffRole.INSTITUTION) || scope.hasRole(StaffRole.OFFICE)) {
      for (const s of studentRepo.findByTenantIdOrderByRollNoAsc(scope.tenantId)) out.set(s.id, s);
    }
    if (scope.hasRole(StaffRole.HOD) && scope.department) {
      for (const s of studentRepo.findByTenantIdAndDepartmentOrderByRollNoAsc(scope.tenantId, scope.department)) out.set(s.id, s);
    }
    if (scope.hasRole(StaffRole.FACULTY)) {
      for (const c of scope.classes()) {
        for (const s of studentRepo.findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc(
          scope.tenantId, c.department, c.semester, c.section)) out.set(s.id, s);
      }
    }
    return [...out.values()];
  },

  rosterIds(scope) { return this.roster(scope).map((s) => s.id); },

  /** The class roster, for attendance and marks. Requires the staff member to teach it. */
  classRoster(scope, c) {
    if (!scope.teaches(c)) throw new StaffAccessException('not a class you teach');
    return studentRepo.findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc(
      scope.tenantId, c.department, c.semester, c.section);
  },

  /** Resolves a studentId to a Student this staff member is actually allowed to see. */
  studentInScope(scope, studentId) {
    const s = studentRepo.findByIdAndTenantId(studentId, scope.tenantId);
    if (!s) throw new StaffAccessException('student not in scope');
    if (!scope.canSee(s)) throw new StaffAccessException('student not in scope');
    return s;
  },

  /**
   * THE RETIREMENT OF THE CLIENT-SUPPLIED ACTOR. The Actor is whatever the FROZEN MATRIX says
   * is the decision-maker for (this type, this state, this event), intersected with the roles
   * this principal actually holds. Exactly one survivor is required.
   */
  actorFor(scope, r, event) {
    const candidates = [];
    for (const t of TransitionMatrix.spec(r.type).from(r.state)) {
      if (t.event !== event) continue;
      if (t.actor === Actor.SYSTEM || t.actor === Actor.STUDENT) continue;
      if (!scope.mayAct(t.actor)) continue;
      if (!candidates.includes(t.actor)) candidates.push(t.actor);
    }
    if (candidates.length !== 1) throw new StaffAccessException('no role of yours may take that action at this stage');
    return candidates[0];
  },

  /** The staff actions on a card, filtered to the ones THIS principal may actually fire. */
  permitted(scope, r) {
    const out = [];
    for (const t of TransitionMatrix.spec(r.type).from(r.state)) {
      if (t.actor === Actor.SYSTEM || t.actor === Actor.STUDENT) continue;
      if (scope.mayAct(t.actor)) out.push(t);
    }
    return out;
  },
};
