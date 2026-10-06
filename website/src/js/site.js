// Comportamentos comuns do site: menu no celular, tema, copiar, abas, índice da página e busca.
// Tudo funciona sem este script; ele só acrescenta conforto.

const root = document.documentElement.dataset.root || './';
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));

/* ---------- aviso curto ---------- */
let toastTimer = 0;
function toast(msg) {
  let el = $('.toast-site');
  if (!el) {
    el = document.createElement('div');
    el.className = 'toast-site';
    el.setAttribute('role', 'status');
    document.body.append(el);
  }
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 2200);
}

/* ---------- menu no celular ---------- */
const menuBtn = $('.menu-btn');
const nav = $('#site-nav');
if (menuBtn && nav) {
  const set = open => {
    nav.classList.toggle('open', open);
    menuBtn.setAttribute('aria-expanded', String(open));
  };
  menuBtn.addEventListener('click', () => set(!nav.classList.contains('open')));
  nav.addEventListener('click', e => { if (e.target.closest('a')) set(false); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && nav.classList.contains('open')) { set(false); menuBtn.focus(); } });
  document.addEventListener('click', e => { if (nav.classList.contains('open') && !e.target.closest('.site-header')) set(false); });
}

/* ---------- tema: automático, claro ou escuro ---------- */
const ICON = {
  auto: '<rect x="3" y="4.5" width="18" height="12" rx="2"/><path d="M8.5 20h7M12 16.5V20"/>',
  light: '<circle cx="12" cy="12" r="4"/><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"/>',
  dark: '<path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z"/>',
};
const THEME_NAME = { auto: 'automático (segue o aparelho)', light: 'claro', dark: 'escuro' };
const themeBtn = $('#theme-btn');
function readTheme() { try { const t = localStorage.getItem('xdr-theme'); return t === 'light' || t === 'dark' ? t : 'auto'; } catch (e) { return 'auto'; } }
function applyTheme(t) {
  if (t === 'auto') delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = t;
  if (themeBtn) {
    const label = `Tema: ${THEME_NAME[t]}. Trocar o tema`;
    themeBtn.setAttribute('aria-label', label);
    themeBtn.title = label;
    const svg = themeBtn.querySelector('svg');
    if (svg) svg.innerHTML = ICON[t];
  }
}
applyTheme(readTheme());
if (themeBtn) {
  themeBtn.addEventListener('click', () => {
    const next = { auto: 'light', light: 'dark', dark: 'auto' }[readTheme()];
    try { if (next === 'auto') localStorage.removeItem('xdr-theme'); else localStorage.setItem('xdr-theme', next); } catch (e) { /* sem armazenamento: vale só nesta página */ }
    applyTheme(next);
    toast(`Tema ${THEME_NAME[next]}`);
  });
}

/* ---------- copiar ---------- */
async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch (e) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.append(ta);
    ta.select();
    let ok = false;
    try { ok = document.execCommand('copy'); } catch (err) { ok = false; }
    ta.remove();
    return ok;
  }
}
for (const block of $$('.code')) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'copy-btn';
  btn.innerHTML = '<svg class="xi" width="15" height="15" viewBox="0 0 24 24" aria-hidden="true"><rect x="8.5" y="8.5" width="11" height="11" rx="2"/><path d="M15.5 8.5V6A1.5 1.5 0 0 0 14 4.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5"/></svg>Copiar';
  btn.setAttribute('aria-label', 'Copiar o código');
  block.append(btn);
}
document.addEventListener('click', async e => {
  const code = e.target.closest('.copy-btn');
  const attr = e.target.closest('[data-copy]');
  if (code) {
    const ok = await copyText(code.parentElement.querySelector('pre').innerText.replace(/\n$/, ''));
    toast(ok ? 'Código copiado' : 'Não deu para copiar; selecione o texto');
  } else if (attr) {
    const ok = await copyText(attr.dataset.copy);
    toast(ok ? (attr.dataset.copied || 'Copiado') : 'Não deu para copiar; selecione o texto');
  }
});

/* ---------- abas (tour da página inicial) ---------- */
for (const box of $$('[data-tabs]')) {
  const list = $('[role="tablist"]', box);
  const tabs = $$('[role="tab"]', box);
  const panels = tabs.map(t => document.getElementById(t.getAttribute('aria-controls')));
  if (!list || !tabs.length) continue;
  list.hidden = false;
  const select = (i, focus) => {
    tabs.forEach((t, k) => {
      const on = k === i;
      t.setAttribute('aria-selected', String(on));
      t.tabIndex = on ? 0 : -1;
      panels[k].hidden = !on;
    });
    if (focus) tabs[i].focus();
  };
  const start = Math.max(0, tabs.findIndex(t => t.getAttribute('aria-selected') === 'true'));
  select(start, false);
  tabs.forEach((t, i) => {
    t.addEventListener('click', () => select(i, false));
    t.addEventListener('keydown', e => {
      const k = { ArrowRight: 1, ArrowLeft: -1, Home: -Infinity, End: Infinity }[e.key];
      if (k === undefined) return;
      e.preventDefault();
      const n = k === -Infinity ? 0 : k === Infinity ? tabs.length - 1 : (i + k + tabs.length) % tabs.length;
      select(n, true);
    });
  });
}

