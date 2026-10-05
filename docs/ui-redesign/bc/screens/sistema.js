/* Lote 6: Primeira abertura, pastas de jogos, navegador de pastas, jogos que saíram, sem Vulkan, atualizador e Sobre. */
'use strict';

Object.assign(NOTES, {
  frsteps: ['existe', 'As mesmas verificações e escolhas do assistente de hoje (L01), agora em passos com Pular sempre à mão.'],
  frchecks: ['existe', 'Verificações do telefone: GPU Vulkan, ARM de 64 bits, versão do Android e pasta de jogos.'],
  frscan: ['novo', 'Escolher a pasta mostra na hora quantos jogos ela tem, com as capas.'],
  frprofile: ['novo', 'Criar o perfil aqui mesmo, em vez de só apontar para Perfis.'],
  frmode: ['novo', 'Modo controle e quantos ajustes mostrar já na primeira abertura (hoje: Jogador ou Desenvolvedor).'],
  fdlist: ['existe', 'Pastas de jogos: para onde vão as instalações, as indisponíveis agora, remover sem mexer nos arquivos.'],
  fdscan: ['novo', 'Quantos jogos cada pasta tem e quando foi a última busca, com Procurar de novo.'],
  brcount: ['novo', 'O navegador mostra quantos jogos há em cada pasta antes de escolher.'],
  brroots: ['existe', 'Armazenamento interno e cartão SD, subir, abrir e usar esta pasta.'],
  miss: ['existe', 'Jogos que saíram da biblioteca: o motivo, o tempo jogado e remover só da lista (nada é apagado).'],
  missfind: ['novo', 'Procurar o arquivo de novo ou apontar onde ele está agora.'],
  nvcheck: ['existe', 'Sem GPU Vulkan o emulador não roda; hoje é um diálogo com Sair.'],
  nvcopy: ['novo', 'Copiar os dados do aparelho para pedir ajuda.'],
  upstate: ['existe', 'Atualização disponível com a lista de mudanças, conferida com o SHA-256 publicado antes de instalar; pular esta versão ou depois.'],
  upsteps: ['novo', 'Baixar, conferir e instalar em etapas visíveis, com os erros em uma frase.'],
  abdevice: ['existe', 'Versão, aparelho, créditos e licenças.'],
  abcopy: ['novo', 'Copiar tudo de uma vez para um relato de problema.'],
});

