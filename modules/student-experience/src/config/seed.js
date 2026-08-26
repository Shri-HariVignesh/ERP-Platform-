import bcrypt from 'bcryptjs';
import { tenantRepo } from '../repo/tenantRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { studentAccountRepo } from '../repo/studentAccountRepo.js';
import { staffUserRepo } from '../repo/staffUserRepo.js';
import { teachingAssignmentRepo } from '../repo/teachingAssignmentRepo.js';
import { attendanceRepo } from '../repo/attendanceRepo.js';
import { subjectMarkRepo } from '../repo/subjectMarkRepo.js';
import { semesterResultRepo } from '../repo/semesterResultRepo.js';
import { examTermRepo } from '../repo/examTermRepo.js';
import { RequestService } from '../service/RequestService.js';
import { Scope } from '../service/Scope.js';
import { SgpaMath } from '../service/SgpaMath.js';
import { DisplayLabels } from '../view/DisplayLabels.js';
import { LeavePayload } from '../payload/LeavePayload.js';
import { InternshipPayload } from '../payload/InternshipPayload.js';
import { DocumentPayload } from '../payload/DocumentPayload.js';
import { GrievancePayload } from '../payload/GrievancePayload.js';
import { RequestType, Event, Actor, DocType, MarkStatus, StaffRole, LeaveType, GrievanceCategory } from '../domain/enums.js';

const DEMO_PASSWORD = 'campus123';
const CSE = 'Computer Science & Engineering';
const ECE = 'Electronics & Communication';

function day(offset) {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return d.toISOString().slice(0, 10);
}

function isWeekend(iso) {
  const d = new Date(iso + 'T00:00:00Z').getUTCDay();
  return d === 0 || d === 6;
}

function student(id, tenantId, rollNo, name, email, program, department, semester, section, feeDues, advisor, hod, affidavitAck = true) {
  const s = { id, tenantId, rollNo, name, email, program, department, semester, section,
    feeDues, active: true, leaveBalance: 12, advisorName: advisor, hodName: hod,
    antiRaggingAffidavitAt: affidavitAck ? day(-200) : null };
  return studentRepo.save(s);
}

function account(tenantId, studentId) {
  const s = studentRepo.findByIdAndTenantId(studentId, tenantId);
  const a = {
    id: `sa_${studentId.replace(/^s_/, '')}`, tenantId, studentId,
    username: s.rollNo.toLowerCase(), passwordHash: bcrypt.hashSync(DEMO_PASSWORD, 8), active: true,
  };
  return studentAccountRepo.save(a);
}

/** Returns the FUTURE class days, which is what a leave request can legitimately target. */
function seedAttendance(tenantId, studentId) {
  const future = [];
  let i = 0;
  for (let off = -75; off <= 30; off++) {
    const d = day(off);
    if (isWeekend(d)) continue;
    let status;
    if (off > 0) { status = 'SCHEDULED'; future.push(d); }
    else status = (i % 9 === 4) ? 'ABSENT' : 'PRESENT';
    attendanceRepo.save({ tenantId, studentId, date: d, status, sourceRequestId: null, markedByStaffId: null });
    i++;
  }
  return future;
}

const SUBJECT_NAMES = ['Mathematics', 'Data Structures', 'Digital Systems', 'Signals & Systems', 'Professional Communication'];
const ROMAN = { 1: 'I', 2: 'II', 3: 'III', 4: 'IV', 5: 'V', 6: 'VI', 7: 'VII' };

function subjectsFor(prefix, semester) {
  return SUBJECT_NAMES.map((name, i) => ({
    code: `${prefix}${semester}0${i + 1}`, name: `${name} ${ROMAN[semester] ?? semester}`, credits: i === 4 ? 2 : 4,
  }));
}

function hashCode(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) { h = (h * 31 + str.charCodeAt(i)) | 0; }
  return h;
}

/** CONSISTENCY BY CONSTRUCTION — SemesterResult is DERIVED from the seeded SubjectMark rows. */
function seedMarksAndResults(tenantId, studentId, prefix, currentSemester) {
  for (let sem = 1; sem < currentSemester; sem++) {
    const published = [];
    for (const sub of subjectsFor(prefix, sem)) {
      const spread = Math.abs(hashCode(studentId + sub.code)) % 26;
      const m = {
        tenantId, studentId, semester: sem, subjectCode: sub.code, subjectName: sub.name, credits: sub.credits,
        internal: 28 + (spread % 11), external: 38 + (spread % 19),
        status: MarkStatus.FINALIZED, enteredByStaffId: 'seed', updatedAt: new Date().toISOString(),
      };
      published.push(subjectMarkRepo.save(m));
    }
    semesterResultRepo.save({ tenantId, studentId, semester: sem, sgpa: SgpaMath.sgpa(published), credits: SgpaMath.credits(published) });
  }
}

