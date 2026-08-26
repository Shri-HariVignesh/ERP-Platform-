# Module map

## Built

### `student-experience`
The student-facing portal. Leave, Internship, Documents and Grievance are four rows in one
`requests` table moved by one guarded state machine. Seven views plus a public
`/verify/{verifyId}` page for certificate QR targets.

Documents: [README](../modules/student-experience/README.md) ·
[state contract](../modules/student-experience/docs/STATE_CONTRACT.md) ·
[repository scope rules](../modules/student-experience/docs/REPOSITORY_SCOPE_RULES.md)

Declared non-goals of this module — each is a candidate module below, not a gap to patch in
place: staff dashboards, finance, real authentication, real payments, real file storage,
notification infrastructure.

### `student-experience-js`
A JavaScript port of `student-experience` — same engine, same frozen state contract, same
views, same security posture. Node/Express/EJS/better-sqlite3 stand in for Spring
Boot/Thymeleaf/H2; nothing at the behaviour level was changed, added, or removed.

Documents: [README](../modules/student-experience-js/README.md). It shares the Java module's
`docs/STATE_CONTRACT.md` and `docs/SECURITY.md` rather than duplicating them, since the frozen
contract is identical by construction.

## Candidates

None of these are started. Listed so the boundary is drawn before code exists.

| Module | Would own | Depends on |
|---|---|---|
| `staff-experience` | Faculty / HOD / office queues — the human side of every approval `student-experience` currently simulates via its demo hook. | request engine, auth |
| `identity` | Real authentication, roles, the tenant registry. Today's identity picker is a prototype stand-in. | — |
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
