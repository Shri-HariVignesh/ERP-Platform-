package com.campusos.portal.web;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.Event;
import com.campusos.portal.domain.Request;
import com.campusos.portal.engine.IllegalTransitionException;
import com.campusos.portal.payload.InternshipPayload;
import com.campusos.portal.payload.RequestPayload;
import com.campusos.portal.service.RequestService;
import com.campusos.portal.service.Scope;
import com.campusos.portal.service.ScopeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpSession;
import java.util.function.Consumer;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The single endpoint behind every conditional student action button, for every request type.
 * The button itself is declared on the edge in TransitionMatrix, so no template knows what
 * a student may do — it just renders card.studentAction if the matrix produced one.
 */
@Controller
public class StudentActionController {

    private static final Logger log = LoggerFactory.getLogger(StudentActionController.class);

    private final RequestService requests;
    private final ScopeResolver scopes;

    public StudentActionController(RequestService requests, ScopeResolver scopes) {
        this.requests = requests;
        this.scopes = scopes;
    }

    @PostMapping("/actions/{id}")
    public String act(@PathVariable String id,
                      @RequestParam Event event,
                      @RequestParam(required = false) String note,
                      @RequestParam(required = false) String input,
                      @RequestParam(defaultValue = "/requests") String back,
                      HttpSession session, RedirectAttributes ra) {
        Scope s = scopes.current(session);
        try {
            Request r = requests.raw(s, id);
            requests.transition(s, id, event, Actor.STUDENT, note, patchFor(r, input));
            ra.addFlashAttribute("flash", "Done — your request has moved on.");
        } catch (IllegalTransitionException e) {
            log.warn("student action rejected: {}", e.getMessage());
            ra.addFlashAttribute("error", "That is no longer available on this request.");
        }
        return "redirect:" + SafeRedirect.resolve(back);
    }

    /** The only per-type code path, and it lives in Java, not in a template. */
    private Consumer<RequestPayload> patchFor(Request r, String input) {
        if (input == null || input.isBlank()) return null;
        return switch (r.type) {
            case INTERNSHIP -> p -> {
                InternshipPayload ip = (InternshipPayload) p;
                ip.certificateRef = new InternshipPayload.CertificateRef(
                        input.trim(), "application/pdf", 312);
                ip.sys.returnCount++;
            };
            case LEAVE, DOCUMENT, GRIEVANCE -> null;
        };
    }
}
