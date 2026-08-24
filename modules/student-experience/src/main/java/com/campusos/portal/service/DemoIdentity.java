package com.campusos.portal.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * No real auth (declared non-goal). The seeder registers identities here and the session
 * holds one. The switcher exists to prove tenant isolation, not to model login.
 */
@Component
public class DemoIdentity {

    public record Identity(String tenantId, String studentId, String label) {}

    private final List<Identity> identities = new ArrayList<>();

    public void register(String tenantId, String studentId, String label) {
        identities.add(new Identity(tenantId, studentId, label));
    }

    public List<Identity> all() { return identities; }

    public Identity primary() { return identities.get(0); }

    public Identity find(String studentId) {
        return identities.stream().filter(i -> i.studentId().equals(studentId))
                .findFirst().orElse(primary());
    }
}
