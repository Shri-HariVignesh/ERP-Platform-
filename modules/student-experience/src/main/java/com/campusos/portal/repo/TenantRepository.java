package com.campusos.portal.repo;

import com.campusos.portal.domain.Tenant;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** The tenant is the scope root — see repo/README.txt. */
public interface TenantRepository extends Repository<Tenant, String> {
    Optional<Tenant> findById(String id);
    Tenant save(Tenant t);
}
