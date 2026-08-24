package com.campusos.portal.engine;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.Event;
import com.campusos.portal.domain.RequestState;
import com.campusos.portal.domain.SideEffect;
import java.util.List;
import java.util.function.Predicate;

/**
 * One edge of the matrix. `guard` is an extra predicate on top of (state, event, actor);
 * it is what lets SUBMITTED fork to DOCUMENT_GENERATED or APPROVAL without a second event.
 */
public record Transition(
        Event event,
        Actor actor,
        RequestState to,
        List<SideEffect> effects,
        Predicate<TransitionContext> guard,
        boolean requiresNote,
        String label,
        String tone,
        String inputLabel) {

    public static Transition of(Event e, Actor a, RequestState to, List<SideEffect> fx) {
        return new Transition(e, a, to, fx, c -> true, false, null, "pending", null);
    }

    public static Transition human(Event e, Actor a, RequestState to, List<SideEffect> fx,
                                   boolean requiresNote, String label, String tone) {
        return new Transition(e, a, to, fx, c -> true, requiresNote, label, tone, null);
    }

    public Transition guardedBy(Predicate<TransitionContext> g) {
        return new Transition(event, actor, to, effects, g, requiresNote, label, tone, inputLabel);
    }

    /** Declares that this edge needs one extra value from the student (rendered generically). */
    public Transition withInput(String inputLabel) {
        return new Transition(event, actor, to, effects, guard, requiresNote, label, tone, inputLabel);
    }
}
