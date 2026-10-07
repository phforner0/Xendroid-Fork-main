/* Biblioteca e ficha do jogo: B (grade + painel) no toque, C (carrossel + menu vertical) no controle. */
'use strict';

Object.assign(NOTES, {
  search: ['app', 'A busca acha o jogo pelo nome ou pelo Title ID, sem diferenciar maiúsculas.'],
  density: ['app', 'Tamanho das capas: P, M (o padrão) ou G. A escolha fica guardada.'],
  chips: ['app', 'Filtros sempre à vista: Todos, Favoritos, cada coleção e, quando a biblioteca tem mais de um formato, um filtro por formato. Em pé, a ordem também fica nesta linha.'],
  libmenu: ['app', 'O ⋮ da biblioteca reúne as pastas de jogos, os jogos que saíram da biblioteca, procurar de novo, o assistente, os dados do usuário e as atualizações.'],
  cover: ['sim', 'As capas aqui são marcadores com as iniciais. No app, sem capa escolhida, aparece o ícone 64×64 do próprio jogo, desfocado ao fundo e nítido na frente, com o nome; a capa pode ser trocada pela ficha.'],
  detail: ['app', 'Deitado, o primeiro toque escolhe o jogo e mostra este painel; tocar de novo, ou um toque longo, abre a ficha. Em pé, o toque abre a ficha direto.'],
  quick: ['app', 'Ajustes rápidos: os ajustes fixados com o alfinete em Todos os ajustes, editados para este jogo. No painel da biblioteca o driver fica de fora.'],
  rail: ['app', 'Deitado, o trilho leva às áreas do app: Jogos, Coleções, Conteúdo, Perfis, Controles, Drivers e Ajustes. Em pé vira a barra de baixo, sem Coleções e Drivers.'],
  blades: ['app', 'Com o modo controle, a biblioteca tem abas trocadas por LB/RB: Recentes, Favoritos, Todos e cada coleção. LT/RT pulam 5 jogos.'],
  carousel: ['app', 'Carrossel de capas: ◀ ▶ move, A joga, X abre a ficha, Y favorita, View busca e Start (≡) abre o menu do app.'],
  dyn: ['app', 'No modo controle, a cor da interface vem da capa do jogo em foco.'],
  guide: ['app', 'O menu do modo controle (Start ou ≡) leva a todas as áreas do app e volta ao modo toque.'],
  hints: ['app', 'Dicas de botão sempre visíveis no modo controle. Com “B confirma” em Configurações, A e B trocam de papel nos menus do app.'],
  cmenu: ['app', 'Na ficha com controle, um menu vertical: o conteúdo de cada item aparece ao lado ao mover o foco.'],
  crow: ['app', 'No modo controle, ◀ ▶ muda o valor da linha em foco; A liga, desliga ou avança.'],
  secnav: ['app', 'Seções à esquerda, com o número de ajustes que este jogo muda em cada grupo.'],
  play: ['app', 'Jogar abre o jogo com o perfil ativo (P1). Com mais de um perfil, o app pode perguntar quem joga antes, se essa opção estiver ligada em Perfis.'],
  launch: ['app', '“Iniciar com…” muda só esta abertura: perfil, driver, executável (launch_module), sem patches, sem os ajustes deste jogo e linha de comando extra. Nada é salvo, e um frontend externo não consegue passar essas opções.'],
  ldrv: ['app', 'Os pacotes de driver listados são os instalados em Drivers; driver personalizado só carrega em GPU Adreno.'],
  perfcard: ['app', 'Cada sessão guarda FPS por segundo, tempo de quadro, pipelines, áudio e temperatura da bateria, sem nada a ligar.'],
  perfex: ['sim', 'As sessões são exemplos: o FPS mediano segue a medição publicada no README do projeto (e a queda, quando o README a dá); os outros números são ilustrativos.'],
  timeline: ['app', 'A linha do tempo vem do gravador de eventos da sessão, em inglês, como no app: ciclo de vida, pausas, travadas, calor, controles e erros.'],
  ineffect: ['app', 'Ajustes em vigor: o que a sessão usou fora dos padrões do núcleo, no formato que o núcleo grava. Aqui entram só os ajustes mudados nesta simulação.'],
  runs: ['sim', 'No app, cada sessão da lista abre com os próprios números; aqui só a mais nova tem números.'],
  report: ['app', 'O relatório é revisado antes de sair do aparelho: caminhos, contas e endereços saem, e nada é enviado até você escolher onde compartilhar.'],
  compat: ['app', 'A avaliação é sua e fica neste aparelho, com a build, a GPU e o driver. O catálogo de compatibilidade online vem desligado nas builds publicadas.'],
  patches: ['dif', 'Os patches são os do catálogo que vai no APK, agrupados por arquivo. No app, depois da primeira sessão os arquivos se dividem em “Para a sua versão” e “Para outras versões”, pelo hash do jogo; aqui a versão não é conhecida.'],
  scope: ['app', 'Escolha entre editar “Este jogo” ou o “Global” na mesma tela; cada linha diz de onde vem o valor.'],
  apply: ['app', '“Ao vivo” também muda com o jogo aberto, pelo menu do jogo; “Próxima abertura” vale quando o jogo abrir de novo.'],
  newcvar: ['app', 'NOVO marca ajustes que o núcleo já lia e a interface antiga não mostrava.'],
  newopt: ['app', 'NOVA OPÇÃO marca valores que o núcleo aceita e a lista antiga não oferecia.'],
  levels: ['app', 'Ajustes mostrados: Essencial, Avançado ou Tudo. As contagens vêm do código da versão escolhida.'],
  filters: ['app', 'A busca acha também a chave do TOML e a descrição; os filtros mostram só os mudados, os novos, os ao vivo ou os fixados.'],
  toml: ['sim', 'No app, TOML mostra as linhas fora do padrão para ler e copiar. Aqui também dá para trocar o Title ID e baixar o arquivo, conferido contra as cvars do núcleo desta versão.'],
  presets: ['app', 'Predefinições aplicam vários ajustes neste jogo de uma vez, com desfazer.'],
  'presets-off': ['app', 'A lista de perfis recomendados que vem no app está vazia nesta build, e os ajustes da comunidade só existem em builds com servidor; as publicadas não têm.'],
  pin: ['app', 'O alfinete fixa o ajuste nos ajustes rápidos da biblioteca e da ficha.'],
  dep: ['app', 'Quando um ajuste depende de outro, a linha diz por que não vale agora, em vez de falhar calada.'],
  saves: ['app', 'Saves por perfil, com backup, restauração revisada e pasta de sincronização opcional.'],
});

