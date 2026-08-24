package com.campusos.portal.view;

/** status: done | current | pending | skipped | failed */
public record TimelineStep(String label, String status) {}
