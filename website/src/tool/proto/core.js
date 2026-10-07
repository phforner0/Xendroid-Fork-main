/* Núcleo do simulador (do protótipo B+C do app): estado, telas, ações, folhas, navegação
   (toque, teclado, controle) e o visor. O teclado só age com o foco na tela do aparelho. */
'use strict';

/* ============ estado ============ */
const saved = store.get('sim', {});
const S = {
  route: { name: 'library', p: {} }, stack: [],
  modePref: 'auto', mode: 'b', orient: 'land', notes: false, full: false,
  gameId: '4D5309C9', sec: {},
  lf: 'all', sort: 'az', q: '', bDensity: 'M', bSel: '4D5309C9',
  cBlade: 'recent', cIndex: 0,
  editScope: 'game', setQ: '', setF: 'all', setLevel: 1, showDesc: true,
  modal: null, mp: {}, guide: false, toast: null, nav: false, pad: false, compact: false, swapAB: false,
};
if (!('orient' in saved) && innerWidth < 700) S.orient = 'port';
for (const k of ['orient', 'notes', 'bDensity', 'modePref']) if (k in saved) S[k] = saved[k];
if (saved.route && typeof saved.route === 'string') S.route = { name: saved.route, p: {} };
const persist = () => store.set('sim', { orient: S.orient, notes: S.notes, bDensity: S.bDensity, modePref: S.modePref, route: S.route.name });

const FAVS = new Set(GAMES.filter(g => g.fav).map(g => g.id));
const PON = Object.fromEntries(GAMES.map(g => [g.id, new Set(g.pOn)]));
/* estado de exemplo: um driver escolhido no global e alguns jogos com ajustes próprios */
const GLOBAL = { 'Vulkan.vulkan_lib_path': 'turnip-a' };
const OV = {
  '4D5309C9': { 'GPU.framerate_limit': '30', 'Display.postprocess_scaling_and_sharpening': 'fsr' },
  '4D5308AB': { 'GPU.anisotropic_override': '3' },
  '5454082B': { 'APU.apu_aaudio_adaptive_buffer': true },
};
let PINNED = VERSION.fixados.filter(k => DEF[k]);
let UNDO = null;

/* ============ registros ============ */
const SCREENS = {}, ACT = {}, MODALS = {}, NOTES = {};
const screen = (name, def) => { SCREENS[name] = def; };
const action = (name, fn) => { ACT[name] = fn; };
const modal = (name, fn) => { MODALS[name] = fn; };
const LOTES = XDR.grupos;
const groupOf = n => (LOTES.find(l => l[1].includes(n)) || [''])[0];

/* ============ modo toque (B) / controle (C) e opções do app ============ */
function wantMode() { return S.modePref === 'auto' ? (S.pad ? 'c' : 'b') : S.modePref; }
function applyMode(silent) { const m = wantMode(); if (m !== S.mode) { S.mode = m; if (!silent) render(); } }
function applyApp(k) {
  if (k === '@app.mode') { S.modePref = globalOf(k); persist(); applyMode(true); }
  if (k === '@app.level') S.setLevel = Number(globalOf(k));
  if (k === '@app.uisize') document.getElementById('app').style.zoom = String(globalOf(k) / 100);
  if (k === '@app.confirm') S.swapAB = globalOf(k) === 'b';
}

/* ============ rotas ============ */
function go(name, p = {}, opts = {}) {
  if (!SCREENS[name]) { toast('Esta tela não existe no simulador.'); return; }
  const doIt = () => { if (!opts.replace) S.stack.push(S.route); S.route = { name, p }; S.modal = null; S.guide = false; S.toast = null; S.compact = false; persist(); render(); focusStart(); };
  if (opts.vt) transition(doIt, opts.from); else doIt();
}
function goTop(name, p = {}) {
  if (!SCREENS[name]) { toast('Esta tela não existe no simulador.'); return; }
  S.stack = name === 'library' ? [] : [{ name: 'library', p: {} }]; S.route = { name, p }; S.modal = null; S.guide = false; S.toast = null; persist(); render(); focusStart();
}
function back() {
  if (S.modal) { S.modal = null; render(); return; }
  if (S.guide) { S.guide = false; render(); return; }
  const r = SCREENS[S.route.name]; if (r && r.onBack && r.onBack()) return;
  const prev = S.stack.pop();
  if (prev) { const from = S.route.name; transition(() => { S.route = prev; persist(); render(); restoreFrom(from); }, null); }
  else if (S.route.name !== 'library') { S.route = { name: 'library', p: {} }; persist(); render(); }
}
function restoreFrom(from) {
  if (from === 'game' && S.route.name === 'library') {
    const cv = app.querySelector(`.cover[data-gid="${S.gameId}"]`); const card = cv && cv.closest('button');
    if (cv && document.startViewTransition && !reduced) { cv.style.viewTransitionName = 'hero-cover'; setTimeout(() => { cv.style.viewTransitionName = ''; }, 700); }
    if (card && S.nav) card.focus({ preventScroll: false });
  }
}
function focusStart() { if (!S.nav) return; const f = app.querySelector('.sheet [data-autofocus]') || app.querySelector('[data-autofocus]') || app.querySelector('.c-item.on'); if (f) f.focus({ preventScroll: true }); }
function transition(fn, from) {
  if (!document.startViewTransition || reduced) { fn(); return; }
  const cv = from ? (from.classList.contains('cover') ? from : from.querySelector('.cover')) : null;
  if (cv) cv.style.viewTransitionName = 'hero-cover';
  try { document.startViewTransition(() => { if (cv) cv.style.viewTransitionName = ''; fn(); }); } catch (e) { fn(); }
}

