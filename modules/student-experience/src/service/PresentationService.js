import { Actor } from '../domain/enums.js';
import { TransitionMatrix } from '../engine/TransitionMatrix.js';
import { PayloadCodec } from '../payload/PayloadCodec.js';
import { requestHistoryRepo } from '../repo/requestHistoryRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { DisplayLabels } from '../view/DisplayLabels.js';

function fmt(iso) {
  const d = new Date(iso);
  return d.toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
}

function studentName(scope) {
  const s = studentRepo.findByIdAndTenantId(scope.studentId, scope.tenantId);
  return s ? s.name : 'You';
}

function tone(spec, state) {
  if (spec.terminalTone[state]) return spec.terminalTone[state];
  if (spec.offPath[state]) return spec.offPath[state].tone;
  return 'pending';
}

/** Type-agnostic. `skipped` is how an automated bypass becomes visible to the student. */
function steps(spec, r, rows, locale) {
  const visited = new Set(rows.map((h) => h.toState));

  const off = spec.offPath[r.state];
  if (off) {
    const out = spec.steps.map((s) => ({ label: DisplayLabels.workflowLabel(s.label, locale), status: visited.has(s.key) ? 'done' : 'pending' }));
    out.push({ label: DisplayLabels.workflowLabel(off.label, locale), status: 'failed' });
    return out;
  }

  const idx = spec.steps.findIndex((s) => s.key === r.state);
  const terminal = spec.isTerminal(r.state);
  return spec.steps.map((s, i) => {
    let status;
    if (i < idx) status = visited.has(s.key) ? 'done' : 'skipped';
    else if (i === idx) status = terminal ? 'done' : 'current';
    else status = 'pending';
    return { label: DisplayLabels.workflowLabel(s.label, locale), status };
  });
}

/**
 * The COLLAPSED card's one line. Deliberately does NOT carry the engine's effect-log prose —
 * the artifact bullets already show the outcome. Off-path states keep the human-written reason.
 */
function headline(spec, r, payload, rows, locale) {
  const last = rows.length ? rows[rows.length - 1] : null;
  const note = last && last.note ? last.note.trim() : '';

  if (spec.offPath[r.state]) {
    const label = DisplayLabels.workflowLabel(spec.offPath[r.state].label, locale);
    return note === '' ? `${label}.` : `${label} — “${note}”`;
  }
  const status = DisplayLabels.status(r.state, locale);
  if (status) return status;

  let handler = payload.handledBy(locale);
  if (!handler) {
    const edge = spec.from(r.state).find((t) => t.actor !== 'SYSTEM');
    handler = edge ? Actor.display(edge.actor, locale) : (locale === 'hi' ? 'कैंपसओएस' : 'CampusOS');
  }
  return DisplayLabels.currentlyWith(handler, locale);
}

function studentAction(spec, r, locale) {
  const edge = spec.from(r.state).find((t) => t.actor === 'STUDENT');
  if (!edge) return null;
  return {
    label: DisplayLabels.actionLabel(edge.label, locale), event: edge.event, tone: edge.tone,
    requiresNote: edge.requiresNote,
    inputLabel: edge.inputLabel ? DisplayLabels.actionLabel(edge.inputLabel, locale) : edge.inputLabel,
  };
}

function artifacts(raw) {
  return raw.map((a) => ({ ...a, label: DisplayLabels.proof(a.label) }));
}

function trail(rows, name, locale) {
  return rows.map((h) => ({
    transition: DisplayLabels.transition(h.fromState, h.toState, locale),
    actor: DisplayLabels.actor(h.actor, name, locale),
    note: h.note,
    effects: DisplayLabels.effects(h.effects, locale),
    proof: DisplayLabels.proof(h.effectLog),
    at: fmt(h.at),
    hasNote: !!(h.note && h.note.trim()),
    hasEffects: !!(h.effects && h.effects.trim()),
    hasProof: !!(h.effectLog && h.effectLog.trim()),
  }));
}

function card(scope, r, name, locale) {
  const spec = TransitionMatrix.spec(r.type);
  const payload = PayloadCodec.read(r.type, r.payload);
  const rows = requestHistoryRepo.findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(r.id, scope.tenantId, scope.studentId);

  return {
    id: r.id,
    type: r.type,
    typeLabel: DisplayLabels.type(r.type, locale),
    title: payload.title(locale),
    subtitle: payload.subtitle(locale),
    state: r.state,
    stateLabel: DisplayLabels.stateLabel(spec.labelFor(r.state), payload.handledBy(locale), locale),
    badgeTone: tone(spec, r.state),
    headline: headline(spec, r, payload, rows, locale),
    steps: steps(spec, r, rows, locale),
    studentAction: studentAction(spec, r, locale),
    artifacts: artifacts(payload.artifacts(locale)),
    createdAt: fmt(r.createdAt),
    updatedAt: fmt(r.updatedAt),
    trail: trail(rows, name, locale),
    isOpen() { return this.badgeTone !== 'success' && this.badgeTone !== 'danger'; },
  };
}

/**
 * Turns a Request of ANY type into one RequestCard, using only WorkflowSpec metadata and the
 * payload's own polymorphic title()/subtitle()/artifacts(). No switch on RequestType.
 *
 * `locale` is trailing and optional (default 'en') on every entry point here, matching
 * DisplayLabels' own convention — see the comment at the top of DisplayLabels.js.
 */
export const PresentationService = {
  cards(scope, requests, locale = 'en') {
    const name = studentName(scope);
    return requests.map((r) => card(scope, r, name, locale));
  },

  card(scope, r, locale = 'en') { return card(scope, r, studentName(scope), locale); },

  /**
   * THE STAFF RENDERING of a student's cards: same read model, minus links the staff member
   * is not authorized to follow. /documents/{id}/download is STUDENT-only.
   */
  staffCards(scope, requests, locale = 'en') {
    return this.cards(scope, requests, locale).map((c) => ({
      ...c,
      artifacts: c.artifacts.map((a) => (a.kind === 'DOCUMENT' ? { ...a, href: null } : a)),
    }));
  },
};
