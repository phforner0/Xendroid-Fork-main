/* Painéis que o próprio jogo pede (mensagem, teclado, troca de disco): GuestMessageBoxPanel.kt,
   GuestKeyboardPanel.kt e DiscSwapPanel.kt. Os textos de jogo aqui são de um jogo fictício. */
'use strict';

Object.assign(NOTES, {
  gpfrom: ['app', 'Qual jogo está perguntando: o jogo fica parado até você escolher.'],
  gpopts: ['app', 'As opções do jogo em linhas inteiras, para frases longas; sem cancelar, porque o jogo espera uma resposta.'],
  gplong: ['app', 'Texto longo rola dentro do painel e os botões continuam à vista.'],
  gpex: ['sim', 'O jogo e os textos destes painéis são exemplos; cada jogo escreve as próprias mensagens.'],
  kbfield: ['app', 'O campo respeita o limite de caracteres que o jogo pede; no toque, abre o teclado do telefone.'],
  kbgrid: ['app', 'Teclado em grade para o controle: letras e símbolos, Shift, espaço, apagar, cursor, Pronto e Cancelar.'],
  kbpt: ['app', 'As teclas de comando seguem o idioma do app.'],
  kbhints: ['app', 'Atalhos no controle: X apaga, Y põe espaço, LB/RB movem o cursor, L3 é Shift, R3 troca para símbolos e Start conclui.'],
  dsopts: ['app', 'Os discos do título encontrados nas pastas de jogos, com o arquivo de cada um e o disco de antes marcado.'],
  dswarn: ['app', 'O jogo já ejetou o disco antes de perguntar: cancelar o deixa sem disco.'],
});

function gpFrom(g, what) { return `<div class="gp-from" data-note="gpfrom">${coverHTML(g)}<span><b>${esc(g.name)}</b> ${what}</span></div>`; }
function gpScene(g, inner, cls = '') {
  return `<div class="ig"><img class="ig-scene" src="${g.scene || ''}" alt="" style="filter:brightness(.55)">${!isC() ? touchOverlay(.35) : ''}<div class="gp-wrap ${cls}">${inner}</div></div>`;
}
function gpAnswer(msg) { IG.menu = false; S.modal = null; go('ingame', {}, { replace: true }); toast(msg); }

/* ---------- mensagem do jogo ---------- */
const MB = { v: 'save' };
const MB_EX = {
  save: { gid: FICTIONAL, title: 'Salvar o progresso', text: 'Nenhum dispositivo de armazenamento escolhido. Sem um, o progresso não será salvo. Continuar? (mensagem de exemplo)', btn: ['Escolher um dispositivo', 'Continuar sem salvar'] },
  live: { gid: FICTIONAL, title: 'Recurso indisponível', text: 'Este recurso não está disponível agora. (mensagem de exemplo)', btn: [] },
  long: { gid: FICTIONAL, title: 'Texto longo', text: 'Um jogo pode mostrar um texto longo, como termos de uso ou instruções. '.repeat(10).trim() + ' (mensagem de exemplo)', btn: ['Aceitar', 'Recusar'] },
};
screen('msgbox', {
  title: 'Mensagem do jogo',
  variants: [{ label: 'Exemplo', list: [['save', 'Salvar sem dispositivo'], ['live', 'Só OK'], ['long', 'Texto longo']], get: () => MB.v, set: v => { MB.v = v; } }],
  render() {
    const m = MB_EX[MB.v], g = GBY[m.gid], btn = m.btn.length ? m.btn : ['OK'];
    return gpScene(g, `<section class="gp" role="dialog" aria-label="${esc(m.title)}" data-note="gpex">${gpFrom(g, 'está esperando a sua resposta')}<h2>${esc(m.title)}</h2><p class="gp-text"${MB.v === 'long' ? ' data-note="gplong"' : ''}>${esc(m.text)}</p>
      <div class="gp-opts" data-note="gpopts">${btn.map((b, i) => `<button class="gp-opt" data-act="mb-pick" data-v="${i}" data-k="mb-${i}"${i === 0 ? ' data-autofocus' : ''}>${esc(b)}${isC() && i === 0 ? '' : ''}</button>`).join('')}</div>
      <p class="gp-note">O jogo fica parado até você escolher.</p>
      ${isC() ? hints([['A', 'Escolher', 'a']]) : ''}</section>`);
  },
  after() { if (isC()) { setNav(true); focusStart(); } },
  onBack() { toast('O jogo espera uma resposta; escolha uma opção.'); return true; },
});
action('mb-pick', el => { const m = MB_EX[MB.v], btn = m.btn.length ? m.btn : ['OK']; gpAnswer(`Resposta enviada ao jogo: ${btn[Number(el.dataset.v)]}`); });

