package com.campusos.portal.service;

import com.campusos.portal.domain.AttendanceRecord;
import java.util.List;

public final class AttendanceMath {

    private AttendanceMath() {}

    /** PRESENT + APPROVED_LEAVE over every day that counts. SCHEDULED days are excluded. */
    public static Double pct(List<AttendanceRecord> rows) {
        long counted = rows.stream().filter(AttendanceRecord::counted).count();
        if (counted == 0) return null;
        long attended = rows.stream().filter(AttendanceRecord::counted)
                .filter(AttendanceRecord::attended).count();
        return Math.round(attended * 1000.0 / counted) / 10.0;
    }
}
