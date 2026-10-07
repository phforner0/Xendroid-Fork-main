/* Lote 5: Conteúdo (instalado, lixeira, instalar), Diagnóstico (sessões guardadas) e Comparar execuções (A/B). */
'use strict';

Object.assign(NOTES, {
  cmall: ['app', 'Todo o conteúdo instalado num lugar, por jogo e com o tamanho. O que você remove vai para a lixeira; o jogo deixa de ver o pacote na próxima abertura.'],
  cmtabs: ['app', 'Aberta pela ficha, a mesma área mostra só o conteúdo daquele jogo: DLC, atualizações, lixeira e instalar.'],
  cmtrash: ['app', 'Lixeira com cota de 4 GiB para todos os jogos: restaurar, apagar de vez ou esvaziar; nada é apagado sozinho. Só pacotes instalados entram nela; os arquivos de jogo das suas pastas nunca são tocados.'],
  cminstall: ['app', 'Instalar um pacote CON, LIVE ou PIRS: o app confere de qual jogo ele é e o que é, confere o espaço livre antes e pergunta antes de substituir. Um jogo inteiro (XBLA, GoD) é copiado para a primeira pasta de jogos.'],
  cmrecent: ['app', 'Pacotes encontrados em Downloads e nas pastas de conteúdo, com as subpastas; os que ainda não foram instalados vêm primeiro.'],
  cmfolders: ['app', '“Criar pastas padrão”, no assistente, acrescenta XenDroid/TU e XenDroid/DLC como pastas de conteúdo.'],
  cmex: ['sim', 'Pacotes, tamanhos, datas e espaço livre são exemplos.'],
  dgsess: ['app', 'Cada vez que o app abre, a sessão anterior vira um ZIP de logs (guarda 4, de 1 a 16, em Configurações). Compartilhar gera uma cópia limpa; os logs no aparelho ficam como estão.'],
  dgstatus: ['app', 'Como cada sessão terminou: normalmente, encerrada pelo Android, com falha ou ainda rodando.'],
  dgprivacy: ['app', 'O que vai no arquivo e o que sai antes. Só textos entram; despejos binários de falha nunca vão.'],
  dgex: ['sim', 'As sessões são exemplos; as que terminam com problema usam um jogo fictício.'],
  bmruns: ['app', 'As execuções terminadas de um jogo, cada uma marcada como A ou B.'],
  bmverdict: ['app', 'O resultado só sai quando todos os pares concordam. Os avisos dizem o que invalida a comparação: ordem, menos de 4 execuções, execução com menos de 30 s, mais de uma mudança ou temperatura inicial com mais de 3 °C de diferença.'],
  bmchart: ['app', 'O FPS mediano de cada execução, na ordem em que rodaram, com a cor do lado, para ver o aquecimento. Quadros gerados nunca contam.'],
  bmhow: ['app', 'Como medir: a mesma cena, marcada pelo menu do jogo, com uma mudança só, fechando o jogo entre as execuções, na ordem A B B A.'],
  bmex: ['sim', 'As execuções são de um jogo fictício; nenhum número aqui é medição do app.'],
});

