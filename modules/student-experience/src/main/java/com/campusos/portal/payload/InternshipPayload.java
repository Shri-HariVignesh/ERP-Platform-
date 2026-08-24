package com.campusos.portal.payload;

import com.campusos.portal.domain.RequestType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InternshipPayload implements RequestPayload {

    /* ---- frozen contract fields ---- */
    public String company;
    public String role;
    public String from;
    public String to;
    public String details;
    public CertificateRef certificateRef;

    /* ---- system-derived ---- */
    public Sys sys = new Sys();

    public static class CertificateRef {
        public String filename;
        public String mime;
        public int sizeKb;

        public CertificateRef() {}

        public CertificateRef(String filename, String mime, int sizeKb) {
            this.filename = filename; this.mime = mime; this.sizeKb = sizeKb;
        }
    }

    public static class Sys {
        public int weeks;
        public String certificateCheck;
        public Integer credits;
        public String verifyId;
        public String documentSerial;
        public int returnCount;
    }

    @Override public RequestType type() { return RequestType.INTERNSHIP; }

    @Override public String title() { return role + " · " + company; }

    @Override public String subtitle() {
        return from + " → " + to + " · " + sys.weeks + " week(s)"
                + (certificateRef == null ? " · no certificate" : " · " + certificateRef.filename);
    }

    @Override public List<Artifact> artifacts() {
        List<Artifact> out = new ArrayList<>();
        if (sys.verifyId != null) {
            out.add(new Artifact("VERIFY_ID", "Verification ID", sys.verifyId, "/verify/" + sys.verifyId));
        }
        if (sys.credits != null) {
            out.add(Artifact.of("CREDITS", "Credits added to academic record", String.valueOf(sys.credits)));
        }
        if (sys.documentSerial != null) {
            out.add(Artifact.of("SERIAL", "Certificate published as", sys.documentSerial));
        }
        return out;
    }

    @Override public void validate() {
        if (company == null || company.isBlank()) throw new IllegalArgumentException("company is required");
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        LocalDate a = LocalDate.parse(from), b = LocalDate.parse(to);
        if (!b.isAfter(a)) throw new IllegalArgumentException("end date must be after start date");
        if (b.isAfter(LocalDate.now())) throw new IllegalArgumentException("internship has not ended yet");
    }
}
