package com.campusos.portal.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Post-login routing. One form, two destinations: a student lands on their portal, a staff
 * member on theirs. Decided by the PRINCIPAL TYPE, never by anything the form submitted.
 *
 * Deliberately NOT "send them back where they came from": a student bounced off a /faculty
 * URL would be returned to it and meet a 403, which reads as a broken login rather than as
 * the refusal it is. Type routing is always right and always the same.
 */
@Component
public class PortalLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication auth) throws IOException, ServletException {
        response.sendRedirect(homeFor(auth));
    }

    /** STUDENT -> the student portal; every staff role -> the staff portal. */
    public static String homeFor(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof StudentPrincipal ? "/home" : "/faculty";
    }
}
