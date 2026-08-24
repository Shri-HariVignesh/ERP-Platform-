package com.campusos.portal.repo;

import com.campusos.portal.domain.Student;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface StudentRepository extends Repository<Student, String> {
    Optional<Student> findByIdAndTenantId(String id, String tenantId);
    Student save(Student s);
}
