package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "academic_record", indexes = @Index(columnList = "tenantId,studentId"))
public class AcademicRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String tenantId;
    public String studentId;
    public String kind;
    public String title;
    public String subtitle;
    public int credits;
    public String verifyId;
    public String sourceRequestId;
    public Instant recordedAt = Instant.now();

    public AcademicRecord() {}
}
