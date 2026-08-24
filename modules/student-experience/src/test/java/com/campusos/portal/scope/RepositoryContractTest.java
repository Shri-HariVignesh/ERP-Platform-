package com.campusos.portal.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusos.portal.engine.EngineTestBase;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 */
@Tag("security")
class RepositoryContractTest extends EngineTestBase {

    private static final String PKG = "com.campusos.portal.repo";

    /**
     * The exception list from repo/README.txt, asserted below to be exhaustive.
     *   TenantRepository#findById            — the tenant is the scope root
     *   VerificationRepository#findByVerifyId — public QR target; the id is the capability
     *   ExamTermRepository#findByTenantId    — institution-level data, no studentId column exists
     *   StudentRepository#findByIdAndTenantId — the Student's PK IS the studentId, so this call
     *                                           already carries both dimensions
     */
    private static final Set<String> DOCUMENTED_EXCEPTIONS = Set.of(
            "TenantRepository#findById",
            "VerificationRepository#findByVerifyId",
            "ExamTermRepository#findByTenantId",
            "StudentRepository#findByIdAndTenantId");

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
                        "VerificationRepository", "SemesterResultRepository", "ExamTermRepository");
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
    @DisplayName("every query method is tenant+student scoped, except the three documented cases")
    void everyQueryIsScoped() {
        List<String> unscoped = new ArrayList<>();
        for (Class<?> itf : repositoryInterfaces()) {
            for (Method m : itf.getMethods()) {
                String name = m.getName();
                boolean isQuery = name.startsWith("find") || name.startsWith("count")
                        || name.startsWith("exists") || name.startsWith("get");
                if (!isQuery) continue;

                String key = itf.getSimpleName() + "#" + name;
                if (DOCUMENTED_EXCEPTIONS.contains(key)) continue;
                if (!name.contains("TenantIdAndStudentId")) unscoped.add(key);
            }
        }
        assertThat(unscoped)
                .as("query methods that do not carry both tenantId and studentId")
                .isEmpty();
    }

    @Test
    @DisplayName("the documented exception list is exhaustive — no undocumented exception survives")
    void exceptionListIsExhaustive() {
        List<String> actual = new ArrayList<>();
        for (Class<?> itf : repositoryInterfaces()) {
            for (Method m : itf.getMethods()) {
                String name = m.getName();
                if (!name.startsWith("find") && !name.startsWith("count")) continue;
                String key = itf.getSimpleName() + "#" + name;
                if (!name.contains("TenantIdAndStudentId")) actual.add(key);
            }
        }
        assertThat(actual)
                .as("if this fails, either scope the new method or document it in repo/README.txt")
                .containsExactlyInAnyOrderElementsOf(DOCUMENTED_EXCEPTIONS);
    }
}
