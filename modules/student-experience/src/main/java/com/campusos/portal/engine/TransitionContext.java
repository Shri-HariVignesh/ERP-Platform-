package com.campusos.portal.engine;

import com.campusos.portal.domain.Request;
import com.campusos.portal.domain.Student;
import com.campusos.portal.payload.RequestPayload;

/** What a guard predicate is allowed to see. */
public record TransitionContext(Request request, RequestPayload payload, Student student) {}
