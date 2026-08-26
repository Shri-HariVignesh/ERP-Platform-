import { Router } from 'express';
import { Actor } from '../domain/enums.js';
import { RequestService } from '../service/RequestService.js';
import { ScopeResolver } from '../service/ScopeResolver.js';
import { IllegalTransitionException } from '../engine/IllegalTransitionException.js';
import { requireStudent } from './middleware/auth.js';
import { redirectAfterSave } from './middleware/sessionRedirect.js';
import { resolve } from './safeRedirect.js';

export const actionsRoutes = Router();
actionsRoutes.use(requireStudent);

/**
 * SECURITY (CWE-1284): `input` never passes through the sized Forms validators, so it is
 * bounded here to the same limit the internship form puts on certificateFilename.
 */
const MAX_INPUT = 160;

/** The only per-type code path, and it lives in code, not in a template. */
function patchFor(r, input) {
  if (!input || !input.trim()) return null;
  if (input.length > MAX_INPUT) throw new IllegalTransitionException(`input exceeds ${MAX_INPUT} characters`);
  if (r.type === 'INTERNSHIP') {
    return (p) => {
      p.certificateRef = { filename: input.trim(), mime: 'application/pdf', sizeKb: 312 };
      p.sys.returnCount++;
    };
  }
  return null;
}

/**
 * The single endpoint behind every conditional student action button, for every request type.
 * The button itself is declared on the edge in TransitionMatrix, so no template knows what a
 * student may do — it just renders card.studentAction if the matrix produced one.
 */
actionsRoutes.post('/:id', (req, res) => {
  const s = ScopeResolver.current(req.session.principal);
  const { event, note, input, back } = req.body;
  try {
    const r = RequestService.raw(s, req.params.id);
    RequestService.transition(s, req.params.id, event, Actor.STUDENT, note, patchFor(r, input));
    req.session.flash = 'Done — your request has moved on.';
  } catch (e) {
    if (!(e instanceof IllegalTransitionException)) throw e;
    console.warn('student action rejected:', e.message);
    req.session.error = 'That is no longer available on this request.';
  }
  redirectAfterSave(req, res, resolve(back ?? '/requests'));
});
