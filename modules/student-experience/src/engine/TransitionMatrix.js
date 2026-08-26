import { Actor, Event, RequestState, RequestType, SideEffect, DocType } from '../domain/enums.js';
import { Transition } from './Transition.js';
import { WorkflowSpec } from './WorkflowSpec.js';

const { SYSTEM, FACULTY, HOD, STUDENT, INSTITUTION, OFFICE } = Actor;
const { AUTO_VALIDATE, APPROVE, REJECT, APPLY, AUTO_CHECK, VERIFY, RETURN, RESUBMIT, WRITE_RECORD,
  AUTO_ELIGIBILITY, AUTO_ASSIGN, START_REVIEW, RESOLVE } = Event;
const S = RequestState;
const FX = SideEffect;

/* ------------------------------ LEAVE ------------------------------ */

const LEAVE = new WorkflowSpec(
  'Leave',
  S.SUBMITTED,
  [
    { key: S.SUBMITTED, label: 'Submitted' },
    { key: S.FACULTY_PENDING, label: 'Faculty review' },
    { key: S.HOD_PENDING, label: 'HOD approval' },
    { key: S.ATTENDANCE_MUTATED, label: 'Attendance updated' },
    { key: S.NOTIFIED, label: 'Approved' },
  ],
  { [S.REJECTED]: { label: 'Rejected', tone: 'danger' } },
  { [S.NOTIFIED]: 'success', [S.REJECTED]: 'danger' },
  {
    [S.SUBMITTED]: 'Validating', [S.FACULTY_PENDING]: 'With faculty', [S.HOD_PENDING]: 'With HOD',
    [S.ATTENDANCE_MUTATED]: 'Updating attendance', [S.NOTIFIED]: 'Approved', [S.REJECTED]: 'Rejected',
  },
  {
    [S.SUBMITTED]: [
      Transition.of(AUTO_VALIDATE, SYSTEM, S.FACULTY_PENDING, [FX.VALIDATE_LEAVE]),
    ],
    [S.FACULTY_PENDING]: [
      Transition.human(APPROVE, FACULTY, S.HOD_PENDING, [FX.NOTIFY], false, 'Faculty approves', 'success'),
      Transition.human(REJECT, FACULTY, S.REJECTED, [FX.NOTIFY_REJECTION], true, 'Faculty rejects', 'danger'),
    ],
    [S.HOD_PENDING]: [
      Transition.human(APPROVE, HOD, S.ATTENDANCE_MUTATED, [], false, 'HOD approves', 'success'),
      Transition.human(REJECT, HOD, S.REJECTED, [FX.NOTIFY_REJECTION], true, 'HOD rejects', 'danger'),
    ],
    [S.ATTENDANCE_MUTATED]: [
      Transition.of(APPLY, SYSTEM, S.NOTIFIED, [FX.MUTATE_ATTENDANCE, FX.NOTIFY]),
    ],
    [S.NOTIFIED]: [],
    [S.REJECTED]: [],
  },
);

/* ---------------------------- INTERNSHIP ---------------------------- */

const INTERNSHIP = new WorkflowSpec(
  'Internship',
  S.SUBMITTED,
  [
    { key: S.SUBMITTED, label: 'Submitted' },
    { key: S.FACULTY_VERIFICATION, label: 'Faculty verification' },
    { key: S.INSTITUTION_APPROVAL, label: 'Institution approval' },
    { key: S.ACADEMIC_RECORD_MUTATED, label: 'Added to record' },
    { key: S.VERIFICATION_ID_GENERATED, label: 'Verified' },
  ],
  {
    [S.RETURNED]: { label: 'Returned for correction', tone: 'action' },
    [S.REJECTED]: { label: 'Rejected', tone: 'danger' },
  },
  { [S.VERIFICATION_ID_GENERATED]: 'success', [S.REJECTED]: 'danger' },
  {
    [S.SUBMITTED]: 'Checking certificate', [S.FACULTY_VERIFICATION]: 'With faculty',
    [S.INSTITUTION_APPROVAL]: 'With institution', [S.ACADEMIC_RECORD_MUTATED]: 'Writing to record',
    [S.VERIFICATION_ID_GENERATED]: 'Verified', [S.RETURNED]: 'Returned to you', [S.REJECTED]: 'Rejected',
  },
  {
    [S.SUBMITTED]: [
      Transition.of(AUTO_CHECK, SYSTEM, S.FACULTY_VERIFICATION, [FX.CHECK_CERTIFICATE]),
    ],
    [S.FACULTY_VERIFICATION]: [
      Transition.human(VERIFY, FACULTY, S.INSTITUTION_APPROVAL, [], false, 'Faculty verifies certificate', 'success'),
      Transition.human(RETURN, FACULTY, S.RETURNED, [FX.NOTIFY_REJECTION], true, 'Faculty returns for correction', 'action'),
    ],
    [S.INSTITUTION_APPROVAL]: [
      Transition.human(APPROVE, INSTITUTION, S.ACADEMIC_RECORD_MUTATED, [], false, 'Institution approves', 'success'),
      Transition.human(REJECT, INSTITUTION, S.REJECTED, [FX.NOTIFY_REJECTION], true, 'Institution rejects', 'danger'),
    ],
    [S.ACADEMIC_RECORD_MUTATED]: [
      Transition.of(WRITE_RECORD, SYSTEM, S.VERIFICATION_ID_GENERATED,
        [FX.WRITE_ACADEMIC_RECORD, FX.GENERATE_VERIFICATION_ID, FX.PUBLISH_CERT_TO_DOCUMENTS, FX.NOTIFY]),
    ],
    [S.RETURNED]: [
      Transition.human(RESUBMIT, STUDENT, S.SUBMITTED, [], false, 'Fix & resubmit', 'action')
        .withInput('Corrected certificate filename'),
    ],
    [S.VERIFICATION_ID_GENERATED]: [],
    [S.REJECTED]: [],
  },
);

