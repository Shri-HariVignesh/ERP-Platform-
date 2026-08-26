import { requestRepo } from '../repo/requestRepo.js';
import { requestHistoryRepo } from '../repo/requestHistoryRepo.js';
import { academicAuditRepo } from '../repo/academicAuditRepo.js';
import { PresentationService } from './PresentationService.js';
import { StaffScopeResolver } from './StaffScopeResolver.js';
import { RequestStateMachine } from '../engine/RequestStateMachine.js';
import { InboxStates } from './InboxStates.js';
import { ClassKey } from './ClassKey.js';
import { Scope } from './Scope.js';
import { StaffAccessException } from './errors.js';
import { DisplayLabels } from '../view/DisplayLabels.js';

function fmt(iso) {
  return new Date(iso).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
}

function taskFrom(scope, r, student) {
  // The card is built through the student's OWN scope, so it is byte-for-byte the read model
  // the student sees — same timeline, same trail, same DisplayLabels.
  const card = PresentationService.card(new Scope(r.tenantId, r.studentId), r);
  const actions = StaffScopeResolver.permitted(scope, r)
    .map((t) => ({ label: t.label, event: t.event, tone: t.tone, requiresNote: t.requiresNote }));
  return {
    card, studentId: student.id, studentName: student.name, rollNo: student.rollNo,
    className: ClassKey.of(student).label(), actions,
    actionable() { return this.actions.length > 0; },
  };
}

export const FacultyService = {
  /** MY TASKS. Every request in this staff member's tenant awaiting an Actor they hold. */
  inbox(scope, type = null) {
    const roster = StaffScopeResolver.roster(scope);
    if (roster.length === 0) return [];

    const states = InboxStates.awaiting(scope.actors());
    if (states.length === 0) return [];

    const byId = new Map(roster.map((s) => [s.id, s]));
    const rows = requestRepo.findByTenantIdAndStudentIdInAndStateInOrderByCreatedAtDesc(
      scope.tenantId, [...byId.keys()], states);

    const out = [];
    for (const r of rows) {
      if (type !== null && r.type !== type) continue;
      out.push(taskFrom(scope, r, byId.get(r.studentId)));
    }
    return out;
  },

  /** Everything about one student's requests, for the Students view. Read-only. */
  requestsOf(scope, student) {
    if (!scope.canSee(student)) throw new StaffAccessException('student not in scope');
    const s = new Scope(student.tenantId, student.id);
    return PresentationService.staffCards(s, requestRepo.findByTenantIdAndStudentIdOrderByCreatedAtDesc(student.tenantId, student.id));
  },

  /**
   * THE ONLY WAY A STAFF DECISION REACHES A REQUEST. No Actor parameter — it is derived from
   * the frozen matrix and the principal's roles, then handed to the SAME RequestStateMachine
   * guard the student side uses.
   */
  act(scope, requestId, event, note) {
    const rosterIds = StaffScopeResolver.rosterIds(scope);
    if (rosterIds.length === 0) throw new StaffAccessException('request not in scope');

    const r = requestRepo.findByIdAndTenantIdAndStudentIdIn(requestId, scope.tenantId, rosterIds);
    if (!r) throw new StaffAccessException('request not in scope');

    const actor = StaffScopeResolver.actorFor(scope, r, event);
    return RequestStateMachine.transition(new Scope(r.tenantId, r.studentId), requestId, event, actor, note);
  },

  /** IN-APP ONLY, and derived — there is no notification table. */
  notifications(scope, limit) {
    const roster = StaffScopeResolver.roster(scope);
    if (roster.length === 0) return [];
    const byId = new Map(roster.map((s) => [s.id, s]));
    const mine = new Set(InboxStates.awaiting(scope.actors()));

    const feed = [];
    for (const h of requestHistoryRepo.findByTenantIdAndStudentIdInOrderByIdDesc(scope.tenantId, [...byId.keys()])) {
      const s = byId.get(h.studentId);
      if (!s) continue;
      const needsMe = mine.has(h.toState);
      const kind = h.fromState === null ? 'New request' : (needsMe ? 'Approval required' : 'Workflow update');
      feed.push({
        at: h.at,
        notice: {
          kind, title: `${s.name} · ${DisplayLabels.state(h.toState)}`,
          detail: DisplayLabels.transition(h.fromState, h.toState),
          who: s.name, atFmt: fmt(h.at), href: `/faculty/students/${s.id}`,
        },
      });
    }
    for (const a of academicAuditRepo.findByTenantIdAndStudentIdInOrderByAtDesc(scope.tenantId, [...byId.keys()])) {
      const s = byId.get(a.studentId);
      if (!s) continue;
      feed.push({
        at: a.at,
        notice: {
          kind: 'Academic write', title: `${s.name} · ${a.kind}`, detail: a.detail,
          who: a.staffName, atFmt: fmt(a.at), href: `/faculty/students/${s.id}`,
        },
      });
    }
    feed.sort((x, y) => (x.at < y.at ? 1 : -1));
    return feed.slice(0, limit).map((f) => ({
      kind: f.notice.kind, title: f.notice.title, detail: f.notice.detail,
      who: f.notice.who, at: f.notice.atFmt, href: f.notice.href,
    }));
  },
};
