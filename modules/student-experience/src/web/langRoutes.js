import { Router } from 'express';

export const langRoutes = Router();

const SUPPORTED = new Set(['en', 'hi']);

/**
 * Same discipline as safeRedirect.js: `back` is redirect-only (this route has no side effect
 * to protect), but an unvalidated redirect target is still an open-redirect footgun, so it
 * gets the same allow-list treatment rather than a laxer "starts with /" check.
 */
const ALLOWED_BACK = new Set([
  '/login',
  '/', '/requests', '/leave', '/internship', '/documents', '/academic', '/grievance',
  '/faculty', '/faculty/tasks', '/faculty/leave', '/faculty/internship',
  '/faculty/students', '/faculty/attendance', '/faculty/marks', '/faculty/notifications',
]);

// A plain cookie, not a session field: login/logout both replace the session outright
// (session-fixation defence, and a clean slate on sign-out) — a language preference living on
// the session would silently vanish at exactly those two moments, which is not a language
// change we ever asked for.
export const LOCALE_COOKIE = 'locale';

export function readLocaleCookie(req) {
  const header = req.headers.cookie;
  if (!header) return null;
  const match = header.match(/(?:^|;\s*)locale=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

langRoutes.get('/lang/:locale', (req, res) => {
  const locale = SUPPORTED.has(req.params.locale) ? req.params.locale : 'en';
  res.cookie(LOCALE_COOKIE, locale, {
    maxAge: 1000 * 60 * 60 * 24 * 365, httpOnly: true, sameSite: 'lax', secure: false,
  });

  const principal = req.session && req.session.principal;
  const fallback = principal && principal.kind === 'staff' ? '/faculty' : '/';
  const back = req.query.back;
  res.redirect(ALLOWED_BACK.has(back) ? back : fallback);
});
