import { Router } from 'express';
import { RequestType, DocType, LeaveType, GrievanceCategory } from '../domain/enums.js';
import { RequestService } from '../service/RequestService.js';
import { AcademicService } from '../service/AcademicService.js';
import { ComplianceService } from '../service/ComplianceService.js';
import { ScopeResolver } from '../service/ScopeResolver.js';
import { documentRepo } from '../repo/documentRepo.js';
import { IllegalTransitionException } from '../engine/IllegalTransitionException.js';
import { requireStudent } from './middleware/auth.js';
import { redirectAfterSave } from './middleware/sessionRedirect.js';
import * as Forms from './forms.js';

export const portalRoutes = Router();

/**
 * SECURITY: the only document types a student may request. DocType also contains
 * INTERNSHIP_VERIFICATION, which the SYSTEM mints when an internship is verified — a student
 * POSTing it directly would have a real verification ID and QR issued for nothing. The
 * dropdown alone is not a control; this list, checked server-side, is.
 */
const STUDENT_REQUESTABLE = [DocType.BONAFIDE, DocType.HALL_TICKET, DocType.FEE_RECEIPT,
  DocType.TRANSCRIPT, DocType.CONDUCT_CERTIFICATE];

/*
 * requireStudent is attached per-route below, not via a blanket router.use(), and flash/error
 * are read inside base() rather than a blanket router.use() too: this router is mounted at app
 * level with no path prefix (its routes span '/', '/home', '/leave', ...), and an unscoped
 * router.use(middleware) runs for EVERY request that reaches this router instance — including
 * ones ultimately destined for /verify or /faculty — before any route match is even attempted.
 * A middleware that DELETES req.session.flash unconditionally is worse than one that merely
 * redirects: it silently consumes a flash message set by a route on a DIFFERENT router, which
 * is exactly what happened here before this fix (a faculty action's flash never survived to
 * render because this router's blanket middleware read-and-deleted it first).
 */

/** Every handler gets its Scope from the AUTHENTICATED PRINCIPAL, never a request parameter. */
function base(req, res, nav) {
  const s = ScopeResolver.current(req.session.principal);
  res.locals.student = RequestService.student(s);
  res.locals.tenant = RequestService.tenant(s);
  res.locals.nav = nav;
  res.locals.attendancePct = AcademicService.attendancePct(s);
  res.locals.flash = req.session.flash ?? null;
  res.locals.error = req.session.error ?? null;
  delete req.session.flash;
  delete req.session.error;
  return s;
}

/* -------------------------------- 1. HOME -------------------------------- */

portalRoutes.get(['/', '/home'], requireStudent, (req, res) => {
  const s = base(req, res, 'home');
  const all = RequestService.all(s);
  res.locals.recent = all.slice(0, 5);
  res.locals.openCount = all.filter((c) => c.isOpen()).length;
  res.locals.banner = all.find((c) => c.studentAction !== null) ?? all.find((c) => c.isOpen()) ?? null;
  res.render('home');
});

/** UGC Anti-Ragging Regulations, 2009: a yearly acknowledgment, not a Request workflow. */
portalRoutes.post('/anti-ragging/ack', requireStudent, (req, res) => {
  const s = ScopeResolver.current(req.session.principal);
  ComplianceService.acknowledgeAntiRagging(s);
  req.session.flash = 'Thank you — your anti-ragging acknowledgment is recorded for this year.';
  redirectAfterSave(req, res, '/');
});

/* ----------------------------- 2. MY REQUESTS ----------------------------- */

portalRoutes.get('/requests', requireStudent, (req, res) => {
  const s = base(req, res, 'requests');
  let cards = RequestService.all(s);
  const filter = req.query.filter;
  if (filter && filter !== 'ALL') cards = cards.filter((c) => c.type === filter);
  res.locals.cards = cards;
  res.locals.filter = filter ?? 'ALL';
  res.locals.types = Object.values(RequestType);
  res.render('requests');
});

/* -------------------------------- 3. LEAVE -------------------------------- */

portalRoutes.get('/leave', requireStudent, (req, res) => {
  const s = base(req, res, 'leave');
  res.locals.form = res.locals.form ?? { leaveType: LeaveType.PERSONAL, from: '', to: '', reason: '' };
  res.locals.errors = res.locals.errors ?? [];
  res.locals.cards = RequestService.ofType(s, RequestType.LEAVE);
  res.locals.leaveTypes = Object.values(LeaveType);
  res.render('leave');
});

portalRoutes.post('/leave', requireStudent, (req, res) => {
  const s = ScopeResolver.current(req.session.principal);
  const errors = Forms.validateLeaveForm(req.body);
  if (errors.length === 0) {
    try {
      RequestService.create(s, RequestType.LEAVE, Forms.leaveFormToPayload(req.body));
      req.session.flash = 'Leave request submitted and auto-validated.';
      return redirectAfterSave(req, res, '/leave');
    } catch (e) {
      errors.push(e.message);
    }
  }
  base(req, res, 'leave');
  res.locals.form = req.body;
  res.locals.errors = errors;
  res.locals.cards = RequestService.ofType(s, RequestType.LEAVE);
  res.locals.leaveTypes = Object.values(LeaveType);
  res.render('leave');
});

/* ----------------------------- 4. INTERNSHIP ----------------------------- */

portalRoutes.get('/internship', requireStudent, (req, res) => {
  const s = base(req, res, 'internship');
  res.locals.form = res.locals.form ?? { company: '', role: '', from: '', to: '', details: '', certificateFilename: '' };
  res.locals.errors = res.locals.errors ?? [];
  res.locals.cards = RequestService.ofType(s, RequestType.INTERNSHIP);
  res.render('internship');
});

