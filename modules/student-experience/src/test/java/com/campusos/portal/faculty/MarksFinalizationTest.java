package com.campusos.portal.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.security.StaffPrincipal;
import com.campusos.portal.service.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * FREEZE CONDITION 2 — MARKS / SEED CONSISTENCY, plus the draft/finalized visibility rule.
 *
 * The seeded SemesterResult rows are DERIVED from seeded SubjectMark rows through SgpaMath,
 * and SemesterResult is only republished once every subject of the semester is finalized.
 * Together those two facts are why finalizing one subject cannot clobber a published SGPA.
 */
@Tag("security")
class MarksFinalizationTest extends EngineTestBase {

    @Autowired StaffScopeResolver staffScopes;
    @Autowired AcademicWriteService writes;
    @Autowired AcademicService academic;

    private static final ClassKey CSE_5A =
            new ClassKey("Computer Science & Engineering", 5, "A");

    private StaffScope scopeOf(String username) {
        StaffPrincipal p = principal(username);
        Authentication auth = new UsernamePasswordAuthenticationToken(p, "n/a", p.getAuthorities());
        return staffScopes.current(auth);
    }

    private static Map<String, AcademicWriteService.MarkInput> one(String studentId, int i, int e) {
        return Map.of(studentId, new AcademicWriteService.MarkInput(i, e));
    }

    @Autowired com.campusos.portal.service.DemoIdentity identities;

    private static final java.util.concurrent.atomic.AtomicInteger SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * A fresh student in a real seeded class. Marks are per-student, so giving each test its
     * own student is what keeps them independent — two tests sharing one transcript would make
     * the suite order-dependent, which is a worse bug than the one being tested for.
     */
    private Student freshStudentIn(ClassKey c, String programme) {
        int n = SEQ.incrementAndGet();
        Student s = new Student();
        s.id = "s_test_" + n;
        s.tenantId = "t_snit";
        s.rollNo = "SNITTEST" + n;
        s.name = "Test Student " + n;
        s.email = "test" + n + "@snit.ac.in";
        s.program = programme;
        s.department = c.department();
        s.semester = c.semester();
        s.section = c.section();
        s.active = true;
        s.leaveBalance = 12;
        s.advisorName = "Advisor";
        s.hodName = "HOD";
        students.save(s);
        identities.register(s.tenantId, s.id, s.name + " · test fixture");
        return s;
    }

    private Student freshCse() {
        return freshStudentIn(CSE_5A, "B.Tech Computer Science");
    }

    private static final ClassKey ECE_5A = new ClassKey("Electronics & Communication", 5, "A");

    private Student freshEce() {
        return freshStudentIn(ECE_5A, "B.Tech Electronics");
    }

    /* ------------------------- the seed is consistent with itself ------------------------- */

    @Test
    @DisplayName("every seeded SemesterResult is reproducible from its own SubjectMark rows")
    void seededResultsAreDerivedFromSeededMarks() {
        for (String studentId : List.of("s_hari", "s_divya", "s_nikhil")) {
            List<SemesterResult> published =
                    semesterResults.findByTenantIdAndStudentIdOrderBySemesterAsc("t_snit", studentId);
            assertThat(published).as("%s has seeded results", studentId).isNotEmpty();

            for (SemesterResult r : published) {
                List<SubjectMark> behindIt = subjectMarks
                        .findByTenantIdAndStudentIdAndSemesterOrderBySubjectCodeAsc(
                                "t_snit", studentId, r.semester)
                        .stream().filter(SubjectMark::finalized).toList();

                assertThat(behindIt)
                        .as("a published SGPA with no marks behind it would be recomputed from "
                                + "partial data the first time anyone finalized a subject")
                        .isNotEmpty();
                assertThat(r.sgpa).isEqualTo(SgpaMath.sgpa(behindIt));
                assertThat(r.credits).isEqualTo(SgpaMath.credits(behindIt));
            }
        }
    }

    /* ---------------------------- the recompute gate ---------------------------- */

