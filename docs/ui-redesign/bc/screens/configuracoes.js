/* Lote 1: Configurações globais e Drivers. */
'use strict';

Object.assign(NOTES, {
  resumo: ['novo', 'Resumo: o que está fora do padrão e quais jogos têm ajustes próprios, com volta ao padrão a um toque.'],
  ovgames: ['novo', 'Cada ajuste global diz quantos jogos usam outro valor; tocar lista esses jogos e abre os ajustes deles.'],
  modepref: ['novo', 'Modo controle automático: com um controle conectado a interface vira a C; sem controle volta à B.'],
  appsection: ['novo', 'Opções do próprio app reunidas: modo, nível, botões nos menus, tamanho, idioma e atualizações. Hoje ficam no rodapé da lista de categorias.'],
  bundle: ['existe', 'Pacote de dados (L08): exportar e importar mostrando antes o que muda e o que fica deste aparelho.'],
  userdata: ['existe', 'Pasta de dados pelo DocumentsProvider, somente leitura com um jogo aberto (L07).'],
  drvnow: ['existe', 'Driver escolhido e o que a última sessão realmente carregou (U03), agora lado a lado.'],
  drvsug: ['existe', 'Sugestão pela família da Adreno nos nomes dos arquivos (15p), com o aviso de que não é garantia.'],
  drvsha: ['existe', 'Downloads conferidos pelo SHA-256 publicado; sem checksum, a linha avisa antes.'],
  drvsrc: ['existe', 'Fontes de driver no GitHub (15p): adicionar, remover, voltar à de sempre.'],
  tuflags: ['existe', 'Flags do TU_DEBUG uma a uma, com explicação (15i). Aqui sysmem e gmem se excluem.'],
  drvgames: ['novo', 'Jogos que usam outro driver, com atalho para os ajustes de cada um.'],
  drvprev: ['existe', 'Voltar à escolha anterior sem baixar de novo (os binários instalados ficam).'],
});

