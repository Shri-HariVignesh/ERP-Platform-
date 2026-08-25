package com.campusos.portal.service;

import com.campusos.portal.domain.*;
import com.campusos.portal.repo.*;
import com.campusos.portal.view.MarkRow;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AcademicService {

    private final AttendanceRepository attendance;
    private final AcademicRecordRepository academic;
    private final SemesterResultRepository results;
    private final ExamTermRepository terms;
    private final DocumentRepository documents;
    private final SubjectMarkRepository marks;

    public AcademicService(AttendanceRepository attendance, AcademicRecordRepository academic,
                           SemesterResultRepository results, ExamTermRepository terms,
                           DocumentRepository documents, SubjectMarkRepository marks) {
        this.attendance = attendance;
        this.academic = academic;
        this.results = results;
        this.terms = terms;
        this.documents = documents;
        this.marks = marks;
    }

    /**
     * THE STUDENT-VISIBLE MARKS LIST. Filtered to FINALIZED, and that filter lives here — in
     * the only service the student's Academic view reads — rather than in a template, so a
     * DRAFT mark cannot become visible by someone forgetting a condition in the HTML.
     */
    public List<MarkRow> publishedMarks(Scope s) {
        return marks.findByTenantIdAndStudentIdOrderBySemesterAscSubjectCodeAsc(
                        s.tenantId(), s.studentId()).stream()
                .filter(SubjectMark::finalized)
                .map(m -> new MarkRow(m.semester, m.subjectCode, m.subjectName, m.internal,
                        m.external, m.total(), SgpaMath.grade(m.total()), m.credits,
                        "Published", true))
                .toList();
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
