package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * THE polymorphic entity. One table for every workflow — never a table per form.
 * `payload` is JSON, produced from a typed DTO per `type` (never a free blob).
 */
@Entity
@Table(name = "requests", indexes = @Index(columnList = "tenantId,studentId,createdAt"))
public class Request {
    @Id
    public String id;

    @Column(nullable = false)
    public String tenantId;

    @Column(nullable = false)
    public String studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RequestState state;

    @Lob
    @Column(nullable = false, length = 8000)
    public String payload;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    public Request() {}
}