/* ---------- teclado do jogo ---------- */
const KB_LET = ['1234567890', 'qwertyuiop', 'asdfghjkl@', 'zxcvbnm,.-'], KB_SYM = ['1234567890', '!#$%&*()_+', '=/\\|[]{};:', '\'"<>?~`^.,'];
const KB = { v: 'name', text: 'Ana', caret: 3, shift: 'off', sym: false };
const KB_EX = { name: { gid: FICTIONAL, desc: 'Nome do personagem (exemplo)', max: 15, init: 'Ana' }, msg: { gid: FICTIONAL, desc: 'Uma mensagem (exemplo)', max: 60, init: '' } };
const kbMax = () => KB_EX[KB.v].max;
function kbType(ch) {
  if (KB.text.length + ch.length > kbMax()) { toast(`O jogo aceita até ${kbMax()} caracteres`); return; }
  const c = KB.shift !== 'off' && /[a-z]/.test(ch) ? ch.toUpperCase() : ch;
  KB.text = KB.text.slice(0, KB.caret) + c + KB.text.slice(KB.caret); KB.caret += c.length;
  if (KB.shift === 'once' && /[a-z]/i.test(ch)) KB.shift = 'off';
}
function kbCmd(c) {
  if (c === 'shift') KB.shift = { off: 'once', once: 'lock', lock: 'off' }[KB.shift];
  else if (c === 'page') KB.sym = !KB.sym;
  else if (c === 'space') kbType(' ');
  else if (c === 'back') { if (KB.caret > 0) { KB.text = KB.text.slice(0, KB.caret - 1) + KB.text.slice(KB.caret); KB.caret--; } }
  else if (c === 'left') KB.caret = Math.max(0, KB.caret - 1);
  else if (c === 'right') KB.caret = Math.min(KB.text.length, KB.caret + 1);
  else if (c === 'done') { gpAnswer(KB.text ? `Texto enviado ao jogo: “${KB.text}”` : 'Texto vazio enviado ao jogo'); return; }
  else if (c === 'cancel') { gpAnswer('Cancelado; o jogo recebe o texto de antes'); return; }
}
function kbRefresh(keepFocus) {
  const k = keepFocus ? (document.activeElement && document.activeElement.dataset.k) : null; render();
  const inp = document.getElementById('kb-in'); if (inp && !k) { try { inp.setSelectionRange(KB.caret, KB.caret); } catch (e) { /* sem seleção */ } }
}
screen('keyboard', {
  title: 'Teclado do jogo',
  variants: [{ label: 'Exemplo', list: [['name', 'Nome (até 15)'], ['msg', 'Mensagem (até 60)']], get: () => KB.v, set: v => { KB.v = v; KB.text = KB_EX[v].init; KB.caret = KB.text.length; KB.shift = 'off'; KB.sym = false; } }],
  render() {
    const ex = KB_EX[KB.v], g = GBY[ex.gid], rows = KB.sym ? KB_SYM : KB_LET;
    const key = (ch, i, r) => { const lab = KB.shift !== 'off' && /[a-z]/.test(ch) ? ch.toUpperCase() : ch; return `<button class="kb-k" data-act="kb-ch" data-v="${esc(ch)}" data-k="kk-${r}-${i}"${r === 1 && i === 0 ? ' data-autofocus' : ''}>${esc(lab)}</button>`; };
    const cmds = [['shift', 'Shift', KB.shift !== 'off'], ['page', KB.sym ? 'ABC' : '?123', KB.sym], ['space', 'Espaço'], ['back', '⌫'], ['left', '←'], ['right', '→'], ['done', 'Pronto'], ['cancel', 'Cancelar']];
    return gpScene(g, `<section class="gp kb" role="dialog" aria-label="${esc(ex.desc)}">${gpFrom(g, 'pede um texto')}
      <p class="gp-text" style="margin:0;color:var(--fg)">${esc(ex.desc)}</p>
      <label class="kb-field" data-note="kbfield"><input id="kb-in" type="text" value="${esc(KB.text)}" maxlength="${ex.max}" data-k="kb-in" autocomplete="off" spellcheck="false" aria-label="${esc(ex.desc)}"><span class="cnt">${KB.text.length}/${ex.max}</span></label>
      <div class="kb-grid" data-note="kbgrid">${rows.map((r, ri) => `<div class="kb-row">${[...r].map((ch, i) => key(ch, i, ri)).join('')}</div>`).join('')}
        <div class="kb-row cmd" data-note="kbpt">${cmds.map(([c, t, on]) => `<button class="kb-k c ${on ? 'on' : ''} ${c === 'done' ? 'done' : ''}" data-act="kb-cmd" data-v="${c}" data-k="kc-${c}">${t}${c === 'shift' && KB.shift === 'lock' ? ' ⇪' : ''}</button>`).join('')}</div></div>
      ${isC() ? hints([['X', 'Apagar', 'x'], ['Y', 'Espaço', 'y'], ['LB/RB', 'Cursor', 'tabs'], ['L3', 'Shift', 'l3'], ['R3', 'Símbolos', 'r3'], ['≡', 'Pronto', 'start']], 'kbhints') : ''}</section>`, 'kb');
  },
  after() { const inp = document.getElementById('kb-in'); if (inp && document.activeElement === inp) { try { inp.setSelectionRange(KB.caret, KB.caret); } catch (e) { /* sem seleção */ } } },
  onKey(k) {
    if (S.modal) return false;
    const map = { x: 'back', y: 'space', start: 'done', l3: 'shift', r3: 'page', view: 'page' };
    if (k === 'lb' || k === 'rb' || k === 'tabs') { kbCmd(k === 'lb' ? 'left' : 'right'); kbRefresh(true); return true; }
    if (map[k]) { kbCmd(map[k]); if (S.route.name === 'keyboard') kbRefresh(true); return true; }
    return false;
  },
  onBack() { kbCmd('cancel'); return true; },
});
action('kb-ch', el => { kbType(el.dataset.v); kbRefresh(true); });
action('kb-cmd', el => { kbCmd(el.dataset.v); if (S.route.name === 'keyboard') kbRefresh(true); });
action('input:kb-in', el => { KB.text = el.value.slice(0, kbMax()); KB.caret = el.selectionStart || KB.text.length; const c = el.parentElement.querySelector('.cnt'); if (c) c.textContent = `${KB.text.length}/${kbMax()}`; });

