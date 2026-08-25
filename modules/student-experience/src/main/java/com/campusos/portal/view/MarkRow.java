package com.campusos.portal.view;

/**
 * A display-ready marks line. Built in the service layer so the templates never compute a
 * grade or read a raw MarkStatus — the same discipline RequestCard applies to workflows.
 */
public record MarkRow(int semester, String subjectCode, String subjectName,
                      int internal, int external, int total, String grade, int credits,
                      String status, boolean finalized) {}
