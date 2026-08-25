package com.campusos.portal.config;

import com.campusos.portal.security.StaffUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Form login, BCrypt, session, CSRF.
 *
 * SCOPE OF THIS CHANGE: /faculty/** becomes authenticated. Everything the student side already
 * served stays exactly as reachable as it was — the student experience is a declared
 * non-regression, and /verify stays public because an employer scanning a QR has no login.
 *
 * CSRF is ON for every POST, including the student forms. Thymeleaf's th:action injects the
 * token automatically via Spring Security's RequestDataValueProcessor, so no student template
 * changed for it.
 *
 * Response headers stay with SecurityHeaders (one source of truth, already regression-tested),
 * so Spring Security's own header writers are switched off rather than doubling up.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(StaffUserDetailsService users,
                                                            PasswordEncoder encoder) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider(users);
        p.setPasswordEncoder(encoder);
        // SECURITY: run the hash comparison even for an unknown username, so response timing
        // does not turn the login form into a staff-directory oracle.
        p.setHideUserNotFoundExceptions(true);
        return p;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider provider)
            throws Exception {
        http
            .authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth
                    // the staff module — the only thing this change locks down
                    .requestMatchers("/faculty/**").authenticated()
                    // public: the QR target, the login form, static assets, errors
                    .requestMatchers("/verify/**", "/login", "/css/**", "/error").permitAll()
                    // the student prototype, unchanged
                    .anyRequest().permitAll())
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .defaultSuccessUrl("/faculty", true)
                    .failureUrl("/login?error")
                    .permitAll())
            .logout(out -> out
                    // SECURITY: logout is POST-only (explicit, not just logoutUrl's default),
                    // so a GET to /logout can never be ridden in via CSRF.
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
            .sessionManagement(s -> s
                    // SECURITY: a new session id at login, so a pre-set cookie cannot be
                    // ridden into an authenticated session.
                    .sessionFixation(fix -> fix.newSession()))
            // SecurityHeaders sets all of these; one writer, one set of assertions.
            .headers(h -> h.disable());
        return http.build();
    }
}