/* ---------- primeira abertura ---------- */
const FR = { step: 0, folder: null, scan: 0, found: 0, locale: false, tag: '', made: false, old: false, timer: 0 };
const FR_STEPS = [['phone', 'Este telefone'], ['games', 'Seus jogos'], ['locale', 'Idioma e região'], ['profile', 'Perfil'], ['use', 'Como usar']];
function frChecks() {
  return [
    ['ok', 'GPU Vulkan', 'Adreno 825 · Vulkan 1.3'],
    ['ok', 'ARM de 64 bits', 'arm64-v8a'],
    FR.old ? ['warn', 'API 29', 'Escolher uma pasta de jogos exige Android 11 ou mais novo; jogos abertos por um frontend continuam funcionando.'] : ['ok', 'API 35', 'Android 15'],
    FR.old ? ['warn', 'Pasta de jogos', 'Exige Android 11 ou mais novo (acesso a todos os arquivos).'] : FR.folder ? ['ok', 'Pasta de jogos', 'Escolhida; a biblioteca procura nela.'] : ['warn', 'Pasta de jogos', 'Ainda não escolhida: escolha a pasta onde estão seus jogos.'],
  ];
}
const chkList = l => `<ul class="chk-l">${l.map(([k, t, s]) => `<li class="${k}">${ic(k === 'ok' ? 'checkC' : k === 'bad' ? 'xC' : 'alert', 22)}<div><b>${esc(t)}</b><small>${esc(s)}</small></div></li>`).join('')}</ul>`;
function frBody(v) {
  const s = FR_STEPS[FR.step][0];
  if (s === 'phone') return `<h2>Boas-vindas ao XenDroid</h2><p class="note">Algumas verificações e escolhas. Tudo pode ser mudado depois, e nada daqui é enviado a lugar nenhum.</p><div data-note="frchecks">${chkList(frChecks())}</div>`;
  if (s === 'games') {
    if (FR.old) return `<h2>Seus jogos</h2><p class="warnline">${ic('alert', 16)} Escolher uma pasta de jogos exige Android 11 ou mais novo. Jogos abertos por um frontend continuam funcionando.</p>`;
    return `<h2>Seus jogos</h2><p class="note">Escolha a pasta onde estão seus jogos (ISO, XEX, ZAR ou pacotes). Dá para adicionar mais pastas depois.</p>
      ${FR.folder ? `<section class="card" data-note="frscan"><div class="row"><b class="mono" style="font-size:13px">${esc(FR.folder)}</b><span class="sp"></span><button class="btn sm ghost" data-act="fr-folder" data-k="fr-folder2">Trocar</button></div>${FR.scan < 100 ? `<div class="bar"><i style="width:${FR.scan}%"></i></div><small class="note">Procurando jogos… ${FR.found} encontrados</small>` : `<p class="okline">${ic('checkC', 15)} ${FR.found} jogos encontrados</p><div class="strip">${GAMES.slice(0, 9).map(g => coverHTML(g)).join('')}</div>`}</section>` : `<button class="btn primary lg" data-act="fr-folder" data-k="fr-folder" data-autofocus>${ic('folder', 18)} Escolher pasta de jogos</button>`}`;
  }
  if (s === 'locale') return `<h2>Idioma e região nos jogos</h2><section class="card"><p style="font-size:14px">Deste telefone: idioma Português · região Brasil</p>${FR.locale ? `<p class="okline">${ic('checkC', 15)} Os jogos vão usá-los a partir da próxima abertura.</p>` : `<div class="row"><button class="btn primary" data-act="fr-locale" data-k="fr-locale" data-autofocus>Usar nos jogos</button><span class="note">Hoje os jogos veem inglês e Estados Unidos.</span></div>`}</section><p class="note">Muda depois em Configurações → Console e sistema, ou por perfil.</p>`;
  if (s === 'profile') return `<h2>Perfil</h2><p class="note">Os jogos salvam num perfil (gamertag). Um chamado “XenDroid” é criado quando não existe nenhum; crie ou renomeie o seu em Perfis.</p>
    <section class="card" data-note="frprofile">${FR.made ? `<div class="row">${avatarImg(PROFILES[PROFILES.length - 1], 'avatar-l')}<div><b style="font:700 20px/1 var(--f-disp)">${esc(FR.tag)}</b><p class="okline" style="margin-top:6px">${ic('checkC', 15)} Perfil criado e ativo (P1).</p></div></div>` : `<label class="fld">Gamertag<input class="txt plain" id="fr-tag" type="text" maxlength="15" placeholder="Ex.: XenPlayer" data-k="fr-tag" autocomplete="off" spellcheck="false" value="${esc(FR.tag)}"></label><p class="errline" id="fr-err" hidden>${ic('warn', 15)} 1 a 15 letras ou dígitos, começando com uma letra.</p><div class="row"><button class="btn primary" data-act="fr-profile" data-k="fr-profile">Criar perfil</button><button class="btn ghost" data-act="go" data-v="profiles" data-k="fr-prof-open">Abrir Perfis</button></div>`}</section>`;
  return `<h2>Como usar</h2><div data-note="frmode">${appRows(['@app.mode', '@app.level'], v)}</div>
    <section class="card"><h3>${ic('chip', 15)} Driver da GPU (opcional)</h3><p style="font-size:13.5px">O driver Vulkan do próprio telefone basta para começar. Um pacote Turnip pode ser instalado depois em Drivers, e trocado de volta quando quiser.</p></section>`;
}
action('fr-step', el => { FR.step = clamp(Number(el.dataset.v), 0, FR_STEPS.length - 1); render(); });
action('fr-next', () => { if (FR.step === FR_STEPS.length - 1) { ACT['fr-done'](); return; } FR.step++; render(); });
action('fr-prev', () => { if (FR.step) { FR.step--; render(); } });
action('fr-done', () => { toast('Pronto. O assistente fica em Configurações → Diagnóstico e testes.'); goTop('library'); });
action('fr-folder', () => {
  clearInterval(FR.timer); FR.folder = '/storage/emulated/0/Games/Xbox 360'; FR.scan = 0; FR.found = 0; render();
  FR.timer = setInterval(() => { FR.scan = Math.min(100, FR.scan + 12); FR.found = Math.round(GAMES.length * FR.scan / 100); if (S.route.name !== 'firstrun') { clearInterval(FR.timer); return; } render(); if (FR.scan >= 100) clearInterval(FR.timer); }, 160);
});
action('fr-locale', () => { FR.locale = true; GLOBAL['Console.user_language'] = '9'; GLOBAL['Console.user_country'] = '13'; render(); });
action('fr-profile', () => { const v = ((document.getElementById('fr-tag') || {}).value || '').trim(); if (!/^[A-Za-z][A-Za-z0-9 ]{0,14}$/.test(v)) { const e = document.getElementById('fr-err'); if (e) e.hidden = false; return; } FR.tag = v; FR.made = true; const p = { xuid: newXuid(), tag: v, c: ['#2f8f4e', '#0f3d22'], lang: 'Português', region: 'Brasil', active: false, slot: null, games: 0, files: 0, mb: 0 }; p.avatar = drawAvatar(p).toDataURL('image/png'); PROFILES.push(p); ACT['pf-active']({ dataset: { v: p.xuid } }); });
action('input:fr-tag', el => { FR.tag = el.value; });
screen('firstrun', {
  title: 'Primeira abertura', lote: 'Lote 6 · Primeira abertura e app',
  info: {
    what: 'O assistente de primeira abertura em cinco passos: o telefone, a pasta de jogos (com a busca acontecendo ali), idioma e região dos jogos, o perfil e como usar o app. Pular e Voltar ficam sempre à mão.',
    replaces: 'FirstRunAssistant.kt (L01): uma página com verificações, pasta de jogos, idioma e região, perfil, driver e Jogador/Desenvolvedor, mostrada uma vez e reaberta pelo menu.',
    changes: ['Passos com progresso em vez de uma página longa', 'Criar o perfil ali mesmo', 'Quantos jogos a pasta tem, com as capas, antes de seguir', 'Modo controle e nível de ajustes escolhidos já aqui'],
    c: 'Igual, com o direcional; A continua, B volta um passo.',
    code: 'ui/library/FirstRunAssistant.kt, FirstRun.kt, FolderBrowserScreen.kt',
  },
  variants: [{ label: 'Aparelho', list: [['ok', 'Android 15'], ['old', 'Android 10 (sem pasta)']], get: () => FR.old ? 'old' : 'ok', set: v => { FR.old = v === 'old'; } }],
  render() {
    const last = FR.step === FR_STEPS.length - 1;
    return `<div class="fr"><aside class="fr-side" data-note="frsteps"><span class="logo"></span><h1>XenDroid</h1><p class="note">Emulação de Xbox 360 no Android.</p>
      <ol class="fr-steps">${FR_STEPS.map(([id, t], i) => `<li><button class="${i < FR.step ? 'done' : ''}" data-act="fr-step" data-v="${i}" data-k="frs-${id}"${i === FR.step ? ' aria-current="step"' : ''}><span class="d">${i < FR.step ? ic('check', 13) : i + 1}</span><span class="t">${t}</span></button></li>`).join('')}</ol></aside>
      <div class="fr-main"><div class="fr-body" data-sk="fr-${FR.step}">${frBody(isC() ? 'c' : 'b')}</div>
      <div class="fr-foot"><button class="btn ghost" data-act="fr-done" data-k="fr-skip">Pular</button><span class="sp"></span>${FR.step ? `<button class="btn" data-act="fr-prev" data-k="fr-prev">Voltar</button>` : ''}<button class="btn primary" data-act="fr-next" data-k="fr-next"${FR.step === 1 || FR.step === 3 ? '' : ' data-autofocus'}>${last ? 'Começar' : 'Continuar'}</button></div></div></div>`;
  },
  onBack() { if (FR.step) { FR.step--; render(); return true; } return false; },
});