/* ---------- Configurações ---------- */
const APP_UI = ['@app.mode', '@app.level', '@app.confirm', '@app.uisize', '@app.textsize'];
function resumoHTML(v) {
  const changed = Object.keys(GLOBAL).filter(k => DEF[k] && !appOnly(k));
  const games = GAMES.filter(g => ovCount(g));
  return `<div class="grid2">
    <section class="card span2" data-note="resumo"><h3>${ic('sliders', 15)} Fora do padrão em todos os jogos<span class="r">${changed.length}</span></h3>
      <div class="list">${changed.length ? changed.map(k => { const d = DEF[k]; return drow({ icon: 'globe', t: esc(d.t), s: `${esc(label(d, GLOBAL[k]))} · padrão ${esc(label(d, d.def))}`, acts: `<button class="btn sm ghost" data-act="reset" data-key="${k}" data-k="rz-${k}">${ic('reset', 14)} Padrão</button>` }); }).join('') : '<p class="note">Tudo no padrão.</p>'}</div>
      <div class="row"><button class="btn sm" data-act="modal" data-v="toml-global" data-k="rz-toml">${ic('code', 15)} Ver o TOML global</button></div></section>
    <section class="card span2"><h3>${ic('gamepad', 15)} Jogos com ajustes próprios<span class="r">${games.length}</span></h3>
      <div class="list">${games.map(g => drow({ img: `<span style="width:34px;display:block">${coverHTML(g)}</span>`, t: esc(g.name), s: Object.keys(OV[g.id]).map(k => esc(DEF[k].t)).join(' · '), acts: `<button class="btn sm ghost" data-act="ovopen-g" data-gid="${g.id}" data-k="rg-${g.id}">Abrir</button>` })).join('')}</div></section>
    <section class="card"><h3>${ic('monitor', 15)} Interface</h3>${appRows(['@app.mode', '@app.level'], v)}</section>
    <section class="card"><h3>${ic('chip', 15)} Driver</h3><dl class="kv"><dt>Todos os jogos</dt><dd>${esc(label(DEF['Vulkan.vulkan_lib_path'], globalOf('Vulkan.vulkan_lib_path')))}</dd><dt>Última sessão carregou</dt><dd>${esc(DRIVERS.lastRun.drv)}</dd></dl><div class="row"><button class="btn sm" data-act="go" data-v="drivers" data-k="rz-drv">Gerenciar drivers</button></div></section>
  </div>`;
}
function bundleHTML() {
  return `<div class="stack">
    <section class="card" data-note="bundle"><h3>${ic('save', 15)} Fazer backup ou levar os ajustes</h3>
      <p class="note">Ajustes do emulador (globais e por jogo), controles de toque, favoritos, coleções e suas notas de compatibilidade, num arquivo só. Saves, perfis, driver e jogos ficam deste aparelho.</p>
      <div class="row"><button class="btn sm" data-act="toast" data-msg="Pacote exportado: 6 partes (protótipo)." data-k="bd-exp">${ic('upload', 15)} Exportar</button><button class="btn sm ghost" data-act="modal" data-v="bundle-import" data-k="bd-imp">${ic('download', 15)} Importar…</button></div></section>
    <section class="card" data-note="userdata"><h3>${ic('folder', 15)} Pasta de dados</h3>
      <div class="list">
        ${drow({ icon: 'gamepad', t: 'Jogos por título', s: 'Patches, configs por jogo e caches' })}
        ${drow({ icon: 'user', t: 'Saves e dados · perfil XenPlayer', s: '61 arquivos · 41 MB' })}
        ${drow({ icon: 'box', t: 'DLC e atualizações de título', s: 'Pacotes instalados' })}
      </div>
      <p class="note">Abre no gerenciador de arquivos do Android. Somente leitura enquanto um jogo roda ou dados estão sendo alterados.</p>
      <div class="row"><button class="btn sm" data-act="toast" data-msg="Abrindo no gerenciador de arquivos (protótipo)." data-k="ud-open">Abrir no gerenciador de arquivos</button></div></section>
  </div>`;
}
function updatesHTML(v) {
  return appRows(['@app.updates'], v) + `<section class="card" style="margin-top:10px"><h3>${ic('download', 15)} Versão</h3><dl class="kv"><dt>Instalada</dt><dd>v412 · 7bb3409</dd><dt>Canal</dt><dd>${esc(label(DEF['@app.updates'], globalOf('@app.updates')))}</dd><dt>Última procura</dt><dd>hoje, 09:12</dd></dl><div class="row"><button class="btn sm" data-act="go" data-v="update" data-k="up-check">Procurar atualizações</button></div></section>`;
}
function communityHTML() {
  return `<section class="card"><h3>${ic('share', 15)} Ajustes da comunidade</h3>
    <p style="font-size:13.5px">Desligado nesta build: nenhum servidor configurado.</p>
    <p class="note">Com um servidor, cada ficha busca pelo Title ID os ajustes que outros jogadores compartilharam, ordenados pelo aparelho mais parecido com o seu, com votos de “ajudou” e “não ajudou”. Aplicar passa pela mesma prévia das predefinições e dá para desfazer.</p>
    <p class="note">Ao compartilhar vão só o modelo do telefone, a build e os ajustes do jogo: nenhuma conta, gamertag, nome de arquivo ou caminho.</p></section>`;
}
function diagHTML() {
  return `<div class="card">
    ${drow({ icon: 'bug', t: 'Diagnóstico', s: `${RUNS.length} sessões guardadas; compartilhe uma ou todas, com dados pessoais removidos`, acts: `<button class="btn sm" data-act="go" data-v="diagnostics" data-k="dg-open">Abrir</button>` })}
    ${drow({ icon: 'ab', t: 'Comparar execuções', s: 'A/B com uma mudança só (driver, limite, geração de quadros)', acts: `<button class="btn sm ghost" data-act="go" data-v="compare" data-k="dg-cmp">Abrir</button>` })}
    ${drow({ icon: 'gamepad', t: 'Teste de controle', s: 'Botões, analógicos, giroscópio e vibração', acts: `<button class="btn sm ghost" data-act="go" data-v="padtest" data-k="dg-pad">Abrir</button>` })}
    ${drow({ icon: 'download', t: 'Exportar logs da sessão para Downloads', s: 'Log do emulador e logcat desta sessão num zip', acts: `<button class="btn sm ghost" data-act="toast" data-msg="Logs exportados para Downloads (protótipo)." data-k="dg-logs">Exportar</button>` })}
    ${drow({ icon: 'sliders', t: 'Nível e filtro do log', s: 'Ficam em Depuração', acts: `<button class="btn sm ghost" data-act="sec" data-key="settings" data-v="set:dbg" data-k="dg-dbg">Abrir</button>` })}
  </div>`;
}
modal('bundle-import', () => ({ html: sheetHead('Importar estes ajustes?', 'xendroid-dados-2026-09-30.zip') + `<div class="list">
    ${drow({ icon: 'sliders', t: 'Ajustes do emulador', s: 'Substituídos (4 linhas diferentes)' })}
    ${drow({ icon: 'gamepad', t: 'Ajustes por jogo', s: 'Novos: 2, substituídos: 1, iguais: 3, mantidos: 1' })}
    ${drow({ icon: 'hand', t: 'Controles de toque', s: 'Iguais aos atuais' })}
    ${drow({ icon: 'star2', t: 'Favoritos', s: '2 adicionados, nenhum removido' })}
    ${drow({ icon: 'layers', t: 'Coleções', s: 'Novas: 1, jogos adicionados: 3, nada removido' })}
    ${drow({ icon: 'shield', t: 'Resultados de compatibilidade', s: '4 adicionados' })}
    ${drow({ icon: 'lock', t: 'Ficam deste aparelho', s: 'Pastas de armazenamento, driver personalizado, saves, perfis, jogos e histórico de jogo' })}
  </div><p class="note">Seus ajustes atuais são salvos antes, então dá para desfazer importando esse backup.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="m-cancel">Cancelar</button><button class="btn primary" data-act="toast" data-msg="Importado. Os ajustes anteriores ficaram em Downloads (protótipo)." data-k="m-imp" data-autofocus>Importar</button></div>` }));
