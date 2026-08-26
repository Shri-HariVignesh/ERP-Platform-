import { LeavePayload } from '../payload/LeavePayload.js';
import { InternshipPayload } from '../payload/InternshipPayload.js';
import { DocumentPayload } from '../payload/DocumentPayload.js';
import { GrievancePayload } from '../payload/GrievancePayload.js';
import { LeaveType, DocType, GrievanceCategory } from '../domain/enums.js';
import { DisplayLabels } from '../view/DisplayLabels.js';

/**
 * Bound form validation, run BEFORE a typed payload DTO is ever built — `payload` is never
 * written from unvalidated input.
 *
 * SECURITY (CWE-1284/CWE-400): EVERY string field carries an explicit max length. A
 * 100,000-character field was once accepted, persisted, and re-rendered on every page load —
 * a 118KB page for one request, repeatable without limit and without a session.
 */
function sized(value, max, field) {
  if (typeof value !== 'string' || value.length > max) return `${field} exceeds ${max} characters`;
  return null;
}

function required(value, field) {
  if (value === undefined || value === null || String(value).trim() === '') return `${field} is required`;
  return null;
}

export function validateLeaveForm(body) {
  const errors = [];
  if (!Object.values(LeaveType).includes(body.leaveType)) errors.push('leaveType is invalid');
  errors.push(required(body.from, 'from') || sized(body.from, 10, 'from'));
  errors.push(required(body.to, 'to') || sized(body.to, 10, 'to'));
  errors.push(required(body.reason, 'reason') || sized(body.reason, 300, 'reason'));
  return errors.filter(Boolean);
}

export function leaveFormToPayload(body) {
  const p = new LeavePayload();
  p.leaveType = body.leaveType;
  p.from = body.from;
  p.to = body.to;
  p.reason = body.reason;
  return p;
}

export function validateInternshipForm(body) {
  const errors = [];
  errors.push(required(body.company, 'company') || sized(body.company, 120, 'company'));
  errors.push(required(body.role, 'role') || sized(body.role, 120, 'role'));
  errors.push(required(body.from, 'from') || sized(body.from, 10, 'from'));
  errors.push(required(body.to, 'to') || sized(body.to, 10, 'to'));
  errors.push(required(body.details, 'details') || sized(body.details, 500, 'details'));
  errors.push(required(body.certificateFilename, 'certificateFilename') || sized(body.certificateFilename, 160, 'certificateFilename'));
  return errors.filter(Boolean);
}

export function internshipFormToPayload(body) {
  const p = new InternshipPayload();
  p.company = body.company;
  p.role = body.role;
  p.from = body.from;
  p.to = body.to;
  p.details = body.details;
  p.certificateRef = { filename: body.certificateFilename, mime: 'application/pdf', sizeKb: 248 };
  return p;
}

export function validateDocumentForm(body) {
  const errors = [];
  if (!DocType.values().includes(body.docType)) errors.push('docType is invalid');
  errors.push(required(body.purpose, 'purpose') || sized(body.purpose, 200, 'purpose'));
  const copies = Number.parseInt(body.copies, 10);
  if (Number.isNaN(copies) || copies < 1 || copies > 3) errors.push('copies must be 1–3');
  return errors.filter(Boolean);
}

export function documentFormToPayload(body) {
  const p = new DocumentPayload();
  p.docType = body.docType;
  p.purpose = body.purpose;
  p.copies = Number.parseInt(body.copies, 10) || 1;
  return p;
}

export function validateGrievanceForm(body) {
  const errors = [];
  if (!Object.values(GrievanceCategory).includes(body.category)) errors.push('category is invalid');
  errors.push(required(body.subject, 'subject') || sized(body.subject, 120, 'subject'));
  errors.push(required(body.description, 'description') || sized(body.description, 800, 'description'));
  return errors.filter(Boolean);
}

export function grievanceFormToPayload(body) {
  const p = new GrievancePayload();
  p.category = body.category;
  p.subject = body.subject;
  p.description = body.description;
  p.anonymous = body.anonymous === 'on' || body.anonymous === true;
  p.sys.routedTo = DisplayLabels.desk(body.category);
  return p;
}
