package com.campusos.portal.repo;

import com.campusos.portal.domain.TeachingAssignment;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface TeachingAssignmentRepository extends Repository<TeachingAssignment, Long> {

    /** Everything this staff member teaches — the root of the academic-write authorization. */
    List<TeachingAssignment> findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc(
            String tenantId, String staffId);

    /**
     * Every subject taught to one class, across all staff. This is the "expected subject set"
     * that gates SGPA recomputation, so a partly-entered semester cannot clobber a published
     * SemesterResult.
     */
    List<TeachingAssignment> findByTenantIdAndDepartmentAndSemesterAndSection(
            String tenantId, String department, int semester, String section);

    TeachingAssignment save(TeachingAssignment t);
}
