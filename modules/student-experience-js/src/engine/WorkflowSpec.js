/**
 * Everything the UI needs to render a workflow it knows nothing about: the on-path steps
 * for the timeline, the off-path (rejection/return) states, which states are terminal, and
 * human labels. Templates read this, never a type switch.
 */
export class WorkflowSpec {
  constructor(label, initial, steps, offPath, terminalTone, stateLabels, edges) {
    this.label = label;
    this.initial = initial;
    this.steps = steps;               // [{key, label}]
    this.offPath = offPath;           // { state: {label, tone} }
    this.terminalTone = terminalTone; // { state: tone }
    this.stateLabels = stateLabels;   // { state: label }
    this.edges = edges;               // { state: Transition[] }
  }

  isTerminal(state) { return Object.prototype.hasOwnProperty.call(this.terminalTone, state); }

  labelFor(state) { return this.stateLabels[state] ?? state; }

  from(state) { return this.edges[state] ?? []; }
}