portalRoutes.post('/internship', requireStudent, (req, res) => {
  const s = ScopeResolver.current(req.session.principal);
  const errors = Forms.validateInternshipForm(req.body);
  if (errors.length === 0) {
    try {
      RequestService.create(s, RequestType.INTERNSHIP, Forms.internshipFormToPayload(req.body));
      req.session.flash = 'Internship submitted; certificate auto-checked.';
      return redirectAfterSave(req, res, '/internship');
    } catch (e) {
      errors.push(e.message);
    }
  }
  base(req, res, 'internship');
  res.locals.form = req.body;
  res.locals.errors = errors;
  res.locals.cards = RequestService.ofType(s, RequestType.INTERNSHIP);
  res.render('internship');
});

/* ------------------------------ 5. DOCUMENTS ------------------------------ */

portalRoutes.get('/documents', requireStudent, (req, res) => {
  const s = base(req, res, 'documents');
  res.locals.form = res.locals.form ?? { docType: DocType.BONAFIDE, purpose: '', copies: 1 };
  res.locals.errors = res.locals.errors ?? [];
  res.locals.cards = RequestService.ofType(s, RequestType.DOCUMENT);
  res.locals.issued = AcademicService.documents(s);
  res.locals.docTypes = STUDENT_REQUESTABLE;
  res.render('documents');
});

portalRoutes.post('/documents', requireStudent, (req, res) => {
  const s = ScopeResolver.current(req.session.principal);
  const errors = Forms.validateDocumentForm(req.body);
  if (errors.length === 0 && !STUDENT_REQUESTABLE.includes(req.body.docType)) {
    errors.push('That document is not one a student can request.');
  }
  if (errors.length === 0) {
    try {
      RequestService.create(s, RequestType.DOCUMENT, Forms.documentFormToPayload(req.body));
      req.session.flash = 'Document request submitted.';
      return redirectAfterSave(req, res, '/documents');
    } catch (e) {
      errors.push(e.message);
    }
  }
  base(req, res, 'documents');
  res.locals.form = req.body;
  res.locals.errors = errors;
  res.locals.cards = RequestService.ofType(s, RequestType.DOCUMENT);
  res.locals.issued = AcademicService.documents(s);
  res.locals.docTypes = STUDENT_REQUESTABLE;
  res.render('documents');
});

portalRoutes.get('/documents/:id/download', requireStudent, (req, res) => {
  const s = ScopeResolver.current(req.session.principal);
  const d = documentRepo.findByIdAndTenantIdAndStudentId(Number(req.params.id), s.tenantId, s.studentId);
  if (!d) throw new IllegalTransitionException('document not visible in scope');
  const file = `<!doctype html><meta charset="utf-8"><title>${d.title}</title>
<style>body{font:15px/1.6 system-ui;margin:40px;color:#111}
.doc{max-width:640px;border:1px solid #ccc;padding:32px}
dl{display:grid;grid-template-columns:auto 1fr;gap:4px 16px}
dl div{display:contents}dt{color:#666}footer{margin-top:24px;border-top:1px solid #eee;
padding-top:12px;display:grid;gap:4px}footer span{color:#666;margin-right:8px}
.sig{color:#666;font-size:13px}</style>${d.html}`;
  res.setHeader('Content-Disposition', `attachment; filename="${d.serialNo.replace(/\//g, '-')}.html"`);
  res.type('html').send(file);
});

/* ------------------------------- 6. ACADEMIC ------------------------------- */

portalRoutes.get('/academic', requireStudent, (req, res) => {
  const s = base(req, res, 'academic');
  res.locals.results = AcademicService.results(s);
  res.locals.cgpa = AcademicService.cgpa(s);
  res.locals.marks = AcademicService.publishedMarks(s);
  res.locals.records = AcademicService.records(s);
  res.locals.approvedLeaveDays = AcademicService.approvedLeaveDays(s);
  res.locals.term = AcademicService.currentTerm(s.tenantId);
  res.locals.hallTicket = AcademicService.latestHallTicket(s);
  res.render('academic');
});

/* ------------------------------- 7. GRIEVANCE ------------------------------- */

portalRoutes.get('/grievance', requireStudent, (req, res) => {
  const s = base(req, res, 'grievance');
  res.locals.form = res.locals.form ?? { category: GrievanceCategory.ACADEMIC, subject: '', description: '', anonymous: false };
  res.locals.errors = res.locals.errors ?? [];
  res.locals.cards = RequestService.ofType(s, RequestType.GRIEVANCE);
  res.locals.categories = Object.values(GrievanceCategory);
  res.render('grievance');
});

portalRoutes.post('/grievance', requireStudent, (req, res) => {
  const s = ScopeResolver.current(req.session.principal);
  const errors = Forms.validateGrievanceForm(req.body);
  if (errors.length === 0) {
    RequestService.create(s, RequestType.GRIEVANCE, Forms.grievanceFormToPayload(req.body));
    req.session.flash = 'Grievance submitted and auto-assigned.';
    return redirectAfterSave(req, res, '/grievance');
  }
  base(req, res, 'grievance');
  res.locals.form = req.body;
  res.locals.errors = errors;
  res.locals.cards = RequestService.ofType(s, RequestType.GRIEVANCE);
  res.locals.categories = Object.values(GrievanceCategory);
  res.render('grievance');
});
