/* Peças de interface compartilhadas pelas telas do simulador (do protótipo B+C do app). */
'use strict';

const isC = () => S.mode === 'c';
const isPortrait = () => { const s = document.getElementById('screen'); return s.clientHeight > s.clientWidth; };
const art = g => g.cover || g.icon || '';
const curGame = () => GBY[S.gameId] || GAMES[0];
const isFav = g => FAVS.has(g.id);

/* ---------- capas, selos ---------- */
function coverHTML(g, cls = '', named = false) {
  const vt = named ? ' style="view-transition-name:hero-cover"' : '';
  const badges = (isFav(g) ? `<span class="cv-fav">${ic('starF', 14)}</span>` : '') + (g.discs > 1 ? `<span class="cv-disc">Disco 1/${g.discs}</span>` : '');
  if (g.cover) return `<span class="cover ${cls}" data-gid="${g.id}"${vt}><img class="art" src="${g.cover}" alt="" draggable="false">${badges}</span>`;
  return `<span class="cover smart ${cls}" data-gid="${g.id}"${vt}><img class="bg" src="${g.icon || ''}" alt="" draggable="false"><img class="ico" src="${g.icon || ''}" alt="" draggable="false"><span class="nm">${esc(g.name)}</span>${badges}</span>`;
}
const pill = (g, short) => `<span class="pill st-${stOf(g)}">${CS[stOf(g)][short ? 1 : 0]}</span>`;
const dot = g => `<i class="dot st-${stOf(g)}"></i>`;
const recents = () => GAMES.filter(g => g.runs).sort((a, b) => a.last - b.last);
function sortList(l, s) { const by = (a, b) => a.name.localeCompare(b.name, 'pt-BR'); if (s === 'az') return l.sort(by); if (s === 'za') return l.sort((a, b) => by(b, a)); if (s === 'fmt') return l.sort((a, b) => FMT_ORDER.indexOf(a.fmt) - FMT_ORDER.indexOf(b.fmt) || by(a, b)); return l.sort((a, b) => (a.last || 99) - (b.last || 99) || by(a, b)); }
function libList() {
  let l = GAMES.slice(); const f = S.lf;
  if (f === 'fav') l = l.filter(isFav); else if (f.startsWith('col:')) l = l.filter(g => g.cols.includes(f.slice(4))); else if (f.startsWith('fmt:')) l = l.filter(g => g.fmt === f.slice(4));
  if (S.q.trim()) { const q = norm(S.q.trim()); l = l.filter(g => norm(g.name).includes(q) || g.id.toLowerCase().includes(q)); }
  return sortList(l, S.sort);
}
const SORTS = [['az', 'Nome A–Z'], ['za', 'Nome Z–A'], ['fmt', 'Formato'], ['recent', 'Jogados recentemente']];
const FMT_ORDER = ['ISO', 'ZAR', 'GOD', 'XEX', 'STFS'];
function filterChips(pfx) {
  const n = f => f === 'all' ? GAMES.length : f === 'fav' ? GAMES.filter(isFav).length : f.startsWith('col:') ? GAMES.filter(g => g.cols.includes(f.slice(4))).length : GAMES.filter(g => g.fmt === f.slice(4)).length;
  const chip = (f, t) => `<button class="chip" data-act="lf" data-v="${esc(f)}" aria-pressed="${S.lf === f}" data-k="${pfx}-${esc(f)}">${t}<span class="n">${n(f)}</span></button>`;
  const fmts = FMT_ORDER.filter(f => GAMES.some(g => g.fmt === f));
  return chip('all', 'Todos') + chip('fav', `${ic('star', 14)} Favoritos`) + COLS.map(c => chip('col:' + c, esc(c))).join('') + (fmts.length > 1 ? '<span class="sepr" aria-hidden="true"></span>' + fmts.map(f => chip('fmt:' + f, FMT[f])).join('') : '');
}
const profileOf = xuid => PROFILES.find(p => p.xuid === xuid) || PROFILE_TRASH.find(p => p.xuid === xuid);
const activeProfile = () => PROFILES.find(p => p.active) || PROFILES[0];
const avatarImg = (p, cls = 'avatar-s') => `<img class="${cls}" src="${p.avatar || ''}" alt="" draggable="false">`;

/* ---------- valores de ajuste (por jogo ou global) ---------- */
/* ajustes só do app (nunca por jogo): interface, toque e controles */
const appOnly = k => /^@(app|touch|ctl)\./.test(k);
function globalOf(k) { if (k === '@res') { const x = globalOf('GPU.draw_resolution_scale_x'), y = globalOf('GPU.draw_resolution_scale_y'); return x === y ? x : 'mixed'; } return k in GLOBAL ? GLOBAL[k] : DEF[k].def; }
function effOf(g, k) { if (!g) return globalOf(k); if (k === '@res') { const x = effOf(g, 'GPU.draw_resolution_scale_x'), y = effOf(g, 'GPU.draw_resolution_scale_y'); return x === y ? x : 'mixed'; } const o = OV[g.id]; return o && k in o ? o[k] : globalOf(k); }
function shown(g, k) { return !g || S.editScope === 'global' ? globalOf(k) : effOf(g, k); }
function isOv(g, k) { if (!g) return false; if (k === '@res') return isOv(g, 'GPU.draw_resolution_scale_x') || isOv(g, 'GPU.draw_resolution_scale_y'); const o = OV[g.id]; return !!o && k in o; }
function globalChanged(k) { if (k === '@res') return globalChanged('GPU.draw_resolution_scale_x') || globalChanged('GPU.draw_resolution_scale_y'); return k in GLOBAL; }
function gamesOverriding(k) { return GAMES.filter(g => OV[g.id] && k in OV[g.id]); }
function coerce(d, v) { if (d.ty === 'bool') return v === true || v === 'true'; if (d.ty === 'int') return parseInt(v, 10); if (d.ty === 'num') return Math.round(parseFloat(v) * 100) / 100; return String(v); }
function setVal(g, k, raw) {
  if (k === '@res') { setVal(g, 'GPU.draw_resolution_scale_x', raw); setVal(g, 'GPU.draw_resolution_scale_y', raw); return; }
  const d = DEF[k]; const v = coerce(d, raw);
  if (!g || S.editScope === 'global' || appOnly(k)) { if (v === d.def) delete GLOBAL[k]; else GLOBAL[k] = v; }
  else (OV[g.id] = OV[g.id] || {})[k] = v;
  if (k.startsWith('@app.')) applyApp(k);
}
function resetVal(g, k) {
  if (k === '@res') { resetVal(g, 'GPU.draw_resolution_scale_x'); resetVal(g, 'GPU.draw_resolution_scale_y'); return; }
  if (!g || S.editScope === 'global' || appOnly(k)) { delete GLOBAL[k]; if (k.startsWith('@app.')) applyApp(k); }
  else if (OV[g.id]) delete OV[g.id][k];
}
function stepVal(g, k, dir, wrap) {
  const d = DEF[k]; const cur = shown(g, k);
  if (d.ty === 'bool') return setVal(g, k, !cur);
  if (d.ty === 'list') { const i = d.o.findIndex(o => String(o[0]) === String(cur)); let n = (i < 0 ? 0 : i) + dir; n = wrap ? (n + d.o.length) % d.o.length : clamp(n, 0, d.o.length - 1); return setVal(g, k, d.o[n][0]); }
  if (d.ty === 'int' || d.ty === 'num') { const s = d.step || 1; let n = Math.round((Number(cur) + dir * s) * 100) / 100; if (wrap && n > d.max) n = d.min; return setVal(g, k, clamp(n, d.min, d.max)); }
}
function label(d, v) {
  if (v === 'mixed') return 'Misto';
  if (d.ty === 'bool') return v ? 'Ligado' : 'Desligado';
  if (d.ty === 'list') { const o = d.o.find(o => String(o[0]) === String(v)); return o ? o[1] : v === '' && d.o.length ? d.o[0][1] : String(v); }
  if (d.ty === 'int') return d.zero != null && Number(v) === 0 ? d.zero : nf(v) + (d.unit || '');
  if (d.ty === 'num') return Number(v).toFixed(2).replace('.', ',');
  if (d.ty === 'text') return v || (d.ph ? d.ph + ' (padrão)' : '(padrão)');
  return '';
}
function depOk(g, d) { if (!d.dep) return true; return String(shown(g, d.dep[0])) === String(d.dep[1]); }
function depText(d) { const p = DEF[d.dep[0]]; return `Só vale com “${p.t}” em ${label(p, coerce(p, d.dep[1]))}.`; }
const ovCount = g => Object.keys(OV[g.id] || {}).length;
const groupOv = (g, gid) => Object.keys(OV[g.id] || {}).filter(k => DEF[k] && DEF[k].g === gid).length;
const globalGroupChanged = gid => Object.keys(GLOBAL).filter(k => DEF[k] && DEF[k].g === gid).length;

