/* Lote 2: jogo aberto. Carregamento, falha ao abrir, menu em jogo e HUD, sobre uma cena gerada. */
'use strict';

Object.assign(NOTES, {
  ighandle: ['existe', 'Alça de toque para abrir o menu; Voltar e Guia também abrem.'],
  igstrip: ['novo', 'Linha de estado no topo do menu: FPS, p99, temperatura, bateria e o driver carregado, sem ligar o HUD.'],
  igtabs: ['existe', 'Abas Gráficos, Desempenho (hoje “Sistema”), Controles e Sessão, com “Mais opções” no fim de cada uma (U01).'],
  igscope: ['existe', 'Limite de FPS da sessão com salvar para o jogo, usar o global ou salvar como global (U01), agora numa linha só.'],
  igall: ['novo', 'Todos os ajustes deste jogo sem sair do jogo, valendo na próxima abertura.'],
  igphones: ['existe', 'Telefones como controle: código e endereço na própria linha (companion na rede local, P2–P4).'],
  igfg: ['existe', 'Geração de quadros experimental e desligada por padrão; em builds de lançamento aparece bloqueada com o motivo (restrita a builds de desenvolvimento até a validação no aparelho).'],
  ldsteps: ['existe', 'Etapas do boot (emulador, jogo, pipelines, primeiro quadro) e o aviso de primeira abertura lenta (15e).'],
  ldopts: ['novo', 'Mostra com que ajustes o jogo está abrindo (driver, limite, escala) e se veio de “Iniciar com…”.'],
  failacts: ['existe', 'Voltar, Tentar de novo num processo novo e Compartilhar logs (15e).'],
  faildrv: ['novo', 'Quando a falha aponta para o driver, oferece tentar uma vez com o driver do sistema.'],
  hudlv: ['existe', 'HUD em três níveis: compacto, completo e o painel de desempenho (15n), com aparência guardada por jogo (15g).'],
  thermal: ['existe', 'Aviso térmico consultivo pelo headroom do Android (15j); nunca muda ajustes sozinho.'],
  ighints: ['novo', 'No modo controle, dicas de botão no rodapé do menu: A seleciona, B fecha, LB/RB trocam de aba.'],
  tcl: ['existe', 'Controles de toque; somem quando um controle físico joga como P1 e voltam quando ele desconecta.'],
});

/* ---------- controles de toque desenhados ---------- */
const TOUCH_LAYOUT = {
  land: [
    { id: 'lt', k: 'sh', t: 'LT', x: 7, y: 21 }, { id: 'lb', k: 'sh', t: 'LB', x: 7, y: 34 },
    { id: 'rt', k: 'sh', t: 'RT', x: 93, y: 21 }, { id: 'rb', k: 'sh', t: 'RB', x: 93, y: 34 },
    { id: 'ls', k: 'stick', t: 'Analógico esquerdo', x: 12, y: 64, d: 118 }, { id: 'dpad', k: 'dpad', t: 'Direcional', x: 26, y: 84, d: 100 },
    { id: 'abxy', k: 'abxy', t: 'A B X Y', x: 88, y: 58, d: 128 }, { id: 'rs', k: 'stick', t: 'Analógico direito', x: 72, y: 82, d: 100 },
    { id: 'back', k: 'sm', t: 'Back', x: 42, y: 91 }, { id: 'start', k: 'sm', t: 'Start', x: 58, y: 91 },
  ],
  port: [
    { id: 'lt', k: 'sh', t: 'LT', x: 13, y: 32 }, { id: 'lb', k: 'sh', t: 'LB', x: 13, y: 38 },
    { id: 'rt', k: 'sh', t: 'RT', x: 87, y: 32 }, { id: 'rb', k: 'sh', t: 'RB', x: 87, y: 38 },
    { id: 'ls', k: 'stick', t: 'Analógico esquerdo', x: 25, y: 57, d: 132 }, { id: 'abxy', k: 'abxy', t: 'A B X Y', x: 75, y: 57, d: 140 },
    { id: 'dpad', k: 'dpad', t: 'Direcional', x: 25, y: 79, d: 112 }, { id: 'rs', k: 'stick', t: 'Analógico direito', x: 75, y: 79, d: 112 },
    { id: 'back', k: 'sm', t: 'Back', x: 40, y: 92 }, { id: 'start', k: 'sm', t: 'Start', x: 60, y: 92 },
  ],
};
const DPAD_SVG = '<svg viewBox="0 0 100 100" aria-hidden="true"><path d="M36 6h28a4 4 0 0 1 4 4v22a4 4 0 0 0 4 4h22a4 4 0 0 1 4 4v20a4 4 0 0 1-4 4H72a4 4 0 0 0-4 4v22a4 4 0 0 1-4 4H36a4 4 0 0 1-4-4V68a4 4 0 0 0-4-4H6a4 4 0 0 1-4-4V40a4 4 0 0 1 4-4h22a4 4 0 0 0 4-4V10a4 4 0 0 1 4-4z" fill="rgba(255,255,255,.1)" stroke="rgba(255,255,255,.42)" stroke-width="2.4"/><path d="M50 14l-6 8h12zM50 86l-6-8h12zM14 50l8-6v12zM86 50l-8-6v12z" fill="rgba(255,255,255,.7)"/></svg>';
function touchEl(e, extra = '', cls = '') {
  const pos = `left:${e.x}%;top:${e.y}%;${e.d ? `--d:${e.d}px;` : ''}${e.s && e.s !== 1 ? `scale:${e.s};` : ''}${e.op != null ? `--op:${e.op};` : ''}`;
  if (e.k === 'stick') return `<div class="tc tc-stick ${cls}" style="${pos}" data-tc="${e.id}" ${extra}></div>`;
  if (e.k === 'dpad') return `<div class="tc tc-dpad ${cls}" style="${pos}" data-tc="${e.id}" ${extra}>${DPAD_SVG}</div>`;
  if (e.k === 'abxy') return `<div class="tc tc-abxy ${cls}" style="${pos}" data-tc="${e.id}" ${extra}><span class="tc-btn y">Y</span><span class="tc-btn x">X</span><span class="tc-btn b">B</span><span class="tc-btn a">A</span></div>`;
  return `<div class="tc ${e.k === 'sh' ? 'tc-sh' : 'tc-sm'} ${cls}" style="${pos}" data-tc="${e.id}" ${extra}>${e.t}</div>`;
}
/* opacidade e liga/desliga vêm dos ajustes gerais do toque (lote 3); dim escurece com o menu aberto */
const touchOp = () => DEF['@touch.opacity'] ? globalOf('@touch.opacity') / 100 : .6;
function touchOverlay(dim) {
  if (!globalOf('HID.show_touch_overlay')) return '';
  const o = isPortrait() ? 'port' : 'land';
  return `<div class="tcl" style="--op:${(touchOp() * (dim || 1)).toFixed(2)}" data-note="tcl">${TOUCH_LAYOUT[o].filter(e => e.vis !== false).map(e => touchEl(e)).join('')}</div>`;
}

