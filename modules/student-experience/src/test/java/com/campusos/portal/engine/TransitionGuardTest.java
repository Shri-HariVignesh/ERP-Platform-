package com.campusos.portal.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusos.portal.domain.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AREA 1 — the guard.
 *
 * Legal edges, illegal edges and terminality are all driven from TransitionMatrix itself:
 * the only hand-written data is the setup path needed to park a request in each state,
 * which is the frozen contract restated. The `matrixCoverage` test then proves that table
 * exercises EVERY human edge the matrix declares, so a new edge cannot be added without a test.
 */
class TransitionGuardTest extends EngineTestBase {

    /** flavor, setup moves to reach the source state, the edge under test, expected end state. */
    record LegalCase(String flavor, List<Move> setup, Move edge, RequestState expected) {
        @Override public String toString() {
            return flavor + " : " + edge.actor() + "." + edge.event() + " -> " + expected;
        }
    }

    /** flavor, setup moves, the state the request rests in afterwards. */
    record Parked(String flavor, RequestType type, List<Move> setup, RequestState state) {
        @Override public String toString() { return type + " @ " + state; }
    }

    private static final String WHY = "reason supplied by the test";

    static Stream<LegalCase> legalEdges() {
        return Stream.of(
                // ---- LEAVE ----
                new LegalCase("LEAVE", List.of(),
                        Move.of(Event.APPROVE, Actor.FACULTY), RequestState.HOD_PENDING),
                new LegalCase("LEAVE", List.of(),
                        Move.of(Event.REJECT, Actor.FACULTY, WHY), RequestState.REJECTED),
                new LegalCase("LEAVE", List.of(Move.of(Event.APPROVE, Actor.FACULTY)),
                        Move.of(Event.APPROVE, Actor.HOD), RequestState.NOTIFIED),
                new LegalCase("LEAVE", List.of(Move.of(Event.APPROVE, Actor.FACULTY)),
                        Move.of(Event.REJECT, Actor.HOD, WHY), RequestState.REJECTED),

                // ---- INTERNSHIP ----
                new LegalCase("INTERNSHIP", List.of(),
                        Move.of(Event.VERIFY, Actor.FACULTY), RequestState.INSTITUTION_APPROVAL),
                new LegalCase("INTERNSHIP", List.of(),
                        Move.of(Event.RETURN, Actor.FACULTY, WHY), RequestState.RETURNED),
                new LegalCase("INTERNSHIP", List.of(Move.of(Event.VERIFY, Actor.FACULTY)),
                        Move.of(Event.APPROVE, Actor.INSTITUTION), RequestState.VERIFICATION_ID_GENERATED),
                new LegalCase("INTERNSHIP", List.of(Move.of(Event.VERIFY, Actor.FACULTY)),
                        Move.of(Event.REJECT, Actor.INSTITUTION, WHY), RequestState.REJECTED),
                new LegalCase("INTERNSHIP", List.of(Move.of(Event.RETURN, Actor.FACULTY, WHY)),
                        Move.of(Event.RESUBMIT, Actor.STUDENT), RequestState.FACULTY_VERIFICATION),

                // ---- DOCUMENTS ----
                new LegalCase("DOCUMENT_MANUAL", List.of(),
                        Move.of(Event.APPROVE, Actor.OFFICE), RequestState.DOCUMENT_GENERATED),
                new LegalCase("DOCUMENT_MANUAL", List.of(),
                        Move.of(Event.REJECT, Actor.OFFICE, WHY), RequestState.REJECTED),

                // ---- GRIEVANCE ----
                new LegalCase("GRIEVANCE", List.of(),
                        Move.of(Event.START_REVIEW, Actor.FACULTY), RequestState.UNDER_REVIEW),
                new LegalCase("GRIEVANCE", List.of(Move.of(Event.START_REVIEW, Actor.FACULTY)),
                        Move.of(Event.RESOLVE, Actor.FACULTY, WHY), RequestState.RESOLVED));
    }

