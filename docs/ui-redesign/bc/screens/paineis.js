/* Lote 7: painéis que o próprio jogo pede (mensagem, teclado, troca de disco), sobre a cena do jogo. */
'use strict';

Object.assign(NOTES, {
  gpfrom: ['novo', 'Qual jogo está perguntando e que ele espera a resposta: o jogo fica parado até você escolher.'],
  gpopts: ['existe', 'Opções do jogo em linhas inteiras (frases longas não quebram mal); sem cancelar, porque o jogo espera uma resposta.'],
  gplong: ['existe', 'Texto longo rola dentro do painel e os botões continuam à vista.'],
  kbfield: ['existe', 'Campo com o limite do jogo contado como o jogo conta (UTF-16) e o teclado do telefone no toque.'],
  kbgrid: ['existe', 'Teclado em grade para o controle (U10): letras e símbolos, Shift, espaço, apagar, cursor, Pronto e Cancelar.'],
  kbpt: ['novo', 'Teclas de comando em português (hoje Shift, Space, Done e Cancel ficam em inglês).'],
  kbhints: ['existe', 'Atalhos do Xbox 360: X apaga, Y espaço, LB/RB movem o cursor, L3 Shift, R3 símbolos, Start conclui.'],
  dsopts: ['existe', 'Os discos do título encontrados na pasta de jogos, com Cancelar por último.'],
  dswarn: ['novo', 'Avisa que cancelar deixa o jogo sem disco (o jogo ejeta antes de perguntar).'],
  dsfind: ['novo', 'Sem disco achado: procurar o arquivo do disco em vez de só cancelar.'],
});

function gpFrom(g, what) { return `<div class="gp-from" data-note="gpfrom">${coverHTML(g)}<span><b>${esc(g.name)}</b> ${what}</span></div>`; }
function gpScene(g, inner, cls = '') {
  return `<div class="ig"><img class="ig-scene" src="${g.scene || ''}" alt="" style="filter:brightness(.55)">${!isC() ? touchOverlay(.35) : ''}<div class="gp-wrap ${cls}">${inner}</div></div>`;
}
function gpAnswer(msg) { IG.menu = false; S.modal = null; go('ingame', {}, { replace: true }); toast(msg); }

