package com.campusos.portal.web;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.Event;
import com.campusos.portal.engine.IllegalTransitionException;
import com.campusos.portal.service.RequestService;
import com.campusos.portal.service.Scope;
import com.campusos.portal.service.ScopeResolver;
import com.campusos.portal.view.DisplayLabels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * DEMO HOOK — not a staff UI (staff UI is a declared non-goal).
 *
 * These endpoints exist only so a reviewer can move a request the way a Faculty/HOD/
 * Institution/Office actor would. They call the SAME RequestStateMachine.transition()
 * guard as everything else, so an illegal event/actor combination is rejected here too —
 * try POST /sim/requests/{id}/advance?event=APPROVE&actor=STUDENT and it will 400.
 */
@Controller
@RequestMapping("/sim")
public class SimController {

    private static final Logger log = LoggerFactory.getLogger(SimController.class);

    private final RequestService requests;
    private final ScopeResolver scopes;

    public SimController(RequestService requests, ScopeResolver scopes) {
        this.requests = requests;
        this.scopes = scopes;
    }

    @PostMapping("/requests/{id}/advance")
    public String advance(@PathVariable String id,
                          @RequestParam Event event,
                          @RequestParam Actor actor,
                          @RequestParam(required = false) String note,
                          @RequestParam(defaultValue = "/requests") String back,
                          HttpSession session, RedirectAttributes ra) {
        Scope s = scopes.current(session);
        try {
            requests.transition(s, id, event, actor, note);
            ra.addFlashAttribute("flash",
                    "[demo hook] " + actor.display() + " — " + DisplayLabels.event(event) + ".");
        } catch (IllegalTransitionException e) {
            log.warn("[demo hook] guard rejected: {}", e.getMessage());
            ra.addFlashAttribute("error", "That move is not allowed from this stage.");
        }
        return "redirect:" + SafeRedirect.resolve(back);
    }

    @PostMapping("/requests/{id}/reject")
    public String reject(@PathVariable String id,
                         @RequestParam Actor actor,
                         @RequestParam Event event,
                         @RequestParam String reason,
                         @RequestParam(defaultValue = "/requests") String back,
                         HttpSession session, RedirectAttributes ra) {
        Scope s = scopes.current(session);
        try {
            requests.transition(s, id, event, actor, reason);
            ra.addFlashAttribute("flash",
                    "[demo hook] " + actor.display() + " sent it back with a reason.");
        } catch (IllegalTransitionException e) {
            log.warn("[demo hook] guard rejected: {}", e.getMessage());
            ra.addFlashAttribute("error", "That move is not allowed from this stage.");
        }
        return "redirect:" + SafeRedirect.resolve(back);
    }
}
