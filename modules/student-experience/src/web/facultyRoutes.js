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
import { DisplayLabels } from '../view/DisplayLabels.js';
import { I18n } from '../view/i18n.js';

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
  res.locals.roleLabels = [...scope.roles].map((r) => StaffRole.display(r, res.locals.locale));
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
  res.locals.tasks = FacultyService.inbox(scope, null, res.locals.locale).slice(0, 5);
  res.locals.recent = FacultyService.notifications(scope, 6, res.locals.locale);
  res.locals.roster = StaffScopeResolver.roster(scope).length;
  res.locals.classes = scope.classes();
  res.render('faculty/home');
});

/* ------------------------------ 2. MY TASKS ------------------------------ */

const SORTS = new Set(['urgency', 'newest', 'type']);
const SLA_RANK = { overdue: 0, due: 1, fresh: 2 };
const TYPE_ORDER = Object.values(RequestType);

/** Sorts an already-built task list in place semantics — 'urgency' (default): overdue first,
 * then due-soon, then fresh, oldest-within-a-bucket first (the most stale item in its bucket is
 * the one that's been waiting longest). 'newest'/'type' are the two other options the sort
 * control on My Tasks offers. */
function sortTasks(tasks, sort) {
  const list = [...tasks];
  if (sort === 'newest') {
    list.sort((a, b) => b.card.createdAtRaw.localeCompare(a.card.createdAtRaw));
  } else if (sort === 'type') {
    list.sort((a, b) => TYPE_ORDER.indexOf(a.card.type) - TYPE_ORDER.indexOf(b.card.type)
      || a.card.createdAtRaw.localeCompare(b.card.createdAtRaw));
  } else {
    list.sort((a, b) => (SLA_RANK[a.card.slaLevel] - SLA_RANK[b.card.slaLevel])
      || a.card.createdAtRaw.localeCompare(b.card.createdAtRaw));
  }
  return list;
}

facultyRoutes.get('/tasks', (req, res) => {
  const scope = base(req, res, 'tasks');
  const locale = res.locals.locale;
  const filter = req.query.filter;
  const type = (filter && filter !== 'ALL') ? filter : null;
  const sort = SORTS.has(req.query.sort) ? req.query.sort : 'urgency';

  const tasks = sortTasks(FacultyService.inbox(scope, type, locale), sort);
  const needsAction = tasks.filter((t) => t.actionable());
  const waitingOnOthers = tasks.filter((t) => !t.actionable());
  // Already ordered most-recently-acted-on first by FacultyService.recentlyClosed() itself
  // (it walks request_history DESC by `at`) — no need to re-sort.
  const recentlyClosed = FacultyService.recentlyClosed(scope, locale);

  res.locals.needsAction = needsAction;
  res.locals.waitingOnOthers = waitingOnOthers;
  res.locals.recentlyClosed = recentlyClosed;
  res.locals.stats = {
    awaiting: needsAction.length,
    overdue: tasks.filter((t) => t.card.slaLevel === 'overdue').length,
    resolvedWeek: recentlyClosed.length,
    totalOpen: tasks.length,
  };
  res.locals.filter = type ?? 'ALL';
  res.locals.types = Object.values(RequestType);
  res.locals.sort = sort;
  res.locals.back = '/faculty/tasks';
  res.render('faculty/tasks');
});

/**
 * The ⌘K command palette's data source. Same in-memory substring match as /faculty/students'
 * roster search, plus a pass over the inbox by student name / card title / type label. GET, so
 * no CSRF token needed. Capped small — this backs a quick-jump palette, not a full search UI.
 */
facultyRoutes.get('/search.json', (req, res) => {
  const scope = StaffScopeResolver.current(req.session.principal);
  const locale = res.locals.locale;
  const q = (req.query.q ?? '').trim().toLowerCase();
  if (!q) return res.json({ students: [], tasks: [] });

  const students = StaffScopeResolver.roster(scope)
    .filter((s) => s.name.toLowerCase().includes(q) || s.rollNo.toLowerCase().includes(q))
    .slice(0, 8)
    .map((s) => ({ id: s.id, name: s.name, rollNo: s.rollNo, href: `/faculty/students/${s.id}` }));

  const tasks = FacultyService.inbox(scope, null, locale)
    .filter((t) => t.studentName.toLowerCase().includes(q)
      || t.card.title.toLowerCase().includes(q) || t.card.typeLabel.toLowerCase().includes(q))
    .slice(0, 8)
    .map((t) => ({
      id: t.card.id, title: t.card.title, typeLabel: t.card.typeLabel, studentName: t.studentName,
      href: `/faculty/tasks#task-${t.card.id}`,
    }));

  res.json({ students, tasks });
});

/* ------------------------------ 3. STUDENTS ------------------------------ */

const STUDENTS_PAGE_SIZE = 10;
const STUDENTS_SORTS = { rollNo: (s) => s.rollNo, name: (s) => s.name };

