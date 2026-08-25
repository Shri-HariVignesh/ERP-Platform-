package com.campusos.portal.repo;

import com.campusos.portal.domain.AcademicAudit;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface AcademicAuditRepository extends Repository<AcademicAudit, Long> {

    List<AcademicAudit> findByTenantIdAndStudentIdOrderByAtDesc(String tenantId, String studentId);

    /** Staff-scoped feed: the writes this cohort of students received. Backs Notifications. */
    List<AcademicAudit> findByTenantIdAndStudentIdInOrderByAtDesc(
            String tenantId, List<String> studentIds);

    AcademicAudit save(AcademicAudit a);
}
