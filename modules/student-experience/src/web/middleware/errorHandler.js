import { IllegalTransitionException } from '../../engine/IllegalTransitionException.js';
import { ScopeAccessException } from '../../service/errors.js';

/**
 * SECURITY (CWE-209): the internal exception MESSAGE is logged, never returned. Probing
 * /documents/{id}/download for another tenant's ids must not answer with engine vocabulary —
 * the caller learns the move was refused; the operator gets the reason from the log.
 *
 * One handler for scope refusals on both sides (staff and student) on purpose: if a staff
 * refusal and a student refusal read differently, the difference itself is information.
 */
export function errorHandler(err, req, res, next) {
  if (err instanceof IllegalTransitionException) {
    console.warn('IllegalTransitionException:', err.message);
    return res.status(400).render('error', {
      status: 400, error: 'Bad Request', message: 'That request is not available.', path: req.path,
    });
  }
  if (err instanceof ScopeAccessException) {
    console.warn(`${err.name}:`, err.message);
    return res.status(403).render('error', {
      status: 403, error: 'Forbidden', message: 'Not available in your scope.', path: req.path,
    });
  }
  console.error(err);
  return res.status(500).render('error', {
    status: 500, error: 'Internal Server Error', message: 'Something went wrong handling that request.', path: req.path,
  });
}

export function notFoundHandler(req, res) {
  res.status(404).render('error', { status: 404, error: 'Not Found', message: 'Nothing here.', path: req.path });
}
