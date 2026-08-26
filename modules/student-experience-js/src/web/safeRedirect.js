/**
 * SECURITY (CWE-601): the `back` parameter must never be reflected straight into a redirect,
 * or a crafted link could perform a real action and then bounce the user to an
 * attacker-controlled page. Only the app's own views are acceptable targets.
 */
const ALLOWED = ['/', '/requests', '/leave', '/internship', '/documents', '/academic', '/grievance'];

/** The staff surface gets its OWN allow-list and its own fallback — never shared with the student's. */
const ALLOWED_STAFF = ['/faculty', '/faculty/tasks', '/faculty/leave', '/faculty/internship',
  '/faculty/students', '/faculty/attendance', '/faculty/marks', '/faculty/notifications'];

const FALLBACK = '/requests';
const FALLBACK_STAFF = '/faculty/tasks';

export function resolve(back) { return back && ALLOWED.includes(back) ? back : FALLBACK; }

export function resolveStaff(back) { return back && ALLOWED_STAFF.includes(back) ? back : FALLBACK_STAFF; }
