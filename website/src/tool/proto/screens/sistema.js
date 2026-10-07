/* Telas de sistema, como no app: o assistente de primeira abertura (FirstRunAssistant.kt), as
   pastas de jogos (GameFoldersScreen.kt), o navegador de pastas (FolderBrowserScreen.kt), os jogos
   que saíram (MissingGamesScreen.kt), sem Vulkan (NoVulkanScreen.kt), as atualizações do app
   (UpdateScreen.kt) e Sobre (AboutScreen.kt). O atualizador usa a release estável publicada;
   pastas, arquivos e o aparelho são exemplos. */
'use strict';

const UPD = XDR.atualizacao;
const LIC = XDR.licencas || { projetos: [], motores: [], notas: [] };
const REPO_SITE = (/github\.com\/([^/?#]+\/[^/?#]+)/.exec(XDR.repo || '') || [])[1] || '';
const UPD_ELSEWHERE = !!(XDR.app.repoAtualizacoes && REPO_SITE && XDR.app.repoAtualizacoes.toLowerCase() !== REPO_SITE.toLowerCase());

Object.assign(NOTES, {
  frsteps: ['app', 'Cinco passos, sempre com Pular e Voltar; o botão principal é Continuar e, no último, Começar. O assistente abre sozinho uma vez e volta pelo menu da biblioteca ou por Sobre.'],
  frchecks: ['app', 'As verificações do app: GPU Vulkan (com o nome da GPU), ARM de 64 bits, a API do Android (a API 29 só avisa) e a pasta de jogos.'],
  frfolder: ['app', '“Escolher pasta de jogos” abre o navegador de pastas do app. “Criar pastas padrão” cria XenDroid/Jogos, XenDroid/TU e XenDroid/DLC no armazenamento interno e já as adiciona.'],
  frscan: ['sim', 'A busca encontra os jogos da biblioteca de exemplo; no app, a contagem e as capas vêm da varredura da pasta.'],
  frlocale: ['app', '“Usar nos jogos” grava o idioma e o país do console (Console.user_language e Console.user_country) na configuração global.'],
  frprofile: ['app', 'Numa instalação nova, o app já criou o perfil “XenDroid”. A gamertag tem de 1 a 15 letras ou dígitos, começa com uma letra e aceita espaços simples entre as palavras; o perfil criado fica ativo como P1.'],
  frmode: ['app', 'Ao concluir ou pular sem escolher “Ajustes mostrados”, o app fica no modo Jogador (Essencial).'],
  fdlist: ['app', 'A primeira pasta recebe as instalações de jogo completo; “Instalar aqui” leva outra para o topo. Remover só para de procurar na pasta: nenhum arquivo é movido ou apagado.'],
  fdsim: ['sim', 'Pastas e arquivos de exemplo. Remover uma pasta aqui não tira jogos da biblioteca de exemplo; no app, eles saem na próxima busca.'],
  brroots: ['app', 'Armazenamento interno e cartões SD ou USB montados, o caminho em passos para voltar vários níveis de uma vez, Subir, Nova pasta e Usar esta pasta. Voltar sobe uma pasta.'],
  brcount: ['app', 'A contagem só considera ISO com a assinatura de disco do Xbox, ZAR, pastas com default.xex e contêineres dentro de pastas de tipo de conteúdo.'],
  brsim: ['sim', 'Pastas e arquivos de exemplo: o simulador não lê nada do seu aparelho.'],
  miss: ['app', 'Títulos já vistos ou jogados que a última busca não achou, com o motivo. Remover da lista só oculta: tempo de jogo, notas, saves e capa ficam.'],
  missfind: ['app', '“Onde ele está agora?” abre o navegador para escolher o arquivo do jogo, e a pasta dele entra nas pastas de jogos. “Adicionar a pasta” faz isso direto quando o arquivo está fora das pastas.'],
  misssim: ['sim', 'Os três jogos são fictícios (Title IDs FFFF0002 a FFFF0004).'],
  nvcheck: ['app', 'Sem dispositivo Vulkan, a biblioteca vira esta tela, com as verificações do aparelho; o assistente não abre por cima dela.'],
  nvquit: ['sim', 'No app, Sair fecha o app; aqui só avisa.'],
  uphero: ['app', 'A versão instalada, o canal e a última procura. Ao abrir, a tela procura na hora, a não ser que uma procura tenha rodado há menos de 5 minutos.'],
  uprel: ['sim', UPD ? `Esta tela finge que a build ${UPD.instalada.build} está instalada para mostrar a build ${UPD.oferecida.build} como o app a mostraria: título, começo do SHA-256 e tamanho do APK, e o resumo em português, tudo da release publicada. Nada é baixado.` : 'Sem release publicada no build do site.'],
  upsha: ['app', 'O download é conferido com o tamanho e o SHA-256 publicados; antes de instalar, o app confere se é o mesmo app, mais novo e assinado com a mesma chave. O instalador do Android sempre pede a confirmação.'],
  upchan: ['app', 'Estável olha a última release; Prévia, as 10 mais recentes, prévias inclusive; Desligado nunca procura (e “Procurar agora” responde com a versão instalada).'],
  uprepo: ['dif', `A build publicada procura atualizações em ${XDR.app.repoAtualizacoes}, enquanto as releases estão em ${REPO_SITE}. Se o nome antigo não redirecionar para o novo, o app não encontra as builds publicadas: confira a página de releases.`],
  abdevice: ['app', 'A tabela do aparelho vem do Android e do relato do núcleo; “Copiar tudo” leva a versão, essas linhas e o relato completo, para um relato de problema.'],
  absim: ['sim', 'Valores de exemplo: o simulador não lê o seu aparelho.'],
  ablic: ['app', 'No app, a folha mostra a página licenses.html do APK, em inglês, como aqui.'],
});

/* ---------- utilitários ---------- */
/* plural do português no Android: 0 e 1 no singular ("0 jogo"); as telas de pastas dizem "nenhum jogo" */
const nGames = n => n === 0 ? 'nenhum jogo' : `${n} ${n === 1 ? 'jogo' : 'jogos'}`;
const cap1 = s => s.charAt(0).toUpperCase() + s.slice(1);
/** Formatter.formatShortFileSize do Android: unidades de 1000 e a vírgula do pt-BR. */
function shortSize(b) {
  const u = ['B', 'kB', 'MB', 'GB', 'TB']; let r = Math.max(0, b), i = 0;
  while (r > 900 && i < u.length - 1) { r /= 1000; i++; }
  const d = i === 0 || r >= 10 ? 0 : r < 1 ? 2 : 1;
  return `${r.toFixed(d).replace('.', DEC)} ${u[i]}`;
}
/** DateUtils.getRelativeTimeSpanString em minutos, como o app mostra a última procura. */
function agoText(t) {
  const m = Math.floor(Math.max(0, Date.now() - t) / 60000);
  if (m < 60) return `há ${m} ${m < 2 ? 'minuto' : 'minutos'}`;
  const h = Math.floor(m / 60); return `há ${h} ${h < 2 ? 'hora' : 'horas'}`;
}
/** formatPlayTime (SessionRunStore.kt): "50 min", "14 h 05 min". */
const playTime = min => min < 1 ? '<1 min' : min < 60 ? `${min} min` : `${Math.floor(min / 60)} h ${String(min % 60).padStart(2, '0')} min`;
const checkList = l => `<ul class="chk-l">${l.map(([k, t, s]) => `<li class="${k}">${ic(k === 'ok' ? 'checkC' : k === 'bad' ? 'xCircle' : 'alert', 22)}<div><b>${esc(t)}</b><small>${esc(s)}</small></div></li>`).join('')}</ul>`;
const xlogo = n => `<span class="xlogo" style="width:${n}px;height:${n}px" aria-hidden="true"></span>`;

/* ---------- armazenamento de exemplo ---------- */
const INT = '/storage/emulated/0', SDC = '/storage/1A2B-3C4D';
const SYS = { sd: false };
const roots = () => [{ label: 'Armazenamento interno', path: INT, sd: false }].concat(SYS.sd ? [{ label: 'Cartão SD', path: SDC, sd: true }] : []);
const inside = (p, d) => p === d || p.startsWith(d.replace(/\/$/, '') + '/');
const rootOf = p => roots().filter(r => inside(p, r.path)).sort((a, b) => b.path.length - a.path.length)[0] || null;
/** StoragePaths.display: "Armazenamento interno › XenDroid › Jogos"; fora de um volume montado, o caminho como é. */
function shownPath(p) { const r = rootOf(p); return r ? [r.label].concat(p.slice(r.path.length).split('/').filter(Boolean)).join(' › ') : p; }
const FSYS = {};
const mkdir = () => ({ d: {}, f: [] });
function node(p, make) {
  const base = [INT, SDC].find(x => inside(p, x)); if (!base) return null;
  if (!FSYS[base]) FSYS[base] = mkdir();
  let n = FSYS[base];
  for (const part of p.slice(base.length).split('/').filter(Boolean)) { if (!n.d[part]) { if (!make) return null; n.d[part] = mkdir(); } n = n.d[part]; }
  return n;
}
const parentOf = p => p.slice(0, p.lastIndexOf('/'));
const nameOf = p => p.slice(p.lastIndexOf('/') + 1);
function put(p, bytes, game) { node(parentOf(p), true).f.push([nameOf(p), Math.round(bytes), !!game]); }
const dirNode = p => (rootOf(p) ? node(p) : null);
const fileExists = p => { const n = dirNode(parentOf(p)); return !!(n && !n.ro && n.f.some(x => x[0] === nameOf(p))); };
/** FolderGames.count: ISO e ZAR, pastas com default.xex (contam como um jogo) e contêineres em pastas de tipo de conteúdo; pastas *.data ficam de fora. */
function countGames(n) {
  if (!n || n.ro) return 0;
  if (n.xex) return 1;
  let c = n.f.filter(x => x[2]).length;
  for (const [k, ch] of Object.entries(n.d)) if (!k.endsWith('.data')) c += countGames(ch);
  return c;
}
const bytesOf = s => Math.round(parseFloat(String(s).replace(',', '.')) * (/GB/.test(s) ? 1e9 : 1e6));
['Android', 'DCIM/Camera', 'Documents', 'Download', 'Movies', 'Music', 'Pictures/Xendroid+', 'XenDroid/TU', 'XenDroid/DLC'].forEach(d => node(`${INT}/${d}`, true));
node(`${INT}/Android`).ro = 1;
for (const g of GAMES) {
  if (g.fmt === 'XEX') { const dir = parentOf(g.path); put(g.path, 14.2e6); node(dir).xex = 1; continue; }
  if (g.fmt === 'GOD' || g.fmt === 'STFS') {
    const id = (hash(g.id + 'god').toString(16).toUpperCase() + 'A3F09C1D7E25B48C60F1D9E2B7C4A58E31D06F2B').slice(0, 40);
    put(`${g.path}/${g.fmt === 'GOD' ? '00007000' : '000D0000'}/${id}`, 11.2e3, true);
    for (let i = 0; i < 3; i++) put(`${g.path}/${g.fmt === 'GOD' ? '00007000' : '000D0000'}/${id}.data/Data000${i}`, 163e6);
    continue;
  }
  put(g.path, bytesOf(g.size), true);
}
for (const f of CI_FILES) put(`${INT}/${f.folder ? 'XenDroid/' + f.folder : 'Download'}/${f.f}`, f.mb * 1e6);
put(`${INT}/DCIM/Camera/IMG_0001.jpg`, 3.1e6);
put(`${INT}/Download/manual.pdf`, 2.4e6);
put(`${INT}/Jogos antigos/Jogo de exemplo 2.iso`, 6.9e9, true);
put(`${INT}/Download/Jogo de exemplo 4.zar`, 1.8e9, true);
put(`${SDC}/Jogos/Jogo de exemplo 3.iso`, 7.3e9, true);
node(`${SDC}/Música`, true);

/* ---------- pastas de jogos ---------- */
const STD_GAMES = `${INT}/XenDroid/Jogos`;
const FOLDERS = [STD_GAMES, `${SDC}/Jogos`];
const folderAway = p => !dirNode(p);
const FD = { scanning: false };
function addFolder(p) { if (!FOLDERS.includes(p)) FOLDERS.push(p); }
function fdRow(p, i, v) {
  const away = folderAway(p), install = i === 0, n = away ? 0 : countGames(dirNode(p));
  const acts = `${!install && !away ? `<button class="btn sm ghost" data-act="fd-inst" data-v="${i}" data-k="fd-i${i}">Instalar aqui</button>` : ''}<button class="btn sm ghost" data-act="fd-rm" data-v="${i}" data-k="fd-r${i}">Remover</button>`;
  return `<div class="fd ${away ? 'away' : ''}">${ic('folder', 24)}<div><b>${esc(shownPath(p))}</b>${install || away ? `<span class="fd-badges">${install ? '<span class="badge acc">instalações vão para cá</span>' : ''}${away ? '<span class="badge warn">indisponível agora</span>' : ''}</span>` : ''}<small>${away ? 'Os jogos dela ficam fora da biblioteca até a pasta voltar (cartão SD ou permissão?).' : esc(cap1(nGames(n)))}</small></div><div class="row">${acts}</div></div>`;
}
function fdBody(v) {
  const total = FOLDERS.filter(p => !folderAway(p)).reduce((s, p) => s + countGames(dirNode(p)), 0);
  const plural = FOLDERS.length === 1 ? '1 pasta' : `${FOLDERS.length} pastas`;
  return `<div class="stack"><div class="row" data-note="fdsim"><span class="note" style="flex:1">${esc(cap1(`${nGames(total)} em ${plural}`))}</span><button class="btn sm ghost" data-act="fd-scan" data-k="fd-scan"${FD.scanning ? ' disabled' : ''}>${ic('refresh', 15)} ${FD.scanning ? 'Procurando…' : 'Procurar de novo'}</button></div>
    ${FOLDERS.length ? `<section class="card" data-note="fdlist"><div class="list">${FOLDERS.map((p, i) => fdRow(p, i, v)).join('')}</div></section>` : `<div class="empty">Nenhuma pasta ainda. <button class="btn sm primary" data-act="br-add" data-v="folders" data-k="fd-add0">${ic('plus', 15)} Adicionar pasta</button></div>`}
    ${FOLDERS.includes(STD_GAMES) ? '' : `<section class="card"><h3>${ic('folder', 15)} Criar pastas padrão</h3><p class="note">Cria XenDroid/Jogos para os jogos e XenDroid/TU e XenDroid/DLC para atualizações e DLC, e já as adiciona.</p><div class="row"><button class="btn sm" data-act="fd-std" data-k="fd-std">${ic('plus', 15)} Criar pastas padrão</button></div></section>`}
    <p class="note">Remover uma pasta só para de procurar nela; os arquivos ficam onde estão. Uma pasta dentro de outra é procurada uma vez só.</p></div>`;
}
action('fd-scan', () => { FD.scanning = true; render(); setTimeout(() => { FD.scanning = false; if (S.route.name === 'folders') render(); }, 1100); });
action('fd-rm', el => { const i = Number(el.dataset.v), before = FOLDERS.slice(), p = FOLDERS.splice(i, 1)[0]; UNDO = { folders: before }; toast(`Paramos de procurar em ${shownPath(p)}; os arquivos ficam.`, true); });
action('fd-inst', el => { const i = Number(el.dataset.v); FOLDERS.unshift(FOLDERS.splice(i, 1)[0]); toast('As instalações vão para esta pasta agora.'); });
function makeStandard() { addFolder(STD_GAMES); toast('Criadas em XenDroid: Jogos (já é pasta de jogos), TU e DLC (pastas de conteúdo, em Conteúdo → Instalar).'); }
action('fd-std', () => makeStandard());
const undoBefore = ACT.undo;
action('undo', el => { if (UNDO && UNDO.folders) { FOLDERS.splice(0, FOLDERS.length, ...UNDO.folders); UNDO = null; S.toast = null; render(); return; } undoBefore(el); });
const sdVariant = { label: 'Cartão SD', list: [['out', 'Fora do aparelho'], ['in', 'Inserido']], get: () => SYS.sd ? 'in' : 'out', set: v => { SYS.sd = v === 'in'; if (!rootOf(BR.dir)) BR.dir = INT; } };
screen('folders', {
  title: 'Pastas de jogos',
  variants: [sdVariant],
  render() { return single({ key: 'folders', title: 'Pastas de jogos', sub: FOLDERS.length === 1 ? '1 pasta' : `${FOLDERS.length} pastas`, icon: 'folder', actions: `<button class="btn sm primary" data-act="br-add" data-v="folders" data-k="fd-add">${ic('plus', 15)} Adicionar pasta</button>`, body: fdBody }); },
});

/* ---------- navegador de pastas ---------- */
const BR = { mode: 'folder', dir: INT, find: null };
const byName = (a, b) => a.toLowerCase() < b.toLowerCase() ? -1 : a.toLowerCase() > b.toLowerCase() ? 1 : 0;
function openBrowser(mode, dir, find) { BR.mode = mode; BR.dir = dir && dirNode(dir) ? dir : INT; BR.find = find || null; go('browse'); }
/** Sai do navegador sem animação, para a ação seguinte já valer na tela de onde ele foi aberto. */
function leaveBrowser(after) { S.route = S.stack.pop() || { name: 'library', p: {} }; S.modal = null; persist(); render(); focusStart(); if (after) after(); }
function brTitles() {
  if (BR.mode === 'find') { const m = MISSING.find(x => x.id === BR.find); return [`Onde está ${m ? m.name : 'o jogo'} agora?`, 'Escolha o arquivo do jogo: a pasta dele entra nas pastas de jogos.']; }
  if (BR.mode === 'file') return ['Escolher o arquivo do pacote', 'Toque num arquivo para instalá-lo.'];
  if (BR.mode === 'cfolder') return ['Pasta de atualizações e DLC', 'Os pacotes dela e das pastas dentro dela aparecem aqui para instalar.'];
  return ['Escolher pasta de jogos', 'Abra a pasta dos seus jogos e escolha Usar esta pasta.'];
}
function brBody(v) {
  const r = rootOf(BR.dir) || roots()[0], n = dirNode(BR.dir) || mkdir(), atRoot = BR.dir === r.path, picksFile = BR.mode === 'file' || BR.mode === 'find';
  const dirs = n.ro ? [] : Object.keys(n.d).sort(byName), files = !picksFile || n.ro ? [] : n.f.slice().sort((a, b) => byName(a[0], b[0]));
  const crumbs = [[r.label, r.path]].concat(BR.dir.slice(r.path.length).split('/').filter(Boolean).map((p, i, a) => [p, r.path + '/' + a.slice(0, i + 1).join('/')]));
  const here = countGames(n);
  return `<div class="stack br">${roots().length > 1 ? `<div class="row" data-note="brroots">${roots().map(x => `<button class="chip" data-act="br-to" data-v="${esc(x.path)}" aria-pressed="${x === r || x.path === r.path}" data-k="brr-${x.sd ? 'sd' : 'int'}">${ic(x.sd ? 'sd' : 'phone', 15)} ${esc(x.label)}</button>`).join('')}</div>` : ''}
    <nav class="br-path" aria-label="Caminho"${roots().length > 1 ? '' : ' data-note="brroots"'}>${crumbs.map(([t, p], i) => `${i ? ic('chevR', 14) : ''}<button data-act="br-to" data-v="${esc(p)}" data-k="brp-${i}"${i === crumbs.length - 1 ? ' aria-current="location"' : ''}>${esc(t)}</button>`).join('')}</nav>
    <section class="card" data-note="brsim"><div class="br-list">${atRoot ? '' : `<button class="br-row" data-act="br-up" data-k="br-up">${ic('back', 20)}<b>Subir</b><span></span></button>`}
      ${dirs.map((d, i) => { const c = countGames(n.d[d]); return `<button class="br-row dir" data-act="br-open" data-v="${esc(d)}" data-k="brd-${hash(BR.dir + '/' + d)}"${i === 0 && atRoot ? ' data-autofocus' : ''}>${ic('folder', 20)}<b>${esc(d)}</b>${c ? `<span class="badge acc"${i === 0 ? ' data-note="brcount"' : ''}>${esc(nGames(c))}</span>` : '<span></span>'}</button>`; }).join('')}
      ${files.map(f => `<button class="br-row" data-act="br-file" data-v="${esc(f[0])}" data-k="brf-${hash(BR.dir + '/' + f[0])}">${ic('box', 20)}<b>${esc(f[0])}</b><span class="note">${shortSize(f[1])}</span></button>`).join('')}
      ${!dirs.length && !files.length ? `<p class="note" style="padding:10px">${picksFile ? 'Nada aqui. Suba e escolha outra pasta.' : 'Nenhuma subpasta aqui. Use “Usar esta pasta” para escolher esta pasta.'}</p>` : ''}</div></section>
    <div class="br-foot"><span class="note" style="flex:1">${picksFile ? '' : here ? `${esc(nGames(here))} nesta pasta e nas de dentro` : 'Nenhum jogo de Xbox 360 aqui'}</span>${picksFile ? '' : `<button class="btn ghost" data-act="modal" data-v="br-new" data-k="br-new"${n.ro ? ' disabled' : ''}>${ic('plus', 16)} Nova pasta</button>`}<button class="btn ghost" data-act="back-out" data-k="br-cancel">Cancelar</button>${picksFile ? '' : `<button class="btn primary" data-act="br-use" data-k="br-use">${ic('check', 16)} Usar esta pasta</button>`}</div></div>`;
}
action('br-add', el => { BR.ret = el.dataset.v || 'library'; openBrowser('folder', INT); });
action('ci-pick', () => openBrowser('file', INT));
action('cmf-add', () => openBrowser('cfolder', INT));
/** No modo controle e no teclado, a lista começa na primeira linha depois de trocar de pasta. */
function brFocus() { render(); if (!S.nav) return; const el = app.querySelector('.br-list .br-row'); if (el) el.focus({ preventScroll: true }); }
action('br-to', el => { BR.dir = el.dataset.v; render(); });
action('br-up', () => { const r = rootOf(BR.dir); if (r && BR.dir !== r.path) BR.dir = parentOf(BR.dir); brFocus(); });
action('br-open', el => { BR.dir = `${BR.dir}/${el.dataset.v}`; brFocus(); });
action('back-out', () => leaveBrowser());
action('br-use', () => {
  const p = BR.dir, ret = BR.ret;
  if (BR.mode === 'cfolder') { if (!CM_FOLDERS.includes(p)) CM_FOLDERS.push(p); leaveBrowser(); return; }
  addFolder(p);
  leaveBrowser(() => { if (ret === 'firstrun') frFolderAdded(p); });
});
action('br-file', el => {
  const p = `${BR.dir}/${el.dataset.v}`;
  if (BR.mode === 'file') {
    const i = CI_FILES.findIndex(f => f.f === el.dataset.v);
    leaveBrowser(() => { if (i >= 0) ACT['ci-file']({ dataset: { v: String(i) } }); else { S.modal = 'ci-msg'; S.mp = { t: 'Não instalado', m: 'Não é um pacote de conteúdo reconhecido (precisa ser CON/LIVE/PIRS).' }; render(); } });
    return;
  }
  const folder = parentOf(p), m = MISSING.find(x => x.id === BR.find);
  addFolder(folder);
  leaveBrowser(() => toast(`${shownPath(folder)} entrou nas pastas de jogos.${m && p !== m.now ? ' Este não é o arquivo do jogo; ele continua na lista.' : ''}`));
});
action('input:br-name', el => { const b = document.getElementById('br-mk'), e = document.getElementById('br-err'); if (b) b.disabled = !el.value.trim(); if (e) e.hidden = true; });
action('br-mk', () => {
  const inp = document.getElementById('br-name'), name = ((inp || {}).value || '').trim(), e = document.getElementById('br-err');
  if (!name) return;
  if (name.includes('/') || name === '.' || name === '..') { if (e) { e.innerHTML = `${ic('warn', 15)} Use um nome sem “/”.`; e.hidden = false; } return; }
  const n = dirNode(BR.dir); if (!n || n.ro) { if (e) { e.innerHTML = `${ic('warn', 15)} Não deu para criar a pasta aqui.`; e.hidden = false; } return; }
  if (!n.d[name]) n.d[name] = mkdir();
  BR.dir = `${BR.dir}/${name}`; S.modal = null; render();
});
modal('br-new', () => ({ html: sheetHead(`Nova pasta em ${esc(nameOf(BR.dir) || BR.dir)}`) + `<input class="txt plain" id="br-name" type="text" maxlength="80" placeholder="Nome da pasta" aria-label="Nome da pasta" data-k="br-name" autocomplete="off" spellcheck="false" data-autofocus><p class="errline" id="br-err" hidden></p><div class="acts"><button class="btn ghost" data-act="close" data-k="brn-no">Cancelar</button><button class="btn primary" id="br-mk" data-act="br-mk" data-k="brn-ok" disabled>${ic('check', 16)} Criar e abrir</button></div>` }));
screen('browse', {
  title: 'Navegador de pastas',
  variants: [
    { label: 'Para', list: [['folder', 'Escolher pasta de jogos'], ['file', 'Escolher arquivo de pacote']], get: () => BR.mode === 'file' ? 'file' : 'folder', set: v => { BR.mode = v; BR.find = null; BR.dir = v === 'file' ? `${INT}/Download` : INT; } },
    sdVariant,
  ],
  render() { const [t, sub] = brTitles(); return single({ key: 'browse', title: t, sub: esc(sub), icon: 'folder', noRail: true, body: brBody }); },
  onBack() { const r = rootOf(BR.dir); if (r && BR.dir !== r.path) { BR.dir = parentOf(BR.dir); brFocus(); return true; } leaveBrowser(); return true; },
});

/* ---------- jogos que saíram ---------- */
const MISSING = [
  { id: 'FFFF0002', name: 'Jogo de exemplo 2', pal: ['#0a1426', '#2a5aa8', '#e0e8ff'], last: `${STD_GAMES}/Jogo de exemplo 2.iso`, now: `${INT}/Jogos antigos/Jogo de exemplo 2.iso`, min: 845, runs: 22 },
  { id: 'FFFF0003', name: 'Jogo de exemplo 3', pal: ['#0d1a14', '#1f6b5a', '#f2c46a'], last: `${SDC}/Jogos/Jogo de exemplo 3.iso`, now: null, min: 182, runs: 5 },
  { id: 'FFFF0004', name: 'Jogo de exemplo 4', pal: ['#140a1a', '#7a2a9a', '#9be37f'], last: `${INT}/Download/Jogo de exemplo 4.zar`, now: null, min: 50, runs: 2 },
];
/** Onde o título está agora, se uma pasta de jogos disponível o alcança. */
function located(m) { const p = fileExists(m.last) ? m.last : m.now && fileExists(m.now) ? m.now : null; return p && FOLDERS.some(f => !folderAway(f) && inside(p, f)) ? p : null; }
function reasonOf(m) { const f = FOLDERS.find(x => inside(m.last, x)); if (f && folderAway(f)) return 'away'; if (fileExists(m.last) && !f) return 'outside'; return 'gone'; }
const missingList = () => MISSING.filter(m => !m.hidden && !located(m));
const missingCount = () => missingList().length;
const MISS_WHY = { gone: 'Arquivo não encontrado: movido, renomeado ou apagado', away: 'A pasta de jogos dele está indisponível agora', outside: 'O arquivo está fora das suas pastas de jogos' };
function missRow(m) {
  if (!m.icon) m.icon = drawIcon(m).toDataURL('image/png');
  const why = reasonOf(m);
  const acts = `${why === 'outside' ? `<button class="btn sm" data-act="miss-add" data-v="${m.id}" data-k="ms-a-${m.id}" data-note="missfind">Adicionar a pasta</button>` : why === 'gone' ? `<button class="btn sm" data-act="miss-find" data-v="${m.id}" data-k="ms-f-${m.id}">Onde ele está agora?</button>` : ''}<button class="btn sm ghost" data-act="miss-rm" data-v="${m.id}" data-k="ms-r-${m.id}">Remover da lista</button>`;
  return drow({ img: `<span class="ms-cover">${coverHTML(m)}</span>`, t: esc(m.name), s: `<span class="${why === 'away' ? 'warn-t' : ''}">${esc(MISS_WHY[why])}</span><br>Jogado ${playTime(m.min)} em ${m.runs} ${m.runs < 2 ? 'sessão' : 'sessões'} · Title ID ${m.id}<br><span class="path">${esc(shownPath(m.last))}</span>`, acts });
}
function missBody() {
  const l = missingList();
  return `<div class="stack">${l.length ? `<section class="card" data-note="miss"><div class="list" data-note="misssim">${l.map(missRow).join('')}</div></section>` : '<div class="empty">Todo jogo que você jogou está na biblioteca.</div>'}
    <p class="note">Remover só oculta o jogo aqui: tempo de jogo, notas de compatibilidade, saves e capa ficam, e ele volta quando o arquivo for encontrado de novo. Nenhum arquivo é mexido.</p></div>`;
}
const missBy = id => MISSING.find(m => m.id === id);
action('miss-rm', el => { const m = missBy(el.dataset.v); m.hidden = true; toast(`${m.name} saiu da lista; volta quando o arquivo aparecer.`); });
action('miss-find', el => { const m = missBy(el.dataset.v); let d = parentOf(m.last); while (d.length > INT.length && !dirNode(d)) d = parentOf(d); openBrowser('find', d, m.id); });
action('miss-add', el => { const m = missBy(el.dataset.v), f = parentOf(m.last); addFolder(f); toast(`${shownPath(f)} entrou nas pastas de jogos.`); });
screen('missing', {
  title: 'Jogos que saíram',
  variants: [sdVariant],
  render() { return single({ key: 'missing', title: 'Jogos que saíram da biblioteca', sub: esc(cap1(nGames(missingCount()))), icon: 'inbox', actions: `<button class="btn sm ghost" data-act="go" data-v="folders" data-k="ms-folders">${ic('folder', 15)} Pastas de jogos</button>`, body: missBody }); },
});

/* ---------- primeira abertura ---------- */
const FR = { step: 0, old: false, folders: [], scanning: false, found: null, locale: '', defTag: 'XenDroid', made: null, another: false, tag: '', timer: 0 };
const FR_STEPS = [['phone', 'Este telefone'], ['games', 'Seus jogos'], ['locale', 'Idioma e região'], ['profile', 'Perfil'], ['use', 'Como usar']];
function frChecks() {
  return [
    ['ok', 'GPU Vulkan', DRIVERS.gpu],
    ['ok', 'ARM de 64 bits', 'arm64-v8a'],
    FR.old ? ['warn', 'Android', 'API 29: escolher uma pasta de jogos exige Android 11 ou mais novo; jogos abertos por um frontend continuam funcionando.'] : ['ok', 'Android', 'API 35'],
    FR.folders.length ? ['ok', 'Pasta de jogos', 'Escolhida; a biblioteca procura nela.'] : FR.old ? ['warn', 'Pasta de jogos', 'Exige Android 11 ou mais novo (acesso a todos os arquivos).'] : ['warn', 'Pasta de jogos', 'Ainda não escolhida: escolha a pasta onde estão seus jogos.'],
  ];
}
function frFolderAdded(p) {
  if (!FR.folders.includes(p)) FR.folders.push(p);
  clearTimeout(FR.timer); FR.scanning = true; FR.found = null; render();
  FR.timer = setTimeout(() => { FR.scanning = false; FR.found = FR.folders.reduce((s, f) => s + countGames(dirNode(f)), 0); if (S.route.name === 'firstrun') render(); }, 1400);
}
const frAvatar = tag => { const p = { tag, c: ['#2f8f4e', '#0f3d22'] }; return `<img class="avatar-l" src="${drawAvatar(p).toDataURL('image/png')}" alt="" draggable="false">`; };
function frBody(v) {
  const s = FR_STEPS[FR.step][0];
  if (s === 'phone') return `<h2>Boas-vindas ao Xendroid+</h2><p class="lead2">Algumas verificações e escolhas. Tudo pode ser mudado depois, e nada daqui é enviado a lugar nenhum.</p><section class="card" data-note="frchecks">${checkList(frChecks())}</section>`;
  if (s === 'games') {
    if (FR.old) return `<h2>Seus jogos</h2><p class="warnline">${ic('alert', 16)} Exige Android 11 ou mais novo (acesso a todos os arquivos).</p>`;
    const head = '<h2>Seus jogos</h2><p class="lead2">Escolha a pasta onde estão seus jogos (ISO, XEX, ZAR ou pacotes). Dá para adicionar mais pastas depois.</p>';
    if (!FR.folders.length) return `${head}<div class="row" data-note="frfolder"><button class="btn primary lg" data-act="fr-folder" data-k="fr-folder">${ic('folder', 18)} Escolher pasta de jogos</button><button class="btn lg" data-act="fr-std" data-k="fr-std">${ic('plus', 18)} Criar pastas padrão</button></div><p class="note">Cria XenDroid/Jogos para os jogos e XenDroid/TU e XenDroid/DLC para atualizações e DLC, e já as adiciona.</p>`;
    const found = FR.found;
    return `${head}<section class="card" data-note="frscan">${FR.folders.map(f => `<div class="row fr-fold">${ic('folder', 20)}<b>${esc(shownPath(f))}</b></div>`).join('')}
      <div class="row"><div style="flex:1;min-width:0">${FR.scanning ? `<div class="bar ind"><i></i></div><small class="note">Procurando jogos…</small>` : found != null ? `<p class="${found ? 'okline' : 'warnline'}">${ic(found ? 'checkC' : 'alert', 15)} ${found} ${found < 2 ? 'jogo encontrado' : 'jogos encontrados'}</p>` : ''}</div><button class="btn sm ghost" data-act="fr-folder" data-k="fr-more">${ic('plus', 15)} Adicionar outra</button></div>
      ${!FR.scanning && found ? `<div class="strip">${GAMES.slice(0, Math.min(10, found)).map(g => coverHTML(g)).join('')}</div>` : ''}</section>`;
  }
  if (s === 'locale') return `<h2>Idioma e região nos jogos</h2><section class="card" data-note="frlocale"><p style="font-size:14px;margin:0">Deste telefone: idioma Português · região Brasil</p>${FR.locale === 'saved' ? `<p class="okline">${ic('checkC', 15)} Os jogos vão usá-los a partir da próxima abertura.</p>` : `<div class="row"><button class="btn primary" data-act="fr-locale" data-k="fr-locale"${FR.locale ? ' disabled' : ''}>Usar nos jogos</button>${FR.locale === 'saving' ? '<span class="note">Salvando…</span>' : ''}</div>`}</section><p class="note">Muda depois em Configurações → Console e sistema, ou por perfil.</p>`;
  if (s === 'profile') {
    let card;
    if (FR.made) card = `<div class="row">${frAvatar(FR.made)}<div><b class="fr-name">${esc(FR.made)}</b><p class="okline" style="margin-top:6px">${ic('checkC', 15)} Perfil criado e ativo (P1).</p></div></div>`;
    else if (FR.another) {
      const bad = FR.tag && !tagOk(FR.tag);
      card = `<label class="fld">Gamertag<input class="txt plain" id="fr-tag" type="text" maxlength="15" placeholder="XenPlayer" data-k="fr-tag" autocomplete="off" spellcheck="false" value="${esc(FR.tag)}" data-autofocus></label><p class="errline" id="fr-err"${bad ? '' : ' hidden'}>${ic('warn', 15)} 1 a 15 letras/dígitos; começando com uma letra.</p><div class="row"><button class="btn primary" id="fr-create" data-act="fr-profile" data-k="fr-profile"${tagOk(FR.tag) ? '' : ' disabled'}>Criar perfil</button><button class="btn ghost" data-act="go" data-v="profiles" data-k="fr-prof-open">Abrir Perfis</button></div>`;
    } else card = `<div class="row">${frAvatar(FR.defTag)}<b>Perfil ativo: ${esc(FR.defTag)}</b></div><div class="row"><button class="btn" data-act="fr-another" data-k="fr-another">Criar outro</button><button class="btn ghost" data-act="go" data-v="profiles" data-k="fr-prof-open">Abrir Perfis</button></div>`;
    return `<h2>Perfil</h2><p class="lead2">Os jogos salvam num perfil (gamertag). Um chamado “XenDroid” é criado quando não existe nenhum; crie ou renomeie o seu em Perfis.</p><section class="card" data-note="frprofile">${card}</section>`;
  }
  return `<h2>Como usar</h2><section class="card" data-note="frmode">${appRows(['@app.mode', '@app.level'], v)}</section>
    <section class="card"><h3>${ic('chip', 15)} Driver da GPU (opcional)</h3><p style="font-size:13.5px;margin:0">O driver Vulkan do próprio telefone basta para começar. Um pacote Turnip pode ser instalado depois em Drivers, e trocado de volta quando quiser.</p></section>`;
}
/** Gamertag.isValid: 1 a 15 caracteres, começa com letra, palavras separadas por um espaço. */
const tagOk = t => t.length >= 1 && t.length <= 15 && /^[A-Za-z][A-Za-z0-9]*( [A-Za-z0-9]+)*$/.test(t);
action('fr-step', el => { FR.step = clamp(Number(el.dataset.v), 0, FR_STEPS.length - 1); render(); focusKey('fr-next'); });
action('fr-next', () => { if (FR.step === FR_STEPS.length - 1) { ACT['fr-done'](); return; } FR.step++; render(); focusKey('fr-next'); });
action('fr-prev', () => { if (FR.step) { FR.step--; render(); focusKey('fr-next'); } });
action('fr-done', () => { clearTimeout(FR.timer); FR.scanning = false; goTop('library'); toast('Pronto. O assistente fica no menu da biblioteca.'); });
action('fr-folder', () => { BR.ret = 'firstrun'; openBrowser('folder', INT); });
action('fr-std', () => { makeStandard(); frFolderAdded(STD_GAMES); });
action('fr-locale', () => { FR.locale = 'saving'; render(); setTimeout(() => { FR.locale = 'saved'; GLOBAL['Console.user_language'] = '9'; GLOBAL['Console.user_country'] = '13'; if (S.route.name === 'firstrun') render(); }, 500); });
action('fr-another', () => { FR.another = true; render(); });
action('input:fr-tag', el => { FR.tag = el.value; const ok = tagOk(FR.tag), b = document.getElementById('fr-create'), e = document.getElementById('fr-err'); if (b) b.disabled = !ok; if (e) e.hidden = ok || !FR.tag; });
action('fr-profile', () => {
  if (!tagOk(FR.tag)) return;
  const p = { xuid: newXuid(), tag: FR.tag, c: ['#2f8f4e', '#0f3d22'], lang: 'Português', region: 'Brasil', active: false, slot: null, games: 0, files: 0, mb: 0 };
  p.avatar = drawAvatar(p).toDataURL('image/png'); PROFILES.push(p);
  PROFILES.forEach(x => { x.active = x === p; if (x.slot === 1) x.slot = null; }); p.slot = 1;
  FR.made = FR.tag; render();
});
function frReset() { clearTimeout(FR.timer); Object.assign(FR, { step: 0, scanning: false, found: null, locale: '', made: null, another: false, tag: '' }); FR.folders = []; }
screen('firstrun', {
  title: 'Primeira abertura', globalScope: true,
  variants: [{ label: 'Aparelho', list: [['ok', 'Android 15 (API 35)'], ['old', 'Android 10 (API 29)']], get: () => FR.old ? 'old' : 'ok', set: v => { FR.old = v === 'old'; } }],
  render() {
    const last = FR.step === FR_STEPS.length - 1;
    return `<div class="fr"><aside class="fr-side" data-note="frsteps">${xlogo(40)}<h1>Xendroid+</h1><p class="note">Emulação de Xbox 360 no Android.</p>
      <ol class="fr-steps">${FR_STEPS.map(([id, t], i) => `<li><button class="${i < FR.step ? 'done' : ''}" data-act="fr-step" data-v="${i}" data-k="frs-${id}"${i === FR.step ? ' aria-current="step"' : ''}><span class="d">${i < FR.step ? ic('check', 13) : i + 1}</span><span class="t">${t}</span></button></li>`).join('')}</ol></aside>
      <div class="fr-main"><div class="fr-body" data-sk="fr-${FR.step}" tabindex="0" role="region" aria-label="${FR_STEPS[FR.step][1]}">${frBody(isC() ? 'c' : 'b')}</div>
      <div class="fr-foot"><button class="btn ghost" data-act="fr-done" data-k="fr-skip">Pular</button><span class="sp"></span>${FR.step ? `<button class="btn" data-act="fr-prev" data-k="fr-prev">Voltar</button>` : ''}<button class="btn primary" data-act="fr-next" data-k="fr-next" data-autofocus>${last ? 'Começar' : 'Continuar'}</button></div></div></div>`;
  },
  onBack() { if (FR.step) { FR.step--; render(); return true; } return true; },
});

/* ---------- sem Vulkan ---------- */
screen('novulkan', {
  title: 'Sem Vulkan',
  render() {
    return `<div class="nv"><section class="card"><h1>${ic('chip', 30)} Este aparelho não tem GPU Vulkan</h1><p style="margin:0;font-size:14px;color:var(--fg2)">O emulador não pode rodar: nenhum dispositivo Vulkan foi encontrado, então os jogos não rodam neste telefone.</p>
      <div data-note="nvcheck">${checkList([['bad', 'GPU Vulkan', 'Nenhum dispositivo Vulkan encontrado: os jogos não rodam neste telefone.'], ['ok', 'ARM de 64 bits', 'arm64-v8a'], ['ok', 'Android', 'API 35']])}</div>
      <div class="row"><button class="btn primary" data-act="toast" data-msg="No app, Sair fecha o app." data-k="nv-quit" data-note="nvquit" data-autofocus>Sair</button><button class="btn ghost" data-act="toast" data-msg="Dados do aparelho copiados." data-k="nv-copy">${ic('copy', 15)} Copiar os dados do aparelho</button></div>
      <p class="note">Nada foi apagado; se o aparelho ganhar suporte a Vulkan numa atualização do Android, o app volta a funcionar.</p></section></div>`;
  },
});

/* ---------- atualizações do app ---------- */
const UP = { st: 'available', p: 0, timer: 0, at: Date.now() - 120000, skipped: false, fail: null, perm: false, latest: false, nosha: false };
const UPD_NOTE = { stable: 'Oferece novas versões; cada uma é conferida com o SHA-256 publicado e só é instalada depois que você confirma.', preview: 'Também oferece versões de prévia, que podem ser menos testadas.', off: 'Nunca procura atualizações.' };
const upInst = () => (UP.latest ? UPD.oferecida : UPD.instalada);
/* a descrição do canal muda com a escolha, como em XdUpdateChannelOption */
Object.defineProperty(DEF['@app.updates'], 'd', { get: () => UPD_NOTE[globalOf('@app.updates')] || UPD_NOTE.stable, configurable: true });
const upBytes = () => UPD.oferecida.apk.bytes;
const cooldownLeft = () => Math.max(0, 5 * 60000 - (Date.now() - UP.at));
function upSteps(stage) {
  const order = ['dl', 'verify', 'install'], at = order.indexOf(stage);
  return `<ol class="steps">${[['Baixar', 0], ['Conferir o SHA-256', 1], ['Instalar', 2]].map(([t, i]) => `<li class="${i < at ? 'done' : i === at ? 'now' : ''}">${i < at ? ic('checkC', 20) : i === at ? `<span class="spin" style="display:inline-grid">${ic('refresh', 20)}</span>` : ic('clock', 20)}<span>${t}</span><time></time></li>`).join('')}</ol>`;
}
function upCard() {
  const st = UP.st;
  if (st === 'nofeed') return '';
  if (st === 'checking') return `<section class="card"><p class="note" style="display:flex;gap:10px;align-items:center;margin:0"><span class="spin" style="display:inline-grid">${ic('refresh', 18)}</span> Procurando…</p></section>`;
  if (st === 'latest') return `<section class="card"><p class="okline" style="font-size:14px">${ic('checkC', 16)} Você está na versão mais recente: ${esc(upInst().commit)}</p>${UP.skipped ? '<p class="note">Esta versão não será oferecida de novo.</p>' : ''}</section>`;
  if (st === 'cooldown') { const s = Math.ceil(cooldownLeft() / 1000); return `<section class="card"><h3>${ic('clock', 15)} Atualizador em pausa</h3><p style="font-size:13.5px;margin:0">Você pode procurar de novo em ${Math.floor(s / 60)} min ${s % 60} s.</p></section>`; }
  const r = UPD.oferecida, sha = UP.nosha ? null : r.apk.sha256, stage = ['dl', 'verify', 'install', 'perm'].includes(st) ? (st === 'perm' ? 'install' : st) : null;
  const head = `<div class="row up-rel"><b>${esc(r.titulo)}</b>${sha ? `<span class="badge">${esc(sha.slice(0, 7))}</span>` : ''}<span class="note">${shortSize(r.apk.bytes)}</span></div>`;
  const notes = r.notas.length ? `<ul class="clog">${r.notas.map(n => `<li>${esc(n)}</li>`).join('')}</ul>` : '<p class="note">Nenhuma lista de mudanças disponível.</p>';
  const verifiable = `<p class="${sha ? 'okline' : 'warnline'}" data-note="upsha">${ic(sha ? 'shield' : 'alert', 15)} ${sha ? 'Conferida com o SHA-256 publicado antes de instalar' : 'Sem SHA-256 publicado: abra a página da versão para instalar você mesmo'}</p>`;
  let tail;
  if (stage) {
    tail = upSteps(stage);
    if (st === 'dl') tail += `<div class="bar"><i id="up-bar" style="width:${UP.p}%"></i></div><div class="row"><span class="note" id="up-txt" style="flex:1">${shortSize(upBytes() * UP.p / 100)} de ${shortSize(upBytes())}</span><button class="btn sm ghost" data-act="up-cancel" data-k="up-cancel">Cancelar</button></div>`;
    if (st === 'perm') tail += `<p class="warnline">${ic('alert', 15)} Permita que o Xendroid+ instale atualizações e toque de novo em Baixar e instalar</p><div class="row"><button class="btn sm primary" data-act="up-allow" data-k="up-allow">Abrir a permissão</button></div>`;
    if (st === 'install') tail += `<p class="okline">${ic('shield', 15)} Download conferido. O instalador do Android pede a confirmação.</p>`;
  } else {
    tail = `${UP.fail ? `<p class="errline">${ic('alert', 15)} ${esc(UP.fail)}</p>` : ''}<div class="row">${sha ? `<button class="btn primary" data-act="up-dl" data-k="up-dl" data-autofocus>${ic('download', 16)} ${UP.fail ? 'Tentar de novo' : 'Baixar e instalar'}</button>` : ''}<button class="btn${sha ? ' ghost' : ' primary'}" data-act="toast" data-msg="No app, abre a página da versão (${esc(r.tag)}) no navegador." data-k="up-page"${sha ? '' : ' data-autofocus'}>Página da versão</button><button class="btn ghost" data-act="up-skip" data-k="up-skip">Pular esta versão</button><button class="btn ghost" data-act="back" data-k="up-later">Depois</button></div>`;
  }
  return `<section class="card" data-note="uprel"><h3>${ic('download', 15)} Atualização disponível</h3>${head}${notes}${verifiable}${tail}</section>`;
}
function upBody(v) {
  if (!UPD) return '<div class="empty">O build do site não tinha uma release publicada para simular a atualização.</div>';
  const inst = upInst(), feed = UP.st !== 'nofeed', chan = label(DEF['@app.updates'], globalOf('@app.updates')).toLowerCase();
  const busy = UP.st === 'checking' || ['dl', 'verify', 'install', 'perm'].includes(UP.st);
  const hero = `<section class="card" data-note="uphero"><div class="up-hero">${xlogo(46)}<div style="flex:1;min-width:0"><b>Xendroid+ v${inst.build}</b><small>${feed ? `${esc(inst.commit)} · canal ${esc(chan)} · última procura ${esc(agoText(UP.at))}` : `${esc(inst.commit)} · sem canal de atualização`}</small></div>${feed ? `<button class="btn sm" data-act="up-check" data-k="up-check"${UPD_ELSEWHERE ? ' data-note="uprepo"' : ''}${busy ? ' disabled' : ''}>${ic('refresh', 15)} ${UP.st === 'checking' ? 'Procurando…' : 'Procurar agora'}</button>` : ''}</div></section>`;
  const left = `<div class="stack">${hero}${feed ? `<div data-note="upchan">${appRows(['@app.updates'], v)}</div>` : '<p class="note">Esta versão não tem canal de atualização: as atualizações vêm de onde você a instalou.</p>'}</div>`;
  return `<div class="grid2">${left}<div class="stack">${upCard()}</div></div>`;
}
function upFinishCheck() {
  UP.at = Date.now();
  UP.st = globalOf('@app.updates') === 'off' || UP.skipped || UP.latest ? 'latest' : 'available';
}
action('up-check', () => {
  if (cooldownLeft() > 0) { UP.st = 'cooldown'; UP.fail = null; render(); return; }
  UP.st = 'checking'; UP.fail = null; render();
  setTimeout(() => { if (UP.st !== 'checking') return; upFinishCheck(); if (S.route.name === 'update') render(); }, 900);
});
action('up-skip', () => { UP.skipped = true; UP.fail = null; UP.st = 'latest'; render(); });
action('up-cancel', () => { clearInterval(UP.timer); UP.st = 'available'; render(); });
action('up-allow', () => { UP.perm = true; UP.st = 'available'; toast('No app, abre “Instalar apps desconhecidos” do Android para o Xendroid+.'); });
action('up-dl', () => {
  clearInterval(UP.timer); UP.fail = null; UP.st = 'dl'; UP.p = 0; render();
  UP.timer = setInterval(() => {
    if (S.route.name !== 'update' || UP.st !== 'dl') { clearInterval(UP.timer); return; }
    UP.p = Math.min(100, UP.p + 7);
    const b = document.getElementById('up-bar'), t = document.getElementById('up-txt');
    if (b) b.style.width = UP.p + '%'; if (t) t.textContent = `${shortSize(upBytes() * UP.p / 100)} de ${shortSize(upBytes())}`;
    if (UP.p >= 100) {
      clearInterval(UP.timer); UP.st = 'verify'; render();
      setTimeout(() => { if (UP.st !== 'verify') return; UP.st = UP.perm ? 'install' : 'perm'; if (S.route.name === 'update') render(); }, 900);
    }
  }, 140);
});
screen('update', {
  title: 'Atualizações do app', globalScope: true,
  variants: [{
    label: 'Estado',
    list: [['available', 'Atualização disponível'], ['checking', 'Procurando'], ['dl', 'Baixando'], ['perm', 'Falta a permissão'], ['install', 'Download conferido'], ['mismatch', 'SHA-256 não confere'], ['nosha', 'Sem SHA-256 publicado'], ['latest', 'Na versão mais recente'], ['cooldown', 'Em pausa'], ['nofeed', 'Build sem canal']],
    get: () => UP.nosha ? 'nosha' : UP.fail ? 'mismatch' : UP.st === 'verify' ? 'dl' : UP.st === 'latest' ? 'latest' : UP.st,
    set: v => {
      clearInterval(UP.timer); Object.assign(UP, { fail: null, nosha: false, skipped: false, latest: false, p: v === 'dl' ? 42 : 0 });
      if (v === 'mismatch') { UP.st = 'available'; UP.fail = 'O download não confere com o SHA-256 da versão publicada'; }
      else if (v === 'nosha') { UP.st = 'available'; UP.nosha = true; }
      else if (v === 'latest') { UP.st = 'latest'; UP.latest = true; }
      else if (v === 'cooldown') { UP.st = 'cooldown'; UP.at = Date.now() - 30000; }
      else UP.st = v;
    },
  }],
  render() { const i = UPD ? upInst() : null; return single({ key: 'update', title: 'Atualizações do app', sub: i ? `v${i.build} · ${esc(i.commit)}` : '', icon: 'download', body: upBody }); },
});

/* ---------- Sobre ---------- */
const AB_VER = UPD ? `v${UPD.oferecida.build} · ${UPD.oferecida.commit}` : (XDR.versoes.estavel ? XDR.versoes.estavel.commit : 'local');
/* relato do núcleo de exemplo, no formato que o About lê (CoreReport.kt) */
const AB_CPU = ['aes', 'atomics', 'bf16', 'crc32', 'dotprod', 'fp16', 'i8mm', 'sha2', 'sha3', 'sve', 'sve2'];
const AB_EXT = ['VK_KHR_swapchain', 'VK_KHR_maintenance4', 'VK_KHR_dynamic_rendering', 'VK_KHR_fragment_shader_barycentric', 'VK_KHR_fragment_shading_rate', 'VK_EXT_external_memory_host', 'VK_EXT_fragment_shader_interlock', 'VK_EXT_shader_stencil_export'];
const AB_NOTABLE = ['sve2', 'sve', 'i8mm', 'bf16', 'atomics', 'lse128', 'crc32', 'aes', 'sha3', 'fp16', 'dotprod'].filter(f => AB_CPU.includes(f));
const AB_DEVICE = [['Aparelho', 'Modelo de exemplo (Fabricante)'], ['SoC', 'SoC de exemplo'], ['GPU', DRIVERS.gpu], ['CPU', 'Cortex-X4 ×1 + Cortex-A720 ×7 · armv9.2-a'], ['Recursos da CPU', `${AB_CPU.length} · ${AB_NOTABLE.slice(0, 4).join(', ')}`], ['Vulkan', `1.3.284 · ${AB_EXT.length} extensões`], ['Android', '15 (API 35)'], ['Memória', '12 GB'], ['ABI', 'arm64-v8a']];
screen('about', {
  title: 'Sobre',
  render() {
    return single({ key: 'about', title: 'Sobre', sub: `Xendroid+ · ${esc(AB_VER)}`, icon: 'info', body: () => `<div class="stack">
      <section class="card"><div class="up-hero">${xlogo(56)}<div><b>Xendroid+</b><p class="ab-l">Criado por phforner0</p><small>Versão ${esc(AB_VER)}</small><p class="ab-l">Emulação de Xbox 360 no Android.</p></div></div></section>
      <div class="grid2">
        <section class="card" data-note="abdevice"><h3>${ic('phone', 15)} Aparelho</h3><dl class="kv" data-note="absim">${AB_DEVICE.map(([k, v]) => `<dt>${k}</dt><dd>${esc(v)}</dd>`).join('')}</dl><div class="row"><button class="btn sm" data-act="toast" data-msg="Versão e dados do aparelho copiados." data-k="ab-copy">${ic('copy', 15)} Copiar tudo</button><button class="btn sm ghost" data-act="modal" data-v="ab-report" data-k="ab-report">${ic('chip', 15)} Relato completo do núcleo</button></div></section>
        <div class="stack">
          <section class="card"><h3>${ic('spark', 15)} Atalhos</h3><ul class="menu"><li><button data-act="go" data-v="update" data-k="ab-up">${ic('download', 19)}<span>Procurar atualizações</span>${ic('chevR', 18)}</button></li><li><button data-act="go" data-v="diagnostics" data-k="ab-dg">${ic('bug', 19)}<span>Diagnóstico</span>${ic('chevR', 18)}</button></li><li><button data-act="ab-setup" data-k="ab-setup">${ic('spark', 19)}<span>Assistente de configuração</span>${ic('chevR', 18)}</button></li></ul></section>
          <section class="card"><h3>${ic('info', 15)} Créditos e licenças</h3><p style="font-size:13.5px;margin:0">Criado e mantido por phforner0. Baseado no Xenia e em outros projetos de código aberto.</p><div class="row"><button class="btn sm ghost" data-act="modal" data-v="licenses" data-k="ab-lic">Licenças de código aberto</button></div></section>
        </div>
      </div></div>` });
  },
});
action('ab-setup', () => { frReset(); go('firstrun'); });
modal('ab-report', () => ({ wide: true, html: sheetHead('Relato completo do núcleo', 'O que o núcleo do emulador lê deste aparelho: cada recurso da CPU e cada extensão Vulkan. Copie para um relato de problema.') + `<dl class="kv"><dt>CPU</dt><dd>Cortex-X4 ×1 + Cortex-A720 ×7 · armv9.2-a</dd><dt>GPU</dt><dd>${esc(DRIVERS.gpu)} · Vulkan 1.3.284</dd></dl>
  <h3 class="ab-h">Recursos da CPU (${AB_CPU.length})</h3><div class="ab-chips">${AB_CPU.map(x => `<code>${x}</code>`).join('')}</div>
  <h3 class="ab-h">Extensões Vulkan (${AB_EXT.length})</h3><div class="ab-chips">${AB_EXT.map(x => `<code>${x}</code>`).join('')}</div>
  <p class="note">Lista curta de exemplo; no app vêm todos os recursos e extensões que o núcleo lê.</p>
  <div class="acts"><button class="btn ghost" data-act="toast" data-msg="Relato do núcleo copiado." data-k="rep-copy">${ic('copy', 16)} Copiar</button><button class="btn primary" data-act="close" data-k="rep-ok" data-autofocus>OK</button></div>` }));
modal('licenses', () => ({ wide: true, html: sheetHead('Licenças') + `<div class="lic" data-note="ablic"><p>The <b>xendroid</b> uses the following open source projects:</p><ul>${LIC.projetos.map(p => `<li>${p.url ? `<a href="${esc(p.url)}" target="_blank" rel="noopener">${esc(p.n)}</a>` : esc(p.n)}</li>`).join('')}</ul>${LIC.motores.length ? `<h3>Host-side presentation engines</h3><ul>${LIC.motores.map(m => `<li>${esc(m)}</li>`).join('')}</ul>` : ''}${LIC.notas.map(n => `<p>${esc(n)}</p>`).join('')}</div><div class="acts"><button class="btn primary" data-act="close" data-k="lic-ok" data-autofocus>OK</button></div>` }));