/* ---------- controles ---------- */
function control(d, g, v, dis) {
  const val = shown(g, d.k), k = d.k, D = dis ? ' disabled' : '';
  if (d.ty === 'action') return `<button class="btn sm" data-act="toast" data-msg="No app, grava as sessões guardadas e a atual num ZIP em Downloads, sem ocultar dados." data-k="ctl-${k}">${ic('download', 16)} Exportar</button>`;
  if (d.ty === 'bool') return `<button class="tg" role="switch" aria-checked="${!!val}" aria-label="${esc(d.t)}" data-act="set" data-key="${k}" data-v="${!val}" data-k="ctl-${k}"${D}></button>`;
  if (d.ty === 'text') return `<input class="txt" type="text" value="${esc(val)}" placeholder="${esc(d.ph || '')}" aria-label="${esc(d.t)}" data-act-change="set" data-key="${k}" data-k="ctl-${k}" autocomplete="off" spellcheck="false"${D}>`;
  if (d.ty === 'list') {
    const total = d.o.reduce((s, o) => s + o[1].length, 0);
    const segOk = v === 'q' ? d.o.length <= 3 && total <= 12 : d.o.length <= 6 && total <= 26;
    if (segOk) return `<div class="seg" role="group" aria-label="${esc(d.t)}">${d.o.map(o => `<button data-act="set" data-key="${k}" data-v="${esc(o[0])}" aria-pressed="${String(val) === String(o[0])}" data-k="${v === 'q' ? 'q' : 'ctl'}-${k}-${esc(o[0])}"${D}>${esc(o[1])}</button>`).join('')}</div>`;
    return `<select class="sel" aria-label="${esc(d.t)}" data-act-change="set" data-key="${k}" data-k="${v === 'q' ? 'q' : 'ctl'}-${k}"${D}>${val === 'mixed' ? '<option selected>Misto</option>' : ''}${d.o.map(o => `<option value="${esc(o[0])}"${String(val) === String(o[0]) ? ' selected' : ''}>${esc(o[1])}</option>`).join('')}</select>`;
  }
  const steps = (d.max - d.min) / (d.step || 1);
  if (steps <= 10) return `<span class="stp" role="group" aria-label="${esc(d.t)}"><button data-act="step" data-key="${k}" data-d="-1" aria-label="Diminuir" data-k="sm-${k}"${D}>${ic('chevL', 16)}</button><b>${esc(label(d, val))}</b><button data-act="step" data-key="${k}" data-d="1" aria-label="Aumentar" data-k="sp-${k}"${D}>${ic('chevR', 16)}</button></span>`;
  return `<span class="rng"><input type="range" min="${d.min}" max="${d.max}" step="${d.step || 1}" value="${val}" aria-label="${esc(d.t)}" data-act-input="set" data-key="${k}" data-k="ctl-${k}"${D}><output>${esc(label(d, val))}</output></span>`;
}
function scopeTag(g, d) {
  if (appOnly(d.k)) return '<span class="src">App</span>';
  if (!g || S.editScope === 'global') {
    const n = gamesOverriding(d.k).length;
    return (globalChanged(d.k) ? '<span class="src chg">Alterado</span>' : '<span class="src">Padrão</span>') + (n ? `<button class="reset" data-act="ovlist" data-key="${d.k}" data-k="ol-${d.k}" data-note="ovgames">${n} ${n === 1 ? 'jogo usa' : 'jogos usam'} outro valor</button>` : '');
  }
  return isOv(g, d.k) ? '<span class="src game">Este jogo</span>' : `<span class="src">Global${globalChanged(d.k) ? ' (alterado)' : ''}</span>`;
}
function applyTag(d) {
  if (d.ap === 'live') return `<span class="b-ap live" title="Também muda com o jogo aberto, pelo menu em jogo" data-note="apply">${ic('bolt', 12)} Ao vivo</span>`;
  if (d.ap === 'act') return `<span class="b-ap">${ic('bolt', 12)} Ação</span>`;
  return `<span class="b-ap">${ic('restart', 12)} Próxima abertura</span>`;
}
function settingRow(d, g, v) {
  const gl = !g || S.editScope === 'global' || appOnly(d.k);
  const ov = gl ? globalChanged(d.k) : isOv(g, d.k);
  const dis = !depOk(g, d);
  if (v === 'q') return `<div class="qrow"><span class="q-t">${esc(d.t)}${g && isOv(g, d.k) ? '<i title="Este jogo"></i>' : ''}</span>${control(d, g, 'q', dis)}</div>`;
  const resetB = ov ? `<button class="reset" data-act="reset" data-key="${d.k}" data-k="rs-${d.k}">${ic('reset', 12)} ${gl ? 'Voltar ao padrão' : 'Voltar ao global: ' + esc(label(d, globalOf(d.k)))}</button>` : '';
  const pinB = d.ty === 'action' || appOnly(d.k) ? '' : `<button class="pinb ${PINNED.includes(d.k) ? 'on' : ''}" data-act="pin" data-key="${d.k}" data-k="pn-${d.k}" aria-pressed="${PINNED.includes(d.k)}" aria-label="Fixar nos ajustes rápidos" title="Fixar nos ajustes rápidos" data-note="pin">${ic('pin', 16)}</button>`;
  return `<div class="srow ${ov ? 'ovr' : ''} ${dis ? 'dis' : ''}">
    <div><div class="srow-t"${d.tEn ? ' lang="en"' : ''}>${esc(d.t)}${d.n ? '<span class="b-new" data-note="newcvar">NOVO</span>' : ''}${d.nOpt ? '<span class="b-new" data-note="newopt">NOVA OPÇÃO</span>' : ''}</div>
    <div class="srow-m">${scopeTag(g, d)}${applyTag(d)}${appOnly(d.k) ? '' : d.k[0] === '@' ? '<span>salvo pelo app</span>' : `<code class="keyc">${d.k}</code>`}${resetB}</div></div>
    <div class="srow-c">${control(d, g, 'full', dis)}${pinB}</div>
    ${S.showDesc && d.d ? `<p class="srow-d"${d.dEn ? ' lang="en"' : ''}>${esc(d.d)}</p>` : ''}
    ${d.w ? `<p class="srow-w">${ic('warn', 14)} ${esc(d.w)}</p>` : ''}
    ${dis ? `<p class="srow-w i" data-note="dep">${ic('info', 14)} ${esc(depText(d))}</p>` : ''}
  </div>`;
}
function cRow(d, g) {
  const val = shown(g, d.k), gl = !g || S.editScope === 'global' || appOnly(d.k);
  const ov = gl ? globalChanged(d.k) : isOv(g, d.k), dis = !depOk(g, d);
  const sub = dis ? depText(d) : gl ? (ov ? 'Alterado' : 'Padrão') + (d.ap === 'live' ? ' · ao vivo' : '') : (ov ? 'Este jogo · global: ' + label(d, globalOf(d.k)) : (d.ap === 'live' ? 'Ao vivo · global' : 'Global'));
  if (d.ty === 'text' || d.ty === 'action') return `<div class="crow ${ov ? 'ovr' : ''}"><span class="t"><b>${esc(d.t)}${d.n ? '<span class="b-new">NOVO</span>' : ''}</b><small>${esc(sub)}</small></span>${control(d, g, 'full', dis)}</div>`;
  const v = d.ty === 'bool' ? `<span class="tg" role="presentation" aria-checked="${!!val}"></span>` : `<span class="v"><span class="ar" data-act="step" data-key="${d.k}" data-d="-1">${ic('chevL', 16)}</span><span class="vv">${esc(label(d, val))}</span><span class="ar" data-act="step" data-key="${d.k}" data-d="1">${ic('chevR', 16)}</span></span>`;
  return `<button class="crow ${ov ? 'ovr' : ''} ${dis ? 'dis' : ''}" data-act="crow" data-key="${d.k}" data-k="cr-${d.k}" data-navx="crow" data-note="crow"${dis ? ' aria-disabled="true"' : ''}><span class="t"><b>${esc(d.t)}${d.n ? '<span class="b-new">NOVO</span>' : ''}</b><small>${esc(sub)}</small></span>${v}</button>`;
}
function matchQ(d) { if (!S.setQ.trim()) return true; const q = norm(S.setQ.trim()); return norm(d.t).includes(q) || norm(d.k).includes(q) || norm(d.d).includes(q); }
function matchF(g, d) {
  switch (S.setF) {
    case 'changed': return !g || S.editScope === 'global' ? globalChanged(d.k) : isOv(g, d.k);
    case 'new': return !!(d.n || d.nOpt);
    case 'live': return d.ap === 'live';
    case 'pinned': return PINNED.includes(d.k);
    default: return true;
  }
}
function setRows(g, group) { return SET.filter(d => d.lvl <= S.setLevel && (!group || S.setQ.trim() || d.g === group) && matchQ(d) && matchF(g, d)); }
function setListHTML(g, v, group) {
  const rows = setRows(g, group);
  if (!rows.length) return `<div class="empty">Nenhum ajuste com esses filtros. <button class="link" data-act="clear-set" data-k="clear-set">Limpar filtros</button></div>`;
  const one = d => v === 'c' ? cRow(d, g) : settingRow(d, g, 'full');
  if (group && !S.setQ.trim()) return `<div class="${v === 'c' ? 'c-rows' : 'setp-list'}">${rows.map(one).join('')}</div>`;
  return GROUPS.map(([gid, gname]) => { const r = rows.filter(d => d.g === gid); if (!r.length) return ''; return `<h4 class="gh">${gname}<span class="n">${r.length}</span></h4><div class="${v === 'c' ? 'c-rows' : 'setp-list'}">${r.map(one).join('')}</div>`; }).join('');
}
/* g = jogo (ajustes por jogo) ou null (ajustes globais) */
function settingsPanel(g, v, group) {
  const lv = [[1, 'Essencial'], [2, 'Avançado'], [3, 'Tudo']];
  const cnt = f => { const keep = S.setF; S.setF = f; const n = SET.filter(d => d.lvl <= S.setLevel && matchF(g, d)).length; S.setF = keep; return n; };
  const gl = !g || S.editScope === 'global';
  const fl = [['all', 'Todos'], ['changed', gl ? 'Mudados' : 'Mudados neste jogo'], ['new', 'Novos'], ['live', 'Ao vivo'], ['pinned', 'Fixados']];
  const ovn = g ? ovCount(g) : 0;
  return `<div class="setp">
    <div class="setp-bar">
      ${g ? `<div class="seg acc" role="group" aria-label="Onde editar" data-note="scope"><button data-act="scope" data-v="game" aria-pressed="${S.editScope === 'game'}" data-k="sc-game">Este jogo · ${ovn}</button><button data-act="scope" data-v="global" aria-pressed="${S.editScope === 'global'}" data-k="sc-global">Global</button></div>` : ''}
      <label class="search" data-note="filters">${ic('search', 17)}<input id="set-q" type="search" placeholder="Buscar ajuste, chave ou descrição" value="${esc(S.setQ)}" data-k="set-q" autocomplete="off" spellcheck="false"></label>
      <div class="seg" role="group" aria-label="Quantos ajustes mostrar" data-note="levels">${lv.map(([n, t]) => `<button data-act="level" data-v="${n}" aria-pressed="${S.setLevel === n}" data-k="lv-${n}">${t}</button>`).join('')}</div>
    </div>
    <div class="setp-bar">
      ${fl.map(([f, t]) => `<button class="chip" data-act="setf" data-v="${f}" aria-pressed="${S.setF === f}" data-k="sf-${f}">${t}${f === 'all' ? '' : `<span class="n">${cnt(f)}</span>`}</button>`).join('')}
      <span class="tools">
        ${g ? `<button class="btn sm" data-act="modal" data-v="presets" data-k="b-presets" data-note="presets">${ic('wand', 16)} Predefinições</button><button class="btn sm" data-act="modal" data-v="toml" data-k="b-toml" data-note="toml">${ic('code', 16)} TOML</button>` : `<button class="btn sm" data-act="modal" data-v="toml-global" data-k="b-tomlg" data-note="toml">${ic('code', 16)} TOML global</button>`}
        <button class="btn sm ghost" data-act="desc" data-k="b-desc" aria-pressed="${S.showDesc}">${ic('info', 16)} ${S.showDesc ? 'Menos texto' : 'Mais texto'}</button>
      </span>
    </div>
    <p class="setp-note">${ic('info', 15)}<span>${!g ? `Valem para todos os jogos que não mudam o mesmo ajuste. ${Object.keys(GLOBAL).filter(k => DEF[k] && !appOnly(k)).length} fora do padrão.` : S.editScope === 'game' ? `Valem só para <b>${esc(g.name)}</b>, em todos os discos e cópias; o resto segue o global.${ovn ? ' <button class="link" data-act="reset-all" data-k="b-resetall">Voltar tudo ao global</button>' : ''}` : 'Valem para todos os jogos que não mudam o mesmo ajuste.'}</span></p>
    <div id="setp-list" data-v="${v}" data-group="${group || ''}" data-global="${g ? '' : '1'}">${setListHTML(g, v, group)}</div>
  </div>`;
}
function quickPanel(g, v, noDriver) {
  const rows = PINNED.filter(k => !(noDriver && k === 'Vulkan.vulkan_lib_path')).map(k => DEF[k]).filter(Boolean);
  if (v === 'c') return `<div class="c-rows">${rows.map(d => cRow(d, g)).join('')}</div>`;
  return `<div class="quick" data-note="quick">${rows.map(d => settingRow(d, g, 'q')).join('')}</div>`;
}
function appRows(keys, v) { const rows = keys.map(k => DEF[k]); return v === 'c' ? `<div class="c-rows">${rows.map(d => cRow(d, null)).join('')}</div>` : `<div class="setp-list">${rows.map(d => settingRow(d, null, 'full')).join('')}</div>`; }

