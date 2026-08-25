package com.campusos.portal.service;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.TransitionMatrix;
import com.campusos.portal.engine.Transition;
import com.campusos.portal.engine.WorkflowSpec;
import com.campusos.portal.payload.Artifact;
import com.campusos.portal.payload.PayloadCodec;
import com.campusos.portal.payload.RequestPayload;
import com.campusos.portal.repo.RequestHistoryRepository;
import com.campusos.portal.repo.StudentRepository;
import com.campusos.portal.view.ActionButton;
import com.campusos.portal.view.DisplayLabels;
import com.campusos.portal.view.TrailEntry;
import com.campusos.portal.view.RequestCard;
import com.campusos.portal.view.TimelineStep;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Turns a Request of ANY type into one RequestCard, using only WorkflowSpec metadata and
 * the payload's own polymorphic title()/subtitle()/artifacts(). No switch on RequestType.
 */
@Service
public class PresentationService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault());

    private final PayloadCodec codec;
    private final RequestHistoryRepository history;
    private final StudentRepository students;

    public PresentationService(PayloadCodec codec, RequestHistoryRepository history,
                               StudentRepository students) {
        this.codec = codec;
        this.history = history;
        this.students = students;
    }

    public List<RequestCard> cards(Scope scope, List<Request> requests) {
        String studentName = studentName(scope);
        List<RequestCard> out = new ArrayList<>();
        for (Request r : requests) out.add(card(scope, r, studentName));
        return out;
    }

    public RequestCard card(Scope scope, Request r) {
        return card(scope, r, studentName(scope));
    }

    /** The student reads their own name in the trail, not the word "STUDENT". */
    private String studentName(Scope scope) {
        return students.findByIdAndTenantId(scope.studentId(), scope.tenantId())
                .map(s -> s.name).orElse("You");
    }

    private RequestCard card(Scope scope, Request r, String studentName) {
        WorkflowSpec spec = TransitionMatrix.spec(r.type);
        RequestPayload payload = codec.read(r.type, r.payload);
        List<RequestHistory> rows = history.findByRequestIdAndTenantIdAndStudentIdOrderByIdAsc(
                r.id, scope.tenantId(), scope.studentId());

        return new RequestCard(
                r.id,
                r.type.name(),
                spec.label(),
                payload.title(),
                payload.subtitle(),
                r.state.name(),
                DisplayLabels.stateLabel(spec.labelFor(r.state), payload.handledBy()),
                tone(spec, r.state),
                headline(spec, r, payload, rows),
                steps(spec, r, rows),
                studentAction(spec, r),
                artifacts(payload.artifacts()),
                FMT.format(r.createdAt),
                FMT.format(r.updatedAt),
                trail(rows, studentName));
    }

    /** Artifact labels go through the same cleaner, so no side-effect label can leak an enum. */
    private List<Artifact> artifacts(List<Artifact> raw) {
        List<Artifact> out = new ArrayList<>();
        for (Artifact a : raw) {
            out.add(new Artifact(a.kind(), DisplayLabels.proof(a.label()), a.value(), a.href()));
        }
        return out;
    }

    /** Raw history rows in, English out. Nothing else formats the trail. */
    private List<TrailEntry> trail(List<RequestHistory> rows, String studentName) {
        List<TrailEntry> out = new ArrayList<>();
        for (RequestHistory h : rows) {
            out.add(new TrailEntry(
                    DisplayLabels.transition(h.fromState, h.toState),
                    DisplayLabels.actor(h.actor, studentName),
                    h.note,
                    DisplayLabels.effects(h.effects),
                    DisplayLabels.proof(h.effectLog),
                    FMT.format(h.at)));
        }
        return out;
    }

    private String tone(WorkflowSpec spec, RequestState state) {
        if (spec.terminalTone().containsKey(state)) return spec.terminalTone().get(state);
        if (spec.offPath().containsKey(state)) return spec.offPath().get(state).tone();
        return "pending";
    }

    /** Type-agnostic. `skipped` is how an automated bypass becomes visible to the student. */
    private List<TimelineStep> steps(WorkflowSpec spec, Request r, List<RequestHistory> rows) {
        Set<RequestState> visited = new HashSet<>();
        for (RequestHistory h : rows) visited.add(h.toState);

        List<TimelineStep> out = new ArrayList<>();
        WorkflowSpec.OffPath off = spec.offPath().get(r.state);

        if (off != null) {
            for (WorkflowSpec.Step s : spec.steps()) {
                out.add(new TimelineStep(s.label(), visited.contains(s.key()) ? "done" : "pending"));
            }
            out.add(new TimelineStep(off.label(), "failed"));
            return out;
        }

        int idx = -1;
        for (int i = 0; i < spec.steps().size(); i++) {
            if (spec.steps().get(i).key() == r.state) { idx = i; break; }
        }
        boolean terminal = spec.isTerminal(r.state);
        for (int i = 0; i < spec.steps().size(); i++) {
            WorkflowSpec.Step s = spec.steps().get(i);
            String status;
            if (i < idx) status = visited.contains(s.key()) ? "done" : "skipped";
            else if (i == idx) status = terminal ? "done" : "current";
            else status = "pending";
            out.add(new TimelineStep(s.label(), status));
        }
        return out;
    }

    private ActionButton studentAction(WorkflowSpec spec, Request r) {
        for (Transition t : spec.from(r.state)) {
            if (t.actor() == Actor.STUDENT) {
                return new ActionButton(t.label(), t.event().name(),
                        t.tone(), t.requiresNote(), t.inputLabel());
            }
        }
        return null;
    }

    /**
     * The COLLAPSED card's one line. Deliberately does NOT carry the engine's effect-log
     * prose — the artifact bullets (serial, verification ID, download, credits, attendance)
     * already show the outcome. Off-path states keep the human-written reason, because that
     * is the one thing a student needs and it is their reviewer's words, not engine output.
     */
    private String headline(WorkflowSpec spec, Request r, RequestPayload payload,
                            List<RequestHistory> rows) {
        RequestHistory last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        String note = last == null || last.note == null ? "" : last.note.trim();

        if (spec.offPath().containsKey(r.state)) {
            String label = spec.offPath().get(r.state).label();
            return note.isEmpty() ? label + "." : label + " — \u201c" + note + "\u201d";
        }
        String status = DisplayLabels.status(r.state);
        if (status != null) return status;

        String handler = payload.handledBy();
        if (handler == null) {
            handler = spec.from(r.state).stream()
                    .filter(t -> t.actor() != Actor.SYSTEM)
                    .map(t -> t.actor().display())
                    .findFirst().orElse("CampusOS");
        }
        return "Currently with " + handler + ".";
    }

}