action('ovopen-g', el => { S.gameId = el.dataset.gid; S.sec.game = isC() ? 'set' : 'set:' + DEF[Object.keys(OV[el.dataset.gid])[0]].g; go('game'); });

screen('settings', {
  title: 'Configurações', lote: 'Lote 1 · Configurações e drivers', secKey: 'settings', globalScope: true,
  info: {
    what: 'Uma tela só para tudo que vale em todos os jogos: um resumo do que está fora do padrão, os mesmos grupos de ajustes da ficha (agora no escopo global) e as opções do próprio app.',
    replaces: 'SettingsScreen.kt: lista de categorias com contagem, botão Jogador/Desenvolvedor no topo e, no rodapé, botões dos menus, tamanho, idioma, pacote de dados e canal de atualização.',
    changes: ['Resumo com o que mudou e os jogos com ajustes próprios', 'Cada ajuste global diz quantos jogos usam outro valor', 'Modo controle automático e níveis Essencial/Avançado/Tudo', 'Busca por nome, descrição ou chave do TOML em todos os grupos'],
    c: 'Menu vertical com os mesmos grupos; ajustes em linhas ◀ ▶, LB/RB troca de grupo.',
    code: 'ui/settings/SettingsScreen.kt, SettingRows.kt, AppearanceRows.kt, DataBundleSection.kt, UpdateChannelSection.kt, CommunityConfigsSection.kt',
  },
  render() {
    const secs = [
      { group: 'Geral', id: 'resumo', t: 'Resumo', icon: 'home', title: 'Resumo', body: resumoHTML },
      ...GROUPS.map(([id, t, i]) => ({ group: 'Emulação', id: 'set:' + id, t, icon: i, n: globalGroupChanged(id) || '', body: v => settingsPanel(null, v, id) })),
      { group: 'App', id: 'app:ui', t: 'Interface', icon: 'monitor', title: 'Interface', lead: 'Valem para o app inteiro, não para os jogos.', body: v => `<div data-note="appsection">${appRows(APP_UI, v)}</div>` },
      { id: 'app:lang', t: 'Idioma', icon: 'globe', body: v => appRows(['@app.lang'], v) + `<p class="note" style="margin-top:10px">O idioma e o país que os jogos veem ficam em <button class="link" data-act="sec" data-key="settings" data-v="set:sys" data-k="lg-sys">Console e sistema</button>.</p>` },
      { id: 'app:data', t: 'Dados e backup', icon: 'save', body: bundleHTML },
      { id: 'app:upd', t: 'Atualizações', icon: 'download', body: updatesHTML },
      { id: 'app:comm', t: 'Comunidade', icon: 'share', body: communityHTML },
      { id: 'app:diag', t: 'Diagnóstico e testes', icon: 'bug', body: diagHTML },
      { id: 'app:about', t: 'Sobre', icon: 'info', body: () => `<div class="card">${drow({ icon: 'info', t: 'XenDroid v412 · 7bb3409', s: 'POCO F7 · Adreno 825 · Android 15', acts: `<button class="btn sm" data-act="go" data-v="about" data-k="ab-open">Abrir Sobre</button>` })}</div>` },
    ];
    return sectioned({ key: 'settings', title: 'Configurações', sub: 'Valem para todos os jogos; cada jogo pode mudar na própria ficha', csub: 'Todos os jogos', plainSub: true, icon: 'gear', sections: secs });
  },
});

