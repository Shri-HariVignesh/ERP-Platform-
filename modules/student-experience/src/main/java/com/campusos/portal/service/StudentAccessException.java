package com.campusos.portal.service;

/**
 * A student route was reached without an authenticated student identity behind it.
 *
 * The filter chain should already have refused, so this is defence in depth: it is what makes
 * "Scope is identity-derived" true at the service layer too, not merely at the edge.
 */
public class StudentAccessException extends ScopeAccessException {
    public StudentAccessException(String message) { super(message); }
}