    @Test
    @DisplayName("finalizing ONE subject does not republish the semester result")
    void partialFinalizeDoesNotClobberTheSemesterResult() {
        StaffScope anjali = scopeOf("anjali.menon");
        String student = freshCse().id;

        assertThat(semesterResults.findByTenantIdAndStudentIdAndSemester("t_snit", student, 5))
                .as("semester 5 is in progress — nothing published yet").isEmpty();

        AcademicWriteService.MarksWrite w =
                writes.saveMarks(anjali, CSE_5A, "CS501", one(student, 35, 50), MarkStatus.FINALIZED);

        assertThat(w.saved()).isEqualTo(1);
        assertThat(w.recomputed())
                .as("one of five subjects is not a semester")
                .isEmpty();
        assertThat(semesterResults.findByTenantIdAndStudentIdAndSemester("t_snit", student, 5))
                .as("and no SemesterResult was invented from that one subject")
                .isEmpty();
    }

    @Test
    @DisplayName("the semester result appears only once EVERY subject of the class is finalized")
    void completeFinalizeRepublishesTheSemesterResult() {
        StaffScope anjali = scopeOf("anjali.menon");        // CS501, CS502, CS503
        StaffScope suresh = scopeOf("suresh.kumar");        // CS504, CS505
        String student = freshCse().id;

        assertThat(semesterResults.findByTenantIdAndStudentIdAndSemester("t_snit", student, 5))
                .isEmpty();

        writes.saveMarks(anjali, CSE_5A, "CS501", one(student, 36, 52), MarkStatus.FINALIZED);
        writes.saveMarks(anjali, CSE_5A, "CS502", one(student, 30, 45), MarkStatus.FINALIZED);
        writes.saveMarks(anjali, CSE_5A, "CS503", one(student, 38, 55), MarkStatus.FINALIZED);
        writes.saveMarks(suresh, CSE_5A, "CS504", one(student, 28, 40), MarkStatus.FINALIZED);

        assertThat(semesterResults.findByTenantIdAndStudentIdAndSemester("t_snit", student, 5))
                .as("four of five is still not a semester").isEmpty();

        AcademicWriteService.MarksWrite last =
                writes.saveMarks(suresh, CSE_5A, "CS505", one(student, 33, 48), MarkStatus.FINALIZED);

        assertThat(last.recomputed()).containsExactly(student);
        SemesterResult published = semesterResults
                .findByTenantIdAndStudentIdAndSemester("t_snit", student, 5).orElseThrow();

        List<SubjectMark> marks = subjectMarks
                .findByTenantIdAndStudentIdAndSemesterOrderBySubjectCodeAsc("t_snit", student, 5)
                .stream().filter(SubjectMark::finalized).toList();
        assertThat(marks).hasSize(5);
        assertThat(published.sgpa)
                .as("computed by the same SgpaMath the seed used")
                .isEqualTo(SgpaMath.sgpa(marks));
        assertThat(published.credits).isEqualTo(SgpaMath.credits(marks));
    }

    @Test
    @DisplayName("a draft among the subjects holds the semester result back")
    void aDraftBlocksRepublication() {
        StaffScope anjali = scopeOf("anjali.menon");
        StaffScope suresh = scopeOf("suresh.kumar");
        String student = freshCse().id;

        writes.saveMarks(anjali, CSE_5A, "CS501", one(student, 33, 48), MarkStatus.FINALIZED);
        writes.saveMarks(anjali, CSE_5A, "CS502", one(student, 31, 44), MarkStatus.FINALIZED);
        writes.saveMarks(anjali, CSE_5A, "CS503", one(student, 34, 47), MarkStatus.FINALIZED);
        writes.saveMarks(suresh, CSE_5A, "CS504", one(student, 29, 41), MarkStatus.FINALIZED);
        AcademicWriteService.MarksWrite draft =
                writes.saveMarks(suresh, CSE_5A, "CS505", one(student, 32, 46), MarkStatus.DRAFT);

        assertThat(draft.recomputed()).isEmpty();
        assertThat(semesterResults.findByTenantIdAndStudentIdAndSemester("t_snit", student, 5))
                .as("the last subject is still a draft, so the semester is not published")
                .isEmpty();

        writes.saveMarks(suresh, CSE_5A, "CS505", one(student, 32, 46), MarkStatus.FINALIZED);
        assertThat(semesterResults.findByTenantIdAndStudentIdAndSemester("t_snit", student, 5))
                .as("finalizing the last one publishes it").isPresent();
    }

