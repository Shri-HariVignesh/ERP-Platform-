package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "documents", indexes = @Index(columnList = "tenantId,studentId"))
public class DocumentArtifact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String tenantId;
    public String studentId;

    @Column(unique = true)
    public String serialNo;

    @Enumerated(EnumType.STRING)
    public DocType docType;

    public String title;

    @Lob
    @Column(length = 8000)
    public String html;

    public String verifyId;
    public String sourceRequestId;
    public Instant issuedAt = Instant.now();

    public DocumentArtifact() {}
}
