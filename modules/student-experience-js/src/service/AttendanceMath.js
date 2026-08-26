export const AttendanceMath = {
  /** PRESENT + APPROVED_LEAVE over every day that counts. SCHEDULED days are excluded. */
  pct(rows) {
    const counted = rows.filter((r) => r.status !== 'SCHEDULED');
    if (counted.length === 0) return null;
    const attended = counted.filter((r) => r.status === 'PRESENT' || r.status === 'APPROVED_LEAVE');
    return Math.round((attended.length * 1000) / counted.length) / 10;
  },
};