/* ---------- troca de disco ---------- */
const DS = { v: 'found' };
screen('discswap', {
  title: 'Troca de disco',
  variants: [{ label: 'Exemplo', list: [['found', 'Discos achados'], ['none', 'Nenhum disco achado']], get: () => DS.v, set: v => { DS.v = v; } }],
  render() {
    const g = GBY[FICTIONAL], n = 3, want = 2;
    const discs = DS.v === 'found' ? Array.from({ length: n }, (_, i) => i + 1) : [];
    return gpScene(g, `<section class="gp" role="dialog" aria-label="Insira o disco ${want}">${gpFrom(g, 'pede outro disco')}<h2>Insira o disco ${want}</h2>
      ${discs.length ? `<div class="gp-opts" data-note="dsopts">${discs.map(d => `<button class="gp-opt${d === 1 ? ' quiet' : ''}" data-act="ds-pick" data-v="${d}" data-k="ds-${d}"${d === want ? ' data-autofocus' : ''}>${ic('disc', 20)}<span>Disco ${d}<small>${d === 1 ? `${esc(g.name)} (Disco ${d}).iso · o de antes` : `${esc(g.name)} (Disco ${d}).iso`}</small></span></button>`).join('')}<button class="gp-opt quiet" data-act="ds-cancel" data-k="ds-x">${ic('x', 20)}<span>Cancelar<small data-note="dswarn">O jogo já ejetou o disco: cancelar o deixa sem disco.</small></span></button></div>`
        : `<p class="gp-text">Nenhum disco deste título foi encontrado na pasta de jogos.</p><div class="gp-opts"><button class="gp-opt quiet" data-act="ds-cancel" data-k="ds-x" data-autofocus>${ic('x', 20)}<span>Cancelar<small data-note="dswarn">O jogo já ejetou o disco: cancelar o deixa sem disco.</small></span></button></div>`}
      ${isC() ? hints([['A', 'Escolher', 'a'], ['B', 'Cancelar', 'back']]) : ''}</section>`);
  },
  after() { if (isC()) { setNav(true); focusStart(); } },
  onBack() { ACT['ds-cancel'](); return true; },
});
action('ds-pick', el => gpAnswer(`Disco ${el.dataset.v} inserido`));
action('ds-cancel', () => gpAnswer('Troca cancelada: o jogo está sem disco'));
