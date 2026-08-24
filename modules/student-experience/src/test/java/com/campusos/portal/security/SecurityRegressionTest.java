package com.campusos.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.domain.DocType;
import com.campusos.portal.domain.DocumentArtifact;
import com.campusos.portal.domain.RequestType;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.payload.DocumentPayload;
import com.campusos.portal.payload.PayloadCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Each test here fails on the vulnerable code and passes after its fix. They EXTEND the
 * existing suite; nothing in the original 63 was changed or weakened.
 */
@Tag("security")
class SecurityRegressionTest extends EngineTestBase {

    private static final String HARI = "s_hari";
    private static final String MEERA = "s_meera";

    private MockHttpSession sessionFor(String studentId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/switch").param("studentId", studentId).session(session));
        return session;
    }

    /* ---- FINDING 1: H2 console exposed (CWE-306) ---- */

    @Test
    @DisplayName("the H2 console is not reachable — it bypasses every tenant boundary")
    void h2ConsoleIsDisabled() throws Exception {
        for (String path : new String[] {"/h2-console", "/h2-console/", "/h2-console/login.jsp"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as("GET %s must not serve the console", path).isNotEqualTo(200);
        }
    }

    /* ---- FINDING 2: document-type parameter tampering (CWE-20) ---- */

    @Test
    @DisplayName("a student cannot request a document type the system reserves for itself")
    void cannotRequestSystemOnlyDocumentType() throws Exception {
        MockHttpSession hari = sessionFor(HARI);
        long before = countDocRequests("t_snit", HARI, DocType.INTERNSHIP_VERIFICATION);

        mvc.perform(post("/documents").session(hari)
                .param("docType", "INTERNSHIP_VERIFICATION")
                .param("purpose", "forged certificate attempt")
                .param("copies", "1"));

        assertThat(countDocRequests("t_snit", HARI, DocType.INTERNSHIP_VERIFICATION))
                .as("no INTERNSHIP_VERIFICATION request may be created by a student POST")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("the document types a student may request are still accepted")
    void legitimateDocumentTypeStillWorks() throws Exception {
        MockHttpSession hari = sessionFor(HARI);
        long before = countDocRequests("t_snit", HARI, DocType.BONAFIDE);

        mvc.perform(post("/documents").session(hari)
                .param("docType", "BONAFIDE").param("purpose", "regression check").param("copies", "1"));

        assertThat(countDocRequests("t_snit", HARI, DocType.BONAFIDE)).isEqualTo(before + 1);
    }

    private long countDocRequests(String tenantId, String studentId, DocType type) {
        PayloadCodec codec = new PayloadCodec();
        return requests.findByTenantIdAndStudentIdAndTypeOrderByCreatedAtDesc(
                        tenantId, studentId, RequestType.DOCUMENT).stream()
                .map(r -> (DocumentPayload) codec.read(r.type, r.payload))
                .filter(p -> p.docType == type)
                .count();
    }

    /* ---- FINDING 3: /verify over-exposure (CWE-359) ---- */

    @Test
    @DisplayName("the public verification page exposes no roll number, serial, or internal id")
    void verifyPageDoesNotLeakPrivateDetail() throws Exception {
        DocumentArtifact doc = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", HARI)
                .stream().filter(d -> d.verifyId != null).findFirst().orElseThrow();

        String page = mvc.perform(get("/verify/" + doc.verifyId))
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("roll number, serial and every internal identifier must be absent")
                .doesNotContain("SNIT21CS042")
                .doesNotContain(doc.serialNo)
                .doesNotContain(doc.sourceRequestId)
                .doesNotContain("s_hari")
                .doesNotContain("t_snit")
                .doesNotContain("req_");
    }

    @Test
    @DisplayName("the verification page still carries every field a verifier needs")
    void verifyPageKeepsTheRequiredFields() throws Exception {
        DocumentArtifact doc = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", HARI)
                .stream().filter(d -> d.verifyId != null).findFirst().orElseThrow();

        String page = mvc.perform(get("/verify/" + doc.verifyId))
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .contains("Hari Prasad")                              // holder
                .contains("Sree Narayana Institute of Technology")    // institution
                .contains(doc.verifyId)                               // verification id
                .contains("Verified by the issuing institution");     // status
        assertThat(page).as("no raw enum on a public page").doesNotContain(">DOCUMENT<");
    }

    @Test
    @DisplayName("the verification page stays reachable with no session and from another tenant")
    void verifyStaysPublicAcrossTenants() throws Exception {
        DocumentArtifact doc = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", HARI)
                .stream().filter(d -> d.verifyId != null).findFirst().orElseThrow();

        assertThat(mvc.perform(get("/verify/" + doc.verifyId)).andReturn().getResponse().getStatus())
                .as("no session at all — an employer has no login").isEqualTo(200);
        assertThat(mvc.perform(get("/verify/" + doc.verifyId).session(sessionFor(MEERA)))
                .andReturn().getResponse().getStatus())
                .as("a different tenant must NOT be blocked; this is the QR target").isEqualTo(200);
    }

    /* ---- FINDING 7: open redirect via the `back` parameter (CWE-601) ---- */

    @Test
    @DisplayName("the back parameter cannot bounce a student to an external site")
    void backParameterCannotOpenRedirect() throws Exception {
        MockHttpSession hari = sessionFor(HARI);
        String id = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", HARI)
                .get(0).id;

        for (String hostile : new String[] {
                "http://evil.example/pwn", "//evil.example/pwn", "https://evil.example", "/../etc"}) {
            String location = mvc.perform(post("/sim/requests/" + id + "/advance").session(hari)
                            .param("event", "APPROVE").param("actor", "FACULTY")
                            .param("back", hostile))
                    .andReturn().getResponse().getHeader("Location");
            assertThat(location)
                    .as("back=%s must not leave the application", hostile)
                    .doesNotContain("evil.example")
                    .isEqualTo("/requests");

            String location2 = mvc.perform(post("/actions/" + id).session(hari)
                            .param("event", "RESUBMIT").param("back", hostile))
                    .andReturn().getResponse().getHeader("Location");
            assertThat(location2).doesNotContain("evil.example").isEqualTo("/requests");
        }
    }

    @Test
    @DisplayName("a legitimate back target is still honoured")
    void legitimateBackTargetPreserved() throws Exception {
        MockHttpSession hari = sessionFor(HARI);
        String id = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", HARI).get(0).id;

        String location = mvc.perform(post("/sim/requests/" + id + "/advance").session(hari)
                        .param("event", "APPROVE").param("actor", "FACULTY").param("back", "/leave"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isEqualTo("/leave");
    }

    /* ---- FINDING 4: security response headers (CWE-693) ---- */

    @Test
    @DisplayName("baseline security headers are set on every response")
    void securityHeadersPresent() throws Exception {
        MvcResult res = mvc.perform(get("/")).andReturn();
        assertThat(res.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(res.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(res.getResponse().getHeader("Content-Security-Policy"))
                .contains("frame-ancestors 'none'").contains("script-src 'none'");
    }

    /* ---- FINDING 5: internal exception type echoed to the client (CWE-209) ---- */

    @Test
    @DisplayName("a denied request does not echo the internal exception type")
    void deniedRequestDoesNotEchoExceptionClass() throws Exception {
        DocumentArtifact his = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", HARI)
                .get(0);
        MockHttpSession meera = sessionFor(MEERA);

        MvcResult res = mvc.perform(get("/documents/" + his.id + "/download").session(meera)).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(res.getResponse().getContentAsString())
                .doesNotContain("IllegalTransitionException")
                .doesNotContain("com.campusos");
    }

    /* ---- XSS regression lock (CWE-79) ---- */

    @Test
    @DisplayName("a script payload in a free-text field is rendered escaped, never live")
    void scriptPayloadIsEscaped() throws Exception {
        MockHttpSession hari = sessionFor(HARI);
        String payload = "<script>alert(1)</script>";

        mvc.perform(post("/grievance").session(hari)
                .param("category", "EXAM").param("subject", payload)
                .param("description", "xss regression probe"));

        String page = mvc.perform(get("/requests").session(hari))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).doesNotContain(payload);
        assertThat(page).contains("&lt;script&gt;");
    }
}
