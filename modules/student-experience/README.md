# CampusOS — Student Experience Portal (prototype)

A multi-tenant student portal built on **one polymorphic request engine**. Leave, Internship,
Documents and Grievance are not four systems — they are four rows in one `requests` table,
moved by one guarded state machine, rendered through one card and one timeline.

## Run

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. H2 is in-memory; a `CommandLineRunner` seeds demo data on every
start. No external services, no credentials, no setup.

## Demo script (about three minutes)

Open **Home** as *Hari Prasad · SNIT (CSE, Sem 5)* and note the attendance figure. Go to
**Documents**, request a **Bonafide Certificate** — it comes back `Ready` before the page
finishes loading, and its timeline shows *Office approval* struck through as **skipped by
automation**; that is the Digital Razor, because "is this an active student?" is a question
the system can answer itself. Request a **Transcript** and it stops at *With office*, because
that one carries an attestation. Now go to **Leave**, apply for two upcoming days, then open
the request's *Audit trail* and use the demo hook to have **Faculty approve** and then
**HOD approve** — watch the headline report `AttendanceRecord mutated: … Attendance 88.9% →
89.3%`, then check **Academic** and see the same new number, because approval wrote the
attendance rows itself instead of asking a clerk to. On **Internship**, the Tata Elxsi record
sits in `Returned to you` with the faculty's reason; hit **Fix & resubmit**, then advance it
through *Faculty verifies* and *Institution approves* — the system writes the academic record,
mints a verification ID, renders a QR, and files the certificate into Documents in one step.
Scan or click that ID to land on the public `/verify/{id}` page. Finally, switch the identity
picker to *Meera Nair · ACE* — a different tenant — and **My Requests** shows only her two
requests; any attempt to touch Hari's data is refused by scope, not by the UI hiding a button.

## The engine

```
Request { id, tenantId, studentId, type, state, payload(JSON), createdAt }
RequestHistory { requestId, fromState, toState, actor, at, note, effects, effectLog }
```

* **One table.** `RequestType` ∈ {LEAVE, INTERNSHIP, DOCUMENT, GRIEVANCE}. Adding a workflow
  means adding a `WorkflowSpec` to `TransitionMatrix` and a payload DTO — no schema change,
  no new controller, no new template.
* **Typed payloads.** `payload` is serialised from a DTO per type (`LeavePayload`,
  `InternshipPayload`, …) via `PayloadCodec`, which validates before it writes. Never a free blob.
* **One guard.** `RequestStateMachine.transition(request, event, actor, note)` is the only
  code in the application that changes a state. It resolves `(type, state, event, actor, guard)`
  against `TransitionMatrix`, throws `IllegalTransitionException` on anything else, appends a
  `RequestHistory` row, then fires the edge's declared side effects — in one transaction.
* **Automation is an actor.** After every move, `autopilot()` keeps firing `SYSTEM` edges whose
  guards pass. That is what makes a bonafide arrive already `Ready`, and what turns
  `HOD approves` into an attendance write without anyone pressing another button.
* **Scope.** Every repository extends the bare `Repository` marker rather than `JpaRepository`,
  so `findAll()` and `findById()` do not exist to be called by accident. Every declared method
  is keyed on `tenantId` **and** `studentId`. Two documented exceptions, both in
  `repo/README.txt`: `TenantRepository` (the tenant is the scope root) and
  `VerificationRepository.findByVerifyId` (the public QR target — the unguessable id is the
  capability).

## The frozen state contract

**Leave** `SUBMITTED →(System, auto-validate) FACULTY_PENDING →(Faculty) HOD_PENDING →(HOD)
ATTENDANCE_MUTATED →(System, writes attendance) NOTIFIED`; rejection path `→ REJECTED`
from either human state.

**Internship** `SUBMITTED →(System, certificate check) FACULTY_VERIFICATION →(Faculty)
INSTITUTION_APPROVAL →(Institution) ACADEMIC_RECORD_MUTATED →(System, writes record + verifyId
+ QR + publishes certificate) VERIFICATION_ID_GENERATED`; return path `→ RETURNED →(Student,
resubmit) SUBMITTED`; rejection `→ REJECTED`.

**Documents** `SUBMITTED →(System, eligibility) DOCUMENT_GENERATED` when auto-eligible, else
`→ APPROVAL →(Office) DOCUMENT_GENERATED`; rejection `→ REJECTED`. BONAFIDE is the auto-eligible
case: zero human touches.

**Grievance** `SUBMITTED →(System) ASSIGNED →(Faculty) UNDER_REVIEW →(Faculty) RESOLVED`.
Supporting workflow, no side effects.

## Demo hook — not a staff UI

A faculty/HOD/office dashboard is a declared non-goal, so approvals are simulated:

```
POST /sim/requests/{id}/advance?event=APPROVE&actor=FACULTY&note=...
POST /sim/requests/{id}/reject?event=REJECT&actor=FACULTY&reason=...
```

These call the **same** `transition(...)` guard as everything else, which is why they are also
the easiest way to see the guard reject something:

```bash
curl -i -X POST 'http://localhost:8080/sim/requests/{id}/advance?event=APPROVE&actor=STUDENT'
# Guard rejected: DOCUMENT: STUDENT may not fire APPROVE from APPROVAL (allowed actors: OFFICE)
```

The same buttons appear inside each card's *Audit trail*, labelled as a demo hook.

**Residual risk, accepted and not mitigated.** `actor` is an ordinary request parameter and
nothing checks that the caller is entitled to it. Any logged-in student can POST
`actor=FACULTY` (or `HOD`, or `OFFICE`) and approve their own leave, verify their own
internship, or issue their own transcript — the guard only rejects combinations the *matrix*
forbids, never the caller. That is privilege escalation, and it is deliberate: real
authentication and a staff UI are both declared non-goals, so the demo has no other way to
show a Faculty or HOD move. It is safe **only** because this is a prototype with seeded data
and no real identities behind it.

The endpoint must not survive contact with real users. Whoever builds the `identity` module
owns deleting `/sim` — or gating it behind an authenticated role check — in the same change
that introduces real logins.

## Scope

Seven student views: Home, My Requests, Leave, Internship, Documents & Certificates, Academic
(read-only), Grievance.

Not built, by design: admin/faculty/HOD dashboards, finance, real authentication, real payments,
real file storage (a certificate is recorded as a filename reference), notification infrastructure
(the `NOTIFY` effect records intent in the audit trail).

`/verify/{verifyId}` is a public page, not an eighth student view — it is what an employer sees
after scanning a certificate QR. Without it the generated QR would point nowhere.
