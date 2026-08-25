package com.campusos.portal.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.security.StaffPrincipal;
import com.campusos.portal.security.StudentPrincipal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * STUDENT AUTHENTICATION — the student half of what the Faculty module did for staff.
 *
 * The property under test is one sentence: a student's Scope is their identity. There is no
 * parameter, form field or session attribute anywhere that can name a different student,
 * because the endpoint that used to do exactly that (/switch) no longer exists.
 */
@Tag("security")
class StudentAuthTest extends EngineTestBase {

    private static final String HARI = "s_hari";
    private static final String MEERA = "s_meera";

    private static final String[] STUDENT_GETS = {
            "/", "/home", "/requests", "/leave", "/internship",
            "/documents", "/academic", "/grievance"};

    /* --------------------------- anonymous is refused --------------------------- */

    @Test
    @DisplayName("every student route refuses an anonymous visitor and sends them to the login form")
    void anonymousIsRefusedOnEveryStudentRoute() throws Exception {
        for (String route : STUDENT_GETS) {
            MockHttpServletResponse res = mvc.perform(get(route)).andReturn().getResponse();
            assertThat(res.getStatus())
                    .as("GET %s must not be served to an anonymous visitor", route)
                    .isIn(302, 401, 403);
            if (res.getStatus() == 302) {
                assertThat(res.getHeader("Location")).as("GET %s", route).contains("/login");
            }
            assertThat(res.getContentAsString())
                    .as("GET %s must not leak page content while refusing", route)
                    .doesNotContain("Hari Prasad").doesNotContain("SNIT21CS042");
        }
    }

    @Test
    @DisplayName("the student POSTs refuse an anonymous visitor too")
    void anonymousIsRefusedOnEveryStudentPost() throws Exception {
        for (String route : new String[] {"/leave", "/internship", "/documents", "/grievance"}) {
            assertThat(mvc.perform(post(route).with(csrf())).andReturn().getResponse().getStatus())
                    .as("POST %s", route).isIn(302, 401, 403);
        }
        assertThat(mvc.perform(post("/actions/req_x").with(csrf()).param("event", "RESUBMIT"))
                .andReturn().getResponse().getStatus()).isIn(302, 401, 403);
    }

    @Test
    @DisplayName("an anonymous document download is refused before any scope check runs")
    void anonymousDownloadIsRefused() throws Exception {
        var doc = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", HARI).get(0);
        MockHttpServletResponse res = mvc.perform(get("/documents/" + doc.id + "/download"))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isIn(302, 401, 403);
        assertThat(res.getContentAsString()).doesNotContain(doc.serialNo);
    }

    /* ---------------------------- authenticated works ---------------------------- */

