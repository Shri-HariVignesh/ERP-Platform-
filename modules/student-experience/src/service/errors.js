/**
 * Someone reached outside the scope their identity grants them. One parent for both sides of
 * the portal, so the error handler has ONE handler and both refusals look identical from
 * outside — a staff refusal and a student refusal must not be distinguishable by their response.
 */
export class ScopeAccessException extends Error {
  constructor(message) { super(message); this.name = 'ScopeAccessException'; }
}

/** A staff member reached for something outside their scope. Surfaces as 403. */
export class StaffAccessException extends ScopeAccessException {
  constructor(message) { super(message); this.name = 'StaffAccessException'; }
}

/** A student route was reached without an authenticated student identity behind it. */
export class StudentAccessException extends ScopeAccessException {
  constructor(message) { super(message); this.name = 'StudentAccessException'; }
}
