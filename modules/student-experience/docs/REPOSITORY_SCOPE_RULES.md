# Repository scope rules — Student Experience (JS)

Every module in [`src/repo/`](../src/repo) exports ONLY scoped query functions — never a
generic `findAll`/`findById`. That is deliberate: by never writing an unscoped finder, an
unscoped read is impossible to call by accident rather than merely discouraged by convention.

Four deliberate exceptions, each justified:

* `tenantRepo.findById` — the tenant IS the scope root; scoping it by itself is meaningless.
* `verificationRepo.findByVerifyId` — the public QR target. An employer scanning a certificate
  has no tenant/student session; the unguessable `verifyId` is the capability.
* `examTermRepo.findByTenantId` — the exam calendar is institution-level data with no student
  dimension at all.
* `studentRepo.findByIdAndTenantId` — carries both dimensions already: the student's own
  primary key IS the `studentId`. This is the lookup that resolves a `Scope`, so it cannot be
  expressed in terms of one.

Staff-scoped finders carry `tenantId` PLUS the staff-scope dimension that bounds them — the
class key for FACULTY, the department for HOD, the tenant alone for INSTITUTION/OFFICE:

* `studentRepo.findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc` — FACULTY
  breadth, exactly the class key of a teaching assignment.
* `studentRepo.findByTenantIdAndDepartmentOrderByRollNoAsc` — HOD breadth.
* `teachingAssignmentRepo.findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc` —
  what a staff member personally teaches.
* `teachingAssignmentRepo.findByTenantIdAndDepartmentAndSemesterAndSection` — every subject
  taught to one class. This is the "expected subject set" that gates SGPA recomputation, so a
  half-entered semester cannot overwrite a published result.

Two further exceptions, for the same reason the original four exist:

* `staffUserRepo.findByUsername` — the login lookup. Authentication is what ESTABLISHES a
  tenant, so it cannot be expressed in terms of one.
* `staffUserRepo.findByIdAndTenantId` — carries both dimensions already, exactly the
  `studentRepo` argument above.

Note what is NOT here: the staff inbox. It is
`requestRepo.findByTenantIdAndStudentIdInAndStateInOrderByCreatedAtDesc`, which carries both
original dimensions — the roster is resolved from the authenticated principal first, and only
then does the query run.

One more deliberately NOT here: confidential grievance categories (ragging, sexual harassment,
SC/ST discrimination, equal opportunity, RTI). That restriction is not a repository-scope rule —
tenant+roster is still exactly what bounds the query — it is a SERVICE-layer rule on top,
because it turns on the request's own payload content (`category`), not on the caller's
identity the way every rule above does. See
[`../src/service/GrievanceVisibility.js`](../src/service/GrievanceVisibility.js) and
`STATE_CONTRACT.md`'s GRIEVANCE section for where it actually lives.
