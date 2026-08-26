# Repository scope rules

Every module in this directory exports ONLY scoped query functions — never a generic
`findAll`/`findById`. That is deliberate, exactly as it is in the Java module: by never writing
an unscoped finder, an unscoped read is impossible to call by accident rather than merely
discouraged by convention.

Four deliberate exceptions, each justified (mirrors `repo/README.txt` in the Java module):
  * `tenantRepo.findById` — the tenant IS the scope root; scoping it by itself is meaningless.
  * `verificationRepo.findByVerifyId` — the public QR target. An employer scanning a certificate
    has no tenant/student session; the unguessable verifyId is the capability.
  * `examTermRepo.findByTenantId` — the exam calendar is institution-level data with no student
    dimension at all.
  * `studentRepo.findByIdAndTenantId` — carries both dimensions already: the Student's own
    primary key IS the studentId. This is the lookup that resolves a Scope.

Staff-scoped finders carry `tenantId` PLUS the staff-scope dimension that bounds them — the
class key for FACULTY, the department for HOD, the tenant alone for INSTITUTION/OFFICE. See
`studentRepo.findByTenantIdAndDepartmentAndSemesterAndSection...` and siblings.
