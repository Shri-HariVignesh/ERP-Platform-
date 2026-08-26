import test from 'node:test';
import assert from 'node:assert/strict';
import { DisplayLabels } from '../../src/view/DisplayLabels.js';

test('proof() strips dev asides and lowercases SCREAMING_SNAKE tokens without touching serials', () => {
  const raw = 'AttendanceRecord mutated: 2 class day(s) (declared non-goal notification infra). '
    + 'ATTENDANCE_MUTATED complete. Serial SNIT-2026-P64HVW unaffected.';
  const out = DisplayLabels.proof(raw);
  assert.ok(!out.includes('declared non-goal'));
  assert.ok(out.includes('attendance mutated'));
  assert.ok(out.includes('SNIT-2026-P64HVW'), 'a serial with an underscore-free hyphenated id must survive untouched');
});

test('publicDetail() strips serial and copy-count before a verifier ever sees it', () => {
  const out = DisplayLabels.publicDetail('Serial BON/2026/00142 · 2 copy/copies · Passport application');
  assert.equal(out.includes('Serial'), false);
  assert.equal(out.includes('copy'), false);
});

test('stateLabel() only overrides the generic "Assigned to desk" label', () => {
  assert.equal(DisplayLabels.stateLabel('Assigned to desk', 'Hostel Warden Office'), 'Assigned to Hostel Warden Office');
  assert.equal(DisplayLabels.stateLabel('With HOD', 'Hostel Warden Office'), 'With HOD');
});

test('actor() reads SYSTEM as automated and STUDENT as the student\'s own name', () => {
  assert.equal(DisplayLabels.actor('SYSTEM', 'Hari'), 'Automated');
  assert.equal(DisplayLabels.actor('STUDENT', 'Hari'), 'Hari');
  assert.equal(DisplayLabels.actor('STUDENT', null), 'You');
});
