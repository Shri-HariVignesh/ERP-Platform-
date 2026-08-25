package com.campusos.portal.repo;

import com.campusos.portal.domain.Request;
import com.campusos.portal.domain.RequestState;
import com.campusos.portal.domain.RequestType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RequestRepository extends Repository<Request, String> {

    Optional<Request> findByIdAndTenantIdAndStudentId(String id, String tenantId, String studentId);

    List<Request> findByTenantIdAndStudentIdOrderByCreatedAtDesc(String tenantId, String studentId);

    List<Request> findByTenantIdAndStudentIdAndTypeOrderByCreatedAtDesc(
            String tenantId, String studentId, RequestType type);

    /**
     * THE STAFF INBOX. The studentIds are resolved from the staff member's own scope BEFORE
     * this runs, so the query is bounded by the same two dimensions as every other finder —
     * it just carries a set of students rather than one. The states come from InboxStates,
     * which derives them from TransitionMatrix so the inbox can never drift from the matrix.
     */
    List<Request> findByTenantIdAndStudentIdInAndStateInOrderByCreatedAtDesc(
            String tenantId, Collection<String> studentIds, Collection<RequestState> states);

    /**
     * One request, bounded by the staff member's OWN roster. A request belonging to a student
     * outside that roster returns empty — which is how a cross-tenant or wrong-department id
     * is refused without the caller ever learning whether it exists.
     */
    Optional<Request> findByIdAndTenantIdAndStudentIdIn(
            String id, String tenantId, Collection<String> studentIds);

    /** A staff member reading one request they have already been scope-checked for. */
    List<Request> findByTenantIdAndStudentIdInAndTypeOrderByCreatedAtDesc(
            String tenantId, Collection<String> studentIds, RequestType type);

    Request save(Request r);
}
