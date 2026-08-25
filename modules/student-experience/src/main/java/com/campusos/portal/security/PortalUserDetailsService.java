package com.campusos.portal.security;

import com.campusos.portal.repo.StaffUserRepository;
import com.campusos.portal.repo.StudentAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * ONE login form, two kinds of account. Replaces StaffUserDetailsService — a second security
 * stack for students would mean two places where "who is this" is decided, and they would
 * eventually disagree.
 *
 * Staff is resolved first, so a username present in BOTH tables would silently shadow the
 * student. Rather than trust the seeder to avoid that, PortalLoginTest asserts the two
 * username sets are disjoint; that assertion is the only thing that makes this order safe.
 */
@Service
public class PortalUserDetailsService implements UserDetailsService {

    private final StaffUserRepository staff;
    private final StudentAccountRepository students;

    public PortalUserDetailsService(StaffUserRepository staff, StudentAccountRepository students) {
        this.staff = staff;
        this.students = students;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return staff.findByUsername(username)
                .map(u -> (UserDetails) new StaffPrincipal(u))
                .or(() -> students.findByUsername(username).map(StudentPrincipal::new))
                // SECURITY: one opaque message for every failure mode. "No such user" and
                // "wrong password" must stay indistinguishable, and this must not leak WHICH
                // kind of account was or was not found either.
                .orElseThrow(() -> new UsernameNotFoundException("bad credentials"));
    }
}
