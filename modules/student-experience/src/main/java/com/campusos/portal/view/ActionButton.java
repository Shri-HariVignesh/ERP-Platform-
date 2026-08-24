package com.campusos.portal.view;

public record ActionButton(String label, String event, String actor, String tone,
                          boolean requiresNote, String inputLabel) {}
