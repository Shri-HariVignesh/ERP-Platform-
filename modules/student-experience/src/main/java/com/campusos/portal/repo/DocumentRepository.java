package com.campusos.portal.repo;

import com.campusos.portal.domain.DocumentArtifact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface DocumentRepository extends Repository<DocumentArtifact, Long> {

    List<DocumentArtifact> findByTenantIdAndStudentIdOrderByIssuedAtDesc(String tenantId, String studentId);

    Optional<DocumentArtifact> findByIdAndTenantIdAndStudentId(Long id, String tenantId, String studentId);

    /** Serial sequence is student-scoped, so the count stays inside the scope rule. */
    long countByTenantIdAndStudentId(String tenantId, String studentId);

    DocumentArtifact save(DocumentArtifact d);
}
