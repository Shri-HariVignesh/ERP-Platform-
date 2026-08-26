import { tenantRepo } from '../src/repo/tenantRepo.js';
import { studentRepo } from '../src/repo/studentRepo.js';
import { Scope } from '../src/service/Scope.js';
import { LeavePayload } from '../src/payload/LeavePayload.js';
import { InternshipPayload } from '../src/payload/InternshipPayload.js';
import { DocumentPayload } from '../src/payload/DocumentPayload.js';
import { GrievancePayload } from '../src/payload/GrievancePayload.js';
import { LeaveType, DocType, GrievanceCategory } from '../src/domain/enums.js';

let counter = 0;

/** A minimal tenant + active student, ready to build a Scope from. */
export function fixture(name) {
  counter++;
  const tenantId = `t_${name}_${counter}`;
  const studentId = `s_${name}_${counter}`;
  tenantRepo.save({ id: tenantId, name: `${name} Institute`, shortName: name.toUpperCase(), city: 'Test City', accent: '#000' });
  const student = studentRepo.save({
    id: studentId, tenantId, rollNo: `${name.toUpperCase()}001`, name: `${name} Student`,
    email: `${name}@test.edu`, program: 'B.Tech', department: 'CSE', semester: 5, section: 'A',
    feeDues: 0, active: true, leaveBalance: 12, advisorName: 'Advisor', hodName: 'HOD',
  });
  return { scope: new Scope(tenantId, studentId), student };
}

function iso(offsetDays) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.toISOString().slice(0, 10);
}

export function leavePayload(from = iso(2), to = iso(3), type = LeaveType.PERSONAL) {
  const p = new LeavePayload();
  p.leaveType = type; p.from = from; p.to = to; p.reason = 'test reason';
  return p;
}

export function internshipPayload(withCertificate = true) {
  const p = new InternshipPayload();
  p.company = 'Test Co'; p.role = 'Intern';
  p.from = iso(-60); p.to = iso(-10); p.details = 'did stuff';
  p.certificateRef = withCertificate ? { filename: 'cert.pdf', mime: 'application/pdf', sizeKb: 100 } : null;
  return p;
}

export function documentPayload(docType = DocType.BONAFIDE, purpose = 'testing') {
  const p = new DocumentPayload();
  p.docType = docType; p.purpose = purpose; p.copies = 1;
  return p;
}

export function grievancePayload(category = GrievanceCategory.ACADEMIC) {
  const p = new GrievancePayload();
  p.category = category; p.subject = 'test subject'; p.description = 'test description'; p.anonymous = false;
  return p;
}
