# Repository scope rules — Student Experience

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
