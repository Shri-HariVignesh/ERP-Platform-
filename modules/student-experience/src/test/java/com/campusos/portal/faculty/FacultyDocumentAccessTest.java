package com.campusos.portal.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.campusos.portal.domain.DocumentArtifact;
import com.campusos.portal.engine.EngineTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * /documents/{id}/download became STUDENT-only when student authentication landed. A generated
 * transcript belongs to the student who asked for it.
 *
 * The faculty Students view renders that student's request cards, and a DOCUMENT artifact
 * carries a download href — so without care the staff screen would have offered a link that
 * 403s. The chosen resolution is VIEW-ONLY METADATA: the artifact still shows, with its label
 * and serial, but PresentationService.staffCards strips the href on the staff rendering path.
 *
 * These tests pin both halves of that choice: the faculty screen still works and still shows
 * the document, and it offers no link a faculty member cannot follow.
 */
@Tag("security")
class FacultyDocumentAccessTest extends EngineTestBase {

    private static final String HARI = "s_hari";

    private DocumentArtifact harisDocument() {
        return documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", HARI).stream()
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("the faculty student profile still renders, and still shows the documents")
    void facultyStudentProfileStillWorks() throws Exception {
        var res = mvc.perform(get("/faculty/students/" + HARI)
                .with(user(principal("anjali.menon")))).andReturn().getResponse();

        assertThat(res.getStatus())
                .as("locking the student download route must not break an existing faculty screen")
                .isEqualTo(200);

        String page = res.getContentAsString();
        assertThat(page).contains("Hari Prasad");
        assertThat(page)
                .as("the document metadata is still on the page")
                .contains(harisDocument().serialNo);
    }

    @Test
    @DisplayName("the faculty view offers no download link it cannot follow")
    void facultyViewOffersNoDeadDownloadLink() throws Exception {
        String page = mvc.perform(get("/faculty/students/" + HARI)
                        .with(user(principal("anjali.menon"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("a link that 403s is worse than no link — the href is stripped for staff")
                .doesNotContain("/download");
    }

    @Test
    @DisplayName("a faculty member cannot fetch a student's document by URL either")
    void facultyCannotDownloadAStudentsDocument() throws Exception {
        DocumentArtifact doc = harisDocument();
        var res = mvc.perform(get("/documents/" + doc.id + "/download")
                .with(user(principal("anjali.menon")))).andReturn().getResponse();

        assertThat(res.getStatus())
                .as("hiding the link is presentation; this is the control")
                .isEqualTo(403);
        assertThat(res.getContentAsString())
                .doesNotContain(doc.serialNo).doesNotContain("Hari Prasad");
    }

    @Test
    @DisplayName("the student themselves can still download it — the route still does its job")
    void theStudentCanStillDownloadTheirOwnDocument() throws Exception {
        DocumentArtifact doc = harisDocument();
        var res = mvc.perform(get("/documents/" + doc.id + "/download")
                .with(user(studentPrincipal(HARI)))).andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).contains(doc.serialNo);
    }

    @Test
    @DisplayName("the student's own card still carries the working download link")
    void theStudentsOwnCardKeepsTheLink() throws Exception {
        String page = mvc.perform(get("/requests").with(user(studentPrincipal(HARI))))
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("staffCards strips the href for staff only; the student keeps theirs")
                .contains("/download");
    }
}
