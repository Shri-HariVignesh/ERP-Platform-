# Security — Student Experience (JS)

This is a description of the mitigations actually present in this codebase — grep the file
named next to each item to verify it. It is not a claim that this module has been through a
formal audit; treat it as a map of what to check, not a certificate.

Threat model: [`THREAT_MODEL.md`](THREAT_MODEL.md).

## Running the checks that exist

```bash
npm test
```

`npm test` runs the engine, scoping, and view-helper test suite
([`../test/`](../test)) — it is not a full security regression suite. There is no live-probe
script analogous to a `security-check.sh` in this module yet; anyone adding one should assert
the specific claims below, live, against a running instance.

## What is actually in place

| Area | Mitigation | Where |
|---|---|---|
| Verification ID entropy | 12 symbols from a 32-character alphabet via `crypto.randomBytes` = 60 bits; alphabet omits I/L/O/U | [`src/engine/SideEffectDispatcher.js`](../src/engine/SideEffectDispatcher.js) `verifyId()` |
| CSRF | Synchronizer token, one per session, required on every non-GET/HEAD/OPTIONS request | [`src/web/middleware/csrf.js`](../src/web/middleware/csrf.js) |
| Response headers | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, a CSP with `script-src` scoped to a fresh per-request nonce (not `'self'`, not `'unsafe-inline'`) | [`src/web/middleware/securityHeaders.js`](../src/web/middleware/securityHeaders.js) |
| Helper widget script | The one script the nonce admits ([`public/js/helper.js`](../public/js/helper.js)) makes no network calls (no fetch/XHR/WebSocket) and never uses `eval`/`innerHTML` — topic content is read from a same-origin JSON island via `textContent` only | [`public/js/helper.js`](../public/js/helper.js) |
| Session fixation | A new session id is issued at login (`req.session.regenerate`) before the principal is attached | [`src/web/loginRoutes.js`](../src/web/loginRoutes.js) |
| Session cookie | `httpOnly`, `sameSite: 'lax'` | [`src/server.js`](../src/server.js) |
| Login oracle | One outcome for "no such user" and "wrong password"; an unknown username still runs a dummy bcrypt compare so response timing does not distinguish the two cases | [`src/web/middleware/auth.js`](../src/web/middleware/auth.js) |
| Unbounded input | Every form field has an explicit max length (`sized()` in Forms); the free-text `input` field behind the internship resubmit action is capped separately since it does not pass through the form validators | [`src/web/forms.js`](../src/web/forms.js), [`src/web/actionsRoutes.js`](../src/web/actionsRoutes.js) |
| Malformed dates | `parseDate()` rejects anything not matching `yyyy-MM-dd` before it reaches a `Date` constructor, so a bad date is an ordinary validation error, not an unhandled exception | [`src/payload/RequestPayload.js`](../src/payload/RequestPayload.js) |
| Log injection | Control characters are stripped and the value capped before a caller-supplied request id reaches a thrown message (which is also what gets logged) | [`src/engine/RequestStateMachine.js`](../src/engine/RequestStateMachine.js) `safeForMessage()` |
| Error responses | `IllegalTransitionException`/`ScopeAccessException` map to a generic body (`"That request is not available."` / `"Not available in your scope."`); the real exception message is logged server-side only, never echoed | [`src/web/middleware/errorHandler.js`](../src/web/middleware/errorHandler.js) |
| Document-type tampering | A student can only create `DOCUMENT` requests for a fixed allowlist (`STUDENT_REQUESTABLE`); `INTERNSHIP_VERIFICATION`, which the SYSTEM actor mints, is rejected server-side even if POSTed directly | [`src/web/portalRoutes.js`](../src/web/portalRoutes.js) |
| Scoping | See [`REPOSITORY_SCOPE_RULES.md`](REPOSITORY_SCOPE_RULES.md) — enforced structurally at the repository layer, not by convention | [`src/repo/`](../src/repo) |
| Client-supplied actor | No route accepts an `actor` parameter from the client. The student endpoint hardcodes `Actor.STUDENT`; the faculty endpoint derives the actor from the session principal's roles intersected with the frozen matrix | [`src/web/actionsRoutes.js`](../src/web/actionsRoutes.js), [`src/service/StaffScopeResolver.js`](../src/service/StaffScopeResolver.js) |

## Residual risk — accepted by design

1. **No real identity provider.** Login is a fixed set of seeded demo accounts with a shared
   password. Real authentication (SSO, MFA, password reset) is a declared non-goal.
2. **No rate limiting on `/verify` or `/login`.** 60 bits of entropy makes `/verify`
   enumeration infeasible as a practical matter, and login rate limiting needs infrastructure
   this prototype doesn't carry (a reverse proxy, a token bucket store). Both are defence in
   depth, not the primary control.
3. **The session cookie has no `Secure` flag.** The app serves plain HTTP for local/demo use;
   set `Secure` (and terminate TLS in front of it) before deploying this anywhere reachable
   over an untrusted network.
4. **`express-session`'s default `MemoryStore` is in-process.** Fine for a single instance and a
   demo; a real deployment needs a shared session store (Redis, a database-backed store) before
   running more than one process.

Also out of scope by declaration, same as the rest of the module: real payments, real file
storage, external notification delivery, a second identity provider.

## If you find something

Re-derive it against the running app before filing it — grep the table above for whether the
mitigation you're probing is actually claimed, and check the source file directly rather than
trusting this document's prose.