    @Test
    @DisplayName("a historical semester with no timetable behind it is never recomputed")
    void semestersWithNoAssignmentsAreLeftAlone() {
        StaffScope anjali = scopeOf("anjali.menon");
        SemesterResult before = semesterResults
                .findByTenantIdAndStudentIdAndSemester("t_snit", "s_hari", 2).orElseThrow();
        double sgpaBefore = before.sgpa;

        // There are no TeachingAssignments for semester 2, so the "expected subject set" is
        // empty and the safe answer is to touch nothing.
        ClassKey sem2 = new ClassKey("Computer Science & Engineering", 2, "A");
        assertThatThrownBy(() -> writes.saveMarks(anjali, sem2, "CS201",
                one("s_hari", 40, 60), MarkStatus.FINALIZED))
                .as("she is not assigned to semester 2 at all")
                .isInstanceOf(StaffAccessException.class);

        assertThat(semesterResults.findByTenantIdAndStudentIdAndSemester("t_snit", "s_hari", 2)
                .orElseThrow().sgpa).isEqualTo(sgpaBefore);
    }

    /* --------------------------- draft / finalized visibility --------------------------- */

    @Test
    @DisplayName("a DRAFT mark is not visible to the student; a FINALIZED one is")
    void draftIsHiddenAndFinalizedIsVisible() throws Exception {
        StaffScope babu = scopeOf("suresh.babu");
        String student = freshEce().id;
        Scope readAs = new Scope("t_snit", student);

        writes.saveMarks(babu, ECE_5A, "EC501", one(student, 33, 49), MarkStatus.DRAFT);

        assertThat(academic.publishedMarks(readAs))
                .as("a draft must not reach the student's read model at all")
                .noneMatch(m -> m.subjectCode().equals("EC501"));
        assertThat(subjectMarks.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
                        "t_snit", student, 5, "EC501"))
                .as("but it IS stored — a draft is saved work, not a discard")
                .isPresent();

        writes.saveMarks(babu, ECE_5A, "EC501", one(student, 33, 49), MarkStatus.FINALIZED);

