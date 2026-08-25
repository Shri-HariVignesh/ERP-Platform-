package com.campusos.portal.domain;

import jakarta.persistence.*;

/**
 * A student's LOGIN. Auth only.
 *
 * It deliberately carries no name, roll number, programme or department: those live on the
 * Student row this account points at, and duplicating them here would create a second answer
 * to "who is this student" that could drift from the first.
 *
 * studentId is UNIQUE, so one account cannot be aimed at two students and one student cannot
 * grow two logins that diverge. This is StaffUser's shape minus the roles and the department.
 */
@Entity
@Table(name = "student_accounts",
       indexes = {@Index(columnList = "tenantId"),
                  @Index(columnList = "username", unique = true),
                  @Index(columnList = "studentId", unique = true)})
public class StudentAccount {

    @Id
    public String id;

    @Column(nullable = false)
    public String tenantId;

    /** The Student row this login authenticates. The whole of the student's Scope. */
    @Column(nullable = false, unique = true)
    public String studentId;

    @Column(nullable = false, unique = true)
    public String username;

    /** SECURITY: BCrypt. Read only by PortalUserDetailsService; never reaches a model or view. */
    @Column(nullable = false)
    public String passwordHash;

    public boolean active = true;

    public StudentAccount() {}
}