/* ============ render ============ */
const app = document.getElementById('app');
let ready = false;
function guideHTML() {
  if (!S.guide) return '';
  const p = activeProfile();
  const items = [['library', 'Biblioteca', 'grid'], ['content', 'Conteúdo', 'box'], ['profiles', 'Perfis', 'user'], ['controls', 'Controles', 'gamepad'], ['drivers', 'Drivers', 'chip'], ['settings', 'Configurações', 'gear'], ['diagnostics', 'Diagnóstico', 'bug'], ['compare', 'Comparar execuções', 'ab'], ['about', 'Sobre', 'info']];
  return `<div class="scrim" data-act="guide"></div><nav class="guide" aria-label="Menu" data-note="guide">
    <div class="gh2">${avatarImg(p, '')}<div><b>${esc(p.tag)}</b><small>Ativo · P1</small></div></div>
    <ul class="menu" style="list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:2px">${items.map(([v, t, i], n) => `<li><button class="c-mi" style="width:100%" data-act="guide-go" data-v="${v}" data-k="gd-${v}"${n === 0 ? ' data-autofocus' : ''}>${ic(i, 19)}${t}</button></li>`).join('')}</ul>
    <span class="sp"></span>
    <button class="c-mi" data-act="mode-toggle" data-k="gd-mode">${ic(S.mode === 'c' ? 'hand' : 'gamepad', 19)}${S.mode === 'c' ? 'Usar o modo toque' : 'Usar o modo controle'}</button>
  </nav>`;
}
function modalHTML() { if (!S.modal || !MODALS[S.modal]) return ''; const r = MODALS[S.modal](S.mp || {}); return `<div class="scrim" data-act="close"></div><div class="sheet ${r.wide ? 'wide' : ''}" role="dialog" aria-modal="true">${r.html}</div>`; }
function toastHTML() { const t = S.toast; if (!t) return ''; return `<div class="toast" role="status"><span>${esc(t.msg)}</span>${t.undo ? '<button data-act="undo" data-k="t-undo">Desfazer</button>' : ''}</div>`; }
function sheetHead(t, sub) { return `<header><h2>${t}</h2><button class="ibtn" data-act="close" data-k="m-close" aria-label="Fechar">${ic('x')}</button></header>${sub ? `<p class="sub">${sub}</p>` : ''}`; }
function render() {
  if (!ready) { app.className = 'app mode-' + S.mode; app.innerHTML = '<div class="empty" style="padding-top:38%">Gerando a arte de exemplo…</div>'; return; }
  if (!SCREENS[S.route.name]) S.route = { name: 'library', p: {} };
  const ae = document.activeElement, fk = ae && app.contains(ae) && ae.dataset ? ae.dataset.k : null;
  const caret = ae && fk && 'selectionStart' in ae && ae.type !== 'range' ? [ae.selectionStart, ae.selectionEnd] : null;
  const scrolls = {}; app.querySelectorAll('[data-sk]').forEach(el => { scrolls[el.dataset.sk] = [el.scrollLeft, el.scrollTop]; });
  app.className = `app mode-${S.mode}${S.nav ? ' nav' : ''}`;
  const scr = SCREENS[S.route.name];
  app.innerHTML = scr.render() + guideHTML() + modalHTML() + toastHTML();
  app.querySelectorAll('[data-sk]').forEach(el => { const s = scrolls[el.dataset.sk]; if (s) { el.scrollLeft = s[0]; el.scrollTop = s[1]; } });
  if (scr.after) scr.after();
  let target = fk ? app.querySelector(`[data-k="${CSS.escape(fk)}"]`) : null;
  const layer = app.querySelector('.sheet') || app.querySelector('.guide');
  if (layer && (!target || !layer.contains(target))) target = layer.querySelector('[data-autofocus]') || layer.querySelector('button, input');
  if (target) { try { target.focus({ preventScroll: true }); if (caret && target.setSelectionRange) target.setSelectionRange(caret[0], caret[1]); } catch (e) { /* sem foco */ } }
  updateChrome(); schedulePins();
}
function refreshList() { const box = document.getElementById('setp-list'); if (!box) return; box.innerHTML = setListHTML(box.dataset.global ? null : curGame(), box.dataset.v, box.dataset.group || null); schedulePins(); }
let toastTimer = 0;
function toast(msg, undo) { S.toast = { msg, undo: !!undo }; clearTimeout(toastTimer); toastTimer = setTimeout(() => { S.toast = null; const t = app.querySelector('.toast'); if (t) t.remove(); }, undo ? 5200 : 2800); render(); }
function focusKey(k) { const el = app.querySelector(`[data-k="${CSS.escape(k)}"]`); if (el) { el.focus({ preventScroll: true }); reveal(el); } }
/* rola só as listas dentro do aparelho até o elemento aparecer (scrollIntoView rolaria a página do protótipo também) */
function reveal(el, smooth) {
  for (let p = el.parentElement; p && p !== app.parentElement; p = p.parentElement) {
    const cs = getComputedStyle(p);
    if (!/(auto|scroll)/.test(cs.overflowY + cs.overflowX) || (p.scrollHeight <= p.clientHeight && p.scrollWidth <= p.clientWidth)) continue;
    const r = el.getBoundingClientRect(), pr = p.getBoundingClientRect(), sc = pr.width / (p.offsetWidth || 1) || 1;
    let dy = 0, dx = 0;
    if (r.top < pr.top) dy = (r.top - pr.top) / sc - 8; else if (r.bottom > pr.bottom) dy = Math.min((r.bottom - pr.bottom) / sc + 8, (r.top - pr.top) / sc - 8);
    if (r.left < pr.left) dx = (r.left - pr.left) / sc - 8; else if (r.right > pr.right) dx = Math.min((r.right - pr.right) / sc + 8, (r.left - pr.left) / sc - 8);
    if (dx || dy) p.scrollBy({ left: dx, top: dy, behavior: smooth && !reduced ? 'smooth' : 'auto' });
  }
}

