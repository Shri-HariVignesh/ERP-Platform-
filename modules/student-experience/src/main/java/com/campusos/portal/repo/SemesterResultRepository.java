package com.campusos.portal.repo;

import com.campusos.portal.domain.SemesterResult;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface SemesterResultRepository extends Repository<SemesterResult, Long> {
    List<SemesterResult> findByTenantIdAndStudentIdOrderBySemesterAsc(String tenantId, String studentId);
    SemesterResult save(SemesterResult r);
}
