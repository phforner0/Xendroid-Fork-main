/* Configurações globais e Drivers (SettingsScreen.kt, XdDataCards.kt, DriversScreen.kt). */
'use strict';

Object.assign(NOTES, {
  resumo: ['app', 'Resumo: o que está fora do padrão em todos os jogos e quais jogos têm ajustes próprios, com volta ao padrão a um toque. A busca daqui procura em todos os grupos.'],
  ovgames: ['app', 'Cada ajuste global diz quantos jogos usam outro valor; tocar lista esses jogos e abre os ajustes deles.'],
  appsection: ['app', 'Opções do próprio app: modo da interface, ajustes mostrados, botões nos menus, sugestões ao fim da sessão e tamanho da interface e do texto. Ficam guardadas pelo app, fora do TOML.'],
  bundle: ['app', 'O pacote de dados leva os ajustes globais e por jogo, os controles de toque, favoritos, coleções e notas de compatibilidade. Antes de importar, o app mostra o que muda e guarda um backup dos ajustes atuais.'],
  userdata: ['app', 'A pasta de dados abre no gerenciador de arquivos do Android, somente leitura enquanto um jogo roda.'],
  updchan: ['dif', 'O atualizador procura releases no repositório phforner0/Xendroid-Fork-main, que é o padrão do build; as releases publicadas estão em phforner0/Xendroid-Plus. Não foi possível confirmar se o nome antigo redireciona.'],
  drvnow: ['app', 'O driver escolhido para todos os jogos e o que a última sessão realmente carregou, com o aviso quando os dois não batem.'],
  drvsug: ['app', 'A sugestão vem dos nomes dos arquivos das fontes, pela família da Adreno, a mais nova primeiro; não é garantia. Só aparece em GPU Adreno.'],
  drvsha: ['app', 'Todo download é conferido: biblioteca Vulkan arm64 e o SHA-256 quando o release publica um.'],
  drvsrc: ['app', 'Fontes são repositórios do GitHub cujos releases trazem pacotes .zip; até 8. A de sempre vem do código do app.'],
  tuflags: ['app', 'Cada flag do TU_DEBUG com a explicação do app. sysmem e gmem se excluem; flags que esta versão não descreve ficam como estão.'],
  drvgames: ['app', 'Jogos que escolheram outro driver, com atalho para os ajustes de cada um.'],
  drvprev: ['app', 'Voltar à escolha anterior não baixa nada de novo: os pacotes instalados ficam no aparelho.'],
  drvex: ['sim', 'Os pacotes de driver, a GPU e as datas são exemplos; a fonte de drivers é a padrão do app.'],
});

