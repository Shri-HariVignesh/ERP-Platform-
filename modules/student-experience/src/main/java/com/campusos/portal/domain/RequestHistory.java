package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "request_history", indexes = @Index(columnList = "requestId"))
public class RequestHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String requestId;

    public String tenantId;
    public String studentId;

    @Enumerated(EnumType.STRING)
    public RequestState fromState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RequestState toState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Actor actor;

    @Column(nullable = false)
    public Instant at = Instant.now();

    @Column(length = 2000)
    public String note;

    /** Comma-joined SideEffect names declared on the edge. */
    @Column(length = 500)
    public String effects = "";

    /** What those side effects actually mutated — the proof line shown in the UI. */
    @Column(length = 2000)
    public String effectLog = "";

    public RequestHistory() {}
}
