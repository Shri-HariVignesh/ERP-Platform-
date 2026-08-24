package com.campusos.portal.web;

import com.campusos.portal.engine.IllegalTransitionException;
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
     * SECURITY: the internal exception type is logged, not echoed to the client.
     */
    @ExceptionHandler(IllegalTransitionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String illegal(IllegalTransitionException e) {
        log.warn("IllegalTransitionException: {}", e.getMessage());
        return e.getMessage();
    }
}