/* ============ ações comuns ============ */
action('toast', el => toast(el.dataset.msg || ''));
action('noop', () => {});
action('back', () => back());
action('close', () => { S.modal = null; render(); });
action('modal', el => { S.modal = el.dataset.v; S.mp = Object.assign({}, el.dataset); render(); });
action('go', el => go(el.dataset.v, { gid: el.dataset.p }));
action('rail', el => { const v = el.dataset.v; if (v === 'collections') { S.lf = 'col:' + COLS[0]; S.sort = 'az'; goTop('library'); } else if (v === 'library') { if (S.lf.startsWith('col:')) S.lf = 'all'; goTop('library'); } else goTop(v); });
action('guide', () => { S.guide = !S.guide; render(); });
action('guide-go', el => { S.guide = false; el.dataset.v === 'library' ? goTop('library') : goTop(el.dataset.v); });
action('mode-toggle', () => { S.guide = false; S.modePref = S.mode === 'c' ? 'b' : 'c'; GLOBAL['@app.mode'] = S.modePref; persist(); applyMode(true); render(); });
action('sec', el => { const scr = SCREENS[S.route.name]; if (scr.onSec && scr.onSec(el.dataset.v)) return; S.sec[el.dataset.key] = el.dataset.v; render(); const b = document.getElementById('secbody') || document.getElementById('c-panel'); if (b) b.scrollTop = 0; });
/* telas de ajustes globais (Configurações, Controles) editam o global; a ficha edita o jogo */
const scopeG = () => { const s = SCREENS[S.route.name]; return s && s.globalScope ? null : curGame(); };
action('set', el => { setVal(scopeG(), el.dataset.key, el.dataset.v); render(); });
action('step', (el, e) => { if (e) e.stopPropagation(); stepVal(scopeG(), el.dataset.key, Number(el.dataset.d)); render(); });
action('crow', el => { const g = scopeG(); const d = DEF[el.dataset.key]; if (el.getAttribute('aria-disabled') === 'true') { toast(depText(d)); return; } stepVal(g, d.k, 1, true); render(); });
action('reset', el => { resetVal(scopeG(), el.dataset.key); render(); });
action('reset-all', () => { const g = curGame(); UNDO = { id: g.id, ov: Object.assign({}, OV[g.id]) }; OV[g.id] = {}; toast(`Todos os ajustes de ${g.name} voltaram ao global`, true); });
action('undo', () => { if (UNDO) { if (UNDO.global) Object.assign(GLOBAL, UNDO.global); else OV[UNDO.id] = UNDO.ov; UNDO = null; } S.toast = null; render(); });
action('pin', el => { const k = el.dataset.key; const i = PINNED.indexOf(k); if (i >= 0) PINNED.splice(i, 1); else PINNED.push(k); toast(i >= 0 ? 'Tirado dos ajustes rápidos' : 'Fixado nos ajustes rápidos'); });
action('scope', el => { S.editScope = el.dataset.v; render(); });
action('level', el => { S.setLevel = Number(el.dataset.v); render(); });
action('setf', el => { S.setF = el.dataset.v; render(); });
action('desc', () => { S.showDesc = !S.showDesc; render(); });
action('clear-set', () => { S.setF = 'all'; S.setQ = ''; S.setLevel = 3; render(); });
action('ovlist', el => { S.modal = 'ovlist'; S.mp = { key: el.dataset.key }; render(); });
action('patch', el => { const g = curGame(), s = PON[g.id], v = el.dataset.v; if (s.has(v)) s.delete(v); else s.add(v); render(); });
action('fav', el => { const g = GBY[el.dataset.gid] || curGame(); if (FAVS.has(g.id)) FAVS.delete(g.id); else FAVS.add(g.id); toast(FAVS.has(g.id) ? 'Adicionado aos favoritos' : 'Removido dos favoritos'); });
action('play', el => { const g = GBY[el.dataset.gid] || curGame(); S.gameId = g.id; S.modal = null; if (SCREENS.loading) go('loading', { gid: g.id }); else toast(`Abrindo ${g.name} como ${activeProfile().tag}… (simulação)`); });
action('preset', el => { const g = curGame(), p = PRESETS.find(x => x.id === el.dataset.v); UNDO = { id: g.id, ov: Object.assign({}, OV[g.id]) }; const keep = S.editScope; S.editScope = 'game'; for (const [k, val] of Object.entries(p.set)) setVal(g, k, val); S.editScope = keep; S.modal = null; toast(`Predefinição ${p.t} aplicada em ${g.name}`, true); });
action('copy-text', el => { const pre = document.getElementById('toml-pre'); if (!pre) { toast('Nada para copiar'); return; } const t = pre.textContent; try { navigator.clipboard.writeText(t).then(() => toast('Copiado'), () => { selectNode(pre); toast('Texto selecionado; copie pelo menu do sistema'); }); } catch (e) { selectNode(pre); } });
action('col-toggle', el => { const g = curGame(), v = el.dataset.v, i = g.cols.indexOf(v); if (i >= 0) g.cols.splice(i, 1); else g.cols.push(v); render(); });
action('cache-clear', () => { const g = curGame(); S.modal = null; toast(`Cache de shaders limpo: ${g.cache[1]} MB`); g.cache = [0, 0]; render(); });
action('rate-pick', el => { S.mp.rs = el.dataset.v; el.parentElement.querySelectorAll('.radio').forEach(b => b.setAttribute('aria-checked', String(b === el))); const sv = app.querySelector('[data-k="rt-save"]'); if (sv) sv.disabled = false; });
action('rate', () => { const g = curGame(); if (!S.mp.rs) return; const note = (document.getElementById('rate-note') || {}).value || ''; g.compat = { s: S.mp.rs, note: note.trim().slice(0, 500), date: new Date().toLocaleDateString('pt-BR'), build: XDR.versoes.estavel ? XDR.versoes.estavel.commit : 'local', gpu: DRIVERS.gpu + ' (exemplo)', drv: label(DEF['Vulkan.vulkan_lib_path'], effOf(g, 'Vulkan.vulkan_lib_path')) }; S.modal = null; toast('Avaliação guardada neste aparelho'); render(); });
action('tg-local', el => { el.setAttribute('aria-checked', el.getAttribute('aria-checked') === 'true' ? 'false' : 'true'); });
action('lf', el => { S.lf = el.dataset.v; render(); });
action('sort', el => { S.sort = el.dataset.v; render(); });
action('hint', el => fire({ tabs: 'rb', sort: 'start', guide: 'start', open: 'x', fav: 'y', back: 'b', play: 'play', a: 'a' }[el.dataset.v] || el.dataset.v));
function selectNode(n) { if (!n) return; const r = document.createRange(); r.selectNodeContents(n); const s = getSelection(); s.removeAllRanges(); s.addRange(r); }

/* ============ folhas comuns ============ */
function launchBody(g) {
  const own = ovCount(g), on = PON[g.id].size;
  const drv = DEF['Vulkan.vulkan_lib_path'];
  return `${PROFILES.length > 1 ? `<div class="opt"><div><b>Perfil</b><small>Quem entra no jogo nesta abertura</small></div><select class="sel" data-k="l-prof" aria-label="Perfil">${PROFILES.map(p => `<option${p.active ? ' selected' : ''}>${esc(p.tag)}</option>`).join('')}</select></div>` : ''}
    <div class="opt" data-note="ldrv"><div><b>Driver</b><small>Configurado para este jogo: ${esc(label(drv, effOf(g, 'Vulkan.vulkan_lib_path')))}</small></div><select class="sel" data-k="l-drv" aria-label="Driver"><option>Como está configurado</option>${drv.o.map(o => `<option>${esc(o[1])}</option>`).join('')}</select></div>
    <div class="opt"><div><b>Executável</b><small>launch_module: outro .xex do disco ou pacote</small></div><input class="txt" type="text" maxlength="260" placeholder="default.xex" data-k="l-xex" aria-label="Executável (launch_module)" autocomplete="off" spellcheck="false"></div>
    <div class="opt"><div><b>Sem patches</b><small>${on ? `Abre sem aplicar ${on === 1 ? 'o 1 patch ativo' : `os ${on} patches ativos`}` : 'Nenhum patch ativo neste jogo'}</small></div><button class="tg" role="switch" aria-checked="false" data-act="tg-local" data-k="l-nopatch" aria-label="Sem patches"${on ? '' : ' disabled'}></button></div>
    <div class="opt"><div><b>Ignorar os ajustes deste jogo</b><small>${own ? `Usa o global no lugar ${own === 1 ? 'do 1 ajuste próprio, para ver se ele causa o problema' : `dos ${own} ajustes próprios, para ver se algum causa o problema`}` : 'Este jogo não tem ajustes próprios.'}</small></div><button class="tg" role="switch" aria-checked="false" data-act="tg-local" data-k="l-safe" aria-label="Ignorar os ajustes deste jogo"${own ? '' : ' disabled'}></button></div>
    <div class="opt"><div><b>Linha de comando extra</b><small>Kernel.cl, repassada ao jogo</small></div><input class="txt" type="text" maxlength="512" placeholder="sem argumentos" data-k="l-cl" aria-label="Linha de comando extra" autocomplete="off" spellcheck="false"></div>
    <p class="note">Nada disso é salvo. Para manter, use os ajustes deste jogo.</p>`;
}
modal('launch', () => { const g = curGame(); return { html: sheetHead('Iniciar com…', 'Só nesta abertura de ' + esc(g.name)) + launchBody(g) + `<div class="acts"><button class="btn ghost" data-act="close" data-k="m-cancel">Cancelar</button><button class="btn primary" data-act="play" data-gid="${g.id}" data-k="m-go" data-autofocus>${ic('play', 18)} Jogar</button></div>` }; });
/* arquivo de configuração de um jogo: o que o app gravaria em config/<TITLE ID>.config.toml */
function tomlGameBody(id, g) {
  const T = window.XDR_TOML, ok = T.normalizeTitleId(id);
  if (!ok) return `<p class="warnline" role="alert">${ic('warn', 15)} Title ID inválido: são 8 dígitos hexadecimais (0–9, A–F), diferente de 00000000.</p>`;
  const p = tomlGame(ok, OV[g.id] || {});
  const path = `${XDR.app.pastaConfig}/${ok}.config.toml`;
  if (p.empty) return `<p class="note">${esc(g.name)} não muda nenhum ajuste que vá para o arquivo: o app não grava arquivo para este jogo (e apaga o arquivo quando ele fica vazio). Mude um ajuste em “Este jogo” para ver o arquivo.</p>${tomlNotes(p)}`;
  return `<p class="note">No aparelho: <code class="keyc sel-text">${esc(path)}</code></p><pre class="toml sel-text" id="toml-pre">${hlToml(p.text)}</pre>${tomlNotes(p)}`;
}
modal('toml', mp => {
  const g = curGame(), id = mp.tid != null ? mp.tid : g.id;
  return { wide: true, html: sheetHead('Arquivo deste jogo', `O que o app grava para ${esc(g.name)}: só as chaves que mudam, no formato que o núcleo lê. Feito com os ajustes da ${esc(VERSION.label.toLowerCase())}.`)
    + `<label class="fld" style="margin-bottom:10px"><span>Title ID do arquivo</span><input class="txt mono" id="toml-id" type="text" inputmode="text" maxlength="8" value="${esc(id)}" data-k="toml-id" autocomplete="off" spellcheck="false" aria-describedby="toml-body"></label>`
    + `<div id="toml-body" aria-live="polite">${tomlGameBody(id, g)}</div>`
    + `<div class="acts"><button class="btn" data-act="copy-text" data-k="m-copy" data-autofocus>${ic('copy', 16)} Copiar</button><button class="btn" data-act="toml-dl" data-k="m-dl">${ic('download', 16)} Baixar arquivo</button><button class="btn primary" data-act="close" data-k="m-ok">Fechar</button></div>` };
});
action('input:toml-id', el => { S.mp.tid = el.value.trim(); const b = document.getElementById('toml-body'); if (b) b.innerHTML = tomlGameBody(S.mp.tid, curGame()); });
action('toml-dl', () => {
  const T = window.XDR_TOML, g = curGame(), id = T.normalizeTitleId(S.mp.tid != null ? S.mp.tid : g.id);
  if (!id) { toast('Corrija o Title ID antes de baixar'); return; }
  const p = tomlGame(id, OV[g.id] || {});
  if (p.empty) { toast('Nada para gravar: este jogo não muda ajustes do arquivo'); return; }
  try {
    const url = URL.createObjectURL(new Blob([p.text], { type: 'application/toml' }));
    const a = document.createElement('a'); a.href = url; a.download = `${id}.config.toml`; document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
    toast(`${id}.config.toml baixado`);
  } catch (e) { toast('O navegador não deixou baixar; use Copiar'); }
});
modal('toml-global', () => { const p = tomlGlobalParts(); return { wide: true, html: sheetHead('Config global', 'Só as chaves que mudam em relação ao padrão do app. O arquivo real (xenia-canary.config.toml) tem todas as chaves e é regravado pelo app; mude por Configurações.')
  + (p.empty ? '<p class="note">Tudo no padrão.</p>' : `<pre class="toml sel-text" id="toml-pre">${hlToml(p.text)}</pre>`) + tomlNotes(p)
  + `<div class="acts">${p.empty ? '' : `<button class="btn" data-act="copy-text" data-k="m-copy" data-autofocus>${ic('copy', 16)} Copiar</button>`}<button class="btn primary" data-act="close" data-k="m-ok"${p.empty ? ' data-autofocus' : ''}>Fechar</button></div>` }; });
