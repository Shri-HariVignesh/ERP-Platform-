import { Router } from 'express';
import { RequestType, StaffRole, AttendanceStatus, MarkStatus } from '../domain/enums.js';
import { FacultyService } from '../service/FacultyService.js';
import { AcademicWriteService } from '../service/AcademicWriteService.js';
import { StaffScopeResolver } from '../service/StaffScopeResolver.js';
import { AcademicService } from '../service/AcademicService.js';
import { ClassKey } from '../service/ClassKey.js';
import { Scope } from '../service/Scope.js';
import { SgpaMath } from '../service/SgpaMath.js';
import { tenantRepo } from '../repo/tenantRepo.js';
import { subjectMarkRepo } from '../repo/subjectMarkRepo.js';
import { attendanceRepo } from '../repo/attendanceRepo.js';
import { StaffAccessException } from '../service/errors.js';
import { IllegalTransitionException } from '../engine/IllegalTransitionException.js';
import { requireStaff } from './middleware/auth.js';
import { redirectAfterSave } from './middleware/sessionRedirect.js';
import { resolveStaff } from './safeRedirect.js';

export const facultyRoutes = Router();
facultyRoutes.use(requireStaff);

/**
 * Every handler starts by resolving a StaffScope from the AUTHENTICATED principal. The
 * role/assignment flags drive which nav tabs exist — presentation only: every POST re-derives
 * the scope and re-checks server-side, so hiding a tab is a courtesy and never the control.
 */
function base(req, res, nav) {
  const scope = StaffScopeResolver.current(req.session.principal);
  res.locals.staff = scope;
  res.locals.tenant = tenantRepo.findById(scope.tenantId);
  res.locals.nav = nav;
  res.locals.roleLabels = [...scope.roles].map((r) => StaffRole.display(r));
  res.locals.canLeave = scope.hasRole(StaffRole.FACULTY) || scope.hasRole(StaffRole.HOD);
  res.locals.canInternship = scope.hasRole(StaffRole.FACULTY) || scope.hasRole(StaffRole.INSTITUTION);
  res.locals.canAuthor = scope.authors();
  res.locals.pending = FacultyService.inbox(scope).length;
  res.locals.flash = req.session.flash ?? null;
  res.locals.error = req.session.error ?? null;
  delete req.session.flash;
  delete req.session.error;
  return scope;
}

function enc(v) { return encodeURIComponent(v); }

/* -------------------------------- 1. HOME -------------------------------- */

facultyRoutes.get('/', (req, res) => {
  const scope = base(req, res, 'home');
  res.locals.tasks = FacultyService.inbox(scope).slice(0, 5);
  res.locals.recent = FacultyService.notifications(scope, 6);
  res.locals.roster = StaffScopeResolver.roster(scope).length;
  res.locals.classes = scope.classes();
  res.render('faculty/home');
});

/* ------------------------------ 2. MY TASKS ------------------------------ */

facultyRoutes.get('/tasks', (req, res) => {
  const scope = base(req, res, 'tasks');
  const filter = req.query.filter;
  const type = (filter && filter !== 'ALL') ? filter : null;
  res.locals.tasks = FacultyService.inbox(scope, type);
  res.locals.filter = type ?? 'ALL';
  res.locals.types = Object.values(RequestType);
  res.locals.back = '/faculty/tasks';
  res.render('faculty/tasks');
});

/* ------------------------------ 3. STUDENTS ------------------------------ */

facultyRoutes.get('/students', (req, res) => {
  const scope = base(req, res, 'students');
  let roster = StaffScopeResolver.roster(scope);
  const q = req.query.q;
  if (q && q.trim()) {
    const needle = q.trim().toLowerCase();
    roster = roster.filter((s) => s.name.toLowerCase().includes(needle) || s.rollNo.toLowerCase().includes(needle));
  }
  res.locals.roster = roster;
  res.locals.q = q ?? '';
  res.render('faculty/students');
});

