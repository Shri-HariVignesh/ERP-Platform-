import { studentRepo } from '../repo/studentRepo.js';

/**
 * UGC (Anti-Ragging) compliance that is not a Request — there is no committee to route it to,
 * no actor to approve it, nothing to audit as a transition. It is a plain acknowledgment on the
 * student's own record, scoped by Scope the same as every other student-facing read/write.
 */
export const ComplianceService = {
  acknowledgeAntiRagging(scope) {
    const s = studentRepo.findByIdAndTenantId(scope.studentId, scope.tenantId);
    if (!s) throw new Error('student not in scope');
    s.antiRaggingAffidavitAt = new Date().toISOString();
    return studentRepo.save(s);
  },
};
