/* Biblioteca e ficha do jogo: B (grade + painel) no toque, C (carrossel + menu vertical) no controle. */
'use strict';

Object.assign(NOTES, {
  search: ['existe', 'Busca por nome ou Title ID (já existe). Ganha atalho “/” no teclado e View no controle.'],
  cover: ['novo', 'Capa grande. Sem capa própria, o ícone 64×64 do jogo vira fundo desfocado, ícone nítido e nome.'],
  play: ['existe', 'Jogar segue como ação principal, agora com o perfil escrito ao lado.'],
  launch: ['novo', '“Iniciar com…”: perfil, driver, executável (launch_module), sem patches ou sem os ajustes deste jogo, só para esta abertura.'],
  perfcard: ['existe', 'Dados reais de cada sessão (C02): FPS por segundo, tempo de quadro, pipelines, áudio e temperatura.'],
  quick: ['novo', 'Ajustes rápidos: o jogador escolhe o que aparece aqui com o alfinete de cada ajuste.'],
  compat: ['existe', 'Resultado de compatibilidade do próprio usuário, com build, GPU e driver (C03).'],
  patches: ['existe', 'Patches conferidos contra a versão do jogo (L10); os interruptores vêm para dentro da ficha.'],
  scope: ['novo', 'Escopo explícito: editar “Este jogo” ou “Global” na mesma tela; cada linha diz de onde vem o valor.'],
  apply: ['existe', 'Contrato do ajuste (SettingContract): “Ao vivo” ou “Próxima abertura”, agora em todas as linhas.'],
  newcvar: ['exposto', 'NOVO: cvar que o core já lê e a UI não mostrava. O texto traduz a descrição do próprio core.'],
  newopt: ['exposto', 'NOVA OPÇÃO: valor que o core aceita e a lista atual não oferece (modo de vídeo 17, gama tipo 3).'],
  levels: ['existe', 'Hoje são 2 modos (Jogador com 23 ajustes, Desenvolvedor com 146). Proposta: Essencial, Avançado e Tudo.'],
  filters: ['novo', 'Filtros: só o que mudou, só os novos, só os ao vivo, só os fixados. A busca acha também a chave do TOML.'],
  toml: ['novo', 'Mostra o TOML que será gravado: só o que muda.'],
  presets: ['novo', 'Predefinições aplicam vários ajustes de uma vez, com desfazer. Perfis recomendados (C05) e da comunidade (15b) entram aqui.'],
  pin: ['novo', 'Fixar nos ajustes rápidos.'],
  dep: ['novo', 'Diz por que o ajuste não vale agora (depende de outro ajuste ou do driver), em vez de falhar calado.'],
  rail: ['novo', 'Trilho de navegação no toque; no retrato vira barra inferior. No modo controle o mesmo destino fica no menu (Start/≡).'],
  density: ['novo', 'Tamanho das capas ajustável: P, M, G.'],
  chips: ['existe', 'Favoritos, coleções e formatos como filtros sempre à vista (hoje ficam em menus).'],
  detail: ['novo', 'Mestre-detalhe em paisagem: Jogar e ajustes rápidos sem sair da grade.'],
  secnav: ['novo', 'Seções à esquerda, com a contagem do que mudou em cada uma.'],
  blades: ['existe', 'Abas por LB/RB, com as coleções como abas. O carrossel para controle já existe (lote 15a).'],
  carousel: ['existe', 'Carrossel do lote 15a: no modo controle vira a biblioteca.'],
  dyn: ['novo', 'Cor da interface tirada da capa (androidx.palette); muda a cada jogo em foco.'],
  hints: ['existe', 'Ações do carrossel (Jogar, Ficha, Favoritar) com dicas de botão sempre visíveis.'],
  cmenu: ['novo', 'Menu vertical grande, navegável só com o direcional; o conteúdo aparece ao lado ao mover o foco.'],
  crow: ['novo', 'No controle, ◀ ▶ muda o valor da linha em foco; A avança e liga ou desliga.'],
  saves: ['existe', 'Saves com backup e restauração transacional (A04/A09).'],
  timeline: ['existe', 'Linha do tempo da última execução: pausas, engasgos, calor, controles.'],
  report: ['existe', 'Relatório da última execução (C06), revisado antes de compartilhar.'],
  guide: ['novo', 'Menu do modo controle (Start/≡), como o guia do console: leva a todas as áreas e volta ao modo toque.'],
});

