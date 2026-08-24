package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "exam_terms")
public class ExamTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String tenantId;
    public String name;
    public LocalDate startDate;
    public LocalDate endDate;
    public boolean hallTicketReleased;

    public ExamTerm() {}
}