/* ---------- mensagem do jogo ---------- */
const MB = { v: 'save' };
const MB_EX = {
  save: { gid: '4D5307E6', title: 'Dispositivo de armazenamento', text: 'Nenhum dispositivo de armazenamento selecionado. Sem um, o seu progresso não será salvo. Deseja continuar?', btn: ['Selecionar dispositivo', 'Continuar sem salvar'] },
  live: { gid: '4D5309C9', title: 'Xbox LIVE', text: 'Este recurso precisa de uma conta Xbox LIVE com acesso online. Entre com um perfil habilitado para online e tente de novo.', btn: [] },
  long: { gid: '5454082B', title: 'Contrato de licença', text: 'Ao jogar você concorda com os termos de uso do jogo. '.repeat(14).trim() + ' Os dados de progresso ficam no perfil ativo.', btn: ['Aceitar', 'Recusar'] },
};
screen('msgbox', {
  title: 'Mensagem do jogo', lote: 'Lote 7 · Painéis do jogo',
  info: {
    what: 'Quando o jogo mostra uma mensagem com botões, o painel aparece sobre a cena parada: qual jogo pergunta, o título, o texto (que rola se for longo) e as opções em linhas inteiras. No controle, o direcional escolhe e A confirma.',
    replaces: 'GuestMessageBoxPanel.kt (XamShowMessageBoxUI): painel no topo com título, texto e opções em largura total, sem cancelar.',
    changes: ['Mostra qual jogo está perguntando e que ele espera a resposta', 'A opção em foco destacada com o botão A no modo controle'],
    c: 'O direcional escolhe e A confirma; B não fecha, porque o jogo espera uma resposta.',
    code: 'ui/messagebox/GuestMessageBoxPanel.kt, ui/panel/GuestPanelOption.kt, EmulatorHostActivity.kt',
  },
  variants: [{ label: 'Exemplo', list: [['save', 'Salvar sem dispositivo'], ['live', 'Só OK'], ['long', 'Texto longo']], get: () => MB.v, set: v => { MB.v = v; } }],
  render() {
    const m = MB_EX[MB.v], g = GBY[m.gid], btn = m.btn.length ? m.btn : ['OK'];
    return gpScene(g, `<section class="gp" role="dialog" aria-label="${esc(m.title)}">${gpFrom(g, 'está esperando a sua resposta')}<h2>${esc(m.title)}</h2><p class="gp-text"${MB.v === 'long' ? ' data-note="gplong"' : ''}>${esc(m.text)}</p>
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
const KB = { v: 'name', text: 'Shepard', caret: 7, shift: 'off', sym: false };
const KB_EX = { name: { gid: '4D5307E6', desc: 'Digite o nome do seu personagem', max: 15, init: 'Shepard' }, msg: { gid: '4D5309C9', desc: 'Mensagem para o seu clube de carros', max: 60, init: '' } };
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
  title: 'Teclado do jogo', lote: 'Lote 7 · Painéis do jogo',
  info: {
    what: 'Quando o jogo pede um texto, o painel mostra o pedido do jogo, o campo com o limite de caracteres e um teclado em grade que funciona com o controle e com toques; no toque, o teclado do telefone também digita.',
    replaces: 'GuestKeyboardPanel.kt e KeyboardGrid.kt (XamShowKeyboardUI, U10): campo, grade de letras e símbolos, comandos e os atalhos do Xbox 360.',
    changes: ['Teclas de comando em português', 'Contador do limite do jogo junto do campo', 'Atalhos do controle sempre à vista no modo controle'],
    c: 'O direcional percorre a grade e A digita; X apaga, Y espaço, LB/RB movem o cursor, L3 Shift, R3 símbolos, Start conclui.',
    code: 'ui/keyboard/GuestKeyboardPanel.kt, KeyboardGrid.kt, EmulatorHostActivity.kt',
  },
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
  title: 'Troca de disco', lote: 'Lote 7 · Painéis do jogo',
  info: {
    what: 'Quando o jogo pede outro disco, o painel diz qual disco inserir e lista os discos do título encontrados na pasta de jogos, com o atual marcado; cancelar fica por último, com o aviso de que o jogo fica sem disco.',
    replaces: 'DiscSwapPanel.kt (XamSwapDisc): “Insira o disco N”, os discos achados e Cancelar por último; sem disco achado, a mensagem e Cancelar.',
    changes: ['O disco pedido em destaque e o atual marcado', 'Aviso de que cancelar deixa o jogo sem disco', 'Proposta: sem disco achado, procurar o arquivo'],
    c: 'O direcional escolhe e A insere; B equivale a Cancelar.',
    code: 'ui/disc/DiscSwapPanel.kt, EmulatorHostActivity.kt',
  },
  variants: [{ label: 'Exemplo', list: [['found', 'Discos achados'], ['none', 'Nenhum disco achado']], get: () => DS.v, set: v => { DS.v = v; } }],
  render() {
    const g = GBY['4D5307DF'] || curGame(), n = g.discs || 3, want = 2;
    const discs = DS.v === 'found' ? Array.from({ length: n }, (_, i) => i + 1) : [];
    return gpScene(g, `<section class="gp" role="dialog" aria-label="Insira o disco ${want}">${gpFrom(g, 'ejetou o disco 1')}<h2>Insira o disco ${want}</h2>
      ${discs.length ? `<div class="gp-opts" data-note="dsopts">${discs.map(d => `<button class="gp-opt${d === 1 ? ' quiet' : ''}" data-act="ds-pick" data-v="${d}" data-k="ds-${d}"${d === want ? ' data-autofocus' : ''}>${ic('disc', 20)}<span>Disco ${d}${d === want ? ' <span class="badge acc">pedido</span>' : ''}${d === 1 ? ' <span class="badge">o de antes</span>' : ''}<small>${esc(g.name)} (Disco ${d}).iso</small></span></button>`).join('')}<button class="gp-opt quiet" data-act="ds-cancel" data-k="ds-x">${ic('x', 20)}<span>Cancelar<small data-note="dswarn">O jogo já ejetou o disco 1: cancelar o deixa sem disco.</small></span></button></div>`
        : `<p class="gp-text">Nenhum disco deste título foi encontrado na pasta de jogos.</p><div class="gp-opts"><button class="gp-opt" data-act="ds-find" data-k="ds-find" data-autofocus data-note="dsfind">${ic('folder', 20)}<span>Procurar o arquivo do disco ${want}<small>No navegador de pastas</small></span></button><button class="gp-opt quiet" data-act="ds-cancel" data-k="ds-x">${ic('x', 20)}<span>Cancelar<small data-note="dswarn">O jogo fica sem disco até você abrir o menu e trocar.</small></span></button></div>`}
      ${isC() ? hints([['A', 'Escolher', 'a'], ['B', 'Cancelar', 'back']]) : ''}</section>`);
  },
  after() { if (isC()) { setNav(true); focusStart(); } },
  onBack() { ACT['ds-cancel'](); return true; },
});
action('ds-pick', el => gpAnswer(`Disco ${el.dataset.v} inserido`));
action('ds-cancel', () => gpAnswer('Troca cancelada: o jogo está sem disco'));
action('ds-find', () => { BR.mode = 'file'; BR.path = ['Cartão SD', 'Jogos']; go('browse'); });