/* ---------- Configurações ---------- */
const APP_UI = ['@app.mode', '@app.level', '@app.confirm', '@app.advice', '@app.uisize', '@app.textsize'];
function resumoHTML(v) {
  const changed = Object.keys(GLOBAL).filter(k => DEF[k] && !appOnly(k));
  const games = GAMES.filter(g => ovCount(g));
  return `<div class="grid2">
    <section class="card span2" data-note="resumo"><h3>${ic('sliders', 15)} Fora do padrão em todos os jogos<span class="r">${changed.length}</span></h3>
      <div class="list">${changed.length ? changed.map(k => { const d = DEF[k]; return drow({ icon: 'globe', t: esc(d.t), s: `${esc(label(d, GLOBAL[k]))} · padrão ${esc(label(d, d.def))}`, acts: `<button class="btn sm ghost" data-act="reset" data-key="${k}" data-k="rz-${k}">${ic('reset', 14)} Padrão</button>` }); }).join('') : '<p class="note">Tudo no padrão.</p>'}</div>
      <div class="row"><button class="btn sm" data-act="modal" data-v="toml-global" data-k="rz-toml">${ic('code', 15)} TOML global</button></div></section>
    <section class="card span2"><h3>${ic('gamepad', 15)} Jogos com ajustes próprios<span class="r">${games.length}</span></h3>
      <div class="list">${games.length ? games.map(g => drow({ img: `<span style="width:34px;display:block">${coverHTML(g)}</span>`, t: esc(g.name), s: Object.keys(OV[g.id]).map(k => esc(DEF[k].t)).join(' · '), acts: `<button class="btn sm ghost" data-act="ovopen-g" data-gid="${g.id}" data-k="rg-${g.id}">Ajustes do jogo</button>` })).join('') : '<p class="note">Nenhum jogo muda um ajuste próprio.</p>'}</div></section>
    <section class="card"><h3>${ic('monitor', 15)} Interface</h3>${appRows(['@app.mode', '@app.level'], v)}</section>
    <section class="card"><h3>${ic('chip', 15)} Driver</h3><dl class="kv"><dt>Todos os jogos</dt><dd>${esc(label(DEF['Vulkan.vulkan_lib_path'], globalOf('Vulkan.vulkan_lib_path')))}</dd><dt>Última sessão carregou</dt><dd>${esc(DRIVERS.lastRun.drv)}</dd></dl><div class="row"><button class="btn sm" data-act="go" data-v="drivers" data-k="rz-drv">Gerenciar drivers</button></div></section>
  </div>`;
}
function bundleHTML() {
  return `<div class="stack">
    <section class="card" data-note="bundle"><h3>${ic('save', 15)} Fazer backup ou levar os ajustes</h3>
      <p class="note">Ajustes do emulador (globais e por jogo), controles de toque, favoritos, coleções e suas notas de compatibilidade, num arquivo só. Saves, perfis, jogos e drivers não entram.</p>
      <div class="row"><button class="btn sm" data-act="toast" data-msg="No app, grava xendroid-settings-<data>.zip onde você escolher." data-k="bd-exp">${ic('upload', 15)} Exportar</button><button class="btn sm ghost" data-act="modal" data-v="bundle-import" data-k="bd-imp">${ic('download', 15)} Importar…</button></div></section>
    <section class="card" data-note="userdata"><h3>${ic('folder', 15)} Pasta de dados</h3>
      <div class="list">
        ${drow({ icon: 'gamepad', t: 'Jogos por título', s: 'Patches, configs por jogo e caches' })}
        ${drow({ icon: 'user', t: 'Saves e dados dos perfis', s: 'Cada perfil na própria pasta' })}
        ${drow({ icon: 'box', t: 'DLC e atualizações de título', s: 'Pacotes instalados' })}
      </div>
      <p class="note">Somente leitura enquanto um jogo roda ou dados estão sendo alterados</p>
      <div class="row"><button class="btn sm" data-act="toast" data-msg="No app, abre a pasta no gerenciador de arquivos do Android." data-k="ud-open">${ic('folder', 15)} Abrir no gerenciador de arquivos</button></div></section>
  </div>`;
}
const appVersion = () => (XDR.versoes.estavel ? XDR.versoes.estavel.commit : 'local');
function updatesHTML(v) {
  return `<div data-note="updchan">${appRows(['@app.updates'], v)}</div><section class="card" style="margin-top:10px"><h3>${ic('download', 15)} Versão</h3><dl class="kv"><dt>Instalada</dt><dd>${esc(appVersion())}</dd><dt>Última procura</dt><dd>nunca</dd></dl><div class="row"><button class="btn sm" data-act="go" data-v="update" data-k="up-check">${ic('refresh', 15)} Procurar atualizações</button></div></section>`;
}
function communityHTML() {
  return `<section class="card" data-note="presets-off"><h3>${ic('share', 15)} Ajustes da comunidade</h3>
    <p style="font-size:13.5px">Desligado nesta build: nenhum servidor configurado.</p>
    <p class="note">Com um servidor, cada ficha busca pelo Title ID os ajustes que outros jogadores compartilharam, o aparelho mais parecido com o seu primeiro, com votos de “ajudou” e “não ajudou”. Aplicar passa pela mesma prévia das predefinições e dá para desfazer.</p>
    <p class="note">Ao compartilhar vão só o modelo do telefone, a build e os ajustes do jogo: nenhuma conta, gamertag, nome de arquivo ou caminho.</p></section>`;
}
function diagHTML() {
  return `<div class="card">
    ${drow({ icon: 'bug', t: 'Diagnóstico', s: 'Sessões guardadas; compartilhe uma ou todas, com dados pessoais removidos', acts: `<button class="btn sm" data-act="go" data-v="diagnostics" data-k="dg-open">Abrir</button>` })}
    ${drow({ icon: 'ab', t: 'Comparar execuções', s: 'A/B com uma mudança só (driver, limite, geração de quadros)', acts: `<button class="btn sm ghost" data-act="go" data-v="compare" data-k="dg-cmp">Abrir</button>` })}
    ${drow({ icon: 'gamepad', t: 'Teste de controle', s: 'Botões, analógicos, giroscópio e vibração', acts: `<button class="btn sm ghost" data-act="go" data-v="padtest" data-k="dg-pad">Abrir</button>` })}
    ${drow({ icon: 'download', t: 'Exportar logs da sessão para Downloads', s: 'Logs brutos (sem ocultar dados) · sessões guardadas + execução atual', acts: `<button class="btn sm ghost" data-act="toast" data-msg="No app, grava xendroid-logs-<data>.zip em Downloads, sem ocultar dados." data-k="dg-logs">Exportar</button>` })}
    ${drow({ icon: 'sliders', t: 'Nível e filtro do log', s: 'Ficam em Depuração', acts: `<button class="btn sm ghost" data-act="sec" data-key="settings" data-v="set:dbg" data-k="dg-dbg">Abrir</button>` })}
  </div>`;
}
modal('bundle-import', () => ({ html: sheetHead('Importar estes ajustes?', 'xendroid-settings-20260930-2110.zip (exemplo)') + `<div class="list">
    ${drow({ icon: 'sliders', t: 'Ajustes do emulador', s: 'Substituídos (4 linhas diferentes)' })}
    ${drow({ icon: 'gamepad', t: 'Ajustes por jogo', s: 'Novos: 2, substituídos: 1, iguais: 3, mantidos: 1' })}
    ${drow({ icon: 'hand', t: 'Controles de toque', s: 'Iguais aos atuais' })}
    ${drow({ icon: 'star2', t: 'Favoritos', s: '2 adicionados, nenhum removido' })}
    ${drow({ icon: 'layers', t: 'Coleções', s: 'Novas: 1, jogos adicionados: 3, nada removido' })}
    ${drow({ icon: 'shield', t: 'Resultados de compatibilidade', s: '4 adicionados' })}
    ${drow({ icon: 'lock', t: 'Ficam deste aparelho', s: 'Pastas de armazenamento, driver personalizado, saves, perfis, jogos e histórico de jogo' })}
  </div><p class="note">Seus ajustes atuais são salvos antes, então dá para desfazer importando esse backup.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="m-cancel">Cancelar</button><button class="btn primary" data-act="toast" data-msg="No app, os ajustes são importados e o backup dos atuais fica guardado (até 5)." data-k="m-imp" data-autofocus>Importar</button></div>` }));
