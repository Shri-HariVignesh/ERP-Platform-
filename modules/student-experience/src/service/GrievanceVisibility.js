import { GrievanceCategory, StaffRole } from '../domain/enums.js';

/**
 * CONFIDENTIALITY FOR STATUTORY GRIEVANCE CATEGORIES.
 *
 * The ordinary Grievance workflow treats "desk" as a display label only (see
 * GrievancePayload.handledBy() / DisplayLabels.desk()) — AUTO_ASSIGN carries no side effects,
 * so any FACULTY-equivalent staff member in the student's roster can see and act on a hostel
 * or fee complaint. That is fine for those categories. It is NOT fine for ragging, sexual
 * harassment, caste-based discrimination or equal-opportunity complaints, which the UGC
 * regulations (Anti-Ragging 2009, POSH Act 2013, Grievance Redressal 2023) require to be seen
 * ONLY by the specific statutory committee — not by every class advisor who happens to share a
 * roster with the complainant.
 *
 * This is enforced here, at the service layer that builds the staff-visible request set
 * (FacultyService.inbox/requestsOf/notifications), the same way REPOSITORY_SCOPE_RULES.md
 * enforces tenant/roster scoping one layer down: a category with no entry below is visible
 * under the ordinary roster rules; a category WITH an entry is invisible to everyone except
 * the named role and INSTITUTION (whose oversight covers even confidential committees).
 */
const CONFIDENTIAL_TO_ROLE = {
  [GrievanceCategory.RAGGING]: StaffRole.ANTI_RAGGING,
  [GrievanceCategory.SEXUAL_HARASSMENT]: StaffRole.ICC,
  [GrievanceCategory.SC_ST_DISCRIMINATION]: StaffRole.SC_ST_CELL,
  [GrievanceCategory.EQUAL_OPPORTUNITY]: StaffRole.EQUAL_OPPORTUNITY_CELL,
  [GrievanceCategory.RTI]: StaffRole.RTI_OFFICER,
};

export const GrievanceVisibility = {
  /** True if this category carries no confidentiality restriction at all. */
  isConfidential(category) { return Object.prototype.hasOwnProperty.call(CONFIDENTIAL_TO_ROLE, category); },

  visible(scope, category) {
    const requiredRole = CONFIDENTIAL_TO_ROLE[category];
    if (!requiredRole) return true;
    // INSTITUTION is oversight; OMBUDSPERSON is the statutory appellate authority for EVERY
    // category once a student escalates — neither is "the committee this category belongs
    // to", but both have a legitimate reason to see it regardless of which committee that is.
    return scope.hasRole(requiredRole) || scope.hasRole(StaffRole.INSTITUTION) || scope.hasRole(StaffRole.OMBUDSPERSON);
  },
};
