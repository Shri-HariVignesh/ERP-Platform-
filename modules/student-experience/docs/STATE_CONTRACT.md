# FROZEN STATE CONTRACT — Student Experience Portal (prototype)

Runtime: Node 24.14 + Express 5 + Drizzle ORM (better-sqlite3, file DB) + React 19 / Vite / TypeScript.
Scope: 7 student views only. Admin/faculty/HOD UI, finance, real auth, real payments, real file
storage, notification infra = NON-GOALS. Approvals are simulated staff-side actions.

## 0. BASE ENTITY (one table, never one per form)

```ts
Request {
  id:         string            // req_<ulid>
  tenant_id:  string            // NEVER optional
  student_id: string            // NEVER optional
  type:       'LEAVE' | 'INTERNSHIP' | 'DOCUMENT' | 'GRIEVANCE'
  state:      string            // union of the per-type enums below
  payload:    Payload           // discriminated union, keyed by `type`
  history:    HistoryEntry[]
  created_at: string
  updated_at: string
}

HistoryEntry {
  from_state: string | null
  to_state:   string
  actor:      'SYSTEM' | 'STUDENT' | 'FACULTY' | 'HOD' | 'INSTITUTION'
  actor_name: string
  at:         string
  note?:      string
}
```

Access rule: the repository exposes no query builder that compiles without BOTH `tenant_id` and
`student_id`. Every read and write goes through `scope(ctx)`; `ctx` is `{tenant_id, student_id}`.

Guard: `transition(request, event, actor) -> Request`. Looks up `MATRIX[type][state][event]`.
If absent, or `actor` mismatches, or the guard predicate fails -> `throw IllegalTransition`.
Side-effects fire inside the same transaction as the state write. No side-effect outside `transition`.

Side-effect vocabulary: `ATTENDANCE_MUTATED`, `ACADEMIC_RECORD_MUTATED`, `VERIFICATION_ID_GENERATED`,
`DOCUMENT_GENERATED`, `SERIAL_ASSIGNED`, `DUES_SNAPSHOT`, `DURATION_COMPUTED`, `ROUTE_DECIDED`.

## 1. LEAVE (Flagship 1)

States: `SUBMITTED` `PENDING_FACULTY` `PENDING_HOD` `APPROVED`(T) `REJECTED`(T)

| STATE | ACTOR | EVENT | GUARD | -> | SIDE_EFFECTS |
|---|---|---|---|---|---|
| — | Student | SUBMIT | to_date >= from_date | SUBMITTED | DURATION_COMPUTED, attendance snapshot |
| SUBMITTED | System | AUTO_EVALUATE | days<=2 AND attendance>=75 | APPROVED | ROUTE_DECIDED(auto), ATTENDANCE_MUTATED |
| SUBMITTED | System | AUTO_EVALUATE | else | PENDING_FACULTY | ROUTE_DECIDED(manual) |
| PENDING_FACULTY | Faculty | APPROVE | days>2 | PENDING_HOD | — |
| PENDING_FACULTY | Faculty | APPROVE | days<=2 | APPROVED | ATTENDANCE_MUTATED |
| PENDING_FACULTY | Faculty | REJECT | note required | REJECTED | — |
| PENDING_HOD | HOD | APPROVE | — | APPROVED | ATTENDANCE_MUTATED |
| PENDING_HOD | HOD | REJECT | note required | REJECTED | — |

Payload: `from_date, to_date, day_count(sys), category('MEDICAL'|'PERSONAL'|'EVENT'), reason,
attendance_at_submit(sys), route_reason(sys)`

Student sees — SUBMITTED: "Checking eligibility…". APPROVED(auto): "Auto-approved. No human approval
needed — 2 days, attendance 84%. Attendance updated." PENDING_FACULTY: "With class advisor. Routed
because attendance is 68% (below 75%)." PENDING_HOD: "Advisor approved. With HOD — leave exceeds
2 days." APPROVED: "Approved. 3 days marked as approved leave; attendance recalculated."
REJECTED: "Rejected by <actor>: <note>." + [Apply Again] (creates a NEW request, not a transition).

## 2. INTERNSHIP (Flagship 2)

States: `SUBMITTED` `PENDING_VERIFICATION` `RETURNED_FOR_CORRECTION` `VERIFIED` `RECORDED`(T)
Rejection path = a RETURN loop, not a dead end. Only ONE human touch in the whole workflow.

| STATE | ACTOR | EVENT | GUARD | -> | SIDE_EFFECTS |
|---|---|---|---|---|---|
| — | Student | SUBMIT | — | SUBMITTED | — |
| SUBMITTED | System | AUTO_EVALUATE | certificate present AND end_date<=today AND weeks>=2 | PENDING_VERIFICATION | DURATION_COMPUTED |
| SUBMITTED | System | AUTO_EVALUATE | else | RETURNED_FOR_CORRECTION | ROUTE_DECIDED(auto-return) |
| PENDING_VERIFICATION | Faculty | VERIFY | — | VERIFIED | — |
| PENDING_VERIFICATION | Faculty | RETURN | note required | RETURNED_FOR_CORRECTION | — |
| RETURNED_FOR_CORRECTION | Student | RESUBMIT | — | SUBMITTED | payload replaced, history appended |
| VERIFIED | System | ISSUE | — | RECORDED | VERIFICATION_ID_GENERATED, ACADEMIC_RECORD_MUTATED |

