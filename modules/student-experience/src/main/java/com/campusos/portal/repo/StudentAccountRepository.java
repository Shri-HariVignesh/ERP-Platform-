package com.campusos.portal.repo;

import com.campusos.portal.domain.StudentAccount;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface StudentAccountRepository extends Repository<StudentAccount, String> {

    /**
     * The login lookup. Not tenant-scoped, for the same reason StaffUserRepository#findByUsername
     * is not: authentication is what ESTABLISHES the tenant, so it cannot presuppose one.
     * Usernames carry a unique index and are asserted disjoint from the staff table.
     */
    Optional<StudentAccount> findByUsername(String username);

    /** Carries both dimensions already — the account's primary key IS the account id. */
    Optional<StudentAccount> findByIdAndTenantId(String id, String tenantId);

    /** Fully scoped: tenant + the student this account belongs to. */
    Optional<StudentAccount> findByTenantIdAndStudentId(String tenantId, String studentId);

    StudentAccount save(StudentAccount a);
}
