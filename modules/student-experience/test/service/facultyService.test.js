import test from 'node:test';
import assert from 'node:assert/strict';
import { RequestService } from '../../src/service/RequestService.js';
import { FacultyService } from '../../src/service/FacultyService.js';
import { StaffScope } from '../../src/service/StaffScope.js';
import { staffUserRepo } from '../../src/repo/staffUserRepo.js';
import { teachingAssignmentRepo } from '../../src/repo/teachingAssignmentRepo.js';
import { requestHistoryRepo } from '../../src/repo/requestHistoryRepo.js';
import { db } from '../../src/db/db.js';
import { StaffRole, RequestType, Actor, Event } from '../../src/domain/enums.js';
import { fixture, grievancePayload } from '../testFixtures.js';

let counter = 0;
function facultyFixture(tenantId, name = 'Class Advisor') {
  counter++;
  const staffId = `st_fs_faculty_${counter}`;
  staffUserRepo.save({
    id: staffId, tenantId, username: `fsfaculty${counter}`, passwordHash: 'x',
    name, email: null, department: 'CSE', active: true, roles: [StaffRole.FACULTY],
  });
  teachingAssignmentRepo.save({
    tenantId, staffId, department: 'CSE', semester: 5, section: 'A',
    subjectCode: 'CS501', subjectName: 'Test Subject', credits: 4,
  });
  return new StaffScope(tenantId, staffId, name, 'CSE', [StaffRole.FACULTY],
    teachingAssignmentRepo.findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc(tenantId, staffId));
}

test('act() records which specific staff member fired the transition, not just their role', () => {
  const { scope } = fixture('fsactor');
  const advisor = facultyFixture(scope.tenantId, 'Prof. Test Advisor');

  const r = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload());
  FacultyService.act(advisor, r.id, Event.START_REVIEW, null);

  const rows = requestHistoryRepo.findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(r.id, scope.tenantId, scope.studentId);
  const last = rows[rows.length - 1];
  assert.equal(last.actor, Actor.FACULTY, 'role is still recorded as before');
  assert.equal(last.actedByStaffId, advisor.staffId, 'and now so is the specific staff member');
  assert.equal(last.actedByStaffName, 'Prof. Test Advisor');
});

test('recentlyClosed() returns requests this staff member personally closed, excludes open and out-of-scope ones', () => {
  const { scope } = fixture('fsclosed');
  const advisor = facultyFixture(scope.tenantId, 'Prof. Closer');
  const otherAdvisor = facultyFixture(scope.tenantId, 'Prof. Other');

  const closedByMe = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload());
  FacultyService.act(advisor, closedByMe.id, Event.START_REVIEW, null);
  FacultyService.act(advisor, closedByMe.id, Event.RESOLVE, 'Handled it');

  const stillOpen = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload());
  FacultyService.act(advisor, stillOpen.id, Event.START_REVIEW, null); // not resolved yet

  const closedBySomeoneElse = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload());
  FacultyService.act(otherAdvisor, closedBySomeoneElse.id, Event.START_REVIEW, null);
  FacultyService.act(otherAdvisor, closedBySomeoneElse.id, Event.RESOLVE, 'Handled by someone else');

  const ids = FacultyService.recentlyClosed(advisor).map((t) => t.card.id);
  assert.ok(ids.includes(closedByMe.id), 'must include what THIS staff member closed');
  assert.ok(!ids.includes(stillOpen.id), 'must exclude anything still open');
  assert.ok(!ids.includes(closedBySomeoneElse.id), 'must exclude what a different staff member closed, even with the same role');
});

test('recentlyClosed() excludes an item that was later reopened (escalated) after this staff member resolved it', () => {
  const { scope } = fixture('fsreopen');
  const advisor = facultyFixture(scope.tenantId, 'Prof. Resolver');

  const r = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload());
  FacultyService.act(advisor, r.id, Event.START_REVIEW, null);
  FacultyService.act(advisor, r.id, Event.RESOLVE, 'Resolved');

  // Student escalates to the Ombudsperson — the request is open again, even though the last
  // thing THIS staff member did was resolve it.
  RequestService.transition(scope, r.id, Event.ESCALATE, Actor.STUDENT, 'Not satisfied');

  assert.ok(!FacultyService.recentlyClosed(advisor).map((t) => t.card.id).includes(r.id));
});

test('recentlyClosed() respects sinceDays and does not look further back than requested', () => {
  const { scope } = fixture('fswindow');
  const advisor = facultyFixture(scope.tenantId, 'Prof. Windowed');

  const r = RequestService.create(scope, RequestType.GRIEVANCE, grievancePayload());
  FacultyService.act(advisor, r.id, Event.START_REVIEW, null);
  FacultyService.act(advisor, r.id, Event.RESOLVE, 'Resolved just now');

  assert.ok(FacultyService.recentlyClosed(advisor, 'en', 20, 7).map((t) => t.card.id).includes(r.id),
    'a just-closed item is within any reasonable window');

  // Backdate every history row for this request directly (test-only) to simulate the whole
  // thing having happened 30 days ago, so a 7-day window must exclude it. Backdating only the
  // RESOLVE row would leave it with an EARLIER `at` than the START_REVIEW row that preceded
  // it — an impossible history the DESC-ordered dedup in recentlyClosed() doesn't need to
  // handle, since a real transition's `at` is always later than the one before it.
  const monthAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
  db.prepare('UPDATE request_history SET at=? WHERE requestId=?').run(monthAgo, r.id);

  assert.ok(!FacultyService.recentlyClosed(advisor, 'en', 20, 7).map((t) => t.card.id).includes(r.id),
    'a resolution from 30 days ago falls outside a 7-day window');
});
