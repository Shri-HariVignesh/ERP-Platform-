import { RequestState, SideEffect, RequestType, LeaveType, GrievanceCategory, Actor } from '../domain/enums.js';

/**
 * THE one place enum constants become English (or Hindi). Display only — nothing here is read
 * by the engine, the matrix or the guard, and no enum value changes. Every card goes through
 * this, which is how the templates stay free of both raw enums and per-type branching.
 *
 * `locale` is a trailing, optional argument (default 'en') on every lookup below, so every
 * existing call site — including ones that persist a label into stored data (seed.js, forms.js)
 * and must therefore stay in English forever — keeps working unchanged. Only render-path callers
 * that were updated to pass `locale` explicitly pick up Hindi.
 */

const STATES = {
  en: {
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
  },
  hi: {
    [RequestState.SUBMITTED]: 'सबमिट किया गया',
    [RequestState.FACULTY_PENDING]: 'फैकल्टी के पास',
    [RequestState.HOD_PENDING]: 'HOD के पास',
    [RequestState.ATTENDANCE_MUTATED]: 'उपस्थिति अपडेट की गई',
    [RequestState.NOTIFIED]: 'स्वीकृत',
    [RequestState.REJECTED]: 'अस्वीकृत',
    [RequestState.FACULTY_VERIFICATION]: 'सत्यापन हेतु फैकल्टी के पास',
    [RequestState.INSTITUTION_APPROVAL]: 'संस्थान के पास',
    [RequestState.ACADEMIC_RECORD_MUTATED]: 'शैक्षणिक रिकॉर्ड में जोड़ा गया',
    [RequestState.VERIFICATION_ID_GENERATED]: 'सत्यापित',
    [RequestState.RETURNED]: 'सुधार हेतु वापस भेजा गया',
    [RequestState.APPROVAL]: 'कार्यालय के पास',
    [RequestState.DOCUMENT_GENERATED]: 'दस्तावेज़ तैयार',
    [RequestState.ASSIGNED]: 'एक डेस्क को सौंपा गया',
    [RequestState.UNDER_REVIEW]: 'समीक्षाधीन',
    [RequestState.RESOLVED]: 'हल किया गया',
    [RequestState.OMBUDSMAN_REVIEW]: 'लोकपाल के पास',
    [RequestState.OMBUDSMAN_DECIDED]: 'लोकपाल ने निर्णय दिया',
  },
};

const EFFECTS = {
  en: {
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
  },
  hi: {
    [SideEffect.VALIDATE_LEAVE]: 'छुट्टी सत्यापित की गई',
    [SideEffect.MUTATE_ATTENDANCE]: 'उपस्थिति अपडेट की गई',
    [SideEffect.CHECK_CERTIFICATE]: 'प्रमाणपत्र जांचा गया',
    [SideEffect.WRITE_ACADEMIC_RECORD]: 'शैक्षणिक रिकॉर्ड में दर्ज किया गया',
    [SideEffect.GENERATE_VERIFICATION_ID]: 'सत्यापन आईडी जारी की गई',
    [SideEffect.PUBLISH_CERT_TO_DOCUMENTS]: 'प्रमाणपत्र प्रकाशित किया गया',
    [SideEffect.RUN_ELIGIBILITY]: 'पात्रता जांची गई',
    [SideEffect.GENERATE_DOCUMENT]: 'दस्तावेज़ तैयार किया गया',
    [SideEffect.NOTIFY]: 'आपको सूचित किया गया',
    [SideEffect.NOTIFY_REJECTION]: 'आपको कारण भेजा गया',
  },
};

const LEAVE_TYPES = {
  en: {
    [LeaveType.MEDICAL]: 'Medical',
    [LeaveType.PERSONAL]: 'Personal',
    [LeaveType.EVENT]: 'Event / On-duty',
  },
  hi: {
    [LeaveType.MEDICAL]: 'मेडिकल',
    [LeaveType.PERSONAL]: 'व्यक्तिगत',
    [LeaveType.EVENT]: 'इवेंट / ऑन-ड्यूटी',
  },
};

