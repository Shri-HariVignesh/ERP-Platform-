package com.campusos.portal.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "semester_results", indexes = @Index(columnList = "tenantId,studentId"))
public class SemesterResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String tenantId;
    public String studentId;
    public int semester;
    public double sgpa;
    public int credits;

    public SemesterResult() {}

    public SemesterResult(String tenantId, String studentId, int semester, double sgpa, int credits) {
        this.tenantId = tenantId; this.studentId = studentId;
        this.semester = semester; this.sgpa = sgpa; this.credits = credits;
    }
}