modal('presets', () => {
  const g = curGame();
  return { html: sheetHead('Predefinições', 'Aplicam vários ajustes neste jogo de uma vez; dá para desfazer.') + PRESETS.map(p => { const ch = Object.entries(p.set).filter(([k, v]) => String(effOf(g, k)) !== String(v)).length; return `<div class="opt"><div><b>${esc(p.t)}</b><small>${esc(p.d)} Muda ${ch} ${ch === 1 ? 'ajuste' : 'ajustes'} agora.</small></div><button class="btn sm${ch ? '' : ' ghost'}" data-act="preset" data-v="${p.id}" data-k="pr-${p.id}"${ch ? '' : ' disabled'}>${ch ? 'Aplicar' : 'Já está assim'}</button></div>`; }).join('')
    + `<div class="opt" data-note="presets-off"><div><b>Recomendados para este jogo</b><small>Perfis que vêm com o app: nenhum para este jogo neste build.</small></div></div>
       <div class="opt"><div><b>Da comunidade</b><small>Configs compartilhadas por jogadores; desligado em builds sem servidor.</small></div></div>
       <div class="acts"><button class="btn primary" data-act="close" data-k="pr-ok" data-autofocus>Pronto</button></div>` };
});
modal('cache', () => { const g = curGame(); return { html: sheetHead('Limpar o cache de shaders deste jogo?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">Remove os shaders e pipelines montados para este jogo (${g.cache[1]} MB). A próxima abertura monta tudo de novo, com engasgos até terminar. Saves, ajustes e os dados do próprio jogo ficam.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="m-cancel">Cancelar</button><button class="btn primary" data-act="cache-clear" data-k="m-clear" data-autofocus>Limpar</button></div>` }; });
modal('compress', () => { const g = curGame(); return { html: sheetHead('Comprimir para .zar?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">Isto empacota o disco num .zar menor. O .iso original fica intacto até o .zar ser criado e verificado, e você é consultado antes de ele ser apagado. O jogo continua na biblioteca.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="m-cancel">Cancelar</button><button class="btn primary" data-act="toast" data-msg="Comprimindo ${esc(g.name)}… (simulação)" data-k="m-zar" data-autofocus>Comprimir</button></div>` }; });
modal('cols', () => { const g = curGame(); return { html: sheetHead('Coleções', esc(g.name)) + `<ul class="menu">${COLS.map(c => `<li><button data-act="col-toggle" data-v="${esc(c)}" data-k="mc-${hash(c)}">${ic(g.cols.includes(c) ? 'check' : 'plus', 19)}<span>${esc(c)}<small>${(n => n + (n === 1 ? ' jogo' : ' jogos'))(GAMES.filter(x => x.cols.includes(c)).length)}</small></span></button></li>`).join('')}</ul><div class="acts"><button class="btn primary" data-act="close" data-k="m-ok" data-autofocus>Pronto</button></div>` }; });
modal('more', () => { const g = curGame(); return { html: sheetHead(esc(g.name)) + `<ul class="menu">
    <li><button data-act="toast" data-msg="No app, abre o seletor de imagens do Android." data-k="mm-cover">${ic('image', 19)}<span>Trocar capa<small>Fica para este título em todos os discos, mesmo se o arquivo mudar de lugar</small></span></button></li>
    <li><button data-act="modal" data-v="cols" data-k="mm-cols">${ic('layers', 19)}<span>Coleções</span></button></li>
    <li><button data-act="toast" data-msg="No app, o Android pede para pôr o atalho na tela inicial (se o launcher aceitar)." data-k="mm-short">${ic('link', 19)}<span>Criar atalho</span></button></li>
    ${g.fmt === 'ISO' ? `<li><button data-act="modal" data-v="compress" data-k="mm-zar">${ic('zip', 19)}<span>Comprimir para .zar</span></button></li>` : ''}
    <li><button data-act="modal" data-v="rate" data-k="mm-rate">${ic('shield', 19)}<span>Avaliar compatibilidade</span></button></li>
  </ul>` }; });
/* menu ⋮ da biblioteca (LibraryMenu em GameLibraryScreen.kt) */
modal('libmenu', () => ({ html: sheetHead('Biblioteca') + `<ul class="menu">
    <li><button data-act="go" data-v="browse" data-k="lm-add">${ic('plus', 19)}<span>Adicionar pasta de jogos</span></button></li>
    <li><button data-act="go" data-v="folders" data-k="lm-folders">${ic('folder', 19)}<span>Pastas de jogos</span></button></li>
    <li><button data-act="go" data-v="missing" data-k="lm-missing">${ic('inbox', 19)}<span>Jogos que saíram da biblioteca (3)</span></button></li>
    <li><button data-act="toast" data-msg="Procurando jogos nas pastas… (simulação)" data-k="lm-rescan">${ic('refresh', 19)}<span>Procurar de novo nas pastas</span></button></li>
    <li><button data-act="go" data-v="firstrun" data-k="lm-setup">${ic('wand', 19)}<span>Assistente de configuração</span></button></li>
    <li><button data-act="toast" data-msg="No app, abre a pasta de dados no gerenciador de arquivos do Android." data-k="lm-data">${ic('folder', 19)}<span>Abrir dados do usuário</span></button></li>
    <li><button data-act="go" data-v="update" data-k="lm-upd">${ic('download', 19)}<span>Procurar atualizações</span></button></li>
  </ul><p class="note" data-note="libmenu">Adicionar pasta e Pastas de jogos só aparecem no Android 11 ou mais novo.</p>` }));
/* as sessões do jogo (RunsSheet em GameCards.kt) */
modal('runs', () => { const g = curGame(), runs = runsOf(g); return { wide: true, html: sheetHead(`Sessões de ${esc(g.name)}`, 'A que você escolher aparece em Desempenho: números, linha do tempo e ajustes.') + `<div class="list">${runs.map((x, i) => drow({ icon: i === 0 ? 'check' : x.state === 'failed' ? 'xCircle' : x.state === 'interrupted' ? 'warn' : 'timeline', t: esc(x.when), s: [runEnding(x), x.p50 ? `${x.p50} FPS (baixo ${x.p5}) · p99 ${x.ft99} ms` : 'sem números', x.drv].map(esc).join(' · ') })).join('')}</div><p class="note" data-note="runs">Só a sessão mais nova tem números e linha do tempo nesta simulação; as outras são exemplos da lista.</p><div class="acts"><button class="btn primary" data-act="close" data-k="m-ok" data-autofocus>Fechar</button></div>` }; });
/* o relatório da última execução, revisado antes de sair do aparelho (RunReportDialog.kt) */
modal('report', () => { const g = curGame(), st = perfOf(g), p = g.perf, last = runsOf(g)[0] || {};
  const lines = [`Device: [aparelho de exemplo] · Android 15 (API 35)`, `App: ${XDR.versoes.estavel ? XDR.versoes.estavel.commit : 'local'}`, `Game: Title ID ${g.id} · [game file]${g.fmt === 'ISO' ? '.iso' : g.fmt === 'ZAR' ? '.zar' : g.fmt === 'XEX' ? '.xex' : ''} · started from library`, `Run: ${last.state === 'failed' ? 'failed' : last.state === 'interrupted' ? 'interrupted' : 'ended'} after ${fmtMs(last.ms || 0)}`];
  if (g.crash) lines.push(`Native crash: ${g.crash}`);
  if (p) lines.push(`Driver: ${p.drv}`, `Performance: ${st.sec} s sampled, median ${st.p50} FPS`);
  for (const l of inEffectLines(g)) lines.push('Setting: ' + l);
  return { wide: true, html: sheetHead('Compartilhar relatório da última execução') + `<div class="mlines" lang="en">${lines.map(l => `<div>${esc(l)}</div>`).join('')}</div><p class="note">Caminhos, contas e endereços são removidos; logs não vão junto (o Diagnóstico compartilha esses). Nada é enviado até você escolher onde compartilhar.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="m-cancel">Cancelar</button><button class="btn primary" data-act="toast" data-msg="No app, abre o compartilhamento do Android." data-k="m-share" data-autofocus>Compartilhar</button></div>` }; });
modal('rate', mp => { const g = curGame(), cur = mp.rs || (g.compat && g.compat.s) || ''; if (!mp.rs && cur) mp.rs = cur;
  return { html: sheetHead('Como roda nesta build?', esc(g.name)) + `<div class="radios" role="radiogroup" aria-label="Resultado">${['nothing', 'boots', 'intro', 'ingame', 'playable'].map(v => `<button class="radio" role="radio" aria-checked="${cur === v}" data-act="rate-pick" data-v="${v}" data-k="rt-${v}"><i aria-hidden="true"></i>${CS[v][0]}</button>`).join('')}</div>
    <label class="fld"><span>Notas (opcional)</span><textarea class="txt wide plain" id="rate-note" maxlength="500" rows="2" data-k="rt-note">${esc((g.compat && g.compat.note) || '')}</textarea></label>
    <p class="note">Fica neste aparelho com a build atual e o driver da última execução.</p>
    <div class="acts"><button class="btn ghost" data-act="close" data-k="m-cancel">Cancelar</button><button class="btn primary" data-act="rate" data-k="rt-save"${cur ? '' : ' disabled'}>Salvar</button></div>` }; });
modal('sort', () => ({ html: sheetHead('Ordenar e filtrar') + `<div class="seg" role="group">${SORTS.map(([v, t]) => `<button data-act="sort" data-v="${v}" aria-pressed="${S.sort === v}" data-k="ms-${v}">${v === 'recent' ? 'Recentes' : t}</button>`).join('')}</div><div class="row">${filterChips('mf')}</div><div class="acts"><button class="btn primary" data-act="close" data-k="m-ok" data-autofocus>Pronto</button></div>` }));
modal('ovlist', mp => { const d = DEF[mp.key]; const l = gamesOverriding(mp.key); return { html: sheetHead(esc(d.t), 'Jogos com valor próprio; o global não vale para eles.') + `<div class="list">${l.map(g => drow({ img: coverHTML(g, '', false).replace('class="cover', 'style="width:34px" class="cover'), t: esc(g.name), s: 'Usa ' + esc(label(d, OV[g.id][mp.key])), acts: `<button class="btn sm ghost" data-act="ovopen" data-gid="${g.id}" data-k="ov-${g.id}">Abrir ajustes</button>` })).join('')}</div><div class="acts"><button class="btn primary" data-act="close" data-k="m-ok" data-autofocus>Fechar</button></div>` }; });
action('ovopen', el => { S.gameId = el.dataset.gid; S.modal = null; S.sec.game = 'set:' + DEF[S.mp.key].g; go('game'); });

/* ============ eventos ============ */
app.addEventListener('click', e => {
  const el = e.target.closest('[data-act]'); if (!el || !app.contains(el)) return;
  if (el.disabled || (el.getAttribute('aria-disabled') === 'true' && el.dataset.act !== 'crow')) return;
  if (el.tagName === 'SELECT' || (el.tagName === 'INPUT' && el.type !== 'checkbox')) return;
  const fn = ACT[el.dataset.act]; if (fn) fn(el, e);
});
app.addEventListener('change', e => {
  const el = e.target; const a = el.dataset.actChange || el.dataset.actInput; if (!a) return;
  if (a === 'set') { setVal(el.closest('[data-global]') ? null : scopeG(), el.dataset.key, el.value); render(); }
  else if (ACT['change:' + a]) ACT['change:' + a](el, e);
});
app.addEventListener('input', e => {
  const el = e.target;
  if (el.id === 'set-q') { S.setQ = el.value; refreshList(); return; }
  if (ACT['input:' + el.id]) { ACT['input:' + el.id](el, e); return; }
  if (el.dataset.actInput === 'set') { const d = DEF[el.dataset.key]; const out = el.parentElement.querySelector('output'); if (out) out.textContent = label(d, coerce(d, el.value)); }
});
app.addEventListener('focusin', e => { const scr = SCREENS[S.route.name]; if (scr && scr.onFocus) scr.onFocus(e.target); else if (e.target.classList.contains('c-mi') && S.nav && e.target.dataset.act === 'sec') { const k = e.target.dataset.key, v = e.target.dataset.v; if (S.sec[k] !== v) { S.sec[k] = v; render(); } } });
app.addEventListener('scroll', e => { const scr = SCREENS[S.route.name]; if (scr && scr.onScroll) scr.onScroll(e.target); schedulePins(); }, true);
app.addEventListener('pointerdown', () => { if (S.nav) { S.nav = false; app.classList.remove('nav'); } });

/* ---- navegação espacial (teclado e controle) ---- */
const NAVX = {
  crow(cur, dir) { const k = cur.dataset.key; if (!k || cur.getAttribute('aria-disabled') === 'true') return true; stepVal(scopeG(), k, dir === 'right' ? 1 : -1); render(); return true; },
};
const FOC = 'button:not([disabled]):not([tabindex="-1"]),input:not([disabled]),select:not([disabled]),[tabindex="0"]';
function focusables(scope) { return Array.from(scope.querySelectorAll(FOC)).filter(el => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'; }); }
function setNav(on) { if (S.nav !== on) { S.nav = on; app.classList.toggle('nav', on); } }
function moveFocus(dir) {
  const scope = app.querySelector('.sheet') || app.querySelector('.guide') || app; const list = focusables(scope); if (!list.length) return;
  const cur = document.activeElement;
  if (!cur || !scope.contains(cur)) { const f = scope.querySelector('[data-autofocus]') || scope.querySelector('.c-item.on') || list[0]; f.focus({ preventScroll: true }); reveal(f); return; }
  const h = cur.closest('[data-navx]'); if (h && (dir === 'left' || dir === 'right') && NAVX[h.dataset.navx] && NAVX[h.dataset.navx](cur, dir)) return;
  const a = cur.getBoundingClientRect(), ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  let best = null, bs = Infinity;
  for (const el of list) {
    if (el === cur || el.contains(cur) || cur.contains(el)) continue;
    const b = el.getBoundingClientRect(), bx = b.left + b.width / 2, by = b.top + b.height / 2; let p, o, off;
    if (dir === 'right') { if (bx <= ax + 1 || b.left < a.left + 2) continue; p = Math.max(0, b.left - a.right); o = Math.max(0, b.top - a.bottom, a.top - b.bottom); off = Math.abs(by - ay); }
    else if (dir === 'left') { if (bx >= ax - 1 || b.right > a.right - 2) continue; p = Math.max(0, a.left - b.right); o = Math.max(0, b.top - a.bottom, a.top - b.bottom); off = Math.abs(by - ay); }
    else if (dir === 'down') { if (by <= ay + 1 || b.top < a.top + 2) continue; p = Math.max(0, b.top - a.bottom); o = Math.max(0, b.left - a.right, a.left - b.right); off = Math.abs(bx - ax); }
    else { if (by >= ay - 1 || b.bottom > a.bottom - 2) continue; p = Math.max(0, a.top - b.bottom); o = Math.max(0, b.left - a.right, a.left - b.right); off = Math.abs(bx - ax); }
    const s = p + o * 3 + off * .15; if (s < bs) { bs = s; best = el; }
  }
  if (best) { const k = best.dataset.k; best.focus({ preventScroll: true }); const el = best.isConnected ? best : k && app.querySelector(`[data-k="${CSS.escape(k)}"]`); if (el) reveal(el, true); }
}
function cycleSelect(sel, d) { const i = clamp(sel.selectedIndex + d, 0, sel.options.length - 1); if (i !== sel.selectedIndex) { sel.selectedIndex = i; sel.dispatchEvent(new Event('change', { bubbles: true })); } }
function stepRange(r, d) { const v = clamp(Number(r.value) + d * Number(r.step || 1), Number(r.min), Number(r.max)); r.value = v; r.dispatchEvent(new Event('input', { bubbles: true })); r.dispatchEvent(new Event('change', { bubbles: true })); }
function focusedGame() { const ae = document.activeElement; const el = ae && app.contains(ae) ? ae.closest('[data-gid]') || (ae.querySelector && ae.querySelector('[data-gid]')) : null; if (el) return GBY[el.dataset.gid]; if (S.route.name === 'game') return curGame(); return null; }
function tabStep(d) {
  if (S.modal || S.guide) return;
  const scr = SCREENS[S.route.name];
  if (scr.tabStep) { scr.tabStep(d); return; }
  const key = scr.secKey; if (!key) return;
  const ids = Array.from(app.querySelectorAll(`[data-act="sec"][data-key="${key}"]`)).map(b => b.dataset.v); if (!ids.length) return;
  S.sec[key] = ids[(ids.indexOf(S.sec[key]) + d + ids.length) % ids.length]; render();
  focusKey((isC() ? 'cm-' : 'bs-') + S.sec[key]);
}
function fire(k) {
  setNav(true);
  if (S.swapAB && (k === 'a' || k === 'b')) k = k === 'a' ? 'b' : 'a';
  const scr = SCREENS[S.route.name];
  if (scr && scr.onKey && scr.onKey(k)) return;
  const t = document.activeElement, inApp = t && app.contains(t);
  switch (k) {
    case 'up': case 'down': case 'left': case 'right':
      if (inApp && t.tagName === 'SELECT' && (k === 'left' || k === 'right')) { cycleSelect(t, k === 'right' ? 1 : -1); break; }
      if (inApp && t.type === 'range' && (k === 'left' || k === 'right')) { stepRange(t, k === 'right' ? 1 : -1); break; }
      moveFocus(k); break;
    case 'a': if (inApp) { if (t.tagName === 'SELECT') { try { t.showPicker(); } catch (e) { cycleSelect(t, 1); } } else if (t.tagName !== 'INPUT') t.click(); } else moveFocus('down'); break;
    case 'play': { const g = focusedGame() || curGame(); ACT.play({ dataset: { gid: g.id } }); break; }
    case 'b': back(); break;
    case 'x': { const g = focusedGame(); if (g && S.route.name !== 'game' && ACT.open) ACT.open({ dataset: { gid: g.id }, classList: { contains: () => false }, querySelector: () => null }); break; }
    case 'y': { const g = focusedGame(); if (g) ACT.fav({ dataset: { gid: g.id } }); break; }
    case 'lb': tabStep(-1); break;
    case 'rb': tabStep(1); break;
    case 'start': S.guide = !S.guide; S.modal = null; render(); break;
    case 'view': S.modal = 'search'; render(); break;
  }
}
document.addEventListener('keydown', e => {
  const t = e.target;
  if (!t.closest || !t.closest('#screen')) return; // fora do aparelho, o teclado é da página
  if (e.altKey || e.ctrlKey || e.metaKey) return;
  const scr = SCREENS[S.route.name]; if (scr && scr.onRawKey && scr.onRawKey(e)) return;
  const k = e.key, inText = (t.tagName === 'INPUT' && /^(text|search|number)$/.test(t.type)) || t.tagName === 'TEXTAREA';
  if (k.startsWith('Arrow')) {
    const dir = k.slice(5).toLowerCase();
    if (inText && (dir === 'left' || dir === 'right')) return;
    e.preventDefault(); fire(dir);
  } else if (k === 'Escape' || (k === 'Backspace' && !inText)) { e.preventDefault(); fire('b'); }
  else if (!inText && (k === 'q' || k === 'Q')) fire('lb');
  else if (!inText && (k === 'e' || k === 'E')) fire('rb');
  else if (!inText && (k === 'f' || k === 'F')) fire('y');
  else if (!inText && (k === 'i' || k === 'I')) fire('x');
  else if (!inText && (k === 'm' || k === 'M')) fire('start');
  else if (!inText && k === '/') { e.preventDefault(); fire('view'); }
  else if ((k === 'Enter' || k === ' ') && !inText) setNav(true);
});

/* ---- controle (Gamepad API) ---- */
let padPrev = {}, padRep = {}, padLoop = 0;
const PAD = { axes: [0, 0, 0, 0], buttons: [], id: '' };
function pollPad(ts) {
  let pads = [];
  try { pads = Array.from(navigator.getGamepads ? navigator.getGamepads() : []).filter(Boolean); } catch (e) { pads = []; }
  const p = pads[0];
  if (p) {
    PAD.axes = Array.from(p.axes); PAD.buttons = p.buttons.map(b => b.value || (b.pressed ? 1 : 0)); PAD.id = p.id;
    const B = i => !!(p.buttons[i] && p.buttons[i].pressed), ax = p.axes[0] || 0, ay = p.axes[1] || 0;
    const st = { a: B(0), b: B(1), x: B(2), y: B(3), lb: B(4), rb: B(5), view: B(8), start: B(9), up: B(12) || ay < -.6, down: B(13) || ay > .6, left: B(14) || ax < -.6, right: B(15) || ax > .6 };
    const scr = SCREENS[S.route.name];
    if (!(scr && scr.capturePad && scr.capturePad(p))) {
      for (const k in st) {
        if (st[k] && !padPrev[k]) { padRep[k] = ts + 380; fire(k); }
        else if (st[k] && padPrev[k] && 'updownleftright'.includes(k) && ts >= padRep[k]) { padRep[k] = ts + 110; fire(k); }
      }
    }
    padPrev = st;
  }
  padLoop = requestAnimationFrame(pollPad);
}
addEventListener('gamepadconnected', () => { S.pad = true; if (!padLoop) padLoop = requestAnimationFrame(pollPad); applyMode(); updateChrome(); });
addEventListener('gamepaddisconnected', () => { let any = false; try { any = Array.from(navigator.getGamepads()).some(Boolean); } catch (e) { any = false; } if (!any) { S.pad = false; applyMode(); updateChrome(); } });

/* ============ visor: aparelho, anotações, notas ============ */
function fit() {
  const wrap = document.getElementById('dwrap');
  if (S.full) { wrap.style.removeProperty('--s'); return; }
  const land = S.orient === 'land', sw = land ? 904 : 407, sh = land ? 407 : 904, dw = sw + 24, dh = sh + 24;
  const availW = Math.max(240, document.getElementById('stage').clientWidth - 32);
  const bar = document.getElementById('ch-bar').getBoundingClientRect().height;
  const head = (document.querySelector('.site-header') || { getBoundingClientRect: () => ({ height: 0 }) }).getBoundingClientRect().height;
  const availH = Math.max(land ? 300 : 480, innerHeight - bar - head - 36);
  const s = Math.min(1, availW / dw, land ? 9 : availH / dh);
  wrap.style.setProperty('--dw', dw); wrap.style.setProperty('--dh', dh); wrap.style.setProperty('--s', s.toFixed(4));
  const scr = SCREENS[S.route.name]; if (ready && scr && scr.after) requestAnimationFrame(() => scr.after(true));
}
let pinRAF = 0;
function schedulePins() { if (pinRAF) return; pinRAF = requestAnimationFrame(() => { pinRAF = 0; placePins(); }); }
const NOTE_KIND = { app: 'No app', dif: 'Diferente no app', sim: 'Simulação' };
function placePins() {
  const box = document.getElementById('pins');
  if (!S.notes) { box.innerHTML = ''; document.getElementById('ch-notes').innerHTML = '<p class="empty-notes">Ligue “Anotações” na barra para numerar na tela o que cada parte faz no app, o que é diferente no app e o que é só da simulação.</p>'; return; }
  const scr = document.getElementById('screen'), sr = scr.getBoundingClientRect(), sc = sr.width / scr.offsetWidth || 1;
  const seen = new Set(), items = [];
  for (const el of app.querySelectorAll('[data-note]')) {
    const id = el.dataset.note; if (seen.has(id) || !NOTES[id]) continue;
    if ((S.modal || S.guide) && !el.closest('.sheet, .guide')) continue;
    const r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2) continue;
    const px = Math.min(r.right - 2, r.left + 6), py = Math.min(r.bottom - 2, r.top + 6);
    if (px < sr.left || py < sr.top || px > sr.right || py > sr.bottom) continue;
    const hit = document.elementFromPoint(px, py); if (!hit || !(el.contains(hit) || hit.contains(el) || hit.closest('#pins'))) continue;
    seen.add(id); items.push({ id, x: (r.left - sr.left) / sc, y: (r.top - sr.top) / sc });
  }
  box.innerHTML = items.map((p, i) => `<button type="button" class="pin k-${NOTES[p.id][0]}" style="left:${Math.max(10, p.x).toFixed(0)}px;top:${Math.max(10, p.y).toFixed(0)}px" data-pin="${p.id}" title="${esc(NOTES[p.id][1])}" aria-label="Anotação ${i + 1}: ${esc(NOTE_KIND[NOTES[p.id][0]])}">${i + 1}</button>`).join('');
  document.getElementById('ch-notes').innerHTML = items.length ? `<ol class="notes">${items.map((p, i) => `<li><span class="nk k-${NOTES[p.id][0]}" aria-hidden="true">${i + 1}</span><span><span class="nkind k-${NOTES[p.id][0]}">${NOTE_KIND[NOTES[p.id][0]]}</span>${esc(NOTES[p.id][1])}</span></li>`).join('')}</ol>` : '<p class="empty-notes">Nada anotado na parte visível. Role a tela do aparelho ou troque de seção.</p>';
}
document.getElementById('pins').addEventListener('click', e => { const b = e.target.closest('.pin'); if (b) toast(NOTES[b.dataset.pin][1]); });

function screenTitle(n) { return SCREENS[n] ? SCREENS[n].title : n; }
const SIM = document.getElementById('sim');
const PRINT_BASE = '../';
const codeLink = f => `<a href="${XDR.repo}/blob/${XDR.ref}/app/src/main/java/xendroid/compose/${f}" rel="noopener"><code>${esc(f.split('/').pop())}</code></a>`;
function updateChrome() {
  document.querySelectorAll('[data-cact="mode"]').forEach(b => b.setAttribute('aria-pressed', b.closest('.full-exit') ? b.dataset.v === S.mode : b.dataset.v === S.modePref));
  document.querySelectorAll('[data-cact="orient"]').forEach(b => b.setAttribute('aria-pressed', !S.full && b.dataset.v === S.orient));
  document.querySelectorAll('.ch-bar [data-cact="full"]').forEach(b => b.setAttribute('aria-pressed', S.full));
  document.querySelector('[data-cact="notes"]').setAttribute('aria-pressed', S.notes);
  const sel = document.getElementById('ch-screen');
  sel.innerHTML = LOTES.filter(([t, l]) => l.some(n => SCREENS[n])).map(([t, l]) => `<optgroup label="${esc(t)}">${l.filter(n => SCREENS[n]).map(n => `<option value="${n}"${S.route.name === n ? ' selected' : ''}>${esc(screenTitle(n))}</option>`).join('')}</optgroup>`).join('');
  const cur = SCREENS[S.route.name] || {};
  document.getElementById('ch-vars').innerHTML = (cur.variants || []).map((v, i) => `<label class="ch-field"><span>${esc(v.label)}</span><select class="ch-sel" data-var="${i}" aria-label="${esc(v.label)} (exemplo)">${v.list.map(([id, t]) => `<option value="${esc(id)}"${String(v.get()) === String(id) ? ' selected' : ''}>${esc(t)}</option>`).join('')}</select></label>`).join('');
  const pad = document.getElementById('ch-pad'); pad.className = 'ch-pad' + (S.pad ? ' on' : ''); pad.innerHTML = `${ic('gamepad', 16)} ${S.pad ? 'Controle conectado' : 'Sem controle'} · modo ${S.mode === 'c' ? 'controle' : 'toque'}`;
  const ver = document.getElementById('ch-ver');
  const canais = Object.keys(XDR.canais);
  ver.innerHTML = canais.length > 1
    ? `<label class="ch-field"><span>Versão</span><select class="ch-sel" id="ch-canal">${canais.map(c => `<option value="${c}"${VERSION === XDR.canais[c] ? ' selected' : ''}>${esc(XDR.canais[c].label)}</option>`).join('')}</select></label>`
    : `<span class="ch-tag">${ic('check', 14)} Ajustes da ${esc(VERSION.label.toLowerCase())}${XDR.mesmoApp ? ', igual ao main' : ''}</span>`;
  SIM.classList.toggle('full', S.full);
  document.documentElement.classList.toggle('sim-full', S.full);
  const meta = XDR.telas[S.route.name] || {};
  document.getElementById('ch-dir').innerHTML = `<p class="ch-eyebrow">${esc(groupOf(S.route.name))} · modo ${S.mode === 'c' ? 'controle' : 'toque'}</p><h2>${esc(cur.title || '')}</h2>${meta.what ? `<p>${esc(meta.what)}</p>` : ''}
    <dl class="ch-dl">${meta.where ? `<dt>No app</dt><dd>${esc(meta.where)}</dd>` : ''}${meta.c ? `<dt>Modo controle</dt><dd>${esc(meta.c)}</dd>` : ''}${meta.code && meta.code.length ? `<dt>Código</dt><dd>${meta.code.map(codeLink).join(', ')}</dd>` : ''}</dl>
    ${meta.prints && meta.prints.length ? `<div class="ch-prints"><p class="ch-sub">Prints reais desta tela, gerados pelos testes do app</p><div>${meta.prints.filter(p => p.file).map(p => `<a href="${PRINT_BASE}${p.file}" target="_blank" rel="noopener"><img src="${PRINT_BASE}${p.file}" alt="${esc(p.alt)}" width="${p.w}" height="${p.h}" loading="lazy" decoding="async"></a>`).join('')}</div></div>` : ''}`;
  document.getElementById('ch-lotes').innerHTML = LOTES.map(([t, l]) => `<div class="lote"><h3>${esc(t)}</h3><ul>${l.filter(n => SCREENS[n]).map(n => `<li><button type="button" data-cact="screen" data-v="${n}" aria-current="${S.route.name === n}">${esc(screenTitle(n))}</button></li>`).join('')}</ul></div>`).join('');
  try { history.replaceState(null, '', '#' + S.route.name + (S.modePref === 'c' ? '-controle' : '') + (S.orient === 'port' ? '-retrato' : '')); } catch (e) { /* sem hash: segue */ }
}
document.addEventListener('change', e => {
  if (e.target.id !== 'ch-canal') return;
  loadChannel(e.target.value); PINNED = VERSION.fixados.filter(k => DEF[k]); render(); toast(`Ajustes da ${VERSION.label.toLowerCase()}`);
});
document.addEventListener('click', e => {
  const b = e.target.closest('[data-cact]'); if (!b) return;
  const a = b.dataset.cact, v = b.dataset.v;
  const swap = fn => { if (document.startViewTransition && !reduced) { try { document.startViewTransition(fn); return; } catch (err) { /* sem transição */ } } fn(); };
  if (a === 'mode') swap(() => { S.modePref = v; GLOBAL['@app.mode'] = v; if (v === 'auto') delete GLOBAL['@app.mode']; persist(); applyMode(true); render(); fit(); });
  else if (a === 'screen') { goTop(v, {}); document.getElementById('stage').scrollIntoView({ block: 'start', behavior: reduced ? 'auto' : 'smooth' }); document.getElementById('screen').focus({ preventScroll: true }); }
  else if (a === 'orient') { S.full = false; S.orient = v; persist(); fit(); render(); }
  else if (a === 'full') { S.full = !S.full; fit(); render(); if (!S.full) b.focus(); }
  else if (a === 'notes') { S.notes = !S.notes; persist(); updateChrome(); schedulePins(); }
});
document.getElementById('ch-screen').addEventListener('change', e => goTop(e.target.value, {}));
document.getElementById('ch-vars').addEventListener('change', e => { const sel = e.target.closest('select[data-var]'); const scr = SCREENS[S.route.name]; const v = sel && scr.variants && scr.variants[Number(sel.dataset.var)]; if (v) { v.set(sel.value); render(); } });
/* um toque ou clique no aparelho passa o teclado para ele */
document.getElementById('screen').addEventListener('pointerdown', e => { if (!e.target.closest(FOC)) document.getElementById('screen').focus({ preventScroll: true }); });
addEventListener('hashchange', () => { const before = S.route.name; readHash(); if (S.route.name !== before) { render(); fit(); } });
addEventListener('keydown', e => { if (e.key === 'Escape' && S.full && !(e.target.closest && e.target.closest('#screen'))) { S.full = false; fit(); render(); } });

function readHash() {
  const m = /^#?([a-z]+)(-controle)?(-retrato)?$/.exec(location.hash || '');
  if (m && SCREENS[m[1]]) { S.route = { name: m[1], p: {} }; if (m[1] !== 'library') S.stack = [{ name: 'library', p: {} }]; if (m[2]) S.modePref = 'c'; if (m[3]) S.orient = 'port'; }
}
function boot() {
  readHash();
  if (S.route.name !== 'library' && !S.stack.length) S.stack = [{ name: 'library', p: {} }];
  if (S.modePref !== 'auto') GLOBAL['@app.mode'] = S.modePref;
  S.mode = wantMode();
  new ResizeObserver(() => fit()).observe(document.getElementById('stage'));
  addEventListener('resize', () => { fit(); schedulePins(); });
  addEventListener('scroll', () => schedulePins(), { passive: true });
  if (window.staticInfo) window.staticInfo();
  fit(); render();
  makeArt().then(() => { ready = true; render(); fit(); });
}
