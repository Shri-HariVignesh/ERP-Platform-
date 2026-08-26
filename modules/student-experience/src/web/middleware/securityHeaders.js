import crypto from 'node:crypto';

/**
 * Baseline response headers, for EVERY response. Mirrors SecurityHeaders.java: nosniff, deny
 * framing, no referrer leak, and a CSP that forbids every off-origin load.
 *
 * script-src is no longer 'none': the in-product Helper widget (public/js/helper.js) is the
 * one exception, and it is admitted by NONCE, not by 'self' or 'unsafe-inline'. A fresh
 * `crypto.randomBytes` nonce is minted per response and exposed as res.locals.cspNonce for the
 * one <script> tag that needs it (see fragments/header.ejs, faculty/header.ejs) — an attacker
 * who manages to inject markup into a response still cannot get a <script> of their own to
 * execute, because they cannot predict this request's nonce. Nothing else changes: object-src,
 * base-uri and frame-ancestors stay fully locked down, and helper.js itself makes no network
 * calls (see the file) so this does not open a data-exfiltration path.
 */
export function securityHeaders(req, res, next) {
  const nonce = crypto.randomBytes(16).toString('base64');
  res.locals.cspNonce = nonce;

  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Content-Security-Policy',
    `default-src 'self'; script-src 'nonce-${nonce}'; object-src 'none'; base-uri 'none'; `
    + "form-action 'self'; frame-ancestors 'none'");
  next();
}