const CATEGORIES = {
  en: {
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
  },
  hi: {
    [GrievanceCategory.ACADEMIC]: 'शैक्षणिक',
    [GrievanceCategory.EXAM]: 'परीक्षा',
    [GrievanceCategory.FEES]: 'शुल्क',
    [GrievanceCategory.HOSTEL]: 'हॉस्टल',
    [GrievanceCategory.OTHER]: 'अन्य',
    [GrievanceCategory.RAGGING]: 'रैगिंग',
    [GrievanceCategory.SEXUAL_HARASSMENT]: 'यौन उत्पीड़न (गोपनीय)',
    [GrievanceCategory.SC_ST_DISCRIMINATION]: 'SC/ST भेदभाव',
    [GrievanceCategory.EQUAL_OPPORTUNITY]: 'समान अवसर',
    [GrievanceCategory.RTI]: 'सूचना का अधिकार (RTI)',
  },
};

/**
 * Which desk a grievance category belongs to. For the ordinary categories this is a DISPLAY
 * MAPPING only, same as ever: AUTO_ASSIGN carries no side effects, nothing in the engine
 * dispatches to a desk. For the five UGC-mandated categories below the desk name is backed by
 * a REAL restriction — see GrievanceVisibility.js — so the label and the enforcement agree.
 */
const DESKS = {
  en: {
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
  },
  hi: {
    [GrievanceCategory.ACADEMIC]: 'शैक्षणिक कार्यालय',
    [GrievanceCategory.EXAM]: 'परीक्षा कार्यालय',
    [GrievanceCategory.FEES]: 'लेखा कार्यालय',
    [GrievanceCategory.HOSTEL]: 'हॉस्टल वार्डन कार्यालय',
    [GrievanceCategory.OTHER]: 'छात्र सेवाएं',
    [GrievanceCategory.RAGGING]: 'रैगिंग-रोधी समिति',
    [GrievanceCategory.SEXUAL_HARASSMENT]: 'आंतरिक शिकायत समिति (ICC)',
    [GrievanceCategory.SC_ST_DISCRIMINATION]: 'SC/ST सेल',
    [GrievanceCategory.EQUAL_OPPORTUNITY]: 'समान अवसर सेल',
    [GrievanceCategory.RTI]: 'RTI सेल',
  },
};
const DEFAULT_DESK = { en: 'Student Services', hi: 'छात्र सेवाएं' };

const TYPES = {
  en: {
    [RequestType.LEAVE]: 'Leave',
    [RequestType.INTERNSHIP]: 'Internship',
    [RequestType.DOCUMENT]: 'Document',
    [RequestType.GRIEVANCE]: 'Grievance',
  },
  hi: {
    [RequestType.LEAVE]: 'छुट्टी',
    [RequestType.INTERNSHIP]: 'इंटर्नशिप',
    [RequestType.DOCUMENT]: 'दस्तावेज़',
    [RequestType.GRIEVANCE]: 'शिकायत',
  },
};

/**
 * TransitionMatrix.js's per-transition human() button labels are literal English strings —
 * stable identities the engine never reads, purely display. Keyed by the exact English text so
 * the matrix itself never has to know about locale.
 */
const ACTIONS_HI = {
  'Faculty approves': 'फैकल्टी स्वीकृत करती है',
  'Faculty rejects': 'फैकल्टी अस्वीकार करती है',
  'HOD approves': 'HOD स्वीकृत करते हैं',
  'HOD rejects': 'HOD अस्वीकार करते हैं',
  'Faculty verifies certificate': 'फैकल्टी प्रमाणपत्र सत्यापित करती है',
  'Faculty returns for correction': 'फैकल्टी सुधार हेतु वापस भेजती है',
  'Institution approves': 'संस्थान स्वीकृत करता है',
  'Institution rejects': 'संस्थान अस्वीकार करता है',
  'Fix & resubmit': 'ठीक करें और पुनः सबमिट करें',
  'Office approves': 'कार्यालय स्वीकृत करता है',
  'Office rejects': 'कार्यालय अस्वीकार करता है',
  'Desk starts review': 'डेस्क समीक्षा शुरू करता है',
  'Desk resolves': 'डेस्क समाधान करता है',
  'Escalate to Ombudsperson': 'लोकपाल को एस्केलेट करें',
  'Ombudsperson decides': 'लोकपाल निर्णय देते हैं',
  'Corrected certificate filename': 'सुधारा गया प्रमाणपत्र फ़ाइल नाम',
};

