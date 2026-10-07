/* Lote 4: Perfis (lista, quem joga P1–P4, lixeira, criar e editar, jogar como) e os saves de cada jogo. */
'use strict';

Object.assign(NOTES, {
  pfcards: ['existe', 'Perfis com avatar, gamertag, idioma, região e quem é o ativo (P1). Hoje é uma lista com menu ⋮ em cada linha.'],
  pfsaves: ['novo', 'Quanto cada perfil guarda e em quais jogos, com atalho para os saves de cada um.'],
  pfslots: ['existe', 'Quem entra como P1–P4: o ativo é o P1; P2–P4 escolhidos aqui (um perfil por jogador). Vale na próxima abertura.'],
  pfask: ['existe', 'Perguntar quem joga antes de cada jogo (a biblioteca abre “Jogar como”).'],
  pftrash: ['existe', 'Lixeira de perfis: restaurar com os saves, ou remover de vez com confirmação.'],
  pfform: ['existe', 'Criar e editar: avatar (imagem do aparelho, com limite de tamanho), gamertag de 1 a 15 caracteres começando com letra, idioma e região.'],
  playas: ['existe', '“Jogar como”: escolher quem entra nesta abertura; quem já era P2–P4 passa para o P1 e deixa a vaga livre.'],
  svnames: ['novo', 'Gamertag e avatar no lugar do XUID, e os saves de cada perfil pelos nomes guardados nos cabeçalhos.'],
  svsel: ['existe', 'Escolher os perfis para exportar, com ou sem o perfil associado; importar um backup.'],
  svsync: ['existe', 'Pasta de sincronização opcional (local ou na nuvem do Android): backup ao voltar para a biblioteca, sincronizar agora e restaurar da pasta.'],
  svreview: ['existe', 'Antes de restaurar: arquivos, XUIDs originais e conflitos; os saves atuais recebem backup antes.'],
  svgames: ['novo', 'Todos os jogos com saves deste perfil, a partir dos Perfis.'],
});

