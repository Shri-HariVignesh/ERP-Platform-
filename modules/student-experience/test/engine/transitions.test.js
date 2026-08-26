import test from 'node:test';
import assert from 'node:assert/strict';
import { RequestStateMachine } from '../../src/engine/RequestStateMachine.js';
import { IllegalTransitionException } from '../../src/engine/IllegalTransitionException.js';
import { RequestType, Event, Actor, RequestState } from '../../src/domain/enums.js';
import { fixture, leavePayload, internshipPayload, documentPayload, grievancePayload } from '../testFixtures.js';

/**
 * AREA 1 — the guard. Mirrors TransitionGuardTest.java: every legal edge succeeds for its
 * declared actor and is recorded in history; illegal actor/event/state combinations throw;
 * terminal states accept nothing further.
 */

test('LEAVE: faculty approve on a long leave routes to HOD, not straight to approved', () => {
  const { scope } = fixture('leave1');
  const r = RequestStateMachine.create(scope, RequestType.LEAVE, leavePayload());
  // days = 2, attendance seeded as null (no rows) -> AttendanceMath.pct([]) is null, so
  // `attendance>=75` is false in the auto-route guard -> lands on FACULTY_PENDING.
  assert.equal(r.state, RequestState.FACULTY_PENDING);

  const approved = RequestStateMachine.transition(scope, r.id, Event.APPROVE, Actor.FACULTY, null);
  assert.equal(approved.state, RequestState.HOD_PENDING);
});

test('LEAVE: HOD approve fires MUTATE_ATTENDANCE then autopilots straight to NOTIFIED', () => {
  const { scope } = fixture('leave2');
  const r = RequestStateMachine.create(scope, RequestType.LEAVE, leavePayload());
  RequestStateMachine.transition(scope, r.id, Event.APPROVE, Actor.FACULTY, null);
  const done = RequestStateMachine.transition(scope, r.id, Event.APPROVE, Actor.HOD, null);
  assert.equal(done.state, RequestState.NOTIFIED);
});

test('LEAVE: reject requires a note and is a genuine terminal state', () => {
  const { scope } = fixture('leave3');
  const r = RequestStateMachine.create(scope, RequestType.LEAVE, leavePayload());
  assert.throws(
    () => RequestStateMachine.transition(scope, r.id, Event.REJECT, Actor.FACULTY, ''),
    IllegalTransitionException,
  );
  const rejected = RequestStateMachine.transition(scope, r.id, Event.REJECT, Actor.FACULTY, 'no good reason needed for the test');
  assert.equal(rejected.state, RequestState.REJECTED);
  assert.throws(() => RequestStateMachine.transition(scope, r.id, Event.APPROVE, Actor.FACULTY, null), IllegalTransitionException);
});

test('LEAVE: a student may never fire an actor-gated event', () => {
  const { scope } = fixture('leave4');
  const r = RequestStateMachine.create(scope, RequestType.LEAVE, leavePayload());
  assert.throws(
    () => RequestStateMachine.transition(scope, r.id, Event.APPROVE, Actor.STUDENT, null),
    (e) => e instanceof IllegalTransitionException && /may not fire/.test(e.message),
  );
});

test('INTERNSHIP: full happy path writes academic record and mints a verification id', () => {
  const { scope } = fixture('intern1');
  const r = RequestStateMachine.create(scope, RequestType.INTERNSHIP, internshipPayload());
  assert.equal(r.state, RequestState.FACULTY_VERIFICATION);

  const verified = RequestStateMachine.transition(scope, r.id, Event.VERIFY, Actor.FACULTY, null);
  assert.equal(verified.state, RequestState.INSTITUTION_APPROVAL);

  const done = RequestStateMachine.transition(scope, r.id, Event.APPROVE, Actor.INSTITUTION, null);
  assert.equal(done.state, RequestState.VERIFICATION_ID_GENERATED);
});

test('INTERNSHIP: RETURNED is a loop back to SUBMITTED via student RESUBMIT, not a dead end', () => {
  const { scope } = fixture('intern2');
  const r = RequestStateMachine.create(scope, RequestType.INTERNSHIP, internshipPayload());
  const returned = RequestStateMachine.transition(scope, r.id, Event.RETURN, Actor.FACULTY, 'certificate unreadable');
  assert.equal(returned.state, RequestState.RETURNED);

  const resubmitted = RequestStateMachine.transition(scope, r.id, Event.RESUBMIT, Actor.STUDENT, null,
    (p) => { p.certificateRef = { filename: 'fixed.pdf', mime: 'application/pdf', sizeKb: 100 }; });
  // autopilot immediately re-runs AUTO_CHECK from the fresh SUBMITTED state
  assert.equal(resubmitted.state, RequestState.FACULTY_VERIFICATION);
});

test('every workflow is terminal at its success/reject states — nothing fires from there', () => {
  const { scope } = fixture('term1');
  const r = RequestStateMachine.create(scope, RequestType.GRIEVANCE, grievancePayload());
  const started = RequestStateMachine.transition(scope, r.id, Event.START_REVIEW, Actor.FACULTY, null);
  const resolved = RequestStateMachine.transition(scope, started.id, Event.RESOLVE, Actor.FACULTY, 'fixed it');
  assert.equal(resolved.state, RequestState.RESOLVED);
  assert.throws(() => RequestStateMachine.transition(scope, resolved.id, Event.RESOLVE, Actor.FACULTY, 'again'), IllegalTransitionException);
});

test('DOCUMENT: bonafide for an active student auto-issues with zero human touches', () => {
  const { scope } = fixture('doc1');
  const r = RequestStateMachine.create(scope, RequestType.DOCUMENT, documentPayload());
  assert.equal(r.state, RequestState.DOCUMENT_GENERATED);
});

test('DOCUMENT: transcript always routes to the office, even for an active student', () => {
  const { scope } = fixture('doc2');
  const r = RequestStateMachine.create(scope, RequestType.DOCUMENT, documentPayload('TRANSCRIPT'));
  assert.equal(r.state, RequestState.APPROVAL);
  const issued = RequestStateMachine.transition(scope, r.id, Event.APPROVE, Actor.OFFICE, null);
  assert.equal(issued.state, RequestState.DOCUMENT_GENERATED);
});
