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
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * RETIRED. This was the demo hook that let a reviewer move a request "as" a staff actor by
 * naming that actor in the POST body — the last endpoint in the application that accepted a
 * client-supplied Actor. The Faculty module replaced it: /faculty/requests/{id}/act takes an
 * event only and DERIVES the actor from the authenticated principal.
 *
 * It is kept, behind the `demo` profile, purely so the old behaviour can be reproduced side
 * by side. In the default profile this controller is not registered at all and every /sim
 * path is a 404 — asserted by SimRetirementTest.
 */
@Profile("demo")
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
                          Authentication auth, RedirectAttributes ra) {
        Scope s = scopes.current(auth);
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
                         Authentication auth, RedirectAttributes ra) {
        Scope s = scopes.current(auth);
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
