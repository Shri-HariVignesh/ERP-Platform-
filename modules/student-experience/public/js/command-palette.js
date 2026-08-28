/**
 * The ⌘K / Ctrl+K command palette. Global — one instance, included from faculty/header.ejs, so
 * it works from any faculty page. Static "Go to X" nav actions are read from a same-origin
 * <script type="application/json"> island the server rendered (see fragments/commandPalette.ejs
 * — same technique as helper.js's topic list); student/task results come from a same-origin
 * fetch('/faculty/search.json?q=…') as the user types, since the roster and inbox aren't
 * embedded wholesale into every page's HTML.
 *
 * Content is placed into the DOM with textContent/createElement, never innerHTML — nothing here
 * renders anything the server didn't already render safely elsewhere.
 */
(function () {
  const trigger = document.getElementById('cmdk-trigger');
  const backdrop = document.getElementById('cmdk-backdrop');
  const palette = document.getElementById('cmdk');
  const input = document.getElementById('cmdk-input');
  const results = document.getElementById('cmdk-results');
  const dataEl = document.getElementById('cmdk-data');
  if (!trigger || !backdrop || !palette || !input || !results || !dataEl) return;

  let data = { navActions: [], labels: {} };
  try {
    data = JSON.parse(dataEl.textContent);
  } catch {
    // no-op — palette still opens, just with nothing to show
  }

  let lastFocused = null;
  let items = []; // flat list of {el, href} in the order they're rendered, for arrow-key nav
  let activeIndex = -1;
  let debounceTimer = null;
  let requestSeq = 0; // guards against an out-of-order fetch response overwriting a newer one

  function isOpen() { return !palette.hidden; }

  function open() {
    lastFocused = document.activeElement;
    backdrop.hidden = false;
    palette.hidden = false;
    document.body.style.overflow = 'hidden';
    input.value = '';
    renderNav('');
    input.focus();
  }

  function close() {
    backdrop.hidden = true;
    palette.hidden = true;
    document.body.style.overflow = '';
    if (lastFocused && typeof lastFocused.focus === 'function') lastFocused.focus();
  }

  function clearResults() {
    results.textContent = '';
    items = [];
    activeIndex = -1;
  }

  function addGroup(label) {
    const h = document.createElement('div');
    h.className = 'cmdk-group';
    h.textContent = label;
    results.appendChild(h);
  }

  function addItem(primary, secondary, href) {
    const a = document.createElement('a');
    a.className = 'cmdk-item';
    a.href = href;
    a.setAttribute('role', 'option');
    a.tabIndex = -1;
    const p = document.createElement('span');
    p.className = 'cmdk-item-primary';
    p.textContent = primary;
    a.appendChild(p);
    if (secondary) {
      const s = document.createElement('span');
      s.className = 'cmdk-item-secondary';
      s.textContent = secondary;
      a.appendChild(s);
    }
    results.appendChild(a);
    items.push(a);
    return a;
  }

  function setActive(i) {
    if (items[activeIndex]) items[activeIndex].classList.remove('active');
    activeIndex = i;
    if (items[activeIndex]) {
      items[activeIndex].classList.add('active');
      items[activeIndex].scrollIntoView({ block: 'nearest' });
    }
  }

  function noResults() {
    const p = document.createElement('p');
    p.className = 'cmdk-empty';
    p.textContent = data.labels.noResults || 'No matches.';
    results.appendChild(p);
  }

  function renderNav(term) {
    clearResults();
    const q = term.trim().toLowerCase();
    const matches = data.navActions.filter((a) => !q || a.label.toLowerCase().includes(q));
    if (matches.length > 0) {
      addGroup(data.labels.goTo || 'Go to');
      matches.forEach((a) => addItem(a.label, null, a.href));
    }
    if (!q) return; // no query yet — nav actions alone are the whole palette
    if (matches.length === 0) noResults();
  }

  function renderServerResults(term, payload) {
    // Nav actions still show above server results, filtered by the same term.
    renderNav(term);
    const hadNav = items.length > 0;
    if (payload.students.length > 0) {
      addGroup(data.labels.students || 'Students');
      payload.students.forEach((s) => addItem(s.name, s.rollNo, s.href));
    }
    if (payload.tasks.length > 0) {
      addGroup(data.labels.tasks || 'Tasks');
      payload.tasks.forEach((tk) => addItem(tk.title, `${tk.studentName} · ${tk.typeLabel}`, tk.href));
    }
    if (!hadNav && payload.students.length === 0 && payload.tasks.length === 0) noResults();
  }

  function search(term) {
    const q = term.trim();
    if (!q) { renderNav(''); return; }
    const seq = ++requestSeq;
    fetch(`/faculty/search.json?q=${encodeURIComponent(q)}`, { headers: { Accept: 'application/json' } })
      .then((r) => (r.ok ? r.json() : { students: [], tasks: [] }))
      .then((payload) => {
        if (seq !== requestSeq) return; // a newer keystroke's response already landed
        renderServerResults(term, payload);
      })
      .catch(() => {
        if (seq !== requestSeq) return;
        renderNav(term);
      });
  }

  trigger.addEventListener('click', open);

  document.addEventListener('keydown', (e) => {
    const meta = e.metaKey || e.ctrlKey;
    if (meta && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      if (isOpen()) close(); else open();
      return;
    }
    if (!isOpen()) return;
    if (e.key === 'Escape') { e.preventDefault(); close(); return; }
    if (e.key === 'ArrowDown') { e.preventDefault(); if (items.length) setActive((activeIndex + 1) % items.length); return; }
    if (e.key === 'ArrowUp') { e.preventDefault(); if (items.length) setActive((activeIndex - 1 + items.length) % items.length); return; }
    if (e.key === 'Enter') {
      e.preventDefault();
      const target = items[activeIndex] || items[0];
      if (target) window.location.href = target.href;
      return;
    }
    if (e.key === 'Tab') {
      // Focus trap: the input is the only real tab stop inside the palette — Tab/Shift+Tab
      // both just keep focus on it rather than escaping to the page underneath.
      e.preventDefault();
      input.focus();
    }
  });

  backdrop.addEventListener('click', close);

  input.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    const term = input.value;
    debounceTimer = setTimeout(() => search(term), 150);
  });
})();
