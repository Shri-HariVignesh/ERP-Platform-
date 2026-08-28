import { requestRepo } from '../repo/requestRepo.js';
import { studentRepo } from '../repo/studentRepo.js';
import { tenantRepo } from '../repo/tenantRepo.js';
import { RequestStateMachine } from '../engine/RequestStateMachine.js';
import { PresentationService } from './PresentationService.js';
import { IllegalTransitionException } from '../engine/IllegalTransitionException.js';

export const RequestService = {
  student(s) {
    const st = studentRepo.findByIdAndTenantId(s.studentId, s.tenantId);
    if (!st) throw new Error('student not in scope');
    return st;
  },

  tenant(s) { return tenantRepo.findById(s.tenantId); },

  all(s, locale = 'en') {
    return PresentationService.cards(s, requestRepo.findByTenantIdAndStudentIdOrderByCreatedAtDesc(s.tenantId, s.studentId), locale);
  },

  ofType(s, type, locale = 'en') {
    return PresentationService.cards(s, requestRepo.findByTenantIdAndStudentIdAndTypeOrderByCreatedAtDesc(s.tenantId, s.studentId, type), locale);
  },

  card(s, id, locale = 'en') {
    const r = requestRepo.findByIdAndTenantIdAndStudentId(id, s.tenantId, s.studentId);
    if (!r) throw new IllegalTransitionException('request not visible in scope');
    return PresentationService.card(s, r, locale);
  },

  raw(s, id) {
    const r = requestRepo.findByIdAndTenantIdAndStudentId(id, s.tenantId, s.studentId);
    if (!r) throw new IllegalTransitionException('request not visible in scope');
    return r;
  },

  create(s, type, payload) { return RequestStateMachine.create(s, type, payload); },

  transition(s, id, e, a, note, patch, actedByStaffId, actedByStaffName) {
    return RequestStateMachine.transition(s, id, e, a, note, patch, actedByStaffId, actedByStaffName);
  },
};
