import test from 'node:test';
import assert from 'node:assert/strict';
import { AttendanceMath } from '../../src/service/AttendanceMath.js';
import { SgpaMath } from '../../src/service/SgpaMath.js';

test('AttendanceMath.pct excludes SCHEDULED days from the denominator', () => {
  const rows = [
    { status: 'PRESENT' }, { status: 'PRESENT' }, { status: 'ABSENT' },
    { status: 'SCHEDULED' }, { status: 'SCHEDULED' },
  ];
  // 2 of 3 counted days attended -> 66.7%
  assert.equal(AttendanceMath.pct(rows), 66.7);
});

test('AttendanceMath.pct treats APPROVED_LEAVE as attended', () => {
  const rows = [{ status: 'PRESENT' }, { status: 'APPROVED_LEAVE' }, { status: 'ABSENT' }, { status: 'ABSENT' }];
  assert.equal(AttendanceMath.pct(rows), 50);
});

test('AttendanceMath.pct returns null when nothing counts yet', () => {
  assert.equal(AttendanceMath.pct([{ status: 'SCHEDULED' }]), null);
  assert.equal(AttendanceMath.pct([]), null);
});

test('SgpaMath.gradePoint follows the ten-point scale, below 40 earns nothing', () => {
  assert.equal(SgpaMath.gradePoint(95), 10);
  assert.equal(SgpaMath.gradePoint(85), 9);
  assert.equal(SgpaMath.gradePoint(40), 5);
  assert.equal(SgpaMath.gradePoint(39), 0);
});

test('SgpaMath.sgpa is credit-weighted and a failed subject earns no credit at all', () => {
  const marks = [
    { internal: 35, external: 55, credits: 4 }, // 90 -> 10 grade points
    { internal: 10, external: 20, credits: 4 }, // 30 -> fail, 0 grade points, 0 credits counted
  ];
  // Only the first subject's 4 credits count in the denominator.
  assert.equal(SgpaMath.credits(marks), 4);
  assert.equal(SgpaMath.sgpa(marks), 10);
});

test('SgpaMath.sgpa is zero when every subject failed', () => {
  const marks = [{ internal: 5, external: 10, credits: 4 }];
  assert.equal(SgpaMath.sgpa(marks), 0);
});
