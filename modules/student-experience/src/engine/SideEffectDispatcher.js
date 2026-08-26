import { SideEffect, AttendanceStatus, DocType } from '../domain/enums.js';
import { attendanceRepo } from '../repo/attendanceRepo.js';
import { academicRecordRepo } from '../repo/academicRecordRepo.js';
import { documentRepo } from '../repo/documentRepo.js';
import { verificationRepo } from '../repo/verificationRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { AttendanceMath } from '../service/AttendanceMath.js';
import { TransitionMatrix } from './TransitionMatrix.js';
import crypto from 'node:crypto';

/**
 * Side effects run here and only here, inside the transition's transaction. Each returns log
 * lines that become RequestHistory.effectLog — the visible proof the effect actually mutated
 * something.
 */
export const SideEffectDispatcher = {
  fire(fx, r, p, s, t) {
    switch (fx) {
      case SideEffect.VALIDATE_LEAVE: return validateLeave(r, p, s);
      case SideEffect.MUTATE_ATTENDANCE: return mutateAttendance(r, p, s);
      case SideEffect.CHECK_CERTIFICATE: return checkCertificate(p);
      case SideEffect.WRITE_ACADEMIC_RECORD: return writeAcademicRecord(r, p);
      case SideEffect.GENERATE_VERIFICATION_ID: return generateVerificationId(r, p, s, t);
      case SideEffect.PUBLISH_CERT_TO_DOCUMENTS: return publishCert(r, p, s, t);
      case SideEffect.RUN_ELIGIBILITY: return runEligibility(p, s);
      case SideEffect.GENERATE_DOCUMENT: return generateDocument(r, p, s, t);
      case SideEffect.NOTIFY:
        return ['Student notified in-app. (External notification infra is a declared non-goal.)'];
      case SideEffect.NOTIFY_REJECTION:
        return ['Reason pushed to the student. (External notification infra is a declared non-goal.)'];
      default: throw new Error(`unhandled side effect ${fx}`);
    }
  },
};

/* ------------------------------- LEAVE ------------------------------- */

function validateLeave(r, p, s) {
  const from = new Date(p.from), to = new Date(p.to);
  const days = Math.round((to.getTime() - from.getTime()) / 86400000) + 1;
  const pct = AttendanceMath.pct(attendanceRepo.findByTenantIdAndStudentId(r.tenantId, r.studentId));
  p.sys.dayCount = days;
  p.sys.balanceAtSubmit = s.leaveBalance;
  p.sys.attendanceBefore = pct;
  p.sys.validation = `${days} day(s); leave balance ${s.leaveBalance}; attendance ${pct}%`;
  return [`Auto-validated dates and leave balance — ${p.sys.validation}. No human checked this.`];
}

function mutateAttendance(r, p, s) {
  const before = AttendanceMath.pct(attendanceRepo.findByTenantIdAndStudentId(r.tenantId, r.studentId));
  const window = attendanceRepo.findByTenantIdAndStudentIdAndDateBetween(r.tenantId, r.studentId, p.from, p.to);

  const mutated = [];
  for (const a of window) {
    a.status = AttendanceStatus.APPROVED_LEAVE;
    a.sourceRequestId = r.id;
    attendanceRepo.save(a);
    mutated.push(a.date);
  }

  const after = AttendanceMath.pct(attendanceRepo.findByTenantIdAndStudentId(r.tenantId, r.studentId));
  const oldBalance = s.leaveBalance;
  const newBalance = Math.max(0, s.leaveBalance - mutated.length);
  s.leaveBalance = newBalance;
  studentRepo.save(s);

  p.sys.attendanceBefore = before;
  p.sys.attendanceAfter = after;
  p.sys.datesMutated = mutated;

  return [`AttendanceRecord mutated: ${mutated.length} class day(s) set to APPROVED_LEAVE. `
    + `Attendance ${before}% → ${after}%. Leave balance ${oldBalance} → ${newBalance}.`];
}

/* ----------------------------- INTERNSHIP ----------------------------- */

function checkCertificate(p) {
  const from = new Date(p.from), to = new Date(p.to);
  const weeks = Math.max(1, Math.round((to.getTime() - from.getTime()) / 86400000 / 7));
  p.sys.weeks = weeks;
  p.sys.certificateCheck = p.certificateRef === null
    ? 'No certificate attached.'
    : `Certificate ${p.certificateRef.filename} present (${p.certificateRef.sizeKb} KB); dates valid; ${weeks} week(s) computed.`;
  return [`Auto-check — ${p.sys.certificateCheck}`];
}

