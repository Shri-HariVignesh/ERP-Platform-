# Threat model — Student Experience (JS)

Derived from the actual routes in [`src/web/`](../src/web), not from a prior report. Every
entry below is a place a caller can put bytes in or read bytes out.

## What is actually being defended

Real authentication (SSO, MFA, self-service account recovery) is a declared non-goal — login is
a fixed set of seeded demo accounts. **The security boundary under test is `tenantId` +
`studentId` scoping**, not identity verification strength. "Attacker" therefore means *a caller
holding a legitimate session for identity A, trying to read or move identity B's data* — plus
the anonymous caller hitting the one public route.

Stating that plainly matters, because it also says what a finding is NOT: "the demo password is
the same for every seeded account" is the design, not a bug.

## Assets

| Asset | Where it lives | Why it matters |
|---|---|---|
| Student PII | `students` table — name, rollNo, program, section, semester, feeDues | Roll number encodes identity; fee dues are financial |
| Cross-tenant data | every table carries `tenantId` | Two institutions share one database |
| Verification IDs | `verifications.verifyId`, `documents.verifyId` | Unguessability IS the access control on `/verify` |
| Issued documents | `documents.html`, `.serialNo` | A forged one is a forged institutional credential |
| Grievance content | `GrievancePayload.description`, `.anonymous` | Complaints, possibly about staff; anonymity is a promise |
| Request state | `requests.state` + `request_history` | Skipping approval = a self-issued credential |

## Actors

| Actor | Holds | Reaches |
|---|---|---|
| Anonymous | nothing | `/login`, `/verify/:id` only |
| Student (session) | a `StudentAccount` login | the 7 student views + `/actions/:id` |
| Staff (session) | a `StaffUser` login with 1+ roles | the 8 faculty views + `/faculty/requests/:id/act` + `/faculty/attendance`, `/faculty/marks` |
| Other-tenant identity | a different session, same kind | same routes, must see nothing of the first |
| External verifier | a scanned or guessed id | `/verify/:verifyId` |

There is no demo hook that accepts a client-supplied `actor` — unlike the Java module's
`@Profile("demo")`-gated `/sim` controller, no equivalent was ported here, so there is no route
in this module (default or otherwise) where a caller can name who they are acting as. Every
actor is derived server-side, either hardcoded (`Actor.STUDENT` on the student action route) or
computed from the session principal's roles (`StaffScopeResolver.actorFor`).

## Trust boundaries

1. **Session → scope.** `ScopeResolver.current()` (student side) and
   `StaffScopeResolver.current()` (staff side) map the session's `principal` to a `Scope` /
   `StaffScope`. Every repository function is keyed on the result. This is the boundary;
   everything else is downstream of it.
2. **Tenant boundary.** One SQLite database, shared by every seeded tenant. Enforced only by
   query shape — there is no row-level security, so an unscoped query function would be a
   cross-tenant read if one ever existed (see `REPOSITORY_SCOPE_RULES.md` for why none do).
3. **The `/verify` capability.** Deliberately unauthenticated and deliberately reachable from
   another tenant. The unguessable id is the entire control, which makes **the entropy of that
   id a security property**, not a cosmetic detail (see `SECURITY.md`).
4. **CSRF token.** Bound to the session, required on every state-changing request. A stolen
   session cookie defeats it same as any synchronizer-token scheme; it is not a defence against
   a fully compromised session, only against a forged cross-site request riding an otherwise
   valid one.
5. **Client-side script surface.** `script-src` is a fresh nonce per response, not `'none'`
   anymore — the Helper widget (`public/js/helper.js`) is the one script admitted, and only
   because the response that renders it carries the matching nonce. Markup an attacker manages
   to inject into a page still cannot execute a `<script>` of its own: they cannot predict this
   request's nonce, and the file itself is static (not built from request/session data) and
   makes no network calls, so there is no exfiltration path even if it were somehow substituted.

## Entry points

| Route | Method | Caller-controlled input |
|---|---|---|
| `/login` | GET POST | `username`, `password` |
| `/`, `/home`, `/requests` | GET | `filter` query param |
| `/leave` | GET POST | `leaveType`, `from`, `to`, `reason` |
| `/internship` | GET POST | `company`, `role`, `from`, `to`, `details`, `certificateFilename` |
| `/documents` | GET POST | `docType`, `purpose`, `copies` |
| `/documents/:id/download` | GET | `id` path param |
| `/academic`, `/grievance` (GET) | GET | — |
| `/grievance` | POST | `category`, `subject`, `description`, `anonymous` |
| `/actions/:id` | POST | `id`, `event`, `note`, `input`, `back` |
| `/verify/:verifyId` | GET | `verifyId` path param |
| `/faculty/*` (GET) | GET | `filter`, `q`, `clazz`, `subject`, `date` query params |
| `/faculty/attendance`, `/faculty/marks` | POST | `clazz`, `subject`, `date`/`action`, per-student `status_*`/`internal_*`/`external_*` fields |
| `/faculty/requests/:id/act` | POST | `id`, `event`, `note`, `back` |
| `/lang/:locale` | GET | `locale` path param (allow-listed to `en`/`hi`), `back` query param (redirect target, allow-listed same as the `back` form field elsewhere) |

Other input surfaces: the session cookie; `CAMPUSOS_BASE_URL` (operator-controlled, reaches the
QR — never taken from the request, so a spoofed `Host` header cannot rewrite where a QR points);
form-body parsing (mass-assignment surface — every POST handler reads named fields explicitly,
never spreads `req.body` into a payload object).

Sinks worth naming: `documents.ejs` renders `d.html` with `<%- %>` (unescaped) by design — it is
server-generated document markup, never user input reflected verbatim. `verify.ejs` renders the
QR SVG the same way, for the same reason. Neither takes a caller-supplied string directly into
that unescaped position.

## Out of scope by design

Real authentication strength (password policy, MFA, lockout), real email/SMS, real file
storage, rate limiting on `/verify` or `/login`, and TLS termination (this app serves plain
HTTP; put a real TLS-terminating proxy in front of anything beyond a local demo).