Payload: `organisation, role, start_date, end_date, weeks(sys), mode('ONSITE'|'REMOTE'|'HYBRID'),
stipend|null, certificate_ref{filename,mime,size}|null (simulated — no real storage),
verification_id(sys)|null, verify_url(sys)|null, credits(sys)|null`

Student sees — SUBMITTED: "Validating…". RETURNED(system): "Returned automatically: certificate
missing / end date in the future. Fix and resubmit." + [Edit & Resubmit]. PENDING_VERIFICATION:
"With <faculty> for certificate verification." RETURNED(faculty): faculty note + [Edit & Resubmit].
VERIFIED: "Verified. Issuing verification ID…". RECORDED: verification ID + QR + "Added to academic
record — 2 credits" + [Copy verify link].

## 3. DOCUMENT & CERTIFICATE (Flagship 3)

States: `SUBMITTED` `PENDING_OFFICE` `ISSUED`(T) `BLOCKED`(T)
Rejection path is SYSTEM-driven (dues). The office can only issue — it can never reject.

| STATE | ACTOR | EVENT | GUARD | -> | SIDE_EFFECTS |
|---|---|---|---|---|---|
| — | Student | SUBMIT | — | SUBMITTED | DUES_SNAPSHOT |
| SUBMITTED | System | AUTO_EVALUATE | dues > 0 | BLOCKED | — |
| SUBMITTED | System | AUTO_EVALUATE | dues==0 AND auto_generable | ISSUED | DOCUMENT_GENERATED, SERIAL_ASSIGNED, VERIFICATION_ID_GENERATED |
| SUBMITTED | System | AUTO_EVALUATE | dues==0 AND !auto_generable | PENDING_OFFICE | ROUTE_DECIDED |
| PENDING_OFFICE | Institution | ISSUE | — | ISSUED | DOCUMENT_GENERATED, SERIAL_ASSIGNED, VERIFICATION_ID_GENERATED |

`auto_generable`: BONAFIDE, ATTENDANCE_CERTIFICATE, FEE_RECEIPT, HALL_TICKET = true.
TRANSCRIPT, CONDUCT_CERTIFICATE = false (needs attestation).

Payload: `doc_type, purpose, copies(1..3), auto_generable(sys), dues_at_submit(sys),
serial_no(sys)|null, verification_id(sys)|null, issued_at(sys)|null, document_html(sys)|null`

Student sees — ISSUED instantly: "Issued in 2 seconds — Bonafide Certificate · BON/2026/00142"
+ [View] [Verify link]. BLOCKED: "Cannot issue — ₹12,500 outstanding. Clear dues and request again."
PENDING_OFFICE: "Transcripts require office attestation. With Examination Office."

## 4. GRIEVANCE (view 7 — not a flagship, minimal contract)

States: `SUBMITTED` `PENDING_DEPARTMENT` `ESCALATED` `RESOLVED`(T) `CLOSED_NO_ACTION`(T)

| STATE | ACTOR | EVENT | GUARD | -> | SIDE_EFFECTS |
|---|---|---|---|---|---|
| — | Student | SUBMIT | — | SUBMITTED | — |
| SUBMITTED | System | AUTO_ROUTE | — | PENDING_DEPARTMENT | ROUTE_DECIDED(by category), sla_due_at set |
| PENDING_DEPARTMENT | System | SLA_BREACH | now > sla_due_at | ESCALATED | ROUTE_DECIDED(escalate to HOD) |
| PENDING_DEPARTMENT / ESCALATED | Faculty\|HOD | RESOLVE | note required | RESOLVED | — |
| PENDING_DEPARTMENT / ESCALATED | Faculty\|HOD | DISMISS | note required | CLOSED_NO_ACTION | — |

Payload: `category('ACADEMIC'|'HOSTEL'|'EXAM'|'FEES'|'OTHER'), subject, description,
anonymous:boolean, routed_to(sys), sla_due_at(sys)`

## 5. NORMALIZED READ MODEL (one call feeds Home AND My Requests)

```ts
RequestCard {
  id, type, title, subtitle, state
  state_label: string
  badge_tone: 'pending' | 'action' | 'success' | 'danger'
  progress: { steps: { key, label, status: 'done'|'current'|'pending'|'skipped'|'failed' }[] }
  student_action: { label, event } | null      // the ONLY source of action buttons
  artifacts: { kind, label, value, href? }[]   // verification id, serial, document link
  created_at, updated_at
  history: HistoryEntry[]
}
```
UI renders `progress.steps` through one `<Timeline>`, `badge_tone` through one `<StatusBadge>`,
`student_action` through one `<ActionButton>`. Zero `if (type === ...)` anywhere in the UI layer.
`skipped` renders the automated bypass (e.g. Leave that never touched Faculty/HOD) — this is where
the automation is made visible to the student.
