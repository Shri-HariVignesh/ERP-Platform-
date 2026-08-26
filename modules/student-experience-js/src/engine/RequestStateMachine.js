import { Actor } from '../domain/enums.js';
import { TransitionMatrix } from './TransitionMatrix.js';
import { TransitionContext } from './TransitionContext.js';
import { IllegalTransitionException } from './IllegalTransitionException.js';
import { PayloadCodec } from '../payload/PayloadCodec.js';
import { requestRepo } from '../repo/requestRepo.js';
import { requestHistoryRepo } from '../repo/requestHistoryRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { tenantRepo } from '../repo/tenantRepo.js';
import { SideEffectDispatcher } from './SideEffectDispatcher.js';
import { db } from '../db/db.js';
import crypto from 'node:crypto';

const AUTOPILOT_LIMIT = 12;

/**
 * THE GUARD. The only way a Request changes state anywhere in this application.
 *
 * transition() validates (state, event, actor, guard) against TransitionMatrix and throws
 * IllegalTransitionException on any move that is not in the matrix. On a legal move it appends
 * a RequestHistory row and fires the edge's declared side effects — in that order, in one
 * transaction.
 */
export const RequestStateMachine = {
  create(scope, type, payload) {
    return db.transaction(() => {
      payload.validate();
      // crypto.randomBytes, not a truncated ULID: a ULID's first characters are its
      // millisecond timestamp, so slicing one down to 8 chars collides constantly when many
      // requests are created within the same millisecond (exactly what seeding does).
      const r = {
        id: `req_${crypto.randomBytes(6).toString('hex')}`,
        tenantId: scope.tenantId,
        studentId: scope.studentId,
        type,
        state: TransitionMatrix.initial(type),
        payload: PayloadCodec.write(payload),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      requestRepo.save(r);

      requestHistoryRepo.save({
        requestId: r.id, tenantId: r.tenantId, studentId: r.studentId,
        fromState: null, toState: r.state, actor: Actor.STUDENT,
        at: new Date().toISOString(), note: 'Submitted by student', effects: '', effectLog: '',
      });

      return autopilot(scope, r);
    })();
  },

  transition(scope, requestId, event, actor, note, patch = null) {
    return db.transaction(() => {
      const r = requestRepo.findByIdAndTenantIdAndStudentId(requestId, scope.tenantId, scope.studentId);
      if (!r) {
        throw new IllegalTransitionException(
          `request ${safeForMessage(requestId)} is not visible in this tenant+student scope`);
      }
      const moved = fire(scope, r, event, actor, note, patch);
      return autopilot(scope, moved);
    })();
  },

  safeForMessage,
};

/** Applies exactly one edge, or throws. */
function fire(scope, r, event, actor, note, patch) {
  const spec = TransitionMatrix.spec(r.type);
  const student = studentRepo.findByIdAndTenantId(r.studentId, r.tenantId);
  if (!student) throw new IllegalTransitionException('student not in scope');
  const tenant = tenantRepo.findById(r.tenantId);
  if (!tenant) throw new IllegalTransitionException('tenant not found');

  const payload = PayloadCodec.read(r.type, r.payload);
  if (patch) patch(payload);

  const edge = select(spec, r, payload, student, event, actor);

  if (edge.requiresNote && (!note || !note.trim())) {
    throw new IllegalTransitionException(`${event} requires a reason`);
  }

  const from = r.state;
  const log = [];
  for (const fx of edge.effects) {
    log.push(...SideEffectDispatcher.fire(fx, r, payload, student, tenant));
  }

  r.state = edge.to;
  r.payload = PayloadCodec.write(payload);
  r.updatedAt = new Date().toISOString();
  requestRepo.save(r);

  requestHistoryRepo.save({
    requestId: r.id, tenantId: r.tenantId, studentId: r.studentId,
    fromState: from, toState: edge.to, actor, at: new Date().toISOString(),
    note: (!note || !note.trim()) ? null : note.trim(),
    effects: edge.effects.join(', '),
    effectLog: log.join(' '),
  });

  return r;
}

function select(spec, r, payload, student, event, actor) {
  const fromState = spec.from(r.state);
  if (fromState.length === 0) {
    throw new IllegalTransitionException(`${r.type} is terminal at ${r.state} — no transition is legal`);
  }
  const byEvent = fromState.filter((t) => t.event === event);
  if (byEvent.length === 0) {
    const legal = [...new Set(fromState.map((t) => t.event))].join(', ');
    throw new IllegalTransitionException(
      `${r.type}: no edge for event ${event} from state ${r.state} (legal events: ${legal})`);
  }
  const byActor = byEvent.filter((t) => t.actor === actor);
  if (byActor.length === 0) {
    const allowed = [...new Set(byEvent.map((t) => t.actor))].join(', ');
    throw new IllegalTransitionException(
      `${r.type}: ${actor} may not fire ${event} from ${r.state} (allowed actors: ${allowed})`);
  }
  const ctx = new TransitionContext(r, payload, student);
  const match = byActor.find((t) => t.guard(ctx));
  if (!match) {
    throw new IllegalTransitionException(`${r.type}: guard rejected ${event} from ${r.state}`);
  }
  return match;
}

/** Chains every SYSTEM edge whose guard passes. This is the automation. */
function autopilot(scope, r) {
  for (let i = 0; i < AUTOPILOT_LIMIT; i++) {
    const spec = TransitionMatrix.spec(r.type);
    const student = studentRepo.findByIdAndTenantId(r.studentId, r.tenantId);
    const payload = PayloadCodec.read(r.type, r.payload);
    const ctx = new TransitionContext(r, payload, student);

    const next = spec.from(r.state).find((t) => t.actor === Actor.SYSTEM && t.guard(ctx));
    if (!next) return r;

    r = fire(scope, r, next.event, Actor.SYSTEM, null, null);
  }
  return r;
}

/**
 * SECURITY (CWE-117, OWASP A09): requestId is caller-supplied and this message is both thrown
 * and logged. A CRLF in it could terminate a log record early and forge a following one.
 * Control characters are stripped and the value is capped, so an id can still be correlated
 * but can never author a line.
 */
function safeForMessage(raw) {
  if (raw === null || raw === undefined) return 'null';
  // eslint-disable-next-line no-control-regex
  const cleaned = String(raw).replace(/[\x00-\x1F\x7F]/g, '');
  return cleaned.length > 64 ? `${cleaned.slice(0, 64)}...` : cleaned;
}