/**
 * WorkflowSpec.js's step labels, off-path labels and per-state `stateLabels` are also literal
 * English strings owned by the engine's matrix definitions, not the RequestState enum — a
 * parallel, per-workflow phrasing (e.g. "Checking certificate" vs the generic "Submitted").
 * Keyed the same way as ACTIONS_HI, for the same reason.
 */
const WORKFLOW_TEXT_HI = {
  Submitted: 'सबमिट किया गया',
  'Faculty review': 'फैकल्टी समीक्षा',
  'HOD approval': 'HOD स्वीकृति',
  'Attendance updated': 'उपस्थिति अपडेट की गई',
  Approved: 'स्वीकृत',
  Rejected: 'अस्वीकृत',
  'Faculty verification': 'फैकल्टी सत्यापन',
  'Institution approval': 'संस्थान स्वीकृति',
  'Added to record': 'रिकॉर्ड में जोड़ा गया',
  Verified: 'सत्यापित',
  'Returned for correction': 'सुधार हेतु वापस भेजा गया',
  Assigned: 'सौंपा गया',
  'Under review': 'समीक्षाधीन',
  Resolved: 'हल किया गया',
  'Ombudsperson review': 'लोकपाल समीक्षा',
  'Ombudsperson decision': 'लोकपाल निर्णय',
  'Office approval': 'कार्यालय स्वीकृति',
  Ready: 'तैयार',
  Validating: 'सत्यापन हो रहा है',
  'With faculty': 'फैकल्टी के पास',
  'With HOD': 'HOD के पास',
  'Updating attendance': 'उपस्थिति अपडेट हो रही है',
  'Checking certificate': 'प्रमाणपत्र जांचा जा रहा है',
  'With institution': 'संस्थान के पास',
  'Writing to record': 'रिकॉर्ड में दर्ज किया जा रहा है',
  'Returned to you': 'आपको वापस भेजा गया',
  'Running eligibility': 'पात्रता जांची जा रही है',
  'With office': 'कार्यालय के पास',
  Routing: 'रूट किया जा रहा है',
  'Assigned to desk': 'डेस्क को सौंपा गया',
  'With the Ombudsperson': 'लोकपाल के पास',
  'Ombudsperson decided': 'लोकपाल ने निर्णय दिया',
};