    static Stream<Parked> parkedStates() {
        return Stream.of(
                new Parked("LEAVE", RequestType.LEAVE, List.of(), RequestState.FACULTY_PENDING),
                new Parked("LEAVE", RequestType.LEAVE, List.of(Move.of(Event.APPROVE, Actor.FACULTY)),
                        RequestState.HOD_PENDING),
                new Parked("LEAVE", RequestType.LEAVE,
                        List.of(Move.of(Event.APPROVE, Actor.FACULTY), Move.of(Event.APPROVE, Actor.HOD)),
                        RequestState.NOTIFIED),
                new Parked("LEAVE", RequestType.LEAVE,
                        List.of(Move.of(Event.REJECT, Actor.FACULTY, WHY)), RequestState.REJECTED),

                new Parked("INTERNSHIP", RequestType.INTERNSHIP, List.of(),
                        RequestState.FACULTY_VERIFICATION),
                new Parked("INTERNSHIP", RequestType.INTERNSHIP,
                        List.of(Move.of(Event.VERIFY, Actor.FACULTY)), RequestState.INSTITUTION_APPROVAL),
                new Parked("INTERNSHIP", RequestType.INTERNSHIP,
                        List.of(Move.of(Event.RETURN, Actor.FACULTY, WHY)), RequestState.RETURNED),
                new Parked("INTERNSHIP", RequestType.INTERNSHIP,
                        List.of(Move.of(Event.VERIFY, Actor.FACULTY), Move.of(Event.APPROVE, Actor.INSTITUTION)),
                        RequestState.VERIFICATION_ID_GENERATED),
                new Parked("INTERNSHIP", RequestType.INTERNSHIP,
                        List.of(Move.of(Event.VERIFY, Actor.FACULTY),
                                Move.of(Event.REJECT, Actor.INSTITUTION, WHY)), RequestState.REJECTED),

                new Parked("DOCUMENT_MANUAL", RequestType.DOCUMENT, List.of(), RequestState.APPROVAL),
                new Parked("DOCUMENT_MANUAL", RequestType.DOCUMENT,
                        List.of(Move.of(Event.APPROVE, Actor.OFFICE)), RequestState.DOCUMENT_GENERATED),
                new Parked("DOCUMENT_MANUAL", RequestType.DOCUMENT,
                        List.of(Move.of(Event.REJECT, Actor.OFFICE, WHY)), RequestState.REJECTED),
                new Parked("DOCUMENT_AUTO", RequestType.DOCUMENT, List.of(),
                        RequestState.DOCUMENT_GENERATED),

                new Parked("GRIEVANCE", RequestType.GRIEVANCE, List.of(), RequestState.ASSIGNED),
                new Parked("GRIEVANCE", RequestType.GRIEVANCE,
                        List.of(Move.of(Event.START_REVIEW, Actor.FACULTY)), RequestState.UNDER_REVIEW),
                new Parked("GRIEVANCE", RequestType.GRIEVANCE,
                        List.of(Move.of(Event.START_REVIEW, Actor.FACULTY),
                                Move.of(Event.RESOLVE, Actor.FACULTY, WHY)), RequestState.RESOLVED));
    }

    static Stream<Arguments> terminalStates() {
        List<Arguments> out = new ArrayList<>();
        parkedStates().forEach(p -> {
            if (TransitionMatrix.spec(p.type()).isTerminal(p.state())) out.add(Arguments.of(p));
        });
        return out.stream();
    }

    /* --------------------------- 1a. every legal edge --------------------------- */

    @ParameterizedTest(name = "legal: {0}")
    @MethodSource("legalEdges")
    @DisplayName("every legal edge in the frozen matrix succeeds for its declared actor")
    void legalEdgeSucceeds(LegalCase c) {
        Fixture f = fixture("legal");
        Request r = drive(f, c.flavor(), c.setup().toArray(Move[]::new));

        RequestState before = r.state;
        Request moved = machine.transition(f.scope(), r.id, c.edge().event(), c.edge().actor(),
                c.edge().note());

        assertThat(moved.state)
                .as("%s from %s via %s.%s", c.flavor(), before, c.edge().actor(), c.edge().event())
                .isEqualTo(c.expected());

        // the move is recorded in the typed history, with the acting actor
        var trail = historyOf(f, moved);
        assertThat(trail).isNotEmpty();
        assertThat(trail.stream().anyMatch(h -> h.fromState == before && h.actor == c.edge().actor()))
                .as("history records %s acting from %s", c.edge().actor(), before)
                .isTrue();
        assertThat(trail.get(trail.size() - 1).toState).isEqualTo(c.expected());
    }