function staff(id, tenantId, username, name, email, department, roles) {
  const u = { id, tenantId, username, passwordHash: bcrypt.hashSync(DEMO_PASSWORD, 8), name, email, department, active: true, roles };
  return staffUserRepo.save(u);
}

function teaches(tenantId, staffId, department, semester, section, prefix, only) {
  for (const sub of subjectsFor(prefix, semester)) {
    if (only && !only.has(sub.code)) continue;
    teachingAssignmentRepo.save({ tenantId, staffId, department, semester, section, subjectCode: sub.code, subjectName: sub.name, credits: sub.credits });
  }
}

function seedTerm(tenantId) {
  examTermRepo.save({ tenantId, name: 'End Semester Examinations, Nov–Dec', startDate: day(21), endDate: day(35), hallTicketReleased: true });
}

function leave(leaveType, from, to, reason) {
  const p = new LeavePayload();
  p.leaveType = leaveType; p.from = from; p.to = to; p.reason = reason;
  return p;
}

function internship(company, role, from, to, details, certificate) {
  const p = new InternshipPayload();
  p.company = company; p.role = role; p.from = from; p.to = to; p.details = details;
  p.certificateRef = { filename: certificate, mime: 'application/pdf', sizeKb: 248 };
  return p;
}

function doc(docType, purpose, copies) {
  const p = new DocumentPayload();
  p.docType = docType; p.purpose = purpose; p.copies = copies;
  return p;
}

function grievance(category, subject, body) {
  const p = new GrievancePayload();
  p.category = category; p.subject = subject; p.description = body; p.anonymous = false;
  p.sys.routedTo = DisplayLabels.desk(category);
  return p;
}

function daysAgo(n) { return day(-n); }

function seedRequests(s, future) {
  // 1. LEAVE — full happy path. Attendance really moves.
  let r = RequestService.create(s, RequestType.LEAVE,
    leave(LeaveType.EVENT, future[1], future[2], 'Represented college at the inter-college hackathon'));
  r = RequestService.transition(s, r.id, Event.APPROVE, Actor.FACULTY, 'Verified with the event convenor');
  RequestService.transition(s, r.id, Event.APPROVE, Actor.HOD, null);

  // 2. LEAVE — parked mid-flow, waiting on the HOD.
  r = RequestService.create(s, RequestType.LEAVE,
    leave(LeaveType.PERSONAL, future[6], future[8], "Sister's wedding at Thrissur"));
  RequestService.transition(s, r.id, Event.APPROVE, Actor.FACULTY, 'Dates clash with no internals');

  // 3. LEAVE — the rejection path.
  r = RequestService.create(s, RequestType.LEAVE, leave(LeaveType.PERSONAL, future[11], future[15], 'Family trip to Ooty'));
  RequestService.transition(s, r.id, Event.REJECT, Actor.FACULTY, 'Overlaps with the Series-II internal exams. Reapply after 30 Nov.');

  // 4. INTERNSHIP — awaiting faculty verification.
  RequestService.create(s, RequestType.INTERNSHIP, internship('Zoho Corporation', 'Backend Intern',
    daysAgo(120), daysAgo(60), 'Worked on the invoicing microservice; Java, Spring Boot, MySQL.', 'zoho-internship-certificate.pdf'));

  // 5. INTERNSHIP — the RETURNED path (rejection path for this workflow).
  r = RequestService.create(s, RequestType.INTERNSHIP, internship('Tata Elxsi', 'Embedded Systems Intern',
    daysAgo(200), daysAgo(150), 'Firmware validation for an automotive ECU.', 'tata-elxsi-scan.pdf'));
  RequestService.transition(s, r.id, Event.RETURN, Actor.FACULTY,
    'Certificate scan is unreadable and the end date does not match the offer letter. Upload a clear copy.');

  // 6. INTERNSHIP — fully verified: academic record written, verifyId + QR issued.
  r = RequestService.create(s, RequestType.INTERNSHIP, internship('Infosys', 'Full-Stack Intern',
    daysAgo(330), daysAgo(240), 'Built an internal dashboard with React and Spring Boot.', 'infosys-completion-certificate.pdf'));
  r = RequestService.transition(s, r.id, Event.VERIFY, Actor.FACULTY, 'Certificate matches the offer letter');
  RequestService.transition(s, r.id, Event.APPROVE, Actor.INSTITUTION, null);

  // 7. DOCUMENT — bonafide. Zero human touches; already READY.
  RequestService.create(s, RequestType.DOCUMENT, doc(DocType.BONAFIDE, 'Passport application', 2));

  // 8. DOCUMENT — transcript. Routed to the office, still waiting.
  RequestService.create(s, RequestType.DOCUMENT, doc(DocType.TRANSCRIPT, 'MS application, Germany', 1));

  // 9. DOCUMENT — hall ticket, approved by the office so the Academic card is populated.
  r = RequestService.create(s, RequestType.DOCUMENT, doc(DocType.HALL_TICKET, 'End semester examinations', 1));
  RequestService.transition(s, r.id, Event.APPROVE, Actor.OFFICE, 'Dues cleared for the exam term');

  // 10. GRIEVANCE — auto-assigned to a desk.
  RequestService.create(s, RequestType.GRIEVANCE, grievance(GrievanceCategory.HOSTEL,
    'No hot water in Block C for six days',
    'The heater on the second floor of Block C has been down since last Tuesday. Two complaints in the register have gone unanswered.'));
}

