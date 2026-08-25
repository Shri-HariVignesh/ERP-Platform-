package com.campusos.portal.service;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.RequestStateMachine;
import com.campusos.portal.engine.Transition;
import com.campusos.portal.repo.*;
import com.campusos.portal.view.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The staff read side, and the one place a staff member's decision reaches the engine.
 *
 * Every method takes a StaffScope built from the authenticated principal. No method takes an
 * Actor, a tenantId or a studentId from the caller's parameters — the roster is resolved from
 * the identity first, and every lookup is bounded by it.
 */
@Service
public class FacultyService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault());

    private final RequestRepository requests;
    private final RequestHistoryRepository histories;
    private final AcademicAuditRepository audits;
    private final PresentationService presentation;
    private final StaffScopeResolver staffScopes;
    private final RequestStateMachine machine;

    public FacultyService(RequestRepository requests, RequestHistoryRepository histories,
                          AcademicAuditRepository audits, PresentationService presentation,
                          StaffScopeResolver staffScopes, RequestStateMachine machine) {
        this.requests = requests;
        this.histories = histories;
        this.audits = audits;
        this.presentation = presentation;
        this.staffScopes = staffScopes;
        this.machine = machine;
    }

    /* --------------------------------- the inbox --------------------------------- */

    /**
     * MY TASKS. Every request in this staff member's tenant whose current state is awaiting an
     * Actor they hold a role for, AND whose student is inside their own scope.
     */
    public List<FacultyTask> inbox(StaffScope scope) {
        return inbox(scope, null);
    }

    /** The same inbox, narrowed to one workflow — this is all that views 4 and 5 are. */
    public List<FacultyTask> inbox(StaffScope scope, RequestType type) {
        List<Student> roster = staffScopes.roster(scope);
        if (roster.isEmpty()) return List.of();

        Set<RequestState> states = InboxStates.awaiting(scope.actors());
        if (states.isEmpty()) return List.of();

        Map<String, Student> byId = roster.stream()
                .collect(Collectors.toMap(s -> s.id, s -> s, (a, b) -> a));

        List<Request> rows = requests.findByTenantIdAndStudentIdInAndStateInOrderByCreatedAtDesc(
                scope.tenantId(), byId.keySet(), states);

        List<FacultyTask> out = new ArrayList<>();
        for (Request r : rows) {
            if (type != null && r.type != type) continue;
            out.add(task(scope, r, byId.get(r.studentId)));
        }
        return out;
    }

    /** Everything about one student's requests, for the Students view. Read-only. */
    public List<RequestCard> requestsOf(StaffScope scope, Student student) {
        if (!scope.canSee(student)) throw new StaffAccessException("student not in scope");
        Scope s = new Scope(student.tenantId, student.id);
        return presentation.cards(s,
                requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc(student.tenantId, student.id));
    }

    private FacultyTask task(StaffScope scope, Request r, Student student) {
        // The card is built through the student's OWN scope, so it is byte-for-byte the read
        // model the student sees — same timeline, same trail, same DisplayLabels.
        RequestCard card = presentation.card(new Scope(r.tenantId, r.studentId), r);

        List<StaffAction> actions = new ArrayList<>();
        for (Transition t : staffScopes.permitted(scope, r)) {
            actions.add(new StaffAction(t.label(), t.event().name(), t.tone(), t.requiresNote()));
        }
        return new FacultyTask(card, student.id, student.name, student.rollNo,
                ClassKey.of(student).label(), actions);
    }

    /* ------------------------------- acting on one ------------------------------- */

    /**
     * THE ONLY WAY A STAFF DECISION REACHES A REQUEST.
     *
     * Note the signature: (scope, requestId, event, note). There is no Actor parameter, and
     * there is nowhere for a client to put one. The Actor is derived from the frozen matrix
     * and the principal's roles, then handed to the SAME RequestStateMachine.transition()
     * guard the student side uses — which validates it a second time, independently.
     */
    public Request act(StaffScope scope, String requestId, Event event, String note) {
        List<String> rosterIds = staffScopes.rosterIds(scope);
        if (rosterIds.isEmpty()) throw new StaffAccessException("request not in scope");

        Request r = requests.findByIdAndTenantIdAndStudentIdIn(requestId, scope.tenantId(), rosterIds)
                .orElseThrow(() -> new StaffAccessException("request not in scope"));

        Actor actor = staffScopes.actorFor(scope, r, event);
        return machine.transition(new Scope(r.tenantId, r.studentId), requestId, event, actor, note);
    }

    /* ------------------------------- notifications ------------------------------- */

    /**
     * IN-APP ONLY, and derived — there is no notification table. A notification IS an audit
     * row seen from the staff side: the request trail for workflow movement, the academic
     * audit for authoring. Nothing is written twice, so nothing can disagree.
     */
    public List<Notice> notifications(StaffScope scope, int limit) {
        List<Student> roster = staffScopes.roster(scope);
        if (roster.isEmpty()) return List.of();
        Map<String, Student> byId = roster.stream()
                .collect(Collectors.toMap(s -> s.id, s -> s, (a, b) -> a));
        Set<RequestState> mine = InboxStates.awaiting(scope.actors());

        record Stamped(java.time.Instant at, Notice notice) {}
        List<Stamped> feed = new ArrayList<>();

        for (RequestHistory h : histories.findByTenantIdAndStudentIdInOrderByIdDesc(
                scope.tenantId(), byId.keySet())) {
            Student s = byId.get(h.studentId);
            if (s == null) continue;
            boolean needsMe = mine.contains(h.toState);
            String kind = h.fromState == null ? "New request"
                    : needsMe ? "Approval required" : "Workflow update";
            feed.add(new Stamped(h.at, new Notice(kind,
                    s.name + " · " + DisplayLabels.state(h.toState),
                    DisplayLabels.transition(h.fromState, h.toState)
                            + (h.note == null || h.note.isBlank() ? "" : " — “" + h.note + "”"),
                    DisplayLabels.actor(h.actor, s.name),
                    FMT.format(h.at),
                    needsMe ? "/faculty/tasks" : "/faculty/students/" + s.id)));
        }

        for (AcademicAudit a : audits.findByTenantIdAndStudentIdInOrderByAtDesc(
                scope.tenantId(), List.copyOf(byId.keySet()))) {
            Student s = byId.get(a.studentId);
            if (s == null) continue;
            feed.add(new Stamped(a.at, new Notice("Academic", s.name + " · " + label(a.kind),
                    a.detail, a.staffName == null ? "Staff" : a.staffName,
                    FMT.format(a.at), "/faculty/students/" + s.id)));
        }

        // Sort on the real Instant, never on the formatted string — "9 Aug" sorts after
        // "10 Aug" lexically, which would quietly scramble the feed.
        feed.sort(Comparator.comparing(Stamped::at).reversed());
        List<Notice> out = new ArrayList<>();
        for (Stamped st : feed) {
            if (out.size() >= limit) break;
            out.add(st.notice());
        }
        return out;
    }

    private String label(AcademicAudit.Kind k) {
        return switch (k) {
            case ATTENDANCE -> "Attendance marked";
            case MARKS_DRAFT -> "Marks saved as draft";
            case MARKS_FINALIZED -> "Marks finalized";
        };
    }
}
