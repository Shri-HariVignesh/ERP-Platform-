/**
 * The ONE definition of how subject marks become a semester aggregate. The seeder and
 * AcademicWriteService both call this, which is why seeded SemesterResult rows are consistent
 * with their SubjectMark rows by construction rather than by coincidence.
 */
export const SgpaMath = {
  MAX_INTERNAL: 40,
  MAX_EXTERNAL: 60,

  /** Total out of 100 -> a ten-point grade point. */
  gradePoint(total) {
    if (total >= 90) return 10;
    if (total >= 80) return 9;
    if (total >= 70) return 8;
    if (total >= 60) return 7;
    if (total >= 50) return 6;
    if (total >= 40) return 5;
    return 0; // below the pass mark earns no credit
  },

  grade(total) {
    if (total >= 90) return 'S';
    if (total >= 80) return 'A';
    if (total >= 70) return 'B';
    if (total >= 60) return 'C';
    if (total >= 50) return 'D';
    if (total >= 40) return 'E';
    return 'F';
  },

  /** Credit-weighted SGPA over the given marks, to two decimals. */
  sgpa(marks) {
    const credits = this.credits(marks);
    if (credits === 0) return 0;
    const weighted = marks.reduce((sum, m) => sum + this.gradePoint(m.internal + m.external) * m.credits, 0);
    return Math.round((weighted / credits) * 100) / 100;
  },

  /** A failed subject earns no credit, so it must not inflate the denominator either. */
  credits(marks) {
    return marks.filter((m) => this.gradePoint(m.internal + m.external) > 0)
      .reduce((sum, m) => sum + m.credits, 0);
  },
};
