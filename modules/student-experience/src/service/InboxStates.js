import { Actor, RequestType } from '../domain/enums.js';
import { TransitionMatrix } from '../engine/TransitionMatrix.js';

/**
 * WHICH STATES ARE WAITING ON WHICH ACTOR — derived from TransitionMatrix at module-load,
 * never hand-written. If an edge is added, moved or removed in the matrix, the inbox follows
 * it on the next start-up.
 */
function build() {
  const out = {};
  for (const type of Object.values(RequestType)) {
    const spec = TransitionMatrix.spec(type);
    for (const [state, transitions] of Object.entries(spec.edges)) {
      for (const t of transitions) {
        if (t.actor === Actor.SYSTEM || t.actor === Actor.STUDENT) continue;
        (out[t.actor] ??= new Set()).add(state);
      }
    }
  }
  return out;
}

const BY_ACTOR = build();

export const InboxStates = {
  /** The states one Actor is the human decision-maker for. */
  awaitingOne(actor) { return BY_ACTOR[actor] ? [...BY_ACTOR[actor]] : []; },

  /** The union over every role a staff member holds — their whole inbox. */
  awaiting(actors) {
    const out = new Set();
    for (const a of actors) for (const s of this.awaitingOne(a)) out.add(s);
    return [...out];
  },
};
