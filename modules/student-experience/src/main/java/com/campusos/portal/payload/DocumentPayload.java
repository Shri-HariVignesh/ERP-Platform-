package com.campusos.portal.payload;

import com.campusos.portal.domain.DocType;
import com.campusos.portal.domain.RequestType;
import java.util.ArrayList;
import java.util.List;

public class DocumentPayload implements RequestPayload {

    /* ---- frozen contract fields ---- */
    public DocType docType;
    public String purpose;
    public int copies = 1;

    /* ---- system-derived ---- */
    public Sys sys = new Sys();

    public static class Sys {
        public Boolean autoEligible;
        public String eligibilityReason;
        public String serialNo;
        public String verifyId;
        public Long documentId;
    }

    @Override public RequestType type() { return RequestType.DOCUMENT; }

    @Override public String title() { return docType.display(); }

    @Override public String subtitle() {
        return copies + " copy/copies · " + purpose;
    }

    @Override public List<Artifact> artifacts() {
        List<Artifact> out = new ArrayList<>();
        if (sys.serialNo != null) out.add(Artifact.of("SERIAL", "Serial number", sys.serialNo));
        if (sys.verifyId != null) {
            out.add(new Artifact("VERIFY_ID", "Verification ID", sys.verifyId, "/verify/" + sys.verifyId));
        }
        if (sys.documentId != null) {
            out.add(new Artifact("DOCUMENT", "Generated document", "View / download",
                    "/documents/" + sys.documentId + "/download"));
        }
        return out;
    }

    @Override public void validate() {
        if (docType == null) throw new IllegalArgumentException("docType is required");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose is required");
        if (copies < 1 || copies > 3) throw new IllegalArgumentException("copies must be 1–3");
    }
}
