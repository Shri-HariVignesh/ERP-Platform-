package com.campusos.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.engine.EngineTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Regressions for the input-handling findings of the audit.
 *
 * F3 (MEDIUM, CWE-1284/CWE-400): company, role, certificateFilename and both date fields
 *    carried @NotBlank with no @Size. A 100,000-character company was accepted, persisted and
 *    re-rendered on every later page load (118KB page), repeatable without limit.
 * F4 (MEDIUM, CWE-248/CWE-20): LocalDate.parse threw DateTimeParseException, which is not an
 *    IllegalArgumentException, so `from=NOTADATE` produced an HTTP 500 and a logged stack
 *    trace instead of a rejected field.
 * F5 (LOW, CWE-209): the IllegalTransitionException handler returned e.getMessage() as the
 *    response body, handing engine vocabulary to anyone probing for another tenant's ids.
 */
@Tag("security")
class InputHardeningTest extends EngineTestBase {

    /**
     * Was: a session primed by POST /switch. /switch is retired, so the harness authenticates
     * as the student instead. Every assertion in this file is unchanged — only the way the
     * request acquires its identity has moved from a form parameter to a principal.
     */
    private RequestPostProcessor as(String studentId) {
        return user(studentPrincipal(studentId));
    }

    private static String repeat(int n) {
        return "A".repeat(n);
    }

    /* ---- F4 ---- */

    @Test
    @DisplayName("a malformed date is a rejected field, not a 500")
    void malformedDateDoesNotCrash() throws Exception {
        int status = mvc.perform(post("/leave").with(csrf()).with(as("s_hari"))
                        .param("leaveType", "PERSONAL")
                        .param("from", "NOTADATE").param("to", "NOTADATE")
                        .param("reason", "probe"))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("malformed input must not reach an unhandled exception").isNotEqualTo(500);
    }

    @Test
    @DisplayName("a malformed internship date is a rejected field, not a 500")
    void malformedInternshipDateDoesNotCrash() throws Exception {
        int status = mvc.perform(post("/internship").with(csrf()).with(as("s_hari"))
                        .param("company", "c").param("role", "r")
                        .param("from", "31-02-2026").param("to", "also-not-a-date")
                        .param("details", "d").param("certificateFilename", "c.pdf"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(500);
    }

    /* ---- F3 ---- */

    @Test
    @DisplayName("an oversized internship field is rejected, not stored and re-rendered forever")
    void oversizedFieldIsRejected() throws Exception {
        RequestPostProcessor s = as("s_hari");
        String huge = repeat(100_000);
        mvc.perform(post("/internship").with(csrf()).with(s)
                .param("company", huge).param("role", "r")
                .param("from", "2026-01-01").param("to", "2026-03-01")
                .param("details", "d").param("certificateFilename", "c.pdf"));

        String page = mvc.perform(get("/internship").with(s))
                .andReturn().getResponse().getContentAsString();
        assertThat(page)
                .as("a 100k-character field must never reach the rendered page")
                .doesNotContain(repeat(1_000));
    }

    @Test
    @DisplayName("every oversized form field is rejected across all four forms")
    void allFormsBoundTheirFields() throws Exception {
        RequestPostProcessor s = as("s_hari");
        String huge = repeat(50_000);

        mvc.perform(post("/leave").with(csrf()).with(s).param("leaveType", "PERSONAL")
                .param("from", "2026-09-01").param("to", "2026-09-02").param("reason", huge));
        mvc.perform(post("/documents").with(csrf()).with(s)
                .param("docType", "BONAFIDE").param("purpose", huge).param("copies", "1"));
        mvc.perform(post("/grievance").with(csrf()).with(s)
                .param("category", "OTHER").param("subject", huge).param("description", huge));

        for (String view : new String[] {"/leave", "/documents", "/grievance", "/requests"}) {
            assertThat(mvc.perform(get(view).with(s)).andReturn().getResponse().getContentAsString())
                    .as("%s must not render an oversized field", view)
                    .doesNotContain(repeat(1_000));
        }
    }

    @Test
    @DisplayName("the /actions input parameter is bounded even though it bypasses the forms")
    void actionInputIsBounded() throws Exception {
        RequestPostProcessor s = as("s_hari");
        String id = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", "s_hari")
                .stream().findFirst().orElseThrow().id;

        mvc.perform(post("/actions/" + id).with(csrf()).with(s)
                .param("event", "RESUBMIT").param("input", repeat(50_000)));

        assertThat(mvc.perform(get("/requests").with(s))
                .andReturn().getResponse().getContentAsString())
                .as("the raw input param must not become an unbounded stored value")
                .doesNotContain(repeat(1_000));
    }

    /* ---- F5 ---- */

    /**
     * F5 (LOW, CWE-209): the handler returned e.getMessage() as the response body, so a denied
     * cross-tenant download answered "document not visible in scope". This was BLOCKED for one
     * iteration because CrossTenantWebTest asserted the body CONTAINED that phrase; the owner
     * then chose to change that assertion, so the leak is closed and pinned here too.
     */
    @Test
    @DisplayName("a denied download does not echo the engine's own words back")
    void deniedDownloadDoesNotLeakInternalMessage() throws Exception {
        RequestPostProcessor meera = as("s_meera");

        int denials = 0;
        for (long id = 1; id <= 8; id++) {
            var res = mvc.perform(get("/documents/" + id + "/download").with(meera))
                    .andReturn().getResponse();
            if (res.getStatus() == 200) continue;          // Meera's own document
            denials++;
            assertThat(res.getContentAsString())
                    .as("denial body for id %d must not carry engine vocabulary", id)
                    .doesNotContain("not visible in scope")
                    .doesNotContain("tenant")
                    .doesNotContain("IllegalTransition");
        }
        assertThat(denials).as("the probe must actually have been denied something").isPositive();
    }

    /**
     * The refusal must still be a refusal — a generic body is only an improvement if the
     * document itself is still withheld.
     */
    @Test
    @DisplayName("the generic denial still withholds the document")
    void deniedDownloadStillWithholdsContent() throws Exception {
        RequestPostProcessor meera = as("s_meera");

        for (long id = 1; id <= 8; id++) {
            var res = mvc.perform(get("/documents/" + id + "/download").with(meera))
                    .andReturn().getResponse();
            if (res.getStatus() == 200) continue;
            assertThat(res.getStatus()).isEqualTo(400);
            assertThat(res.getContentAsString())
                    .doesNotContain("Hari Prasad").doesNotContain("SNIT21CS042")
                    .doesNotContain("bonafide").doesNotContain("<article");
        }
    }
}
