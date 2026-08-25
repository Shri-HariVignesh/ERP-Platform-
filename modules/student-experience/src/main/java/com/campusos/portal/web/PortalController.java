package com.campusos.portal.web;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.IllegalTransitionException;
import com.campusos.portal.repo.DocumentRepository;
import com.campusos.portal.service.*;
import com.campusos.portal.view.RequestCard;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PortalController {

    /**
     * SECURITY: the only document types a student may request. DocType also contains
     * INTERNSHIP_VERIFICATION, which the SYSTEM mints when an internship is verified —
     * a student POSTing it directly would have a real verification ID and QR issued for an
     * internship that never happened. The dropdown alone is not a control; this is.
     */
    static final List<DocType> STUDENT_REQUESTABLE = List.of(
            DocType.BONAFIDE, DocType.HALL_TICKET, DocType.FEE_RECEIPT,
            DocType.TRANSCRIPT, DocType.CONDUCT_CERTIFICATE);

    private final RequestService requests;
    private final AcademicService academic;
    private final ScopeResolver scopes;
    private final DocumentRepository documents;

    public PortalController(RequestService requests, AcademicService academic, ScopeResolver scopes,
                            DocumentRepository documents) {
        this.requests = requests;
        this.academic = academic;
        this.scopes = scopes;
        this.documents = documents;
    }

    /* ------------------------------- shared ------------------------------- */

    /**
     * Every handler starts here, and every handler gets its Scope from the AUTHENTICATED
     * PRINCIPAL. There is no parameter, header or session attribute anywhere in this
     * controller that can name a different student.
     */
    private Scope base(Model model, Authentication auth, String nav) {
        Scope s = scopes.current(auth);
        model.addAttribute("student", requests.student(s));
        model.addAttribute("tenant", requests.tenant(s));
        model.addAttribute("nav", nav);
        model.addAttribute("attendancePct", academic.attendancePct(s));
        return s;
    }

    /* -------------------------------- 1. HOME -------------------------------- */

    @GetMapping({"/", "/home"})
    public String home(Model model, Authentication auth) {
        Scope s = base(model, auth, "home");
        List<RequestCard> all = requests.all(s);
        model.addAttribute("recent", all.stream().limit(5).toList());
        model.addAttribute("openCount", all.stream().filter(RequestCard::isOpen).count());
        model.addAttribute("banner", all.stream()
                .filter(c -> c.studentAction() != null).findFirst()
                .orElse(all.stream().filter(RequestCard::isOpen).findFirst().orElse(null)));
        return "home";
    }

    /* ----------------------------- 2. MY REQUESTS ----------------------------- */

    @GetMapping("/requests")
    public String allRequests(@RequestParam(required = false) String filter,
                              Model model, Authentication auth) {
        Scope s = base(model, auth, "requests");
        List<RequestCard> cards = requests.all(s);
        if (filter != null && !filter.isBlank() && !"ALL".equals(filter)) {
            cards = cards.stream().filter(c -> c.type().equals(filter)).toList();
        }
        model.addAttribute("cards", cards);
        model.addAttribute("filter", filter == null ? "ALL" : filter);
        model.addAttribute("types", RequestType.values());
        return "requests";
    }

    /* -------------------------------- 3. LEAVE -------------------------------- */

    @GetMapping("/leave")
    public String leave(Model model, Authentication auth) {
        Scope s = base(model, auth, "leave");
        if (!model.containsAttribute("form")) model.addAttribute("form", new Forms.LeaveForm());
        model.addAttribute("cards", requests.ofType(s, RequestType.LEAVE));
        model.addAttribute("leaveTypes", com.campusos.portal.payload.LeavePayload.LeaveType.values());
        return "leave";
    }

    @PostMapping("/leave")
    public String submitLeave(@Valid @ModelAttribute("form") Forms.LeaveForm form, BindingResult binding,
                              Model model, Authentication auth, RedirectAttributes ra) {
        Scope s = scopes.current(auth);
        if (!binding.hasErrors()) {
            try {
                requests.create(s, RequestType.LEAVE, form.toPayload());
                ra.addFlashAttribute("flash", "Leave request submitted and auto-validated.");
                return "redirect:/leave";
            } catch (IllegalArgumentException e) {
                binding.reject("payload", e.getMessage());
            }
        }
        base(model, auth, "leave");
        model.addAttribute("cards", requests.ofType(s, RequestType.LEAVE));
        model.addAttribute("leaveTypes", com.campusos.portal.payload.LeavePayload.LeaveType.values());
        return "leave";
    }

    /* ----------------------------- 4. INTERNSHIP ----------------------------- */

    @GetMapping("/internship")
    public String internship(Model model, Authentication auth) {
        Scope s = base(model, auth, "internship");
        if (!model.containsAttribute("form")) model.addAttribute("form", new Forms.InternshipForm());
        model.addAttribute("cards", requests.ofType(s, RequestType.INTERNSHIP));
        return "internship";
    }

    @PostMapping("/internship")
    public String submitInternship(@Valid @ModelAttribute("form") Forms.InternshipForm form,
                                   BindingResult binding, Model model, Authentication auth,
                                   RedirectAttributes ra) {
        Scope s = scopes.current(auth);
        if (!binding.hasErrors()) {
            try {
                requests.create(s, RequestType.INTERNSHIP, form.toPayload());
                ra.addFlashAttribute("flash", "Internship submitted; certificate auto-checked.");
                return "redirect:/internship";
            } catch (IllegalArgumentException e) {
                binding.reject("payload", e.getMessage());
            }
        }
        base(model, auth, "internship");
        model.addAttribute("cards", requests.ofType(s, RequestType.INTERNSHIP));
        return "internship";
    }

    /* ------------------------------ 5. DOCUMENTS ------------------------------ */

    @GetMapping("/documents")
    public String documents(Model model, Authentication auth) {
        Scope s = base(model, auth, "documents");
        if (!model.containsAttribute("form")) model.addAttribute("form", new Forms.DocumentForm());
        model.addAttribute("cards", requests.ofType(s, RequestType.DOCUMENT));
        model.addAttribute("issued", academic.documents(s));
        model.addAttribute("docTypes", STUDENT_REQUESTABLE);
        return "documents";
    }

    @PostMapping("/documents")
    public String submitDocument(@Valid @ModelAttribute("form") Forms.DocumentForm form,
                                 BindingResult binding, Model model, Authentication auth,
                                 RedirectAttributes ra) {
        Scope s = scopes.current(auth);
        if (!binding.hasErrors() && !STUDENT_REQUESTABLE.contains(form.getDocType())) {
            binding.reject("docType", "That document is not one a student can request.");
        }
        if (!binding.hasErrors()) {
            try {
                requests.create(s, RequestType.DOCUMENT, form.toPayload());
                ra.addFlashAttribute("flash", "Document request submitted.");
                return "redirect:/documents";
            } catch (IllegalArgumentException e) {
                binding.reject("payload", e.getMessage());
            }
        }
        base(model, auth, "documents");
        model.addAttribute("cards", requests.ofType(s, RequestType.DOCUMENT));
        model.addAttribute("issued", academic.documents(s));
        model.addAttribute("docTypes", List.of(DocType.BONAFIDE, DocType.HALL_TICKET,
                DocType.FEE_RECEIPT, DocType.TRANSCRIPT, DocType.CONDUCT_CERTIFICATE));
        return "documents";
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<String> download(@PathVariable Long id, Authentication auth) {
        Scope s = scopes.current(auth);
        DocumentArtifact d = documents.findByIdAndTenantIdAndStudentId(id, s.tenantId(), s.studentId())
                .orElseThrow(() -> new IllegalTransitionException("document not visible in scope"));
        String file = """
            <!doctype html><meta charset="utf-8"><title>%s</title>
            <style>body{font:15px/1.6 system-ui;margin:40px;color:#111}
            .doc{max-width:640px;border:1px solid #ccc;padding:32px}
            dl{display:grid;grid-template-columns:auto 1fr;gap:4px 16px}
            dl div{display:contents}dt{color:#666}footer{margin-top:24px;border-top:1px solid #eee;
            padding-top:12px;display:grid;gap:4px}footer span{color:#666;margin-right:8px}
            .sig{color:#666;font-size:13px}</style>%s""".formatted(d.title, d.html);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + d.serialNo.replace('/', '-') + ".html\"")
                .contentType(MediaType.TEXT_HTML)
                .body(file);
    }

    /* ------------------------------- 6. ACADEMIC ------------------------------- */

    @GetMapping("/academic")
    public String academicView(Model model, Authentication auth) {
        Scope s = base(model, auth, "academic");
        model.addAttribute("results", academic.results(s));
        model.addAttribute("cgpa", academic.cgpa(s));
        // Finalized subject marks only — this is where faculty authoring becomes visible.
        model.addAttribute("marks", academic.publishedMarks(s));
        model.addAttribute("records", academic.records(s));
        model.addAttribute("approvedLeaveDays", academic.approvedLeaveDays(s));
        model.addAttribute("term", academic.currentTerm(s.tenantId()));
        model.addAttribute("hallTicket", academic.latestHallTicket(s));
        return "academic";
    }

    /* ------------------------------- 7. GRIEVANCE ------------------------------- */

    @GetMapping("/grievance")
    public String grievance(Model model, Authentication auth) {
        Scope s = base(model, auth, "grievance");
        if (!model.containsAttribute("form")) model.addAttribute("form", new Forms.GrievanceForm());
        model.addAttribute("cards", requests.ofType(s, RequestType.GRIEVANCE));
        model.addAttribute("categories", com.campusos.portal.payload.GrievancePayload.Category.values());
        return "grievance";
    }

    @PostMapping("/grievance")
    public String submitGrievance(@Valid @ModelAttribute("form") Forms.GrievanceForm form,
                                  BindingResult binding, Model model, Authentication auth,
                                  RedirectAttributes ra) {
        Scope s = scopes.current(auth);
        if (!binding.hasErrors()) {
            requests.create(s, RequestType.GRIEVANCE, form.toPayload());
            ra.addFlashAttribute("flash", "Grievance submitted and auto-assigned.");
            return "redirect:/grievance";
        }
        base(model, auth, "grievance");
        model.addAttribute("cards", requests.ofType(s, RequestType.GRIEVANCE));
        model.addAttribute("categories", com.campusos.portal.payload.GrievancePayload.Category.values());
        return "grievance";
    }
}
