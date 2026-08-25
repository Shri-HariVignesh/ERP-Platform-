package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * The audit trail for FACULTY ACADEMIC AUTHORING — the direct-write category that does NOT
 * go through the request engine and therefore has no RequestHistory of its own.
 *
 * AcademicWriteService writes exactly one of these per affected student per write, inside the
 * same transaction as the mutation. An attendance or marks change with no audit row is a bug.
 */
@Entity
@Table(name = "academic_audit",
       indexes = {@Index(columnList = "tenantId,studentId"), @Index(columnList = "tenantId,staffId")})
public class AcademicAudit {

    public enum Kind { ATTENDANCE, MARKS_DRAFT, MARKS_FINALIZED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String tenantId;

    @Column(nullable = false)
    public String studentId;

    /** WHO authored it — the authenticated staff id, never anything the client supplied. */
    @Column(nullable = false)
    public String staffId;

    public String staffName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Kind kind;

    /** The teaching assignment that authorized this write. */
    public String subjectCode;

    @Column(length = 1000)
    public String detail;

    @Column(nullable = false)
    public Instant at = Instant.now();

    public AcademicAudit() {}

    public AcademicAudit(String tenantId, String studentId, String staffId, String staffName,
                         Kind kind, String subjectCode, String detail) {
        this.tenantId = tenantId; this.studentId = studentId; this.staffId = staffId;
        this.staffName = staffName; this.kind = kind; this.subjectCode = subjectCode;
        this.detail = detail;
    }
}