facultyRoutes.get('/students/:id', (req, res) => {
  const scope = base(req, res, 'students');
  const s = StaffScopeResolver.studentInScope(scope, req.params.id);
  const studentScope = new Scope(s.tenantId, s.id);

  res.locals.s = s;
  res.locals.className = ClassKey.of(s).label();
  res.locals.attendancePct = AcademicService.attendancePct(studentScope);
  res.locals.approvedLeaveDays = AcademicService.approvedLeaveDays(studentScope);
  res.locals.results = AcademicService.results(studentScope);
  res.locals.cgpa = AcademicService.cgpa(studentScope);
  res.locals.marks = AcademicService.publishedMarks(studentScope);
  res.locals.records = AcademicService.records(studentScope);
  res.locals.documents = AcademicService.documents(studentScope);
  res.locals.cards = FacultyService.requestsOf(scope, s);
  res.render('faculty/student');
});

/* --------------------------- 4/5. LEAVE, INTERNSHIP --------------------------- */

facultyRoutes.get('/leave', (req, res) => {
  const scope = base(req, res, 'leave');
  res.locals.tasks = FacultyService.inbox(scope, RequestType.LEAVE);
  res.locals.workflow = 'Leave';
  res.locals.back = '/faculty/leave';
  res.render('faculty/workflow');
});

facultyRoutes.get('/internship', (req, res) => {
  const scope = base(req, res, 'internship');
  res.locals.tasks = FacultyService.inbox(scope, RequestType.INTERNSHIP);
  res.locals.workflow = 'Internship';
  res.locals.back = '/faculty/internship';
  res.render('faculty/workflow');
});

/* ------------------------------ 6. ATTENDANCE ------------------------------ */

facultyRoutes.get('/attendance', (req, res) => {
  const scope = base(req, res, 'attendance');
  if (scope.classes().length === 0) return res.render('faculty/no-classes');

  const key = req.query.clazz ? ClassKey.parse(req.query.clazz) : scope.classes()[0];
  if (!scope.teaches(key)) throw new StaffAccessException('not a class you teach');

  const subjects = scope.subjectsIn(key);
  const subjectCode = req.query.subject || subjects[0].subjectCode;
  if (!scope.teachesSubject(key, subjectCode)) throw new StaffAccessException('not a subject you teach');

  const day = req.query.date || new Date().toISOString().slice(0, 10);
  const roster = StaffScopeResolver.classRoster(scope, key);

  const current = {}, pct = {};
  for (const s of roster) {
    const row = attendanceRepo.findByTenantIdAndStudentIdAndDate(scope.tenantId, s.id, day);
    if (row) current[s.id] = row.status;
    pct[s.id] = AcademicService.attendancePct(new Scope(s.tenantId, s.id));
  }

  res.locals.classes = scope.classes();
  res.locals.subjects = subjects;
  res.locals.clazz = key;
  res.locals.subject = subjectCode;
  res.locals.date = day;
  res.locals.today = new Date().toISOString().slice(0, 10);
  res.locals.roster = roster;
  res.locals.current = current;
  res.locals.pct = pct;
  res.render('faculty/attendance');
});

facultyRoutes.post('/attendance', (req, res) => {
  const scope = StaffScopeResolver.current(req.session.principal);
  const { clazz, subject, date } = req.body;
  const key = ClassKey.parse(clazz);

  const input = {};
  for (const [k, v] of Object.entries(req.body)) {
    if (!k.startsWith('status_') || !v) continue;
    input[k.slice('status_'.length)] = v;
  }

  const w = AcademicWriteService.markAttendance(scope, key, subject, date, input);
  let msg = `${w.marked} student(s) marked for ${date}.`;
  if (w.leaveProtected > 0) {
    msg += ` ${w.leaveProtected} left untouched — approved leave is owned by the leave workflow, not the register.`;
  }
  req.session.flash = msg;
  redirectAfterSave(req, res, `/faculty/attendance?clazz=${enc(clazz)}&subject=${enc(subject)}&date=${enc(date)}`);
});

/* --------------------------- 7. MARKS & RESULTS --------------------------- */

