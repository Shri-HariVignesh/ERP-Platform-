package com.campusos.portal.web;

import com.campusos.portal.engine.IllegalTransitionException;
import com.campusos.portal.service.ScopeAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalErrors {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrors.class);

    /**
     * An illegal move never silently passes — it surfaces as a 400.
     *
     * SECURITY (CWE-209): the internal exception MESSAGE is logged, never returned. It used to
     * be the response body, so probing /documents/{id}/download for another tenant's ids
     * answered "document not visible in scope" — engine vocabulary handed to whoever asked.
     * Guard messages elsewhere also name a request's current state and which actors may act on
     * it. The caller learns the move was refused; the operator gets the reason from the log.
     *
     * The exception message itself is unchanged, so the engine-level assertions in ScopingTest
     * still hold — this narrows what crosses the HTTP boundary, nothing else.
     */
    @ExceptionHandler(IllegalTransitionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String illegal(IllegalTransitionException e) {
        log.warn("IllegalTransitionException: {}", e.getMessage());
        return "That request is not available.";
    }

    /**
     * Someone — staff or student — reached outside their scope. 403, and the body says only
     * that: it never reveals whether the thing they asked for exists, which would turn the id
     * into an oracle for another department's, another student's or another tenant's data.
     *
     * One handler for both sides on purpose. If a staff refusal and a student refusal read
     * differently, the difference itself is information.
     */
    @ExceptionHandler(ScopeAccessException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    public String denied(ScopeAccessException e) {
        log.warn("{}: {}", e.getClass().getSimpleName(), e.getMessage());
        return "Not available in your scope.";
    }
}