/* ---------- B ---------- */
function bCard(g) {
  return `<button class="b-card ${S.bSel === g.id ? 'picked' : ''}" data-act="bsel" data-gid="${g.id}" data-k="b-${g.id}" aria-label="${esc(g.name)}">${coverHTML(g)}<span class="nm">${esc(g.name)}</span><span class="sub">${dot(g)}${CS[stOf(g)][1]} · ${g.runs ? esc(g.ago) : 'nunca'}</span></button>`;
}
function bDetail(g) {
  if (!g) return '<div class="empty">Escolha um jogo.</div>';
  const st = perfOf(g);
  return `<div class="b-dhead"><div class="bgimg" style="background-image:url('${art(g)}')"></div>${coverHTML(g)}<div style="min-width:0"><h2>${esc(g.name)}</h2><div class="ids">${g.id} · ${fmtLabel(g)} · ${g.size}</div>${pill(g)}</div></div>
    <div class="b-dbody">
      <div class="b-dacts"><button class="btn primary" data-act="play" data-gid="${g.id}" data-k="bd-play" data-note="play">${ic('play', 18)} Jogar</button><button class="btn" data-act="open" data-gid="${g.id}" data-k="bd-open">Ficha</button><button class="ibtn ${isFav(g) ? 'on' : ''}" data-act="fav" data-gid="${g.id}" data-k="bd-fav" aria-label="Favorito">${ic(isFav(g) ? 'starF' : 'star')}</button></div>
      <dl class="kv"><dt>Tempo jogado</dt><dd>${g.runs ? fmtMin(g.playMin) : '—'}</dd><dt>Última sessão</dt><dd>${st ? `${st.p50} FPS mediana · ${st.p5} nos piores 5%` : (g.runs ? 'sem quadros' : '—')}</dd><dt>Patches</dt><dd>${PON[g.id].size} de ${g.patches.length}</dd><dt>Entra como</dt><dd>${esc(activeProfile().tag)}</dd></dl>
      <div><h4>Ajustes rápidos · este jogo <button class="link" data-act="open-set" data-gid="${g.id}" data-k="bd-allset">Todos</button></h4>${quickPanel(g, 'b')}</div>
    </div>`;
}
function viewBLib() {
  const list = libList(); const sel = GBY[S.bSel] && list.includes(GBY[S.bSel]) ? GBY[S.bSel] : list[0];
  return `<div class="b-lib">${rail()}
    <main class="b-main">
      <div class="b-tools">
        <label class="search" data-note="search">${ic('search', 18)}<input id="b-q" type="search" placeholder="Buscar jogos ou Title ID" value="${esc(S.q)}" data-k="b-q" autocomplete="off" spellcheck="false"></label>
        <select class="sel" aria-label="Ordenar" data-act-change="sort" data-k="b-sort">${SORTS.map(([v, t]) => `<option value="${v}"${S.sort === v ? ' selected' : ''}>${t}</option>`).join('')}</select>
        <div class="seg" role="group" aria-label="Tamanho das capas" data-note="density">${[['P', 'Pequenas'], ['M', 'Médias'], ['G', 'Grandes']].map(([d, t]) => `<button data-act="density" data-v="${d}" aria-pressed="${S.bDensity === d}" aria-label="Capas ${t}" data-k="den-${d}">${d}</button>`).join('')}</div>
      </div>
      <div class="b-chips" data-sk="b-chips" data-note="chips">${filterChips('bf')}</div>
      <div class="b-grid d-${S.bDensity}" id="b-grid" data-sk="b-grid">${list.map(bCard).join('') || '<div class="empty">Nenhum jogo com esse filtro.</div>'}</div>
    </main>
    <aside class="b-detail" data-sk="b-detail" data-note="detail">${bDetail(sel)}</aside>
  </div>`;
}

