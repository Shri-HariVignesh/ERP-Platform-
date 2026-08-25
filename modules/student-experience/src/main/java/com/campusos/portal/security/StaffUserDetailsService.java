package com.campusos.portal.security;

import com.campusos.portal.repo.StaffUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffUserRepository staff;

    public StaffUserDetailsService(StaffUserRepository staff) {
        this.staff = staff;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return staff.findByUsername(username)
                .map(StaffPrincipal::new)
                // SECURITY: the same opaque message for unknown user and wrong password, so the
                // login form is not a staff-directory oracle.
                .orElseThrow(() -> new UsernameNotFoundException("bad credentials"));
    }
}