/** SNIT staff: between them cover every Actor of the frozen matrix, plus a cross-department and a two-role case. */
function seedSnitStaff(tenantId) {
  const anjali = staff('st_anjali', tenantId, 'anjali.menon', 'Prof. Anjali Menon', 'anjali.menon@snit.ac.in', CSE, [StaffRole.FACULTY]);
  teaches(tenantId, anjali.id, CSE, 5, 'A', 'CS', new Set(['CS501', 'CS502', 'CS503']));

  const suresh = staff('st_suresh', tenantId, 'suresh.kumar', 'Prof. Suresh Kumar', 'suresh.kumar@snit.ac.in', CSE, [StaffRole.FACULTY]);
  teaches(tenantId, suresh.id, CSE, 5, 'A', 'CS', new Set(['CS504', 'CS505']));

  const krishna = staff('st_krishna', tenantId, 'krishnakumar', 'Dr. R. Krishnakumar', 'hod.cse@snit.ac.in', CSE, [StaffRole.HOD, StaffRole.FACULTY]);
  teaches(tenantId, krishna.id, CSE, 5, 'A', 'CS', new Set(['CS503']));

  const babu = staff('st_babu', tenantId, 'suresh.babu', 'Prof. Suresh Babu', 'suresh.babu@snit.ac.in', ECE, [StaffRole.FACULTY]);
  teaches(tenantId, babu.id, ECE, 5, 'A', 'EC', new Set(['EC501', 'EC502']));

  staff('st_registrar', tenantId, 'registrar.snit', 'Dr. Latha Pillai (Registrar)', 'registrar@snit.ac.in', null, [StaffRole.INSTITUTION]);
  staff('st_exam', tenantId, 'exam.office', 'Examination Office', 'exams@snit.ac.in', null, [StaffRole.OFFICE]);

  // UGC-mandated statutory committees. No teaching assignment — their reach into a student's
  // requests is institution-wide but strictly grievance-only (see GrievanceVisibility.js),
  // never the class-scoped roster a FACULTY/HOD role would otherwise carry.
  staff('st_icc', tenantId, 'icc.chair', 'Prof. Meena Pillai (ICC Chairperson)', 'icc@snit.ac.in', null, [StaffRole.ICC]);
  staff('st_antiragging', tenantId, 'antiragging.chair', 'Dr. Ravi Nair (Anti-Ragging Committee)', 'antiragging@snit.ac.in', null, [StaffRole.ANTI_RAGGING]);
  staff('st_scst', tenantId, 'scst.cell', 'Prof. Ajitha Kumari (SC/ST Cell)', 'scstcell@snit.ac.in', null, [StaffRole.SC_ST_CELL]);
  staff('st_eoc', tenantId, 'equal.opportunity', 'Prof. Deepa Thomas (Equal Opportunity Cell)', 'eoc@snit.ac.in', null, [StaffRole.EQUAL_OPPORTUNITY_CELL]);
  staff('st_rti', tenantId, 'rti.officer', 'Vinod Menon (RTI / Public Information Officer)', 'rti@snit.ac.in', null, [StaffRole.RTI_OFFICER]);
  staff('st_ombuds', tenantId, 'ombudsperson', 'Justice (Retd.) K. Balakrishnan (Ombudsperson)', 'ombudsperson@snit.ac.in', null, [StaffRole.OMBUDSPERSON]);
}