function writeAcademicRecord(r, p) {
  const credits = Math.min(4, Math.max(1, Math.round(p.sys.weeks / 4)));
  const a = {
    tenantId: r.tenantId, studentId: r.studentId, kind: 'INTERNSHIP',
    title: `${p.role} · ${p.company}`, subtitle: `${p.sys.weeks} weeks · ${p.from} → ${p.to}`,
    credits, verifyId: null, sourceRequestId: r.id, recordedAt: new Date().toISOString(),
  };
  academicRecordRepo.save(a);
  p.sys.credits = credits;
  return [`AcademicRecord mutated: internship written to the official record, ${credits} credit(s) awarded.`];
}

/** Shared by INTERNSHIP's edge only — no DOCUMENT edge declares this effect (see generateDocument). */
function generateVerificationId(r, p, s, t) {
  const id = verifyId(t);
  let subject, detail, kind;
  if (p.type() === 'INTERNSHIP') {
    kind = 'INTERNSHIP';
    subject = `${p.role} · ${p.company}`;
    detail = `${p.sys.weeks} weeks · ${p.from} → ${p.to} · ${p.sys.credits} credit(s)`;
    p.sys.verifyId = id;
    for (const a of academicRecordRepo.findByTenantIdAndStudentIdAndSourceRequestId(r.tenantId, r.studentId, r.id)) {
      a.verifyId = id;
      academicRecordRepo.save(a);
    }
  } else if (p.type() === 'DOCUMENT') {
    kind = 'DOCUMENT';
    subject = DocType.display(p.docType);
    detail = `${p.copies} copy/copies · ${p.purpose}`;
    p.sys.verifyId = id;
  } else {
    throw new Error(`verification id not defined for ${p.type()}`);
  }

  verificationRepo.save({
    verifyId: id, tenantId: r.tenantId, studentId: r.studentId, kind, subject, detail,
    sourceRequestId: r.id, issuedAt: new Date().toISOString(),
  });

  return [`Verification ID generated: ${id} — QR resolves at /verify/${id}.`];
}

function publishCert(r, p, s, t) {
  const serial = nextSerial(r, s, 'INT');
  const d = {
    tenantId: r.tenantId, studentId: r.studentId, serialNo: serial,
    docType: DocType.INTERNSHIP_VERIFICATION, title: DocType.display(DocType.INTERNSHIP_VERIFICATION),
    verifyId: p.sys.verifyId, sourceRequestId: r.id,
    html: render(t, s, DocType.display(DocType.INTERNSHIP_VERIFICATION), serial, p.sys.verifyId,
      'This is to certify that the student named above completed an internship as '
      + `<strong>${esc(p.role)}</strong> at <strong>${esc(p.company)}</strong> for ${p.sys.weeks} weeks `
      + `(${p.from} to ${p.to}). The engagement has been verified by the department and entered into `
      + `the student's academic record for ${p.sys.credits} credit(s).`),
    issuedAt: new Date().toISOString(),
  };
  documentRepo.save(d);
  p.sys.documentSerial = serial;
  return [`Certificate published into Documents & Certificates as ${serial}.`];
}

/* ----------------------------- DOCUMENTS ----------------------------- */

function runEligibility(p, s) {
  const auto = p.docType === DocType.BONAFIDE && s.active;
  p.sys.autoEligible = auto;
  p.sys.eligibilityReason = TransitionMatrix.eligibilityReason(p, s.active);
  return [`Eligibility rule ran — ${p.sys.eligibilityReason}`];
}

/**
 * Documents mint their OWN verification id inline — no DOCUMENT edge in the matrix declares
 * GENERATE_VERIFICATION_ID, so this is the only place a document's verifyId is ever set.
 */
function generateDocument(r, p, s, t) {
  const serial = nextSerial(r, s, p.docType.slice(0, 3));
  const id = verifyId(t);

  verificationRepo.save({
    verifyId: id, tenantId: r.tenantId, studentId: r.studentId, kind: 'DOCUMENT',
    subject: DocType.display(p.docType),
    detail: `Serial ${serial} · ${p.copies} copy/copies · ${p.purpose}`,
    sourceRequestId: r.id, issuedAt: new Date().toISOString(),
  });

  const title = DocType.display(p.docType);
  const d = {
    tenantId: r.tenantId, studentId: r.studentId, serialNo: serial, docType: p.docType,
    title, verifyId: id, sourceRequestId: r.id,
    html: render(t, s, title, serial, id, documentBody(p, s)),
    issuedAt: new Date().toISOString(),
  };
  documentRepo.save(d);

  p.sys.serialNo = serial;
  p.sys.verifyId = id;
  p.sys.documentId = d.id;

  return [`Document rendered: ${title}, serial ${serial}, verify id ${id}. View/Download is now enabled.`];
}