/* ---------- pastas de jogos ---------- */
const FOLDERS = [
  { path: '/storage/emulated/0/Games/Xbox 360', games: 11, ok: true, install: true },
  { path: 'Cartão SD › Jogos', games: 3, ok: false },
  { path: '/storage/emulated/0/Download', games: 0, ok: true },
];
const FD = { last: 'hoje, 12:40', scanning: false };
function fdBody() {
  return `<div class="stack"><div class="row"><span class="note" data-note="fdscan">Última busca: ${esc(FD.last)} · ${FOLDERS.reduce((s, f) => s + f.games, 0)} jogos em ${FOLDERS.length} pastas</span><span class="sp"></span><button class="btn sm ghost" data-act="fd-scan" data-k="fd-scan"${FD.scanning ? ' disabled' : ''}>${ic('refresh', 15)} ${FD.scanning ? 'Procurando…' : 'Procurar de novo'}</button><button class="btn sm primary" data-act="go" data-v="browse" data-k="fd-add">${ic('plus', 15)} Adicionar pasta</button></div>
    <section class="card" data-note="fdlist">${FOLDERS.length ? `<div class="list">${FOLDERS.map((f, i) => `<div class="fd ${f.ok ? '' : 'away'}">${ic('folder', 24)}<div><b>${esc(f.path)}${f.install ? ' <span class="badge acc">instalações vão para cá</span>' : ''}${f.ok ? '' : ' <span class="badge warn">indisponível agora</span>'}</b><small>${f.ok ? `${f.games} ${f.games === 1 ? 'jogo' : 'jogos'}` : `${f.games} jogos ficam fora da biblioteca até a pasta voltar (cartão SD ou permissão?)`}</small></div><div class="row">${f.install || !f.ok ? '' : `<button class="btn sm ghost" data-act="fd-inst" data-v="${i}" data-k="fd-i${i}">Instalar aqui</button>`}<button class="btn sm ghost" data-act="fd-rm" data-v="${i}" data-k="fd-r${i}">Remover</button></div></div>`).join('')}</div>` : '<p class="note">Nenhuma pasta ainda.</p>'}</section>
    <p class="note">Remover uma pasta só para de procurar nela; os arquivos ficam onde estão. Uma pasta dentro de outra é procurada uma vez só.</p></div>`;
}
action('fd-scan', () => { FD.scanning = true; render(); setTimeout(() => { FD.scanning = false; FD.last = 'agora'; if (S.route.name === 'folders') toast(`${FOLDERS.reduce((s, f) => s + (f.ok ? f.games : 0), 0)} jogos nas pastas disponíveis`); else render(); }, 1200); });
action('fd-rm', el => { const i = Number(el.dataset.v), f = FOLDERS.splice(i, 1)[0]; UNDO = { folder: f, i }; toast(`Paramos de procurar em ${f.path}; os arquivos ficam.`, true); });
action('fd-inst', el => { FOLDERS.forEach((f, i) => { f.install = i === Number(el.dataset.v); }); toast('Instalações vão para esta pasta'); });
const undoFolders = ACT.undo;
action('undo', el => { if (UNDO && UNDO.folder) { FOLDERS.splice(UNDO.i, 0, UNDO.folder); UNDO = null; S.toast = null; render(); return; } undoFolders(el); });
screen('folders', {
  title: 'Pastas de jogos', lote: 'Lote 6 · Primeira abertura e app',
  info: {
    what: 'As pastas onde a biblioteca procura jogos, com quantos jogos cada uma tem, qual recebe as instalações e quais estão indisponíveis agora; adicionar pelo navegador de pastas e procurar de novo.',
    replaces: 'GameFoldersDialog.kt: lista de pastas com “instalações vão para cá”, “indisponível agora”, remover e adicionar.',
    changes: ['Tela própria em vez de diálogo', 'Quantos jogos cada pasta tem e a última busca', 'Proposta: escolher a pasta que recebe as instalações'],
    c: 'Igual, com foco nas linhas.',
    code: 'ui/library/GameFoldersDialog.kt, FolderBrowserScreen.kt, library/GameFolders.kt',
  },
  render() { return single({ key: 'folders', title: 'Pastas de jogos', sub: `${FOLDERS.length} pastas`, icon: 'folder', body: fdBody }); },
});

