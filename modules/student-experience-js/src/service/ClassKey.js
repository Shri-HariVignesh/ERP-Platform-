/**
 * A class: one section of one semester of one department. This is the unit a faculty member is
 * assigned to teach, and the unit a student belongs to.
 */
export class ClassKey {
  constructor(department, semester, section) {
    if (!department || !department.trim()) throw new Error('department missing — class scope refused');
    if (!section || !section.trim()) throw new Error('section missing — class scope refused');
    this.department = department;
    this.semester = semester;
    this.section = section;
    Object.freeze(this);
  }

  static of(entity) { return new ClassKey(entity.department, entity.semester, entity.section); }

  matches(s) {
    return this.department === s.department && this.semester === s.semester && this.section === s.section;
  }

  equals(other) {
    return !!other && this.department === other.department
      && this.semester === other.semester && this.section === other.section;
  }

  /** Stable round-trippable form for a form field, so no free-text class ever reaches a query. */
  token() { return `${this.department}|${this.semester}|${this.section}`; }

  static parse(token) {
    const p = (token ?? '').split('|');
    if (p.length !== 3) throw new Error('unreadable class token');
    const semester = Number.parseInt(p[1], 10);
    if (Number.isNaN(semester)) throw new Error('unreadable class token');
    return new ClassKey(p[0], semester, p[2]);
  }

  label() { return `${this.department} · Semester ${this.semester} · Section ${this.section}`; }
}
