# Module map

## Built

### `student-experience`
The student-facing portal, plus a faculty portal for the approvals it generates. Leave,
Internship, Documents and Grievance are four rows in one `requests` table moved by one guarded
state machine. Seven student views, eight faculty views, plus a public `/verify/{verifyId}`
page for certificate QR targets. Node.js / Express / EJS / better-sqlite3.

Documents: [README](../modules/student-experience/README.md) ·
[state contract](../modules/student-experience/docs/STATE_CONTRACT.md) ·
[repository scope rules](../modules/student-experience/docs/REPOSITORY_SCOPE_RULES.md) ·
[security](../modules/student-experience/docs/SECURITY.md) ·
[threat model](../modules/student-experience/docs/THREAT_MODEL.md)

Declared non-goals of this module — each is a candidate module below, not a gap to patch in
place: finance, real authentication, real payments, real file storage, notification
infrastructure.

## Candidates

None of these are started. Listed so the boundary is drawn before code exists.

| Module | Would own | Depends on |
|---|---|---|
| `identity` | Real authentication, roles, the tenant registry. `student-experience`'s seeded demo accounts are a prototype stand-in. | — |
| `academics` | Programmes, courses, terms, attendance and grade records as a system of record rather than a side effect target. | identity |
| `finance` | Fees, dues, receipts. `student-experience` reads a dues figure today and blocks document issue on it. | identity, academics |
| `admissions` | Applications, offers, enrolment — the step before a student exists. | identity |
| `hostel` | Rooms, allocation, mess. Already a grievance category. | identity |
| `library` | Catalogue, issue/return, dues feeding `finance`. | identity, finance |
| `placements` | Drives, offers. Consumes verified internship records. | student-experience |
| `hr-payroll` | Staff records, payroll. | identity |

## Starting a module

1. Write `docs/STATE_CONTRACT.md` first and freeze it. Every workflow the module can run, with
   guards and side effects named, before any code.
2. Declare the non-goals in the module `README.md` as explicitly as the goals.
3. Follow the two platform non-negotiables in [`ARCHITECTURE.md`](ARCHITECTURE.md): scoped
   repositories, one guarded transition.
