import crypto from 'node:crypto';
import { homeFor } from './errorPageContext.js';

/**
 * A synchronizer-token CSRF guard: every state-changing form
 * carries a hidden `_csrf` field; every POST/PUT/DELETE/PATCH is checked against the session's
 * own token before any handler runs.
 */
export function csrfToken(req, res, next) {
  if (!req.session.csrfToken) req.session.csrfToken = crypto.randomBytes(24).toString('hex');
  res.locals.csrfToken = req.session.csrfToken;
  next();
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export function csrfProtect(req, res, next) {
  if (SAFE_METHODS.has(req.method)) return next();
  const token = req.body && req.body._csrf;
  if (!token || !req.session.csrfToken || token !== req.session.csrfToken) {
    return res.status(403).render('error', {
      status: 403, error: 'Forbidden', message: 'Your session expired — please try again.', path: req.path, ...homeFor(req),
    });
  }
  next();
}