/* ---------- conteúdo ---------- */
const mbFmt = mb => mb >= 1024 ? (mb / 1024).toFixed(1).replace('.', DEC) + ' GB' : Math.round(mb) + ' MB';
const parseMB = s => { const m = /([\d,]+)\s*(MB|GB)/.exec(s || ''); return m ? parseFloat(m[1].replace(',', '.')) * (m[2] === 'GB' ? 1024 : 1) : 0; };
const CONTENT = [];
for (const g of GAMES) {
  if (g.tu) CONTENT.push({ id: g.id + ':tu', gid: g.id, type: 'tu', name: 'Title Update ' + g.tu, mb: 4 + hash(g.id + 'tu') % 56 });
  g.dlc.forEach((d, i) => CONTENT.push({ id: `${g.id}:dlc${i}`, gid: g.id, type: 'dlc', name: d, mb: 90 + hash(g.id + d) % 1100 }));
}
const TRASH_QUOTA = 4096; /* 4 GiB, a cota do app (ContentTrash.kt) */
const trashUsed = () => CONTENT_TRASH.reduce((s, t) => s + (t.mb || parseMB(t.size)), 0);
const typeName = t => t === 'tu' ? 'Atualização de título' : 'DLC';
/* pacotes encontrados (exemplos): em Downloads (folder vazio) ou numa pasta de conteúdo */
const CI_FILES = [
  { f: 'Forza Horizon - DLC de exemplo.con', gid: '4D5309C9', type: 'dlc', mb: 1210, name: 'DLC de exemplo', folder: '' },
  { f: 'Forza Horizon TU4 (exemplo).live', gid: '4D5309C9', type: 'tu', mb: 31, name: 'Title Update 4', folder: 'TU' },
  { f: 'Halo 4 - DLC de exemplo.con', gid: '4D530919', type: 'dlc', mb: 880, name: 'DLC de exemplo', folder: 'DLC' },
  { f: 'Jogo arcade de exemplo', gid: null, type: 'game', mb: 46, name: 'Jogo arcade de exemplo', folder: '' },
];
const CM_FOLDERS = ['/storage/emulated/0/XenDroid/TU', '/storage/emulated/0/XenDroid/DLC'];
const isInstalled = f => f.gid && CONTENT.some(c => c.gid === f.gid && c.name === f.name);
const CI = { busy: null, timer: 0 };
function cmItemRow(c, showGame) {
  const g = GBY[c.gid];
  return drow({ icon: c.type === 'tu' ? 'download' : 'box', t: esc(c.name), s: `${typeName(c.type)} · ${mbFmt(c.mb)}${showGame ? ' · ' + esc(g.name) : ''}`, acts: `<button class="btn sm ghost" data-act="cm-remove" data-v="${c.id}" data-k="cr-${c.id}">${ic('trash', 14)} Remover</button>` });
}
function cmAllHTML() {
  const games = GAMES.filter(g => CONTENT.some(c => c.gid === g.id)), total = CONTENT.reduce((s, c) => s + c.mb, 0);
  if (!CONTENT.length) return `<div class="empty">Nenhum DLC ou title update instalado ainda. <button class="link" data-act="sec" data-key="content" data-v="install" data-k="cm-none-inst">Instalar</button></div>`;
  return `<p class="note" style="margin-bottom:10px" data-note="cmex">${CONTENT.length} ${CONTENT.length === 1 ? 'pacote' : 'pacotes'} · ${mbFmt(total)} em ${games.length} ${games.length === 1 ? 'jogo' : 'jogos'}. O que você remover vai para a lixeira; o jogo deixa de vê-lo na próxima abertura.</p>
    <div class="stack" data-note="cmall">${games.map(g => { const items = CONTENT.filter(c => c.gid === g.id); return `<section class="card"><div class="cg-h">${coverHTML(g)}<div style="flex:1;min-width:0"><b>${esc(g.name)}</b><small>${items.filter(c => c.type === 'dlc').length} DLC · ${items.some(c => c.type === 'tu') ? items.find(c => c.type === 'tu').name : 'sem title update'} · ${mbFmt(items.reduce((s, c) => s + c.mb, 0))}</small></div><button class="btn sm ghost" data-act="go" data-v="content" data-p="${g.id}" data-k="cg-${g.id}">Abrir</button></div><div class="list">${items.map(c => cmItemRow(c)).join('')}</div></section>`; }).join('')}</div>`;
}
function cmListHTML(g, type) {
  const items = CONTENT.filter(c => c.gid === g.id && c.type === type);
  if (!items.length) return `<p class="note">${type === 'dlc' ? 'Nenhum DLC instalado.' : 'Nenhuma title update instalada.'}</p>`;
  return `<div class="card" data-note="cmtabs"><div class="list">${items.map(c => cmItemRow(c)).join('')}</div></div>`;
}
function cmTrashHTML(g) {
  const used = trashUsed(), list = CONTENT_TRASH.filter(t => !g || t.gid === g.id);
  return `<div class="stack" data-note="cmtrash"><section class="card usage"><div class="row"><b>Lixeira: ${mbFmt(used)} de ${TRASH_LIMIT}</b><button class="btn sm ghost" data-act="cm-empty" data-k="cm-empty"${CONTENT_TRASH.length ? '' : ' disabled'}>Esvaziar</button></div><span class="bar${used / TRASH_QUOTA > .8 ? ' warn' : ''}"><i style="width:${Math.min(100, used / TRASH_QUOTA * 100).toFixed(1)}%"></i></span><p class="note">DLC e title updates removidos ficam aqui até você restaurar ou apagar de vez. Nada é apagado sozinho. A lixeira vale para todos os jogos.</p></section>
    <section class="card">${list.length ? `<div class="list">${list.map((t, i) => drow({ icon: t.type === 'tu' ? 'download' : 'box', t: esc(t.name), s: `${typeName(t.type)} · ${t.size || mbFmt(t.mb)} · removido em ${t.removed}${g ? '' : ' · ' + esc(GBY[t.gid].name)}`, acts: `<button class="btn sm" data-act="cm-restore" data-v="${CONTENT_TRASH.indexOf(t)}" data-k="ct-r-${i}">Restaurar</button><button class="btn sm ghost" data-act="cm-purge" data-v="${CONTENT_TRASH.indexOf(t)}" data-k="ct-p-${i}">Apagar de vez</button>` })).join('')}</div>` : `<p class="note">${g ? 'Nada deste jogo está na lixeira.' : 'A lixeira está vazia.'}</p>`}</section></div>`;
}
function cmInstallHTML() {
  const g = S.route.p.gid ? GBY[S.route.p.gid] : null;
  const found = CI_FILES.filter(f => !g || f.gid === g.id).sort((a, b) => isInstalled(a) - isInstalled(b));
  const where = f => (f.folder ? ` · em ${f.folder}` : '');
  const desc = f => f.type === 'game' ? `Jogo · ${mbFmt(f.mb)} · vai para a biblioteca${where(f)}` : `${typeName(f.type)} · ${esc(GBY[f.gid].name)} · ${mbFmt(f.mb)}${where(f)}`;
  const nIn = folder => CI_FILES.filter(f => f.folder === folder).length, pk = n => `${n} ${n === 1 ? 'pacote' : 'pacotes'}`;
  return `<div class="grid2"><div class="col">
      <section class="card" data-note="cminstall"><h3>${ic('plus', 15)} Instalar um pacote</h3><ol class="ci-steps"><li>Escolha o arquivo do pacote (CON, LIVE ou PIRS).</li><li>${g ? 'O app confere se é DLC ou title update deste jogo.' : 'O app confere de qual jogo ele é e o que é: DLC, title update, um jogo inteiro.'}</li><li>${g ? 'Ele vai para a pasta de conteúdo e vale na próxima vez que o jogo abrir.' : 'Ele vai para a pasta de conteúdo e vale na próxima vez que o jogo abrir; um jogo inteiro vai para a sua biblioteca.'}</li></ol><div class="row"><button class="btn sm primary" data-act="ci-pick" data-k="ci-pick">${ic('folder', 15)} Escolher arquivo…</button><span class="note">Livre: 41,2 GB</span></div></section>
      <section class="card" data-note="cmfolders"><h3>${ic('folder', 15)} Pastas de conteúdo</h3><p class="note">Atualizações e DLC são procurados nestas pastas e em todas as pastas dentro delas, além de Downloads.</p>
        <div class="list">${drow({ icon: 'download', t: 'Downloads', s: pk(nIn('')) })}${CM_FOLDERS.map(p => drow({ icon: 'folder', t: esc(p.split('/').pop()), s: `${esc(shownPath(p))} · ${pk(nIn(p.split('/').pop()))}`, acts: `<button class="ibtn" data-act="toast" data-msg="No app, a pasta deixa de ser procurada; os arquivos ficam onde estão." aria-label="Remover" data-k="cmf-${esc(p)}">${ic('x', 16)}</button>` })).join('')}</div>
        <div class="row"><button class="btn sm" data-act="cmf-add" data-k="cmf-add">${ic('plus', 15)} Adicionar pasta</button></div></section></div>
    <section class="card" data-note="cmrecent"><h3>${ic('box', 15)} Pacotes encontrados</h3>${found.length ? `<div class="list">${found.map(f => { const i = CI_FILES.indexOf(f), on = isInstalled(f); return drow({ icon: f.type === 'game' ? 'gamepad' : f.type === 'tu' ? 'download' : 'box', t: `<span style="overflow-wrap:anywhere">${esc(f.f)}</span>${on ? ' <span class="badge ok">Instalado</span>' : ''}`, s: desc(f), acts: `<button class="btn sm${on ? ' ghost' : ''}" data-act="ci-file" data-v="${i}" data-k="ci-f${i}">${on ? 'Instalar de novo' : 'Instalar'}</button>` }); }).join('')}</div>` : `<p class="note">${g ? 'Nenhum pacote deste jogo em Downloads nem nas suas pastas de conteúdo.' : 'Nenhum pacote de conteúdo em Downloads nem nas suas pastas de conteúdo.'}</p>`}
      <div class="row"><button class="btn sm ghost" data-act="toast" data-msg="Procurando em Downloads e nas suas pastas de conteúdo… (simulação)" data-k="ci-again">${ic('refresh', 15)} Procurar de novo</button></div></section></div>
    ${g ? `<p class="note" style="margin-top:10px">Daqui, um pacote de outro jogo é recusado; instale-o pela área Conteúdo.</p>` : ''}`;
}
function cmTrashIt(c) {
  const i = CONTENT.indexOf(c); if (i < 0) return;
  CONTENT.splice(i, 1); CONTENT_TRASH.unshift({ gid: c.gid, name: c.name, type: c.type, mb: c.mb, size: mbFmt(c.mb), removed: 'hoje' });
  S.modal = null; toast(`“${c.name}” foi para a lixeira. Restaure na aba Lixeira.`);
}
action('cm-remove', el => { const c = CONTENT.find(x => x.id === el.dataset.v); if (!c) return; S.modal = trashUsed() + c.mb > TRASH_QUOTA ? 'cm-full' : 'cm-remove'; S.mp = { v: c.id }; render(); });
action('cm-trash-go', el => cmTrashIt(CONTENT.find(x => x.id === el.dataset.v)));
action('cm-forgood', el => { const c = CONTENT.find(x => x.id === el.dataset.v); CONTENT.splice(CONTENT.indexOf(c), 1); S.modal = null; toast(`“${c.name}” apagado de vez.`); });
action('cm-restore', el => {
  const t = CONTENT_TRASH[Number(el.dataset.v)]; if (!t) return;
  if (t.type === 'tu' && CONTENT.some(c => c.gid === t.gid && c.type === 'tu')) { toast('Outro pacote usa o mesmo cabeçalho; remova-o antes'); return; }
  CONTENT_TRASH.splice(CONTENT_TRASH.indexOf(t), 1); CONTENT.push({ id: `${t.gid}:r${Date.now()}`, gid: t.gid, type: t.type, name: t.name, mb: t.mb || parseMB(t.size) }); toast(`“${t.name}” restaurado.`);
});
action('cm-purge', el => { S.modal = 'cm-purge'; S.mp = { v: el.dataset.v }; render(); });
action('cm-purge-go', el => { const t = CONTENT_TRASH.splice(Number(el.dataset.v), 1)[0]; S.modal = null; toast(`“${t.name}” apagado de vez.`); });
action('cm-empty', () => { S.modal = 'cm-empty'; render(); });
action('cm-empty-go', () => { const n = CONTENT_TRASH.length; CONTENT_TRASH.length = 0; S.modal = null; toast(`Lixeira esvaziada (${n} ${n === 1 ? 'item' : 'itens'}).`); });
action('ci-file', el => {
  const f = CI_FILES[Number(el.dataset.v)], here = S.route.p.gid;
  if (here && f.gid && f.gid !== here) { S.modal = 'ci-msg'; S.mp = { t: 'Não instalado', m: `Este pacote é do título ${f.gid}, não deste jogo (${here}).` }; render(); return; }
  if (isInstalled(f)) { S.modal = 'ci-dup'; S.mp = { v: el.dataset.v }; render(); return; }
  ciInstall(f);
});
action('ci-over', el => ciInstall(CI_FILES[Number(el.dataset.v)], true));
function ciInstall(f, over) {
  clearTimeout(CI.timer); CI.busy = f.type === 'game' ? 'Copiando para a sua biblioteca…' : 'Instalando…'; S.modal = 'ci-busy'; render();
  CI.timer = setTimeout(() => {
    CI.busy = null;
    if (f.type === 'game') { S.modal = 'ci-msg'; S.mp = { t: 'Pronto', m: `“${f.name}” foi copiado para a sua primeira pasta de jogos e entra na biblioteca.`, ok: 1 }; render(); return; }
    if (!over) CONTENT.push({ id: `${f.gid}:n${Date.now()}`, gid: f.gid, type: f.type, name: f.name, mb: f.mb });
    S.modal = 'ci-msg'; S.mp = { t: 'Pronto', m: `“${f.name}” instalado. Fica disponível na próxima vez que o jogo abrir.`, ok: 1 }; render();
  }, 1300);
}
modal('cm-remove', mp => { const c = CONTENT.find(x => x.id === mp.v); return { html: sheetHead('Remover conteúdo?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">Mandar “${esc(c.name)}” para a lixeira? O jogo deixa de vê-lo; dá para restaurar na aba Lixeira, ou apagar de vez lá.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="cmr-x" data-autofocus>Cancelar</button><button class="btn danger" data-act="cm-trash-go" data-v="${c.id}" data-k="cmr-go">Mandar para a lixeira</button></div>` }; });
modal('cm-full', mp => { const c = CONTENT.find(x => x.id === mp.v); return { html: sheetHead('A lixeira está cheia') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">A lixeira tem ${mbFmt(trashUsed())} de ${mbFmt(TRASH_QUOTA)}, então “${esc(c.name)}” não cabe. Esvazie a lixeira (de todos os jogos) antes, ou apague este de vez agora: isso não pode ser desfeito.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="cmf-x" data-autofocus>Cancelar</button><button class="btn ghost" data-act="cm-empty" data-k="cmf-empty">Esvaziar a lixeira</button><button class="btn danger" data-act="cm-forgood" data-v="${c.id}" data-k="cmf-del">Apagar de vez</button></div>` }; });
modal('cm-purge', mp => { const t = CONTENT_TRASH[Number(mp.v)]; return { html: sheetHead('Apagar de vez?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">“${esc(t.name)}” (${t.size || mbFmt(t.mb)}) será apagado. Isso não pode ser desfeito.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="cmp-x" data-autofocus>Cancelar</button><button class="btn danger" data-act="cm-purge-go" data-v="${mp.v}" data-k="cmp-go">Apagar</button></div>` }; });
modal('cm-empty', () => ({ html: sheetHead('Esvaziar a lixeira?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">Todos os pacotes da lixeira, de todos os jogos, serão apagados. Isso não pode ser desfeito.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="cme-x" data-autofocus>Cancelar</button><button class="btn danger" data-act="cm-empty-go" data-k="cme-go">Esvaziar a lixeira</button></div>` }));
modal('ci-busy', () => ({ html: `<div class="busy"><b style="font-size:15px">${esc(CI.busy || '')}</b><div class="bar ind"><i></i></div></div>` }));
modal('ci-dup', mp => { const f = CI_FILES[Number(mp.v)]; return { html: sheetHead('Já instalado') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">“${esc(f.name)}” já está instalado. Substituir?</p><div class="acts"><button class="btn ghost" data-act="close" data-k="cd-x" data-autofocus>Cancelar</button><button class="btn primary" data-act="ci-over" data-v="${mp.v}" data-k="cd-go">Substituir</button></div>` }; });
modal('ci-msg', mp => ({ html: sheetHead(esc(mp.t)) + `<p class="${mp.ok ? 'okline' : 'errline'}" style="font-size:13.5px">${ic(mp.ok ? 'checkC' : 'alert', 16)} ${esc(mp.m)}</p><div class="acts"><button class="btn primary" data-act="close" data-k="cim-ok" data-autofocus>OK</button></div>` }));

screen('content', {
  title: 'Conteúdo', globalScope: true,
  get secKey() { return S.route.p.gid ? 'content-g' : 'content'; },
  render() {
    const g = S.route.p.gid ? GBY[S.route.p.gid] : null;
    if (g) {
      const n = t => CONTENT.filter(c => c.gid === g.id && c.type === t).length, nt = CONTENT_TRASH.filter(t => t.gid === g.id).length;
      return sectioned({ key: 'content-g', title: `Conteúdo · ${g.name}`, sub: `${g.id} · ${n('dlc')} DLC · ${n('tu') ? 'com title update' : 'sem title update'}`, csub: `${n('dlc')} DLC`, plainSub: true, head: `<span style="width:40px;flex:none">${coverHTML(g)}</span>`, art: art(g), dyn: dynVars(g), icon: 'box', sections: [
        { id: 'dlc', t: 'DLC', icon: 'box', n: n('dlc') || '', title: 'DLC', body: () => cmListHTML(g, 'dlc') },
        { id: 'tu', t: 'Atualizações', icon: 'download', n: n('tu') || '', title: 'Atualizações de título', body: () => cmListHTML(g, 'tu') },
        { id: 'trash', t: 'Lixeira', icon: 'trash', n: nt || '', title: 'Lixeira', body: () => cmTrashHTML(g) },
        { id: 'install', t: 'Instalar', icon: 'plus', title: 'Instalar conteúdo', body: cmInstallHTML },
      ] });
    }
    return sectioned({ key: 'content', title: 'Conteúdo', sub: `${CONTENT.length} ${CONTENT.length === 1 ? 'pacote' : 'pacotes'} · lixeira ${mbFmt(trashUsed())} de ${TRASH_LIMIT}`, csub: `${CONTENT.length} pacotes`, plainSub: true, icon: 'box', actions: `<button class="btn sm" data-act="sec" data-key="content" data-v="install" data-k="cm-top-inst">${ic('plus', 15)} Instalar</button>`, sections: [
      { id: 'inst', t: 'Instalado', icon: 'box', n: CONTENT.length, title: 'Conteúdo instalado', body: cmAllHTML },
      { id: 'trash', t: 'Lixeira', icon: 'trash', n: CONTENT_TRASH.length || '', title: 'Lixeira', body: () => cmTrashHTML(null) },
      { id: 'install', t: 'Instalar', icon: 'plus', title: 'Instalar conteúdo', body: cmInstallHTML },
    ] });
  },
});

/* ---------- diagnóstico ---------- */
const DG = { filter: 'all' };
const END_T = { ok: 'terminou normalmente', interrupted: 'encerrada pelo Android', fail: 'falha' };
function dgList() { const gid = S.route.p.gid; return RUNS.filter(r => DG.filter === 'all' ? true : DG.filter === 'game' ? r.gid === gid : r.end !== 'ok'); }
function dgBody() {
  const gid = S.route.p.gid, g = gid ? GBY[gid] : null, list = dgList();
  const chips = [['all', 'Todas'], ...(g ? [['game', esc(g.name)]] : []), ['bad', 'Com problema']];
  return `<div class="stack"><p class="note">Compartilhe uma cópia com dados pessoais removidos de uma sessão ou de todo o histórico guardado. Os logs originais ficam no aparelho.</p>
    <div class="row">${chips.map(([v, t]) => `<button class="chip" data-act="dg-f" data-v="${v}" aria-pressed="${DG.filter === v}" data-k="dgf-${v}">${t}</button>`).join('')}<span class="sp"></span><button class="btn sm primary" data-act="toast" data-msg="No app, gera a cópia limpa e abre o compartilhamento do Android." data-k="dg-all">${ic('share', 15)} Compartilhar todas as sessões</button></div>
    <section class="card" data-note="dgsess">${list.length ? `<div class="list">${list.map((r, i) => { const gg = GBY[r.gid]; return `<div class="sess"><i class="st end-${r.end}" title="${END_T[r.end]}" data-note="dgstatus"></i>${coverHTML(gg)}<div><b>${esc(gg.name)} · ${esc(r.when)}</b><small>${esc(r.dur)} · ${esc(r.drv)} · ${r.kb} KB · ${END_T[r.end]}${r.why ? ': ' + esc(r.why) : ''}</small></div><div class="row"><button class="btn sm ghost" data-act="dg-open" data-v="${RUNS.indexOf(r)}" data-k="dgo-${i}">Resumo</button><button class="btn sm" data-act="toast" data-msg="No app, gera a cópia limpa desta sessão e abre o compartilhamento do Android." data-k="dgs-${i}">${ic('share', 14)} Compartilhar</button></div></div>`; }).join('')}</div>` : `<p class="note">${DG.filter === 'game' ? 'Ainda não há sessão indexada para este jogo. Logs mais antigos, sem índice, continuam disponíveis em Compartilhar todas as sessões.' : 'Nenhuma sessão com esse filtro.'}</p>`}</section>
    <div class="row" data-note="dgex"><span class="note" style="flex:1">Os logs originais, sem limpeza, podem ir para Downloads.</span><button class="btn sm ghost" data-act="toast" data-msg="No app, grava as sessões guardadas e a atual num ZIP em Downloads, sem ocultar dados." data-k="dg-dl">${ic('download', 15)} Exportar</button></div></div>`;
}
action('dg-f', el => { DG.filter = el.dataset.v; render(); });
action('dg-open', el => { S.modal = 'dg-sess'; S.mp = { v: el.dataset.v }; render(); });
modal('dg-sess', mp => {
  const r = RUNS[Number(mp.v)], g = GBY[r.gid], st = r.end === 'ok' ? perfOf(g) : null, p = g.perf;
  return { wide: true, html: sheetHead(`${esc(g.name)} · ${esc(r.when)}`, `${END_T[r.end]}${r.why ? ': ' + esc(r.why) : ''}`) + `<dl class="kv"><dt>Começou</dt><dd>${esc(r.when)}</dd><dt>Tempo jogado</dt><dd>${esc(r.dur)}</dd><dt>Driver</dt><dd>${esc(r.drv)}</dd>${st ? `<dt>FPS mediano · 99% dos quadros</dt><dd>${st.p50} FPS · abaixo de ${st.ft99} ms</dd><dt>Primeiro quadro</dt><dd>${p.first} s</dd><dt>Pipelines criados</dt><dd>${nf(p.pipes[0])}</dd><dt>Bateria</dt><dd>${p.bat[0]} → ${p.bat[1]} °C</dd>` : ''}<dt>Logs</dt><dd>${r.kb} KB</dd></dl>
    ${r.end === 'fail' ? `<h4 class="gh">Onde falhou</h4><pre class="toml" style="max-height:110px" lang="en">Fatal signal 11 (SIGSEGV), code 1 (exemplo fictício)\n#00 pc 0012af40 libvulkan_turnip.so\n#01 pc 0008c2d4 libxenia.so</pre>` : ''}
    <div class="privacy" data-note="dgprivacy"><div class="card tight"><h3>${ic('check', 14)} Vai no arquivo</h3><ul><li>O log do emulador da sessão</li><li>O logcat do app (só deste app)</li><li>Quais jogos rodaram e a versão do app</li><li>Motivos de saída do Android e rastros de ANR</li></ul></div><div class="card tight"><h3>${ic('lock', 14)} Sai antes</h3><ul><li>Caminhos de arquivos e pastas</li><li>Gamertags, XUIDs e nomes de perfil</li><li>Endereços IP e e-mails</li><li>Senhas e tokens de acesso</li></ul></div></div>
    <p class="note">Despejos binários de falha nunca vão. Os logs neste aparelho ficam como estão.</p>
    <div class="acts"><button class="btn ghost" data-act="close" data-k="dgs-x">Fechar</button><button class="btn primary" data-act="toast" data-msg="No app, gera a cópia limpa desta sessão e abre o compartilhamento do Android." data-k="dgs-go" data-autofocus>${ic('share', 15)} Compartilhar esta sessão</button></div>` };
});
screen('diagnostics', {
  title: 'Diagnóstico', globalScope: true,
  render() { const g = S.route.p.gid ? GBY[S.route.p.gid] : null; if (g && DG.filter === 'all' && !S.route.p.seen) { DG.filter = 'game'; S.route.p.seen = 1; } return single({ key: 'diagnostics', title: 'Diagnóstico', sub: `${RUNS.length} ${RUNS.length === 1 ? 'sessão guardada' : 'sessões guardadas'}`, icon: 'bug', body: dgBody }); },
});

/* ---------- comparar execuções ---------- */
const BM_SETS = { [BENCH.gid]: BENCH.runs };
const BM_MIN_S = 30, BM_MAX_DT = 3; /* Benchmark.kt: MIN_SECONDS e MAX_START_DELTA_C */
const BM = { gid: BENCH.gid };
function bmCompute(runs) {
  const L = runs.filter(r => r.side), A = L.filter(r => r.side === 'A'), B = L.filter(r => r.side === 'B'), notes = [];
  if (!A.length || !B.length) return { outcome: 'none', notes: ['Marque pelo menos uma execução como A e uma como B.'], L, A, B, pairs: [] };
  const order = L.map(r => r.side).join(' '), seq = L.map(r => r.side).join('');
  const balanced = L.length >= 4 && A.length === B.length && seq === [...seq].reverse().join('');
  if (L.length < 4) notes.push('Rode pelo menos A B B A (4 execuções): o telefone esquenta durante uma sessão.');
  else if (!balanced) notes.push(`A ordem ${order} não cancela o aquecimento: rode na ordem ${seq.startsWith('A') ? 'A B B A' : 'B A A B'}.`);
  for (const r of L) if (r.sec < BM_MIN_S) notes.push(`Uma execução ${r.side} mediu só ${r.sec} s (pelo menos ${BM_MIN_S} s da mesma cena).`);
  const dims = [['drv', 'driver', v => v], ['lim', 'limite de FPS', v => v ? v + ' FPS' : 'sem limite'], ['hz', 'taxa da tela', v => v + ' Hz']], changes = [];
  for (const [k, t, f] of dims) {
    for (const [side, rs] of [['A', A], ['B', B]]) { const vs = [...new Set(rs.map(r => r[k]))]; if (vs.length > 1) notes.push(`Valores de ${t} diferentes entre as execuções ${side} (${vs.map(f).join(', ')}): cada lado precisa de uma só configuração.`); }
    const va = A[0][k], vb = B[0][k]; if (va !== vb) changes.push(`${t} (${f(va)} → ${f(vb)})`);
  }
  if (changes.length > 1) notes.push(`A e B diferem em mais de uma coisa: ${changes.map(c => c.split(' (')[0]).join(', ')}. Mude uma de cada vez.`);
  const temps = L.map(r => r.temp), dt = Math.max(...temps) - Math.min(...temps);
  if (dt > BM_MAX_DT) notes.push(`As execuções começaram com ${dt} °C de diferença; deixe o telefone esfriar até a mesma temperatura antes.`);
  const pairs = []; for (let i = 0; i + 1 < L.length; i += 2) { const a = L[i].side === 'A' ? L[i] : L[i + 1], b = L[i].side === 'B' ? L[i] : L[i + 1]; if (a && b && a.side === 'A' && b.side === 'B') pairs.push(b.p50 - a.p50); }
  let outcome = notes.length ? 'fix' : !pairs.length ? 'nopair' : pairs.every(d => d > 0) ? 'faster' : pairs.every(d => d < 0) ? 'slower' : pairs.every(d => d === 0) ? 'same' : 'disagree';
  return { outcome, notes, order, balanced, changes, pairs, A, B, L };
}
const med = a => { const s = a.slice().sort((x, y) => x - y), n = s.length; return n ? (n % 2 ? s[(n - 1) / 2] : (s[n / 2 - 1] + s[n / 2]) / 2) : 0; };
function bmVerdict(r) {
  const d = r.pairs, lo = Math.min(...d), hi = Math.max(...d);
  return { none: 'Nenhum par A/B para comparar.', nopair: 'Nenhum par A/B para comparar.', fix: 'Resolva os avisos antes; os números abaixo ainda não são um resultado.', faster: `B é mais rápido em todos os pares (+${lo} a +${hi} FPS na mediana).`, slower: `B é mais lento em todos os pares (${hi} a ${lo} FPS na mediana).`, same: 'Nenhuma diferença no FPS mediano.', disagree: `Os pares discordam (${d.map(x => (x > 0 ? '+' : '') + x).join(', ')}): nenhuma diferença comprovada; rode mais pares.` }[r.outcome];
}
function bmBody(v) {
  const runs = BM_SETS[BM.gid], g = GBY[BM.gid], r = bmCompute(runs), maxF = Math.max(...runs.map(x => x.p50)) + 4;
  const games = Object.keys(BM_SETS);
  const runRow = x => `<div class="bm-run ${x.side || ''}"><span class="n">${x.side || x.id}</span><div><b>${esc(x.when)} · ${x.p50} FPS</b><small>5º pct ${x.p5} · 99% &lt; ${x.p99} ms · ${esc(x.drv)} · ${x.temp} °C no início · limite ${x.lim || 'desligado'} · ${x.sec} s</small></div><div class="seg" role="group" aria-label="Lado da execução ${x.id}">${[['A', 'A'], ['', '–'], ['B', 'B']].map(([s, t]) => `<button data-act="bm-side" data-v="${x.id}" data-s="${s}" aria-pressed="${(x.side || '') === s}" data-k="bms-${x.id}-${s || 'x'}">${t}</button>`).join('')}</div></div>`;
  return `<div class="stack">
    <div class="row" data-note="bmex">${games.map(id => `<button class="chip" data-act="bm-game" data-v="${id}" aria-pressed="${BM.gid === id}" data-k="bmg-${id}">${esc(GBY[id].name)}<span class="n">${BM_SETS[id].length}</span></button>`).join('')}<span class="sp"></span><button class="btn sm ghost" data-act="modal" data-v="bm-how" data-k="bm-how" data-note="bmhow">${ic('info', 15)} Como medir</button></div>
    <div class="bm">
      <section class="card" data-note="bmruns"><h3>${ic('timeline', 15)} Execuções de ${esc(g.name)}<span class="r">${runs.length}</span></h3><div class="list">${runs.map(runRow).join('')}</div></section>
      <section class="card" data-note="bmverdict"><h3>${ic('ab', 15)} Resultado</h3>
        <p class="verdict ${r.outcome === 'fix' || r.outcome === 'disagree' ? 'warn' : r.outcome === 'faster' || r.outcome === 'slower' || r.outcome === 'same' ? 'ok' : ''}">${esc(bmVerdict(r))}</p>
        ${r.notes.length ? `<ul class="wl">${r.notes.map(n => `<li>${ic('warn', 14)}<span>${esc(n)}</span></li>`).join('')}</ul>` : ''}
        ${r.A && r.A.length && r.B && r.B.length ? `<div class="bm-bars" data-note="bmchart">${r.L.map(x => `<div class="bb ${x.side}"><b>${x.p50}</b><i style="height:${(x.p50 / maxF * 100).toFixed(0)}%"></i><span>${x.side}${x.id}</span></div>`).join('')}</div>
        <div class="bm-legend"><span><i style="background:#2f6fd1"></i>A</span><span><i style="background:#c4501c"></i>B</span><span>FPS mediano por execução, na ordem em que rodaram</span></div>
        <div class="bm-sides">${[['A', r.A], ['B', r.B]].map(([s, rs]) => `<div class="bm-side ${s}"><b>${med(rs.map(x => x.p50)).toString().replace('.', DEC)} FPS</b><small>${s}: ${rs.length} ${rs.length === 1 ? 'execução' : 'execuções'}, 5º pct ${med(rs.map(x => x.p5)).toString().replace('.', DEC)}, 99% &lt; ${Math.round(med(rs.map(x => x.p99)))} ms</small></div>`).join('')}</div>
        <dl class="kv"><dt>Ordem</dt><dd>${r.order}${r.balanced ? ' (equilibrada)' : ''}</dd>${r.changes.length === 1 ? `<dt>O que mudou</dt><dd>${esc(r.changes[0])}</dd>` : ''}${r.pairs.length ? `<dt>B − A por par</dt><dd>${r.pairs.map(d => (d > 0 ? '+' : '') + d).join(', ')} FPS (mediana)</dd>` : ''}</dl>` : ''}
      </section>
    </div></div>`;
}
action('bm-side', el => { const x = BM_SETS[BM.gid].find(r => String(r.id) === el.dataset.v); x.side = el.dataset.s || null; render(); });
action('bm-game', el => { BM.gid = el.dataset.v; render(); });
modal('bm-how', () => ({ html: sheetHead('Como medir') + `<ol class="ci-steps"><li>Escolha uma cena e marque-a pelo menu do jogo (Sessão → Marcar cena para comparar).</li><li>Mude uma coisa só: o driver, a geração de quadros, o limite de FPS ou um ajuste.</li><li>Feche o jogo entre as execuções e rode na ordem A B B A, cada uma com pelo menos ${BM_MIN_S} s da mesma cena.</li><li>Deixe o telefone esfriar até a mesma temperatura antes de cada execução.</li><li>O FPS aqui conta só os quadros do próprio jogo: quadros gerados nunca contam.</li></ol><div class="acts"><button class="btn primary" data-act="close" data-k="bmh-ok" data-autofocus>Entendi</button></div>` }));
screen('compare', {
  title: 'Comparar execuções', globalScope: true,
  render() { return single({ key: 'compare', title: 'Comparar execuções', sub: 'A/B com uma mudança só', icon: 'ab', body: bmBody }); },
});
