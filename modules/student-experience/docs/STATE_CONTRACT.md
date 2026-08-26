# Frozen state contract — Student Experience Portal

Runtime: Node.js + Express + EJS + better-sqlite3 (in-memory by default) + express-session.
Scope: 7 student views + an 8-view faculty portal. Real payments, real file storage, and
external notification delivery are declared non-goals.

This describes what [`src/engine/TransitionMatrix.js`](../src/engine/TransitionMatrix.js)
actually enforces — the matrix is the single source of truth; this file is a readable summary
of it, not the other way around. If the two ever disagree, the code wins.

## 0. Base entity (one table, never one per form)

```
Request { id, tenantId, studentId, type, state, payload(JSON), createdAt, updatedAt }
RequestHistory { requestId, fromState, toState, actor, at, note, effects, effectLog }
```

* **One table.** `type` ∈ `LEAVE | INTERNSHIP | DOCUMENT | GRIEVANCE`. Adding a workflow means
  adding a `WorkflowSpec` entry to `TransitionMatrix` and a payload class — no schema change, no
  new route, no new template.
* **Typed payloads.** `payload` is produced from a class per type (`LeavePayload`,
  `InternshipPayload`, `DocumentPayload`, `GrievancePayload`) via `PayloadCodec`, which
  validates before it writes. Never a free blob.
* **One guard.** `RequestStateMachine.transition(scope, requestId, event, actor, note)` is the
  only code in the application that changes a state. It resolves `(type, state, event, actor,
  guard)` against `TransitionMatrix`, throws `IllegalTransitionException` on anything else,
  appends a `RequestHistory` row, then fires the edge's declared side effects — in one SQLite
  transaction.
* **Automation is an actor.** After every move, `autopilot()` keeps firing `SYSTEM` edges whose
  guards pass, up to 12 times. That is what makes a bonafide certificate arrive already issued,
  and what turns an HOD approval into an attendance write with no further click.
* **Scope.** Every repository module in [`src/repo/`](../src/repo) exports only scoped query
  functions — no generic `findAll`/`findById` exists to be called by accident. Every declared
  function is keyed on `tenantId` **and** `studentId`. See
  [`REPOSITORY_SCOPE_RULES.md`](REPOSITORY_SCOPE_RULES.md) for the four documented exceptions.

## 1. LEAVE

States: `SUBMITTED → FACULTY_PENDING → HOD_PENDING → ATTENDANCE_MUTATED → NOTIFIED`;
`REJECTED` from either human state.

| State | Actor | Event | Guard | → | Side effects |
|---|---|---|---|---|---|
| — | Student | (create) | `to >= from`, span ≤ 30 days | SUBMITTED | — |
| SUBMITTED | System | AUTO_VALIDATE | — | FACULTY_PENDING | `VALIDATE_LEAVE` |
| FACULTY_PENDING | Faculty | APPROVE | — | HOD_PENDING | `NOTIFY` |
| FACULTY_PENDING | Faculty | REJECT | note required | REJECTED | `NOTIFY_REJECTION` |
| HOD_PENDING | HOD | APPROVE | — | ATTENDANCE_MUTATED | — |
| HOD_PENDING | HOD | REJECT | note required | REJECTED | `NOTIFY_REJECTION` |
| ATTENDANCE_MUTATED | System | APPLY | — | NOTIFIED | `MUTATE_ATTENDANCE`, `NOTIFY` |

Payload: `leaveType, from, to, reason`, plus system-derived `dayCount, balanceAtSubmit,
attendanceBefore, attendanceAfter, datesMutated, validation`.

## 2. INTERNSHIP

States: `SUBMITTED → FACULTY_VERIFICATION → INSTITUTION_APPROVAL → ACADEMIC_RECORD_MUTATED →
VERIFICATION_ID_GENERATED`; return path `RETURNED → (student RESUBMIT) → SUBMITTED`; rejection
`→ REJECTED`. Only ONE human touch (faculty verification) in the whole happy path.