action('ovopen-g', el => { S.gameId = el.dataset.gid; S.sec.game = isC() ? 'set' : 'set:' + DEF[Object.keys(OV[el.dataset.gid])[0]].g; go('game'); });

screen('settings', {
  title: 'Configurações', secKey: 'settings', globalScope: true,
  render() {
    const secs = [
      { group: 'Geral', id: 'resumo', t: 'Resumo', icon: 'home', title: 'Resumo', body: resumoHTML },
      ...GROUPS.map(([id, t, i]) => ({ group: 'Emulação', id: 'set:' + id, t, icon: i, n: globalGroupChanged(id) || '', body: v => settingsPanel(null, v, id) })),
      { group: 'App', id: 'app:ui', t: 'Interface', icon: 'monitor', title: 'Interface', lead: 'Valem para o app inteiro, não para os jogos.', body: v => `<div data-note="appsection">${appRows(APP_UI, v)}</div>` },
      { id: 'app:lang', t: 'Idioma', icon: 'globe', body: v => appRows(['@app.lang'], v) + `<p class="note" style="margin-top:10px"><button class="link" data-act="sec" data-key="settings" data-v="set:sys" data-k="lg-sys">O idioma e o país que os jogos veem ficam em Console e sistema</button></p>` },
      { id: 'app:data', t: 'Dados e backup', icon: 'save', body: bundleHTML },
      { id: 'app:upd', t: 'Atualizações', icon: 'download', body: updatesHTML },
      { id: 'app:comm', t: 'Comunidade', icon: 'share', body: communityHTML },
      { id: 'app:diag', t: 'Diagnóstico e testes', icon: 'bug', body: diagHTML },
      { id: 'app:about', t: 'Sobre', icon: 'info', body: () => `<div class="card">${drow({ icon: 'info', t: `Xendroid+ ${esc(appVersion())}`, s: esc(DRIVERS.gpu), acts: `<button class="btn sm" data-act="go" data-v="about" data-k="ab-open">Abrir Sobre</button>` })}</div>` },
    ];
    return sectioned({ key: 'settings', title: 'Configurações', sub: 'Valem para todos os jogos; cada jogo pode mudar na própria ficha', csub: 'Todos os jogos', plainSub: true, icon: 'gear', sections: secs });
  },
});