/* ---------- navegador de pastas ---------- */
const FS = {
  'Armazenamento interno': { Android: {}, DCIM: { Camera: {} }, Download: { $files: [['Halo 3 - Mythic Map Pack.con', '880 MB'], ['TU13_4D5307E6.live', '31 MB'], ['saves-halo3.zip', '3 MB']] }, Games: { 'Xbox 360': { $iso: 11 }, PS2: {}, Switch: {} }, Music: {}, Pictures: {} },
  'Cartão SD': { Jogos: { $iso: 3, Arcade: { $iso: 2 } } },
};
const BR = { path: ['Armazenamento interno', 'Games'], pick: null, mode: 'folder' };
const brNode = p => p.reduce((n, k) => n && n[k], FS);
const brCount = n => !n || typeof n !== 'object' ? 0 : (n.$iso || 0) + Object.keys(n).filter(k => k[0] !== '$').reduce((s, k) => s + brCount(n[k]), 0);
function brBody() {
  const n = brNode(BR.path) || {}, dirs = Object.keys(n).filter(k => k[0] !== '$'), files = n.$files || [], here = brCount(n);
  return `<div class="stack"><div class="row" data-note="brroots">${Object.keys(FS).map(r => `<button class="chip" data-act="br-root" data-v="${esc(r)}" aria-pressed="${BR.path[0] === r}" data-k="brr-${hash(r)}">${ic(r === 'Cartão SD' ? 'sd' : 'phone', 15)} ${esc(r)}</button>`).join('')}</div>
    <nav class="br-path" aria-label="Caminho">${BR.path.map((p, i) => `${i ? ic('chevR', 14) : ''}<button data-act="br-to" data-v="${i}" data-k="brp-${i}">${esc(p)}</button>`).join('')}</nav>
    <section class="card"><div class="br-list">${BR.path.length > 1 ? `<button class="br-row" data-act="br-up" data-k="br-up">${ic('back', 20)}<b>Subir</b><span></span></button>` : ''}
      ${dirs.map((d, i) => { const c = brCount(n[d]); return `<button class="br-row dir" data-act="br-open" data-v="${esc(d)}" data-k="brd-${i}"${i === 0 ? ' data-autofocus' : ''}>${ic('folder', 20)}<b>${esc(d)}</b>${c ? `<span class="badge acc" data-note="brcount">${c} ${c === 1 ? 'jogo' : 'jogos'}</span>` : '<span></span>'}</button>`; }).join('')}
      ${BR.mode === 'file' ? files.map((f, i) => `<button class="br-row" data-act="br-pick" data-v="${i}" aria-pressed="${BR.pick === i}" data-k="brf-${i}">${ic('box', 20)}<b>${esc(f[0])}</b><span class="note">${f[1]}</span></button>`).join('') : ''}
      ${!dirs.length && !(BR.mode === 'file' && files.length) ? `<p class="note" style="padding:10px">${n.$iso ? 'Nenhuma subpasta aqui. Use “Usar esta pasta” para escolher esta pasta.' : 'Nada aqui. Suba e escolha outra pasta.'}</p>` : ''}</div></section>
    <div class="br-foot"><span class="note">${here ? `${here} ${here === 1 ? 'jogo' : 'jogos'} nesta pasta e nas de dentro` : 'Nenhum jogo de Xbox 360 aqui'}</span><span class="sp"></span>${BR.mode === 'file' ? `<button class="btn primary" data-act="br-install" data-k="br-inst"${BR.pick == null ? ' disabled' : ''}>Instalar</button>` : `<button class="btn primary" data-act="br-use" data-k="br-use">${ic('check', 16)} Usar esta pasta</button>`}</div></div>`;
}
action('br-root', el => { BR.path = [el.dataset.v]; BR.pick = null; render(); });
action('br-to', el => { BR.path = BR.path.slice(0, Number(el.dataset.v) + 1); BR.pick = null; render(); });
action('br-up', () => { if (BR.path.length > 1) BR.path.pop(); BR.pick = null; render(); });
action('br-open', el => { BR.path.push(el.dataset.v); BR.pick = null; render(); });
action('br-pick', el => { BR.pick = Number(el.dataset.v); render(); });
action('br-use', () => { const p = BR.path.join(' › '); if (!FOLDERS.some(f => f.path === p)) FOLDERS.push({ path: p, games: brCount(brNode(BR.path)), ok: true }); toast(`Pasta adicionada: ${p}`); back(); });
action('br-install', () => { toast('Instalando… (veja Conteúdo → Instalar)'); back(); });
screen('browse', {
  title: 'Navegador de pastas', lote: 'Lote 6 · Primeira abertura e app',
  info: {
    what: 'O navegador de pastas do app, para escolher a pasta de jogos ou o arquivo de um pacote: armazenamento interno e cartão SD, o caminho clicável, quantos jogos há em cada pasta e Usar esta pasta.',
    replaces: 'FolderBrowserScreen.kt: Armazenamento, Subir, lista de subpastas, Abrir e Usar esta pasta.',
    changes: ['Quantos jogos cada pasta tem, antes de escolher', 'Caminho clicável para voltar vários níveis'],
    c: 'O direcional percorre as pastas; A abre; B sobe.',
    code: 'ui/library/FolderBrowserScreen.kt',
  },
  variants: [{ label: 'Para', list: [['folder', 'Escolher pasta de jogos'], ['file', 'Escolher arquivo de pacote']], get: () => BR.mode, set: v => { BR.mode = v; BR.pick = null; BR.path = v === 'file' ? ['Armazenamento interno', 'Download'] : ['Armazenamento interno', 'Games']; } }],
  render() { return single({ key: 'browse', title: BR.mode === 'file' ? 'Escolher o arquivo do pacote' : 'Escolher pasta de jogos', sub: BR.path.join(' › '), icon: 'folder', body: brBody }); },
  onBack() { if (BR.path.length > 1) { BR.path.pop(); BR.pick = null; render(); return true; } return false; },
});

