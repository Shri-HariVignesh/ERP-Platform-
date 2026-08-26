/**
 * SECURITY (CWE-248/CWE-20): a bare `new Date(value)` on a malformed string yields
 * `Invalid Date` rather than throwing, which would sail past validation exactly the way
 * Java's DateTimeParseException once sailed past the controllers' catch (from=NOTADATE -> 500).
 * validate() is the gate every payload passes through, so the conversion belongs here.
 */
export function parseDate(field, value) {
  if (value === null || value === undefined || String(value).trim() === '') {
    throw new IllegalArgumentException(`${field} is required`);
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new IllegalArgumentException(`${field} must be a date in yyyy-MM-dd form`);
  }
  const d = new Date(value + 'T00:00:00Z');
  if (Number.isNaN(d.getTime())) {
    throw new IllegalArgumentException(`${field} must be a date in yyyy-MM-dd form`);
  }
  return d;
}

export function epochDay(date) { return Math.floor(date.getTime() / 86400000); }

export class IllegalArgumentException extends Error {
  constructor(message) {
    super(message);
    this.name = 'IllegalArgumentException';
  }
}
