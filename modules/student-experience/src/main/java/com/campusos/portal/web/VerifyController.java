package com.campusos.portal.web;

import com.campusos.portal.domain.Verification;
import com.campusos.portal.repo.StudentRepository;
import com.campusos.portal.repo.TenantRepository;
import com.campusos.portal.repo.VerificationRepository;
import com.campusos.portal.view.DisplayLabels;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.campusos.portal.service.QrService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The QR target. NOT one of the 7 student views — it is the public page an employer lands
 * on after scanning a certificate, and without it the generated QR would point nowhere.
 * Deliberately unscoped: the unguessable verifyId is the capability.
 */
@Controller
public class VerifyController {

    private static final DateTimeFormatter ISSUED =
            DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneId.systemDefault());

    private final VerificationRepository verifications;
    private final TenantRepository tenants;
    private final StudentRepository students;
    private final QrService qr;

    public VerifyController(VerificationRepository verifications, TenantRepository tenants,
                            StudentRepository students, QrService qr) {
        this.verifications = verifications;
        this.tenants = tenants;
        this.students = students;
        this.qr = qr;
    }

    @GetMapping("/verify/{verifyId}")
    public String verify(@PathVariable String verifyId, Model model, HttpServletResponse response) {
        Verification v = verifications.findByVerifyId(verifyId).orElse(null);
        model.addAttribute("verifyId", verifyId);
        model.addAttribute("v", v);
        if (v == null) response.setStatus(HttpStatus.NOT_FOUND.value());
        if (v != null) {
            // Exactly the fields a verifier needs: who issued it, what it is, who holds it,
            // the id, and when. No roll number, no serial, no internal identifiers.
            model.addAttribute("tenant", tenants.findById(v.tenantId).orElse(null));
            model.addAttribute("holder", students.findByIdAndTenantId(v.studentId, v.tenantId)
                    .map(s -> s.name).orElse("—"));
            model.addAttribute("credential", DisplayLabels.credentialKind(v.kind));
            model.addAttribute("issuedOn", ISSUED.format(v.issuedAt));
            model.addAttribute("qr", qr.svg("http://localhost:8080/verify/" + verifyId, 132));
        }
        return "verify";
    }
}
