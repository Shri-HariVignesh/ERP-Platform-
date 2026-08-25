package com.campusos.portal.security;

import com.campusos.portal.domain.StudentAccount;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated student identity — and the whole of a student's Scope.
 *
 * It carries tenantId and studentId and nothing else that matters: no display name, because
 * the Student row already answers that and one source is better than two. ROLE_STUDENT is the
 * only authority, and a principal is built from exactly one table, so a student principal
 * cannot also hold a staff role — that is structural, not a filter someone could forget.
 */
public class StudentPrincipal implements UserDetails {

    private static final List<GrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));

    private final String accountId;
    private final String tenantId;
    private final String studentId;
    private final String username;
    private final String passwordHash;
    private final boolean active;

    public StudentPrincipal(StudentAccount a) {
        this.accountId = a.id;
        this.tenantId = a.tenantId;
        this.studentId = a.studentId;
        this.username = a.username;
        this.passwordHash = a.passwordHash;
        this.active = a.active;
    }

    public String accountId() { return accountId; }
    public String tenantId() { return tenantId; }
    public String studentId() { return studentId; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return STUDENT; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return active; }
    @Override public boolean isAccountNonLocked() { return active; }
    @Override public boolean isCredentialsNonExpired() { return active; }
    @Override public boolean isEnabled() { return active; }
}
