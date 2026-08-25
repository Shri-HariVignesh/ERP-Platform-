package com.campusos.portal.config;

import com.campusos.portal.security.PortalLoginSuccessHandler;
import com.campusos.portal.security.PortalUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * ONE security stack for the whole portal: one login form, one UserDetailsService, one
 * filter chain. Students and staff differ only in the authority their principal carries and
 * therefore in which half of the route matrix they can reach.
 *
 * THE ROUTE-AUTHORIZATION MATRIX BELOW IS THE WHOLE ACCESS-CONTROL STORY. Read it as one
 * table; anything not in it is refused by the final denyAll().
 *
 * Response headers stay with SecurityHeaders (one writer, one set of assertions).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The four staff roles. A student principal holds none of them, structurally. */
    private static final String[] STAFF_ROLES = {"FACULTY", "HOD", "INSTITUTION", "OFFICE"};

    /** The seven student views, plus the home alias the login handler routes to. */
    private static final String[] STUDENT_VIEWS = {
            "/", "/home", "/requests", "/leave", "/internship",
            "/documents", "/academic", "/grievance"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PortalUserDetailsService users,
                                                            PasswordEncoder encoder) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider(users);
        p.setPasswordEncoder(encoder);
        // SECURITY: run the hash comparison even for an unknown username, so response timing
        // does not turn the login form into an account-directory oracle.
        p.setHideUserNotFoundExceptions(true);
        return p;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider provider,
                                           PortalLoginSuccessHandler success) throws Exception {
        http
            .authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth
                    /* ---------------------------- public ----------------------------
                     * /verify is the QR target: an employer scanning a certificate has no
                     * login and never will. /css carries the only static asset the app has,
                     * and the login and verify pages both need it or they render unstyled.
                     * /error must be permitted or a refusal turns into a second refusal. */
                    .requestMatchers("/verify/**", "/login", "/logout",
                                     "/css/**", "/error", "/favicon.ico").permitAll()

                    /* ----------------------------- staff -----------------------------
                     * Was authenticated(); an authenticated STUDENT would have passed that.
                     * Naming the roles is what stops student auth opening the staff portal. */
                    .requestMatchers("/faculty/**").hasAnyRole(STAFF_ROLES)

                    /* ---------------------------- students ---------------------------- */
                    .requestMatchers(HttpMethod.GET, STUDENT_VIEWS).hasRole("STUDENT")
                    .requestMatchers(HttpMethod.GET, "/documents/*/download").hasRole("STUDENT")
                    .requestMatchers(HttpMethod.POST, "/leave", "/internship", "/documents",
                                     "/grievance", "/actions/*").hasRole("STUDENT")

                    /* ---------------------------- retired ----------------------------
                     * /switch and /sim have NO controller in the default profile. They are
                     * permitted here so they reach the dispatcher and come back as a true 404.
                     *
                     * That is deliberate and it is the stronger answer: under denyAll an
                     * unmapped path returns 403, which tells a prober "this exists, you just
                     * cannot have it". A retired endpoint should be indistinguishable from one
                     * that never existed. There is nothing behind these paths to serve.
                     * (Under the `demo` profile SimController maps again and is reachable —
                     * that profile is a reviewer's tool and was never access-controlled.) */
                    .requestMatchers("/switch", "/sim/**").permitAll()

                    /* --------------------------- deny by default ---------------------------
                     * Was permitAll(). That made every future endpoint public the moment it
                     * was written. Now an unclassified route is refused until someone puts it
                     * in this table on purpose. */
                    .anyRequest().denyAll())

            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(success)
                    .failureUrl("/login?error")
                    .permitAll())
            .logout(out -> out
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
            .sessionManagement(s -> s
                    // SECURITY: a new session id at login, so a pre-set cookie cannot be
                    // ridden into an authenticated session.
                    .sessionFixation(fix -> fix.newSession()))
            .headers(h -> h.disable());
        return http.build();
    }
}