/* ---------- Drivers ---------- */
const DL = { id: null, p: 0, timer: 0 };
const TU_FLAGS = [
  ['sysmem', 'Desenha direto na memória em vez dos tiles do chip (GMEM). Mais lento, mas evita uma classe de travamentos da GPU Adreno.'],
  ['gmem', 'Sempre desenha nos tiles do chip. Mais rápido onde funciona; o oposto de sysmem.'],
  ['nolrz', 'Desliga o LRZ, o teste de profundidade antecipado. Pode corrigir cintilação ou geometria sumindo; custa velocidade.'],
  ['nolrzfc', 'Desliga só a limpeza rápida do LRZ. Uma versão mais estreita do nolrz.'],
  ['noubwc', 'Desliga a compressão de imagem UBWC. Pode corrigir texturas ou render targets corrompidos; mais tráfego de memória.'],
  ['nomultipos', 'Desliga a otimização de saída multiposição. Pode corrigir polígonos esticados ou piscando.'],
  ['noconcurrentresolves', 'Não resolve tiles enquanto os próximos desenham. Pode corrigir artefatos na Adreno 7xx; mais lento.'],
  ['noconcurrentunresolves', 'Não carrega tiles enquanto outros desenham. Pode corrigir artefatos na Adreno 7xx; mais lento.'],
  ['forcebin', 'Sempre usa a passada de binning no modo por tiles. Para diagnóstico.'],
  ['flushall', 'Esvazia a GPU depois de cada comando. Muito lento; ajuda a achar qual desenho trava a GPU.'],
  ['syncdraw', 'Espera cada desenho terminar. Muito lento; para diagnosticar travamentos da GPU.'],
];
const drvName = id => { const d = DRIVERS.installed.find(x => x.id === id); return id === '' ? 'Driver do sistema (Qualcomm 819.0)' : d ? d.name : id; };
const stateBadge = s => s === 'verified' ? `<span class="badge ok" data-note="drvsha">${ic('check', 12)} SHA-256 conferido</span>` : s === 'unverified' ? '<span class="badge warn">Sem checksum publicado</span>' : '<span class="badge err">Danificado ou ABI errada</span>';
function drvNowHTML() {
  const cur = globalOf('Vulkan.vulkan_lib_path'), inst = DRIVERS.installed.find(x => x.id === cur), sug = DRIVERS.available.find(x => x.suggested);
  const games = gamesOverriding('Vulkan.vulkan_lib_path');
  const prev = DRIVERS.previous;
  return `<div class="grid2">
    <section class="card span2" data-note="drvnow"><h3>${ic('chip', 15)} Escolhido para todos os jogos</h3>
      <div class="row" style="gap:12px"><b style="font:700 22px/1.1 var(--f-disp)">${esc(drvName(cur))}</b>${inst ? stateBadge(inst.state) : '<span class="badge">do sistema</span>'}</div>
      <dl class="kv"><dt>Escolhido em</dt><dd>${DRIVERS.selectedAt}</dd><dt>Última sessão carregou</dt><dd>${esc(DRIVERS.lastRun.drv)} · ${esc(DRIVERS.lastRun.game)}, ${esc(DRIVERS.lastRun.when)}</dd></dl>
      ${DRIVERS.lastRun.drv === drvName(cur) ? `<p class="okline">${ic('checkC', 15)} A última sessão rodou no driver escolhido.</p>` : `<p class="warnline">${ic('warn', 15)} A última sessão rodou em outro driver: o escolhido não carregou ou foi trocado depois.</p>`}
      <div class="row">${prev ? `<button class="btn sm" data-act="drv-use" data-v="${prev}" data-k="dn-prev" data-note="drvprev">${ic('reset', 15)} Voltar ao anterior: ${esc(drvName(prev))}</button>` : ''}<button class="btn sm ghost" data-act="drv-use" data-v="" data-k="dn-sys">Usar o driver do sistema</button></div></section>
    ${sug ? `<section class="card span2" data-note="drvsug"><h3>${ic('spark', 15)} Sugerido para ${DRIVERS.gpu}</h3><div class="row"><b style="font-size:15px">${esc(sug.name)}</b><span class="badge">${sug.date}</span>${sug.sha ? '<span class="badge ok">SHA-256 publicado</span>' : ''}</div><p class="note">Escolhido pelos nomes dos arquivos, o mais novo primeiro; não é garantia.</p><div class="row"><button class="btn sm" data-act="drv-dl" data-v="${sug.id}" data-k="dn-sug">${ic('download', 15)} Baixar e instalar</button></div></section>` : ''}
    <section class="card span2" data-note="drvgames"><h3>${ic('gamepad', 15)} Jogos com driver próprio<span class="r">${games.length}</span></h3>
      <div class="list">${games.length ? games.map(g => drow({ img: `<span style="width:34px;display:block">${coverHTML(g)}</span>`, t: esc(g.name), s: esc(drvName(OV[g.id]['Vulkan.vulkan_lib_path'])), acts: `<button class="btn sm ghost" data-act="ovopen-g" data-gid="${g.id}" data-k="dg-${g.id}">Ajustes do jogo</button>` })).join('') : '<p class="note">Nenhum: todos usam o driver acima.</p>'}</div></section>
  </div>`;
}
function drvInstalledHTML() {
  const cur = globalOf('Vulkan.vulkan_lib_path');
  return `<div class="row" style="margin-bottom:10px"><button class="btn sm" data-act="toast" data-msg="Escolha o ZIP do driver (seletor do Android)." data-k="di-zip">${ic('upload', 15)} Importar ZIP</button></div>
    <div class="card">${drow({ icon: 'cpu', t: `Driver do sistema${cur === '' ? ' <span class="badge acc">Em uso</span>' : ''}`, s: 'Qualcomm 819.0 · sempre disponível', acts: cur === '' ? '' : `<button class="btn sm" data-act="drv-use" data-v="" data-k="di-sys">Usar</button>` })}
    ${DRIVERS.installed.map(d => drow({ icon: 'chip', t: `${esc(d.name)} ${cur === d.id ? '<span class="badge acc">Em uso</span>' : ''} ${stateBadge(d.state)}`, s: `${esc(d.from)} · ${d.size} · ${d.date}`, acts: d.state === 'damaged' ? `<button class="btn sm" data-act="toast" data-msg="Reinstalando ${esc(d.name)}… (protótipo)" data-k="di-re-${d.id}">Reinstalar</button><button class="btn sm ghost" data-act="drv-rm" data-v="${d.id}" data-k="di-rm-${d.id}">Remover</button>` : cur === d.id ? '' : `<button class="btn sm" data-act="drv-use" data-v="${d.id}" data-k="di-use-${d.id}">Usar</button><button class="btn sm ghost" data-act="drv-rm" data-v="${d.id}" data-k="di-rm-${d.id}">Remover</button>` })).join('')}</div>
    <p class="note" style="margin-top:10px">Trocar vale na próxima abertura de um jogo. Os binários ficam instalados; voltar à escolha anterior não baixa de novo.</p>`;
}
function drvAvailHTML() {
  return `<div class="row" style="margin-bottom:10px"><button class="btn sm ghost" data-act="toast" data-msg="Lista atualizada das fontes (protótipo)." data-k="da-ref">${ic('refresh', 15)} Atualizar lista</button><span class="note">De ${DRIVERS.sources.length} ${DRIVERS.sources.length === 1 ? 'fonte' : 'fontes'}: ${DRIVERS.sources.map(esc).join(', ')}</span></div>
    <div class="card">${DRIVERS.available.length ? DRIVERS.available.map(d => drow({ icon: 'download', t: `${esc(d.name)} ${d.suggested ? `<span class="badge acc">Sugerido para ${DRIVERS.gpu}</span>` : ''} ${d.sha ? '<span class="badge ok">SHA-256 publicado</span>' : '<span class="badge warn">Sem checksum publicado</span>'}`, s: DL.id === d.id ? `<span class="bar" style="display:block;margin-top:6px"><i id="dl-bar" style="width:${DL.p}%"></i></span><span id="dl-txt">${DL.p}% · baixando</span>` : `De ${esc(d.from)} · ${d.size} · ${d.date}`, acts: DL.id === d.id ? `<button class="btn sm ghost" data-act="drv-cancel" data-k="da-x">Cancelar</button>` : `<button class="btn sm" data-act="drv-dl" data-v="${d.id}" data-k="da-${d.id}"${DL.id ? ' disabled' : ''}>Baixar e instalar</button>` })).join('') : '<p class="note">Tudo das fontes já está instalado.</p>'}</div>
    <p class="note" style="margin-top:10px">Todo download é conferido: biblioteca Vulkan arm64 e o SHA-256 quando o release publica um. Sem checksum, a instalação avisa e pede confirmação.</p>`;
}
function drvSourcesHTML() {
  return `<div class="card" data-note="drvsrc">${DRIVERS.sources.map((s, i) => drow({ icon: 'cloud', t: esc(s), s: s === 'K11MCH1/AdrenoToolsDrivers' ? 'Fonte de sempre' : 'Adicionada por você', acts: DRIVERS.sources.length > 1 ? `<button class="btn sm ghost" data-act="src-rm" data-v="${i}" data-k="sr-${i}">Remover</button>` : '' })).join('')}</div>
    <div class="row" style="margin-top:10px"><input class="txt" id="src-in" type="text" placeholder="dono/repositório ou um link do GitHub" data-k="src-in" autocomplete="off" spellcheck="false" style="width:280px"><button class="btn sm" data-act="src-add" data-k="src-add">${ic('plus', 15)} Adicionar</button><button class="btn sm ghost" data-act="src-def" data-k="src-def">Voltar a usar a fonte de sempre</button></div>
    <p class="note" style="margin-top:10px">Repositórios do GitHub cujos releases trazem pacotes de driver (.zip). Até 8 fontes.</p>`;
}
function tuFlags() { return String(globalOf('Vulkan.turnip_debug') || '').split(',').filter(Boolean); }
function drvTurnipHTML(v) {
  const on = tuFlags();
  return `<p class="note" style="margin-bottom:10px">Flags passadas ao driver Turnip (TU_DEBUG) quando o próximo jogo iniciar. Drivers proprietários as ignoram. Valem para todos os jogos; um jogo pode ter as próprias na ficha.</p>
    <div class="card" data-note="tuflags">${TU_FLAGS.map(([f, h]) => drow({ lead: `<span class="mono" style="font-size:10px">TU</span>`, t: `<span class="mono">${f}</span>`, s: esc(h), acts: `<button class="tg" role="switch" aria-checked="${on.includes(f)}" aria-label="${f}" data-act="tu" data-v="${f}" data-k="tu-${f}"></button>` })).join('')}</div>
    <p class="note" style="margin-top:10px">Resultado: <code class="keyc" style="font-size:12px">TU_DEBUG=${esc(on.join(',') || '(nenhuma)')}</code></p>`;
}
action('drv-use', el => { const v = el.dataset.v, cur = globalOf('Vulkan.vulkan_lib_path'); if (v === cur) return; DRIVERS.previous = cur; DRIVERS.selectedAt = 'agora'; setVal(null, 'Vulkan.vulkan_lib_path', v); toast(`${drvName(v)} · próxima abertura`); });
action('drv-rm', el => { const i = DRIVERS.installed.findIndex(d => d.id === el.dataset.v); if (i >= 0) { const d = DRIVERS.installed.splice(i, 1)[0]; toast(`${d.name} removido`); } });
action('drv-dl', el => {
  if (DL.id) return; const d = DRIVERS.available.find(x => x.id === el.dataset.v); if (!d) return;
  DL.id = d.id; DL.p = 0; if (S.route.name === 'drivers') S.sec.drivers = 'drv:avail'; render();
  DL.timer = setInterval(() => {
    DL.p = Math.min(100, DL.p + 7);
    const bar = document.getElementById('dl-bar'), txt = document.getElementById('dl-txt');
    if (bar) bar.style.width = DL.p + '%'; if (txt) txt.textContent = DL.p < 100 ? `${DL.p}% · baixando` : 'Conferindo o SHA-256…';
    if (DL.p >= 100) { clearInterval(DL.timer); setTimeout(() => { DRIVERS.available = DRIVERS.available.filter(x => x.id !== d.id); DRIVERS.installed.unshift({ id: d.id, name: d.name, state: d.sha ? 'verified' : 'unverified', from: d.from, size: d.size, date: 'hoje' }); DEF['Vulkan.vulkan_lib_path'].o.push([d.id, d.name]); DL.id = null; toast(d.sha ? 'Driver instalado · SHA-256 conferido' : 'Driver instalado · sem checksum publicado'); }, 600); }
  }, 140);
});
action('drv-cancel', () => { clearInterval(DL.timer); DL.id = null; toast('Download cancelado'); });
action('src-add', () => {
  const inp = document.getElementById('src-in'); const raw = (inp && inp.value || '').trim();
  const m = /^(?:https?:\/\/github\.com\/)?([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+?)(?:\.git)?\/?$/.exec(raw);
  if (!m) { toast('Não é um repositório do GitHub.'); return; }
  const id = m[1] + '/' + m[2];
  if (DRIVERS.sources.includes(id)) { toast('Já está na lista.'); return; }
  if (DRIVERS.sources.length >= 8) { toast('Limite de fontes atingido (8).'); return; }
  DRIVERS.sources.push(id); toast(`${id} adicionada`);
});
action('src-rm', el => { DRIVERS.sources.splice(Number(el.dataset.v), 1); render(); });
action('src-def', () => { DRIVERS.sources = ['K11MCH1/AdrenoToolsDrivers']; toast('Voltou à fonte de sempre'); });
action('tu', el => { let on = tuFlags(); const f = el.dataset.v; if (on.includes(f)) on = on.filter(x => x !== f); else { on.push(f); if (f === 'sysmem') on = on.filter(x => x !== 'gmem'); if (f === 'gmem') on = on.filter(x => x !== 'sysmem'); } const v = TU_FLAGS.map(x => x[0]).filter(x => on.includes(x)).join(','); if (v === DEF['Vulkan.turnip_debug'].def) delete GLOBAL['Vulkan.turnip_debug']; else GLOBAL['Vulkan.turnip_debug'] = v; render(); });

screen('drivers', {
  title: 'Drivers', lote: 'Lote 1 · Configurações e drivers', secKey: 'drivers',
  info: {
    what: 'O gerenciador de drivers vira uma área própria: o que está escolhido e o que a última sessão realmente carregou, a sugestão para a GPU, os instalados, os disponíveis nas fontes, as fontes e as flags do Turnip.',
    replaces: 'O diálogo “Gerenciar drivers” aberto pela linha Driver Vulkan personalizado (SettingRows.kt), com o diálogo de fontes; as flags do TU_DEBUG ficam em outra linha das Configurações.',
    changes: ['Escolhido e carregado lado a lado, com aviso quando diferem', 'Jogos com driver próprio listados, com atalho', 'Instalar mostra progresso e o resultado da conferência do SHA-256', 'Flags do Turnip na mesma área do driver'],
    c: 'Mesmas seções no menu vertical; A usa ou baixa o driver em foco.',
    code: 'ui/settings/SettingRows.kt (DriverActionRow, DriverSourcesDialog), driver/DriverRepository.kt, DriverSources.kt, DriverSuggestion.kt, settings/TurnipFlags.kt',
  },
  render() {
    const secs = [
      { group: 'Driver', id: 'drv:now', t: 'Em uso', icon: 'chip', body: drvNowHTML },
      { id: 'drv:inst', t: 'Instalados', icon: 'check', n: DRIVERS.installed.length + 1, body: drvInstalledHTML },
      { id: 'drv:avail', t: 'Para baixar', icon: 'download', n: DRIVERS.available.length || '', body: drvAvailHTML },
      { group: 'Avançado', id: 'drv:src', t: 'Fontes', icon: 'cloud', n: DRIVERS.sources.length, body: drvSourcesHTML },
      { id: 'drv:tu', t: 'Opções do Turnip', icon: 'flask', body: drvTurnipHTML },
    ];
    return sectioned({ key: 'drivers', title: 'Drivers de GPU', sub: `${DRIVERS.gpu} · driver do sistema ${DRIVERS.system}`, csub: DRIVERS.gpu, plainSub: true, icon: 'chip', sections: secs });
  },
});
