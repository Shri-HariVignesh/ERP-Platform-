/**
 * The Helper widget. This is the ONLY script the CSP admits (script-src is a per-request
 * nonce — see securityHeaders.js) and it is intentionally small and self-contained:
 *   - no fetch/XHR/WebSocket — it never talks to the network, so there is nothing here to
 *     exfiltrate through even if this file were somehow swapped;
 *   - no eval, no innerHTML — topic content is read from a same-origin <script type=
 *     "application/json"> island the server rendered and is placed into the DOM with
 *     textContent, never as markup;
 *   - one job — toggle the panel, filter a static topic list by substring. It does not
 *     replace or fight the server-rendered page; every link it shows is a normal <a href>.
 */
(function () {
  const root = document.getElementById('helper');
  if (!root) return;

  const toggle = document.getElementById('helper-toggle');
  const panel = document.getElementById('helper-panel');
  const search = document.getElementById('helper-search');
  const list = document.getElementById('helper-list');
  const dataEl = document.getElementById('helper-topics');
  if (!toggle || !panel || !search || !list || !dataEl) return;

  let topics = [];
  try {
    topics = JSON.parse(dataEl.textContent);
  } catch {
    topics = [];
  }

  function renderTopics(items) {
    list.textContent = '';
    for (const topic of items) {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.href = topic.href;
      const q = document.createElement('strong');
      q.textContent = topic.q;
      const p = document.createElement('span');
      p.textContent = topic.a;
      a.appendChild(q);
      a.appendChild(p);
      li.appendChild(a);
      list.appendChild(li);
    }
    if (items.length === 0) {
      const li = document.createElement('li');
      li.className = 'helper-empty';
      li.textContent = 'No matching topic — try a different word.';
      list.appendChild(li);
    }
  }

  function openPanel() {
    panel.hidden = false;
    toggle.setAttribute('aria-expanded', 'true');
    search.focus();
  }

  function closePanel() {
    panel.hidden = true;
    toggle.setAttribute('aria-expanded', 'false');
  }

  toggle.addEventListener('click', () => {
    if (panel.hidden) openPanel();
    else closePanel();
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !panel.hidden) closePanel();
  });

  search.addEventListener('input', () => {
    const term = search.value.trim().toLowerCase();
    const filtered = term
      ? topics.filter((t) => t.q.toLowerCase().includes(term) || t.a.toLowerCase().includes(term))
      : topics;
    renderTopics(filtered);
  });

  renderTopics(topics);
})();
