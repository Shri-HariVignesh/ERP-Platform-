import { RequestType } from '../domain/enums.js';
import { parseDate, IllegalArgumentException } from './RequestPayload.js';
import { artifact } from './Artifact.js';

export class InternshipPayload {
  constructor() {
    this.company = null;
    this.role = null;
    this.from = null;
    this.to = null;
    this.details = null;
    this.certificateRef = null; // { filename, mime, sizeKb }
    this.sys = { weeks: 0, certificateCheck: null, credits: null, verifyId: null,
      documentSerial: null, returnCount: 0 };
  }

  type() { return RequestType.INTERNSHIP; }

  title() { return `${this.role} · ${this.company}`; }

  subtitle() {
    return `${this.from} → ${this.to} · ${this.sys.weeks} week(s)`
      + (this.certificateRef === null ? ' · no certificate' : ` · ${this.certificateRef.filename}`);
  }

  artifacts() {
    const out = [];
    if (this.sys.verifyId !== null) {
      out.push(artifact('VERIFY_ID', 'Verification ID', this.sys.verifyId, `/verify/${this.sys.verifyId}`));
    }
    if (this.sys.credits !== null) {
      out.push(artifact('CREDITS', 'Credits added to academic record', String(this.sys.credits)));
    }
    if (this.sys.documentSerial !== null) {
      out.push(artifact('SERIAL', 'Certificate published as', this.sys.documentSerial));
    }
    return out;
  }

  handledBy() { return null; }

  validate() {
    if (!this.company || !this.company.trim()) throw new IllegalArgumentException('company is required');
    if (!this.role || !this.role.trim()) throw new IllegalArgumentException('role is required');
    const a = parseDate('start date', this.from);
    const b = parseDate('end date', this.to);
    if (!(b.getTime() > a.getTime())) throw new IllegalArgumentException('end date must be after start date');
    if (b.getTime() > Date.now()) throw new IllegalArgumentException('internship has not ended yet');
  }

  static fromJSON(o) { return Object.assign(new InternshipPayload(), o, { sys: { ...new InternshipPayload().sys, ...o.sys } }); }
}
