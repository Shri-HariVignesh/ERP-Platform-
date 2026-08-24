package com.campusos.portal.engine;

import com.campusos.portal.domain.RequestState;
import java.util.List;
import java.util.Map;

/**
 * Everything the UI needs to render a workflow it knows nothing about:
 * the on-path steps for the timeline, the off-path (rejection/return) states,
 * which states are terminal, and human labels. Templates read this, never a type switch.
 */
public record WorkflowSpec(
        String label,
        RequestState initial,
        List<Step> steps,
        Map<RequestState, OffPath> offPath,
        Map<RequestState, String> terminalTone,
        Map<RequestState, String> stateLabels,
        Map<RequestState, List<Transition>> edges) {

    public record Step(RequestState key, String label) {}

    public record OffPath(String label, String tone) {}

    public boolean isTerminal(RequestState s) { return terminalTone.containsKey(s); }

    public String labelFor(RequestState s) {
        return stateLabels.getOrDefault(s, s.name());
    }

    public List<Transition> from(RequestState s) {
        return edges.getOrDefault(s, List.of());
    }
}
