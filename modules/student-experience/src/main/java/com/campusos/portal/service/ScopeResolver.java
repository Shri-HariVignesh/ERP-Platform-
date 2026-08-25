package com.campusos.portal.service;

import com.campusos.portal.domain.StudentAccount;
import com.campusos.portal.repo.StudentAccountRepository;
import com.campusos.portal.repo.StudentRepository;
import com.campusos.portal.security.StudentPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * PRINCIPAL -> SCOPE, for students. The exact counterpart of StaffScopeResolver.
 *
 * This class used to read a studentId out of the HTTP session, which /switch wrote from a
 * request parameter — the student equivalent of the retired /sim hook, and the last place in
 * the application where the client chose whose data it was looking at. There is no longer any
 * input to this method other than the authenticated principal.
 */
@Component
public class ScopeResolver {

    private final StudentAccountRepository accounts;
    private final StudentRepository students;

    public ScopeResolver(StudentAccountRepository accounts, StudentRepository students) {
        this.accounts = accounts;
        this.students = students;
    }

    public Scope current(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof StudentPrincipal p)) {
            throw new StudentAccessException("not authenticated as a student");
        }
        // Re-read per request, exactly as the staff side does: deactivating an account takes
        // effect on the next click rather than at next login.
        StudentAccount a = accounts.findByIdAndTenantId(p.accountId(), p.tenantId())
                .orElseThrow(() -> new StudentAccessException("account no longer valid"));
        if (!a.active) throw new StudentAccessException("account is inactive");

        students.findByIdAndTenantId(a.studentId, a.tenantId)
                .orElseThrow(() -> new StudentAccessException("student record not in scope"));

        return new Scope(a.tenantId, a.studentId);
    }
}