/* ---------- documentação: páginas no celular e índice da página ---------- */
const sideBtn = $('.docs-side-toggle');
if (sideBtn) {
  const sideNav = $('#docs-nav');
  sideBtn.addEventListener('click', () => {
    const open = !sideNav.classList.contains('open');
    sideNav.classList.toggle('open', open);
    sideBtn.setAttribute('aria-expanded', String(open));
  });
}
const tocLinks = $$('.docs-toc a');
if (tocLinks.length && 'IntersectionObserver' in window) {
  const byId = new Map(tocLinks.map(a => [decodeURIComponent(a.hash.slice(1)), a]));
  const heads = [...byId.keys()].map(id => document.getElementById(id)).filter(Boolean);
  const visible = new Set();
  const update = () => {
    const first = heads.find(h => visible.has(h)) || heads.filter(h => h.getBoundingClientRect().top < 120).pop();
    tocLinks.forEach(a => a.classList.toggle('on', first && a.hash === `#${first.id}`));
  };
  const io = new IntersectionObserver(entries => {
    for (const en of entries) { if (en.isIntersecting) visible.add(en.target); else visible.delete(en.target); }
    update();
  }, { rootMargin: '-80px 0px -60% 0px' });
  heads.forEach(h => io.observe(h));
}

/* ---------- busca ---------- */
const dialog = $('#busca');
const input = $('#busca-q');
const results = $('#busca-res');
let searcher = null;
let active = -1;
async function ensureSearch() {
  if (searcher) return searcher;
  const mod = await import(new URL('./search.js', import.meta.url));
  searcher = await mod.createSearch(root);
  return searcher;
}
function escapeHtml(s) { return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
const ACC = { a: 'aáàâãä', e: 'eéèêë', i: 'iíìîï', o: 'oóòôõö', u: 'uúùûü', c: 'cç', n: 'nñ' };
/** Expressão que acha os termos da busca no texto, com ou sem acento. */
function termRegex(q) {
  const parts = q.split(/\s+/).filter(t => t.length >= 2).map(t => [...t.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()]
    .map(ch => (ACC[ch] ? `[${ACC[ch]}]` : ch.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))).join(''));
  return parts.length ? new RegExp(`(${parts.join('|')})`, 'gi') : null;
}
function mark(text, re) {
  if (!re) return escapeHtml(text);
  return text.split(re).map((piece, i) => (i % 2 ? `<mark>${escapeHtml(piece)}</mark>` : escapeHtml(piece))).join('');
}
async function runSearch() {
  const q = input.value.trim();
  active = -1;
  if (!q) { results.innerHTML = '<p class="search-hint">Digite para buscar em todas as páginas da documentação, nos ajustes do app e nas chaves do TOML.</p>'; return; }
  let s;
  try { s = await ensureSearch(); } catch (e) { results.innerHTML = '<p class="search-empty">Não deu para carregar a busca. Tente de novo ou use o índice da documentação.</p>'; return; }
  const hits = s.search(q).slice(0, 12);
  if (!hits.length) { results.innerHTML = `<p class="search-empty">Nada encontrado para “${escapeHtml(q)}”.</p>`; return; }
  const re = termRegex(q);
  results.innerHTML = `<ol role="listbox" aria-label="Resultados">${hits.map((h, i) => `<li role="none"><a role="option" id="r-${i}" aria-selected="false" href="${root}${h.u}"><span class="r-page">${escapeHtml(h.p)}</span><span class="r-title">${mark(h.t, re)}</span>${h.x ? `<span class="r-text">${mark(h.x.slice(0, 200), re)}</span>` : ''}</a></li>`).join('')}</ol>`;
}
function moveActive(d) {
  const opts = $$('[role="option"]', results);
  if (!opts.length) return;
  active = (active + d + opts.length) % opts.length;
  opts.forEach((o, i) => o.setAttribute('aria-selected', String(i === active)));
  input.setAttribute('aria-activedescendant', opts[active].id);
  opts[active].scrollIntoView({ block: 'nearest' });
}
function openSearch() {
  if (!dialog) return;
  if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open', '');
  input.focus();
  input.select();
  ensureSearch().catch(() => {});
}
if (dialog) {
  let timer = 0;
  input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(runSearch, 80); });
  input.addEventListener('keydown', e => {
    if (e.key === 'ArrowDown') { e.preventDefault(); moveActive(1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); moveActive(-1); }
    else if (e.key === 'Enter') {
      const opts = $$('[role="option"]', results);
      const target = opts[active >= 0 ? active : 0];
      if (target) { e.preventDefault(); location.href = target.href; dialog.close(); }
    }
  });
  dialog.addEventListener('click', e => { if (e.target === dialog) dialog.close(); });
  results.addEventListener('click', e => { if (e.target.closest('a')) dialog.close(); });
  for (const b of $$('[data-close-search]')) b.addEventListener('click', () => dialog.close());
}
for (const b of $$('[data-open-search]')) b.addEventListener('click', openSearch);
document.addEventListener('keydown', e => {
  const t = e.target;
  const typing = t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName);
  if (t.closest && t.closest('.sim')) return; // o simulador tem o próprio teclado
  if ((e.key === '/' && !typing) || (e.key.toLowerCase() === 'k' && (e.ctrlKey || e.metaKey))) {
    e.preventDefault();
    openSearch();
  }
});
