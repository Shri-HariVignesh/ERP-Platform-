import test from 'node:test';
import assert from 'node:assert/strict';
import { RequestStateMachine } from '../../src/engine/RequestStateMachine.js';
import { IllegalTransitionException } from '../../src/engine/IllegalTransitionException.js';
import { requestRepo } from '../../src/repo/requestRepo.js';
import { RequestType, Event, Actor } from '../../src/domain/enums.js';
import { fixture, documentPayload } from '../testFixtures.js';

/**
 * AREA 2 — scoping. Mirrors ScopingTest.java: a different tenant, and a different student
 * inside the SAME tenant — which is the leak a tenant-only check would miss.
 */

test('a student in another tenant cannot read the request', () => {
  const hari = fixture('hari');
  const meera = fixture('meera');

  const his = RequestStateMachine.create(hari.scope, RequestType.DOCUMENT, documentPayload('TRANSCRIPT'));

  assert.equal(requestRepo.findByIdAndTenantIdAndStudentId(his.id, meera.scope.tenantId, meera.scope.studentId), null);
  assert.deepEqual(requestRepo.findByTenantIdAndStudentIdOrderByCreatedAtDesc(meera.scope.tenantId, meera.scope.studentId), []);
  assert.equal(requestRepo.findByTenantIdAndStudentIdOrderByCreatedAtDesc(hari.scope.tenantId, hari.scope.studentId).length, 1);
});

test('a student in another tenant cannot act on the request', () => {
  const hari = fixture('hari2');
  const meera = fixture('meera2');

  const his = RequestStateMachine.create(hari.scope, RequestType.DOCUMENT, documentPayload('TRANSCRIPT'));
  assert.equal(his.state, 'APPROVAL');

  assert.throws(
    () => RequestStateMachine.transition(meera.scope, his.id, Event.APPROVE, Actor.OFFICE, null),
    (e) => e instanceof IllegalTransitionException && /not visible in this tenant\+student scope/.test(e.message),
  );

  assert.equal(requestRepo.findByIdAndTenantIdAndStudentId(his.id, hari.scope.tenantId, hari.scope.studentId).state, 'APPROVAL');
});

test('a classmate in the SAME tenant is still blocked — the scope is tenant AND student', () => {
  const owner = fixture('owner');
  const mate = fixture('mate');
  // force the "classmate" into the SAME tenant as owner, to prove student-id alone is checked
  const sameTenantMate = { tenantId: owner.scope.tenantId, studentId: mate.scope.studentId };

  const his = RequestStateMachine.create(owner.scope, RequestType.DOCUMENT, documentPayload('TRANSCRIPT'));

  assert.equal(requestRepo.findByIdAndTenantIdAndStudentId(his.id, sameTenantMate.tenantId, sameTenantMate.studentId), null);
});

test('the public verify capability is the only unscoped read, and it answers only for issued ids', async () => {
  const { verificationRepo } = await import('../../src/repo/verificationRepo.js');
  const { fixture: f2, documentPayload: doc2 } = await import('../testFixtures.js');
  const owner = f2('verifyowner');
  const r = RequestStateMachine.create(owner.scope, RequestType.DOCUMENT, doc2('BONAFIDE'));
  assert.equal(r.state, 'DOCUMENT_GENERATED');

  const rows = requestRepo.findByTenantIdAndStudentIdOrderByCreatedAtDesc(owner.scope.tenantId, owner.scope.studentId);
  const { PayloadCodec } = await import('../../src/payload/PayloadCodec.js');
  const payload = PayloadCodec.read(rows[0].type, rows[0].payload);
  assert.ok(payload.sys.verifyId, 'a verifyId must have been minted');

  const v = verificationRepo.findByVerifyId(payload.sys.verifyId);
  assert.ok(v);
  assert.equal(v.studentId, owner.scope.studentId);
  assert.equal(verificationRepo.findByVerifyId('NOT-A-REAL-ID'), null);
});