/* ---------- linhas de lista ---------- */
function drow(o) {
  const lead = o.img ? o.img : o.lead ? `<span class="lead">${o.lead}</span>` : ic(o.icon || 'info', 22);
  return `<div class="drow"${o.note ? ` data-note="${o.note}"` : ''}>${lead}<div><b>${o.t}</b>${o.s ? `<small>${o.s}</small>` : ''}</div><div class="row">${o.acts || ''}</div></div>`;
}

/* ---------- abas da ficha (os cartões de GameCards.kt) ---------- */
const lastSessionText = g => { const st = perfOf(g); return st ? `${st.p50} FPS mediana · ${st.p5} nos piores 5%` : g.runs ? 'sem quadros' : null; };
const fmtMs = ms => { const m = Math.round(ms / 60000); return m >= 60 ? fmtMin(m) : m ? `${m} min` : `${Math.round(ms / 1000)} s`; };
/* as sessões de exemplo de um jogo, da mais nova para a mais velha (a primeira é a última sessão) */
function runsOf(g) {
  if (!g.runs) return [];
  const st = perfOf(g), r = rng(hash(g.id + ':runs')), list = [];
  const whens = [g.ago, 'há 3 dias', 'há 6 dias', 'há 9 dias', 'há 2 semanas'];
  for (let i = 0; i < Math.min(g.runs, 5); i++) {
    if (g.fictional) { list.push(i === 0 ? { when: g.ago, ms: 9000, state: 'failed', why: 'falha nativa (sinal 11)', drv: 'Turnip B (exemplo)' } : { when: whens[i], ms: (5 + i) * 60000, state: i === 1 ? 'interrupted' : 'ended', why: 'encerrada pelo Android (pouca memória)', drv: 'Turnip A (exemplo)' }); continue; }
    const d = i === 0 ? 0 : Math.round((r() - .5) * 3);
    list.push({ when: whens[i], ms: (i === 0 ? 48 : 12 + Math.round(r() * 40)) * 60000, state: 'ended', p50: st ? st.p50 + d : null, p5: st ? st.p5 + d : null, ft99: st ? st.ft99 + Math.round(r() * 6) : null, drv: i === 3 ? 'Driver do sistema' : g.perf ? g.perf.drv : 'Driver do sistema' });
  }
  return list;
}
const runEnding = x => x.state === 'failed' ? `Falhou depois de ${fmtMs(x.ms)}: ${x.why}` : x.state === 'interrupted' ? `Interrompida depois de ${fmtMs(x.ms)}: ${x.why}` : `Terminou normalmente depois de ${fmtMs(x.ms)}`;
function perfHTML(g, full) {
  const st = perfOf(g), p = g.perf, last = runsOf(g)[0];
  if (!g.runs) return `<p class="note" style="font-size:13px">Nenhuma sessão ainda. Depois da primeira aparecem aqui FPS por segundo, tempo de quadro, pipelines, áudio e temperatura.</p>`;
  const crash = full && g.crash ? `<p class="warnline" role="note">${ic('warn', 15)} Falha nativa: ${esc(g.crash)}</p>` : '';
  if (!st) return `<p style="font-size:13px;color:var(--fg2)">${esc(runEnding(last))}</p><p class="note">Sem números de desempenho nesta sessão.</p>${crash}`;
  const k = `<div class="kpis">
      <div class="kpi"><b>${st.p50}<small>FPS</small></b><span>mediana dos segundos</span></div>
      <div class="kpi"><b>${st.p5}<small>FPS</small></b><span>ou menos em 5% dos segundos</span></div>
      <div class="kpi"><b>${st.ft99}<small>ms</small></b><span>99% dos quadros abaixo disso</span></div>
      <div class="kpi"><b>${p.first}<small>s</small></b><span>até o primeiro quadro</span></div>
    </div>`;
  const chart = `<figure class="chart">${fpsSVG(st)}<figcaption>Segundos em cada FPS (média de cada segundo; ${nf(st.sec)} s medidos, pausas fora)</figcaption></figure>`;
  const example = p.readme ? `<p class="note" data-note="perfex">Números de exemplo: ${p.readme.low != null ? 'a mediana e a queda seguem' : 'a mediana segue'} a medição publicada no README (${esc(p.readme.fps)}${p.readme.obs ? '; ' + esc(p.readme.obs) : ''}); o resto é ilustrativo.</p>` : '';
  if (!full) return k + chart + example;
  return k + chart + `<figure class="chart">${ftSVG(st)}<figcaption>Quadros por tempo de quadro; metade abaixo de ${st.ft50} ms</figcaption></figure>
    <dl class="kv">
      <dt>Pipelines criados</dt><dd>${nf(p.pipes[0])} (${String(p.pipes[1]).replace('.', ',')} s)</dd>
      <dt>Áudio (${p.audio[0]})</dt><dd>${nf(p.audio[2])} de ${nf(p.audio[1])} blocos com falha</dd>
      <dt>Bateria</dt><dd>${p.bat[0]} → ${p.bat[1]} °C (fim ${p.bat[2]} °C)</dd>
      <dt>Limite e tela</dt><dd>${p.lim ? p.lim + ' FPS' : 'sem limite'} · ${p.hz} Hz</dd>
      <dt>Driver usado</dt><dd>${esc(p.drv)}</dd>
      <dt>Jogou como</dt><dd>${esc(activeProfile().tag)}</dd>
    </dl><p class="note">${esc(runEnding(last))}</p>${example}${crash}`;
}
/* linha do tempo como o app grava (RunEvents.kt): "m:ss tipo · detalhe", em inglês */
function runEvents(g) {
  const p = g.perf;
  if (g.fictional) return [['0:00', 'boot · run started'], ['0:00', 'driver · Turnip B (exemplo)'], ['0:01', 'surface · created, booting the core'], ['0:03', `title · ${g.id} running`], ['0:09', 'error · native crash (signal 11)']];
  if (!p) return [];
  const end = Math.round(p.sec / 60), at = (m, s) => `${m}:${String(s).padStart(2, '0')}`;
  return [['0:00', 'boot · run started'], ['0:00', `driver · ${p.drv}`], ['0:01', 'surface · created, booting the core'], ['0:02', `title · ${g.id} running`],
    [at(0, p.first), `boot · first guest frames after ${p.first} s`], ['3:12', 'controller · connected (vendor 0x054C product 0x0CE6) as P1'], ['3:12', 'touch controls · hidden: a controller is playing P1'],
    ['12:05', 'compile · pipeline creation burst started'], ['12:07', `compile · ${Math.round(p.pipes[0] / 9)} pipelines, 1840 ms over 2 s`],
    [at(Math.round(end * .55), 40), 'thermal · moderate'], [at(Math.round(end * .7), 2), 'menu · opened, guest paused'], [at(Math.round(end * .7), 31), 'menu · closed'],
    [at(end, 19), 'exit · user exit']];
}
function timelineHTML(g) {
  const ev = runEvents(g); if (!ev.length) return '';
  const shown = ev.slice(0, 14);
  return `<section class="card span2" data-note="timeline"><h3>${ic('timeline', 15)} Linha do tempo da última execução<span class="r">${ev.length} eventos: ciclo de vida, pausas, travadas, calor, controles, erros</span></h3>
    <ol class="tl">${shown.map(e => `<li><time>${e[0]}</time><i class="${/^(error|exit · user)/.test(e[1]) ? (e[1].startsWith('error') ? 'e' : 'g') : /^(thermal|compile · pipeline)/.test(e[1]) ? 'w' : ''}"></i><span lang="en">${esc(e[1])}</span></li>`).join('')}</ol></section>`;
}
/* "Ajustes em vigor": o que a sessão usou fora dos padrões do núcleo, como o núcleo escreve */
function inEffectLines(g) {
  const lines = [];
  const add = (k, v, own) => {
    const d = DEF[k]; if (!d || k.startsWith('@') || k === 'Vulkan.vulkan_lib_path') return;
    const [sec, name] = k.split('.'); const cv = CVARS[name]; if (!cv) return;
    const raw = rawOf(d, v), def = k in VERSION.padraoNucleo ? VERSION.padraoNucleo[k] : rawOf(d, d.def);
    if (raw === def) return;
    const q = x => (cv[1] === 'string' ? `"${x}"` : x);
    lines.push(`${cv[0]}|${name} = ${q(raw)} (default ${q(def)})${own ? ' · this game' : ''}`);
  };
  for (const [k, v] of Object.entries(GLOBAL)) if (!(OV[g.id] && k in OV[g.id])) add(k, v, false);
  for (const [k, v] of Object.entries(OV[g.id] || {})) {
    if (k === '@res') { add('GPU.draw_resolution_scale_x', v, true); add('GPU.draw_resolution_scale_y', v, true); } else add(k, v, true);
  }
  return lines.sort();
}
function compatHTML(g) {
  const c = g.compat;
  return `<div class="row">${pill(g, true)}${c ? `<span class="muted" style="font-size:12px">${c.date}</span>` : ''}</div>
    ${c ? `${c.note ? `<p style="font-size:13px">${esc(c.note)}</p>` : ''}<p class="note">Build ${esc(c.build)} · ${esc(c.gpu)} · ${esc(c.drv)}</p>` : '<p class="note">Você ainda não avaliou este jogo.</p>'}
    <div class="row"><button class="btn sm" data-act="modal" data-v="rate" data-k="rate-${g.id}">${c ? 'Avaliar de novo' : 'Avaliar'}</button></div>
    <p class="note">Avalie você mesmo: os resultados ficam por build e driver, nunca são adivinhados.</p>`;
}
function patchesHTML(g, limit) {
  if (!g.patches.length) return '<p class="note">Nenhum patch para este jogo na pasta de patches.</p>';
  const many = g.pfiles.length > 1, fileOf = e => g.pfiles[Number(e.id.split(':')[0])];
  const row = (e, sub) => `<div class="prow"><span>${esc(e.n)}${sub ? `<small>${esc(sub)}</small>` : ''}</span><button class="tg" role="switch" aria-checked="${PON[g.id].has(e.id)}" aria-label="${esc(e.n)}" data-act="patch" data-v="${esc(e.id)}" data-k="pt-${esc(e.id)}"></button></div>`;
  const body = limit
    ? g.patches.slice(0, limit).map(e => row(e, many ? fileOf(e).label : '')).join('')
    : g.pfiles.map(f => (many ? `<p class="pfile">${esc(f.label)}</p>` : '') + f.entries.map(e => row(e, e.d)).join('')).join('');
  return `<div class="plist">${body}</div>${limit && g.patches.length > limit ? `<button class="link" data-act="gsec" data-v="cont" data-k="more-patches">Ver os ${g.patches.length} patches</button>` : ''}`;
}
const patchTrail = g => g.patches.length ? `${PON[g.id].size} de ${g.patches.length} ativados` : '';
function contentFacts(g, withCache) {
  return `<dl class="kv"><dt>Title update</dt><dd>${g.tu ? `Title Update ${g.tu}` : 'nenhum'}</dd><dt>DLC</dt><dd>${g.dlc.length || 'nenhum'}</dd>${withCache ? `<dt>Cache de shaders</dt><dd>${g.cache[0] ? `${g.cache[1]} MB` : 'nenhum'}</dd>` : ''}</dl>`;
}
function contentHTML(g) {
  return `<div class="grid2">
    <section class="card span2" data-note="patches"><h3>${ic('patch', 15)} Patches do jogo<span class="r">${patchTrail(g)}</span></h3>
      ${g.patches.length ? '<p class="note">Conferidos contra a versão do jogo que você joga. Só valem com “Aplicar patches” ligado.</p>' : ''}${patchesHTML(g)}
      ${g.patches.length ? `<div class="row"><button class="btn sm ghost" data-act="toast" data-msg="No app, abre os arquivos de patch deste jogo: importar, atualizar e ver as versões. Essa tela não está no simulador." data-k="pt-manage">${ic('patch', 15)} Importar, atualizações e versões</button></div>` : ''}</section>
    <section class="card"><h3>${ic('box', 15)} Title update e DLC</h3>${contentFacts(g, false)}
      <div class="row"><button class="btn sm" data-act="go" data-v="content" data-p="${g.id}" data-k="cont-manage">Gerenciar conteúdo</button></div></section>
    <section class="card"><h3>${ic('disc', 15)} Arquivo</h3><p class="path">${esc(g.path)}</p><dl class="kv"><dt>Formato</dt><dd>${fmtLabel(g)}</dd>${g.discs > 1 ? `<dt>Discos</dt><dd>Disco 1 de ${g.discs}</dd>` : ''}</dl></section>
  </div>`;
}
function dataHTML(g) {
  const cache = g.cache[0] ? `${g.cache[0]} ${g.cache[0] === 1 ? 'arquivo' : 'arquivos'} · ${g.cache[1]} MB` : 'Nada em cache para este jogo ainda';
  return `<div class="card">
    ${drow({ icon: 'save', t: 'Saves · backup e restauração', s: 'Backup, restauração e sincronização dos saves deste jogo', note: 'saves', acts: `<button class="btn sm" data-act="go" data-v="saves" data-p="${g.id}" data-k="d-saves">Abrir saves</button>` })}
    ${drow({ icon: 'layers', t: 'Cache de shaders', s: cache, acts: `<button class="btn sm ghost" data-act="modal" data-v="cache" data-k="d-cache"${g.cache[0] ? '' : ' disabled'}>${ic('trash', 15)} Limpar…</button>` })}
    ${drow({ icon: 'image', t: 'Capa', s: 'Ícone do próprio jogo (64×64)', note: 'cover', acts: `<button class="btn sm ghost" data-act="toast" data-msg="No app, abre o seletor de imagens do Android." data-k="d-cover">Trocar capa</button>` })}
    ${drow({ icon: 'layers', t: 'Coleções', s: g.cols.length ? g.cols.map(esc).join(', ') : 'Fora de coleções', acts: `<button class="btn sm ghost" data-act="modal" data-v="cols" data-k="d-cols">Editar</button>` })}
    ${g.fmt === 'ISO' ? drow({ icon: 'zip', t: 'Comprimir para .zar', s: 'O .iso fica intacto até o .zar ser criado e verificado', acts: `<button class="btn sm ghost" data-act="modal" data-v="compress" data-k="d-zar">Comprimir…</button>` }) : ''}
    ${drow({ icon: 'link', t: 'Criar atalho', s: 'Atalho na tela inicial do Android', acts: `<button class="btn sm ghost" data-act="toast" data-msg="No app, o Android pede para pôr o atalho na tela inicial (se o launcher aceitar)." data-k="d-short">Criar</button>` })}
    ${drow({ icon: 'timeline', t: 'Últimas sessões · diagnóstico', s: g.runs ? `${g.runs} ${g.runs === 1 ? 'sessão registrada' : 'sessões registradas'}` : 'Nenhuma sessão ainda', acts: `<button class="btn sm ghost" data-act="go" data-v="diagnostics" data-p="${g.id}" data-k="d-diag">Abrir</button>` })}
  </div>`;
}
function lastSessionCard(g, full) {
  return `<section class="card span2" data-note="perfcard"><h3>${ic('chart', 15)} Última sessão<span class="r">${g.runs ? `${esc(g.ago)} · ${fmtMin(g.playMin)} no total` : 'Nunca jogado'}</span></h3>${perfHTML(g, full)}${!full && perfOf(g) ? `<button class="link" data-act="gsec" data-v="perf" data-k="ov-perf">Ver detalhes e linha do tempo</button>` : ''}</section>`;
}
function overviewHTML(g, v) {
  const n = ovCount(g);
  return `<div class="grid2">
    ${lastSessionCard(g, false)}
    <div class="col">
      <section class="card"><h3>${ic('sliders', 15)} Ajustes rápidos<span class="r">${n} ${n === 1 ? 'mudado' : 'mudados'} neste jogo</span></h3>${quickPanel(g, v)}<button class="link" data-act="gsec" data-v="set" data-k="ov-set">Todos os ajustes</button></section>
      <section class="card" data-note="patches"><h3>${ic('patch', 15)} Patches<span class="r">${patchTrail(g)}</span></h3>${patchesHTML(g, 3)}</section>
    </div>
    <div class="col">
      <section class="card" data-note="compat"><h3>${ic('shield', 15)} Compatibilidade</h3>${compatHTML(g)}</section>
      <section class="card"><h3>${ic('box', 15)} Conteúdo</h3>${contentFacts(g, true)}</section>
    </div>
  </div>`;
}
function perfTabHTML(g) {
  const runs = runsOf(g), lines = inEffectLines(g);
  const picker = runs.length > 1 ? `<button class="runpick span2" data-act="modal" data-v="runs" data-k="p-runs">${ic('timeline', 18)}<span><b>${esc(runs[0].when)} · ${fmtMs(runs[0].ms)}${runs[0].p50 ? ` · ${runs[0].p50} FPS` : ''}</b><small>${g.runs} sessões · toque para ver outra</small></span>${ic('chevD', 16)}</button>` : '';
  return `<div class="grid2">${picker}${lastSessionCard(g, true)}${timelineHTML(g)}
    ${g.runs ? `<section class="card span2" data-note="ineffect"><h3>${ic('sliders', 15)} Ajustes em vigor${lines.length ? `<span class="r">${lines.length}</span>` : ''}</h3>${lines.length ? `<div class="mlines" lang="en">${lines.map(l => `<div>${esc(l)}</div>`).join('')}</div>` : '<p class="note">Tudo no padrão.</p>'}</section>` : ''}
    <section class="card span2"><div class="row"><button class="btn sm" data-act="modal" data-v="report" data-k="p-report" data-note="report"${g.runs ? '' : ' disabled'}>${ic('share', 15)} Compartilhar relatório…</button><button class="btn sm ghost" data-act="go" data-v="compare" data-k="p-compare">Comparar execuções</button><button class="btn sm ghost" data-act="go" data-v="diagnostics" data-p="${g.id}" data-k="p-logs">Sessões e logs</button></div></section></div>`;
}