function seedAceStaff(tenantId) {
  const latha = staff('st_latha', tenantId, 'latha.iyer', 'Dr. Latha Iyer', 'hod.ece@ace.ac.in', ECE, [StaffRole.HOD, StaffRole.FACULTY]);
  teaches(tenantId, latha.id, ECE, 3, 'B', 'EC', null);
  staff('st_ace_office', tenantId, 'office.ace', 'ACE Examination Office', 'exams@ace.ac.in', null, [StaffRole.OFFICE, StaffRole.INSTITUTION]);
}

/** Seeds demo data by driving the REAL state machine — every seeded request has genuine history. */
export function runSeed() {
  const snit = tenantRepo.save({ id: 't_snit', name: 'Sree Narayana Institute of Technology', shortName: 'SNIT', city: 'Kollam, Kerala', accent: '#3b6fd4' });
  // affidavitAck=false on purpose — Hari is the primary demo login, so the acknowledgment
  // banner (and the route behind it) is actually reachable in the demo, not just in seed data.
  const hari = student('s_hari', snit.id, 'SNIT21CS042', 'Hari Prasad', 'hari.prasad@snit.ac.in', 'B.Tech Computer Science', CSE, 5, 'A', 12500, 'Prof. Anjali Menon', 'Dr. R. Krishnakumar', false);
  account(snit.id, hari.id);

  const classDays = seedAttendance(snit.id, hari.id);
  seedMarksAndResults(snit.id, hari.id, 'CS', 5);
  seedTerm(snit.id);
  seedRequests(new Scope(snit.id, hari.id), classDays);

  const divya = student('s_divya', snit.id, 'SNIT21CS051', 'Divya Rajan', 'divya.rajan@snit.ac.in', 'B.Tech Computer Science', CSE, 5, 'A', 0, 'Prof. Anjali Menon', 'Dr. R. Krishnakumar');
  account(snit.id, divya.id);
  const divyaDays = seedAttendance(snit.id, divya.id);
  seedMarksAndResults(snit.id, divya.id, 'CS', 5);
  RequestService.create(new Scope(snit.id, divya.id), RequestType.LEAVE,
    leave(LeaveType.MEDICAL, divyaDays[3], divyaDays[4], 'Dengue — hospital advice attached'));

  const nikhil = student('s_nikhil', snit.id, 'SNIT21EC017', 'Nikhil Varma', 'nikhil.varma@snit.ac.in', 'B.Tech Electronics', ECE, 5, 'A', 0, 'Prof. Suresh Babu', 'Dr. Geetha Menon');
  account(snit.id, nikhil.id);
  const nikhilDays = seedAttendance(snit.id, nikhil.id);
  seedMarksAndResults(snit.id, nikhil.id, 'EC', 5);
  RequestService.create(new Scope(snit.id, nikhil.id), RequestType.LEAVE,
    leave(LeaveType.PERSONAL, nikhilDays[5], nikhilDays[6], "Cousin's wedding in Alappuzha"));
  // A confidential category: visible to the Anti-Ragging Committee (and Institution) only —
  // Prof. Suresh Babu, Nikhil's own class advisor, must NOT see this in his inbox or on
  // Nikhil's profile. Demonstrates GrievanceVisibility.js live, not just in a docstring.
  RequestService.create(new Scope(snit.id, nikhil.id), RequestType.GRIEVANCE,
    grievance(GrievanceCategory.RAGGING, 'Senior students demanding money and errands from first-years',
      'Since the start of the semester, a group of final-year hostel residents has been demanding money and personal errands from first-year students, with threats over refusal.'));

  seedSnitStaff(snit.id);

  const ace = tenantRepo.save({ id: 't_ace', name: 'Amrita College of Engineering', shortName: 'ACE', city: 'Coimbatore, Tamil Nadu', accent: '#a2452f' });
  const meera = student('s_meera', ace.id, 'ACE22EC118', 'Meera Nair', 'meera.nair@ace.ac.in', 'B.Tech Electronics', ECE, 3, 'B', 0, 'Prof. S. Ravi', 'Dr. Latha Iyer');
  account(ace.id, meera.id);
  const aceDays = seedAttendance(ace.id, meera.id);
  seedMarksAndResults(ace.id, meera.id, 'EC', 3);
  seedTerm(ace.id);
  seedAceStaff(ace.id);
  const aceScope = new Scope(ace.id, meera.id);
  RequestService.create(aceScope, RequestType.DOCUMENT, doc(DocType.BONAFIDE, 'Passport application', 1));
  RequestService.create(aceScope, RequestType.LEAVE, leave(LeaveType.MEDICAL, aceDays[2], aceDays[3], 'Viral fever'));
}
