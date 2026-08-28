import { RequestType } from '../domain/enums.js';

/**
 * Per-type "how long is too long" thresholds, measured from the request's createdAt (i.e. how
 * long it has been open, not how long it's sat in its current stage — that's what a faculty
 * member scanning an inbox actually means by "6 days open"). Tunable per workflow: a leave
 * request is usually dated in the near future and stales fast; a grievance under the UGC
 * redressal expectations should move quickly too; an internship verification tolerates a
 * slower cadence.
 */
const THRESHOLDS = {
  [RequestType.LEAVE]: { dueSoonDays: 1, overdueDays: 3 },
  [RequestType.INTERNSHIP]: { dueSoonDays: 3, overdueDays: 7 },
  [RequestType.DOCUMENT]: { dueSoonDays: 2, overdueDays: 5 },
  [RequestType.GRIEVANCE]: { dueSoonDays: 2, overdueDays: 4 },
};
const DEFAULT_THRESHOLDS = { dueSoonDays: 2, overdueDays: 5 };

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export const Sla = {
  ageDays(createdAtIso, now = Date.now()) {
    return Math.max(0, Math.floor((now - new Date(createdAtIso).getTime()) / MS_PER_DAY));
  },

  /** 'fresh' | 'due' | 'overdue' */
  levelFor(type, createdAtIso, now = Date.now()) {
    const { dueSoonDays, overdueDays } = THRESHOLDS[type] ?? DEFAULT_THRESHOLDS;
    const age = this.ageDays(createdAtIso, now);
    if (age >= overdueDays) return 'overdue';
    if (age >= dueSoonDays) return 'due';
    return 'fresh';
  },
};
