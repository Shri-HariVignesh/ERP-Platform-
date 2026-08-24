package com.campusos.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.engine.EngineTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

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

    private MockHttpSession session() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/switch").param("studentId", "s_hari").session(s));
        return s;
    }

    private static String repeat(int n) {
        return "A".repeat(n);
    }

    /* ---- F4 ---- */

    @Test
    @DisplayName("a malformed date is a rejected field, not a 500")
    void malformedDateDoesNotCrash() throws Exception {
        int status = mvc.perform(post("/leave").session(session())
                        .param("leaveType", "PERSONAL")
                        .param("from", "NOTADATE").param("to", "NOTADATE")
                        .param("reason", "probe"))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("malformed input must not reach an unhandled exception").isNotEqualTo(500);
    }

    @Test
    @DisplayName("a malformed internship date is a rejected field, not a 500")
    void malformedInternshipDateDoesNotCrash() throws Exception {
        int status = mvc.perform(post("/internship").session(session())
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
        MockHttpSession s = session();
        String huge = repeat(100_000);
        mvc.perform(post("/internship").session(s)
                .param("company", huge).param("role", "r")
                .param("from", "2026-01-01").param("to", "2026-03-01")
                .param("details", "d").param("certificateFilename", "c.pdf"));

        String page = mvc.perform(get("/internship").session(s))
                .andReturn().getResponse().getContentAsString();
        assertThat(page)
                .as("a 100k-character field must never reach the rendered page")
                .doesNotContain(repeat(1_000));
    }

    @Test
    @DisplayName("every oversized form field is rejected across all four forms")
    void allFormsBoundTheirFields() throws Exception {
        MockHttpSession s = session();
        String huge = repeat(50_000);

        mvc.perform(post("/leave").session(s).param("leaveType", "PERSONAL")
                .param("from", "2026-09-01").param("to", "2026-09-02").param("reason", huge));
        mvc.perform(post("/documents").session(s)
                .param("docType", "BONAFIDE").param("purpose", huge).param("copies", "1"));
        mvc.perform(post("/grievance").session(s)
                .param("category", "OTHER").param("subject", huge).param("description", huge));

        for (String view : new String[] {"/leave", "/documents", "/grievance", "/requests"}) {
            assertThat(mvc.perform(get(view).session(s)).andReturn().getResponse().getContentAsString())
                    .as("%s must not render an oversized field", view)
                    .doesNotContain(repeat(1_000));
        }
    }

    @Test
    @DisplayName("the /actions input parameter is bounded even though it bypasses the forms")
    void actionInputIsBounded() throws Exception {
        MockHttpSession s = session();
        String id = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", "s_hari")
                .stream().findFirst().orElseThrow().id;

        mvc.perform(post("/actions/" + id).session(s)
                .param("event", "RESUBMIT").param("input", repeat(50_000)));

        assertThat(mvc.perform(get("/requests").session(s))
                .andReturn().getResponse().getContentAsString())
                .as("the raw input param must not become an unbounded stored value")
                .doesNotContain(repeat(1_000));
    }

    /*
     * F5 (LOW, CWE-209) is BLOCKED, not fixed. The IllegalTransitionException handler returns
     * e.getMessage() as the response body, so a denied cross-tenant download answers
     * "document not visible in scope". Removing that echo breaks CrossTenantWebTest line 61,
     * which asserts the body CONTAINS that phrase — an existing test pins the behaviour, and
     * weakening a test to land a fix is not allowed here. See docs/SECURITY.md.
     *
     * The residual exposure is small: the string is fixed, identical whether or not the id
     * exists, so it is not an existence oracle and carries no PII. CrossTenantWebTest already
     * asserts the denial leaks neither the serial nor the holder's name.
     */
}
