import { RequestType, LeaveType } from '../domain/enums.js';
import { parseDate, epochDay, IllegalArgumentException } from './RequestPayload.js';
import { artifact } from './Artifact.js';

/** Typed DTO for LEAVE. Plain object shape, matching the Java class field-for-field. */
export class LeavePayload {
  constructor() {
    this.leaveType = null;
    this.from = null;
    this.to = null;
    this.reason = null;
    this.sys = { dayCount: 0, balanceAtSubmit: 0, attendanceBefore: null, attendanceAfter: null,
      datesMutated: [], validation: null };
  }

  type() { return RequestType.LEAVE; }

  title() {
    const t = this.leaveType;
    return t.charAt(0) + t.slice(1).toLowerCase() + ' leave';
  }

  subtitle() { return `${this.from} → ${this.to} · ${this.sys.dayCount} day(s) · ${this.reason}`; }

  artifacts() {
    const out = [];
    if (this.sys.attendanceAfter !== null) {
      out.push(artifact('ATTENDANCE', 'Attendance', `${this.sys.attendanceBefore}% → ${this.sys.attendanceAfter}%`));
      out.push(artifact('DAYS', 'Days marked APPROVED_LEAVE', String(this.sys.datesMutated.length)));
    }
    return out;
  }

  handledBy() { return null; }

  validate() {
    if (!this.leaveType || !Object.values(LeaveType).includes(this.leaveType)) {
      throw new IllegalArgumentException('leaveType is required');
    }
    if (!this.reason || !this.reason.trim()) throw new IllegalArgumentException('reason is required');
    const a = parseDate('from-date', this.from);
    const b = parseDate('to-date', this.to);
    if (epochDay(b) < epochDay(a)) throw new IllegalArgumentException('to-date is before from-date');
    if (epochDay(b) - epochDay(a) > 30) throw new IllegalArgumentException('leave longer than 30 days');
  }

  static fromJSON(o) { return Object.assign(new LeavePayload(), o, { sys: { ...new LeavePayload().sys, ...o.sys } }); }
}
