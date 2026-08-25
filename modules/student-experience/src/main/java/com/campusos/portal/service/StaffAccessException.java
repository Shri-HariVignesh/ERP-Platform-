package com.campusos.portal.service;

/**
 * A staff member reached for something outside their scope: another tenant, another
 * department, a class they do not teach, or an event no role of theirs may fire.
 * Surfaces as 403 (see GlobalErrors) and never carries data about the thing being refused.
 */
public class StaffAccessException extends RuntimeException {
    public StaffAccessException(String message) { super(message); }
}
