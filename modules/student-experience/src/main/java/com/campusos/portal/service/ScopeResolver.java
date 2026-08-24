package com.campusos.portal.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class ScopeResolver {

    private static final String KEY = "campusos.studentId";

    private final DemoIdentity identities;

    public ScopeResolver(DemoIdentity identities) {
        this.identities = identities;
    }

    public Scope current(HttpSession session) {
        Object v = session.getAttribute(KEY);
        DemoIdentity.Identity id = v == null ? identities.primary() : identities.find((String) v);
        return new Scope(id.tenantId(), id.studentId());
    }

    public void switchTo(HttpSession session, String studentId) {
        session.setAttribute(KEY, identities.find(studentId).studentId());
    }
}
