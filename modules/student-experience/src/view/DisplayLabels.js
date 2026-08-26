import { RequestState, SideEffect, RequestType, LeaveType, GrievanceCategory, Actor } from '../domain/enums.js';

/**
 * THE one place enum constants become English. Display only — nothing here is read by the
 * engine, the matrix or the guard, and no enum value changes. Every card goes through this,
 * which is how the templates stay free of both raw enums and per-type branching.
 */

const STATES = {
  [RequestState.SUBMITTED]: 'Submitted',
  [RequestState.FACULTY_PENDING]: 'With faculty',
  [RequestState.HOD_PENDING]: 'With HOD',
  [RequestState.ATTENDANCE_MUTATED]: 'Attendance updated',
  [RequestState.NOTIFIED]: 'Approved',
  [RequestState.REJECTED]: 'Rejected',
  [RequestState.FACULTY_VERIFICATION]: 'With faculty for verification',
  [RequestState.INSTITUTION_APPROVAL]: 'With the institution',
  [RequestState.ACADEMIC_RECORD_MUTATED]: 'Added to academic record',
  [RequestState.VERIFICATION_ID_GENERATED]: 'Verified',
  [RequestState.RETURNED]: 'Returned for correction',
  [RequestState.APPROVAL]: 'With the office',
  [RequestState.DOCUMENT_GENERATED]: 'Document ready',
  [RequestState.ASSIGNED]: 'Assigned to a desk',
  [RequestState.UNDER_REVIEW]: 'Under review',
  [RequestState.RESOLVED]: 'Resolved',
  [RequestState.OMBUDSMAN_REVIEW]: 'With the Ombudsperson',
  [RequestState.OMBUDSMAN_DECIDED]: 'Ombudsperson decided',
};

const EFFECTS = {
  [SideEffect.VALIDATE_LEAVE]: 'Validated leave',
  [SideEffect.MUTATE_ATTENDANCE]: 'Updated attendance',
  [SideEffect.CHECK_CERTIFICATE]: 'Checked certificate',
  [SideEffect.WRITE_ACADEMIC_RECORD]: 'Wrote academic record',
  [SideEffect.GENERATE_VERIFICATION_ID]: 'Issued verification ID',
  [SideEffect.PUBLISH_CERT_TO_DOCUMENTS]: 'Published certificate',
  [SideEffect.RUN_ELIGIBILITY]: 'Checked eligibility',
  [SideEffect.GENERATE_DOCUMENT]: 'Generated document',
  [SideEffect.NOTIFY]: 'Notified you',
  [SideEffect.NOTIFY_REJECTION]: 'Sent you the reason',
};

const LEAVE_TYPES = {
  [LeaveType.MEDICAL]: 'Medical',
  [LeaveType.PERSONAL]: 'Personal',
  [LeaveType.EVENT]: 'Event / On-duty',
};

const CATEGORIES = {
  [GrievanceCategory.ACADEMIC]: 'Academic',
  [GrievanceCategory.EXAM]: 'Examination',
  [GrievanceCategory.FEES]: 'Fees',
  [GrievanceCategory.HOSTEL]: 'Hostel',
  [GrievanceCategory.OTHER]: 'Other',
  [GrievanceCategory.RAGGING]: 'Ragging',
  [GrievanceCategory.SEXUAL_HARASSMENT]: 'Sexual harassment (confidential)',
  [GrievanceCategory.SC_ST_DISCRIMINATION]: 'SC/ST discrimination',
  [GrievanceCategory.EQUAL_OPPORTUNITY]: 'Equal opportunity',
  [GrievanceCategory.RTI]: 'Right to Information (RTI)',
};

/**
 * Which desk a grievance category belongs to. For the ordinary categories this is a DISPLAY
 * MAPPING only, same as ever: AUTO_ASSIGN carries no side effects, nothing in the engine
 * dispatches to a desk. For the five UGC-mandated categories below the desk name is backed by
 * a REAL restriction — see GrievanceVisibility.js — so the label and the enforcement agree.
 */
const DESKS = {
  [GrievanceCategory.ACADEMIC]: 'Academic Office',
  [GrievanceCategory.EXAM]: 'Examination Office',
  [GrievanceCategory.FEES]: 'Accounts Office',
  [GrievanceCategory.HOSTEL]: 'Hostel Warden Office',
  [GrievanceCategory.OTHER]: 'Student Services',
  [GrievanceCategory.RAGGING]: 'Anti-Ragging Committee',
  [GrievanceCategory.SEXUAL_HARASSMENT]: 'Internal Complaints Committee (ICC)',
  [GrievanceCategory.SC_ST_DISCRIMINATION]: 'SC/ST Cell',
  [GrievanceCategory.EQUAL_OPPORTUNITY]: 'Equal Opportunity Cell',
  [GrievanceCategory.RTI]: 'RTI Cell',
};
const DEFAULT_DESK = 'Student Services';

const TYPES = {
  [RequestType.LEAVE]: 'Leave',
  [RequestType.INTERNSHIP]: 'Internship',
  [RequestType.DOCUMENT]: 'Document',
  [RequestType.GRIEVANCE]: 'Grievance',
};

