package com.campusos.portal.repo;

import com.campusos.portal.domain.RequestHistory;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface RequestHistoryRepository extends Repository<RequestHistory, Long> {

    List<RequestHistory> findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(
            String requestId, String tenantId, String studentId);

    List<RequestHistory> findByTenantIdAndStudentIdOrderByIdDesc(String tenantId, String studentId);

    RequestHistory save(RequestHistory h);
}
