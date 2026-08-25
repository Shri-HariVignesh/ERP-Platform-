package com.campusos.portal.repo;

import com.campusos.portal.domain.StaffUser;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface StaffUserRepository extends Repository<StaffUser, String> {

    /**
     * The login lookup. Deliberately not tenant-scoped: authentication is what ESTABLISHES the
     * tenant, so it cannot be expressed in terms of one — exactly the argument that already
     * exempts StudentRepository#findByIdAndTenantId. Usernames are globally unique (unique
     * index), and the resolved tenantId is then carried by every subsequent query.
     */
    Optional<StaffUser> findByUsername(String username);

    Optional<StaffUser> findByIdAndTenantId(String id, String tenantId);

    StaffUser save(StaffUser s);
}
