package com.campusos.portal.security;

import com.campusos.portal.domain.StaffRole;
import com.campusos.portal.domain.StaffUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated staff identity. Carries the tenantId and staffId that every downstream
 * query is scoped by — so scope is a property of the SESSION, not of a request parameter.
 *
 * Deliberately holds no TeachingAssignment list: assignments are re-read per request by
 * StaffScopeResolver, so revoking one takes effect on the next click rather than at next login.
 */
public class StaffPrincipal implements UserDetails {

    private final String staffId;
    private final String tenantId;
    private final String username;
    private final String passwordHash;
    private final String name;
    private final String department;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    public StaffPrincipal(StaffUser u) {
        this.staffId = u.id;
        this.tenantId = u.tenantId;
        this.username = u.username;
        this.passwordHash = u.passwordHash;
        this.name = u.name;
        this.department = u.department;
        this.active = u.active;
        this.authorities = u.roles.stream()
                .map(StaffRole::name)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }

    public String staffId() { return staffId; }
    public String tenantId() { return tenantId; }
    public String displayName() { return name; }
    public String department() { return department; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return active; }
    @Override public boolean isAccountNonLocked() { return active; }
    @Override public boolean isCredentialsNonExpired() { return active; }
    @Override public boolean isEnabled() { return active; }
}
