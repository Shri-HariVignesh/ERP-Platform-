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
import { GrievanceVisibility } from './GrievanceVisibility.js';
import { PayloadCodec } from '../payload/PayloadCodec.js';
import { RequestType } from '../domain/enums.js';
import { I18n } from '../view/i18n.js';

function fmt(iso) {
  return new Date(iso).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
}

/**
 * The set of GRIEVANCE request ids among `studentIds` that THIS staff member is not the
 * statutory committee for (see GrievanceVisibility). Computed once per call and checked
 * everywhere a staff member could otherwise see one: the inbox, a student's request list, and
 * the notification feed all derive from the same roster, so all three need the same filter.
 */
function hiddenGrievanceIds(scope, studentIds) {
  if (studentIds.length === 0) return new Set();
  const hidden = new Set();
  for (const r of requestRepo.findByTenantIdAndStudentIdInAndTypeOrderByCreatedAtDesc(
    scope.tenantId, studentIds, RequestType.GRIEVANCE)) {
    const payload = PayloadCodec.read(r.type, r.payload);
    if (!GrievanceVisibility.visible(scope, payload.category)) hidden.add(r.id);
  }
  return hidden;
}

function taskFrom(scope, r, student, canOpenProfile, locale) {
  // The card is built through the student's OWN scope, so it is byte-for-byte the read model
  // the student sees — same timeline, same trail, same DisplayLabels.
  const card = PresentationService.card(new Scope(r.tenantId, r.studentId), r, locale);
  const actions = StaffScopeResolver.permitted(scope, r)
    .map((t) => ({ label: DisplayLabels.actionLabel(t.label, locale), event: t.event, tone: t.tone, requiresNote: t.requiresNote }));
  return {
    card, studentId: student.id, studentName: student.name, rollNo: student.rollNo,
    className: ClassKey.of(student).label(), actions,
    // A committee member's institution-wide GRIEVANCE reach (see grievanceRoster()) is not a
    // grant to browse that student's full profile — canSee() still gates that separately, so
    // the "who-for" link on a task card must know whether to render as a link at all.
    canOpenProfile,
    actionable() { return this.actions.length > 0; },
  };
}