    @Test
    @DisplayName("an authenticated student is served every one of their own routes")
    void authenticatedStudentIsServed() throws Exception {
        for (String route : STUDENT_GETS) {
            assertThat(mvc.perform(get(route).with(user(studentPrincipal(HARI))))
                    .andReturn().getResponse().getStatus())
                    .as("GET %s for a signed-in student", route).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("the page a student is served is their own data, and only their own")
    void studentSeesOnlyTheirOwnData() throws Exception {
        String page = mvc.perform(get("/requests").with(user(studentPrincipal(HARI))))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Hari Prasad").contains("SNIT21CS042");
        assertThat(page)
                .as("a classmate in the same class and tenant is still not his business")
                .doesNotContain("Divya Rajan").doesNotContain("Meera Nair");
    }

    /**
     * THE POINT OF THE WHOLE CHANGE. Scope comes from the principal, so naming another
     * student in the request changes nothing at all — there is no parameter that reads it.
     */
    @Test
    @DisplayName("passing another studentId does not move a student's scope")
    void aStudentIdParameterIsIgnoredEverywhere() throws Exception {
        for (String attempt : new String[] {
                "/requests?studentId=s_divya", "/academic?studentId=s_meera",
                "/requests?tenantId=t_ace", "/home?studentId=s_divya&tenantId=t_ace"}) {
            String page = mvc.perform(get(attempt).with(user(studentPrincipal(HARI))))
                    .andReturn().getResponse().getContentAsString();
            assertThat(page).as("%s", attempt)
                    .contains("Hari Prasad")
                    .doesNotContain("Divya Rajan")
                    .doesNotContain("Meera Nair");
        }
    }

    @Test
    @DisplayName("a student cannot download another student's document, in or out of tenant")
    void crossStudentAndCrossTenantDownloadRefused() throws Exception {
        var his = documents.findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", HARI).get(0);

        MockHttpServletResponse sameTenant = mvc.perform(
                get("/documents/" + his.id + "/download").with(user(studentPrincipal("s_divya"))))
                .andReturn().getResponse();
        MockHttpServletResponse otherTenant = mvc.perform(
                get("/documents/" + his.id + "/download").with(user(studentPrincipal(MEERA))))
                .andReturn().getResponse();

        for (MockHttpServletResponse res : List.of(sameTenant, otherTenant)) {
            assertThat(res.getStatus()).isNotEqualTo(200);
            assertThat(res.getContentAsString())
                    .doesNotContain(his.serialNo).doesNotContain("Hari Prasad");
        }
    }

    /* ------------------------- the two halves stay apart ------------------------- */

    @Test
    @DisplayName("a student principal is refused by every staff route")
    void studentCannotReachTheFacultyPortal() throws Exception {
        for (String route : new String[] {
                "/faculty", "/faculty/tasks", "/faculty/students", "/faculty/attendance",
                "/faculty/marks", "/faculty/notifications"}) {
            MockHttpServletResponse res = mvc.perform(get(route).with(user(studentPrincipal(HARI))))
                    .andReturn().getResponse();
            assertThat(res.getStatus())
                    .as("GET %s as a STUDENT must be forbidden, not merely unauthenticated", route)
                    .isEqualTo(403);
            assertThat(res.getContentAsString()).doesNotContain("Divya Rajan");
        }
        assertThat(mvc.perform(post("/faculty/requests/req_x/act").with(csrf())
                        .with(user(studentPrincipal(HARI))).param("event", "APPROVE"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("a staff principal is refused by the student routes")
    void staffCannotActAsAStudent() throws Exception {
        for (String route : new String[] {"/", "/requests", "/academic"}) {
            assertThat(mvc.perform(get(route).with(user(principal("anjali.menon"))))
                    .andReturn().getResponse().getStatus())
                    .as("GET %s as staff", route).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("no principal carries both a student and a staff authority")
    void principalsAreOneKindOrTheOther() {
        StudentPrincipal student = studentPrincipal(HARI);
        StaffPrincipal staff = principal("krishnakumar");   // the multi-role staff member

        assertThat(student.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_STUDENT");
        assertThat(staff.getAuthorities()).extracting(Object::toString)
                .doesNotContain("ROLE_STUDENT");
    }

    /**
     * PortalUserDetailsService resolves staff BEFORE students, so a username in both tables
     * would silently shadow the student. This is the assertion that makes that order safe.
     */
    @Test
    @DisplayName("staff and student usernames are disjoint")
    void usernamesDoNotCollide() {
        Set<String> staffNames = new HashSet<>();
        for (String u : new String[] {"anjali.menon", "suresh.kumar", "krishnakumar",
                                      "suresh.babu", "registrar.snit", "exam.office",
                                      "latha.iyer", "office.ace"}) {
            staffNames.add(u);
            assertThat(staffUsers.findByUsername(u)).as("seeded staff %s", u).isPresent();
            assertThat(studentAccounts.findByUsername(u))
                    .as("%s must not also be a student login", u).isEmpty();
        }
        for (String studentId : new String[] {HARI, "s_divya", "s_nikhil", MEERA}) {
            String username = studentPrincipal(studentId).getUsername();
            assertThat(staffNames).doesNotContain(username);
            assertThat(staffUsers.findByUsername(username))
                    .as("student login %s must not also be a staff username", username).isEmpty();
        }
    }

    /* ------------------------------ login routing ------------------------------ */

    @Test
    @DisplayName("login routes a student to /home and a staff member to /faculty")
    void loginRoutesByAccountType() throws Exception {
        String studentUsername = studentPrincipal(HARI).getUsername();

        assertThat(mvc.perform(post("/login").with(csrf())
                        .param("username", studentUsername).param("password", "campus123"))
                .andReturn().getResponse().getHeader("Location"))
                .as("a student lands on their own portal").isEqualTo("/home");

        assertThat(mvc.perform(post("/login").with(csrf())
                        .param("username", "anjali.menon").param("password", "campus123"))
                .andReturn().getResponse().getHeader("Location"))
                .as("a staff member lands on theirs").isEqualTo("/faculty");
    }

    @Test
    @DisplayName("a wrong student password is refused, with the same message as a wrong username")
    void badStudentCredentialsRefused() throws Exception {
        String username = studentPrincipal(HARI).getUsername();
        assertThat(mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", "wrong"))
                .andReturn().getResponse().getHeader("Location")).contains("error");
        assertThat(mvc.perform(post("/login").with(csrf())
                        .param("username", "no.such.person").param("password", "campus123"))
                .andReturn().getResponse().getHeader("Location")).contains("error");
    }

    @Test
    @DisplayName("student passwords are stored as BCrypt hashes")
    void studentPasswordsAreHashed() {
        var account = studentAccounts.findByTenantIdAndStudentId("t_snit", HARI).orElseThrow();
        assertThat(account.passwordHash).startsWith("$2").doesNotContain("campus123");
    }

    /* ------------------------------ /switch retired ------------------------------ */

    @Test
    @DisplayName("/switch is a 404 — the client can no longer choose whose data it sees")
    void switchIsRetired() throws Exception {
        assertThat(mvc.perform(post("/switch").with(csrf()).param("studentId", "s_divya"))
                .andReturn().getResponse().getStatus())
                .as("the identity switcher must not exist").isEqualTo(404);

        assertThat(mvc.perform(post("/switch").with(csrf())
                        .with(user(studentPrincipal(HARI))).param("studentId", "s_divya"))
                .andReturn().getResponse().getStatus())
                .as("not even for an authenticated student").isEqualTo(404);

        assertThat(mvc.perform(get("/requests").with(user(studentPrincipal(HARI))))
                .andReturn().getResponse().getContentAsString())
                .as("and the switcher control is gone from the page")
                .doesNotContain("/switch")
                .doesNotContain("name=\"studentId\"");
    }

    @Test
    @DisplayName("an unmapped path is refused by the deny-by-default rule")
    void unmappedPathsAreDeniedByDefault() throws Exception {
        for (String path : new String[] {"/admin", "/api/students", "/internal/metrics"}) {
            assertThat(mvc.perform(get(path).with(user(studentPrincipal(HARI))))
                    .andReturn().getResponse().getStatus())
                    .as("%s is in no rule, so it must not be served", path)
                    .isIn(403, 404);
        }
    }
}
