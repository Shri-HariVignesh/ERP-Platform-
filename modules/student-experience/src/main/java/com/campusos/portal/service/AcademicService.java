package com.campusos.portal.service;

import com.campusos.portal.domain.*;
import com.campusos.portal.repo.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AcademicService {

    private final AttendanceRepository attendance;
    private final AcademicRecordRepository academic;
    private final SemesterResultRepository results;
    private final ExamTermRepository terms;
    private final DocumentRepository documents;

    public AcademicService(AttendanceRepository attendance, AcademicRecordRepository academic,
                           SemesterResultRepository results, ExamTermRepository terms,
                           DocumentRepository documents) {
        this.attendance = attendance;
        this.academic = academic;
        this.results = results;
        this.terms = terms;
        this.documents = documents;
    }

    public Double attendancePct(Scope s) {
        return AttendanceMath.pct(attendance.findByTenantIdAndStudentId(s.tenantId(), s.studentId()));
    }

    public long approvedLeaveDays(Scope s) {
        return attendance.findByTenantIdAndStudentId(s.tenantId(), s.studentId()).stream()
                .filter(a -> a.status == AttendanceRecord.Status.APPROVED_LEAVE).count();
    }

    public List<AcademicRecord> records(Scope s) {
        return academic.findByTenantIdAndStudentIdOrderByRecordedAtDesc(s.tenantId(), s.studentId());
    }

    public List<SemesterResult> results(Scope s) {
        return results.findByTenantIdAndStudentIdOrderBySemesterAsc(s.tenantId(), s.studentId());
    }

    public double cgpa(Scope s) {
        List<SemesterResult> rs = results(s);
        if (rs.isEmpty()) return 0;
        int credits = rs.stream().mapToInt(r -> r.credits).sum();
        if (credits == 0) return 0;
        double weighted = rs.stream().mapToDouble(r -> r.sgpa * r.credits).sum();
        return Math.round(weighted / credits * 100) / 100.0;
    }

    public ExamTerm currentTerm(String tenantId) {
        List<ExamTerm> t = terms.findByTenantId(tenantId);
        return t.isEmpty() ? null : t.get(0);
    }

    public DocumentArtifact latestHallTicket(Scope s) {
        return documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(s.tenantId(), s.studentId())
                .stream().filter(d -> d.docType == DocType.HALL_TICKET).findFirst().orElse(null);
    }

    public List<DocumentArtifact> documents(Scope s) {
        return documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(s.tenantId(), s.studentId());
    }
}
