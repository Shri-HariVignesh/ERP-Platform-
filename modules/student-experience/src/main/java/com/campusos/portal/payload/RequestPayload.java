package com.campusos.portal.payload;

import com.campusos.portal.domain.RequestType;
import java.util.List;

/**
 * The discriminated union, keyed by RequestType.
 * Home and My Requests call title()/subtitle()/artifacts() polymorphically —
 * that is why no template contains a per-type branch.
 */
public interface RequestPayload {
    RequestType type();
    String title();
    String subtitle();
    List<Artifact> artifacts();
    /** Throws IllegalArgumentException if the typed payload is not well-formed. */
    void validate();

    /**
     * Who actually holds this request, when the payload knows better than the matrix actor.
     * A grievance is routed to a named desk; a leave is simply with "the class advisor".
     * Null means "use the actor declared on the matrix edge".
     */
    default String handledBy() { return null; }
}