/* ---------- C ---------- */
const cBlades = () => [['recent', 'Recentes'], ['fav', 'Favoritos'], ['all', 'Todos'], ...COLS.map(c => ['col:' + c, c])];
function cList() {
  const b = S.cBlade;
  if (b === 'recent') return recents();
  if (b === 'fav') return sortList(GAMES.filter(isFav), 'recent');
  if (b === 'all') return sortList(GAMES.slice(), 'az');
  return sortList(GAMES.filter(g => g.cols.includes(b.slice(4))), 'az');
}
function cInfo(g) {
  return `<div class="tt"><h2>${esc(g.name)}</h2><div class="meta">${pill(g)}<span>${g.runs ? `${esc(g.ago)} · ${fmtMin(g.playMin)}` : 'Nunca jogado'}</span><span>${fmtLabel(g)} · ${PON[g.id].size}/${g.patches.length} patches${g.tu ? ' · ' + g.tu : ''}</span>${perfLine(g)}</div></div>
    <div class="acts"><button class="btn primary" data-act="play" data-gid="${g.id}" data-k="ci-play">${ic('play', 18)} Jogar</button><button class="btn" data-act="open" data-gid="${g.id}" data-k="ci-open">Ficha</button></div>`;
}
function viewCLib() {
  const list = cList(); S.cIndex = clamp(S.cIndex, 0, Math.max(0, list.length - 1)); const g = list[S.cIndex] || GAMES[0];
  const p = activeProfile();
  return `<div class="c-lib" style="${dynVars(g)}" id="c-lib" data-note="dyn">
    <div class="c-bg"><div class="bgimg" id="c-bgimg" style="background-image:url('${art(g)}')"></div></div>
    <header class="c-top"><span class="gb LB">LB</span><nav class="c-blades" role="tablist" data-sk="c-blades" data-note="blades">${cBlades().map(([v, t]) => `<button role="tab" aria-selected="${S.cBlade === v}" data-act="c-blade" data-v="${esc(v)}" data-k="cb-${esc(v)}">${esc(t)}</button>`).join('')}</nav><span class="gb RB">RB</span>
      <button class="ibtn" data-act="modal" data-v="search" data-k="c-search" aria-label="Buscar">${ic('search')}</button><button class="c-prof" data-act="guide" data-k="c-prof" data-note="guide">${avatarImg(p, '')}<span>${esc(p.tag)}</span></button></header>
    <div class="c-mid"><div class="c-stage" data-navx="carousel" data-note="carousel"><div class="c-track" id="c-track">${list.map((x, j) => `<button class="c-item ${j === S.cIndex ? 'on' : ''}" data-act="c-pick" data-i="${j}" data-gid="${x.id}" data-k="ci-${x.id}" aria-label="${esc(x.name)}">${coverHTML(x)}</button>`).join('') || '<div class="empty">Nada nesta aba.</div>'}</div></div>
    <div class="c-info" id="c-info">${list.length ? cInfo(g) : ''}</div></div>
    ${hints([['A', 'Jogar', 'play'], ['X', 'Ficha', 'open'], ['Y', isFav(g) ? 'Desfavoritar' : 'Favoritar', 'fav'], ['LB/RB', 'Abas', 'tabs'], ['≡', 'Menu', 'guide']])}
  </div>`;
}
function cLayout(animate) {
  const track = document.getElementById('c-track'); if (!track) return; const on = track.querySelector('.c-item.on'); if (!on) return;
  const stage = track.parentElement; const x = stage.clientWidth / 2 - (on.offsetLeft + on.offsetWidth / 2);
  if (!animate) { track.style.transition = 'none'; track.style.transform = `translateX(${x}px)`; void track.offsetWidth; track.style.transition = ''; }
  else track.style.transform = `translateX(${x}px)`;
}
function cMove(d, abs) {
  const list = cList(); if (!list.length) return;
  const i = clamp(abs != null ? abs : S.cIndex + d, 0, list.length - 1); if (i === S.cIndex && abs == null) return;
  S.cIndex = i; const g = list[i];
  app.querySelectorAll('#c-track .c-item').forEach((el, j) => el.classList.toggle('on', j === i));
  cLayout(true);
  const lib = document.getElementById('c-lib'); if (lib) lib.setAttribute('style', dynVars(g));
  const bg = document.getElementById('c-bgimg'); if (bg) bg.style.backgroundImage = `url('${art(g)}')`;
  const info = document.getElementById('c-info'); if (info) info.innerHTML = cInfo(g);
  const fav = app.querySelector('.c-hints [data-v="fav"] span:last-child'); if (fav) fav.textContent = isFav(g) ? 'Desfavoritar' : 'Favoritar';
  const item = app.querySelectorAll('#c-track .c-item')[i]; if (item && (S.nav || abs != null)) item.focus({ preventScroll: true });
  schedulePins();
}
NAVX.carousel = (cur, dir) => { if (!cur.classList.contains('c-item')) return false; cMove(dir === 'right' ? 1 : -1); return true; };

