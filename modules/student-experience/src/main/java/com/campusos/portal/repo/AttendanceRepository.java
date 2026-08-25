package com.campusos.portal.repo;

import com.campusos.portal.domain.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface AttendanceRepository extends Repository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByTenantIdAndStudentId(String tenantId, String studentId);

    List<AttendanceRecord> findByTenantIdAndStudentIdAndDateBetween(
            String tenantId, String studentId, LocalDate from, LocalDate to);

    /** The upsert key for faculty marking: one row per student per class day. */
    Optional<AttendanceRecord> findByTenantIdAndStudentIdAndDate(
            String tenantId, String studentId, LocalDate date);

    AttendanceRecord save(AttendanceRecord a);
}
