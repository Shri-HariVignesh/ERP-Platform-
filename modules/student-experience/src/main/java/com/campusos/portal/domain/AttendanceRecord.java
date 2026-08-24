package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

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

    public String sourceRequestId;

    public AttendanceRecord() {}

    public AttendanceRecord(String tenantId, String studentId, LocalDate date, Status status) {
        this.tenantId = tenantId; this.studentId = studentId; this.date = date; this.status = status;
    }

    /** SCHEDULED days are future class days — not yet part of the percentage. */
    public boolean counted() { return status != Status.SCHEDULED; }

    public boolean attended() { return status == Status.PRESENT || status == Status.APPROVED_LEAVE; }
}
