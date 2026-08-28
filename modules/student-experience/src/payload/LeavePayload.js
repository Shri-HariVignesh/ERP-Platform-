import { RequestType, LeaveType } from '../domain/enums.js';
import { parseDate, epochDay, IllegalArgumentException } from './RequestPayload.js';
import { artifact } from './Artifact.js';
import { DisplayLabels } from '../view/DisplayLabels.js';
import { I18n } from '../view/i18n.js';

/** Typed DTO for LEAVE. */
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

  title(locale = 'en') {
    return `${DisplayLabels.leaveType(this.leaveType, locale)} ${I18n.t(locale, 'payload.leave.suffix')}`;
  }

  subtitle(locale = 'en') {
    return `${this.from} → ${this.to} · ${this.sys.dayCount} ${I18n.t(locale, 'unit.days')} · ${this.reason}`;
  }

  artifacts(locale = 'en') {
    const out = [];
    if (this.sys.attendanceAfter !== null) {
      out.push(artifact('ATTENDANCE', I18n.t(locale, 'artifact.attendance'), `${this.sys.attendanceBefore}% → ${this.sys.attendanceAfter}%`));
      out.push(artifact('DAYS', I18n.t(locale, 'artifact.daysMarkedApprovedLeave'), String(this.sys.datesMutated.length)));
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