function sentenceCase(enumName) {
  const words = enumName.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export const DisplayLabels = {
  state(s) { return s ? (STATES[s] ?? sentenceCase(s)) : ''; },

  /** "Submitted → With faculty", or just the destination for the first entry. */
  transition(from, to) { return from ? `${this.state(from)} → ${this.state(to)}` : this.state(to); },

  /** SYSTEM reads as automation; the student reads as themselves; staff read as their role. */
  actor(a, studentName) {
    if (!a) return '';
    if (a === 'SYSTEM') return 'Automated';
    if (a === 'STUDENT') return studentName && studentName.trim() ? studentName : 'You';
    return Actor.display(a);
  },

  /** RequestHistory stores effects comma-joined; turn that back into readable phrases. */
  effects(commaJoined) {
    if (!commaJoined || !commaJoined.trim()) return '';
    return commaJoined.split(',').map((s) => s.trim()).filter(Boolean)
      .map((s) => this.effect(s)).join(' · ');
  },

  effect(name) { return EFFECTS[name] ?? sentenceCase(name); },

  status(s) { return s ? STATUS[s] ?? null : null; },

  /** Reviewer-facing asides the engine writes into its effect log; hidden from students. */
  proof(text) {
    if (!text || !text.trim()) return text;
    let out = text.replace(/\s*\([^)]*declared non-goal[^)]*\)/g, '')
      .replace(/AttendanceRecord/g, 'Attendance record')
      .replace(/AcademicRecord/g, 'Academic record');
    for (const p of STRIP) out = out.replace(p, '');
    for (const [k, v] of Object.entries(REPHRASE)) {
      out = out.replace(new RegExp(escapeRegExp(k), 'gi'), v);
    }
    out = out.replace(/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/g, (m) => m.replace(/_/g, ' ').toLowerCase());
    const cleaned = out.replace(/\s{2,}/g, ' ').trim();
    return cleaned === '' ? cleaned : cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
  },

  /**
   * SECURITY: /verify is unauthenticated by design — the unguessable id is the capability.
   * A verifier needs to know WHAT was attested, not the student's roll number or serial.
   */
  publicDetail(detail) {
    if (!detail || !detail.trim()) return detail;
    let out = detail.replace(/\s*Serial\s+\S+\s*·?/i, '');
    out = out.replace(/\s*·?\s*\d+\s+cop(?:y|ies)(?:\/copies)?\s*·.*$/i, '');
    return out.replace(/^\s*·\s*/, '').replace(/\s{2,}/g, ' ').trim();
  },

  event(e) { return e ? sentenceCase(e) : ''; },

  credentialKind(kind) {
    if (!kind) return 'Credential';
    if (kind === 'INTERNSHIP') return 'Internship record';
    if (kind === 'DOCUMENT') return 'Institutional document';
    return sentenceCase(kind);
  },

  type(t) { return t ? (TYPES[t] ?? sentenceCase(t)) : ''; },

  leaveType(t) { return t ? (LEAVE_TYPES[t] ?? sentenceCase(t)) : ''; },

  desk(c) { return c ? (DESKS[c] ?? DEFAULT_DESK) : DEFAULT_DESK; },

  /**
   * The matrix labels states generically ("Assigned to desk"); when the payload names a
   * handler, say it, so the badge and the headline can never disagree.
   */
  stateLabel(matrixLabel, handledBy) {
    if (!matrixLabel || !handledBy || !handledBy.trim()) return matrixLabel;
    return matrixLabel === 'Assigned to desk' ? `Assigned to ${handledBy}` : matrixLabel;
  },

  category(c) { return c ? (CATEGORIES[c] ?? sentenceCase(c)) : ''; },
};

/** Short human status for a state, or null when the card should say who has it. */
const STATUS = {
  [RequestState.NOTIFIED]: 'Approved — attendance updated.',
  [RequestState.DOCUMENT_GENERATED]: 'Ready to download.',
  [RequestState.VERIFICATION_ID_GENERATED]: 'Verified and added to your record.',
  [RequestState.ACADEMIC_RECORD_MUTATED]: 'Adding to your academic record.',
  [RequestState.ATTENDANCE_MUTATED]: 'Updating your attendance.',
  [RequestState.RETURNED]: 'Returned — see the reason below.',
  [RequestState.REJECTED]: 'Rejected — see the reason below.',
  [RequestState.RESOLVED]: 'Resolved.',
  [RequestState.OMBUDSMAN_REVIEW]: 'Escalated — with the Ombudsperson.',
  [RequestState.OMBUDSMAN_DECIDED]: 'Ombudsperson decision issued — see the note below.',
};

const STRIP = [
  /\s*Document rendered:[^.]*\./gi,
  /\s*View\/Download is now enabled\.?/gi,
  /\s*[-—]?\s*QR resolves at\s*\S*/gi,
  /\s*\S*\/verify\/\S*/g,
  /\s*\b\w+\s+notified\s+in-app\.?/gi,
  /^\s*Eligibility rule ran\s*[-—]\s*/gi,
];

const REPHRASE = {
  'attendance record mutated': 'attendance updated',
  'academic record mutated': 'added to your academic record',
  'verify id': 'Verification ID',
};

function escapeRegExp(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
