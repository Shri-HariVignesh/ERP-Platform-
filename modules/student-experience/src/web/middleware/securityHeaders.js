/**
 * Baseline response headers, for EVERY response. Mirrors SecurityHeaders.java: nosniff, deny
 * framing, no referrer leak, and a CSP that forbids scripts entirely (this app ships none) and
 * every off-origin load.
 */
export function securityHeaders(req, res, next) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Content-Security-Policy',
    "default-src 'self'; script-src 'none'; object-src 'none'; base-uri 'none'; "
    + "form-action 'self'; frame-ancestors 'none'");
  next();
}
