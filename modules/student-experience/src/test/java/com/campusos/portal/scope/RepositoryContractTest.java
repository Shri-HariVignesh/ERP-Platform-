package com.campusos.portal.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusos.portal.engine.EngineTestBase;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;
import org.springframework.util.ClassUtils;

/**
 * AREA 2 — the structural half of scoping. Inspects the repository interfaces Spring actually
 * registered, not the source text: if someone swaps a repository to JpaRepository, findAll()
 * and findById() reappear on the API and this fails.
 *
 * EXTENDED FOR THE FACULTY MODULE. The original rule — every query carries tenantId AND
 * studentId — still applies unchanged to every student-scoped finder. But a staff member's
 * roster is a SET of students, so a roster query cannot name one. Rather than exempting those
 * queries, they get a second rule of their own (staffScopedQueriesCarryAStaffDimension), which
 * is asserted just as strictly. Nothing was relaxed; a category was added.
 */
@Tag("security")
class RepositoryContractTest extends EngineTestBase {

    private static final String PKG = "com.campusos.portal.repo";

    /**
     * Queries that carry neither a studentId nor a staff-scope dimension. Each is justified in
     * repo/README.txt, and exceptionListIsExhaustive proves an eighth cannot appear quietly.
     *   TenantRepository#findById              — the tenant is the scope root
     *   VerificationRepository#findByVerifyId  — public QR target; the id is the capability
     *   ExamTermRepository#findByTenantId      — institution-level, no studentId column exists
     *   StudentRepository#findByIdAndTenantId  — the Student's PK IS the studentId
     *   StaffUserRepository#findByUsername     — the login lookup; authentication is what
     *                                            ESTABLISHES a tenant, so it cannot presuppose one
     *   StaffUserRepository#findByIdAndTenantId — the StaffUser's PK IS the staffId
     *   StudentRepository#findByTenantIdOrderByRollNoAsc — the INSTITUTION/OFFICE roster. Their
     *                                            breadth IS the tenant, so the tenant is the
     *                                            whole of the scope, not half of it.
     */
    private static final Set<String> DOCUMENTED_EXCEPTIONS = Set.of(
            "TenantRepository#findById",
            "VerificationRepository#findByVerifyId",
            "ExamTermRepository#findByTenantId",
            "StudentRepository#findByIdAndTenantId",
            "StaffUserRepository#findByUsername",
            "StaffUserRepository#findByIdAndTenantId",
            "StudentRepository#findByTenantIdOrderByRollNoAsc");

    /**
     * STAFF-SCOPED queries. These bound a set of students by the dimension that bounds the
     * staff member's authority — the class key for a FACULTY, the department for an HOD, the
     * staff id for a teaching assignment. Held to STAFF_DIMENSION below.
     */
    private static final Set<String> STAFF_SCOPED = Set.of(
            "StudentRepository#findByTenantIdAndDepartmentAndSemesterAndSectionOrderByRollNoAsc",
            "StudentRepository#findByTenantIdAndDepartmentOrderByRollNoAsc",
            "TeachingAssignmentRepository#findByTenantIdAndStaffIdOrderBySemesterAscSectionAscSubjectCodeAsc",
            "TeachingAssignmentRepository#findByTenantIdAndDepartmentAndSemesterAndSection");

    /** A staff-scoped query must start from the tenant AND name a real scope dimension. */
    private static final Pattern STAFF_DIMENSION =
            Pattern.compile("findByTenantIdAnd(Department|StaffId)");

    @Autowired ApplicationContext context;

    private List<Class<?>> repositoryInterfaces() {
        List<Class<?>> out = new ArrayList<>();
        for (String name : context.getBeanNamesForType(Repository.class)) {
            Object bean = context.getBean(name);
            for (Class<?> itf : ClassUtils.getAllInterfacesForClass(bean.getClass())) {
                if (itf.getName().startsWith(PKG)) out.add(itf);
            }
        }
        return out;
    }

