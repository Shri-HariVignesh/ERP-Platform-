package com.campusos.portal.web;

import com.campusos.portal.domain.DocType;
import com.campusos.portal.payload.*;
import com.campusos.portal.view.DisplayLabels;
import jakarta.validation.constraints.*;

/**
 * Bound form objects. Validation runs here, before a typed payload DTO is ever built,
 * so `payload` is never written from unvalidated input.
 * (JavaBean accessors are required by Spring's th:field binder.)
 */
public final class Forms {

    private Forms() {}

    public static class LeaveForm {
        @NotNull private LeavePayload.LeaveType leaveType = LeavePayload.LeaveType.PERSONAL;
        @NotBlank private String from;
        @NotBlank private String to;
        @NotBlank @Size(max = 300) private String reason;

        public LeavePayload.LeaveType getLeaveType() { return leaveType; }
        public void setLeaveType(LeavePayload.LeaveType v) { this.leaveType = v; }
        public String getFrom() { return from; }
        public void setFrom(String v) { this.from = v; }
        public String getTo() { return to; }
        public void setTo(String v) { this.to = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }

        public LeavePayload toPayload() {
            LeavePayload p = new LeavePayload();
            p.leaveType = leaveType;
            p.from = from;
            p.to = to;
            p.reason = reason;
            return p;
        }
    }

    public static class InternshipForm {
        @NotBlank private String company;
        @NotBlank private String role;
        @NotBlank private String from;
        @NotBlank private String to;
        @NotBlank @Size(max = 500) private String details;
        /** Real file storage is a declared non-goal — a reference is recorded, not a file. */
        @NotBlank private String certificateFilename;

        public String getCompany() { return company; }
        public void setCompany(String v) { this.company = v; }
        public String getRole() { return role; }
        public void setRole(String v) { this.role = v; }
        public String getFrom() { return from; }
        public void setFrom(String v) { this.from = v; }
        public String getTo() { return to; }
        public void setTo(String v) { this.to = v; }
        public String getDetails() { return details; }
        public void setDetails(String v) { this.details = v; }
        public String getCertificateFilename() { return certificateFilename; }
        public void setCertificateFilename(String v) { this.certificateFilename = v; }

        public InternshipPayload toPayload() {
            InternshipPayload p = new InternshipPayload();
            p.company = company;
            p.role = role;
            p.from = from;
            p.to = to;
            p.details = details;
            p.certificateRef = new InternshipPayload.CertificateRef(
                    certificateFilename, "application/pdf", 248);
            return p;
        }
    }

    public static class DocumentForm {
        @NotNull private DocType docType = DocType.BONAFIDE;
        @NotBlank @Size(max = 200) private String purpose;
        @Min(1) @Max(3) private int copies = 1;

        public DocType getDocType() { return docType; }
        public void setDocType(DocType v) { this.docType = v; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String v) { this.purpose = v; }
        public int getCopies() { return copies; }
        public void setCopies(int v) { this.copies = v; }

        public DocumentPayload toPayload() {
            DocumentPayload p = new DocumentPayload();
            p.docType = docType;
            p.purpose = purpose;
            p.copies = copies;
            return p;
        }
    }

    public static class GrievanceForm {
        @NotNull private GrievancePayload.Category category = GrievancePayload.Category.ACADEMIC;
        @NotBlank @Size(max = 120) private String subject;
        @NotBlank @Size(max = 800) private String description;
        private boolean anonymous;

        public GrievancePayload.Category getCategory() { return category; }
        public void setCategory(GrievancePayload.Category v) { this.category = v; }
        public String getSubject() { return subject; }
        public void setSubject(String v) { this.subject = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public boolean isAnonymous() { return anonymous; }
        public void setAnonymous(boolean v) { this.anonymous = v; }

        public GrievancePayload toPayload() {
            GrievancePayload p = new GrievancePayload();
            p.category = category;
            p.subject = subject;
            p.description = description;
            p.anonymous = anonymous;
            // The frozen contract keeps routed_to as a payload field, so it is still written.
            // It is NOT the display source — DisplayLabels.desk is — but it must agree with it,
            // so both come from the same map rather than from two copies that drift apart.
            p.sys.routedTo = DisplayLabels.desk(category);
            return p;
        }
    }
}
