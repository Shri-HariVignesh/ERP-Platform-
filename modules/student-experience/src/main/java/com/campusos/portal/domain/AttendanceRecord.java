package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * ONE ROW PER STUDENT PER CLASS DAY. There is deliberately no subject dimension: attendance
 * is a class-day series, so exactly one percentage exists for a student and every writer —
 * the leave workflow and a faculty member marking a register — edits the SAME row for a date.
 *
 * A per-subject dimension was considered and rejected: mixing a daily series with per-subject
 * rows would make AttendanceMath.pct() average two different things, and the number the
 * student reads would silently stop meaning "share of class days attended".
 */
@Entity
@Table(name = "attendance", indexes = @Index(columnList = "tenantId,studentId"))
public class AttendanceRecord {
    public enum Status { PRESENT, ABSENT, APPROVED_LEAVE, SCHEDULED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String tenantId;
    public String studentId;
    public LocalDate date;

    @Enumerated(EnumType.STRING)
    public Status status;

    /** Set when the leave workflow mutated this row. */
    public String sourceRequestId;

    /** Set when a faculty member marked this row through AcademicWriteService. */
    public String markedByStaffId;

    public AttendanceRecord() {}

    public AttendanceRecord(String tenantId, String studentId, LocalDate date, Status status) {
        this.tenantId = tenantId; this.studentId = studentId; this.date = date; this.status = status;
    }

    /** SCHEDULED days are future class days — not yet part of the percentage. */
    public boolean counted() { return status != Status.SCHEDULED; }

    public boolean attended() { return status == Status.PRESENT || status == Status.APPROVED_LEAVE; }
}
