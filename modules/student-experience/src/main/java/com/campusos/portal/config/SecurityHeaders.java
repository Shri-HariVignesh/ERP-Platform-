package com.campusos.portal.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Baseline response headers, for EVERY response — including the ones Spring Security writes
 * itself and never passes down the chain.
 *
 * ORDER IS LOAD-BEARING. This filter must run BEFORE springSecurityFilterChain (order -100).
 * A redirect to /login or a 403 is produced inside that chain, which then does not call
 * chain.doFilter, so a filter ordered after it never sees the response — and every refusal
 * would go out with no CSP and no X-Frame-Options. Setting the headers on the way IN means
 * they are already on the response whoever ends up writing it.
 *
 * Spring Security's own header writers stay disabled (SecurityConfig): one writer, one set
 * of assertions.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeaders implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse r = (HttpServletResponse) res;
        r.setHeader("X-Content-Type-Options", "nosniff");
        r.setHeader("X-Frame-Options", "DENY");
        r.setHeader("Referrer-Policy", "no-referrer");
        // Inline styles are not used; scripts are not used at all. Nothing is loaded off-origin.
        r.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'none'; object-src 'none'; "
                        + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
        chain.doFilter(req, res);
    }
}