/* ---------- B ---------- */
function bCard(g) {
  return `<button class="b-card ${S.bSel === g.id ? 'picked' : ''}" data-act="bsel" data-gid="${g.id}" data-k="b-${g.id}" aria-label="${esc(g.name)}" data-note="cover">${coverHTML(g)}<span class="nm">${esc(g.name)}</span><span class="sub">${dot(g)}${CS[stOf(g)][1]} · ${g.runs ? esc(g.ago) : 'nunca'}</span></button>`;
}
function bDetail(g) {
  if (!g) return '<div class="empty">Escolha um jogo.</div>';
  const last = lastSessionText(g);
  return `<div class="b-dhead"><div class="bgimg" style="background-image:url('${art(g)}')"></div>${coverHTML(g)}<div style="min-width:0"><h2>${esc(g.name)}</h2><div class="ids">${g.id} · ${fmtLabel(g)}</div>${pill(g, true)}</div></div>
    <div class="b-dbody">
      <div class="b-dacts"><button class="btn primary" data-act="play" data-gid="${g.id}" data-k="bd-play" data-note="play">${ic('play', 18)} Jogar</button><button class="btn" data-act="open" data-gid="${g.id}" data-k="bd-open">Ficha</button><button class="ibtn ${isFav(g) ? 'on' : ''}" data-act="fav" data-gid="${g.id}" data-k="bd-fav" aria-label="Favoritos" aria-pressed="${isFav(g)}">${ic(isFav(g) ? 'starF' : 'star')}</button></div>
      <dl class="kv"><dt>Tempo jogado</dt><dd>${g.runs ? fmtMin(g.playMin) : '—'}</dd><dt>Última sessão</dt><dd>${last || '—'}</dd><dt>Patches do jogo</dt><dd>${g.patches.length ? `${PON[g.id].size} de ${g.patches.length}` : '—'}</dd><dt>Entra como</dt><dd>${esc(activeProfile().tag)}</dd></dl>
      <div><h4>Ajustes rápidos · este jogo <button class="link" data-act="open-set" data-gid="${g.id}" data-k="bd-allset">Todos os ajustes</button></h4>${quickPanel(g, 'b', true)}</div>
    </div>`;
}
const sortSelect = () => `<select class="sel" aria-label="Ordenar" data-act-change="sort" data-k="b-sort">${SORTS.map(([v, t]) => `<option value="${v}"${S.sort === v ? ' selected' : ''}>${t}</option>`).join('')}</select>`;
const noMatch = () => `<div class="empty">Nenhum jogo corresponde à busca. <button class="link" data-act="lib-clear" data-k="lib-clear">Limpar busca</button></div>`;
function viewBLib() {
  const list = libList(); const sel = GBY[S.bSel] && list.includes(GBY[S.bSel]) ? GBY[S.bSel] : list[0];
  return `<div class="b-lib">${rail()}
    <main class="b-main">
      <div class="b-tools">
        <label class="search" data-note="search">${ic('search', 18)}<input id="b-q" type="search" placeholder="Buscar jogos ou Title ID" value="${esc(S.q)}" data-k="b-q" autocomplete="off" spellcheck="false"></label>
        ${isPortrait() ? '' : sortSelect()}
        <div class="seg" role="group" aria-label="Tamanho das capas" data-note="density">${[['P', 'Pequenas'], ['M', 'Médias'], ['G', 'Grandes']].map(([d, t]) => `<button data-act="density" data-v="${d}" aria-pressed="${S.bDensity === d}" aria-label="Capas ${t}" data-k="den-${d}">${d}</button>`).join('')}</div>
        <button class="ibtn" data-act="modal" data-v="libmenu" data-k="b-menu" aria-label="Mais">${ic('more')}</button>
      </div>
      <div class="b-chips" data-sk="b-chips" data-note="chips">${filterChips('bf')}${isPortrait() ? '<span class="sepr" aria-hidden="true"></span>' + sortSelect() : ''}</div>
      <div class="b-grid d-${S.bDensity}" id="b-grid" data-sk="b-grid">${list.map(bCard).join('') || noMatch()}</div>
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
  const last = lastSessionText(g);
  return `<div class="tt"><h2>${esc(g.name)}</h2><div class="meta">${pill(g, true)}<span>${g.runs ? `${esc(g.ago)} · ${fmtMin(g.playMin)}` : 'Nunca jogado'}</span><span>${fmtLabel(g)}${g.patches.length ? ` · ${PON[g.id].size} de ${g.patches.length} patches` : ''}</span>${last ? `<span>${last}</span>` : ''}</div></div>
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
    ${hints([['A', 'Jogar', 'play'], ['X', 'Ficha', 'open'], ['Y', isFav(g) ? 'Desfavoritar' : 'Favoritar', 'fav'], ['LB/RB', 'Abas', 'tabs'], ['LT/RT', 'Pular 5', 'jump'], ['≡', 'Menu', 'guide']])}
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
action('open-set', el => { S.gameId = el.dataset.gid; S.sec.game = 'set'; go('game'); });
action('lib-clear', () => { S.q = ''; S.lf = 'all'; render(); });
action('bsel', (el, e) => { const gid = el.dataset.gid; if (isPortrait() || (S.bSel === gid && e && e.detail >= 2)) { openGame(gid, el); return; } S.bSel = gid; S.gameId = gid; render(); });
action('density', el => { S.bDensity = el.dataset.v; persist(); render(); });
action('c-blade', el => { S.cBlade = el.dataset.v; S.cIndex = 0; render(); });
action('c-pick', el => { const i = Number(el.dataset.i); if (i === S.cIndex) openGame(el.dataset.gid, el); else cMove(0, i); });
action('gsec', el => { const v = el.dataset.v; S.sec.game = isC() ? ({ ov: 'play' }[v] || (v.startsWith('set:') ? 'set' : v)) : v; render(); const b = document.getElementById('secbody') || document.getElementById('c-panel'); if (b) b.scrollTop = 0; });
action('change:sort', el => { S.sort = el.value; render(); });
action('input:b-q', el => { S.q = el.value; const grid = document.getElementById('b-grid'); if (grid) { const l = libList(); grid.innerHTML = l.map(bCard).join('') || noMatch(); } schedulePins(); });
action('input:s-q', el => { S.q = el.value; const res = document.getElementById('s-res'); if (res) { const l = libList(); res.innerHTML = l.map(bCard).join('') || '<div class="empty">Nenhum jogo corresponde à busca</div>'; } schedulePins(); });
modal('search', () => { const l = libList(); return { wide: true, html: sheetHead('Buscar') + `<label class="search">${ic('search', 18)}<input id="s-q" type="search" placeholder="Buscar jogos ou Title ID" value="${esc(S.q)}" data-k="s-q" autocomplete="off" spellcheck="false" data-autofocus></label><div class="b-grid d-P" id="s-res" style="padding:0;overflow:visible">${l.map(bCard).join('') || '<div class="empty">Nenhum jogo corresponde à busca</div>'}</div>` }; });

screen('library', {
  title: 'Biblioteca',
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
  return `<div class="c-hero">${coverHTML(g, '', true)}<div class="tt"><span class="eyebrow">${fmtLabel(g)} · ${g.id}</span><h2>${esc(g.name)}</h2><div class="meta">${pill(g, true)}<span>${g.runs ? `${fmtMin(g.playMin)} · ${esc(g.ago)}` : 'Nunca jogado'}</span></div>${st ? `<div class="meta"><span>Última sessão: ${st.p50} FPS na mediana, ${st.p5} nos piores 5%</span></div>` : ''}<div class="row"><button class="btn primary lg" data-act="play" data-gid="${g.id}" data-k="ch-play" data-note="play">${ic('play')} Jogar</button><span class="muted" style="font-size:12.5px">Entra como ${esc(activeProfile().tag)}</span></div></div></div>`;
}
screen('game', {
  title: 'Ficha do jogo', secKey: 'game',
  onSec(v) { if (isC() && v === 'play' && S.sec.game === 'play') { ACT.play({ dataset: { gid: S.gameId } }); return true; } return false; },
  render() {
    const g = curGame();
    if (isC()) {
      if (!['play', 'launch', 'quick', 'set', 'perf', 'cont', 'data', 'compat'].includes(S.sec.game)) S.sec.game = String(S.sec.game || '').startsWith('set:') ? 'set' : 'play';
      return sectioned({
        key: 'game', title: g.name, sub: g.id, head: S.sec.game === 'play' ? '' : coverHTML(g, '', true), art: art(g), dyn: dynVars(g),
        hints: [['A', 'Selecionar', 'a'], ['B', 'Voltar', 'back'], ['Y', isFav(g) ? 'Desfavoritar' : 'Favoritar', 'fav'], ['LB/RB', 'Seções', 'tabs'], ['≡', 'Menu', 'guide']],
        sections: [
          { id: 'play', t: 'Jogar', icon: 'play', play: true, noTitle: true, body: () => cHero(g) },
          { id: 'launch', t: 'Iniciar com…', icon: 'sliders', lead: `Só nesta abertura de ${esc(g.name)}`, body: () => launchBody(g) + `<div class="row" style="margin-top:12px"><button class="btn primary lg" data-act="play" data-gid="${g.id}" data-k="cl-go">${ic('play')} Jogar com estas opções</button></div>` },
          { id: 'quick', t: 'Ajustes rápidos', icon: 'pin', lead: '◀ ▶ muda o valor. Escolha o que aparece aqui fixando ajustes em Todos os ajustes.', body: () => quickPanel(g, 'c') },
          { id: 'set', t: 'Todos os ajustes', icon: 'gear', n: ovCount(g) || '', body: () => settingsPanel(g, 'c') },
          { id: 'perf', t: 'Desempenho', icon: 'chart', body: () => perfTabHTML(g) },
          { id: 'cont', t: 'Patches e conteúdo', icon: 'patch', n: g.patches.length ? `${PON[g.id].size}/${g.patches.length}` : '', body: () => contentHTML(g) },
          { id: 'data', t: 'Saves e dados', icon: 'save', body: () => dataHTML(g) },
          { id: 'compat', t: 'Compatibilidade', icon: 'shield', body: () => `<div class="card">${compatHTML(g)}</div>` },
        ],
      });
    }
    return sectioned({
      key: 'game', title: g.name, art: art(g), head: coverHTML(g, '', true),
      sub: `<span>${g.id}</span><span>${fmtLabel(g)}</span>`,
      actions: `<button class="ibtn ${isFav(g) ? 'on' : ''}" data-act="fav" data-gid="${g.id}" data-k="g-fav" aria-label="Favoritos" aria-pressed="${isFav(g)}">${ic(isFav(g) ? 'starF' : 'star')}</button><button class="ibtn" data-act="modal" data-v="more" data-k="g-more" aria-label="Mais">${ic('more')}</button><button class="btn" data-act="modal" data-v="launch" data-k="g-launch" data-note="launch">Iniciar com…</button><button class="btn primary" data-act="play" data-gid="${g.id}" data-k="g-play" data-autofocus>${ic('play', 18)} Jogar</button>`,
      sections: [
        { group: 'Jogo', id: 'ov', t: 'Visão geral', icon: 'home', noTitle: true, body: v => overviewHTML(g, v) },
        { group: 'Ajustes deste jogo', id: 'set', t: 'Todos os ajustes', icon: 'gear', n: ovCount(g) || '', body: v => settingsPanel(g, v) },
        ...GROUPS.map(([id, t, i]) => ({ group: 'Ajustes deste jogo', id: 'set:' + id, t, icon: i, n: groupOv(g, id) || '', body: v => settingsPanel(g, v, id) })),
        { group: 'Mais', id: 'perf', t: 'Desempenho', icon: 'chart', body: () => perfTabHTML(g) },
        { id: 'cont', t: 'Patches e conteúdo', icon: 'patch', body: () => contentHTML(g) },
        { id: 'data', t: 'Saves e dados', icon: 'save', body: () => dataHTML(g) },
      ],
    });
  },
});
