/**
 * One edge of the matrix. `guard` is an extra predicate on top of (state, event, actor);
 * it is what lets SUBMITTED fork to DOCUMENT_GENERATED or APPROVAL without a second event.
 */
export class Transition {
  constructor(event, actor, to, effects, guard, requiresNote, label, tone, inputLabel) {
    this.event = event;
    this.actor = actor;
    this.to = to;
    this.effects = effects;
    this.guard = guard;
    this.requiresNote = requiresNote;
    this.label = label;
    this.tone = tone;
    this.inputLabel = inputLabel;
  }

  static of(event, actor, to, effects) {
    return new Transition(event, actor, to, effects, () => true, false, null, 'pending', null);
  }

  static human(event, actor, to, effects, requiresNote, label, tone) {
    return new Transition(event, actor, to, effects, () => true, requiresNote, label, tone, null);
  }

  guardedBy(guard) {
    return new Transition(this.event, this.actor, this.to, this.effects, guard,
      this.requiresNote, this.label, this.tone, this.inputLabel);
  }

  /** Declares that this edge needs one extra value from the student (rendered generically). */
  withInput(inputLabel) {
    return new Transition(this.event, this.actor, this.to, this.effects, this.guard,
      this.requiresNote, this.label, this.tone, inputLabel);
  }
}
