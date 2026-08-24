package com.campusos.portal.repo;

import com.campusos.portal.domain.AcademicRecord;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface AcademicRecordRepository extends Repository<AcademicRecord, Long> {

    List<AcademicRecord> findByTenantIdAndStudentIdOrderByRecordedAtDesc(String tenantId, String studentId);

    List<AcademicRecord> findByTenantIdAndStudentIdAndSourceRequestId(
            String tenantId, String studentId, String sourceRequestId);

    AcademicRecord save(AcademicRecord a);
}
