import { RequestType, DocType } from '../domain/enums.js';
import { IllegalArgumentException } from './RequestPayload.js';
import { artifact } from './Artifact.js';
import { I18n } from '../view/i18n.js';

export class DocumentPayload {
  constructor() {
    this.docType = null;
    this.purpose = null;
    this.copies = 1;
    this.sys = { autoEligible: null, eligibilityReason: null, serialNo: null, verifyId: null, documentId: null };
  }

  type() { return RequestType.DOCUMENT; }

  title(locale = 'en') { return DocType.display(this.docType, locale); }

  subtitle(locale = 'en') { return `${this.copies} ${I18n.t(locale, 'payload.document.copySuffix')} · ${this.purpose}`; }

  artifacts(locale = 'en') {
    const out = [];
    if (this.sys.serialNo !== null) out.push(artifact('SERIAL', I18n.t(locale, 'artifact.serialNumber'), this.sys.serialNo));
    if (this.sys.verifyId !== null) {
      out.push(artifact('VERIFY_ID', I18n.t(locale, 'artifact.verificationId'), this.sys.verifyId, `/verify/${this.sys.verifyId}`));
    }
    if (this.sys.documentId !== null) {
      out.push(artifact('DOCUMENT', I18n.t(locale, 'artifact.generatedDocument'), I18n.t(locale, 'artifact.viewDownload'), `/documents/${this.sys.documentId}/download`));
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
