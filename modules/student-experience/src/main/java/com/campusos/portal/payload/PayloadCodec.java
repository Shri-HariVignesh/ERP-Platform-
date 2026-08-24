package com.campusos.portal.payload;

import com.campusos.portal.domain.RequestType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Typed DTO in, JSON string out. Nothing writes `payload` without passing through here. */
@Component
public class PayloadCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    public String write(RequestPayload p) {
        p.validate();
        try {
            return mapper.writeValueAsString(p);
        } catch (Exception e) {
            throw new IllegalStateException("payload serialize failed", e);
        }
    }

    public RequestPayload read(RequestType type, String json) {
        try {
            return switch (type) {
                case LEAVE -> mapper.readValue(json, LeavePayload.class);
                case INTERNSHIP -> mapper.readValue(json, InternshipPayload.class);
                case DOCUMENT -> mapper.readValue(json, DocumentPayload.class);
                case GRIEVANCE -> mapper.readValue(json, GrievancePayload.class);
            };
        } catch (Exception e) {
            throw new IllegalStateException("payload deserialize failed for " + type, e);
        }
    }
}
