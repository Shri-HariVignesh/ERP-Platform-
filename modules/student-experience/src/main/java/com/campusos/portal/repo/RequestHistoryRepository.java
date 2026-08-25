package com.campusos.portal.repo;

import com.campusos.portal.domain.RequestHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface RequestHistoryRepository extends Repository<RequestHistory, Long> {

    List<RequestHistory> findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(
            String requestId, String tenantId, String studentId);

    List<RequestHistory> findByTenantIdAndStudentIdOrderByIdDesc(String tenantId, String studentId);

    /** Staff-scoped activity feed. Backs Notifications and Home's recent activity. */
    List<RequestHistory> findByTenantIdAndStudentIdInOrderByIdDesc(
            String tenantId, Collection<String> studentIds);

    RequestHistory save(RequestHistory h);
}
