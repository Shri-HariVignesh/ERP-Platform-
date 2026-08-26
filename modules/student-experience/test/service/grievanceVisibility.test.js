import test from 'node:test';
import assert from 'node:assert/strict';
import { RequestService } from '../../src/service/RequestService.js';
import { FacultyService } from '../../src/service/FacultyService.js';
import { StaffAccessException } from '../../src/service/errors.js';
import { StaffScope } from '../../src/service/StaffScope.js';
import { staffUserRepo } from '../../src/repo/staffUserRepo.js';
import { teachingAssignmentRepo } from '../../src/repo/teachingAssignmentRepo.js';
import { StaffRole, RequestType, GrievanceCategory } from '../../src/domain/enums.js';
import { fixture, grievancePayload } from '../testFixtures.js';

/**
 * A class advisor who genuinely teaches this student's class must NOT see (or act on) a
 * confidential grievance category — the class-advisor relationship is exactly the ordinary
 * case GrievanceVisibility.js exists to override. A committee member with no teaching
 * assignment at all must see it, institution-wide.
 */
let counter = 0;
function facultyFixture(tenantId) {
  counter++;
  const staffId = `st_gv_faculty_${counter}`;
  staffUserRepo.save({
    id: staffId, tenantId, username: `gvfaculty${counter}`, passwordHash: 'x',
    name: 'Class Advisor', email: null, department: 'CSE', active: true, roles: [StaffRole.FACULTY],
  });
  teachingAssignmentRepo.save({
    tenantId, staffId, department: 'CSE', semester: 5, section: 'A',
    subjectCode: 'CS501', subjectName: 'Test Subject', credits: 4,
  });
  return new StaffScope(tenantId, staffId, 'Class Advisor', 'CSE', [StaffRole.FACULTY],
    teachingAssignmentRepo.findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc(tenantId, staffId));
}

function committeeFixture(tenantId, role) {
  counter++;
  const staffId = `st_gv_committee_${counter}`;
  staffUserRepo.save({
    id: staffId, tenantId, username: `gvcommittee${counter}`, passwordHash: 'x',
    name: 'Committee Member', email: null, department: null, active: true, roles: [role],
  });
  return new StaffScope(tenantId, staffId, 'Committee Member', null, [role], []);
}

test('a class advisor cannot see a RAGGING grievance from a student they teach', () => {
  const { scope, student } = fixture('gvrag');
  const advisor = facultyFixture(scope.tenantId);
  const committee = committeeFixture(scope.tenantId, StaffRole.ANTI_RAGGING);

  const r = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload(GrievanceCategory.RAGGING));

  const advisorInbox = FacultyService.inbox(advisor).map((t) => t.card.id);
  const committeeInbox = FacultyService.inbox(committee).map((t) => t.card.id);
  assert.ok(!advisorInbox.includes(r.id), 'class advisor must not see the ragging complaint');
  assert.ok(committeeInbox.includes(r.id), 'Anti-Ragging Committee must see it, with no teaching assignment at all');

  assert.throws(
    () => FacultyService.act(advisor, r.id, 'START_REVIEW', null),
    (e) => e instanceof StaffAccessException,
    'a direct act() call must be blocked too, not just the listing',
  );

  const advisorRequestsOf = FacultyService.requestsOf(advisor, student).map((c) => c.id);
  assert.ok(!advisorRequestsOf.includes(r.id), 'must not leak into the student profile view either');
});

test('an ICC member cannot see a different confidential category (SC/ST) even though they can see RAGGING-adjacent students', () => {
  const { scope } = fixture('gvscst');
  const icc = committeeFixture(scope.tenantId, StaffRole.ICC);

  const r = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload(GrievanceCategory.SC_ST_DISCRIMINATION));

  assert.ok(!FacultyService.inbox(icc).map((t) => t.card.id).includes(r.id));
  assert.throws(() => FacultyService.act(icc, r.id, 'START_REVIEW', null), (e) => e instanceof StaffAccessException);
});

test('a committee\'s institution-wide reach does NOT sweep in an unrelated ORDINARY grievance from an out-of-roster student', () => {
  const { scope } = fixture('gvhostelwide');
  const antiRagging = committeeFixture(scope.tenantId, StaffRole.ANTI_RAGGING);

  // HOSTEL is not confidential — an ordinary category — from a student this committee member
  // has no teaching/HOD/institution relationship with at all (empty ordinary roster).
  const r = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload(GrievanceCategory.HOSTEL));

  assert.ok(!FacultyService.inbox(antiRagging).map((t) => t.card.id).includes(r.id),
    'the wide reach is for the committee\'s OWN confidential category only, not every ordinary grievance institution-wide');
  assert.throws(() => FacultyService.act(antiRagging, r.id, 'START_REVIEW', null), (e) => e instanceof StaffAccessException);
});

test('ordinary categories are unaffected — a class advisor still sees a HOSTEL grievance', () => {
  const { scope } = fixture('gvhostel');
  const advisor = facultyFixture(scope.tenantId);

  const r = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload(GrievanceCategory.HOSTEL));

  assert.ok(FacultyService.inbox(advisor).map((t) => t.card.id).includes(r.id));
});