/* ---------- perfis ---------- */
const PFS = { ask: false };
const pfBy = x => PROFILES.find(p => p.xuid === x);
const pfSlot = n => PROFILES.find(p => p.slot === n);
const LANGS = DEF['Console.user_language'].o, REGIONS = DEF['Console.user_country'].o;
const gamesOfProfile = x => Object.keys(SAVES).filter(gid => SAVES[gid].some(s => s.xuid === x));
function pfStatus(p) { return p.active ? 'Ativo · P1' : p.slot ? `Entra como P${p.slot}` : ''; }
function pfCardHTML(p) {
  const g = gamesOfProfile(p.xuid);
  return `<article class="pf-card ${p.active ? 'on' : ''}">
    <div class="pf-top">${avatarImg(p, 'avatar-l')}<div><b>${esc(p.tag)}</b>${pfStatus(p) ? `<span class="badge ${p.active ? 'acc' : ''}">${pfStatus(p)}</span>` : ''}<small>${p.xuid}</small></div><span class="sp"></span><button class="ibtn" style="align-self:flex-start" data-act="pf-trash" data-v="${p.xuid}" data-k="pt-${p.xuid}" aria-label="Mandar ${esc(p.tag)} para a lixeira" title="Mandar para a lixeira">${ic('trash', 17)}</button></div>
    <dl class="kv"><dt>Idioma · região</dt><dd>${esc(p.lang)} · ${esc(p.region)}</dd><dt>Saves</dt><dd>${p.games} ${p.games === 1 ? 'jogo' : 'jogos'} · ${p.files} arquivos · ${p.mb} MB</dd></dl>
    <div class="row">${p.active ? '' : `<button class="btn sm" data-act="pf-active" data-v="${p.xuid}" data-k="pa-${p.xuid}">Tornar ativo</button>`}<button class="btn sm ghost" data-act="pf-edit" data-v="${p.xuid}" data-k="pe-${p.xuid}">Editar</button><button class="btn sm ghost" data-act="pf-saves" data-v="${p.xuid}" data-k="ps-${p.xuid}"${g.length ? '' : ' disabled'} data-note="pfsaves">${ic('save', 14)} Saves</button></div>
  </article>`;
}
function pfListHTML(v) {
  if (v === 'c') return `<div class="pf-c" data-note="pfcards">${PROFILES.map((p, i) => `<button class="pf-ct ${p.active ? 'on' : ''}" data-act="pf-menu" data-v="${p.xuid}" data-k="pc-${p.xuid}"${i === 0 ? ' data-autofocus' : ''}>${avatarImg(p, 'avatar-xl')}<b>${esc(p.tag)}</b><small>${pfStatus(p) || esc(p.lang)}</small></button>`).join('')}<button class="pf-ct" data-act="pf-edit" data-v="" data-k="pc-new">${ic('plus', 40)}<b>Criar perfil</b><small>Gamertag e avatar</small></button></div>
    <p class="note">A abre as opções do perfil. Cada perfil mantém os próprios saves.</p>`;
  return `<div class="pf-grid" data-note="pfcards">${PROFILES.map(pfCardHTML).join('')}<button class="pf-new" data-act="pf-edit" data-v="" data-k="pf-new">${ic('plus', 30)}Criar perfil</button></div>
    <p class="note" style="margin-top:10px">Cada perfil mantém os próprios saves. O ativo entra como P1 em todos os jogos.</p>`;
}
function pfPlayersHTML(v) {
  const slot = n => { const p = pfSlot(n); return `<div class="pslot ${p ? '' : 'free'}"><span class="p">P${n}</span><div class="who">${p ? `${avatarImg(p, '')}<b>${esc(p.tag)}</b>` : '<b class="muted">Ninguém entra</b>'}</div><div class="row"><button class="btn sm${p ? ' ghost' : ''}" data-act="pf-slot" data-v="${n}" data-k="sl-${n}">${n === 1 ? 'Trocar o ativo' : p ? 'Trocar' : 'Escolher'}</button>${p && n > 1 ? `<button class="btn sm ghost" data-act="pf-slot-set" data-n="${n}" data-v="" data-k="slx-${n}">Ninguém</button>` : ''}</div></div>`; };
  return `<div class="stack"><div class="pslots" data-note="pfslots">${[1, 2, 3, 4].map(slot).join('')}</div>
    <p class="note">Um perfil entra por um jogador de cada vez; o controle de cada jogador usa o seu. Vale na próxima abertura de um jogo.</p>
    <div data-note="pfask">${v === 'c' ? `<button class="crow" data-act="pf-ask" data-k="pf-ask"><span class="t"><b>Perguntar quem joga antes de cada jogo</b><small>${PFS.ask ? 'A biblioteca pergunta com qual perfil entrar.' : 'Os jogos entram com o perfil ativo.'}</small></span><span class="tg" role="presentation" aria-checked="${PFS.ask}"></span></button>` : `<div class="srow"><div><div class="srow-t">Perguntar quem joga antes de cada jogo</div><div class="srow-m"><span class="src">App</span></div></div><div class="srow-c"><button class="tg" role="switch" aria-checked="${PFS.ask}" data-act="pf-ask" data-k="pf-ask" aria-label="Perguntar quem joga antes de cada jogo"></button></div><p class="srow-d">${PFS.ask ? 'A biblioteca pergunta com qual perfil entrar.' : 'Os jogos entram com o perfil ativo.'}</p></div>`}</div></div>`;
}
function pfTrashHTML() {
  if (!PROFILE_TRASH.length) return '<p class="note">A lixeira está vazia.</p>';
  return `<div class="card pf-trash" data-note="pftrash"><div class="list">${PROFILE_TRASH.map(p => drow({ img: avatarImg(p), t: esc(p.tag), s: `Removido em ${p.removed} · ${p.games} ${p.games === 1 ? 'jogo' : 'jogos'} · ${p.files} arquivos · ${p.mb} MB`, acts: `<button class="btn sm" data-act="pf-restore" data-v="${p.xuid}" data-k="pr-${p.xuid}">Restaurar</button><button class="btn sm ghost" data-act="pf-purge" data-v="${p.xuid}" data-k="pp-${p.xuid}">Remover de vez</button>` })).join('')}</div></div>
    <p class="note" style="margin-top:10px">Tudo fica na lixeira deste aparelho até você remover de vez, e pode ser restaurado com os saves.</p>`;
}
function newXuid() { let s = 'E03000'; for (let i = 0; i < 10; i++) s += '0123456789ABCDEF'[Math.floor(Math.random() * 16)]; return s; }
action('pf-active', el => { const p = pfBy(el.dataset.v); if (!p) return; const old = PROFILES.find(x => x.active); if (old) { old.active = false; old.slot = null; } p.active = true; p.slot = 1; S.modal = null; toast('Perfil ativo definido. Vale na próxima abertura de um jogo.'); });
action('pf-slot', el => { S.modal = 'pf-slot'; S.mp = { n: Number(el.dataset.v) }; render(); });
action('pf-slot-set', el => {
  const n = Number(el.dataset.n), x = el.dataset.v, p = x ? pfBy(x) : null;
  if (n === 1) { if (p) ACT['pf-active']({ dataset: { v: x } }); return; }
  if (p && p.active) { toast('Esse perfil joga como P1. Escolha outro, ou torne outro perfil ativo antes.'); return; }
  const cur = pfSlot(n); if (cur) cur.slot = null;
  if (p) p.slot = n;
  S.modal = null; toast(p ? `Perfil do P${n} definido. Vale na próxima abertura de um jogo.` : `O P${n} não vai entrar com perfil. Vale na próxima abertura de um jogo.`);
});
action('pf-ask', () => { PFS.ask = !PFS.ask; render(); });
action('pf-menu', el => { S.modal = 'pf-menu'; S.mp = { v: el.dataset.v }; render(); });
action('pf-edit', el => { S.modal = 'pf-edit'; S.mp = { v: el.dataset.v || '' }; render(); });
action('pf-save', el => {
  const tag = (document.getElementById('pf-tag') || {}).value || '', lang = (document.getElementById('pf-lang') || {}).value, reg = (document.getElementById('pf-reg') || {}).value;
  if (!/^[A-Za-z][A-Za-z0-9 ]{0,14}$/.test(tag.trim())) { const e = document.getElementById('pf-err'); if (e) e.hidden = false; return; }
  const lt = (LANGS.find(o => o[0] === lang) || LANGS[0])[1], rt = (REGIONS.find(o => o[0] === reg) || REGIONS[0])[1];
  const x = el.dataset.v;
  if (x) { const p = pfBy(x); p.tag = tag.trim(); p.lang = lt; p.region = rt; p.avatar = drawAvatar(p).toDataURL('image/png'); S.modal = null; toast(`“${p.tag}” salvo.`); return; }
  const pal = [['#7a3fb2', '#2a0f46'], ['#c07a1c', '#3d2306'], ['#1c9a9a', '#063333']][PROFILES.length % 3];
  const p = { xuid: newXuid(), tag: tag.trim(), c: pal, lang: lt, region: rt, active: false, slot: null, games: 0, files: 0, mb: 0 };
  p.avatar = drawAvatar(p).toDataURL('image/png'); PROFILES.push(p); S.modal = null; toast(`“${p.tag}” criado.`);
});
action('pf-trash', el => { const p = pfBy(el.dataset.v); if (p && p.active) { toast('Esse é o perfil ativo. Torne outro perfil ativo antes.'); return; } S.modal = 'pf-trash'; S.mp = { v: el.dataset.v }; render(); });
action('pf-trash-go', el => { const i = PROFILES.findIndex(p => p.xuid === el.dataset.v); if (i < 0) return; const p = PROFILES.splice(i, 1)[0]; p.removed = 'hoje'; p.slot = null; PROFILE_TRASH.unshift(p); S.modal = null; toast('Perfil na lixeira. Restaure nesta tela, ou remova de vez.'); });
action('pf-restore', el => { const i = PROFILE_TRASH.findIndex(p => p.xuid === el.dataset.v); if (i < 0) return; const p = PROFILE_TRASH.splice(i, 1)[0]; Object.assign(p, { active: false, slot: null, lang: p.lang || 'Português', region: p.region || 'Brasil' }); PROFILES.push(p); toast('Perfil restaurado com os saves.'); });
action('pf-purge', el => { S.modal = 'pf-purge'; S.mp = { v: el.dataset.v }; render(); });
action('pf-purge-go', el => { const i = PROFILE_TRASH.findIndex(p => p.xuid === el.dataset.v); if (i >= 0) PROFILE_TRASH.splice(i, 1); S.modal = null; toast('Removido de vez.'); });
action('pf-saves', el => { S.modal = 'pf-saves'; S.mp = { v: el.dataset.v }; render(); });
modal('pf-slot', mp => {
  const n = mp.n, cur = pfSlot(n);
  return { html: sheetHead(n === 1 ? 'Quem é o ativo (P1)' : `O jogador ${n} entra como`) + `<ul class="menu">${n > 1 ? `<li><button data-act="pf-slot-set" data-n="${n}" data-v="" data-k="ss-none"${cur ? '' : ' data-autofocus'}>${ic('x', 19)}<span>Ninguém</span></button></li>` : ''}${PROFILES.map((p, i) => `<li><button data-act="pf-slot-set" data-n="${n}" data-v="${p.xuid}" data-k="ss-${p.xuid}"${(cur ? cur === p : i === 0 && n === 1) ? ' data-autofocus' : ''}>${avatarImg(p)}<span>${esc(p.tag)}<small>${p.active ? 'Ativo · P1' : p.slot && p.slot !== n ? `Sai do jogador ${p.slot}` : esc(p.lang)}</small></span>${cur === p ? ic('check', 18) : ''}</button></li>`).join('')}</ul>${n === 1 ? '<p class="note">O ativo entra como P1. Se ele era de outro jogador, essa vaga fica sem ninguém.</p>' : ''}` };
});
modal('pf-menu', mp => { const p = pfBy(mp.v); return { html: sheetHead(esc(p.tag), pfStatus(p) || esc(p.xuid)) + `<ul class="menu">${p.active ? '' : `<li><button data-act="pf-active" data-v="${p.xuid}" data-k="pm-act" data-autofocus>${ic('user', 19)}<span>Tornar ativo<small>Entra como P1 na próxima abertura</small></span></button></li>`}<li><button data-act="pf-edit" data-v="${p.xuid}" data-k="pm-edit"${p.active ? ' data-autofocus' : ''}>${ic('text', 19)}<span>Editar<small>Gamertag, avatar, idioma e região</small></span></button></li><li><button data-act="pf-saves" data-v="${p.xuid}" data-k="pm-saves">${ic('save', 19)}<span>Saves<small>${p.games} ${p.games === 1 ? 'jogo' : 'jogos'} · ${p.mb} MB</small></span></button></li><li><button data-act="pf-trash" data-v="${p.xuid}" data-k="pm-trash">${ic('trash', 19)}<span>Mandar para a lixeira</span></button></li></ul>` }; });
modal('pf-edit', mp => {
  const p = mp.v ? pfBy(mp.v) : null, lang = p ? (LANGS.find(o => o[1] === p.lang) || LANGS[0])[0] : '9', reg = p ? (REGIONS.find(o => o[1] === p.region) || REGIONS[0])[0] : '13';
  return { html: sheetHead(p ? 'Editar perfil' : 'Criar perfil') + `<div class="row" style="gap:14px;align-items:center" data-note="pfform">${p ? avatarImg(p, 'avatar-l') : `<span class="avatar-l" style="display:grid;place-items:center;background:var(--s3)">${ic('user', 28)}</span>`}<button class="btn sm ghost" data-act="toast" data-msg="Escolha uma imagem (seletor do Android); até 512 px por lado." data-k="pf-av">${ic('image', 15)} Escolher avatar</button></div>
    <label class="fld">Gamertag<input class="txt plain" id="pf-tag" type="text" maxlength="15" value="${p ? esc(p.tag) : ''}" placeholder="Ex.: XenPlayer" data-k="pf-tag" autocomplete="off" spellcheck="false" data-autofocus></label>
    <p class="errline" id="pf-err" hidden>${ic('warn', 15)} Digite uma gamertag válida (1 a 15 caracteres, começando com uma letra).</p>
    <div class="grid2"><label class="fld">Idioma<select class="sel" id="pf-lang" data-k="pf-lang" style="max-width:none">${LANGS.map(([v, t]) => `<option value="${v}"${v === lang ? ' selected' : ''}>${t}</option>`).join('')}</select></label><label class="fld">Região<select class="sel" id="pf-reg" data-k="pf-reg" style="max-width:none">${REGIONS.map(([v, t]) => `<option value="${v}"${v === reg ? ' selected' : ''}>${t}</option>`).join('')}</select></label></div>
    <p class="note">1 a 15 letras ou dígitos, começando com uma letra. O idioma e a região deste perfil valem nos jogos em que ele entrar.</p>
    <div class="acts"><button class="btn ghost" data-act="close" data-k="pf-x">Cancelar</button><button class="btn primary" data-act="pf-save" data-v="${p ? p.xuid : ''}" data-k="pf-ok">${p ? 'Salvar' : 'Criar'}</button></div>` };
});
modal('pf-trash', mp => {
  const p = pfBy(mp.v), gs = gamesOfProfile(p.xuid).map(id => GBY[id] ? GBY[id].name : id);
  return { html: sheetHead('Mandar o perfil para a lixeira?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">“${esc(p.tag)}” some da lista de perfis e de todos os jogos. ${gs.length ? `Isso também tira os dados salvos de ${gs.length} ${gs.length === 1 ? 'jogo' : 'jogos'}: ${gs.map(esc).join(', ')} (${p.files} arquivos, ${p.mb} MB).` : 'Ele não tem saves de jogo neste aparelho.'} Tudo fica na lixeira deste aparelho até você remover de vez, e pode ser restaurado nesta tela.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="tr-x" data-autofocus>Cancelar</button><button class="btn danger" data-act="pf-trash-go" data-v="${p.xuid}" data-k="tr-go">Mandar para a lixeira</button></div>` };
});
modal('pf-purge', mp => { const p = PROFILE_TRASH.find(x => x.xuid === mp.v); return { html: sheetHead('Remover de vez?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">O perfil ${esc(p.tag)} (<span class="mono">${p.xuid}</span>) da lixeira e todos os saves guardados com ele serão apagados deste aparelho. Isso não pode ser desfeito.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="pu-x" data-autofocus>Cancelar</button><button class="btn danger" data-act="pf-purge-go" data-v="${p.xuid}" data-k="pu-go">Remover de vez</button></div>` }; });
modal('pf-saves', mp => { const p = pfBy(mp.v), gs = gamesOfProfile(p.xuid); return { html: sheetHead(`Saves de ${esc(p.tag)}`, `${gs.length} ${gs.length === 1 ? 'jogo' : 'jogos'} neste aparelho`) + `<div class="list" data-note="svgames">${gs.map((gid, i) => { const g = GBY[gid], s = SAVES[gid].find(x => x.xuid === p.xuid); return drow({ img: `<span style="width:34px;display:block">${coverHTML(g)}</span>`, t: esc(g.name), s: `${s.files} arquivos · ${fmtKB(s.kb)} · último save ${esc(s.when)}`, acts: `<button class="btn sm" data-act="go" data-v="saves" data-p="${gid}" data-k="psg-${gid}"${i === 0 ? ' data-autofocus' : ''}>Abrir</button>` }); }).join('')}</div>` }; });

/* jogar como: a biblioteca pergunta antes de abrir */
const playDirect = ACT.play;
action('play', el => { if (PFS.ask && !(el.dataset && el.dataset.asked)) { S.modal = 'playas'; S.mp = { gid: (el.dataset && el.dataset.gid) || S.gameId }; render(); return; } playDirect(el); });
action('playas-go', el => { const p = pfBy(el.dataset.v); if (p && !p.active) ACT['pf-active']({ dataset: { v: p.xuid } }); if ((document.getElementById('pa-dont') || {}).getAttribute && document.getElementById('pa-dont').getAttribute('aria-checked') === 'true') PFS.ask = false; playDirect({ dataset: { gid: el.dataset.gid, asked: '1' } }); });
modal('playas', mp => { const g = GBY[mp.gid] || curGame(); return { html: sheetHead('Jogar como', esc(g.name)) + `<ul class="menu" data-note="playas">${PROFILES.map((p, i) => `<li><button data-act="playas-go" data-v="${p.xuid}" data-gid="${g.id}" data-k="pa-${p.xuid}"${p.active ? ' data-autofocus' : ''}>${avatarImg(p)}<span>${esc(p.tag)}<small>${p.active ? 'Ativo · P1' : p.slot ? `Ele joga como P${p.slot} agora; escolher aqui o leva para o P1 e o P${p.slot} fica sem ninguém.` : esc(p.lang)}</small></span></button></li>`).join('')}</ul>
  <div class="row"><button class="tg" role="switch" aria-checked="false" id="pa-dont" data-act="tg-local" data-k="pa-dont" aria-label="Não perguntar de novo"></button><span style="font-size:13.5px">Não perguntar de novo</span></div><p class="note">Cada perfil mantém os próprios saves.</p>` }; });

screen('profiles', {
  title: 'Perfis', secKey: 'profiles', globalScope: true,
  render() {
    const secs = [
      { id: 'list', t: 'Perfis', icon: 'user', n: PROFILES.length, title: 'Perfis', body: pfListHTML },
      { id: 'players', t: 'Quem joga', icon: 'gamepad', title: 'Quem joga', lead: 'O perfil ativo é o P1. Os outros jogadores entram com o perfil escolhido aqui.', body: pfPlayersHTML },
      { id: 'trash', t: 'Lixeira', icon: 'trash', n: PROFILE_TRASH.length || '', title: 'Lixeira de perfis', body: pfTrashHTML },
    ];
    const a = activeProfile();
    return sectioned({ key: 'profiles', title: 'Perfis', sub: `${PROFILES.length} perfis · ativo: ${esc(a.tag)}`, csub: `Ativo: ${esc(a.tag)}`, plainSub: true, icon: 'user', actions: `<button class="btn sm" data-act="pf-edit" data-v="" data-k="pf-new-top">${ic('plus', 15)} Criar perfil</button>`, sections: secs });
  },
});

/* ---------- saves de um jogo ---------- */
const SV = { sel: {}, incl: true, open: {}, folder: 'Google Drive › XenDroid › Backups', auto: true, last: 'hoje, 14:02', replace: false, busy: null, timer: 0 };
const SAVE_NAMES = {
  '4D5307E6': { E03000A1B2C3D4E5: [['Campanha · Lendário', 'hoje, 13:58', 1434], ['Perfil do jogador', 'hoje, 13:58', 486], ['Filmes salvos (3)', '28/09/2026', 320]], E03000F6A7B8C9D0: [['Campanha · Normal', '21/09/2026', 480], ['Perfil do jogador', '21/09/2026', 32]] },
};
const fmtKB = kb => kb >= 1024 ? (kb / 1024).toFixed(1).replace('.', ',') + ' MB' : kb + ' KB';
function svNames(gid, x, s) { const n = SAVE_NAMES[gid] && SAVE_NAMES[gid][x]; if (n) return n; return [['Progresso', s.when, Math.round(s.kb * .8)], ['Configurações', s.when, Math.round(s.kb * .2)]]; }
function svHereHTML(v) {
  const g = GBY[S.route.p.gid] || curGame(), list = SAVES[g.id] || [], nsel = list.filter(s => SV.sel[s.xuid]).length;
  if (!list.length) return '<p class="note">Nenhum save encontrado para este jogo.</p>';
  return `<p class="note" style="margin-bottom:10px">Os backups guardam os cabeçalhos dos saves e os XUIDs originais dos perfis. Feche o jogo antes de fazer backup ou restaurar.</p>
    <div class="card" data-note="svnames"><div class="list">${list.map(s => { const p = profileOf(s.xuid), names = svNames(g.id, s.xuid, s); return `<div class="sv-prof"><button class="chk" role="checkbox" aria-checked="${!!SV.sel[s.xuid]}" data-act="sv-sel" data-v="${s.xuid}" data-k="svs-${s.xuid}" aria-label="Escolher ${esc(p ? p.tag : s.xuid)}" data-note="svsel">${ic('check', 15)}</button>${p ? avatarImg(p, '') : `<span class="lead">${ic('user', 18)}</span>`}<div><b>${esc(p ? p.tag : 'Perfil que não está neste aparelho')}${p && p.removed ? ' <span class="badge">na lixeira</span>' : ''}</b><small><span class="mono">${s.xuid}</span> · ${s.files} ${s.files === 1 ? 'arquivo' : 'arquivos'} · ${fmtKB(s.kb)} · último save ${esc(s.when)}</small></div><button class="btn sm ghost" data-act="sv-open" data-v="${s.xuid}" data-k="svo-${s.xuid}" aria-expanded="${!!SV.open[s.xuid]}">${SV.open[s.xuid] ? 'Fechar' : 'Ver saves'}</button>${SV.open[s.xuid] ? `<div class="sv-files">${names.map(([n, w, kb]) => `<span>${esc(n)}<i>${esc(w)} · ${fmtKB(kb)}</i></span>`).join('')}</div>` : ''}</div>`; }).join('')}</div></div>
    <div class="row" style="margin-top:12px"><button class="btn sm${SV.incl ? ' primary' : ''}" data-act="sv-incl" data-k="sv-incl" aria-pressed="${SV.incl}">${ic(SV.incl ? 'check' : 'plus', 14)} Incluir o perfil associado</button><span class="sp"></span><button class="btn sm primary" data-act="sv-export" data-k="sv-export"${nsel ? '' : ' disabled'}>${ic('upload', 15)} Exportar ${nsel ? `(${nsel})` : 'os perfis escolhidos'}</button><button class="btn sm ghost" data-act="sv-import" data-k="sv-import">${ic('download', 15)} Importar backup</button></div>`;
}
function svSyncHTML(v) {
  return `<div class="stack" data-note="svsync"><p class="note">Escolha uma pasta local ou na nuvem oferecida pelo Android. Os envios não são alterados depois; conflitos só são restaurados com a sua confirmação.</p>
    <section class="card"><h3>${ic('cloud', 15)} Pasta</h3>${SV.folder ? `<b style="font-size:14px">${esc(SV.folder)}</b><dl class="kv"><dt>Última sincronização</dt><dd>${esc(SV.last)}</dd></dl>` : '<p style="font-size:13.5px">Nenhuma pasta escolhida.</p>'}
      <div class="row"><button class="btn sm${SV.folder ? ' ghost' : ''}" data-act="sv-folder" data-k="sv-folder">${ic('folder', 15)} ${SV.folder ? 'Trocar a pasta' : 'Escolher a pasta de sincronização'}</button>${SV.folder ? `<button class="btn sm" data-act="sv-sync" data-k="sv-sync">${ic('refresh', 15)} Sincronizar agora</button>` : ''}</div></section>
    ${SV.folder ? `${v === 'c' ? `<button class="crow" data-act="sv-auto" data-k="sv-auto"><span class="t"><b>Fazer backup ao voltar para a biblioteca</b><small>Com o jogo fechado</small></span><span class="tg" role="presentation" aria-checked="${SV.auto}"></span></button>` : `<div class="srow"><div><div class="srow-t">Fazer backup ao voltar para a biblioteca</div><div class="srow-m"><span class="src">Este jogo</span></div></div><div class="srow-c"><button class="tg" role="switch" aria-checked="${SV.auto}" data-act="sv-auto" data-k="sv-auto" aria-label="Fazer backup ao voltar para a biblioteca"></button></div><p class="srow-d">Com o jogo fechado, um backup verificado vai para a pasta.</p></div>`}
    <section class="card"><h3>${ic('layers', 15)} Backups na pasta<span class="r">${SAVE_BACKUPS.length}</span></h3><div class="list">${SAVE_BACKUPS.length ? SAVE_BACKUPS.map((b, i) => drow({ icon: 'zip', t: esc(b.when), s: `${esc(b.name)} · ${b.size}`, acts: `<button class="btn sm" data-act="sv-review" data-v="${i}" data-k="svb-${i}">Restaurar…</button>` })).join('') : '<p class="note">Nenhum backup verificado com o nome deste título.</p>'}</div></section>` : ''}
  </div>`;
}
function svBusy(msg, done) { clearTimeout(SV.timer); SV.busy = msg; S.modal = 'sv-busy'; render(); SV.timer = setTimeout(() => { SV.busy = null; S.modal = null; done(); }, 1400); }
action('sv-sel', el => { SV.sel[el.dataset.v] = !SV.sel[el.dataset.v]; render(); });
action('sv-open', el => { SV.open[el.dataset.v] = !SV.open[el.dataset.v]; render(); });
action('sv-incl', () => { SV.incl = !SV.incl; render(); });
action('sv-export', () => svBusy('Fazendo backup dos saves…', () => toast('Backup criado com os XUIDs originais e os cabeçalhos dos saves.')));
action('sv-import', () => { S.modal = 'sv-review'; S.mp = { v: 'file' }; render(); });
action('sv-review', el => { S.modal = 'sv-review'; S.mp = { v: el.dataset.v }; render(); });
action('sv-replace', () => { SV.replace = !SV.replace; render(); });
action('sv-restore', () => svBusy('Restaurando os saves…', () => { S.modal = 'sv-done'; render(); }));
action('sv-folder', () => { SV.folder = SV.folder ? 'Neste aparelho › Documentos › XenDroid' : 'Google Drive › XenDroid › Backups'; toast('Pasta escolhida (seletor do Android).'); });
action('sv-sync', () => svBusy('Copiando o backup verificado para a pasta escolhida…', () => { SV.last = 'agora'; const g = GBY[S.route.p.gid] || curGame(); SAVE_BACKUPS.unshift({ name: `${g.id} · 2026-10-04 15:20.zip`, when: '04/10/2026 15:20', size: '2,8 MB' }); toast(`Backup verificado na pasta escolhida: ${g.id} · 2026-10-04 15:20.zip`); }));
action('sv-auto', () => { SV.auto = !SV.auto; render(); });
modal('sv-busy', () => ({ html: `<div class="busy"><b style="font-size:15px">${esc(SV.busy || '')}</b><div class="bar ind"><i></i></div></div>` }));
modal('sv-review', mp => {
  const g = GBY[S.route.p.gid] || curGame(), b = mp.v === 'file' ? { name: `xendroid-saves-${g.id}.zip`, when: 'arquivo escolhido' } : SAVE_BACKUPS[Number(mp.v)];
  const xs = (SAVES[g.id] || []).map(s => s.xuid), names = xs.map(x => { const p = profileOf(x); return p ? `${p.tag} (${x})` : x; });
  return { html: sheetHead(`Restaurar saves de ${esc(g.name)}?`, esc(b.name)) + `<div class="card" data-note="svreview"><p style="font-size:13.5px">${xs.length * 3} arquivos · XUIDs originais: ${names.map(esc).join(', ')}</p><p style="font-size:13.5px">${xs.length} ${xs.length === 1 ? 'pasta de save já existe' : 'pastas de save já existem'} neste aparelho. Os saves atuais recebem backup antes de serem substituídos.</p></div>
    <div class="row"><button class="tg" role="switch" aria-checked="${SV.replace}" data-act="sv-replace" data-k="sv-repl" aria-label="Substituir também os dados de perfil existentes"></button><span style="font-size:13.5px">Substituir também os dados de perfil existentes</span></div>
    <div class="acts"><button class="btn ghost" data-act="close" data-k="sr-x">Cancelar</button><button class="btn primary" data-act="sv-restore" data-k="sr-go" data-autofocus>Restaurar</button></div>` };
});
modal('sv-done', () => ({ html: sheetHead('Pronto') + `<p class="okline" style="font-size:13.5px">${ic('checkC', 16)} Saves restaurados. As pastas originais ficaram em content/.save-transactions/2026-10-04T15-21 para recuperação.</p><div class="acts"><button class="btn primary" data-act="close" data-k="sd-ok" data-autofocus>OK</button></div>` }));

screen('saves', {
  title: 'Saves do jogo', secKey: 'saves', globalScope: true,
  render() {
    const g = GBY[S.route.p.gid] || curGame(), n = (SAVES[g.id] || []).length;
    const secs = [
      { id: 'here', t: 'Neste aparelho', icon: 'save', n: n || '', title: 'Saves neste aparelho', body: svHereHTML },
      { id: 'sync', t: 'Sincronização', icon: 'cloud', title: 'Sincronização opcional de backups', body: svSyncHTML },
    ];
    return sectioned({ key: 'saves', title: `Saves · ${g.name}`, sub: `${g.id} · ${n} ${n === 1 ? 'perfil' : 'perfis'} com saves`, csub: `${n} ${n === 1 ? 'perfil' : 'perfis'}`, plainSub: true, head: `<span style="width:40px;flex:none">${coverHTML(g)}</span>`, art: art(g), dyn: dynVars(g), icon: 'save', sections: secs });
  },
  onBack() { if (SV.busy) return true; return false; },
  onKey(k) { if (S.modal || k !== 'x') return false; const a = document.activeElement, r = a && a.closest && a.closest('.sv-prof'); if (!r) return false; const b = r.querySelector('[data-act="sv-open"]'); if (b) { ACT['sv-open'](b); focusKey(b.dataset.k); } return true; },
});