/* ---------- ações ---------- */
function openGame(gid, from) {
  if (!GBY[gid]) return;
  S.gameId = gid;
  go('game', {}, { vt: true, from });
}
action('open', el => openGame(el.dataset.gid, el.classList && el.classList.contains ? el : null));
action('open-set', el => { S.gameId = el.dataset.gid; S.sec.game = isC() ? 'set' : 'set:img'; go('game'); });
action('bsel', (el, e) => { const gid = el.dataset.gid; if (isPortrait() || (S.bSel === gid && e && e.detail >= 2)) { openGame(gid, el); return; } S.bSel = gid; S.gameId = gid; render(); });
action('density', el => { S.bDensity = el.dataset.v; persist(); render(); });
action('c-blade', el => { S.cBlade = el.dataset.v; S.cIndex = 0; render(); });
action('c-pick', el => { const i = Number(el.dataset.i); if (i === S.cIndex) openGame(el.dataset.gid, el); else cMove(0, i); });
action('gsec', el => { const v = el.dataset.v; S.sec.game = isC() ? ({ 'set:img': 'set', ov: 'play' }[v] || v) : v; render(); const b = document.getElementById('secbody') || document.getElementById('c-panel'); if (b) b.scrollTop = 0; });
action('change:sort', el => { S.sort = el.value; render(); });
action('input:b-q', el => { S.q = el.value; const grid = document.getElementById('b-grid'); if (grid) { const l = libList(); grid.innerHTML = l.map(bCard).join('') || '<div class="empty">Nenhum jogo com esse filtro.</div>'; } schedulePins(); });
action('input:s-q', el => { S.q = el.value; const res = document.getElementById('s-res'); if (res) { const l = libList(); res.innerHTML = l.map(bCard).join('') || '<div class="empty">Nada encontrado.</div>'; } schedulePins(); });
modal('search', () => { const l = libList(); return { wide: true, html: sheetHead('Buscar') + `<label class="search">${ic('search', 18)}<input id="s-q" type="search" placeholder="Buscar jogos ou Title ID" value="${esc(S.q)}" data-k="s-q" autocomplete="off" spellcheck="false" data-autofocus></label><div class="b-grid d-P" id="s-res" style="padding:0;overflow:visible">${l.map(bCard).join('') || '<div class="empty">Nada encontrado.</div>'}</div>` }; });

