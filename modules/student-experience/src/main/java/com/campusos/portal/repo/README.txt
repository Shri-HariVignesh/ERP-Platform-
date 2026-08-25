Every repository below extends org.springframework.data.repository.Repository, NOT JpaRepository.
That is deliberate: JpaRepository would inherit findAll()/findById()/deleteAll(), which are
unscoped. By extending the bare Repository marker, the ONLY query methods that exist on these
interfaces are the ones declared here — and every one of them is scoped by tenantId + studentId.

Four deliberate exceptions, each justified. RepositoryContractTest asserts this list is
exhaustive, so a fifth cannot appear without a failing test:
  * TenantRepository.findById — the tenant IS the scope root; scoping it by itself is meaningless.
  * VerificationRepository.findByVerifyId — the public QR target. An employer scanning a
    certificate has no tenant/student session; the unguessable verifyId is the capability.
  * ExamTermRepository.findByTenantId — the exam calendar is institution-level data with no
    student dimension at all; there is no studentId column to scope by.
  * StudentRepository.findByIdAndTenantId — carries both dimensions already: the Student's own
    primary key IS the studentId. This is the lookup that resolves a Scope, so it cannot be
    expressed in terms of one. Every call site passes scope.studentId() or the studentId of an
    already scope-verified Request.


-------------------------------------------------------------------------------
FACULTY MODULE — a second scope category, not a relaxation of the first
-------------------------------------------------------------------------------
Every student-scoped finder above is unchanged. The staff side needed queries that bound a
SET of students rather than one, so those get their own rule instead of an exemption:

    a staff-scoped finder must be findByTenantIdAndDepartment... or findByTenantIdAndStaffId...

RepositoryContractTest#staffScopedQueriesCarryAStaffDimension asserts that structurally, and
#staffScopedListHasNoGhosts stops a renamed method from keeping its exemption. The four:
  * StudentRepository.findByTenantIdAndDepartmentAndSemesterAndSection... — FACULTY breadth,
    exactly the class key of a teaching assignment.
  * StudentRepository.findByTenantIdAndDepartment...                      — HOD breadth.
  * TeachingAssignmentRepository.findByTenantIdAndStaffId...              — what I teach.
  * TeachingAssignmentRepository.findByTenantIdAndDepartmentAndSemesterAndSection — every
    subject taught to one class. This is the "expected subject set" that gates SGPA
    recomputation, so a half-entered semester cannot overwrite a published result.

Three further documented exceptions, for the same reason the original four exist:
  * StaffUserRepository.findByUsername — the login lookup. Authentication is what ESTABLISHES
    a tenant, so it cannot be expressed in terms of one; usernames carry a unique index, and
    the resolved tenantId is carried by every query that follows.
  * StaffUserRepository.findByIdAndTenantId — carries both dimensions already: the StaffUser's
    primary key IS the staffId. Exactly the StudentRepository.findByIdAndTenantId argument.
  * StudentRepository.findByTenantIdOrderByRollNoAsc — the INSTITUTION/OFFICE roster. For a
    tenant-wide role the tenant is the WHOLE of the scope, not half of it.

Note what is NOT here: the staff inbox. It is
findByTenantIdAndStudentIdInAndStateIn..., which carries both original dimensions — the
roster is resolved from the authenticated principal first, and only then does the query run.