        assertThat(academic.publishedMarks(readAs))
                .as("finalizing publishes it")
                .anyMatch(m -> m.subjectCode().equals("EC501") && m.total() == 82);
    }

    @Test
    @DisplayName("the student's Academic page renders finalized marks and never a draft")
    void studentAcademicPageShowsOnlyFinalized() throws Exception {
        StaffScope anjali = scopeOf("anjali.menon");
        String student = freshCse().id;
        writes.saveMarks(anjali, CSE_5A, "CS502", one(student, 11, 12), MarkStatus.DRAFT);
        writes.saveMarks(anjali, CSE_5A, "CS501", one(student, 39, 58), MarkStatus.FINALIZED);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/switch").with(csrf()).param("studentId", student).session(session));
        String page = mvc.perform(get("/academic").session(session))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).as("the finalized subject is on the page").contains("CS501");
        assertThat(page)
                .as("the draft total (23) must appear nowhere on a student's screen")
                .doesNotContain(">23<");
    }

    /* ------------------------------- integrity guards ------------------------------- */

    @Test
    @DisplayName("finalizing is one-way — a published mark cannot be returned to draft")
    void finalizedCannotGoBackToDraft() {
        StaffScope babu = scopeOf("suresh.babu");
        String student = freshEce().id;

        writes.saveMarks(babu, ECE_5A, "EC502", one(student, 30, 44), MarkStatus.FINALIZED);

        assertThatThrownBy(() -> writes.saveMarks(babu, ECE_5A, "EC502",
                one(student, 10, 10), MarkStatus.DRAFT))
                .isInstanceOf(StaffAccessException.class)
                .hasMessageContaining("cannot be returned to draft");

        assertThat(subjectMarks.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
                "t_snit", student, 5, "EC502").orElseThrow().total())
                .as("and the published value is untouched").isEqualTo(74);
    }

    @Test
    @DisplayName("a correction to a finalized mark is allowed, and re-audits as finalized")
    void aFinalizedMarkMayBeCorrected() {
        StaffScope babu = scopeOf("suresh.babu");
        String student = freshEce().id;

        writes.saveMarks(babu, ECE_5A, "EC501", one(student, 20, 30), MarkStatus.FINALIZED);
        writes.saveMarks(babu, ECE_5A, "EC501", one(student, 25, 35), MarkStatus.FINALIZED);

        assertThat(subjectMarks.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
                "t_snit", student, 5, "EC501").orElseThrow().total()).isEqualTo(60);
        assertThat(academicAudits.findByTenantIdAndStudentIdOrderByAtDesc("t_snit", student))
                .as("the correction is audited, not silent")
                .anyMatch(a -> a.kind == AcademicAudit.Kind.MARKS_FINALIZED
                        && "EC501".equals(a.subjectCode));
    }

    @Test
    @DisplayName("marks cannot be entered for a class or subject the staff member does not teach")
    void cannotEnterMarksOutsideTheTeachingAssignment() {
        StaffScope anjali = scopeOf("anjali.menon");        // CS501-503 of CSE 5 A

        assertThatThrownBy(() -> writes.saveMarks(anjali, CSE_5A, "CS504",
                one("s_hari", 30, 40), MarkStatus.DRAFT))
                .as("a subject in her own class that is not hers")
                .isInstanceOf(StaffAccessException.class);

        assertThatThrownBy(() -> writes.saveMarks(anjali, ECE_5A, "EC501",
                one("s_nikhil", 30, 40), MarkStatus.DRAFT))
                .as("another department's class")
                .isInstanceOf(StaffAccessException.class);

        assertThatThrownBy(() -> writes.saveMarks(anjali, CSE_5A, "CS501",
                one("s_nikhil", 30, 40), MarkStatus.DRAFT))
                .as("a student who is not in the class")
                .isInstanceOf(StaffAccessException.class);
    }

    @Test
    @DisplayName("marks outside the permitted range are refused")
    void marksAreRangeChecked() {
        StaffScope anjali = scopeOf("anjali.menon");
        for (AcademicWriteService.MarkInput bad : List.of(
                new AcademicWriteService.MarkInput(41, 50),
                new AcademicWriteService.MarkInput(30, 61),
                new AcademicWriteService.MarkInput(-1, 50))) {
            assertThatThrownBy(() -> writes.saveMarks(anjali, CSE_5A, "CS501",
                    Map.of("s_hari", bad), MarkStatus.DRAFT))
                    .isInstanceOf(StaffAccessException.class)
                    .hasMessageContaining("out of range");
        }
    }

    @Test
    @DisplayName("every marks write leaves an audit row naming the staff member and the status")
    void marksAreAudited() {
        StaffScope babu = scopeOf("suresh.babu");
        String student = freshEce().id;

        writes.saveMarks(babu, ECE_5A, "EC502", one(student, 22, 33), MarkStatus.DRAFT);

        var audits = academicAudits.findByTenantIdAndStudentIdOrderByAtDesc("t_snit", student);
        assertThat(audits.get(0).kind).isEqualTo(AcademicAudit.Kind.MARKS_DRAFT);
        assertThat(audits.get(0).staffId).isEqualTo("st_babu");
        assertThat(audits.get(0).subjectCode).isEqualTo("EC502");
    }
}
