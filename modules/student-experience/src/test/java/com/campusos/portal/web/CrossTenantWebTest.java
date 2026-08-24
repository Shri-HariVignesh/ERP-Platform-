package com.campusos.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.domain.DocumentArtifact;
import com.campusos.portal.engine.EngineTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AREA 2 — scoping over HTTP, against the seeded tenants (Hari/SNIT, Meera/ACE).
 * Proves the boundary holds at the edge, not only in the service layer.
 */
@Tag("security")
class CrossTenantWebTest extends EngineTestBase {

    private static final String HARI = "s_hari";
    private static final String MEERA = "s_meera";

    private MockHttpSession sessionFor(String studentId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/switch").param("studentId", studentId).session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is3xxRedirection());
        return session;
    }

    private DocumentArtifact documentOf(String tenantId, String studentId) {
        var docs = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc(tenantId, studentId);
        assertThat(docs).as("seed data must contain a generated document for %s", studentId).isNotEmpty();
        return docs.get(0);
    }

    @Test
    @DisplayName("a student can download their own generated document")
    void ownDocumentDownloads() throws Exception {
        MockHttpSession meera = sessionFor(MEERA);
        DocumentArtifact hers = documentOf("t_ace", MEERA);

        MvcResult res = mvc.perform(get("/documents/" + hers.id + "/download").session(meera))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentAsString()).contains(hers.serialNo);
    }

    @Test
    @DisplayName("cross-tenant document download is refused with 400, and leaks no content")
    void crossTenantDownloadIsRefused() throws Exception {
        DocumentArtifact his = documentOf("t_snit", HARI);
        MockHttpSession meera = sessionFor(MEERA);

        MvcResult res = mvc.perform(get("/documents/" + his.id + "/download").session(meera))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("not visible in scope");
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
        MockHttpSession hari = sessionFor(HARI);
        String hisPage = mvc.perform(get("/requests").session(hari))
                .andReturn().getResponse().getContentAsString();
        assertThat(main(hisPage)).contains("Infosys").contains("Tata Elxsi");

        MockHttpSession meera = sessionFor(MEERA);
        String herPage = mvc.perform(get("/requests").session(meera))
                .andReturn().getResponse().getContentAsString();

        assertThat(herPage).contains("Amrita College of Engineering");
        assertThat(main(herPage))
                .as("none of Hari's data may appear in Meera's tracker")
                .doesNotContain("SNIT21CS042")
                .doesNotContain("Infosys")
                .doesNotContain("Tata Elxsi")
                .doesNotContain("Hari Prasad");
    }

    @Test
    @DisplayName("Hari's name on Meera's page comes only from the demo identity picker, never from data")
    void otherStudentAppearsOnlyInTheDemoSwitcher() throws Exception {
        MockHttpSession meera = sessionFor(MEERA);
        String herPage = mvc.perform(get("/requests").session(meera))
                .andReturn().getResponse().getContentAsString();

        // real auth is a declared non-goal, so the header carries an identity switcher listing
        // every seeded demo student. That is the ONLY place another student's name may appear.
        int switcherFrom = herPage.indexOf("<form class=\"switcher\"");
        int switcherTo = herPage.indexOf("</form>", switcherFrom);
        String switcher = herPage.substring(switcherFrom, switcherTo);

        assertThat(switcher).contains("Hari Prasad");
        assertThat(herPage.replace(switcher, ""))
                .as("outside the demo switcher, Hari does not exist on this page")
                .doesNotContain("Hari Prasad");
    }

    @Test
    @DisplayName("the demo hook cannot move another tenant's request")
    void crossTenantSimAdvanceIsRefused() throws Exception {
        var his = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", HARI).stream()
                .filter(r -> r.state == com.campusos.portal.domain.RequestState.APPROVAL)
                .findFirst().orElseThrow();
        var stateBefore = his.state;

        MockHttpSession meera = sessionFor(MEERA);
        mvc.perform(post("/sim/requests/" + his.id + "/advance")
                        .param("event", "APPROVE").param("actor", "OFFICE").session(meera))
                .andReturn();

        assertThat(requests.findByIdAndTenantIdAndStudentId(his.id, "t_snit", HARI).orElseThrow().state)
                .as("her attempt changed nothing").isEqualTo(stateBefore);
    }
}
