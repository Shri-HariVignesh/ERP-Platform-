package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A staff identity. The tenantId is the scope root exactly as it is for a Student, and
 * `department` is the second dimension for an HOD. Tenant-wide staff (INSTITUTION, OFFICE)
 * carry a null department.
 *
 * SECURITY: passwordHash is BCrypt. Nothing outside StaffUserDetailsService reads it, and
 * it never reaches a model attribute or a template.
 */
@Entity
@Table(name = "staff_users",
       indexes = {@Index(columnList = "tenantId"), @Index(columnList = "username", unique = true)})
public class StaffUser {

    @Id
    public String id;

    @Column(nullable = false)
    public String tenantId;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(nullable = false)
    public String passwordHash;

    public String name;
    public String email;

    /** null = tenant-wide staff (INSTITUTION / OFFICE). Set for FACULTY and HOD. */
    public String department;

    public boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_user_roles", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    public Set<StaffRole> roles = new LinkedHashSet<>();

    public StaffUser() {}
}
