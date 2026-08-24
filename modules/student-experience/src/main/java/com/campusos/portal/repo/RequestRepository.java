package com.campusos.portal.repo;

import com.campusos.portal.domain.Request;
import com.campusos.portal.domain.RequestType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RequestRepository extends Repository<Request, String> {

    Optional<Request> findByIdAndTenantIdAndStudentId(String id, String tenantId, String studentId);

    List<Request> findByTenantIdAndStudentIdOrderByCreatedAtDesc(String tenantId, String studentId);

    List<Request> findByTenantIdAndStudentIdAndTypeOrderByCreatedAtDesc(
            String tenantId, String studentId, RequestType type);

    Request save(Request r);
}
