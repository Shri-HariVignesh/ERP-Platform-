package com.campusos.portal.service;

import com.campusos.portal.domain.SubjectMark;
import java.util.List;

/**
 * The ONE definition of how subject marks become a semester aggregate. The seeder and
 * AcademicWriteService both call this, which is why the seeded SemesterResult rows are
 * consistent with their SubjectMark rows by construction rather than by coincidence.
 */
public final class SgpaMath {

    private SgpaMath() {}

    /** Internal is out of 40, external out of 60. */
    public static final int MAX_INTERNAL = 40;
    public static final int MAX_EXTERNAL = 60;

    /** Total out of 100 -> a ten-point grade point. */
    public static int gradePoint(int total) {
        if (total >= 90) return 10;
        if (total >= 80) return 9;
        if (total >= 70) return 8;
        if (total >= 60) return 7;
        if (total >= 50) return 6;
        if (total >= 40) return 5;
        return 0;   // below the pass mark earns no credit
    }

    public static String grade(int total) {
        if (total >= 90) return "S";
        if (total >= 80) return "A";
        if (total >= 70) return "B";
        if (total >= 60) return "C";
        if (total >= 50) return "D";
        if (total >= 40) return "E";
        return "F";
    }

    /** Credit-weighted SGPA over the given marks, to two decimals. */
    public static double sgpa(List<SubjectMark> marks) {
        int credits = credits(marks);
        if (credits == 0) return 0;
        double weighted = marks.stream()
                .mapToDouble(m -> gradePoint(m.total()) * (double) m.credits).sum();
        return Math.round(weighted / credits * 100) / 100.0;
    }

    /** A failed subject earns no credit, so it must not inflate the denominator either. */
    public static int credits(List<SubjectMark> marks) {
        return marks.stream().filter(m -> gradePoint(m.total()) > 0).mapToInt(m -> m.credits).sum();
    }
}
