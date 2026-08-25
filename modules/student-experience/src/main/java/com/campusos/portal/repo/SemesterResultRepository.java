package com.campusos.portal.repo;

import com.campusos.portal.domain.SemesterResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface SemesterResultRepository extends Repository<SemesterResult, Long> {
    List<SemesterResult> findByTenantIdAndStudentIdOrderBySemesterAsc(String tenantId, String studentId);
    /** The upsert key: one published aggregate per student per semester. */
    Optional<SemesterResult> findByTenantIdAndStudentIdAndSemester(
            String tenantId, String studentId, int semester);

    SemesterResult save(SemesterResult r);
}