    /* ------------------------ 1b. the table covers the matrix ------------------------ */

    @org.junit.jupiter.api.Test
    @DisplayName("the legal-edge table exercises EVERY human edge the matrix declares")
    void matrixCoverage() {
        Set<String> declared = new LinkedHashSet<>();
        for (RequestType type : RequestType.values()) {
            WorkflowSpec spec = TransitionMatrix.spec(type);
            spec.edges().forEach((state, edges) -> edges.stream()
                    .filter(t -> t.actor() != Actor.SYSTEM)
                    .forEach(t -> declared.add(type + "|" + state + "|" + t.event() + "|" + t.actor())));
        }

        Set<String> exercised = new LinkedHashSet<>();
        legalEdges().forEach(c -> {
            Fixture f = fixture("cover");
            Request r = drive(f, c.flavor(), c.setup().toArray(Move[]::new));
            exercised.add(typeFor(c.flavor()) + "|" + r.state + "|" + c.edge().event()
                    + "|" + c.edge().actor());
            machine.transition(f.scope(), r.id, c.edge().event(), c.edge().actor(), c.edge().note());
        });

        assertThat(exercised)
                .as("human edges declared in TransitionMatrix but never driven by a test")
                .containsExactlyInAnyOrderElementsOf(declared);
    }

    /* --------------------- 1c. every illegal move is rejected --------------------- */

    @ParameterizedTest(name = "illegal moves from {0}")
    @MethodSource("parkedStates")
    @DisplayName("wrong actor and undefined event both throw IllegalTransitionException")
    void illegalMovesAreRejected(Parked p) {
        Fixture f = fixture("illegal");
        Request r = drive(f, p.flavor(), p.setup().toArray(Move[]::new));
        assertThat(r.state).as("setup parked the request in the expected state").isEqualTo(p.state());

        WorkflowSpec spec = TransitionMatrix.spec(p.type());
        List<Transition> legal = spec.from(p.state());

        int wrongActor = 0;
        int undefinedEvent = 0;

        for (Event event : Event.values()) {
            boolean eventDefined = legal.stream().anyMatch(t -> t.event() == event);
            for (Actor actor : Actor.values()) {
                boolean allowed = legal.stream()
                        .anyMatch(t -> t.event() == event && t.actor() == actor);
                if (allowed) continue;

                assertThatThrownBy(() ->
                        machine.transition(f.scope(), r.id, event, actor, WHY))
                        .as("%s @ %s must reject %s.%s", p.type(), p.state(), actor, event)
                        .isInstanceOf(IllegalTransitionException.class);

                if (eventDefined) wrongActor++; else undefinedEvent++;

                // and the rejected attempt must not have moved anything
                assertThat(requests.findByIdAndTenantIdAndStudentId(
                        r.id, f.scope().tenantId(), f.scope().studentId()).orElseThrow().state)
                        .as("state unchanged after a rejected %s.%s", actor, event)
                        .isEqualTo(p.state());
            }
        }

        assertThat(undefinedEvent).as("undefined-event combinations covered").isPositive();
        if (!legal.isEmpty()) {
            assertThat(wrongActor).as("wrong-actor combinations covered").isPositive();
        }
    }

    /* ----------------------------- 1d. terminal states ----------------------------- */

    @ParameterizedTest(name = "terminal: {0}")
    @MethodSource("terminalStates")
    @DisplayName("no transition is legal out of a terminal state")
    void terminalStatesAreClosed(Parked p) {
        Fixture f = fixture("terminal");
        Request r = drive(f, p.flavor(), p.setup().toArray(Move[]::new));
        assertThat(r.state).isEqualTo(p.state());
        assertThat(TransitionMatrix.spec(p.type()).from(p.state()))
                .as("a terminal state declares no outgoing edges").isEmpty();

        for (Event event : Event.values()) {
            for (Actor actor : Actor.values()) {
                assertThatThrownBy(() -> machine.transition(f.scope(), r.id, event, actor, WHY))
                        .isInstanceOf(IllegalTransitionException.class)
                        .hasMessageContaining("terminal");
            }
        }
    }
}
