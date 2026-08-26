import { Router } from 'express';
import { authenticate, homeFor } from './middleware/auth.js';

export const loginRoutes = Router();

loginRoutes.get('/login', (req, res) => {
  const locals = {};
  // SECURITY: one message for every failure mode — must not be a staff-directory oracle.
  if (req.query.error !== undefined) locals.error = 'Those credentials were not accepted.';
  if (req.query.logout !== undefined) locals.flash = 'Signed out.';
  res.render('login', locals);
});

loginRoutes.post('/login', (req, res) => {
  const { username, password } = req.body;
  const principal = authenticate(username ?? '', password ?? '');
  if (!principal) return res.redirect('/login?error');

  // SECURITY: a new session id at login, so a pre-set cookie cannot be ridden into an
  // authenticated session (session-fixation defence).
  req.session.regenerate((err) => {
    if (err) return res.redirect('/login?error');
    req.session.principal = principal;
    res.redirect(homeFor(principal));
  });
});

loginRoutes.post('/logout', (req, res) => {
  req.session.destroy(() => {
    res.clearCookie('connect.sid');
    res.redirect('/login?logout');
  });
});
