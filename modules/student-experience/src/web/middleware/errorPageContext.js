/**
 * The error page's "take me back" link should go somewhere that actually exists for this
 * caller: a signed-in student's home, a signed-in staff member's faculty home, or the login
 * page for anyone without a session. Shared by errorHandler and csrfProtect — every renderer
 * of the `error` view must supply this, or the template's `<%= homeHref %>` throws.
 */
export function homeFor(req) {
  const principal = req.session && req.session.principal;
  if (principal && principal.kind === 'student') return { homeHref: '/home', homeLabel: 'Home' };
  if (principal && principal.kind === 'staff') return { homeHref: '/faculty', homeLabel: 'Faculty home' };
  return { homeHref: '/login', homeLabel: 'Sign in' };
}
