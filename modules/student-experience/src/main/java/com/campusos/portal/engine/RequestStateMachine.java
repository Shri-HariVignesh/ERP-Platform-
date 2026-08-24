package com.campusos.portal.engine;

import com.campusos.portal.domain.*;
import com.campusos.portal.payload.PayloadCodec;
import com.campusos.portal.payload.RequestPayload;
import com.campusos.portal.repo.*;
import com.campusos.portal.service.Scope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE GUARD. The only way a Request changes state anywhere in this application.
 *
 * transition() validates (state, event, actor, guard) against TransitionMatrix and throws
 * IllegalTransitionException on any move that is not in the matrix. On a legal move it
 * appends a RequestHistory row and fires the edge's declared side effects — in that order,
 * in one transaction.
 */
@Service
public class RequestStateMachine {

    private static final int AUTOPILOT_LIMIT = 12;

    private final RequestRepository requests;
    private final RequestHistoryRepository history;
    private final StudentRepository students;
    private final TenantRepository tenants;
    private final PayloadCodec codec;
    private final SideEffectDispatcher effects;

    public RequestStateMachine(RequestRepository requests, RequestHistoryRepository history,
                               StudentRepository students, TenantRepository tenants,
                               PayloadCodec codec, SideEffectDispatcher effects) {
        this.requests = requests;
        this.history = history;
        this.students = students;
        this.tenants = tenants;
        this.codec = codec;
        this.effects = effects;
    }

    /* ------------------------------ public API ------------------------------ */

    @Transactional
    public Request create(Scope scope, RequestType type, RequestPayload payload) {
        payload.validate();
        Request r = new Request();
        r.id = "req_" + UUID.randomUUID().toString().substring(0, 8);
        r.tenantId = scope.tenantId();
        r.studentId = scope.studentId();
        r.type = type;
        r.state = TransitionMatrix.initial(type);
        r.payload = codec.write(payload);
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        requests.save(r);

        RequestHistory seed = new RequestHistory();
        seed.requestId = r.id;
        seed.tenantId = r.tenantId;
        seed.studentId = r.studentId;
        seed.fromState = null;
        seed.toState = r.state;
        seed.actor = Actor.STUDENT;
        seed.note = "Submitted by student";
        history.save(seed);

        return autopilot(scope, r);
    }

    @Transactional
    public Request transition(Scope scope, String requestId, Event event, Actor actor, String note) {
        return transition(scope, requestId, event, actor, note, null);
    }

    @Transactional
    public Request transition(Scope scope, String requestId, Event event, Actor actor, String note,
                              Consumer<RequestPayload> patch) {
        Request r = requests.findByIdAndTenantIdAndStudentId(requestId, scope.tenantId(), scope.studentId())
                .orElseThrow(() -> new IllegalTransitionException(
                        "request " + requestId + " is not visible in this tenant+student scope"));
        Request moved = fire(scope, r, event, actor, note, patch);
        return autopilot(scope, moved);
    }

    /* -------------------------------- internals -------------------------------- */

    /** Applies exactly one edge, or throws. */
    private Request fire(Scope scope, Request r, Event event, Actor actor, String note,
                         Consumer<RequestPayload> patch) {
        WorkflowSpec spec = TransitionMatrix.spec(r.type);
        Student student = students.findByIdAndTenantId(r.studentId, r.tenantId)
                .orElseThrow(() -> new IllegalTransitionException("student not in scope"));
        Tenant tenant = tenants.findById(r.tenantId)
                .orElseThrow(() -> new IllegalTransitionException("tenant not found"));

        RequestPayload payload = codec.read(r.type, r.payload);
        if (patch != null) patch.accept(payload);

        Transition edge = select(spec, r, payload, student, event, actor);

        if (edge.requiresNote() && (note == null || note.isBlank())) {
            throw new IllegalTransitionException(event + " requires a reason");
        }

        RequestState from = r.state;
        List<String> log = new ArrayList<>();
        for (SideEffect fx : edge.effects()) {
            log.addAll(effects.fire(fx, r, payload, student, tenant));
        }

        r.state = edge.to();
        r.payload = codec.write(payload);
        r.updatedAt = Instant.now();
        requests.save(r);

        RequestHistory h = new RequestHistory();
        h.requestId = r.id;
        h.tenantId = r.tenantId;
        h.studentId = r.studentId;
        h.fromState = from;
        h.toState = edge.to();
        h.actor = actor;
        h.note = (note == null || note.isBlank()) ? null : note.trim();
        h.effects = edge.effects().stream().map(Enum::name).collect(Collectors.joining(", "));
        h.effectLog = String.join(" ", log);
        history.save(h);

        return r;
    }

    private Transition select(WorkflowSpec spec, Request r, RequestPayload payload, Student student,
                              Event event, Actor actor) {
        List<Transition> fromState = spec.from(r.state);
        if (fromState.isEmpty()) {
            throw new IllegalTransitionException(
                    r.type + " is terminal at " + r.state + " — no transition is legal");
        }
        List<Transition> byEvent = fromState.stream().filter(t -> t.event() == event).toList();
        if (byEvent.isEmpty()) {
            throw new IllegalTransitionException(r.type + ": no edge for event " + event
                    + " from state " + r.state + " (legal events: "
                    + fromState.stream().map(t -> t.event().name()).distinct()
                            .collect(Collectors.joining(", ")) + ")");
        }
        List<Transition> byActor = byEvent.stream().filter(t -> t.actor() == actor).toList();
        if (byActor.isEmpty()) {
            throw new IllegalTransitionException(r.type + ": " + actor + " may not fire " + event
                    + " from " + r.state + " (allowed actors: "
                    + byEvent.stream().map(t -> t.actor().name()).distinct()
                            .collect(Collectors.joining(", ")) + ")");
        }
        TransitionContext ctx = new TransitionContext(r, payload, student);
        return byActor.stream().filter(t -> t.guard().test(ctx)).findFirst()
                .orElseThrow(() -> new IllegalTransitionException(
                        r.type + ": guard rejected " + event + " from " + r.state));
    }

    /** Chains every SYSTEM edge whose guard passes. This is the automation. */
    private Request autopilot(Scope scope, Request r) {
        for (int i = 0; i < AUTOPILOT_LIMIT; i++) {
            WorkflowSpec spec = TransitionMatrix.spec(r.type);
            Student student = students.findByIdAndTenantId(r.studentId, r.tenantId).orElseThrow();
            RequestPayload payload = codec.read(r.type, r.payload);
            TransitionContext ctx = new TransitionContext(r, payload, student);

            Transition next = spec.from(r.state).stream()
                    .filter(t -> t.actor() == Actor.SYSTEM)
                    .filter(t -> t.guard().test(ctx))
                    .findFirst().orElse(null);
            if (next == null) return r;

            r = fire(scope, r, next.event(), Actor.SYSTEM, null, null);
        }
        return r;
    }
}