| State | Actor | Event | → | Side effects |
|---|---|---|---|---|
| — | Student | (create) | SUBMITTED | — |
| SUBMITTED | System | AUTO_CHECK | FACULTY_VERIFICATION | `CHECK_CERTIFICATE` |
| FACULTY_VERIFICATION | Faculty | VERIFY | INSTITUTION_APPROVAL | — |
| FACULTY_VERIFICATION | Faculty | RETURN (note required) | RETURNED | `NOTIFY_REJECTION` |
| INSTITUTION_APPROVAL | Institution | APPROVE | ACADEMIC_RECORD_MUTATED | — |
| INSTITUTION_APPROVAL | Institution | REJECT (note required) | REJECTED | `NOTIFY_REJECTION` |
| ACADEMIC_RECORD_MUTATED | System | WRITE_RECORD | VERIFICATION_ID_GENERATED | `WRITE_ACADEMIC_RECORD`, `GENERATE_VERIFICATION_ID`, `PUBLISH_CERT_TO_DOCUMENTS`, `NOTIFY` |
| RETURNED | Student | RESUBMIT (takes a corrected filename) | SUBMITTED | payload's `certificateRef` replaced |

Payload: `company, role, from, to, details, certificateRef{filename,mime,sizeKb}|null`, plus
system-derived `weeks, certificateCheck, credits, verifyId, documentSerial, returnCount`.

## 3. DOCUMENT & CERTIFICATE

States: `SUBMITTED → APPROVAL → DOCUMENT_GENERATED`, or straight `SUBMITTED → DOCUMENT_GENERATED`
when auto-eligible; rejection `→ REJECTED`. The office can only issue — it can never reject.

`BONAFIDE` for an **active student** is the sole auto-eligible case — the "Digital Razor": the
one question ("is this an active student?") the system can answer itself, so it does, with zero
human touches. Every other document type, and BONAFIDE for an inactive student, routes to the
office.

| State | Actor | Event | Guard | → | Side effects |
|---|---|---|---|---|---|
| — | Student | (create) | docType in the requestable set, 1–3 copies | SUBMITTED | — |
| SUBMITTED | System | AUTO_ELIGIBILITY | `docType==BONAFIDE && student.active` | DOCUMENT_GENERATED | `RUN_ELIGIBILITY`, `GENERATE_DOCUMENT` |
| SUBMITTED | System | AUTO_ELIGIBILITY | else | APPROVAL | `RUN_ELIGIBILITY` |
| APPROVAL | Office | APPROVE | — | DOCUMENT_GENERATED | `GENERATE_DOCUMENT` |
| APPROVAL | Office | REJECT (note required) | — | REJECTED | `NOTIFY_REJECTION` |

Payload: `docType, purpose, copies`, plus system-derived `autoEligible, eligibilityReason,
serialNo, verifyId, documentId`.

## 4. GRIEVANCE (supporting workflow, minimal contract)

States: `SUBMITTED → ASSIGNED → UNDER_REVIEW → RESOLVED`.

| State | Actor | Event | → | Side effects |
|---|---|---|---|---|
| — | Student | (create) | SUBMITTED | — |
| SUBMITTED | System | AUTO_ASSIGN | ASSIGNED | — |
| ASSIGNED | Faculty | START_REVIEW | UNDER_REVIEW | — |
| UNDER_REVIEW | Faculty | RESOLVE (note required) | RESOLVED | `NOTIFY` |

Payload: `category, subject, description, anonymous`. The desk a grievance is shown against
(`DisplayLabels.desk(category)`) is a *display* mapping, not routing — `AUTO_ASSIGN` declares no
side effects, so nothing in the engine actually dispatches to a named desk.

## 5. Normalized read model (one call feeds Home AND My Requests AND the faculty inbox)

```
RequestCard {
  id, type, typeLabel, title, subtitle, state, stateLabel, badgeTone, headline
  steps: [{ label, status: 'done'|'current'|'pending'|'skipped'|'failed' }]
  studentAction: { label, event, tone, requiresNote, inputLabel } | null
  artifacts: [{ kind, label, value, href? }]
  createdAt, updatedAt
  trail: [{ transition, actor, note, effects, proof, at }]
}
```

Built by [`src/service/PresentationService.js`](../src/service/PresentationService.js) from
`WorkflowSpec` metadata and the payload's own `title()`/`subtitle()`/`artifacts()` — no `if
(type === ...)` anywhere in a view. `skipped` is how an automated bypass (e.g. a Leave that
never touched Faculty/HOD) becomes visible to the student.
