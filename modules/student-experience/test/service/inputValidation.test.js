import test from 'node:test';
import assert from 'node:assert/strict';
import { AcademicWriteService } from '../../src/service/AcademicWriteService.js';
import { StaffAccessException } from '../../src/service/errors.js';
import { DocumentPayload } from '../../src/payload/DocumentPayload.js';
import { StaffScope } from '../../src/service/StaffScope.js';
import { ClassKey } from '../../src/service/ClassKey.js';
import { fixture } from '../testFixtures.js';
import { staffUserRepo } from '../../src/repo/staffUserRepo.js';
import { teachingAssignmentRepo } from '../../src/repo/teachingAssignmentRepo.js';
import { StaffRole } from '../../src/domain/enums.js';

let counter = 0;
function staffFixture() {
  counter++;
  const { scope } = fixture(`ivstu${counter}`);
  const staffId = `st_iv_${counter}`;
  staffUserRepo.save({
    id: staffId, tenantId: scope.tenantId, username: `iv${counter}`, passwordHash: 'x',
    name: 'IV Staff', email: null, department: 'CSE', active: true, roles: [StaffRole.FACULTY],
  });
  teachingAssignmentRepo.save({
    tenantId: scope.tenantId, staffId, department: 'CSE', semester: 5, section: 'A',
    subjectCode: 'CS501', subjectName: 'Test Subject', credits: 4,
  });
  const clazz = new ClassKey('CSE', 5, 'A');
  const staffScope = new StaffScope(scope.tenantId, staffId, 'IV Staff', 'CSE', [StaffRole.FACULTY],
    teachingAssignmentRepo.findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc(scope.tenantId, staffId));
  return { staffScope, clazz };
}

/**
 * Regression tests for two bugs found in QA: JS's `NaN < 0` and `NaN > MAX` are both false, so
 * a non-numeric field used to sail past a bare range check and get persisted, silently
 * corrupting the record (and, for marks, poisoning every SGPA computed from it).
 */

test('markAttendance rejects a non-date string instead of silently persisting it', () => {
  const { staffScope, clazz } = staffFixture();
  assert.throws(
    () => AcademicWriteService.markAttendance(staffScope, clazz, 'CS501', 'not-a-date', { s1: 'PRESENT' }),
    StaffAccessException,
  );
});

test('markAttendance rejects a format-valid but implausible date (e.g. year 0000)', () => {
  const { staffScope, clazz } = staffFixture();
  // "0000-01-01" passes a naive regex/Date.parse check and sorts before today lexicographically,
  // so only an explicit floor catches it — this is the exact case that slipped through once.
  assert.throws(
    () => AcademicWriteService.markAttendance(staffScope, clazz, 'CS501', '0000-01-01', { s1: 'PRESENT' }),
    StaffAccessException,
  );
});

test('saveMarks rejects a non-numeric internal/external value instead of persisting NaN', () => {
  const { staffScope, clazz } = staffFixture();
  assert.throws(
    () => AcademicWriteService.saveMarks(staffScope, clazz, 'CS501',
      { s1: { internal: Number.parseInt('abc', 10), external: 30 } }, 'DRAFT'),
    StaffAccessException,
  );
});

test('DocumentPayload.validate() rejects a non-numeric copies value instead of persisting NaN', () => {
  const p = new DocumentPayload();
  p.docType = 'BONAFIDE';
  p.purpose = 'test';
  p.copies = Number.parseInt('abc', 10);
  assert.throws(() => p.validate());
});
