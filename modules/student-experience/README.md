# CampusOS — Student Experience Portal (prototype)

A multi-tenant student portal built on **one polymorphic request engine**. Leave, Internship,
Documents and Grievance are not four systems — they are four rows in one `requests` table,
moved by one guarded state machine, rendered through one card and one timeline.

## Run

```bash
npm install
npm start           # http://localhost:8080
npm run dev          # same, with --watch
npm test             # unit + engine + scoping tests
```

The database is in-memory (`better-sqlite3` against `:memory:`) and reseeded on every start via
the real state machine — every seeded request has genuine history rows and genuinely fired side
effects, nothing writes a state directly. Set `CAMPUSOS_DB_FILE=./campusos.db` to persist across
restarts instead.

## Demo accounts

Password for every seeded account: `campus123`.

| Username | Who |
|---|---|
| `snit21cs042` | Hari Prasad — SNIT, CSE Sem 5 (the primary demo student) |
| `snit21cs051` | Divya Rajan — SNIT, CSE Sem 5 (classmate) |
| `snit21ec017` | Nikhil Varma — SNIT, ECE Sem 5 (different department) |
| `ace22ec118` | Meera Nair — ACE (a different tenant entirely) |
| `anjali.menon` | Faculty · CSE Sem 5 A |
| `suresh.kumar` | Faculty · CSE Sem 5 A (other subjects) |
| `krishnakumar` | HOD + Faculty · CSE |
| `suresh.babu` | Faculty · ECE (other department) |
| `registrar.snit` | Institution |
| `exam.office` | Examination Office |
| `latha.iyer` | HOD + Faculty · ACE (other tenant) |

## Demo script (about three minutes)

Sign in as `snit21cs042` and note the attendance figure on Home. Go to **Documents**, request a
**Bonafide Certificate** — it comes back issued before the page finishes loading, and its
timeline shows *Office approval* struck through as **skipped by automation**; that is the
Digital Razor, because "is this an active student?" is a question the system can answer itself.
Request a **Transcript** and it stops at *With office*, because that one carries an
attestation. Now go to **Leave**, apply for two upcoming days, sign out, sign back in as
`anjali.menon` (Hari's faculty advisor) and open **My Tasks** — **Approve** it, then sign in as
`krishnakumar` (HOD) and approve again — watch the flash message and Hari's Academic attendance
figure move, because HOD approval writes the attendance rows itself instead of asking a clerk
to. On **Internship**, submit one with a certificate filename; it lands in `anjali.menon`'s
faculty inbox (she teaches Hari's class) for verification — sign in as her and verify it, then
sign in as `registrar.snit` (Institution) and approve — the system writes the academic record,
mints a verification ID, renders a QR, and files the certificate into Documents in one step.
Open `/verify/{id}` to see the public page an employer would land on after scanning that QR.
Finally, sign in as `ace22ec118` (Meera, a different tenant) and confirm **My Requests** shows
only her own two requests.

## The engine

```
Request { id, tenantId, studentId, type, state, payload(JSON), createdAt, updatedAt }
RequestHistory { requestId, fromState, toState, actor, at, note, effects, effectLog }
```

* **One table.** `type` ∈ `LEAVE | INTERNSHIP | DOCUMENT | GRIEVANCE`. Adding a workflow means
  adding a `WorkflowSpec` to [`TransitionMatrix`](src/engine/TransitionMatrix.js) and a payload
  class — no schema change, no new route, no new template.
* **Typed payloads.** `payload` is produced from a class per type
  ([`LeavePayload`](src/payload/LeavePayload.js), [`InternshipPayload`](src/payload/InternshipPayload.js), …)
  via [`PayloadCodec`](src/payload/PayloadCodec.js), which validates before it writes. Never a
  free blob.
* **One guard.** [`RequestStateMachine.transition(...)`](src/engine/RequestStateMachine.js) is
  the only code in the application that changes a state. It resolves `(type, state, event,
  actor, guard)` against `TransitionMatrix`, throws `IllegalTransitionException` on anything
  else, appends a `RequestHistory` row, then fires the edge's declared side effects — in one
  transaction.
* **Automation is an actor.** After every move, `autopilot()` keeps firing `SYSTEM` edges whose
  guards pass. That is what makes a bonafide arrive already issued, and what turns an HOD
  approval into an attendance write without anyone pressing another button.
* **Scope.** Every module in [`src/repo/`](src/repo) exports only scoped query functions —
  never an unscoped `findAll`. Every declared function is keyed on `tenantId` **and**
  `studentId`. See [`docs/REPOSITORY_SCOPE_RULES.md`](docs/REPOSITORY_SCOPE_RULES.md) for the
  documented exceptions.

Full state contract: [`docs/STATE_CONTRACT.md`](docs/STATE_CONTRACT.md).

## Faculty side

Every staff decision goes through one endpoint,
[`POST /faculty/requests/:id/act`](src/web/facultyRoutes.js) — the body carries an event and an
optional note, **never an actor**. The actor is derived server-side from the authenticated
principal's roles intersected with the frozen matrix
([`StaffScopeResolver.actorFor`](src/service/StaffScopeResolver.js)), so there is nowhere in any
form for a client to name who it is acting as. Attendance and marks are a separate, non-workflow
write path ([`AcademicWriteService`](src/service/AcademicWriteService.js)) authorized by a
teaching assignment, never by a role alone — an HOD who teaches nothing may author nothing.

## Scope

Seven student views: Home, My Requests, Leave, Internship, Documents & Certificates, Academic
(read-only), Grievance. Eight faculty views: Home, My Tasks, Students, Leave, Internship,
Attendance, Marks & Results, Notifications.

Not built, by design: real authentication (a fixed set of seeded demo accounts stands in), real
payments, real file storage (a certificate is recorded as a filename reference), external
notification delivery (in-app only, derived from the audit trail itself — nothing is stored
twice).

`/verify/{verifyId}` is a public page, not an eighth student view — it is what an employer sees
after scanning a certificate QR. Without it the generated QR would point nowhere.

## Security

See [`docs/SECURITY.md`](docs/SECURITY.md) for what's actually mitigated (CSRF, security
headers, verification-id entropy, scoping, input bounds, opaque error responses) and
[`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) for what's being defended against.
