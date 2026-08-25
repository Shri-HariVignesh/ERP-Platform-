package com.campusos.portal.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.engine.EngineTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * GATE 1 OF THE DEFINITION OF DONE — authentication.
 *
 * Staff routes require a login; the student experience and the public /verify page do not
 * change; and /sim — the last endpoint that took a client-supplied actor — is gone.
 */
@Tag("security")
class FacultyAuthTest extends EngineTestBase {

    private static final String[] STAFF_ROUTES = {
            "/faculty", "/faculty/tasks", "/faculty/students", "/faculty/leave",
            "/faculty/internship", "/faculty/attendance", "/faculty/marks",
            "/faculty/notifications"};

    @Test
    @DisplayName("every staff route refuses an anonymous visitor")
    void anonymousIsRefusedEverywhere() throws Exception {
        for (String route : STAFF_ROUTES) {
            MockHttpServletResponse res = mvc.perform(get(route)).andReturn().getResponse();
            assertThat(res.getStatus())
                    .as("GET %s must not be served to an anonymous visitor", route)
                    .isIn(302, 401, 403);
            if (res.getStatus() == 302) {
                assertThat(res.getHeader("Location")).as("GET %s", route).contains("/login");
            }
            assertThat(res.getContentAsString())
                    .as("GET %s must not leak page content while refusing", route)
                    .doesNotContain("Hari Prasad");
        }
    }

    @Test
    @DisplayName("an authenticated staff member is served the same routes")
    void authenticatedStaffIsServed() throws Exception {
        for (String route : STAFF_ROUTES) {
            assertThat(mvc.perform(get(route).with(user(principal("anjali.menon"))))
                    .andReturn().getResponse().getStatus())
                    .as("GET %s for a signed-in faculty member", route)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("the login form is public and does not say which half of the credentials failed")
    void loginPageIsPublicAndOpaque() throws Exception {
        assertThat(mvc.perform(get("/login")).andReturn().getResponse().getStatus()).isEqualTo(200);

        String page = mvc.perform(get("/login").param("error", ""))
                .andReturn().getResponse().getContentAsString();
        assertThat(page)
                .contains("Those credentials were not accepted.")
                .doesNotContain("Bad credentials")
                .doesNotContain("not found")
                .doesNotContain("UsernameNotFound");
    }

    @Test
    @DisplayName("a real login succeeds and a wrong password does not")
    void formLoginWorks() throws Exception {
        String ok = mvc.perform(post("/login").with(csrf())
                        .param("username", "anjali.menon").param("password", "campus123"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(ok).isEqualTo("/faculty");

        String bad = mvc.perform(post("/login").with(csrf())
                        .param("username", "anjali.menon").param("password", "wrong"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(bad).contains("/login").contains("error");
    }

    @Test
    @DisplayName("the password is stored as a BCrypt hash, never in the clear")
    void passwordsAreHashed() {
        String hash = staffUsers.findByUsername("anjali.menon").orElseThrow().passwordHash;
        assertThat(hash).startsWith("$2").doesNotContain("campus123");
        assertThat(hash.length()).isGreaterThan(50);
    }

    /* ---------------------------- /sim is retired ---------------------------- */

    @Test
    @DisplayName("/sim is a 404 in the default profile — no client may name its own actor")
    void simIsGoneInTheDefaultProfile() throws Exception {
        String id = requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc("t_snit", "s_hari")
                .get(0).id;

        assertThat(mvc.perform(post("/sim/requests/" + id + "/advance").with(csrf())
                        .param("event", "APPROVE").param("actor", "FACULTY"))
                .andReturn().getResponse().getStatus())
                .as("the demo hook must not exist").isEqualTo(404);

        assertThat(mvc.perform(post("/sim/requests/" + id + "/reject").with(csrf())
                        .param("event", "REJECT").param("actor", "FACULTY").param("reason", "x"))
                .andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("no reachable endpoint accepts an actor parameter any more")
    void noEndpointAcceptsAClientSuppliedActor() throws Exception {
        String id = freshLeaveFor("s_divya");

        // The actor parameter is simply not bound: Anjali is FACULTY, and passing HOD changes
        // nothing about what she is allowed to do.
        mvc.perform(post("/faculty/requests/" + id + "/act").with(csrf())
                .with(user(principal("anjali.menon")))
                .param("event", "APPROVE").param("actor", "HOD"));

        assertThat(historyActor(id))
                .as("the recorded actor comes from the identity, not from the form")
                .isEqualTo(com.campusos.portal.domain.Actor.FACULTY);
    }

    private com.campusos.portal.domain.Actor historyActor(String requestId) {
        var rows = histories.findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(
                requestId, "t_snit", "s_divya");
        return rows.get(rows.size() - 1).actor;
    }

    private String freshLeaveFor(String studentId) {
        var future = attendance.findByTenantIdAndStudentId("t_snit", studentId).stream()
                .filter(a -> a.status == com.campusos.portal.domain.AttendanceRecord.Status.SCHEDULED)
                .map(a -> a.date).sorted().toList();
        return machine.create(new com.campusos.portal.service.Scope("t_snit", studentId),
                com.campusos.portal.domain.RequestType.LEAVE,
                leave(future.get(0), future.get(1))).id;
    }
}