screen('library', {
  title: 'Biblioteca', lote: 'Biblioteca e ficha',
  info: {
    what: 'No toque, grade com capas em três tamanhos, filtros sempre à vista e o jogo escolhido num painel ao lado. Com um controle, a mesma biblioteca vira o carrossel com abas por LB/RB e a cor tirada da capa.',
    replaces: 'GameLibraryScreen.kt (grade, menu ⋮ e bottom sheet) e GameCarousel.kt (carrossel do lote 15a, visão separada).',
    changes: ['Trilho com as áreas do app no lugar do menu ⋮ com 15 itens', 'Painel lateral com Jogar e ajustes rápidos em paisagem', 'Carrossel promovido a modo controle, ligado sozinho com um controle'],
    c: 'Carrossel de capas grandes, abas por LB/RB, Y favorita, X abre a ficha, Start abre o menu.',
    code: 'ui/library/GameLibraryScreen.kt, GameCarousel.kt, GameLibraryViewModel.kt',
  },
  render() { return isC() ? viewCLib() : viewBLib(); },
  after() { if (isC()) cLayout(false); },
  onFocus(t) { if (isC() && t.classList.contains('c-item')) { const i = Number(t.dataset.i); if (i !== S.cIndex) cMove(0, i); } },
  tabStep(d) {
    const rot = (arr, cur) => arr[(arr.indexOf(cur) + d + arr.length) % arr.length];
    if (isC()) { S.cBlade = rot(cBlades().map(b => b[0]), S.cBlade); S.cIndex = 0; render(); const it = document.querySelector('#c-track .c-item.on'); if (it) it.focus({ preventScroll: true }); }
    else { const fs = ['all', 'fav', ...COLS.map(c => 'col:' + c)]; S.lf = rot(fs, fs.includes(S.lf) ? S.lf : 'all'); render(); focusKey('bf-' + S.lf); }
  },
});

