package com.campusos.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Each test here fails on the vulnerable code and passes after its fix. They EXTEND the
 * existing suite; nothing in the original 63 was changed or weakened.
 */
@Tag("security")
class SecurityRegressionTest extends EngineTestBase {

    private static final String HARI = "s_hari";
    private static final String MEERA = "s_meera";
    private static final String DIVYA = "s_divya";

    /** Was POST /switch; now a real authenticated student. Assertions unchanged. */
    private RequestPostProcessor as(String studentId) {
        return user(studentPrincipal(studentId));
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
        RequestPostProcessor hari = as(HARI);
        long before = countDocRequests("t_snit", HARI, DocType.INTERNSHIP_VERIFICATION);

        mvc.perform(post("/documents").with(csrf()).with(hari)
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
        RequestPostProcessor hari = as(HARI);
        long before = countDocRequests("t_snit", HARI, DocType.BONAFIDE);

        mvc.perform(post("/documents").with(csrf()).with(hari)
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
        assertThat(mvc.perform(get("/verify/" + doc.verifyId).with(as(MEERA)))
                .andReturn().getResponse().getStatus())
                .as("a different tenant must NOT be blocked; this is the QR target").isEqualTo(200);
    }

    /* ---- FINDING 7: open redirect via the `back` parameter (CWE-601) ---- */

    /**
     * REPOINTED FROM /sim, which is retired. The open-redirect property is unchanged and is
     * now asserted on BOTH live surfaces that still take a `back` parameter: the student
     * action endpoint, and the staff action endpoint that replaced the demo hook.
     */
    @Test
    @DisplayName("the back parameter cannot bounce a student to an external site")
    void backParameterCannotOpenRedirect() throws Exception {
        RequestPostProcessor hari = as(HARI);
        String id = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", HARI)
                .get(0).id;

        for (String hostile : new String[] {
                "http://evil.example/pwn", "//evil.example/pwn", "https://evil.example", "/../etc"}) {
            String location = mvc.perform(post("/actions/" + id).with(csrf()).with(hari)
                            .param("event", "RESUBMIT").param("back", hostile))
                    .andReturn().getResponse().getHeader("Location");
            assertThat(location)
                    .as("back=%s must not leave the application", hostile)
                    .doesNotContain("evil.example")
                    .isEqualTo("/requests");
        }
    }

    @Test
    @DisplayName("the staff back parameter cannot bounce a faculty member off-site either")
    void staffBackParameterCannotOpenRedirect() throws Exception {
        for (String hostile : new String[] {
                "http://evil.example/pwn", "//evil.example/pwn", "https://evil.example", "/../etc"}) {
            // A fresh request per probe: the first APPROVE really moves it, and a spent
            // request would then be refused for the wrong reason.
            String location = mvc.perform(post("/faculty/requests/" + freshPendingLeave() + "/act")
                            .with(csrf()).with(user(principal("anjali.menon")))
                            .param("event", "APPROVE").param("back", hostile))
                    .andReturn().getResponse().getHeader("Location");
            assertThat(location)
                    .as("back=%s must not leave the application", hostile)
                    .doesNotContain("evil.example")
                    .isEqualTo("/faculty/tasks");
        }
    }

    /** ADDED per the freeze conditions: the replacement endpoint refuses anonymous callers. */
    @Test
    @DisplayName("the staff action endpoint is not reachable without a login")
    void staffActionRequiresAuthentication() throws Exception {
        String id = freshPendingLeave();
        var res = mvc.perform(post("/faculty/requests/" + id + "/act").with(csrf())
                .param("event", "APPROVE").param("back", "/faculty/tasks")).andReturn().getResponse();

        assertThat(res.getStatus()).isIn(302, 401, 403);
        if (res.getStatus() == 302) assertThat(res.getHeader("Location")).contains("/login");
        assertThat(requests.findByIdAndTenantIdAndStudentId(id, "t_snit", DIVYA).orElseThrow().state)
                .as("an anonymous POST changed nothing")
                .isEqualTo(com.campusos.portal.domain.RequestState.FACULTY_PENDING);
    }

    @Test
    @DisplayName("a legitimate back target is still honoured, on both surfaces")
    void legitimateBackTargetPreserved() throws Exception {
        RequestPostProcessor hari = as(HARI);
        String studentRequest = requests
                .findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", HARI).get(0).id;

        assertThat(mvc.perform(post("/actions/" + studentRequest).with(csrf()).with(hari)
                        .param("event", "RESUBMIT").param("back", "/leave"))
                .andReturn().getResponse().getHeader("Location")).isEqualTo("/leave");

        assertThat(mvc.perform(post("/faculty/requests/" + freshPendingLeave() + "/act").with(csrf())
                        .with(user(principal("anjali.menon")))
                        .param("event", "APPROVE").param("back", "/faculty/leave"))
                .andReturn().getResponse().getHeader("Location")).isEqualTo("/faculty/leave");
    }

    /**
     * A brand-new leave request for Divya — a student of Anjali's own class — sitting at
     * FACULTY_PENDING. Created through the real engine, so it is a genuine inbox item.
     */
    private String freshPendingLeave() {
        var future = attendance.findByTenantIdAndStudentId("t_snit", DIVYA).stream()
                .filter(a -> a.status == com.campusos.portal.domain.AttendanceRecord.Status.SCHEDULED)
                .map(a -> a.date).sorted().toList();
        var r = machine.create(new com.campusos.portal.service.Scope("t_snit", DIVYA),
                RequestType.LEAVE, leave(future.get(0), future.get(1)));
        assertThat(r.state).isEqualTo(com.campusos.portal.domain.RequestState.FACULTY_PENDING);
        return r.id;
    }

    /* ---- FINDING 4: security response headers (CWE-693) ---- */

    @Test
    @DisplayName("baseline security headers are set on every response, refusals included")
    void securityHeadersPresent() throws Exception {
        // A refusal is a response too. Spring Security writes the redirect to /login from
        // inside its own filter chain and never calls the rest of the chain, so if the header
        // filter is ordered after it, every 302 and every 403 goes out bare. Assert the
        // anonymous refusal FIRST — it is the case that regressed.
        MvcResult refused = mvc.perform(get("/")).andReturn();
        assertThat(refused.getResponse().getStatus()).isIn(302, 401, 403);
        assertThat(refused.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(refused.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(refused.getResponse().getHeader("Content-Security-Policy"))
                .contains("frame-ancestors 'none'");

        MvcResult res = mvc.perform(get("/").with(as(HARI))).andReturn();
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
        RequestPostProcessor meera = as(MEERA);

        MvcResult res = mvc.perform(get("/documents/" + his.id + "/download").with(meera)).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(res.getResponse().getContentAsString())
                .doesNotContain("IllegalTransitionException")
                .doesNotContain("com.campusos");
    }

    /* ---- XSS regression lock (CWE-79) ---- */

    @Test
    @DisplayName("a script payload in a free-text field is rendered escaped, never live")
    void scriptPayloadIsEscaped() throws Exception {
        RequestPostProcessor hari = as(HARI);
        String payload = "<script>alert(1)</script>";

        mvc.perform(post("/grievance").with(csrf()).with(hari)
                .param("category", "EXAM").param("subject", payload)
                .param("description", "xss regression probe"));

        String page = mvc.perform(get("/requests").with(hari))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).doesNotContain(payload);
        assertThat(page).contains("&lt;script&gt;");
    }
}