/* ----------------------------- DOCUMENTS ----------------------------- */

/** The auto-eligibility razor: BONAFIDE self-approves for an active student. */
function autoEligible(ctx) {
  const d = ctx.payload;
  if (d.type() !== RequestType.DOCUMENT) return false;
  return d.docType === DocType.BONAFIDE && ctx.student.active;
}

function eligibilityReason(d, active) {
  if (d.docType !== DocType.BONAFIDE) {
    return `${DocType.display(d.docType)} carries an institutional attestation — routed to the office.`;
  }
  if (!active) return 'Enrolment is not active — routed to the office.';
  return "Bonafide only asks 'is this an active student?'. The system already knows. "
    + 'Auto-generated with zero human touches.';
}

const DOCUMENT = new WorkflowSpec(
  'Document',
  S.SUBMITTED,
  [
    { key: S.SUBMITTED, label: 'Submitted' },
    { key: S.APPROVAL, label: 'Office approval' },
    { key: S.DOCUMENT_GENERATED, label: 'Ready' },
  ],
  { [S.REJECTED]: { label: 'Rejected', tone: 'danger' } },
  { [S.DOCUMENT_GENERATED]: 'success', [S.REJECTED]: 'danger' },
  {
    [S.SUBMITTED]: 'Running eligibility', [S.APPROVAL]: 'With office',
    [S.DOCUMENT_GENERATED]: 'Ready', [S.REJECTED]: 'Rejected',
  },
  {
    [S.SUBMITTED]: [
      // Digital Razor: the system can answer "is this an active student?" itself.
      Transition.of(AUTO_ELIGIBILITY, SYSTEM, S.DOCUMENT_GENERATED, [FX.RUN_ELIGIBILITY, FX.GENERATE_DOCUMENT])
        .guardedBy(autoEligible),
      Transition.of(AUTO_ELIGIBILITY, SYSTEM, S.APPROVAL, [FX.RUN_ELIGIBILITY])
        .guardedBy((c) => !autoEligible(c)),
    ],
    [S.APPROVAL]: [
      Transition.human(APPROVE, OFFICE, S.DOCUMENT_GENERATED, [FX.GENERATE_DOCUMENT], false, 'Office approves', 'success'),
      Transition.human(REJECT, OFFICE, S.REJECTED, [FX.NOTIFY_REJECTION], true, 'Office rejects', 'danger'),
    ],
    [S.DOCUMENT_GENERATED]: [],
    [S.REJECTED]: [],
  },
);

/* ----------------------------- GRIEVANCE ----------------------------- */

const GRIEVANCE = new WorkflowSpec(
  'Grievance',
  S.SUBMITTED,
  [
    { key: S.SUBMITTED, label: 'Submitted' },
    { key: S.ASSIGNED, label: 'Assigned' },
    { key: S.UNDER_REVIEW, label: 'Under review' },
    { key: S.RESOLVED, label: 'Resolved' },
  ],
  {},
  { [S.RESOLVED]: 'success' },
  {
    [S.SUBMITTED]: 'Routing', [S.ASSIGNED]: 'Assigned to desk',
    [S.UNDER_REVIEW]: 'Under review', [S.RESOLVED]: 'Resolved',
  },
  {
    [S.SUBMITTED]: [Transition.of(AUTO_ASSIGN, SYSTEM, S.ASSIGNED, [])],
    [S.ASSIGNED]: [Transition.human(START_REVIEW, FACULTY, S.UNDER_REVIEW, [], false, 'Desk starts review', 'pending')],
    [S.UNDER_REVIEW]: [Transition.human(RESOLVE, FACULTY, S.RESOLVED, [FX.NOTIFY], true, 'Desk resolves', 'success')],
    [S.RESOLVED]: [],
  },
);

const SPECS = {
  [RequestType.LEAVE]: LEAVE,
  [RequestType.INTERNSHIP]: INTERNSHIP,
  [RequestType.DOCUMENT]: DOCUMENT,
  [RequestType.GRIEVANCE]: GRIEVANCE,
};

export const TransitionMatrix = {
  spec(type) { return SPECS[type]; },
  initial(type) { return SPECS[type].initial; },
  autoEligible,
  eligibilityReason,
};