/* ---------- ficha ---------- */
function cHero(g) {
  const st = perfOf(g);
  return `<div class="c-hero">${coverHTML(g, '', true)}<div class="tt"><span class="eyebrow">${fmtLabel(g)} · ${g.id}</span><h2>${esc(g.name)}</h2><div class="meta">${pill(g)}<span>${g.runs ? `${fmtMin(g.playMin)} · ${esc(g.ago)}` : 'Nunca jogado'}</span></div>${st ? `<div class="meta"><span>Última sessão: <b>${st.p50} FPS</b> na mediana, ${st.p5} nos piores 5%</span></div>` : ''}<div class="row"><button class="btn primary lg" data-act="play" data-gid="${g.id}" data-k="ch-play">${ic('play')} Jogar</button><span class="muted" style="font-size:12.5px">Entra como ${esc(activeProfile().tag)}</span></div></div></div>`;
}
screen('game', {
  title: 'Ficha do jogo', lote: 'Biblioteca e ficha', secKey: 'game',
  info: {
    what: 'A ficha vira uma tela: Jogar e Iniciar com… sempre à vista, e seções para visão geral, cada grupo de ajustes deste jogo, desempenho, patches e conteúdo, saves e dados.',
    replaces: 'O ModalBottomSheet de GameLibraryScreen.kt (até 17 itens em lista) e as telas separadas de ajustes do jogo, patches, conteúdo e saves.',
    changes: ['Ajustes deste jogo dentro da ficha, com escopo, aplicação, busca pela chave, níveis, alfinete e TOML', '29 cvars do core expostas e 2 valores novos', 'Desempenho com os histogramas que cada sessão grava'],
    c: 'Menu vertical (Jogar, Iniciar com…, Ajustes rápidos, Todos os ajustes, Desempenho, Patches e conteúdo, Saves e dados, Compatibilidade); ◀ ▶ muda valores; cor tirada da capa.',
    code: 'ui/library/GameLibraryScreen.kt, ui/settings/PerGameSettingsScreen.kt, settings/SettingsSchema.kt',
  },
  onSec(v) { if (isC() && v === 'play' && S.sec.game === 'play') { ACT.play({ dataset: { gid: S.gameId } }); return true; } return false; },
  render() {
    const g = curGame();
    if (isC()) {
      if (!['play', 'launch', 'quick', 'set', 'perf', 'cont', 'data', 'compat'].includes(S.sec.game)) S.sec.game = { 'set:img': 'set', ov: 'play' }[S.sec.game] || (String(S.sec.game || '').startsWith('set:') ? 'set' : 'play');
      return sectioned({
        key: 'game', title: g.name, sub: g.id, head: S.sec.game === 'play' ? '' : coverHTML(g, '', true), art: art(g), dyn: dynVars(g),
        hints: [['A', 'Selecionar', 'a'], ['B', 'Voltar', 'back'], ['Y', isFav(g) ? 'Desfavoritar' : 'Favoritar', 'fav'], ['LB/RB', 'Seções', 'tabs'], ['≡', 'Menu', 'guide']],
        sections: [
          { id: 'play', t: 'Jogar', icon: 'play', play: true, noTitle: true, body: () => cHero(g) },
          { id: 'launch', t: 'Iniciar com…', icon: 'sliders', body: () => launchBody(g) + `<div class="row" style="margin-top:12px"><button class="btn primary lg" data-act="play" data-gid="${g.id}" data-k="cl-go">${ic('play')} Jogar com estas opções</button></div>` },
          { id: 'quick', t: 'Ajustes rápidos', icon: 'pin', lead: '◀ ▶ muda o valor. Escolha o que aparece aqui fixando ajustes em Todos os ajustes.', body: () => quickPanel(g, 'c') },
          { id: 'set', t: 'Todos os ajustes', icon: 'gear', n: ovCount(g) || '', body: () => settingsPanel(g, 'c') },
          { id: 'perf', t: 'Desempenho', icon: 'chart', body: () => perfTabHTML(g) },
          { id: 'cont', t: 'Patches e conteúdo', icon: 'patch', n: `${PON[g.id].size}/${g.patches.length}`, body: () => contentHTML(g) },
          { id: 'data', t: 'Saves e dados', icon: 'save', body: () => dataHTML(g) },
          { id: 'compat', t: 'Compatibilidade', icon: 'shield', body: () => `<div class="card">${compatHTML(g)}</div>` },
        ],
      });
    }
    return sectioned({
      key: 'game', title: g.name, art: art(g), head: coverHTML(g, '', true),
      sub: `<span>${g.id}</span><span>Media ${g.media}</span>${pill(g, true)}`,
      actions: `<button class="ibtn ${isFav(g) ? 'on' : ''}" data-act="fav" data-gid="${g.id}" data-k="g-fav" aria-label="Favorito">${ic(isFav(g) ? 'starF' : 'star')}</button><button class="ibtn" data-act="modal" data-v="more" data-k="g-more" aria-label="Mais">${ic('more')}</button><button class="btn" data-act="modal" data-v="launch" data-k="g-launch" data-note="launch">Iniciar com…</button><button class="btn primary" data-act="play" data-gid="${g.id}" data-k="g-play" data-autofocus>${ic('play', 18)} Jogar</button>`,
      sections: [
        { group: 'Jogo', id: 'ov', t: 'Visão geral', icon: 'home', noTitle: true, body: v => overviewHTML(g, v) },
        ...GROUPS.map(([id, t, i]) => ({ group: 'Ajustes deste jogo', id: 'set:' + id, t, icon: i, n: groupOv(g, id) || '', body: v => settingsPanel(g, v, id) })),
        { group: 'Mais', id: 'perf', t: 'Desempenho', icon: 'chart', body: () => perfTabHTML(g) },
        { id: 'cont', t: 'Patches e conteúdo', icon: 'patch', body: () => contentHTML(g) },
        { id: 'data', t: 'Saves e dados', icon: 'save', body: () => dataHTML(g) },
      ],
    });
  },
});
