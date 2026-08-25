package com.campusos.portal.service;

/**
 * Someone reached outside the scope their identity grants them. One parent for both sides of
 * the portal, so GlobalErrors has ONE handler and both refusals look identical from outside —
 * a staff refusal and a student refusal must not be distinguishable by their response.
 */
public class ScopeAccessException extends RuntimeException {
    public ScopeAccessException(String message) { super(message); }
}
