package com.campusos.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.campusos.portal.domain.DocumentArtifact;
import com.campusos.portal.domain.Verification;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.repo.StudentRepository;
import com.campusos.portal.repo.TenantRepository;
import com.campusos.portal.repo.VerificationRepository;
import com.campusos.portal.service.QrService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.ModelAndView;

/**
 * FINDING: the QR encoded a hardcoded http://localhost:8080, so a scanned certificate was
 * unverifiable anywhere except the machine that rendered it. The origin is now the
 * `app.base-url` property.
 *
 * A QR is only as good as the URL inside it, so these assert the encoded TARGET — the exact
 * string handed to QrService — not merely that a QR was drawn.
 */
class VerifyLinkTest extends EngineTestBase {

    private static final String ID = "SNIT-2026-TEST01";

    @Value("${app.base-url}")
    private String configuredBaseUrl;

    /**
     * Builds the controller directly rather than booting a second Spring context with an
     * overridden property — the suite deliberately shares one context, and a unit call proves
     * the same thing: whatever origin is injected is the origin the QR carries.
     */
    private String qrTargetFor(String baseUrl) {
        VerificationRepository verifications = mock(VerificationRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        QrService qr = mock(QrService.class);

        Verification v = new Verification();
        v.verifyId = ID;
        v.tenantId = "t_x";
        v.studentId = "s_x";
        v.kind = "DOCUMENT";
        v.subject = "Bonafide Certificate";
        when(verifications.findByVerifyId(ID)).thenReturn(Optional.of(v));
        when(tenants.findById("t_x")).thenReturn(Optional.empty());
        when(students.findByIdAndTenantId("s_x", "t_x")).thenReturn(Optional.empty());

        new VerifyController(verifications, tenants, students, qr, baseUrl)
                .verify(ID, new ConcurrentModel(), new MockHttpServletResponse());

        ArgumentCaptor<String> target = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(qr).svg(target.capture(), anyInt());
        return target.getValue();
    }

    @Test
    @DisplayName("the QR encodes the configured origin, not a hardcoded localhost")
    void qrUsesTheConfiguredBaseUrl() {
        assertThat(qrTargetFor("https://erp.snit.edu.in"))
                .isEqualTo("https://erp.snit.edu.in/verify/" + ID)
                .doesNotContain("localhost");
    }

    @Test
    @DisplayName("a base-url with a trailing slash does not produce a double slash")
    void trailingSlashIsNormalised() {
        assertThat(qrTargetFor("https://erp.snit.edu.in/"))
                .isEqualTo("https://erp.snit.edu.in/verify/" + ID);
    }

    @Test
    @DisplayName("the wired page builds its verify link from the property, whatever it is set to")
    void wiredPageUsesTheProperty() throws Exception {
        DocumentArtifact doc = documents
                .findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", "s_hari")
                .stream().filter(d -> d.verifyId != null).findFirst().orElseThrow();

        ModelAndView mv = mvc.perform(get("/verify/" + doc.verifyId))
                .andReturn().getModelAndView();

        assertThat(configuredBaseUrl).as("the property must actually be declared").isNotBlank();
        assertThat(mv).isNotNull();
        assertThat(mv.getModel().get("verifyUrl"))
                .isEqualTo(configuredBaseUrl + "/verify/" + doc.verifyId);
    }

    @Test
    @DisplayName("the page prints the verify link for a verifier who cannot scan")
    void pageShowsTheLink() throws Exception {
        DocumentArtifact doc = documents
                .findByTenantIdAndStudentIdOrderByIssuedAtDesc("t_snit", "s_hari")
                .stream().filter(d -> d.verifyId != null).findFirst().orElseThrow();

        String page = mvc.perform(get("/verify/" + doc.verifyId))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains(configuredBaseUrl + "/verify/" + doc.verifyId);
    }
}
