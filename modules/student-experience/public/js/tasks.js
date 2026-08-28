/**
 * My Tasks page enhancements. Everything here is PROGRESSIVE ENHANCEMENT over server-rendered,
 * fully-functional markup: every <form class="act"> still POSTs to the same endpoint and works
 * exactly as it always did if this script is off, fails to load, or throws — nothing here is
 * the only path to completing an action.
 *
 * DELAYED-COMMIT OPTIMISTIC ACTIONS. Clicking Approve/Reject/etc. does NOT call the server. It
 * animates the card away immediately and starts a 5s "Undo" toast; the real POST only fires once
 * that window elapses untouched — this is the Gmail "Undo Send" pattern, not "commit then roll
 * back". The workflow engine has real irreversible side effects (attendance mutation,
 * verification-ID minting, notifications), so there is nothing to safely roll back once
 * committed — deferring the commit sidesteps that entirely: nothing is true until it's true.
 *
 * Surviving a closed tab: every pending action is mirrored into sessionStorage the moment it's
 * queued. If the tab is hidden/closed before its 5s elapses, every still-pending action is fired
 * via navigator.sendBeacon() (fire-and-forget — there's no time to wait for a response) but is
 * NOT removed from sessionStorage yet, since a beacon's outcome can't be observed from JS. The
 * NEXT time this page loads, any leftover sessionStorage entries are resent via a normal fetch()
 * and only cleared on a definite response. A resend against an already-applied transition is
 * safe by construction: RequestStateMachine's transition guard rejects a repeat of an
 * already-fired event with a 409 (see facultyRoutes.js's `/requests/:id/act` handler and
 * RequestStateMachine.js's `select()`) — the reconciliation path treats that as "done", not an
 * error, so a beacon that actually succeeded and a resend racing it can never double-apply.
 */