/* ---------- moldura B: trilho e telas com seções ---------- */
const RAIL = [['library', 'Jogos', 'grid'], ['collections', 'Coleções', 'layers', 'opt'], ['content', 'Conteúdo', 'box'], ['profiles', 'Perfis', 'user'], ['controls', 'Controles', 'gamepad'], ['drivers', 'Drivers', 'chip', 'opt']];
function railActive() { const r = S.route.name; const map = { game: 'library', saves: 'library', keymap: 'controls', touchedit: 'controls', padtest: 'controls', phonepad: 'controls', compare: 'settings', diagnostics: 'settings', about: 'settings', folders: 'settings', missing: 'settings', update: 'settings' }; if (r === 'library') return S.lf.startsWith('col:') ? 'collections' : 'library'; return map[r] || r; }
function rail() {
  const a = railActive();
  return `<nav class="b-rail" aria-label="Navegação" data-note="rail"><span class="logo" aria-hidden="true"></span>${RAIL.map(([v, t, i, o]) => `<button class="${o || ''}" data-act="rail" data-v="${v}" data-k="rail-${v}"${a === v ? ' aria-current="page"' : ''}>${ic(i, 21)}${t}</button>`).join('')}<span class="sp"></span><button data-act="rail" data-v="settings" data-k="rail-settings"${a === 'settings' ? ' aria-current="page"' : ''}>${ic('gear', 21)}Ajustes</button></nav>`;
}
function glyph(b) { return b.split('/').map(x => `<span class="gb ${x === '≡' ? 'M' : x === '⧉' ? 'V' : x}">${x}</span>`).join(''); }
function hints(list, note = 'hints') { return `<footer class="c-hints" data-note="${note}">${list.map(([b, t, a]) => `<button class="hint" data-act="hint" data-v="${a}" tabindex="-1">${glyph(b)}<span>${t}</span></button>`).join('')}<span class="hint kb">${S.pad ? 'Controle conectado' : 'Teclado: setas · Enter · Esc · Q/E · F · I'}</span></footer>`; }
const DEFAULT_HINTS = [['A', 'Selecionar', 'a'], ['B', 'Voltar', 'back'], ['LB/RB', 'Seções', 'tabs'], ['≡', 'Menu', 'guide']];
/* def: { key, title, sub, head, actions, art, dyn, icon, sections:[{group,id,t,icon,n,body(v)}], hints, note } */
function sectioned(def) {
  const secs = def.sections.filter(s => s.id);
  let sel = S.sec[def.key]; if (!secs.find(s => s.id === sel)) sel = secs[0].id;
  S.sec[def.key] = sel;
  const cur = secs.find(s => s.id === sel);
  if (isC()) {
    let lastGroup = null;
    const menu = def.sections.map(s => {
      let h = '';
      if (s.group && s.group !== lastGroup) { lastGroup = s.group; h = `<h5>${s.group}</h5>`; }
      if (s.go) return h + `<button class="c-mi" data-act="go" data-v="${s.go}" data-k="cm-go-${s.go}">${ic(s.icon || 'chevR', 19)}${s.t}<span class="n">${ic('chevR', 14)}</span></button>`;
      return h + `<button class="c-mi ${s.id === sel ? 'on' : ''} ${s.play ? 'play' : ''}" data-act="sec" data-key="${def.key}" data-v="${s.id}" data-k="cm-${s.id}"${s.id === sel ? ' data-autofocus' : ''}>${ic(s.icon || 'chevR', 19)}${s.t}${s.n ? `<span class="n">${s.n}</span>` : ''}</button>`;
    }).join('');
    return `<div class="cshell" style="${def.dyn || ''}">
      <div class="c-bg">${def.art ? `<div class="bgimg" style="background-image:url('${def.art}')"></div>` : ''}</div>
      <div class="c-ghead"><button class="ibtn" data-act="back" data-k="g-back" aria-label="Voltar">${ic('back')}</button>${def.head != null ? def.head : `<span class="gicon">${ic(def.icon || 'gear', 22)}</span>`}<div style="min-width:0"><h1>${esc(def.title)}</h1>${def.csub || def.sub ? `<span class="${def.plainSub ? 'csub' : 'mono'}">${def.csub || def.sub}</span>` : ''}</div></div>
      <nav class="c-menu" data-sk="c-menu-${def.key}" data-note="cmenu">${menu}</nav>
      <section class="c-panel" id="c-panel" data-sk="c-panel-${def.key}-${sel}" data-key="${def.key}">${cur.noTitle ? '' : `<h2>${cur.title || cur.t}</h2>`}${cur.lead ? `<p class="lead">${cur.lead}</p>` : ''}${cur.body('c')}</section>
      ${hints(def.hints || DEFAULT_HINTS)}
    </div>`;
  }
  let lastGroup = null;
  const nav = def.sections.map(s => {
    let h = '';
    if (s.group && s.group !== lastGroup) { lastGroup = s.group; h = `<h5>${s.group}</h5>`; }
    if (s.go) return h + `<button data-act="go" data-v="${s.go}" data-k="bs-go-${s.go}">${ic(s.icon || 'chevR', 17)}${s.t}<span class="go">${ic('chevR', 14)}</span></button>`;
    return h + `<button data-act="sec" data-key="${def.key}" data-v="${s.id}" data-k="bs-${s.id}" aria-current="${s.id === sel}">${ic(s.icon || 'chevR', 17)}${s.t}${s.n ? `<span class="n">${s.n}</span>` : ''}</button>`;
  }).join('');
  return `<div class="shell">${rail()}
    <div class="main">
      <header class="topbar">${def.art ? `<div class="bgimg" style="background-image:url('${def.art}')"></div>` : ''}
        ${def.noBack ? '' : `<button class="ibtn" data-act="back" data-k="g-back" aria-label="Voltar">${ic('back')}</button>`}
        ${def.head || ''}
        <div class="tt"><h1>${esc(def.title)}</h1>${def.sub ? `<div class="ids${def.plainSub ? ' plain' : ''}">${def.sub}</div>` : ''}</div>
        ${def.actions ? `<div class="tacts">${def.actions}</div>` : ''}
      </header>
      <div class="body">
        <nav class="secnav" data-sk="secnav-${def.key}" data-note="${def.navNote || 'secnav'}">${nav}</nav>
        <div class="secbody" data-sk="sec-${def.key}-${sel}" id="secbody">${cur.noTitle ? '' : `<h2>${cur.title || cur.t}</h2>`}${cur.lead ? `<p class="lead">${cur.lead}</p>` : ''}${cur.body('b')}</div>
      </div>
    </div>
  </div>`;
}
/* tela simples (sem seções) nas duas molduras */
function single(def) {
  if (isC()) {
    return `<div class="cshell" style="${def.dyn || ''};grid-template-columns:minmax(0,1fr);grid-template-areas:'head' 'panel' 'hints';grid-template-rows:auto minmax(0,1fr) 42px">
      <div class="c-bg">${def.art ? `<div class="bgimg" style="background-image:url('${def.art}')"></div>` : ''}</div>
      <div class="c-ghead"><button class="ibtn" data-act="back" data-k="g-back" aria-label="Voltar">${ic('back')}</button><span class="gicon">${ic(def.icon || 'gear', 22)}</span><div style="min-width:0"><h1>${esc(def.title)}</h1>${def.sub ? `<span class="mono">${def.sub}</span>` : ''}</div><span class="sp"></span>${def.actions || ''}</div>
      <section class="c-panel" id="c-panel" data-sk="c-panel-${def.key}" style="padding-left:22px">${def.body('c')}</section>
      ${hints(def.hints || [['A', 'Selecionar', 'a'], ['B', 'Voltar', 'back'], ['≡', 'Menu', 'guide']])}
    </div>`;
  }
  return `<div class="shell${def.noRail ? ' norail' : ''}">${def.noRail ? '' : rail()}
    <div class="main">
      <header class="topbar">${def.noBack ? '' : `<button class="ibtn" data-act="back" data-k="g-back" aria-label="Voltar">${ic('back')}</button>`}<div class="tt"><h1>${esc(def.title)}</h1>${def.sub ? `<div class="ids plain">${def.sub}</div>` : ''}</div>${def.actions ? `<div class="tacts">${def.actions}</div>` : ''}</header>
      <div class="body single" data-sk="single-${def.key}" id="secbody">${def.body('b')}</div>
    </div>
  </div>`;
}