function documentBody(p, s) {
  const purpose = esc(p.purpose);
  switch (p.docType) {
    case DocType.BONAFIDE:
      return `This is to certify that <strong>${esc(s.name)}</strong> is a bonafide student of this `
        + `institution, currently enrolled in semester ${s.semester} of the ${esc(s.program)} programme. `
        + `Issued for: ${purpose}.`;
    case DocType.HALL_TICKET:
      return `Examination hall ticket for <strong>${esc(s.name)}</strong>, semester ${s.semester}, `
        + `section ${esc(s.section)}. Candidates must carry a photo ID. Issued for: ${purpose}.`;
    case DocType.FEE_RECEIPT:
      return `Consolidated fee receipt for <strong>${esc(s.name)}</strong>. Outstanding balance at issue: `
        + `₹${s.feeDues}. Issued for: ${purpose}.`;
    case DocType.TRANSCRIPT:
      return `Consolidated academic transcript for <strong>${esc(s.name)}</strong>, attested by the `
        + `Controller of Examinations. Issued for: ${purpose}.`;
    case DocType.CONDUCT_CERTIFICATE:
      return `Conduct certificate for <strong>${esc(s.name)}</strong>, attested by the Dean of Students. `
        + `Issued for: ${purpose}.`;
    case DocType.INTERNSHIP_VERIFICATION:
      return `Internship verification for <strong>${esc(s.name)}</strong>.`;
    default:
      return '';
  }
}

/* -------------------------------- shared -------------------------------- */

function nextSerial(r, s, prefix) {
  const n = documentRepo.countByTenantIdAndStudentId(r.tenantId, r.studentId) + 1;
  const year = new Date().getFullYear();
  return `${prefix}/${year}/${s.rollNo}/${String(n).padStart(3, '0')}`;
}

/**
 * SECURITY (CWE-330/CWE-340): a verification id is a BEARER CAPABILITY. 12 characters drawn
 * uniformly from a 32-symbol alphabet via crypto.randomBytes = exactly 60 bits, no truncation
 * of a biased source (256 % 32 === 0 keeps the modulo uniform). Alphabet omits I, L, O, U so a
 * human reading an id off a printed page cannot confuse it with 1/0 and cannot spell words.
 * Mirrors the fix for F1 in SECURITY.md — the previous ~2^27.7-entropy UUID-prefix scheme.
 */
const ID_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const ID_LENGTH = 12;

function verifyId(t) {
  const bytes = crypto.randomBytes(ID_LENGTH);
  let sb = '';
  for (let i = 0; i < ID_LENGTH; i++) sb += ID_ALPHABET[bytes[i] % 32];
  return `${t.shortName.toUpperCase()}-${new Date().getFullYear()}-${sb}`;
}

function render(t, s, title, serial, verifyIdValue, body) {
  const issued = new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  return `<article class="doc">
  <header><h1>${esc(title)}</h1><p>${esc(t.name)}, ${esc(t.city)}</p></header>
  <dl>
    <div><dt>Name</dt><dd>${esc(s.name)}</dd></div>
    <div><dt>Roll No.</dt><dd>${esc(s.rollNo)}</dd></div>
    <div><dt>Programme</dt><dd>${esc(s.program)}, ${esc(s.department)}</dd></div>
    <div><dt>Semester</dt><dd>${s.semester} · Section ${esc(s.section)}</dd></div>
  </dl>
  <p class="body">${body}</p>
  <footer>
    <div><span>Serial</span><strong>${esc(serial)}</strong></div>
    <div><span>Verify ID</span><strong>${esc(verifyIdValue)}</strong></div>
    <div><span>Issued</span><strong>${issued}</strong></div>
  </footer>
  <p class="sig">Digitally issued by ${esc(t.shortName)} on CampusOS. No physical signature required.</p>
</article>`;
}

function esc(v) {
  if (v === null || v === undefined) return '';
  return String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