facultyRoutes.get('/marks', (req, res) => {
  const scope = base(req, res, 'marks');
  if (scope.classes().length === 0) return res.render('faculty/no-classes');

  const key = req.query.clazz ? ClassKey.parse(req.query.clazz) : scope.classes()[0];
  if (!scope.teaches(key)) throw new StaffAccessException('not a class you teach');

  const subjects = scope.subjectsIn(key);
  const subjectCode = req.query.subject || subjects[0].subjectCode;
  if (!scope.teachesSubject(key, subjectCode)) throw new StaffAccessException('not a subject you teach');

  const roster = StaffScopeResolver.classRoster(scope, key);
  const current = {};
  for (const s of roster) {
    const m = subjectMarkRepo.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(scope.tenantId, s.id, key.semester, subjectCode);
    if (m) {
      const total = m.internal + m.external;
      current[s.id] = {
        semester: m.semester, subjectCode: m.subjectCode, subjectName: m.subjectName,
        internal: m.internal, external: m.external, total, grade: SgpaMath.grade(total),
        credits: m.credits, finalized: m.status === MarkStatus.FINALIZED,
      };
    }
  }

  res.locals.classes = scope.classes();
  res.locals.subjects = subjects;
  res.locals.clazz = key;
  res.locals.subject = subjectCode;
  res.locals.roster = roster;
  res.locals.current = current;
  res.locals.maxInternal = SgpaMath.MAX_INTERNAL;
  res.locals.maxExternal = SgpaMath.MAX_EXTERNAL;
  res.render('faculty/marks');
});

facultyRoutes.post('/marks', (req, res) => {
  const scope = StaffScopeResolver.current(req.session.principal);
  const { clazz, subject, action } = req.body;
  const key = ClassKey.parse(clazz);
  const target = action === 'finalize' ? MarkStatus.FINALIZED : MarkStatus.DRAFT;

  const input = {};
  for (const [k, v] of Object.entries(req.body)) {
    if (!k.startsWith('internal_') || !v || !v.trim()) continue;
    const studentId = k.slice('internal_'.length);
    const external = req.body[`external_${studentId}`];
    if (!external || !external.trim()) continue;
    input[studentId] = { internal: Number.parseInt(v, 10), external: Number.parseInt(external, 10) };
  }

  const w = AcademicWriteService.saveMarks(scope, key, subject, input, target);
  let msg = `${w.saved} entr(ies) ${target === MarkStatus.FINALIZED ? 'finalized.' : 'saved as draft — not visible to students.'}`;
  if (w.recomputed.length > 0) {
    msg += ` ${w.recomputed.length} semester result(s) republished — every subject for the semester is now finalized.`;
  } else if (target === MarkStatus.FINALIZED) {
    msg += ' Semester results are unchanged until every subject of the semester is finalized.';
  }
  req.session.flash = msg;
  redirectAfterSave(req, res, `/faculty/marks?clazz=${enc(clazz)}&subject=${enc(subject)}`);
});

/* ---------------------------- 8. NOTIFICATIONS ---------------------------- */

facultyRoutes.get('/notifications', (req, res) => {
  const scope = base(req, res, 'notifications');
  res.locals.notices = FacultyService.notifications(scope, 60);
  res.render('faculty/notifications');
});

/* ------------------------- the one action endpoint ------------------------- */

/**
 * EVERY staff decision, for every workflow, goes through here. The body carries an event and
 * an optional note — never an actor. The actor is derived from the session.
 */
facultyRoutes.post('/requests/:id/act', (req, res) => {
  const scope = StaffScopeResolver.current(req.session.principal);
  const { event, note, back } = req.body;
  try {
    FacultyService.act(scope, req.params.id, event, note);
    req.session.flash = 'Done — the request has moved on.';
  } catch (e) {
    if (!(e instanceof IllegalTransitionException)) throw e;
    req.session.error = 'That move is not allowed from this stage.';
  }
  redirectAfterSave(req, res, resolveStaff(back ?? '/faculty/tasks'));
});