export const FacultyService = {
  /** MY TASKS. Every request in this staff member's tenant awaiting an Actor they hold. */
  inbox(scope, type = null, locale = 'en') {
    const states = InboxStates.awaiting(scope.actors());
    if (states.length === 0) return [];

    const out = [];

    const roster = StaffScopeResolver.roster(scope);
    const byId = new Map(roster.map((s) => [s.id, s]));
    const studentIds = [...byId.keys()];
    if (studentIds.length > 0) {
      const hidden = hiddenGrievanceIds(scope, studentIds);
      const rows = requestRepo.findByTenantIdAndStudentIdInAndStateInOrderByCreatedAtDesc(
        scope.tenantId, studentIds, states);
      for (const r of rows) {
        if (type !== null && r.type !== type) continue;
        if (hidden.has(r.id)) continue;
        out.push(taskFrom(scope, r, byId.get(r.studentId), true, locale));
      }
    }

    // A statutory committee's reach is institution-wide but GRIEVANCE-only, and only for the
    // category it is actually the designated recipient of — never a side door into every other
    // workflow, and never for a student already covered above (no duplicate task rows).
    if (scope.isCommitteeMember() && (type === null || type === RequestType.GRIEVANCE)) {
      const wide = StaffScopeResolver.grievanceRoster(scope).filter((s) => !byId.has(s.id));
      const wideById = new Map(wide.map((s) => [s.id, s]));
      const wideIds = [...wideById.keys()];
      if (wideIds.length > 0) {
        const rows = requestRepo.findByTenantIdAndStudentIdInAndStateInOrderByCreatedAtDesc(
          scope.tenantId, wideIds, states);
        for (const r of rows) {
          if (r.type !== RequestType.GRIEVANCE) continue;
          const category = PayloadCodec.read(r.type, r.payload).category;
          // The wide reach exists ONLY to grant a committee institution-wide sight of ITS OWN
          // confidential category — an ordinary (non-confidential) grievance from a student
          // outside the normal roster is not theirs to see just because they hold a committee
          // seat; that already came through the roster branch above if it was ever theirs.
          if (!GrievanceVisibility.isConfidential(category)) continue;
          if (!GrievanceVisibility.visible(scope, category)) continue;
          out.push(taskFrom(scope, r, wideById.get(r.studentId), scope.canSee(wideById.get(r.studentId)), locale));
        }
      }
    }
    return out;
  },

  /** Everything about one student's requests, for the Students view. Read-only. */
  requestsOf(scope, student, locale = 'en') {
    if (!scope.canSee(student)) throw new StaffAccessException('student not in scope');
    const hidden = hiddenGrievanceIds(scope, [student.id]);
    const visible = requestRepo.findByTenantIdAndStudentIdOrderByCreatedAtDesc(student.tenantId, student.id)
      .filter((r) => !hidden.has(r.id));
    const s = new Scope(student.tenantId, student.id);
    return PresentationService.staffCards(s, visible, locale);
  },

  /**
   * THE ONLY WAY A STAFF DECISION REACHES A REQUEST. No Actor parameter — it is derived from
   * the frozen matrix and the principal's roles, then handed to the SAME RequestStateMachine
   * guard the student side uses.
   */
  act(scope, requestId, event, note) {
    const rosterIds = StaffScopeResolver.rosterIds(scope);
    let r = rosterIds.length > 0
      ? requestRepo.findByIdAndTenantIdAndStudentIdIn(requestId, scope.tenantId, rosterIds)
      : null;

    // Same narrow, GRIEVANCE-only widening as inbox(): a committee member acting on a
    // confidential complaint from a student outside their ordinary roster. An ordinary
    // (non-confidential) grievance from an out-of-roster student is NOT covered by this
    // widening — that reach exists only for the committee's own confidential category.
    if (!r && scope.isCommitteeMember()) {
      const wideIds = StaffScopeResolver.grievanceRoster(scope).map((s) => s.id);
      const candidate = wideIds.length > 0
        ? requestRepo.findByIdAndTenantIdAndStudentIdIn(requestId, scope.tenantId, wideIds)
        : null;
      if (candidate && candidate.type === RequestType.GRIEVANCE
        && GrievanceVisibility.isConfidential(PayloadCodec.read(candidate.type, candidate.payload).category)) {
        r = candidate;
      }
    }
    if (!r) throw new StaffAccessException('request not in scope');

    // Visibility alone (inbox/requestsOf) does not stop a direct POST against a known id —
    // the same confidentiality rule has to gate the actual mutation, not just the listing.
    if (r.type === RequestType.GRIEVANCE) {
      const category = PayloadCodec.read(r.type, r.payload).category;
      if (!GrievanceVisibility.visible(scope, category)) throw new StaffAccessException('request not in scope');
    }

    const actor = StaffScopeResolver.actorFor(scope, r, event);
    return RequestStateMachine.transition(new Scope(r.tenantId, r.studentId), requestId, event, actor, note);
  },

  /** IN-APP ONLY, and derived — there is no notification table. */
  notifications(scope, limit, locale = 'en') {
    const roster = StaffScopeResolver.roster(scope);
    if (roster.length === 0) return [];
    const byId = new Map(roster.map((s) => [s.id, s]));
    const mine = new Set(InboxStates.awaiting(scope.actors()));
    const hidden = hiddenGrievanceIds(scope, [...byId.keys()]);

    const feed = [];
    for (const h of requestHistoryRepo.findByTenantIdAndStudentIdInOrderByIdDesc(scope.tenantId, [...byId.keys()])) {
      const s = byId.get(h.studentId);
      if (!s) continue;
      if (hidden.has(h.requestId)) continue;
      const needsMe = mine.has(h.toState);
      const kind = h.fromState === null
        ? I18n.t(locale, 'notif.newRequest')
        : (needsMe ? I18n.t(locale, 'notif.approvalRequired') : I18n.t(locale, 'notif.workflowUpdate'));
      feed.push({
        at: h.at,
        notice: {
          kind, title: `${s.name} · ${DisplayLabels.state(h.toState, locale)}`,
          detail: DisplayLabels.transition(h.fromState, h.toState, locale),
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
          kind: I18n.t(locale, 'notif.academicWrite'), title: `${s.name} · ${a.kind}`, detail: a.detail,
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
