package com.campusos.portal.repo;

import com.campusos.portal.domain.ExamTerm;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface ExamTermRepository extends Repository<ExamTerm, Long> {
    List<ExamTerm> findByTenantId(String tenantId);
    ExamTerm save(ExamTerm t);
}