/* ---------- jogos que saíram ---------- */
const MISSING = [
  { id: '4D5307D5', name: 'Mass Effect', pal: ['#0a1426', '#2a5aa8', '#e0e8ff'], motif: 'shards', why: 'gone', time: '14 h', n: 22, path: '/storage/emulated/0/Games/Xbox 360/Mass Effect.iso' },
  { id: '5454085C', name: 'BioShock', pal: ['#0d1a14', '#1f6b5a', '#f2c46a'], motif: 'bands', why: 'away', time: '3 h', n: 5, path: 'Cartão SD › Jogos/BioShock.iso' },
  { id: '41560817', name: 'Crackdown', pal: ['#140a1a', '#7a2a9a', '#9be37f'], motif: 'grid', why: 'outside', time: '50 min', n: 2, path: '/storage/emulated/0/Download/Crackdown.zar' },
];
const MISS_WHY = { gone: 'Arquivo não encontrado: movido, renomeado ou apagado', away: 'A pasta de jogos dele está indisponível agora', outside: 'O arquivo está fora das suas pastas de jogos' };
function missBody() {
  for (const m of MISSING) if (!m.cover) m.cover = drawCover(m).toDataURL('image/jpeg', .85);
  if (!MISSING.length) return '<p class="note">Todo jogo que você jogou está na biblioteca.</p>';
  return `<div class="stack"><section class="card" data-note="miss"><div class="list">${MISSING.map((m, i) => drow({ img: `<span style="width:40px;display:block">${coverHTML(m)}</span>`, t: esc(m.name), s: `${esc(MISS_WHY[m.why])}<br>Jogado ${m.time} em ${m.n} ${m.n === 1 ? 'sessão' : 'sessões'} · Title ID ${m.id}<br><span class="path">${esc(m.path)}</span>`, acts: `${m.why === 'outside' ? `<button class="btn sm" data-act="miss-add" data-v="${i}" data-k="ms-a${i}" data-note="missfind">Adicionar a pasta</button>` : m.why === 'gone' ? `<button class="btn sm" data-act="miss-find" data-v="${i}" data-k="ms-f${i}" data-note="missfind">Onde ele está agora?</button>` : ''}<button class="btn sm ghost" data-act="miss-rm" data-v="${i}" data-k="ms-r${i}">Remover da lista</button>` })).join('')}</div></section>
    <p class="note">Remover só oculta o jogo aqui: tempo de jogo, notas de compatibilidade, saves e capa ficam, e ele volta quando o arquivo for encontrado de novo. Nenhum arquivo é mexido.</p></div>`;
}
action('miss-rm', el => { const m = MISSING.splice(Number(el.dataset.v), 1)[0]; toast(`${m.name} saiu da lista; volta quando o arquivo aparecer.`); });
action('miss-find', () => { BR.mode = 'file'; go('browse'); });
action('miss-add', el => { const m = MISSING.splice(Number(el.dataset.v), 1)[0]; FOLDERS.push({ path: '/storage/emulated/0/Download', games: 1, ok: true }); toast(`${m.name} voltou à biblioteca`); });
screen('missing', {
  title: 'Jogos que saíram', lote: 'Lote 6 · Primeira abertura e app',
  info: {
    what: 'Os jogos que você jogou e cujo arquivo a biblioteca não acha mais, com o motivo, quanto você jogou e o caminho de antes; remover só da lista, ou apontar onde o arquivo está agora.',
    replaces: 'MissingGamesDialog.kt: lista com o motivo, “Jogado … em N sessões · Title ID” e remover.',
    changes: ['Capa guardada e o caminho de antes', 'Proposta: procurar o arquivo de novo ou adicionar a pasta onde ele está'],
    c: 'Igual, com foco nas linhas.',
    code: 'ui/library/MissingGamesDialog.kt, library/MissingTitles.kt',
  },
  render() { return single({ key: 'missing', title: 'Jogos que saíram da biblioteca', sub: `${MISSING.length} jogos`, icon: 'inbox', body: missBody }); },
});

