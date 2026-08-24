package com.campusos.portal.service;

import com.campusos.portal.domain.*;
import com.campusos.portal.engine.IllegalTransitionException;
import com.campusos.portal.engine.RequestStateMachine;
import com.campusos.portal.payload.RequestPayload;
import com.campusos.portal.repo.*;
import com.campusos.portal.view.RequestCard;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class RequestService {

    private final RequestRepository requests;
    private final StudentRepository students;
    private final TenantRepository tenants;
    private final RequestStateMachine machine;
    private final PresentationService presentation;

    public RequestService(RequestRepository requests, StudentRepository students,
                          TenantRepository tenants, RequestStateMachine machine,
                          PresentationService presentation) {
        this.requests = requests;
        this.students = students;
        this.tenants = tenants;
        this.machine = machine;
        this.presentation = presentation;
    }

    public Student student(Scope s) {
        return students.findByIdAndTenantId(s.studentId(), s.tenantId())
                .orElseThrow(() -> new IllegalStateException("student not in scope"));
    }

    public Tenant tenant(Scope s) {
        return tenants.findById(s.tenantId()).orElseThrow();
    }

    public List<RequestCard> all(Scope s) {
        return presentation.cards(s,
                requests.findByTenantIdAndStudentIdOrderByCreatedAtDesc(s.tenantId(), s.studentId()));
    }

    public List<RequestCard> ofType(Scope s, RequestType type) {
        return presentation.cards(s,
                requests.findByTenantIdAndStudentIdAndTypeOrderByCreatedAtDesc(
                        s.tenantId(), s.studentId(), type));
    }

    public RequestCard card(Scope s, String id) {
        Request r = requests.findByIdAndTenantIdAndStudentId(id, s.tenantId(), s.studentId())
                .orElseThrow(() -> new IllegalTransitionException("request not visible in scope"));
        return presentation.card(s, r);
    }

    public Request raw(Scope s, String id) {
        return requests.findByIdAndTenantIdAndStudentId(id, s.tenantId(), s.studentId())
                .orElseThrow(() -> new IllegalTransitionException("request not visible in scope"));
    }

    public Request create(Scope s, RequestType type, RequestPayload payload) {
        return machine.create(s, type, payload);
    }

    public Request transition(Scope s, String id, Event e, Actor a, String note) {
        return machine.transition(s, id, e, a, note);
    }

    public Request transition(Scope s, String id, Event e, Actor a, String note,
                              Consumer<RequestPayload> patch) {
        return machine.transition(s, id, e, a, note, patch);
    }
}