/* ---------- Drivers ---------- */
const DL = { id: null, p: 0, timer: 0 };
const drvName = id => { const d = DRIVERS.installed.find(x => x.id === id); return id === '' ? 'Driver do sistema' : d ? d.name : id; };
const checkedBadge = d => d.checked ? `<span class="badge ok">${ic('check', 12)} arquivos conferidos</span>` : '<span class="badge warn">importação antiga</span>';
function drvNowHTML() {
  const cur = globalOf('Vulkan.vulkan_lib_path'), inst = DRIVERS.installed.find(x => x.id === cur), sug = DRIVERS.available.find(x => x.suggested);
  const games = gamesOverriding('Vulkan.vulkan_lib_path');
  const prev = DRIVERS.previous;
  const ran = DRIVERS.lastRun.drv === drvName(cur);
  return `<div class="grid2">
    <section class="card span2" data-note="drvnow"><h3>${ic('chip', 15)} Escolhido para todos os jogos</h3>
      <div class="row" style="gap:12px"><b style="font:700 22px/1.1 var(--f-disp)">${esc(drvName(cur))}</b>${cur === '' ? '<span class="badge">do sistema</span>' : inst ? checkedBadge(inst) : '<span class="badge err">não instalado</span>'}</div>
      <dl class="kv"><dt>Escolhido em</dt><dd>${DRIVERS.selectedAt}</dd><dt>Última sessão carregou</dt><dd>${esc(DRIVERS.lastRun.drv)}</dd></dl>
      ${ran ? `<p class="okline">${ic('checkC', 15)} A última sessão rodou no driver escolhido.</p>` : `<p class="note">${ic('info', 14)} Nenhum jogo rodou com esta escolha ainda (o último usou ${esc(DRIVERS.lastRun.drv)}).</p>`}
      <div class="row">${prev !== cur && prev != null ? `<button class="btn sm" data-act="drv-use" data-v="${prev}" data-k="dn-prev" data-note="drvprev">${ic('reset', 15)} Voltar ao anterior: ${esc(drvName(prev))}</button>` : ''}${cur !== '' ? '<button class="btn sm ghost" data-act="drv-use" data-v="" data-k="dn-sys">Usar o driver do sistema</button>' : ''}</div></section>
    ${sug ? `<section class="card span2" data-note="drvsug"><h3>${ic('spark', 15)} Sugerido para Adreno ${DRIVERS.model}</h3><div class="row"><b style="font-size:15px">${esc(sug.name)}</b>${sug.sha ? '<span class="badge ok">SHA-256 publicado</span>' : ''}</div><p class="note">Escolhido pelos nomes dos arquivos, o mais novo primeiro; não é garantia.</p><div class="row"><button class="btn sm" data-act="drv-dl" data-v="${sug.id}" data-k="dn-sug">${ic('download', 15)} Baixar e instalar</button></div></section>` : ''}
    <section class="card span2" data-note="drvgames"><h3>${ic('gamepad', 15)} Jogos com driver próprio<span class="r">${games.length}</span></h3>
      <div class="list">${games.length ? games.map(g => drow({ img: `<span style="width:34px;display:block">${coverHTML(g)}</span>`, t: esc(g.name), s: esc(drvName(OV[g.id]['Vulkan.vulkan_lib_path'])), acts: `<button class="btn sm ghost" data-act="ovopen-g" data-gid="${g.id}" data-k="dg-${g.id}">Ajustes do jogo</button>` })).join('') : '<p class="note">Nenhum: todos usam o driver acima.</p>'}</div></section>
  </div>`;
}
function drvInstalledHTML() {
  const cur = globalOf('Vulkan.vulkan_lib_path');
  const byGames = id => GAMES.some(g => OV[g.id] && OV[g.id]['Vulkan.vulkan_lib_path'] === id);
  return `<div class="row" style="margin-bottom:10px"><button class="btn sm" data-act="toast" data-msg="No app, abre o seletor de arquivos do Android para escolher o ZIP do driver." data-k="di-zip">${ic('upload', 15)} Importar ZIP</button></div>
    <div class="card" data-note="drvex">${drow({ icon: 'cpu', t: `Driver do sistema${cur === '' ? ' <span class="badge acc">Em uso</span>' : ''}`, s: 'Sempre disponível', acts: cur === '' ? '' : `<button class="btn sm" data-act="drv-use" data-v="" data-k="di-sys">Usar</button>` })}
    ${DRIVERS.installed.map(d => drow({ icon: 'chip', t: `${esc(d.name)} ${cur === d.id ? '<span class="badge acc">Em uso</span>' : ''} ${checkedBadge(d)}${byGames(d.id) ? ' <span class="badge">jogos usam</span>' : ''}`, s: `${d.size} · ${d.date}`, acts: cur === d.id ? '' : `<button class="btn sm" data-act="drv-use" data-v="${d.id}" data-k="di-use-${d.id}">Usar</button><button class="btn sm ghost" data-act="drv-rm" data-v="${d.id}" data-k="di-rm-${d.id}">Remover</button>` })).join('')}</div>
    <p class="note" style="margin-top:10px">Trocar vale na próxima abertura de um jogo. Os binários ficam instalados; voltar à escolha anterior não baixa de novo.</p>`;
}
function drvAvailHTML() {
  const n = DRIVERS.sources.length;
  const list = DRIVERS.available.slice().sort((a, b) => (b.suggested ? 1 : 0) - (a.suggested ? 1 : 0));
  return `<div class="row" style="margin-bottom:10px"><button class="btn sm ghost" data-act="toast" data-msg="No app, lê de novo os releases das fontes no GitHub." data-k="da-ref">${ic('refresh', 15)} Atualizar lista</button><span class="note">De ${n} ${n === 1 ? 'fonte' : 'fontes'}: ${DRIVERS.sources.map(esc).join(', ')}</span></div>
    <div class="card" data-note="drvsha">${list.length ? list.map(d => drow({ icon: 'download', t: `${esc(d.name)} ${d.suggested ? `<span class="badge acc">Sugerido para Adreno ${DRIVERS.model}</span>` : ''} ${d.sha ? '<span class="badge ok">SHA-256 publicado</span>' : '<span class="badge warn">Sem checksum publicado</span>'}`, s: DL.id === d.id ? `<span class="bar" style="display:block;margin-top:6px"><i id="dl-bar" style="width:${DL.p}%"></i></span><span id="dl-txt">${DL.p}%</span>` : [n > 1 ? `De ${esc(d.from)}` : '', d.date].filter(Boolean).join(' · '), acts: DL.id === d.id ? '' : `<button class="btn sm" data-act="drv-dl" data-v="${d.id}" data-k="da-${d.id}"${DL.id ? ' disabled' : ''}>Baixar e instalar</button>` })).join('') : '<p class="note">Nenhum driver disponível.</p>'}</div>
    <p class="note" style="margin-top:10px">Todo download é conferido: biblioteca Vulkan arm64 e o SHA-256 quando o release publica um.</p>`;
}
function drvSourcesHTML() {
  return `<div class="card" data-note="drvsrc">${DRIVERS.sources.length ? DRIVERS.sources.map((s, i) => drow({ icon: 'cloud', t: esc(s), s: s.toLowerCase() === XDR.fonteDrivers.toLowerCase() ? 'Fonte de sempre' : 'Adicionada por você', acts: `<button class="btn sm ghost" data-act="src-rm" data-v="${i}" data-k="sr-${i}">Remover</button>` })).join('') : '<p class="note">Nenhuma fonte: adicione uma para listar drivers.</p>'}</div>
    <div class="row" style="margin-top:10px"><input class="txt" id="src-in" type="text" placeholder="dono/repositório ou um link do GitHub" data-k="src-in" autocomplete="off" spellcheck="false" style="width:280px" aria-label="Nova fonte de drivers"><button class="btn sm" data-act="src-add" data-k="src-add">${ic('plus', 15)} Adicionar</button>${DRIVERS.sources.some(s => s.toLowerCase() === XDR.fonteDrivers.toLowerCase()) ? '' : '<button class="btn sm ghost" data-act="src-def" data-k="src-def">Voltar a usar a fonte de sempre</button>'}</div>
    <p class="warnline" id="src-err" role="alert" style="margin-top:8px"></p>
    <p class="note" style="margin-top:10px">Repositórios do GitHub cujos releases trazem pacotes de driver (.zip). Todo download continua conferido: biblioteca Vulkan arm64 e o SHA-256 quando o release informa.</p>`;
}
function tuFlags() { return String(globalOf('Vulkan.turnip_debug') || '').split(',').map(x => x.trim()).filter(Boolean); }
function drvTurnipHTML() {
  const on = tuFlags(), known = TURNIP.map(t => t.f), kept = on.filter(f => !known.includes(f));
  return `<p class="note" style="margin-bottom:10px">Flags passadas ao driver Turnip (TU_DEBUG) quando o próximo jogo iniciar. Drivers proprietários as ignoram. Valem para todos os jogos; um jogo pode ter as próprias na ficha.</p>
    <div class="card" data-note="tuflags">${TURNIP.map(t => drow({ lead: '<span class="mono" style="font-size:10px">TU</span>', t: `<span class="mono">${esc(t.f)}</span>`, s: esc(t.h), acts: `<button class="tg" role="switch" aria-checked="${on.includes(t.f)}" aria-label="${esc(t.f)}" data-act="tu" data-v="${esc(t.f)}" data-k="tu-${esc(t.f)}"></button>` })).join('')}</div>
    ${kept.length ? `<p class="note" style="margin-top:10px">Flags que esta versão não descreve ficam como estão: ${esc(kept.join(','))}</p>` : ''}
    <p class="note" style="margin-top:10px"><code class="keyc" style="font-size:12px">TU_DEBUG=${esc(on.join(',') || '(nenhuma)')}</code></p>`;
}
action('drv-use', el => { const v = el.dataset.v, cur = globalOf('Vulkan.vulkan_lib_path'); if (v === cur) return; DRIVERS.previous = cur; DRIVERS.selectedAt = 'agora'; setVal(null, 'Vulkan.vulkan_lib_path', v); toast(`${drvName(v)} · próxima abertura`); });
action('drv-rm', el => { S.modal = 'drv-rm'; S.mp = { v: el.dataset.v }; render(); });
action('drv-rm-go', el => {
  const i = DRIVERS.installed.findIndex(d => d.id === el.dataset.v); if (i < 0) return;
  const d = DRIVERS.installed.splice(i, 1)[0];
  for (const g of GAMES) if (OV[g.id] && OV[g.id]['Vulkan.vulkan_lib_path'] === d.id) delete OV[g.id]['Vulkan.vulkan_lib_path'];
  const drv = DEF['Vulkan.vulkan_lib_path']; drv.o = drv.o.filter(o => o[0] !== d.id);
  S.modal = null; toast(`${d.name} removido`);
});
modal('drv-rm', mp => {
  const d = DRIVERS.installed.find(x => x.id === mp.v); if (!d) return { html: '' };
  const games = GAMES.filter(g => OV[g.id] && OV[g.id]['Vulkan.vulkan_lib_path'] === d.id);
  return { html: sheetHead(`Remover ${esc(d.name)}?`) + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">Os arquivos saem do aparelho. Baixe ou importe de novo para usar depois.</p>${games.length ? `<p class="warnline" style="margin-top:8px">${ic('warn', 15)} ${games.length} ${games.length === 1 ? 'jogo usa este driver e voltaria' : 'jogos usam este driver e voltariam'} ao driver global: ${games.map(g => esc(g.name)).join(', ')}</p>` : ''}<div class="acts"><button class="btn ghost" data-act="close" data-k="drm-x" data-autofocus>Cancelar</button><button class="btn danger" data-act="drv-rm-go" data-v="${d.id}" data-k="drm-go">Remover</button></div>` };
});
action('drv-dl', el => {
  if (DL.id) return; const d = DRIVERS.available.find(x => x.id === el.dataset.v); if (!d) return;
  DL.id = d.id; DL.p = 0; if (S.route.name === 'drivers') S.sec.drivers = 'drv:avail'; render();
  DL.timer = setInterval(() => {
    DL.p = Math.min(100, DL.p + 7);
    const bar = document.getElementById('dl-bar'), txt = document.getElementById('dl-txt');
    if (bar) bar.style.width = DL.p + '%'; if (txt) txt.textContent = `${DL.p}%`;
    if (DL.p >= 100) { clearInterval(DL.timer); setTimeout(() => { DRIVERS.available = DRIVERS.available.filter(x => x.id !== d.id); DRIVERS.installed.unshift({ id: d.id, name: d.name, checked: true, size: '14,8 MB', date: 'hoje' }); DEF['Vulkan.vulkan_lib_path'].o.push([d.id, d.name]); DL.id = null; toast(d.sha ? 'Driver instalado · SHA-256 conferido' : 'Driver instalado · sem checksum publicado'); }, 600); }
  }, 140);
});
action('src-add', () => {
  const inp = document.getElementById('src-in'), err = document.getElementById('src-err'), raw = (inp && inp.value || '').trim();
  const fail = m => { if (err) err.textContent = m; else toast(m); };
  const m = /^(?:https?:\/\/(?:www\.)?github\.com\/)?([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+?)(?:\.git)?\/?$/.exec(raw);
  if (!m) { fail('Não é um repositório do GitHub.'); return; }
  const id = m[1] + '/' + m[2];
  if (DRIVERS.sources.some(s => s.toLowerCase() === id.toLowerCase())) { fail('Já está na lista.'); return; }
  if (DRIVERS.sources.length >= XDR.maxFontes) { fail(`Limite de fontes atingido (${XDR.maxFontes}).`); return; }
  DRIVERS.sources.push(id); render(); toast(`${id} adicionada`);
});
action('src-rm', el => { DRIVERS.sources.splice(Number(el.dataset.v), 1); render(); });
action('src-def', () => { DRIVERS.sources.push(XDR.fonteDrivers); render(); });
action('tu', el => {
  let on = tuFlags(); const f = el.dataset.v, flag = TURNIP.find(t => t.f === f);
  if (on.includes(f)) on = on.filter(x => x !== f);
  else { on.push(f); if (flag && flag.x) on = on.filter(x => x === f || !TURNIP.some(t => t.x && t.f === x)); }
  const order = TURNIP.map(t => t.f), v = on.slice().sort((a, b) => (order.indexOf(a) + 1 || 99) - (order.indexOf(b) + 1 || 99)).join(',');
  if (v === String(DEF['Vulkan.turnip_debug'].def)) delete GLOBAL['Vulkan.turnip_debug']; else GLOBAL['Vulkan.turnip_debug'] = v;
  render();
});

screen('drivers', {
  title: 'Drivers', secKey: 'drivers',
  render() {
    const secs = [
      { group: 'Driver', id: 'drv:now', t: 'Em uso', icon: 'chip', body: drvNowHTML },
      { id: 'drv:inst', t: 'Instalados', icon: 'check', n: DRIVERS.installed.length + 1, body: drvInstalledHTML },
      { id: 'drv:avail', t: 'Para baixar', icon: 'download', n: DRIVERS.available.length || '', body: drvAvailHTML },
      { group: 'Avançado', id: 'drv:src', t: 'Fontes', icon: 'cloud', n: DRIVERS.sources.length, body: drvSourcesHTML },
      { id: 'drv:tu', t: 'Opções do Turnip', icon: 'flask', body: drvTurnipHTML },
    ];
    return sectioned({ key: 'drivers', title: 'Drivers de GPU', sub: `${esc(DRIVERS.gpu)} · o que cada jogo carrega`, csub: esc(DRIVERS.gpu), plainSub: true, icon: 'chip', sections: secs });
  },
});
