package com.campusos.portal.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "students")
public class Student {
    @Id
    public String id;
    public String tenantId;
    public String rollNo;
    public String name;
    public String email;
    public String program;
    public String department;
    public int semester;
    public String section;
    public int feeDues;
    public boolean active = true;
    public int leaveBalance = 12;
    public String advisorName;
    public String hodName;

    public Student() {}
}