/* ---------- sem Vulkan ---------- */
screen('novulkan', {
  title: 'Sem Vulkan', lote: 'Lote 6 · Primeira abertura e app',
  info: {
    what: 'Quando o aparelho não tem GPU Vulkan, uma tela clara do porquê, o que foi verificado e os dados do aparelho para pedir ajuda.',
    replaces: 'NoVulkanDialog em GameLibraryScreen.kt: “Este aparelho não tem GPU Vulkan; o emulador não pode rodar.” com Sair.',
    changes: ['As verificações do aparelho à vista', 'Copiar os dados do aparelho'],
    c: 'Igual; A sai.',
    code: 'ui/library/GameLibraryScreen.kt (NoVulkanDialog), FirstRun.kt',
  },
  render() {
    return `<div class="nv"><section class="card"><h1>${ic('chip', 30)} Este aparelho não tem GPU Vulkan</h1><p style="margin:0;font-size:14px;color:var(--fg2)">O emulador não pode rodar: nenhum dispositivo Vulkan foi encontrado, então os jogos não rodam neste telefone.</p>
      <div data-note="nvcheck">${chkList([['bad', 'GPU Vulkan', 'Nenhum dispositivo Vulkan encontrado: os jogos não rodam neste telefone.'], ['ok', 'ARM de 64 bits', 'arm64-v8a'], ['ok', 'API 30', 'Android 11']])}</div>
      <div class="row"><button class="btn primary" data-act="toast" data-msg="O app fecharia aqui (protótipo)." data-k="nv-quit" data-autofocus>Sair</button><button class="btn ghost" data-act="toast" data-msg="Dados do aparelho copiados." data-k="nv-copy" data-note="nvcopy">${ic('copy', 15)} Copiar os dados do aparelho</button></div>
      <p class="note">Nada foi apagado; se o aparelho ganhar suporte a Vulkan numa atualização do Android, o app volta a funcionar.</p></section></div>`;
  },
});