    @Test
    @DisplayName("every repository is registered, and this test is actually looking at them")
    void repositoriesAreDiscovered() {
        assertThat(repositoryInterfaces())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder(
                        "TenantRepository", "StudentRepository", "RequestRepository",
                        "RequestHistoryRepository", "AttendanceRepository",
                        "AcademicRecordRepository", "DocumentRepository",
                        "VerificationRepository", "SemesterResultRepository", "ExamTermRepository",
                        // the faculty module
                        "StaffUserRepository", "TeachingAssignmentRepository",
                        "SubjectMarkRepository", "AcademicAuditRepository");
    }

    @Test
    @DisplayName("no repository inherits the unscoped CRUD surface")
    void noRepositoryExtendsCrudRepository() {
        for (Class<?> itf : repositoryInterfaces()) {
            assertThat(CrudRepository.class.isAssignableFrom(itf))
                    .as("%s must not extend CrudRepository/JpaRepository", itf.getSimpleName())
                    .isFalse();
            assertThat(PagingAndSortingRepository.class.isAssignableFrom(itf))
                    .as("%s must not extend PagingAndSortingRepository", itf.getSimpleName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("findAll() exists nowhere, on any repository")
    void noFindAllAnywhere() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> itf : repositoryInterfaces()) {
            for (Method m : itf.getMethods()) {
                if (m.getName().equals("findAll") || m.getName().equals("findAllById")) {
                    offenders.add(itf.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("every query method is tenant+student scoped, except the documented cases")
    void everyQueryIsScoped() {
        List<String> unscoped = new ArrayList<>();
        for (String key : allQueryKeys(true)) {
            if (DOCUMENTED_EXCEPTIONS.contains(key) || STAFF_SCOPED.contains(key)) continue;
            if (!key.contains("TenantIdAndStudentId")) unscoped.add(key);
        }
        assertThat(unscoped)
                .as("query methods that do not carry both tenantId and studentId")
                .isEmpty();
    }

    @Test
    @DisplayName("the exception list is exhaustive — no undocumented exception survives")
    void exceptionListIsExhaustive() {
        List<String> actual = new ArrayList<>();
        for (String key : allQueryKeys(false)) {
            if (!key.contains("TenantIdAndStudentId")) actual.add(key);
        }
        Set<String> allowed = new LinkedHashSet<>(DOCUMENTED_EXCEPTIONS);
        allowed.addAll(STAFF_SCOPED);
        assertThat(actual)
                .as("if this fails, either scope the new method, add it to STAFF_SCOPED, "
                        + "or document it in repo/README.txt")
                .containsExactlyInAnyOrderElementsOf(allowed);
    }

    /* ------------------------- the faculty module's own rule ------------------------- */

    @Test
    @DisplayName("every staff-scoped query starts at the tenant and names a real scope dimension")
    void staffScopedQueriesCarryAStaffDimension() {
        List<String> weak = new ArrayList<>();
        for (String key : STAFF_SCOPED) {
            String method = key.substring(key.indexOf('#') + 1);
            if (!STAFF_DIMENSION.matcher(method).find()) weak.add(key);
        }
        assertThat(weak)
                .as("a staff-scoped finder must be findByTenantIdAndDepartment... or "
                        + "findByTenantIdAndStaffId... — the tenant alone is not a staff scope")
                .isEmpty();
    }

    @Test
    @DisplayName("every method named in STAFF_SCOPED actually exists on a repository")
    void staffScopedListHasNoGhosts() {
        Set<String> real = new LinkedHashSet<>(allQueryKeys(false));
        assertThat(real)
                .as("a renamed or deleted finder must not keep its exemption")
                .containsAll(STAFF_SCOPED);
    }

    /**
     * @param includeExistsAndGet the two tests historically scan slightly different verb sets;
     *                            preserved exactly so neither assertion is widened or narrowed.
     */
    private List<String> allQueryKeys(boolean includeExistsAndGet) {
        List<String> out = new ArrayList<>();
        for (Class<?> itf : repositoryInterfaces()) {
            for (Method m : itf.getMethods()) {
                String name = m.getName();
                boolean isQuery = name.startsWith("find") || name.startsWith("count")
                        || (includeExistsAndGet && (name.startsWith("exists") || name.startsWith("get")));
                if (!isQuery) continue;
                out.add(itf.getSimpleName() + "#" + name);
            }
        }
        return out;
    }
}
