import { RequestType, GrievanceCategory } from '../domain/enums.js';
import { IllegalArgumentException } from './RequestPayload.js';
import { DisplayLabels } from '../view/DisplayLabels.js';

export class GrievancePayload {
  constructor() {
    this.category = null;
    this.subject = null;
    this.description = null;
    this.anonymous = false;
    this.sys = { routedTo: null };
  }

  type() { return RequestType.GRIEVANCE; }

  title() { return this.subject; }

  subtitle() {
    return `${DisplayLabels.category(this.category)} · ${DisplayLabels.desk(this.category)}`
      + (this.anonymous ? ' · anonymous' : '');
  }

  artifacts() { return []; }

  /**
   * The desk, not the generic matrix actor — "Hostel Warden Office", never "Class Advisor".
   * Derived from the category at render time; nothing in the engine maintains sys.routedTo.
   */
  handledBy() { return DisplayLabels.desk(this.category); }

  validate() {
    if (!this.category || !Object.values(GrievanceCategory).includes(this.category)) {
      throw new IllegalArgumentException('category is required');
    }
    if (!this.subject || !this.subject.trim()) throw new IllegalArgumentException('subject is required');
    if (!this.description || !this.description.trim()) throw new IllegalArgumentException('description is required');
  }

  static fromJSON(o) { return Object.assign(new GrievancePayload(), o, { sys: { ...new GrievancePayload().sys, ...o.sys } }); }
}
