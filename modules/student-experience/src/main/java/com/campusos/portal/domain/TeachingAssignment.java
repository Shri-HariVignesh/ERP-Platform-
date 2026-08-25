package com.campusos.portal.domain;

import jakarta.persistence.*;

/**
 * "This staff member teaches THIS subject to THIS class." The class key
 * (department, semester, section) joins onto columns Student already has, so the
 * teaching-assignment scope rule needs no change to the student side.
 *
 * This row is the ONLY thing that authorizes an academic write. Role breadth governs which
 * requests land in an inbox; it never grants the right to author attendance or marks.
 */
@Entity
@Table(name = "teaching_assignments",
       indexes = {@Index(columnList = "tenantId,staffId"),
                  @Index(columnList = "tenantId,department,semester,section")})
public class TeachingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String tenantId;

    @Column(nullable = false)
    public String staffId;

    @Column(nullable = false)
    public String department;

    @Column(nullable = false)
    public int semester;

    @Column(nullable = false)
    public String section;

    @Column(nullable = false)
    public String subjectCode;

    public String subjectName;

    public int credits = 3;

    public TeachingAssignment() {}

    public TeachingAssignment(String tenantId, String staffId, String department, int semester,
                              String section, String subjectCode, String subjectName, int credits) {
        this.tenantId = tenantId; this.staffId = staffId; this.department = department;
        this.semester = semester; this.section = section; this.subjectCode = subjectCode;
        this.subjectName = subjectName; this.credits = credits;
    }
}
