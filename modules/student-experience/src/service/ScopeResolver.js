import { studentAccountRepo } from '../repo/studentAccountRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { Scope } from './Scope.js';
import { StudentAccessException } from './errors.js';

/**
 * PRINCIPAL -> SCOPE, for students. There is no input to this function other than the
 * authenticated session principal — no request parameter can name a different student.
 */
export const ScopeResolver = {
  current(principal) {
    if (!principal || principal.kind !== 'student') {
      throw new StudentAccessException('not authenticated as a student');
    }
    // Re-read per request, exactly as the staff side does: deactivating an account takes
    // effect on the next click rather than at next login.
    const a = studentAccountRepo.findByIdAndTenantId(principal.accountId, principal.tenantId);
    if (!a) throw new StudentAccessException('account no longer valid');
    if (!a.active) throw new StudentAccessException('account is inactive');

    const student = studentRepo.findByIdAndTenantId(a.studentId, a.tenantId);
    if (!student) throw new StudentAccessException('student record not in scope');

    return new Scope(a.tenantId, a.studentId);
  },
};