(function () {
  const main = document.querySelector('main.wrap');
  if (!main) return;

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const UNDO_MS = 5000;
  const STORAGE_KEY = 'campusos.pendingActions';

  let I18N = {};
  const i18nEl = document.getElementById('tasks-i18n');
  if (i18nEl) { try { I18N = JSON.parse(i18nEl.textContent); } catch { I18N = {}; } }

  /* ------------------------------- toasts ------------------------------- */

  const toastStack = document.getElementById('toast-stack');

  function showToast({ text, kind = 'info', actions = [], persist = false, live = true }) {
    if (!toastStack) return { el: null, dismiss() {}, setText() {} };
    const el = document.createElement('div');
    el.className = `toast ${kind}`;
    if (live) el.setAttribute('role', 'status');
    const msg = document.createElement('span');
    msg.className = 'toast-msg';
    msg.textContent = text;
    el.appendChild(msg);
    actions.forEach(({ label, onClick }) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'toast-action';
      btn.textContent = label;
      btn.addEventListener('click', () => { onClick(); dismiss(); });
      el.appendChild(btn);
    });
    toastStack.appendChild(el);

    let timer = null;
    function dismiss() {
      clearTimeout(timer);
      if (el.parentNode) el.parentNode.removeChild(el);
    }
    if (!persist) {
      timer = setTimeout(dismiss, 6000);
      el.addEventListener('mouseenter', () => clearTimeout(timer));
      el.addEventListener('mouseleave', () => { timer = setTimeout(dismiss, 6000); });
    }
    return { el, dismiss, setText: (t) => { msg.textContent = t; } };
  }

  /* --------------------------- pending queue ---------------------------- */

  const pending = new Map(); // requestId -> entry

  function persistQueue() {
    const serializable = [...pending.values()].map((e) => ({
      requestId: e.requestId, event: e.event, note: e.note, csrf: e.csrf, back: e.back,
    }));
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(serializable)); } catch { /* ignore */ }
  }

  function clearPersistedEntry(requestId) {
    try {
      const list = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '[]').filter((e) => e.requestId !== requestId);
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(list));
    } catch { /* ignore */ }
  }

  function sectionCountEl(taskEl) {
    const group = taskEl.closest('.section-group');
    return group ? group.querySelector('summary .count') : null;
  }

  function refreshSectionState(taskEl) {
    const group = taskEl.closest('.section-group');
    if (!group) return;
    const cardsEl = group.querySelector('.cards');
    const visible = [...cardsEl.querySelectorAll('.task')].filter((el) => !el.classList.contains('optimistic-out'));
    const countEl = group.querySelector('summary .count');
    if (countEl) countEl.textContent = String(visible.length);
    const emptyEl = cardsEl.querySelector('.empty');
    if (emptyEl) emptyEl.hidden = visible.length > 0;
  }

  function animateOut(taskEl) {
    if (reduceMotion) { taskEl.classList.add('optimistic-out'); refreshSectionState(taskEl); return; }
    taskEl.classList.add('optimistic-out');
    refreshSectionState(taskEl);
  }

  function animateIn(taskEl) {
    taskEl.classList.remove('optimistic-out');
    refreshSectionState(taskEl);
  }

  function startCountdown(entry) {
    const toast = showToast({
      text: `${I18N.queuedPrefix || ''}${entry.label}`,
      kind: 'action',
      persist: true,
      actions: [{ label: I18N.undo || 'Undo', onClick: () => undo(entry.requestId) }],
    });
    const sub = document.createElement('span');
    sub.className = 'toast-countdown';
    if (toast.el) toast.el.insertBefore(sub, toast.el.lastElementChild);
    function tick() {
      const secs = Math.max(0, Math.ceil((entry.deadline - Date.now()) / 1000));
      sub.textContent = `${I18N.undoingInPrefix || ''}${secs}${I18N.undoingInSuffix || ''}`;
      if (secs <= 0) clearInterval(entry.countdownTimer);
    }
    tick();
    entry.countdownTimer = setInterval(tick, 250);
    entry.toast = toast;
  }

  function enqueueAction(ctx) {
    animateOut(ctx.taskEl);
    const entry = { ...ctx, flushing: false, deadline: Date.now() + UNDO_MS };
    pending.set(ctx.requestId, entry);
    persistQueue();
    startCountdown(entry);
    entry.timerId = setTimeout(() => flush(ctx.requestId), UNDO_MS);
  }

  function undo(requestId) {
    const entry = pending.get(requestId);
    if (!entry || entry.flushing) return;
    clearTimeout(entry.timerId);
    clearInterval(entry.countdownTimer);
    pending.delete(requestId);
    persistQueue();
    if (entry.toast) entry.toast.dismiss();
    animateIn(entry.taskEl);
    showToast({ text: I18N.undone || 'Undone.', kind: 'info' });
  }

  function retry(entry) {
    entry.flushing = false;
    entry.deadline = Date.now();
    pending.set(entry.requestId, entry);
    persistQueue();
    flush(entry.requestId);
  }

  function bodyFor(entry) {
    return new URLSearchParams({ event: entry.event, note: entry.note || '', back: entry.back, _csrf: entry.csrf });
  }

  function flush(requestId, opts) {
    const viaBeacon = opts && opts.viaBeacon;
    const entry = pending.get(requestId);
    if (!entry || entry.flushing) return;
    entry.flushing = true;
    if (entry.countdownTimer) clearInterval(entry.countdownTimer);

    if (viaBeacon) {
      // Fire-and-forget — the tab is going away, there is no time to await a response and no
      // way to react to one. Left in sessionStorage on purpose: reconciliation on next load is
      // what actually confirms this landed (or safely resends it — see the file header).
      const blob = new Blob([bodyFor(entry).toString()], { type: 'application/x-www-form-urlencoded' });
      navigator.sendBeacon(`/faculty/requests/${requestId}/act`, blob);
      return;
    }

    fetch(`/faculty/requests/${requestId}/act`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' },
      body: bodyFor(entry).toString(),
    }).then(async (r) => {
      let payload = null;
      try { payload = await r.json(); } catch { /* non-JSON response (e.g. session expired -> login page) */ }
      pending.delete(requestId);
      clearPersistedEntry(requestId);
      if (entry.toast) entry.toast.dismiss();

      if (r.ok && payload && payload.ok) return; // done — card is already gone

      if (r.status === 409) {
        showToast({ text: (payload && payload.message) || I18N.alreadyHandled, kind: 'info' });
        return; // stays removed: genuinely no longer actionable by this person
      }

      animateIn(entry.taskEl);
      showToast({
        text: (payload && payload.message) || I18N.error, kind: 'error', persist: true,
        actions: [{ label: I18N.retry || 'Retry', onClick: () => retry(entry) }],
      });
    }).catch(() => {
      pending.delete(requestId);
      clearPersistedEntry(requestId);
      if (entry.toast) entry.toast.dismiss();
      animateIn(entry.taskEl);
      showToast({
        text: I18N.error, kind: 'error', persist: true,
        actions: [{ label: I18N.retry || 'Retry', onClick: () => retry(entry) }],
      });
    });
  }

  function flushAllViaBeacon() {
    pending.forEach((entry) => { if (!entry.flushing) flush(entry.requestId, { viaBeacon: true }); });
  }

  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') flushAllViaBeacon(); });
  window.addEventListener('pagehide', flushAllViaBeacon);

  /** Anything left in sessionStorage from a previous load — the tab closed before its beacon's
   * outcome could be confirmed. Resend now via a normal fetch(); safe even if the beacon already
   * succeeded (see file header). */
  function reconcileFromLastSession() {
    let saved = [];
    try { saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '[]'); } catch { saved = []; }
    if (saved.length === 0) return;
    showToast({ text: I18N.reconciling || 'Finishing a pending action from last time…', kind: 'info' });
    saved.forEach((e) => {
      fetch(`/faculty/requests/${e.requestId}/act`, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ event: e.event, note: e.note || '', back: e.back, _csrf: e.csrf }).toString(),
      }).finally(() => clearPersistedEntry(e.requestId));
    });
  }

  /* ---------------------------- action forms ----------------------------- */

  document.addEventListener('submit', (e) => {
    const form = e.target;
    if (!(form instanceof HTMLFormElement) || !form.matches('form.act[data-event]')) return;
    e.preventDefault();

    const noteEl = form.querySelector('textarea[name=note]');
    if (noteEl && noteEl.hasAttribute('required') && !noteEl.value.trim()) {
      form.reportValidity();
      return;
    }

    const taskEl = form.closest('.task');
    if (!taskEl) return;
    const requestId = taskEl.id.replace(/^task-/, '');
    // A task can carry two action buttons (Approve/Reject) sharing one card — guard against a
    // second one firing while the first is already queued, which would silently overwrite the
    // pending Map entry and orphan the first click's timer.
    if (pending.has(requestId)) return;

    enqueueAction({
      requestId,
      event: form.dataset.event,
      note: noteEl ? noteEl.value.trim() : '',
      csrf: form.querySelector('[name=_csrf]').value,
      back: form.querySelector('[name=back]').value,
      label: form.dataset.label,
      taskEl,
    });
  });

  /* ------------------------------ stat strip ------------------------------ */

  function countUp(el) {
    const target = Number(el.dataset.count || el.textContent) || 0;
    if (reduceMotion || target === 0) { el.textContent = String(target); return; }
    const start = performance.now();
    const duration = 600;
    function frame(now) {
      const p = Math.min(1, (now - start) / duration);
      const eased = 1 - (1 - p) * (1 - p); // ease-out
      el.textContent = String(Math.round(target * eased));
      if (p < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }
  document.querySelectorAll('.stat-tile strong[data-count]').forEach(countUp);

  const STAT_TO_SECTION = { awaiting: 'needsAction', overdue: null, resolvedWeek: 'recentlyClosed', totalOpen: null };
  let activeStat = null;
  document.querySelectorAll('.stat-tile').forEach((tile) => {
    tile.addEventListener('click', () => {
      const stat = tile.dataset.stat;
      const isActive = activeStat === stat;
      document.querySelectorAll('.stat-tile').forEach((t) => { t.setAttribute('aria-pressed', 'false'); t.classList.remove('active'); });
      activeStat = isActive ? null : stat;
      if (activeStat) { tile.setAttribute('aria-pressed', 'true'); tile.classList.add('active'); }

      document.querySelectorAll('.task').forEach((taskEl) => {
        if (!activeStat) { taskEl.hidden = false; return; }
        if (activeStat === 'overdue') {
          const cardEl = taskEl.querySelector('.card');
          taskEl.hidden = !cardEl || cardEl.dataset.sla !== 'overdue';
          return;
        }
        if (activeStat === 'totalOpen') {
          const cardEl = taskEl.querySelector('.card');
          taskEl.hidden = !cardEl || !cardEl.dataset.sla; // open cards always carry a data-sla value
          return;
        }
        const wantSection = STAT_TO_SECTION[activeStat];
        const inSection = taskEl.closest('.section-group')?.dataset.section === wantSection;
        taskEl.hidden = !inSection;
      });
      // Make sure a filtered-into section is actually expanded, and open sections have their
      // details opened so a filtered result isn't hidden inside a collapsed accordion.
      if (activeStat) {
        document.querySelectorAll('.section-group').forEach((g) => { g.open = true; });
      }
    });
  });

  /* --------------------------- sort auto-submit --------------------------- */

  const sortSelect = document.getElementById('sort-select');
  if (sortSelect) sortSelect.addEventListener('change', () => sortSelect.form.submit());

  /* ------------------------- collapsible sections -------------------------- */

  document.querySelectorAll('.section-group[data-section]').forEach((group) => {
    const key = `campusos.section.${group.dataset.section}`;
    const saved = (() => { try { return localStorage.getItem(key); } catch { return null; } })();
    if (saved === 'open') group.open = true;
    if (saved === 'closed') group.open = false;
    group.addEventListener('toggle', () => {
      try { localStorage.setItem(key, group.open ? 'open' : 'closed'); } catch { /* ignore */ }
    });
  });

  /* ------------------------------ stepper fx ------------------------------ */

  if (!reduceMotion) {
    document.querySelectorAll('.stepper-fill').forEach((fill) => {
      const target = fill.style.width;
      fill.style.width = '0%';
      // eslint-disable-next-line no-unused-expressions
      fill.offsetHeight; // force reflow so the 0% is painted before the transition starts
      requestAnimationFrame(() => { fill.style.width = target; });
    });
  }

  document.querySelectorAll('.stepper-toggle').forEach((cb) => {
    cb.addEventListener('change', () => {
      cb.setAttribute('aria-expanded', String(cb.checked));
    });
  });

  /* ------------------------------ slide-over ------------------------------ */

  const backdrop = document.getElementById('slideover-backdrop');
  const panel = document.getElementById('slideover');
  const panelTitle = document.getElementById('slideover-title');
  const panelKind = document.getElementById('slideover-kind');
  const panelBody = document.getElementById('slideover-body');
  const panelClose = document.getElementById('slideover-close');
  let slideOverTrigger = null;
  let closeTimer = null; // guards against a pending close's hidden=true landing after a re-open

  function openSlideOver(cardEl, summaryEl) {
    if (!backdrop || !panel || !panelBody) return;
    clearTimeout(closeTimer);
    panel.removeEventListener('transitionend', finishClose);
    slideOverTrigger = summaryEl;
    panelKind.textContent = cardEl.querySelector('.kind')?.textContent || '';
    panelTitle.textContent = cardEl.querySelector('.card-top h3')?.textContent || '';
    panelBody.textContent = '';
    const hist = cardEl.querySelector('.hist');
    if (hist) panelBody.appendChild(hist.cloneNode(true));
    backdrop.hidden = false;
    panel.hidden = false;
    document.body.style.overflow = 'hidden';
    if (reduceMotion) {
      panel.classList.add('open');
    } else {
      // Removing `hidden` alone commits display:none -> flex at translateX(100%) — .open is
      // added on the NEXT frame (after a forced reflow) so the transform transition has a real
      // previous frame to animate from, instead of collapsing both changes into one paint.
      // eslint-disable-next-line no-unused-expressions
      panel.offsetHeight;
      requestAnimationFrame(() => panel.classList.add('open'));
    }
    panelClose.focus();
  }

  function finishClose() { panel.hidden = true; }

  function closeSlideOver() {
    if (!backdrop || !panel) return;
    backdrop.hidden = true;
    panel.classList.remove('open');
    document.body.style.overflow = '';
    if (slideOverTrigger) slideOverTrigger.focus();
    if (reduceMotion) {
      finishClose();
    } else {
      // Wait for the close transition to actually finish before re-adding `hidden` (which
      // snaps display to none) — otherwise the panel would vanish instantly instead of sliding
      // out. The timeout is a fallback in case transitionend never fires (e.g. the panel was
      // already off-screen for some other reason). Both are cleared by openSlideOver() if the
      // panel is reopened before this close finished, so a stale `hidden = true` can never land
      // after a fresh open.
      panel.addEventListener('transitionend', finishClose, { once: true });
      closeTimer = setTimeout(finishClose, 260);
    }
  }

  document.addEventListener('click', (e) => {
    const summary = e.target.closest('.trail > summary');
    if (!summary) return;
    e.preventDefault(); // take over from the native <details> toggle
    const cardEl = summary.closest('.card');
    if (cardEl) openSlideOver(cardEl, summary);
  });

  if (panelClose) panelClose.addEventListener('click', closeSlideOver);
  if (backdrop) backdrop.addEventListener('click', closeSlideOver);
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && panel && !panel.hidden) closeSlideOver();
    if (e.key === 'Tab' && panel && !panel.hidden) {
      // Minimal focus trap: the close button is the only interactive control besides links
      // already inside the cloned trail — Shift+Tab from it wraps back to itself.
      const focusables = panel.querySelectorAll('a, button');
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  });

  /* --------------------------------- init ---------------------------------- */

  reconcileFromLastSession();
})();