function sentenceCase(enumName) {
  const words = enumName.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export const DisplayLabels = {
  state(s, locale = 'en') { return s ? ((STATES[locale] ?? STATES.en)[s] ?? sentenceCase(s)) : ''; },

  /** "Submitted → With faculty", or just the destination for the first entry. */
  transition(from, to, locale = 'en') {
    return from ? `${this.state(from, locale)} → ${this.state(to, locale)}` : this.state(to, locale);
  },

  /** SYSTEM reads as automation; the student reads as themselves; staff read as their role. */
  actor(a, studentName, locale = 'en') {
    if (!a) return '';
    if (a === 'SYSTEM') return locale === 'hi' ? 'स्वचालित' : 'Automated';
    if (a === 'STUDENT') return studentName && studentName.trim() ? studentName : (locale === 'hi' ? 'आप' : 'You');
    return Actor.display(a, locale);
  },

  /** RequestHistory stores effects comma-joined; turn that back into readable phrases. */
  effects(commaJoined, locale = 'en') {
    if (!commaJoined || !commaJoined.trim()) return '';
    return commaJoined.split(',').map((s) => s.trim()).filter(Boolean)
      .map((s) => this.effect(s, locale)).join(' · ');
  },

  effect(name, locale = 'en') { return (EFFECTS[locale] ?? EFFECTS.en)[name] ?? sentenceCase(name); },

  status(s, locale = 'en') { return s ? (STATUS[locale] ?? STATUS.en)[s] ?? null : null; },

  /**
   * Every TransitionMatrix.js human() action label and the one input placeholder, translated
   * by exact English text. Falls back to the English text itself for anything not in the
   * dictionary — never throws, never shows a raw key.
   */
  actionLabel(label, locale = 'en') {
    if (!label) return label;
    return locale === 'hi' ? (ACTIONS_HI[label] ?? label) : label;
  },

  /** Same idea as actionLabel(), for WorkflowSpec.js step / off-path / stateLabels text. */
  workflowLabel(label, locale = 'en') {
    if (!label) return label;
    return locale === 'hi' ? (WORKFLOW_TEXT_HI[label] ?? label) : label;
  },

  /** PresentationService's "Currently with X." headline, in either language. */
  currentlyWith(handler, locale = 'en') {
    return locale === 'hi' ? `वर्तमान में ${handler} के पास।` : `Currently with ${handler}.`;
  },

  /**
   * Reviewer-facing asides the engine writes into its effect log; hidden from students. This
   * text is generated once, in English, at transition time and stored (RequestHistory.effectLog)
   * — it is historical record, not a live render, so it is not locale-aware (same reason
   * seed.js/forms.js write `desk()`'s result in English: it is persisted business data).
   */
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

  event(e, locale = 'en') { return e ? this.workflowLabel(sentenceCase(e), locale) : ''; },

  credentialKind(kind, locale = 'en') {
    if (!kind) return locale === 'hi' ? 'क्रेडेंशियल' : 'Credential';
    if (kind === 'INTERNSHIP') return locale === 'hi' ? 'इंटर्नशिप रिकॉर्ड' : 'Internship record';
    if (kind === 'DOCUMENT') return locale === 'hi' ? 'संस्थागत दस्तावेज़' : 'Institutional document';
    return sentenceCase(kind);
  },

  type(t, locale = 'en') { return t ? ((TYPES[locale] ?? TYPES.en)[t] ?? sentenceCase(t)) : ''; },

  leaveType(t, locale = 'en') { return t ? ((LEAVE_TYPES[locale] ?? LEAVE_TYPES.en)[t] ?? sentenceCase(t)) : ''; },

  desk(c, locale = 'en') { return c ? ((DESKS[locale] ?? DESKS.en)[c] ?? DEFAULT_DESK[locale] ?? DEFAULT_DESK.en) : (DEFAULT_DESK[locale] ?? DEFAULT_DESK.en); },

  /**
   * The matrix labels states generically ("Assigned to desk"); when the payload names a
   * handler, say it, so the badge and the headline can never disagree. The special-case check
   * runs against the ORIGINAL English matrix label — translation happens after, so it still
   * fires regardless of locale.
   */
  stateLabel(matrixLabel, handledBy, locale = 'en') {
    if (matrixLabel === 'Assigned to desk' && handledBy && handledBy.trim()) {
      return locale === 'hi' ? `${handledBy} को सौंपा गया` : `Assigned to ${handledBy}`;
    }
    return this.workflowLabel(matrixLabel, locale);
  },

  category(c, locale = 'en') { return c ? ((CATEGORIES[locale] ?? CATEGORIES.en)[c] ?? sentenceCase(c)) : ''; },
};

/** Short human status for a state, or null when the card should say who has it. */
const STATUS = {
  en: {
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
  },
  hi: {
    [RequestState.NOTIFIED]: 'स्वीकृत — उपस्थिति अपडेट कर दी गई है।',
    [RequestState.DOCUMENT_GENERATED]: 'डाउनलोड के लिए तैयार।',
    [RequestState.VERIFICATION_ID_GENERATED]: 'सत्यापित और आपके रिकॉर्ड में जोड़ा गया।',
    [RequestState.ACADEMIC_RECORD_MUTATED]: 'आपके शैक्षणिक रिकॉर्ड में जोड़ा जा रहा है।',
    [RequestState.ATTENDANCE_MUTATED]: 'आपकी उपस्थिति अपडेट की जा रही है।',
    [RequestState.RETURNED]: 'वापस भेजा गया — नीचे कारण देखें।',
    [RequestState.REJECTED]: 'अस्वीकृत — नीचे कारण देखें।',
    [RequestState.RESOLVED]: 'हल कर दिया गया।',
    [RequestState.OMBUDSMAN_REVIEW]: 'एस्केलेट किया गया — लोकपाल के पास।',
    [RequestState.OMBUDSMAN_DECIDED]: 'लोकपाल का निर्णय जारी — नीचे टिप्पणी देखें।',
  },
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
