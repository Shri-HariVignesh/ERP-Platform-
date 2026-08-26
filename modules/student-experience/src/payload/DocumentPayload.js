import { RequestType, DocType } from '../domain/enums.js';
import { IllegalArgumentException } from './RequestPayload.js';
import { artifact } from './Artifact.js';

export class DocumentPayload {
  constructor() {
    this.docType = null;
    this.purpose = null;
    this.copies = 1;
    this.sys = { autoEligible: null, eligibilityReason: null, serialNo: null, verifyId: null, documentId: null };
  }

  type() { return RequestType.DOCUMENT; }

  title() { return DocType.display(this.docType); }

  subtitle() { return `${this.copies} copy/copies · ${this.purpose}`; }

  artifacts() {
    const out = [];
    if (this.sys.serialNo !== null) out.push(artifact('SERIAL', 'Serial number', this.sys.serialNo));
    if (this.sys.verifyId !== null) {
      out.push(artifact('VERIFY_ID', 'Verification ID', this.sys.verifyId, `/verify/${this.sys.verifyId}`));
    }
    if (this.sys.documentId !== null) {
      out.push(artifact('DOCUMENT', 'Generated document', 'View / download', `/documents/${this.sys.documentId}/download`));
    }
    return out;
  }

  handledBy() { return null; }

  validate() {
    if (!this.docType || !DocType.values().includes(this.docType)) {
      throw new IllegalArgumentException('docType is required');
    }
    if (!this.purpose || !this.purpose.trim()) throw new IllegalArgumentException('purpose is required');
    // Number.isInteger(NaN) is false — this is the gate every payload passes through, so a
    // non-numeric `copies` must be caught here even though the form layer also checks it.
    if (!Number.isInteger(this.copies) || this.copies < 1 || this.copies > 3) {
      throw new IllegalArgumentException('copies must be 1–3');
    }
  }

  static fromJSON(o) { return Object.assign(new DocumentPayload(), o, { sys: { ...new DocumentPayload().sys, ...o.sys } }); }
}
