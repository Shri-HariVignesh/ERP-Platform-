import bcrypt from 'bcryptjs';
import { staffUserRepo } from '../../repo/staffUserRepo.js';
import { studentAccountRepo } from '../../repo/studentAccountRepo.js';

/**
 * ONE login form, two kinds of account — mirrors PortalUserDetailsService. Staff is resolved
 * first, so a username present in BOTH tables would silently shadow the student; the seeder
 * keeps the two username sets disjoint.
 *
 * SECURITY: one opaque outcome for every failure mode. "No such user" and "wrong password"
 * must stay indistinguishable, so the hash comparison runs even for an unknown username —
 * response timing must not turn the login form into an account-directory oracle.
 */
const DUMMY_HASH = bcrypt.hashSync('not-a-real-password', 10);

export function authenticate(username, password) {
  const staff = staffUserRepo.findByUsername(username);
  if (staff) {
    const ok = bcrypt.compareSync(password, staff.passwordHash);
    if (ok && staff.active) return { kind: 'staff', staffId: staff.id, tenantId: staff.tenantId };
    return null;
  }
  const student = studentAccountRepo.findByUsername(username);
  if (student) {
    const ok = bcrypt.compareSync(password, student.passwordHash);
    if (ok && student.active) return { kind: 'student', accountId: student.id, tenantId: student.tenantId };
    return null;
  }
  // Unknown username: still hash, so timing does not distinguish "no such user".
  bcrypt.compareSync(password, DUMMY_HASH);
  return null;
}

/**
 * DEMO MODE: resolves a principal from username alone, no password check. Used only by the
 * one-click demo-account buttons on the login page — never by the real credentialed form.
 */
export function principalForUsername(username) {
  const staff = staffUserRepo.findByUsername(username);
  if (staff && staff.active) return { kind: 'staff', staffId: staff.id, tenantId: staff.tenantId };

  const student = studentAccountRepo.findByUsername(username);
  if (student && student.active) return { kind: 'student', accountId: student.id, tenantId: student.tenantId };

  return null;
}

/** Requires an authenticated student principal; otherwise redirects to /login. */
export function requireStudent(req, res, next) {
  if (req.session.principal && req.session.principal.kind === 'student') return next();
  return res.redirect('/login');
}

/** Requires an authenticated staff principal; otherwise redirects to /login. */
export function requireStaff(req, res, next) {
  if (req.session.principal && req.session.principal.kind === 'staff') return next();
  return res.redirect('/login');
}

/** Post-login routing, decided by PRINCIPAL TYPE, never by anything the form submitted. */
export function homeFor(principal) {
  return principal && principal.kind === 'student' ? '/home' : '/faculty';
}
