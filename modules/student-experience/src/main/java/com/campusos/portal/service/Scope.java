package com.campusos.portal.service;

/** Both ids or nothing. Every service and repository call carries this. */
public record Scope(String tenantId, String studentId) {
    public Scope {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalStateException("tenantId missing — query refused");
        if (studentId == null || studentId.isBlank())
            throw new IllegalStateException("studentId missing — query refused");
    }
}
