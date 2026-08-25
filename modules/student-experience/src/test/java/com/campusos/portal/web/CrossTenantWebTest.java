package com.campusos.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.domain.DocumentArtifact;
import com.campusos.portal.engine.EngineTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AREA 2 — scoping over HTTP, against the seeded tenants (Hari/SNIT, Meera/ACE).
 * Proves the boundary holds at the edge, not only in the service layer.
 */
@Tag("security")
class CrossTenantWebTest extends EngineTestBase {

    private static final String HARI = "s_hari";
    private static final String MEERA = "s_meera";

    /**
     * Was: POST /switch with a studentId. That is exactly the client-chosen identity this
     * work removes, so the harness now authenticates as the student instead. Every assertion
     * below is unchanged — only how the session gets its identity has moved.
     */
    private RequestPostProcessor as(String studentId) {
        return user(studentPrincipal(studentId));
    }

    private DocumentArtifact documentOf(String tenantId, String studentId) {
        var docs = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(tenantId, studentId);
        assertThat(docs).as("seed data must contain a generated document for %s", studentId).isNotEmpty();
        return docs.get(0);
    }

    @Test
    @DisplayName("a student can download their own generated document")
    void ownDocumentDownloads() throws Exception {
        RequestPostProcessor meera = as(MEERA);
        DocumentArtifact hers = documentOf("t_ace", MEERA);

        MvcResult res = mvc.perform(get("/documents/" + hers.id + "/download").with(meera))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentAsString()).contains(hers.serialNo);
    }

    @Test
    @DisplayName("cross-tenant document download is refused with 400, and leaks no content")
    void crossTenantDownloadIsRefused() throws Exception {
        DocumentArtifact his = documentOf("t_snit", HARI);
        RequestPostProcessor meera = as(MEERA);

        MvcResult res = mvc.perform(get("/documents/" + his.id + "/download").with(meera))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        String body = res.getResponse().getContentAsString();
        // Was: contains("not visible in scope"). That pinned an information leak (CWE-209) as
        // expected behaviour — the handler returned the engine's own message as the response
        // body, so probing another tenant's ids was answered in engine vocabulary. The refusal
        // is what this test is for, and the refusal is still asserted above; what the refusal
        // SAYS is now asserted to be free of internals.
        assertThat(body).doesNotContain("not visible in scope");
        assertThat(body).doesNotContain("tenant");
        assertThat(body).doesNotContain("IllegalTransition");
        assertThat(body).doesNotContain(his.serialNo);
        assertThat(body).doesNotContain("Hari Prasad");
    }

    /** The tracker itself, without the page chrome. */
    private static String main(String page) {
        int from = page.indexOf("<main");
        int to = page.indexOf("</main>");
        assertThat(from).as("page has a <main>").isNotNegative();
        return page.substring(from, to);
    }

    @Test
    @DisplayName("the tracker shows only the signed-in student's own requests")
    void trackerIsScoped() throws Exception {
        RequestPostProcessor hari = as(HARI);
        String hisPage = mvc.perform(get("/requests").with(hari))
                .andReturn().getResponse().getContentAsString();
        assertThat(main(hisPage)).contains("Infosys").contains("Tata Elxsi");

        RequestPostProcessor meera = as(MEERA);
        String herPage = mvc.perform(get("/requests").with(meera))
                .andReturn().getResponse().getContentAsString();

        assertThat(herPage).contains("Amrita College of Engineering");
        assertThat(main(herPage))
                .as("none of Hari's data may appear in Meera's tracker")
                .doesNotContain("SNIT21CS042")
                .doesNotContain("Infosys")
                .doesNotContain("Tata Elxsi")
                .doesNotContain("Hari Prasad");
    }

    /**
     * STRENGTHENED. This used to carve out an exception: the demo identity switcher listed
     * every seeded student, so Hari's name legitimately appeared on Meera's page and the test
     * could only assert it appeared NOWHERE ELSE.
     *
     * The switcher is gone with /switch, so the carve-out is gone with it and the claim is now
     * the flat one it always should have been: another student's name does not appear on this
     * page at all, anywhere, for any reason.
     */
    @Test
    @DisplayName("no other student's name appears anywhere on a student's page")
    void noOtherStudentAppearsAnywhere() throws Exception {
        String herPage = mvc.perform(get("/requests").with(as(MEERA)))
                .andReturn().getResponse().getContentAsString();

        assertThat(herPage)
                .as("with no identity switcher there is no longer any excuse for another "
                        + "student's name to be on this page")
                .doesNotContain("Hari Prasad")
                .doesNotContain("Divya Rajan")
                .doesNotContain("Nikhil Varma")
                .doesNotContain("SNIT21");
        assertThat(herPage).as("and she does see her own").contains("Meera Nair");
    }

    /**
     * REPOINTED FROM /sim. This used to prove the demo hook could not move another tenant's
     * request. /sim is retired, so the same property is now asserted against the live staff
     * surface — and it is a stronger claim: the ACE staff member here is genuinely
     * authenticated and genuinely holds the OFFICE role. She is refused because the request
     * belongs to another tenant, not because she failed to log in.
     */
    @Test
    @DisplayName("an authenticated staff member cannot move another tenant's request")
    void crossTenantStaffActionIsRefused() throws Exception {
        var his = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", HARI).stream()
                .filter(r -> r.state == com.campusos.portal.domain.RequestState.APPROVAL)
                .findFirst().orElseThrow();
        var stateBefore = his.state;

        int status = mvc.perform(post("/faculty/requests/" + his.id + "/act").with(csrf())
                        .with(user(principal("office.ace")))
                        .param("event", "APPROVE"))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("refused, not quietly redirected").isEqualTo(403);
        assertThat(requests.findByIdAndTenantIdAndStudentId(his.id, "t_snit", HARI).orElseThrow().state)
                .as("her attempt changed nothing").isEqualTo(stateBefore);
    }

    /** ADDED per the freeze conditions: the same endpoint, with no session at all. */
    @Test
    @DisplayName("the staff action endpoint refuses an anonymous caller outright")
    void anonymousStaffActionIsRefused() throws Exception {
        var his = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", HARI).stream()
                .filter(r -> r.state == com.campusos.portal.domain.RequestState.APPROVAL)
                .findFirst().orElseThrow();
        var stateBefore = his.state;

        var res = mvc.perform(post("/faculty/requests/" + his.id + "/act").with(csrf())
                .param("event", "APPROVE")).andReturn().getResponse();

        assertThat(res.getStatus())
                .as("anonymous must be bounced to the login form, never served")
                .isIn(302, 401, 403);
        if (res.getStatus() == 302) {
            assertThat(res.getHeader("Location")).contains("/login");
        }
        assertThat(requests.findByIdAndTenantIdAndStudentId(his.id, "t_snit", HARI).orElseThrow().state)
                .as("an anonymous POST changed nothing").isEqualTo(stateBefore);
    }
}
