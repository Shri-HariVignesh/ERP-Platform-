package com.campusos.portal.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Baseline response headers. No Spring Security in this prototype (real authentication is a
 * declared non-goal), so these are set directly rather than via its header writers.
 */
@Component
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