/* ---------- atualizador ---------- */
const UP = { st: 'available', p: 0, timer: 0 };
const UP_REL = { v: 'v413', sha: '8c1e2f0', size: '31,4 MB', date: '03/10/2026', notes: ['Biblioteca: capas maiores e o carrossel no modo controle', 'Menu em jogo: limite de FPS com salvar para o jogo numa linha', 'Drivers: download conferido pelo SHA-256 com progresso', 'Correção: travamento ao restaurar saves com o jogo aberto'] };
function upCard() {
  const r = UP_REL, st = UP.st;
  if (st === 'latest') return `<section class="card"><p class="okline" style="font-size:14px">${ic('checkC', 16)} Você está na versão mais recente: v412</p></section>`;
  if (st === 'cooldown') return `<section class="card"><h3>${ic('clock', 15)} Atualizador em pausa</h3><p style="font-size:13.5px">Você pode procurar de novo em 4 min 30 s.</p></section>`;
  const head = `<div class="row"><b style="font:700 20px/1 var(--f-disp)">${r.v}</b>${st === 'preview' ? '<span class="badge warn">prévia</span>' : ''}<span class="badge">${r.sha}</span><span class="note">${r.size} · ${r.date}</span></div>`;
  const notes = `<ul class="clog">${r.notes.map(n => `<li>${esc(n)}</li>`).join('')}</ul>`;
  if (st === 'available' || st === 'preview') return `<section class="card" data-note="upstate"><h3>${ic('download', 15)} Atualização disponível</h3>${head}${notes}<p class="${st === 'preview' ? 'warnline' : 'okline'}">${ic(st === 'preview' ? 'alert' : 'shield', 15)} ${st === 'preview' ? 'Sem SHA-256 publicado: abra a página da versão para instalar você mesmo.' : 'Conferida com o SHA-256 publicado antes de instalar.'}</p><div class="row">${st === 'preview' ? '' : `<button class="btn primary" data-act="up-dl" data-k="up-dl" data-autofocus>${ic('download', 16)} Baixar e instalar</button>`}<button class="btn${st === 'preview' ? ' primary' : ' ghost'}" data-act="toast" data-msg="Abrindo a página da versão (protótipo)." data-k="up-page">Página da versão</button><button class="btn ghost" data-act="up-skip" data-k="up-skip">Pular esta versão</button><button class="btn ghost" data-act="close" data-k="up-later">Depois</button></div></section>`;
  const steps = [['Baixar', st === 'dl' ? 'now' : 'done'], ['Conferir o SHA-256', st === 'verify' ? 'now' : st === 'dl' ? '' : st === 'mismatch' ? 'bad' : 'done'], ['Instalar', st === 'install' || st === 'perm' ? 'now' : '']];
  return `<section class="card" data-note="upsteps"><h3>${ic('download', 15)} ${r.v}</h3>${head}<ol class="steps">${steps.map(([t, k]) => `<li class="${k === 'done' ? 'done' : k === 'now' ? 'now' : ''}">${k === 'done' ? ic('checkC', 20) : k === 'now' ? `<span class="spin" style="display:inline-grid">${ic('refresh', 20)}</span>` : k === 'bad' ? ic('xC', 20) : ic('clock', 20)}<span>${t}</span><time></time></li>`).join('')}</ol>
    ${st === 'dl' ? `<div class="bar"><i id="up-bar" style="width:${UP.p}%"></i></div><div class="row"><span class="note" id="up-txt">${(UP.p * .314).toFixed(1).replace('.', ',')} de 31,4 MB</span><span class="sp"></span><button class="btn sm ghost" data-act="up-cancel" data-k="up-cancel">Cancelar</button></div>` : ''}
    ${st === 'perm' ? `<p class="warnline">${ic('alert', 15)} Permita que o XenDroid instale atualizações e toque de novo em Baixar e instalar.</p><div class="row"><button class="btn primary" data-act="toast" data-msg="Abrindo a permissão do Android (protótipo)." data-k="up-perm">Abrir a permissão</button></div>` : ''}
    ${st === 'install' ? `<p class="okline">${ic('shield', 15)} Download conferido. O instalador do Android pede a confirmação.</p>` : ''}
    ${st === 'mismatch' ? `<p class="errline">${ic('alert', 15)} Atualização não instalada: o download não confere com o SHA-256 da versão publicada.</p><div class="row"><button class="btn" data-act="up-dl" data-k="up-retry">Tentar de novo</button><button class="btn ghost" data-act="toast" data-msg="Abrindo a página da versão (protótipo)." data-k="up-page2">Página da versão</button></div>` : ''}</section>`;
}
function upBody(v) {
  return `<div class="stack"><div class="up-hero"><span class="app-logo">X</span><div><b>XenDroid v412</b><small>7bb3409 · canal ${esc(label(DEF['@app.updates'], globalOf('@app.updates')).toLowerCase())} · última procura hoje, 09:12</small></div><span class="sp"></span><button class="btn sm" data-act="up-check" data-k="up-check">${ic('refresh', 15)} Procurar agora</button></div>
    ${appRows(['@app.updates'], v)}${upCard()}</div>`;
}
action('up-check', () => { UP.st = globalOf('@app.updates') === 'preview' ? 'preview' : 'available'; toast('Procurando…'); });
action('up-skip', () => { UP.st = 'latest'; toast('Esta versão não será oferecida de novo'); });
action('up-cancel', () => { clearInterval(UP.timer); UP.st = 'available'; toast('Download cancelado'); });
action('up-dl', () => {
  clearInterval(UP.timer); UP.st = 'dl'; UP.p = 0; render();
  UP.timer = setInterval(() => {
    if (S.route.name !== 'update') { clearInterval(UP.timer); return; }
    UP.p = Math.min(100, UP.p + 9); const b = document.getElementById('up-bar'), t = document.getElementById('up-txt'); if (b) b.style.width = UP.p + '%'; if (t) t.textContent = `${(UP.p * .314).toFixed(1).replace('.', ',')} de 31,4 MB`;
    if (UP.p >= 100) { clearInterval(UP.timer); UP.st = 'verify'; render(); setTimeout(() => { if (UP.st === 'verify') { UP.st = 'install'; render(); } }, 900); }
  }, 150);
});
screen('update', {
  title: 'Atualizador', lote: 'Lote 6 · Primeira abertura e app', globalScope: true,
  info: {
    what: 'O canal de atualização, a versão instalada e o estado da procura: atualização disponível com a lista de mudanças, as etapas de baixar, conferir o SHA-256 e instalar, e os erros numa frase.',
    replaces: 'O diálogo de atualização (upd_*): disponível, prévia, sem SHA-256, baixar e instalar, página da versão, pular, depois, erros e o atualizador em pausa.',
    changes: ['Etapas visíveis: baixar, conferir, instalar', 'Canal e versão instalada na mesma tela'],
    c: 'Igual; A baixa e instala.',
    code: 'update/* (UpdateChecker, ApkVerifier), ui/settings/UpdateChannelSection.kt',
  },
  variants: [{ label: 'Estado', list: [['available', 'Disponível'], ['preview', 'Prévia sem SHA-256'], ['dl', 'Baixando'], ['install', 'Pronta para instalar'], ['perm', 'Falta permissão'], ['mismatch', 'SHA-256 não confere'], ['latest', 'Mais recente'], ['cooldown', 'Em pausa']], get: () => UP.st === 'verify' ? 'dl' : UP.st, set: v => { clearInterval(UP.timer); UP.st = v; UP.p = v === 'dl' ? 42 : 0; } }],
  render() { return single({ key: 'update', title: 'Atualizações do app', sub: 'v412 · 7bb3409', icon: 'download', body: upBody }); },
});

