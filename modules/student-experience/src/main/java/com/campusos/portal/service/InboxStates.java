package com.campusos.portal.service;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.RequestState;
import com.campusos.portal.domain.RequestType;
import com.campusos.portal.engine.Transition;
import com.campusos.portal.engine.TransitionMatrix;
import com.campusos.portal.engine.WorkflowSpec;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * WHICH STATES ARE WAITING ON WHICH ACTOR — derived from TransitionMatrix at class-init,
 * never hand-written. If an edge is added, moved or removed in the matrix, the inbox follows
 * it on the next start-up. A hand-maintained list here would be a second source of truth for
 * the workflow, which is exactly what the engine exists to prevent.
 */
public final class InboxStates {

    private InboxStates() {}

    private static final Map<Actor, Set<RequestState>> BY_ACTOR = build();

    private static Map<Actor, Set<RequestState>> build() {
        Map<Actor, Set<RequestState>> out = new EnumMap<>(Actor.class);
        for (RequestType type : RequestType.values()) {
            WorkflowSpec spec = TransitionMatrix.spec(type);
            for (Map.Entry<RequestState, java.util.List<Transition>> e : spec.edges().entrySet()) {
                for (Transition t : e.getValue()) {
                    if (t.actor() == Actor.SYSTEM || t.actor() == Actor.STUDENT) continue;
                    out.computeIfAbsent(t.actor(), a -> EnumSet.noneOf(RequestState.class))
                            .add(e.getKey());
                }
            }
        }
        return Map.copyOf(out);
    }

    /** The states one Actor is the human decision-maker for. */
    public static Set<RequestState> awaiting(Actor a) {
        return BY_ACTOR.getOrDefault(a, Set.of());
    }

    /** The union over every role a staff member holds — their whole inbox. */
    public static Set<RequestState> awaiting(Collection<Actor> actors) {
        Set<RequestState> out = new LinkedHashSet<>();
        for (Actor a : actors) out.addAll(awaiting(a));
        return out;
    }
}
