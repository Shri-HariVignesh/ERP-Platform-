/** Both ids or nothing. Every service and repository call carries this. */
export class Scope {
  constructor(tenantId, studentId) {
    if (!tenantId || !tenantId.trim()) throw new Error('tenantId missing — query refused');
    if (!studentId || !studentId.trim()) throw new Error('studentId missing — query refused');
    this.tenantId = tenantId;
    this.studentId = studentId;
    Object.freeze(this);
  }
}
