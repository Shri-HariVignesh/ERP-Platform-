package com.campusos.portal.domain;

/**
 * DRAFT marks are the faculty member's working copy: stored, audited, and invisible to the
 * student. FINALIZED is one-way — see AcademicWriteService, which refuses to walk it back.
 */
public enum MarkStatus { DRAFT, FINALIZED }