/* ---------- Sobre ---------- */
const DEVICE = [['Aparelho', 'POCO F7 (Xiaomi)'], ['SoC', 'Snapdragon 8s Gen 4'], ['GPU', 'Adreno 825'], ['Driver do sistema', 'Qualcomm 819.0'], ['Vulkan', '1.3.284'], ['Android', '15 (API 35)'], ['Memória', '12 GB'], ['ABI', 'arm64-v8a']];
const LICENSES = [['Xenia', 'BSD-3-Clause'], ['Turnip (Mesa)', 'MIT'], ['Kotlin e Jetpack Compose', 'Apache-2.0'], ['libadrenotools', 'BSD-2-Clause'], ['FFmpeg', 'LGPL-2.1'], ['xxHash', 'BSD-2-Clause'], ['Barlow (fonte)', 'OFL-1.1']];
screen('about', {
  title: 'Sobre', lote: 'Lote 6 · Primeira abertura e app',
  info: {
    what: 'Versão, canal e build; os dados do aparelho para um relato de problema, com copiar tudo; créditos e licenças; atalhos para atualizações, diagnóstico e o assistente de configuração.',
    replaces: 'AboutScreen.kt: nome, versão, créditos, aparelho em texto selecionável e o botão de licenças.',
    changes: ['Aparelho em tabela, com copiar tudo', 'Atalhos para atualizações, diagnóstico e o assistente'],
    c: 'Igual, com foco nos botões.',
    code: 'ui/about/AboutScreen.kt',
  },
  render() {
    return single({ key: 'about', title: 'Sobre', sub: 'XenDroid v412', icon: 'info', body: () => `<div class="grid2">
      <section class="card span2"><div class="up-hero"><span class="app-logo">X</span><div><b>XenDroid</b><small>Versão v412 · 7bb3409 · ${esc(label(DEF['@app.updates'], globalOf('@app.updates')))}</small><p class="note" style="margin-top:6px">Xendroid — emulação de Xbox 360 no Android.</p></div></div></section>
      <section class="card" data-note="abdevice"><h3>${ic('phone', 15)} Aparelho</h3><dl class="kv">${DEVICE.map(([k, v]) => `<dt>${k}</dt><dd>${esc(v)}</dd>`).join('')}</dl><div class="row"><button class="btn sm" data-act="toast" data-msg="Versão e dados do aparelho copiados." data-k="ab-copy" data-note="abcopy">${ic('copy', 15)} Copiar tudo</button></div></section>
      <section class="card"><h3>${ic('info', 15)} Créditos e licenças</h3><p style="font-size:13.5px">Baseado no Xenia e em projetos de código aberto.</p><div class="row"><button class="btn sm ghost" data-act="modal" data-v="licenses" data-k="ab-lic">Licenças de código aberto</button></div>
        <div class="list">${drow({ icon: 'download', t: 'Procurar atualizações', acts: `<button class="btn sm ghost" data-act="go" data-v="update" data-k="ab-up">Abrir</button>` })}${drow({ icon: 'bug', t: 'Diagnóstico', acts: `<button class="btn sm ghost" data-act="go" data-v="diagnostics" data-k="ab-dg">Abrir</button>` })}${drow({ icon: 'spark', t: 'Assistente de configuração', acts: `<button class="btn sm ghost" data-act="ab-setup" data-k="ab-setup">Abrir</button>` })}</div></section>
    </div>` });
  },
});
action('ab-setup', () => { FR.step = 0; go('firstrun'); });
modal('licenses', () => ({ html: sheetHead('Licenças') + `<div class="list">${LICENSES.map(([n, l]) => drow({ icon: 'text', t: esc(n), s: l })).join('')}</div><div class="acts"><button class="btn primary" data-act="close" data-k="lic-ok" data-autofocus>OK</button></div>` }));
