package com.campusos.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** The QR target. Resolves a verifyId to what the institution actually attested. */
@Entity
@Table(name = "verifications")
public class Verification {
    @Id
    public String verifyId;

    public String tenantId;
    public String studentId;
    public String kind;
    public String subject;

    @Column(length = 1000)
    public String detail;

    public String sourceRequestId;
    public Instant issuedAt = Instant.now();

    public Verification() {}
}