facultyRoutes.get('/students', (req, res) => {
  const scope = base(req, res, 'students');
  let roster = StaffScopeResolver.roster(scope);
  const q = req.query.q;
  if (q && q.trim()) {
    const needle = q.trim().toLowerCase();
    roster = roster.filter((s) => s.name.toLowerCase().includes(needle) || s.rollNo.toLowerCase().includes(needle));
  }

  const sort = STUDENTS_SORTS[req.query.sort] ? req.query.sort : 'rollNo';
  const dir = req.query.dir === 'desc' ? 'desc' : 'asc';
  const key = STUDENTS_SORTS[sort];
  roster = [...roster].sort((a, b) => (key(a) < key(b) ? -1 : key(a) > key(b) ? 1 : 0));
  if (dir === 'desc') roster.reverse();

  const total = roster.length;
  const totalPages = Math.max(1, Math.ceil(total / STUDENTS_PAGE_SIZE));
  const page = Math.min(totalPages, Math.max(1, parseInt(req.query.page, 10) || 1));
  const start = (page - 1) * STUDENTS_PAGE_SIZE;

  res.locals.roster = roster.slice(start, start + STUDENTS_PAGE_SIZE);
  res.locals.q = q ?? '';
  res.locals.sort = sort;
  res.locals.dir = dir;
  res.locals.page = page;
  res.locals.totalPages = totalPages;
  res.locals.total = total;
  res.locals.rangeStart = total === 0 ? 0 : start + 1;
  res.locals.rangeEnd = Math.min(start + STUDENTS_PAGE_SIZE, total);
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
  res.locals.cards = FacultyService.requestsOf(scope, s, res.locals.locale);
  res.render('faculty/student');
});

/* --------------------------- 4/5. LEAVE, INTERNSHIP --------------------------- */

facultyRoutes.get('/leave', (req, res) => {
  const scope = base(req, res, 'leave');
  res.locals.tasks = FacultyService.inbox(scope, RequestType.LEAVE, res.locals.locale);
  res.locals.workflow = DisplayLabels.type(RequestType.LEAVE, res.locals.locale);
  res.locals.back = '/faculty/leave';
  res.render('faculty/workflow');
});

facultyRoutes.get('/internship', (req, res) => {
  const scope = base(req, res, 'internship');
  res.locals.tasks = FacultyService.inbox(scope, RequestType.INTERNSHIP, res.locals.locale);
  res.locals.workflow = DisplayLabels.type(RequestType.INTERNSHIP, res.locals.locale);
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

  // A bulk button ("Mark all present/absent") ignores whatever the individual radios say and
  // applies one status to the whole class roster in a single write — markAttendance's own
  // APPROVED_LEAVE guard still protects a leave-locked row from either path.
  const bulk = req.body.bulk === 'PRESENT' || req.body.bulk === 'ABSENT' ? req.body.bulk : null;
  let input;
  if (bulk) {
    input = {};
    for (const s of StaffScopeResolver.classRoster(scope, key)) input[s.id] = bulk;
  } else {
    input = {};
    for (const [k, v] of Object.entries(req.body)) {
      if (!k.startsWith('status_') || !v) continue;
      input[k.slice('status_'.length)] = v;
    }
  }

  const w = AcademicWriteService.markAttendance(scope, key, subject, date, input);
  let msg = bulk
    ? `${w.marked} student(s) bulk-marked ${bulk.toLowerCase()} for ${date}.`
    : `${w.marked} student(s) marked for ${date}.`;
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
  res.locals.notices = FacultyService.notifications(scope, 60, res.locals.locale);
  res.render('faculty/notifications');
});

/* ------------------------- the one action endpoint ------------------------- */

/**
 * EVERY staff decision, for every workflow, goes through here. The body carries an event and
 * an optional note — never an actor. The actor is derived from the session.
 *
 * Two response shapes, same underlying call: My Tasks' inline optimistic actions (public/js/
 * tasks.js) POST here with `Accept: application/json` — deliberately an EXACT header match, not
 * Express's fuzzy req.accepts(), because a normal browser form submission's Accept header always
 * includes text/html too and must never accidentally take the JSON branch. Everything else (the
 * plain <form> in faculty/task.ejs, with JS off or failed to load) gets the original
 * flash+redirect — that form still works exactly as it always has, unchanged.
 */
facultyRoutes.post('/requests/:id/act', (req, res) => {
  const wantsJson = req.get('Accept') === 'application/json';
  const scope = StaffScopeResolver.current(req.session.principal);
  const { event, note, back } = req.body;
  try {
    FacultyService.act(scope, req.params.id, event, note);
    if (wantsJson) return res.json({ ok: true });
    req.session.flash = 'Done — the request has moved on.';
  } catch (e) {
    // The optimistic My Tasks flow (wantsJson) can genuinely race itself: the button was valid
    // when the page rendered, but by the time the deferred commit actually fires — up to 5s
    // later, or later still via sessionStorage reconciliation on a fresh load — the request may
    // have moved on, through this staff member's own earlier action, someone else's, or a scope
    // change. That surfaces as EITHER an IllegalTransitionException (the engine's own transition
    // guard) or a StaffAccessException (StaffScopeResolver.actorFor() can no longer find a
    // matching actor for the event from the request's current state) — both mean the same thing
    // to this specific caller: "not actionable any more", not a real error. The traditional
    // <form> POST path (!wantsJson) is UNCHANGED from before this feature — only
    // IllegalTransitionException was ever caught there, and still is.
    if (wantsJson && (e instanceof IllegalTransitionException || e instanceof StaffAccessException)) {
      return res.status(409).json({ ok: false, code: 'CONFLICT', message: I18n.t(res.locals.locale, 'toast.alreadyHandled') });
    }
    if (!(e instanceof IllegalTransitionException)) {
      if (!wantsJson) throw e;
      console.error(e);
      return res.status(500).json({ ok: false, code: 'ERROR', message: I18n.t(res.locals.locale, 'toast.error') });
    }
    req.session.error = 'That move is not allowed from this stage.';
  }
  redirectAfterSave(req, res, resolveStaff(back ?? '/faculty/tasks'));
});