/* ---------- estado da sessão em jogo ---------- */
const IG = {
  menu: true, tab: 'gfx', more: {}, confirmQuit: false, logs: false,
  fps: '30', display: 'fit', scaling: 'fsr', color: 'off', fg: 'off', fgPreset: 'bal', stretchNext: false,
  hud: 'compact', hudLook: 'box', hz: 'auto', sustained: false, hints: true, metrics: new Set(['cpu', 'gpu', 'ram', 'bat', 'soc']),
  touch: true, adaptive: false, touchCam: false, split: 'off', phones: false, rumble: 'medium', gyroCam: false, gyroAim: 'always', gyroSens: 'normal', unbuffered: true,
  lsfgMul: '2', lsfgTarget: 'screen',
  volume: 80, muted: false, scenes: 0, background: true, tv: false, tvMargin: 0,
  t0: Date.now(), thermal: 'near',
};
const igSeg = (f, opts, note) => `<div class="seg" role="group"${note ? ` data-note="${note}"` : ''}>${opts.map(([v, t]) => `<button data-act="ig" data-f="${f}" data-v="${v}" aria-pressed="${String(IG[f]) === String(v)}" data-k="ig-${f}-${v}">${t}</button>`).join('')}</div>`;
const igTg = f => `<button class="tg" role="switch" aria-checked="${!!IG[f]}" data-act="ig" data-f="${f}" data-v="${!IG[f]}" data-k="ig-${f}" aria-label="${f}"></button>`;
const igRow = (t, s, ctl, opt = {}) => `<div class="igrow"${opt.note ? ` data-note="${opt.note}"` : ''}><div><b>${t}</b>${s ? `<small>${s}</small>` : ''}</div>${opt.below ? '' : `<div>${ctl || ''}</div>`}${opt.below ? `<div class="full">${ctl}</div>` : ''}</div>`;
const moreRow = (tab, n) => `<button class="igrow more" data-act="ig-more" data-v="${tab}" data-k="igm-more-${tab}" style="width:100%">${ic(IG.more[tab] ? 'chevD' : 'chevR', 16)} ${IG.more[tab] ? 'Menos opções' : `Mais opções (${n})`}</button>`;
const FPS_OPTS = [['30', '30'], ['45', '45'], ['60', '60'], ['90', '90'], ['120', '120'], ['0', '∞']];
function igGfx(g) {
  const sc = DEF['Display.postprocess_scaling_and_sharpening'];
  return igRow('Tela', 'Vale na hora, só nesta sessão', igSeg('display', [['fit', 'Ajustar'], ['fill', 'Preencher'], ['stretch', 'Esticar'], ['int', 'Inteira']]), { below: true })
    + igRow('Escala e nitidez', `Herdada do global: ${esc(label(sc, effOf(g, sc.k)))}`, `<select class="sel" data-act-change="igsel" data-f="scaling" data-k="ig-scaling" aria-label="Escala e nitidez">${sc.o.map(([v, t]) => `<option value="${v}"${IG.scaling === v ? ' selected' : ''}>${t}</option>`).join('')}</select>`)
    + igRow('TV / tela externa', IG.tv ? 'TV · Sala · 60 Hz' : 'Nenhuma tela de apresentação conectada', `<span class="stp"><button data-act="ig-step" data-f="tvMargin" data-d="-1" aria-label="Menos margem" data-k="ig-tvm-"${IG.tv ? '' : ' disabled'}>${ic('chevL', 16)}</button><b>Margem ${IG.tvMargin}%</b><button data-act="ig-step" data-f="tvMargin" data-d="1" aria-label="Mais margem" data-k="ig-tvm+"${IG.tv ? '' : ' disabled'}>${ic('chevR', 16)}</button></span>`)
    + igRow('Geração de quadros <span class="badge warn">experimental</span>', IG.fg === 'off' ? 'Interpolação experimental no host; cadência e latência no aparelho ainda não validadas.' : IG.fg === 'lsfg' ? `LSFG nativo com o seu Lossless.dll · ${IG.lsfgMul}×${IG.lsfgTarget === 'off' ? '' : ' pelo alvo'}` : `Win-FG 2× · predefinição ${{ qual: 'Qualidade', bal: 'Equilíbrio', perf: 'Desempenho' }[IG.fgPreset]}`, igSeg('fg', [['off', 'Desligada'], ['winfg', 'Win-FG 2×'], ['lsfg', 'LSFG']], 'igfg'), { below: true })
    + igRow('Driver', 'Escolha em Drivers ou nos ajustes deste jogo; vale quando um jogo abre', `<span class="badge ok">${esc(DRIVERS.lastRun.drv)}</span>`)
    + igRow('Todos os ajustes deste jogo', 'Valem na próxima abertura', `<button class="btn sm" data-act="modal" data-v="ig-settings" data-k="ig-all">${ic('sliders', 15)} Abrir</button>`, { note: 'igall' })
    + moreRow('gfx', 5)
    + (IG.more.gfx ? igRow('Filtro de cor', 'Filtro SDR opcional', igSeg('color', [['off', 'Desligado'], ['gray', 'Cinza'], ['contrast', 'Contraste'], ['warm', 'Quente'], ['vivid', 'Vívido']]), { below: true })
      + igRow('Esticar na próxima abertura', 'Fica salvo', igTg('stretchNext'))
      + (IG.fg === 'winfg' ? igRow('Predefinição do Win-FG', '', igSeg('fgPreset', [['qual', 'Qualidade'], ['bal', 'Equilíbrio'], ['perf', 'Desempenho']]), { below: true }) : '')
      + (IG.fg === 'lsfg' ? igRow('Alvo do LSFG <span class="badge warn">experimental</span>', IG.lsfgTarget === 'off' ? 'Desligado: multiplicador manual' : IG.lsfgTarget === 'screen' ? `A tela, 120 Hz → 4× com o jogo a ${IG.fps === '0' ? 34 : IG.fps} FPS` : `60 FPS → 2× com o jogo a ${IG.fps === '0' ? 34 : IG.fps} FPS`, igSeg('lsfgTarget', [['off', 'Manual'], ['screen', 'A tela'], ['60', '60 FPS']]), { below: true })
        + igRow('Multiplicador LSFG <span class="badge warn">experimental</span>', IG.lsfgTarget === 'off' ? 'Escolhido à mão' : 'Calculado pelo alvo; escolha um para usar à mão', igSeg('lsfgMul', [['2', '2×'], ['3', '3×'], ['4', '4×']])) : '')
      + igRow('Importar meu Lossless.dll', 'O app nunca traz a DLL nem shaders extraídos', `<button class="btn sm ghost" data-act="toast" data-msg="Escolha o Lossless.dll (seletor do Android)." data-k="ig-lsfgdll">Importar</button>`)
      + igRow('Remover o cache de shaders LSFG importado', '', `<button class="btn sm ghost" data-act="toast" data-msg="Cache LSFG removido (protótipo)." data-k="ig-lsfgclr">Remover</button>`) : '');
}
function igPerf(g) {
  const gl = globalOf('GPU.framerate_limit'), gm = OV[g.id] && OV[g.id]['GPU.framerate_limit'];
  const lbl = v => v === '0' ? 'sem limite' : v + ' FPS';
  return igRow('Limite de FPS', `Agora: ${lbl(IG.fps)} · só nesta sessão. Próxima abertura: ${gm ? 'jogo ' + lbl(gm) : 'global ' + lbl(gl)}.`, igSeg('fps', FPS_OPTS) + `<div class="scope3"><button class="btn sm" data-act="ig-fps" data-v="game" data-k="ig-fps-game">Salvar para este jogo</button><button class="btn sm ghost" data-act="ig-fps" data-v="inherit" data-k="ig-fps-inh"${gm ? '' : ' disabled'}>Usar o global (${lbl(gl)})</button></div>`, { below: true, note: 'igscope' })
    + igRow('HUD de desempenho', '', igSeg('hud', [['off', 'Desligado'], ['compact', 'Compacto'], ['full', 'Completo'], ['panel', 'Painel']]), { below: true })
    + igRow('Aparência do HUD', 'Guardada para este jogo', igSeg('hudLook', [['box', 'Caixa'], ['outline', 'Texto com contorno'], ['plain', 'Texto simples']]), { below: true })
    + igRow('Taxa de atualização da tela', `Pedido ${IG.hz === 'auto' ? 'automático' : IG.hz + ' Hz'} · efetivo ${IG.hz === 'auto' ? 120 : IG.hz} Hz`, igSeg('hz', [['auto', 'Auto'], ['60', '60'], ['90', '90'], ['120', '120']]), { below: true })
    + moreRow('perf', 4)
    + (IG.more.perf ? igRow('Salvar o limite atual como global', '', `<button class="btn sm ghost" data-act="ig-fps" data-v="global" data-k="ig-fps-global">Salvar como global</button>`)
      + igRow('Desempenho sustentado', 'Desligado por padrão: em muitos aparelhos limita os clocks', igTg('sustained'))
      + igRow('Dicas ADPF do apresentador', '', igTg('hints'))
      + igRow('Métricas do HUD completo', '', `<div class="row">${[['vk', 'Envios do Vulkan'], ['cpu', 'CPU'], ['gpu', 'GPU'], ['ram', 'RAM'], ['bat', 'Bateria °C'], ['soc', 'SoC °C'], ['pwr', 'Potência'], ['gmem', 'Memória da GPU']].map(([v, t]) => `<button class="chip" data-act="ig-metric" data-v="${v}" aria-pressed="${IG.metrics.has(v)}" data-k="ig-m-${v}">${t}</button>`).join('')}</div>`, { below: true }) : '');
}
function igCtl() {
  return igRow('Controles de toque · esta sessão', 'Somem quando um controle físico joga como P1', igTg('touch'))
    + igRow('Analógicos adaptativos · este jogo', 'Toque perto da posição salva e o analógico vai para baixo do dedo', igTg('adaptive'))
    + igRow('Câmera por toque', 'No lado direito, longe dos botões, deslize para girar a câmera', igTg('touchCam'))
    + igRow('Editar layout', 'Posição, tamanho, opacidade, esconder, vibração', `<button class="btn sm" data-act="go" data-v="touchedit" data-k="ig-edit">${ic('move', 15)} Editar</button>`)
    + igRow('Tela dividida', 'Jogo em cima, controles embaixo', igSeg('split', [['off', 'Desligada'], ['fold', 'Dobrável'], ['always', 'Sempre']]))
    + igRow('Telefones como controle', IG.phones ? 'Ligado · nenhum telefone conectado ainda (P2–P4)' : 'Outros telefones na mesma rede jogam como P2–P4', igTg('phones'), { note: 'igphones' })
    + (IG.phones ? `<div class="igrow"><div class="full"><small>No outro telefone: Biblioteca → Controles → Celular como controle</small><div class="row" style="margin-top:6px;gap:16px"><span><small>Endereço</small><b class="mono">192.168.1.20:41234</b></span><span><small>Código</small><span class="code6">482 913</span></span><span class="badge">Wi-Fi</span></div></div></div>` : '')
    + igRow('Vibração do controle', '8BitDo Ultimate 2C', igSeg('rumble', [['off', 'Desligada'], ['low', 'Baixa'], ['medium', 'Média'], ['high', 'Alta']]), { below: true })
    + igRow('Câmera pelo giroscópio', '', igTg('gyroCam'))
    + igRow('Mira pelo giroscópio', '', igSeg('gyroAim', [['always', 'Sempre'], ['lt', 'Segurando LT'], ['lb', 'Segurando LB']]))
    + igRow('Sensibilidade do giroscópio', '', igSeg('gyroSens', [['low', 'Baixa'], ['normal', 'Normal'], ['high', 'Alta']]))
    + moreRow('ctl', 2)
    + (IG.more.ctl ? igRow('Calibrar giroscópio', 'Deixe o telefone parado ao fechar o menu', `<button class="btn sm ghost" data-act="toast" data-msg="Calibra ao fechar o menu (protótipo)." data-k="ig-cal">Calibrar</button>`) + igRow('Entrada sem buffer', 'Menos atraso; Android 11 ou mais novo', igTg('unbuffered')) : '');
}
function igSes() {
  return (isC() ? igRow('Continuar', 'Fecha o menu e volta ao jogo', `<button class="btn sm primary" data-act="ig-close" data-k="ig-resume">${ic('play', 15)} Continuar</button>`) : '')
    + igRow('Som', IG.muted ? 'Mudo · toque no alto-falante para voltar' : 'Toque no alto-falante para silenciar', `<div class="row"><span class="stp"><button data-act="ig-step" data-f="volume" data-d="-10" aria-label="Volume −10%" data-k="ig-vol-">${ic('minus', 16)}</button><b>${IG.volume}%</b><button data-act="ig-step" data-f="volume" data-d="10" aria-label="Volume +10%" data-k="ig-vol+">${ic('plus', 16)}</button></span><button class="ibtn ${IG.muted ? 'on' : ''}" data-act="ig" data-f="muted" data-v="${!IG.muted}" data-k="ig-mute" aria-label="Mudo">${ic(IG.muted ? 'mute' : 'speaker', 19)}</button></div>`)
    + igRow('Marcar cena para comparar', `${IG.scenes} até agora · aparece na linha do tempo da sessão`, `<button class="btn sm ghost" data-act="ig-scene" data-k="ig-scene">${ic('flag', 15)} Marcar</button>`)
    + igRow('Compartilhar logs de diagnóstico', 'Escolha uma sessão; dados pessoais saem antes', `<button class="btn sm ghost" data-act="modal" data-v="ig-logs" data-k="ig-logs">${ic('share', 15)} Escolher</button>`)
    + moreRow('ses', 1)
    + (IG.more.ses ? igRow('Pausar em segundo plano', '', igTg('background')) : '')
    + igRow('Sair do jogo', 'O progresso não salvo pode ser perdido', `<button class="btn sm danger" data-act="modal" data-v="ig-quit" data-k="ig-quit">${ic('exit', 15)} Sair</button>`);
}
function hudHTML(kind, look) {
  if (kind === 'off') return '';
  const t = (Date.now() - IG.t0) / 1000, fps = IG.fps === '0' ? 34 : Math.max(0, Number(IG.fps) - (Math.sin(t) > .8 ? 1 : 0)), ms = fps ? (1000 / fps).toFixed(1).replace('.', ',') : '—';
  if (kind === 'compact') return `<div class="hud ${look}" data-note="hudlv"><b>${fps} FPS</b> <span class="k">·</span> ${ms} ms</div>`;
  const rows = [['FPS', `<span class="g">${fps}</span> · ${ms} ms`], ['CPU', '38% · 2,4 GHz'], ['GPU', '91% · 1,1 GHz'], ['RAM', '5,1 / 11,2 GB'], ['Bateria', '<span class="w">41 °C</span>'], ['SoC', '58 °C'], ['Potência', '7,2 W · 2 h 10 min']];
  if (kind === 'full') return `<div class="hud full ${look}" data-note="hudlv">${rows.map(([k, v]) => `<div><span class="k">${k}</span><span>${v}</span></div>`).join('')}</div>`;
  return `<div class="hud panel ${look}" data-note="hudlv">
    <section><h6>Agora</h6>${rows.slice(0, 4).map(([k, v]) => `<div style="display:flex;justify-content:space-between"><span class="k">${k}</span><span>${v}</span></div>`).join('')}</section>
    <section><h6>Ritmo</h6><div>Últimos 10 s: p50 33,4 ms · p99 41 ms</div><div>Sessão: FPS mediano 30, baixo 25</div></section>
    <section><h6>Trabalho</h6><div>Pipelines criados: 1.830 (6,4 s)</div><div>Áudio: sem falhas</div><div class="w">Calor: perto do limite do aparelho</div></section>
    <section><h6>Ajustes em vigor</h6><div>Driver: ${esc(DRIVERS.lastRun.drv)}</div><div>Imagem: FSR 1, resolução 1x</div><div>Limite de FPS: ${IG.fps === '0' ? 'sem limite' : IG.fps} · tela 120 Hz</div><div>Alterados do padrão (3): limite de FPS, anisotrópica, escala</div></section>
  </div>`;
}
function igMenu(g) {
  if (!IG.menu) return '';
  const tabs = [['gfx', 'Gráficos'], ['perf', 'Desempenho'], ['ctl', 'Controles'], ['ses', 'Sessão']];
  const body = { gfx: igGfx, perf: igPerf, ctl: igCtl, ses: igSes }[IG.tab](g);
  const st = perfOf(g);
  return `<aside class="igm" role="dialog" aria-label="Menu do jogo">
    <div class="igm-head">${coverHTML(g)}<div class="tt"><h2>${esc(g.name)}</h2><small>Pausado · Voltar para fechar</small></div><button class="ibtn" data-act="ig-close" data-k="ig-x" aria-label="Fechar">${ic('x')}</button></div>
    <div class="igm-strip" data-note="igstrip"><span><b>${IG.fps === '0' ? 34 : IG.fps}</b> FPS</span><span>p99 <b>${st ? st.ft99 : '—'}</b> ms</span><span><b>41</b> °C</span><span>bateria <b>72%</b></span><span>${esc(DRIVERS.lastRun.drv)}</span></div>
    <nav class="tabs" role="tablist" data-note="igtabs">${isC() ? '<span class="gb LB">LB</span>' : ''}${tabs.map(([v, t]) => `<button role="tab" aria-selected="${IG.tab === v}" data-act="ig-tab" data-v="${v}" data-k="igt-${v}"${IG.tab === v ? ' data-autofocus' : ''}>${t}</button>`).join('')}${isC() ? '<span class="gb RB">RB</span>' : ''}</nav>
    <div class="igm-body" data-sk="igm-${IG.tab}">${body}</div>
    ${isC() ? hints([['A', 'Selecionar', 'a'], ['B', 'Fechar', 'back'], ['LB/RB', 'Abas', 'tabs']], 'ighints') : `<div class="igm-foot"><button class="btn primary" data-act="ig-close" data-k="ig-cont">${ic('play', 17)} Continuar</button><button class="btn ghost" data-act="modal" data-v="ig-quit" data-k="ig-quit2">${ic('exit', 16)} Sair do jogo</button></div>`}
  </aside>`;
}
action('ig', el => { const f = el.dataset.f; let v = el.dataset.v; if (v === 'true' || v === 'false') v = v === 'true'; IG[f] = v; if (f === 'touch' && !v) toast('Controles de toque escondidos nesta sessão'); render(); });
action('change:igsel', el => { IG[el.dataset.f] = el.value; render(); });
action('ig-step', el => { const f = el.dataset.f, d = Number(el.dataset.d); IG[f] = f === 'volume' ? clamp(IG[f] + d, 0, 100) : clamp(IG[f] + d, 0, 10); render(); });
action('ig-tab', el => { IG.tab = el.dataset.v; render(); });
action('ig-more', el => { IG.more[el.dataset.v] = !IG.more[el.dataset.v]; render(); });
action('ig-metric', el => { const v = el.dataset.v; if (IG.metrics.has(v)) IG.metrics.delete(v); else IG.metrics.add(v); render(); });
action('ig-scene', () => { IG.scenes++; toast(`Cena ${IG.scenes} marcada`); });
action('ig-close', () => { IG.menu = false; S.modal = null; render(); });
action('ig-open', () => { IG.menu = true; render(); });
action('ig-fps', el => { const g = curGame(), v = el.dataset.v; if (v === 'game') { (OV[g.id] = OV[g.id] || {})['GPU.framerate_limit'] = IG.fps; toast(`Limite ${IG.fps === '0' ? 'sem limite' : IG.fps + ' FPS'} salvo para ${g.name}`); } else if (v === 'inherit') { if (OV[g.id]) delete OV[g.id]['GPU.framerate_limit']; toast('Este jogo volta a usar o limite global'); } else { if (IG.fps === DEF['GPU.framerate_limit'].def) delete GLOBAL['GPU.framerate_limit']; else GLOBAL['GPU.framerate_limit'] = IG.fps; toast('Limite salvo como global'); } });
action('ig-quit-go', () => { S.modal = null; IG.menu = true; goTop('library'); toast('Jogo encerrado'); });
modal('ig-quit', () => ({ html: sheetHead('Sair do jogo?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">O progresso não salvo pode ser perdido.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="q-cancel" data-autofocus>Cancelar</button><button class="btn danger" data-act="ig-quit-go" data-k="q-go">Sair do jogo</button></div>` }));
modal('ig-logs', () => ({ html: sheetHead('Compartilhar diagnóstico', 'Escolha uma sessão. Caminhos, contas e endereços são removidos antes.') + `<ul class="menu"><li><button data-act="toast" data-msg="Todas as sessões compartilhadas (protótipo)." data-k="lg-all" data-autofocus>${ic('layers', 19)}<span>Todas as sessões guardadas<small>${RUNS.length} sessões</small></span></button></li>${RUNS.slice(0, 4).map((r, i) => `<li><button data-act="toast" data-msg="Sessão compartilhada (protótipo)." data-k="lg-${i}">${ic('timeline', 19)}<span>${esc(GBY[r.gid].name)} · ${esc(r.when)}<small>${r.kb} KB · ${esc(r.dur)}</small></span></button></li>`).join('')}</ul>` }));
modal('ig-settings', () => { const g = curGame(); return { wide: true, html: sheetHead('Ajustes de ' + esc(g.name), 'Valem na próxima abertura deste jogo. Os itens “ao vivo” também estão no menu.') + settingsPanel(g, isC() ? 'c' : 'b') + `<div class="acts"><button class="btn primary" data-act="close" data-k="is-ok" data-autofocus>Pronto</button></div>` }; });

let hudTimer = 0;
function hudTick() { clearInterval(hudTimer); hudTimer = setInterval(() => { if (!['ingame', 'hud'].includes(S.route.name)) { clearInterval(hudTimer); return; } const h = app.querySelector('.hud'); if (h && S.route.name === 'ingame') { const kind = IG.hud; const tmp = document.createElement('div'); tmp.innerHTML = hudHTML(kind, IG.hudLook); if (tmp.firstElementChild) h.innerHTML = tmp.firstElementChild.innerHTML; } }, 1000); }

screen('ingame', {
  title: 'Menu em jogo',
  render() {
    const g = curGame();
    return `<div class="ig ${IG.menu ? 'dim' : ''}" style="${dynVars(g)}"><img class="ig-scene" src="${g.scene || ''}" alt="">
      ${!isC() && IG.touch ? touchOverlay(IG.menu ? .45 : 1) : ''}
      ${hudHTML(IG.hud, IG.hudLook)}
      ${IG.menu ? '' : `<button class="ig-handle" data-act="ig-open" data-k="ig-handle" data-note="ighandle">${isC() ? glyph('≡') : ic('menu', 15)} Menu</button>`}
      ${igMenu(g)}
    </div>`;
  },
  after() { hudTick(); },
  onBack() { IG.menu = !IG.menu; render(); return true; },
  tabStep(d) { if (!IG.menu) return; const t = ['gfx', 'perf', 'ctl', 'ses']; IG.tab = t[(t.indexOf(IG.tab) + d + 4) % 4]; render(); focusKey('igt-' + IG.tab); },
  onKey(k) { if (k === 'start' || k === 'view') { IG.menu = !IG.menu; render(); return true; } return false; },
});

/* ---------- carregamento ---------- */
const LD = { t0: 0, gid: null, timer: 0, done: false, route: null, demo: 'go', kind: 'first' };
const LD_T = { first: [800, 2200, 6000, 7000, 7600], cached: [800, 2200, 2900, 3300, 3700] };
function ldSteps(el) {
  const [a, b, c, d] = LD_T[LD.kind], s = x => (x / 1000).toFixed(1).replace('.', ',') + ' s';
  const pipes = LD.kind === 'first' ? Math.min(412, Math.max(0, Math.round((el - b) * .11))) : Math.min(12, Math.max(0, Math.round((el - b) / 50)));
  return [
    ['Iniciando o emulador', el >= a, el < a, s(a)],
    ['Iniciando o jogo', el >= b, el >= a && el < b, s(b - a)],
    [`Preparando os gráficos: ${pipes} ${pipes === 1 ? 'pipeline criado' : 'pipelines criados'}`, el >= c, el >= b && el < c, el >= c ? s(c - b) : ''],
    ['Esperando o primeiro quadro', el >= d, el >= c && el < d, el >= d ? s(d - c) : ''],
  ];
}
function ldElapsed() { const el = Date.now() - LD.t0; return LD.demo === 'hold' ? Math.min(el, LD.kind === 'first' ? 4600 : 2600) : el; }
function ldStepsHTML(el) { return ldSteps(el).map(([t, done, now, tm]) => `<li class="${done ? 'done' : now ? 'now' : ''}">${done ? ic('checkC', 20) : now ? `<span class="spin" style="display:inline-grid">${ic('refresh', 20)}</span>` : ic('clock', 20)}<span>${t}</span><time>${tm}</time></li>`).join(''); }
function ldBody() {
  const g = GBY[LD.gid] || curGame(), el = ldElapsed();
  const drv = label(DEF['Vulkan.vulkan_lib_path'], effOf(g, 'Vulkan.vulkan_lib_path'));
  return `${coverHTML(g)}<div>
    <span class="eyebrow" id="ld-eb">Abrindo · ${Math.floor(el / 1000)} s</span><h1>${esc(g.name)}</h1><div class="who">Entra como ${esc(activeProfile().tag)} · Title ID ${g.id}</div>
    <ol class="steps" id="ld-steps" data-note="ldsteps">${ldStepsHTML(el)}</ol>
    <div class="bar ind" style="max-width:520px;margin-top:14px"><i></i></div>
    <p class="note" id="ld-note" style="margin-top:12px;max-width:520px"${LD.kind === 'first' && el > 4000 ? '' : ' hidden'}>Ainda iniciando. A primeira vez de um jogo demora mais: os shaders que ele usa estão sendo montados, e as próximas vezes os reaproveitam.</p>
    <div class="opts" data-note="ldopts"><span class="badge">${ic('chip', 13)} ${esc(drv)}</span><span class="badge">Limite ${esc(label(DEF['GPU.framerate_limit'], effOf(g, 'GPU.framerate_limit')))}</span><span class="badge">Escala ${esc(label(RES, effOf(g, '@res')))}</span><span class="badge">${PON[g.id].size} patches</span>${ovCount(g) ? `<span class="badge acc">${ovCount(g)} ajustes deste jogo</span>` : ''}</div>
  </div>`;
}
function ldLive() {
  const el = ldElapsed(), q = id => document.getElementById(id);
  if (!q('ld-steps')) return;
  q('ld-eb').textContent = `Abrindo · ${Math.floor(el / 1000)} s`; q('ld-steps').innerHTML = ldStepsHTML(el); q('ld-note').hidden = !(LD.kind === 'first' && el > 4000);
}
screen('loading', {
  title: 'Carregamento',
  variants: [
    { label: 'Abertura', list: [['first', 'Primeira vez (monta shaders)'], ['cached', 'Com cache']], get: () => LD.kind, set: v => { LD.kind = v; LD.t0 = Date.now(); } },
    { label: 'Ao terminar', list: [['go', 'Abre o jogo'], ['hold', 'Para no meio']], get: () => LD.demo, set: v => { LD.demo = v; LD.t0 = Date.now(); } },
  ],
  render() {
    const g = GBY[S.route.p.gid] || curGame();
    if (LD.route !== S.route) { LD.route = S.route; LD.gid = g.id; LD.t0 = Date.now(); LD.done = false; }
    return `<div class="stagebox"><div class="c-bg" style="${dynVars(g)}"><div class="bgimg" style="background-image:url('${art(g)}')"></div></div><div class="ld" id="ld" style="${dynVars(g)}">${ldBody()}</div>
      ${isC() ? `<div style="position:absolute;left:0;right:0;bottom:0">${hints([['B', 'Voltar', 'back']])}</div>` : `<button class="btn ghost sm" style="position:absolute;left:14px;top:14px" data-act="ld-cancel" data-k="ld-x">${ic('back', 16)} Voltar</button>`}</div>`;
  },
  after() {
    clearInterval(LD.timer);
    LD.timer = setInterval(() => {
      if (S.route.name !== 'loading') { clearInterval(LD.timer); return; }
      if (window.LD_FREEZE) return;
      ldLive();
      if (LD.demo === 'go' && Date.now() - LD.t0 > LD_T[LD.kind][4] && !LD.done) { LD.done = true; clearInterval(LD.timer); IG.menu = false; IG.t0 = Date.now(); S.gameId = LD.gid; go('ingame', {}, { replace: true }); }
    }, 250);
  },
  onBack() { clearInterval(LD.timer); LD.done = true; return false; },
});
action('ld-cancel', () => { clearInterval(LD.timer); LD.done = true; back(); });

/* ---------- falha ao abrir ---------- */
const LF = [
  { id: 'core', n: 'Driver não iniciou', t: 'O jogo não pôde iniciar', why: 'O núcleo do emulador não iniciou: vkCreateDevice falhou (VK_ERROR_INITIALIZATION_FAILED) com o driver Turnip 26.0 dev.', log: 'E xe: vkCreateDevice: VK_ERROR_INITIALIZATION_FAILED\nE xe: GPU: Adreno 825 · driver Turnip 26.0 dev (sem checksum publicado)\nI xe: Abortando o boot: o dispositivo Vulkan não foi criado', drv: true },
  { id: 'busy', n: 'Dados ocupados', t: 'Os dados do jogo estão ocupados', why: 'Outra operação de save, perfil ou conteúdo ainda está usando os dados do jogo. Espere ela terminar e abra o jogo de novo.', log: 'I storage: lease ocupado por SaveBackupStore (backup em andamento)' },
  { id: 'recovery', n: 'Restauração interrompida', t: 'Uma restauração de save foi interrompida', why: 'Uma restauração de save interrompida não pôde ser desfeita (pasta de staging ilegível). Os dados de antes dessa restauração ficam em content/.save-transactions; nenhum jogo ou operação de save pode rodar até que sejam recuperados.', log: 'E saves: journal 2026-10-02T22:10 sem rollback possível', saves: true },
  { id: 'running', n: 'Outro jogo rodando', t: 'Um jogo já está rodando', why: 'Saia dele (Voltar → Sair do jogo) antes de abrir outro.', log: 'I host: processo :emu ocupado por 4D5309C9' },
];
screen('launchfail', {
  title: 'Falha ao abrir',
  variants: [{ label: 'Motivo', list: LF.map(x => [x.id, x.n]), get: () => S.route.p.lf || 'core', set: v => { S.route.p.lf = v; } }],
  render() {
    const g = curGame(), f = LF.find(x => x.id === (S.route.p.lf || 'core')) || LF[0];
    return `<div class="stagebox"><div class="c-bg" style="${dynVars(g)}"><div class="bgimg" style="background-image:url('${art(g)}')"></div></div>
    <div class="fail"><section class="card">
      <h1>${ic('alert', 26)} ${esc(f.t)}</h1>
      <p style="margin:0;font-size:14px;color:var(--fg2)">${esc(f.why)}</p>
      <pre>${esc(f.log)}</pre>
      <div class="row" data-note="failacts">
        ${f.drv ? `<button class="btn primary" data-act="lf-sysdrv" data-k="lf-sys" data-autofocus data-note="faildrv">${ic('chip', 16)} Tentar com o driver do sistema</button>` : ''}
        ${f.saves ? `<button class="btn primary" data-act="go" data-v="saves" data-k="lf-saves" data-autofocus>${ic('save', 16)} Abrir os saves</button>` : ''}
        <button class="btn${f.drv || f.saves ? '' : ' primary'}" data-act="play" data-gid="${g.id}" data-k="lf-retry"${f.drv || f.saves ? '' : ' data-autofocus'}>${ic('refresh', 16)} Tentar de novo</button>
        <button class="btn ghost" data-act="toast" data-msg="Logs prontos para compartilhar (protótipo)." data-k="lf-logs">${ic('share', 16)} Compartilhar logs</button>
        <button class="btn ghost" data-act="back" data-k="lf-back">Voltar</button>
      </div>
      <p class="note">Tentar de novo abre num processo novo.${f.drv ? ' O driver do sistema vale só para esta abertura; o escolhido continua salvo.' : ''}</p>
    </section></div>${isC() ? `<div style="position:absolute;left:0;right:0;bottom:0">${hints([['A', 'Selecionar', 'a'], ['B', 'Voltar', 'back']])}</div>` : ''}</div>`;
  },
});
action('lf-sysdrv', () => { toast('Abrindo com o driver do sistema, só desta vez'); ACT.play({ dataset: { gid: S.gameId } }); });

/* ---------- HUD ---------- */
const THERMAL = {
  near: 'O telefone está perto do limite de calor e vai reduzir o desempenho em breve. Resolução ou limite de FPS menores, ou desligar o clock máximo forçado da GPU ou a geração de quadros, o mantêm mais frio.',
  throttling: 'O telefone chegou ao limite de calor e está reduzindo o desempenho. Resolução ou limite de FPS menores, ou desligar o clock máximo forçado da GPU ou a geração de quadros, o mantêm mais frio.',
};
screen('hud', {
  title: 'HUD de desempenho',
  variants: [
    { label: 'Nível', list: [['compact', 'Compacto'], ['full', 'Completo'], ['panel', 'Painel']], get: () => IG.hud === 'off' ? 'compact' : IG.hud, set: v => { IG.hud = v; } },
    { label: 'Aparência', list: [['box', 'Caixa'], ['outline', 'Texto com contorno'], ['plain', 'Texto simples']], get: () => IG.hudLook, set: v => { IG.hudLook = v; } },
    { label: 'Aviso térmico', list: [['near', 'Perto do limite'], ['throttling', 'Reduzindo'], ['off', 'Nenhum']], get: () => IG.thermal, set: v => { IG.thermal = v; } },
  ],
  render() {
    const g = curGame();
    return `<div class="ig"><img class="ig-scene" src="${g.scene || ''}" alt="">
      ${!isC() ? touchOverlay(.8) : ''}
      ${hudHTML(IG.hud === 'off' ? 'compact' : IG.hud, IG.hudLook)}
      ${IG.thermal !== 'off' ? `<div class="ig-banner" data-note="thermal">${ic('thermo', 18)}<span>${THERMAL[IG.thermal]}</span><button class="ibtn" style="width:28px;height:28px;color:#ffe2a3;flex:none" data-act="ig" data-f="thermal" data-v="off" data-k="hd-ban" aria-label="Fechar aviso">${ic('x', 16)}</button></div>` : ''}
    </div>`;
  },
  after() { hudTick(); },
});
