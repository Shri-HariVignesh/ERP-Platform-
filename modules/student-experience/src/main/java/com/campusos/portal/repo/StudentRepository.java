package com.campusos.portal.repo;

import com.campusos.portal.domain.Student;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface StudentRepository extends Repository<Student, String> {

    Optional<Student> findByIdAndTenantId(String id, String tenantId);

    /* ---------------------------- staff-scope rosters ----------------------------
     * A roster is a set of students, so it cannot carry a single studentId. Each of these
     * carries tenantId PLUS the staff-scope dimension that bounds it — the class key for a
     * FACULTY, the department for an HOD, the tenant alone for INSTITUTION/OFFICE.
     * RepositoryContractTest asserts that rule structurally.
     */

    /** FACULTY breadth: exactly one class of one department. */
    List<Student> findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc(
            String tenantId, String department, int semester, String section);

    /** HOD breadth: every class in their own department. */
    List<Student> findByTenantIdAndDepartmentOrderByRollNoAsc(String tenantId, String department);

    /** INSTITUTION / OFFICE breadth: the tenant, which is the scope root. */
    List<Student> findByTenantIdOrderByRollNoAsc(String tenantId);

    Student save(Student s);
}
