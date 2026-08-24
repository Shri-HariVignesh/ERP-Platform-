package com.campusos.portal.domain;

/** One enum for one table. The matrix is keyed by (type, state), so states may be shared. */
public enum RequestState {
    // shared
    SUBMITTED, REJECTED,
    // LEAVE
    FACULTY_PENDING, HOD_PENDING, ATTENDANCE_MUTATED, NOTIFIED,
    // INTERNSHIP
    FACULTY_VERIFICATION, INSTITUTION_APPROVAL, ACADEMIC_RECORD_MUTATED, VERIFICATION_ID_GENERATED, RETURNED,
    // DOCUMENT
    APPROVAL, DOCUMENT_GENERATED,
    // GRIEVANCE
    ASSIGNED, UNDER_REVIEW, RESOLVED
}
