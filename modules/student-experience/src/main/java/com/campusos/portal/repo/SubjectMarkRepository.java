package com.campusos.portal.repo;

import com.campusos.portal.domain.SubjectMark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface SubjectMarkRepository extends Repository<SubjectMark, Long> {

    List<SubjectMark> findByTenantIdAndStudentIdOrderBySemesterAscSubjectCodeAsc(
            String tenantId, String studentId);

    List<SubjectMark> findByTenantIdAndStudentIdAndSemesterOrderBySubjectCodeAsc(
            String tenantId, String studentId, int semester);

    /** The upsert key: one row per (tenant, student, semester, subject). */
    Optional<SubjectMark> findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
            String tenantId, String studentId, int semester, String subjectCode);

    SubjectMark save(SubjectMark m);
}
