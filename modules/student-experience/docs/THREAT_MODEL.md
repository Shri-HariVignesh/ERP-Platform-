# Threat model — Student Experience Portal

Derived from source on the audited revision, not from prior reports. Every entry below is a
place an attacker can put bytes or read bytes; Phase 1 probes are driven from this list.

## What is actually being defended

Real authentication is a declared non-goal, so **the security boundary under test is
`tenant_id` + `student_id` scoping**, not login. "Attacker" therefore means *a caller holding
a legitimate session for identity A, trying to read or move identity B's data* — plus the
anonymous caller hitting the one public route.

Stating that plainly matters, because it also says what a finding is NOT: "an unauthenticated
user can act as a student" is the design, not a bug.

## Assets

| Asset | Where it lives | Why it matters |
|---|---|---|
| Student PII | `Student` — name, rollNo, program, section, semester, feeDues | Roll number encodes identity; fee dues are financial |
| Cross-tenant data | every table carries `tenant_id` | Two institutions share one database |
| Verification IDs | `Verification.verifyId`, `DocumentArtifact.verifyId` | Unguessability IS the access control on `/verify` |
| Issued documents | `DocumentArtifact.html`, `.serialNo` | A forged one is a forged institutional credential |
| Grievance content | `GrievancePayload.description`, `anonymous` | Complaints, possibly about staff; anonymity is a promise |
| Request state | `Request.state` + `RequestHistory` | Skipping approval = self-issued credential |

## Actors

| Actor | Holds | Reaches |
|---|---|---|
| Anonymous | nothing | `/verify/{id}` only (by design) |
| Student (session) | a `DemoIdentity` | the 7 views + `/actions/{id}` |
| Other-tenant student | a different `DemoIdentity` | same routes, must see nothing of the first |
| `/sim` demo caller | a session | `/sim/*` with an arbitrary `actor` — accepted escalation |
| External verifier | a scanned/guessed id | `/verify/{id}` |

## Trust boundaries

1. **Session → scope.** `ScopeResolver.current()` maps the session attribute to a
   `Scope(tenantId, studentId)`. Every repository method is keyed on both. This is the
   boundary; everything else is downstream of it.
2. **Tenant boundary.** Inside one database. Enforced only by query shape — there is no row
   level security, so an unscoped query method is a cross-tenant read.
3. **The `/verify` capability.** Deliberately unauthenticated and deliberately reachable from
   another tenant. The unguessable id is the entire control, which makes **the entropy of that
   id a security property**, not a cosmetic detail.
4. **`/sim`.** Explicitly outside the model: it accepts any `actor`. Documented as residual.

## Entry points

Routes (re-derived from `@*Mapping`):

| Route | Method | Attacker-controlled input |
|---|---|---|
| `/` `/requests` | GET | `filter` query param |
| `/switch` | POST | `studentId` |
| `/leave` | GET POST | `leaveType`, `from`, `to`, `reason` |
| `/internship` | GET POST | `company`, `role`, `from`, `to`, `details`, `certificateFilename` |
| `/documents` | GET POST | `docType`, `purpose`, `copies` |
| `/documents/{id}/download` | GET | `id` path param |
| `/academic` `/grievance` | GET | — |
| `/grievance` | POST | `category`, `subject`, `description`, `anonymous` |
| `/actions/{id}` | POST | `id`, `event`, `note`, `input`, `back` |
| `/sim/requests/{id}/advance` `/reject` | POST | `id`, `event`, `actor`, `note`, `reason`, `back` |
| `/verify/{verifyId}` | GET | `verifyId` path param |

Other input surfaces: the session cookie; `app.base-url` (operator-controlled, reaches the QR);
`@ModelAttribute` binding on four forms (mass-assignment surface); `@RequestParam Event`/`Actor`
enum coercion.

Sinks worth naming: `th:utext` appears twice — `documents.html` renders `${d.html}` unescaped,
`verify.html` renders `${qr}` unescaped. Both are the interesting XSS sinks. The document
download concatenates `d.html` into a response body and puts `d.serialNo` in a
`Content-Disposition` header.

## Out of scope by design

Authentication/login, real email/SMS, real file storage, a staff UI, and the `/sim` actor
parameter. None of these are findings.
