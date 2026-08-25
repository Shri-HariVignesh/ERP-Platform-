package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One subject's marks for one student in one semester. This is the SOURCE record;
 * SemesterResult stays the published per-semester aggregate the student already reads.
 * Not a parallel copy of it — the aggregate is derived from these rows and from nothing else.
 *
 * Uniqueness is (tenantId, studentId, semester, subjectCode): AcademicWriteService upserts
 * on that key, so re-entering marks edits the row rather than growing a second one.
 */
@Entity
@Table(name = "subject_marks",
       indexes = @Index(columnList = "tenantId,studentId,semester"))
public class SubjectMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String tenantId;

    @Column(nullable = false)
    public String studentId;

    @Column(nullable = false)
    public int semester;

    @Column(nullable = false)
    public String subjectCode;

    public String subjectName;

    /** Out of 40. */
    public int internal;

    /** Out of 60. */
    public int external;

    public int credits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public MarkStatus status = MarkStatus.DRAFT;

    public String enteredByStaffId;

    public Instant updatedAt = Instant.now();

    public SubjectMark() {}

    public int total() { return internal + external; }

    public boolean finalized() { return status == MarkStatus.FINALIZED; }
}
