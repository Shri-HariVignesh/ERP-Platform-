/** What a guard predicate is allowed to see. */
export class TransitionContext {
  constructor(request, payload, student) {
    this.request = request;
    this.payload = payload;
    this.student = student;
  }
}
