package com.campusos.portal.payload;

import com.campusos.portal.domain.RequestType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

    /**
     * SECURITY (CWE-248/CWE-20): LocalDate.parse throws DateTimeParseException, which is NOT
     * an IllegalArgumentException, so it sailed straight past the controllers' catch and
     * surfaced as an HTTP 500 with an ERROR-level stack trace in the log — for input as
     * trivial as from=NOTADATE. validate() is the gate every payload passes through, so the
     * conversion belongs here: nothing leaves it as an unchecked type the caller does not
     * handle, and a malformed date becomes an ordinary rejected form field.
     */
    static LocalDate parseDate(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must be a date in yyyy-MM-dd form");
        }
    }
}