/* ---------- C: cor da capa ---------- */
function dynVars(g) {
  if (!g) return '';
  let acc = g.pal[2]; if (lum(acc) < .3) acc = mixHex(acc, '#ffffff', .45);
  const on = lum(acc) > .42 ? '#0b0f0d' : '#ffffff';
  return `--dyn:${g.pal[1]};--acc:${acc};--on-acc:${on}`;
}

/* ---------- TOML ----------
   O arquivo que o app grava, pelo núcleo: o tipo de cada valor sai da forma do texto
   (save_config_entry) e o texto segue o toml++ (config-shape.js, o mesmo do build do site).
   Cada chave é conferida contra as cvars da versão escolhida. */
function hlToml(t) { return esc(t).split('\n').map(l => l.startsWith('#') ? `<span class="c">${l}</span>` : /^\[.+\]$/.test(l) ? `<span class="s">${l}</span>` : l.includes(' = ') ? l.replace(/^([^=]+) = (.*)$/, '<span class="k">$1</span> = <span class="v">$2</span>') : l).join('\n'); }
const rawOf = (d, v) => (d.ty === 'bool' ? (v === true || v === 'true' ? 'true' : 'false') : String(v));
function tomlParts(map) {
  const T = window.XDR_TOML, tables = {}, out = [], app = [];
  for (const k of Object.keys(map).sort()) {
    const d = DEF[k];
    if (!d || k === '@res') continue;
    if (k[0] === '@') { if (!appOnly(k)) app.push(`${d.t}: ${label(d, map[k])}`); continue; }
    const i = k.indexOf('.'), sec = k.slice(0, i), name = k.slice(i + 1), raw = rawOf(d, map[k]);
    if (d.drv) { out.push(`${k}: o caminho do driver depende do aparelho; escolha o driver na tela Drivers do app.`); continue; }
    const stored = T.nativeStore(raw), cv = CVARS[name];
    const why = !cv ? 'o núcleo desta versão não lê essa chave' : cv[0] !== sec ? `o núcleo lê essa chave em [${cv[0]}]` : T.coreRejects(cv[1], stored);
    if (why) { out.push(`${k} = ${raw}: ${why}; o núcleo descartaria o valor.`); continue; }
    (tables[sec] = tables[sec] || {})[name] = stored;
  }
  return { tables, out, app, empty: !Object.keys(tables).length };
}
function tomlGame(id, map) { const p = tomlParts(map); return Object.assign(p, { text: p.empty ? '' : window.XDR_TOML.gameConfigFile(id, p.tables) }); }
function tomlGlobalParts() { const p = tomlParts(GLOBAL); return Object.assign(p, { text: p.empty ? '' : window.XDR_TOML.formatTomlTables(p.tables) + '\n' }); }
function tomlNotes(p) {
  return (p.out.length ? `<div class="note" style="margin-top:10px"><b>Fora do arquivo</b><ul class="tnotes">${p.out.map(x => `<li>${esc(x)}</li>`).join('')}</ul></div>` : '')
    + (p.app.length ? `<div class="note" style="margin-top:8px"><b>Guardados pelo app, fora do TOML</b><ul class="tnotes">${p.app.map(x => `<li>${esc(x)}</li>`).join('')}</ul></div>` : '');
}
