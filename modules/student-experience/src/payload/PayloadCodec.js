import { RequestType } from '../domain/enums.js';
import { LeavePayload } from './LeavePayload.js';
import { InternshipPayload } from './InternshipPayload.js';
import { DocumentPayload } from './DocumentPayload.js';
import { GrievancePayload } from './GrievancePayload.js';

/** Typed DTO in, JSON string out. Nothing writes `payload` without passing through here. */
export const PayloadCodec = {
  write(p) {
    p.validate();
    return JSON.stringify(p);
  },

  read(type, json) {
    const o = JSON.parse(json);
    switch (type) {
      case RequestType.LEAVE: return LeavePayload.fromJSON(o);
      case RequestType.INTERNSHIP: return InternshipPayload.fromJSON(o);
      case RequestType.DOCUMENT: return DocumentPayload.fromJSON(o);
      case RequestType.GRIEVANCE: return GrievancePayload.fromJSON(o);
      default: throw new Error(`payload deserialize failed for ${type}`);
    }
  },
};
