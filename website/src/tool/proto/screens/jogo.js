/* Jogo aberto: carregamento, falha ao abrir, menu em jogo e HUD (GameLoadingScreen.kt,
   LaunchFailureScreen.kt, InGameMenu.kt, InGameMenuState.kt, FpsOverlay.kt). No lugar da imagem
   do jogo fica um quadro neutro: o simulador não roda jogos nem mostra imagens deles. */
'use strict';

Object.assign(NOTES, {
  igopen: ['app', 'No app não há botão desenhado sobre o jogo: o menu abre com Voltar (gesto da borda ou botão), deslizando a partir da borda esquerda ou com o botão Guia do controle.'],
  igedge: ['sim', 'Aqui a faixa da borda esquerda fica visível para dar para clicar; Esc também abre e fecha o menu.'],
  igrail: ['app', 'Cinco categorias: Imagem, Desempenho, HUD, Controles e Sessão. Deitado ficam num trilho à esquerda; em pé, em abas no alto. Com controle, LB e RB trocam de categoria.'],
  igstrip: ['app', 'A linha de estado mostra FPS, p99 dos últimos segundos, temperatura e carga da bateria e o driver carregado, sem precisar ligar o HUD.'],
  igrows: ['app', 'Cada linha muda o valor no lugar: escolhas lado a lado, ‹ valor › para listas longas, interruptor ou barra. Com controle, ◀ ▶ ajusta e A aciona.'],
  igmore: ['app', 'Opções avançadas ficam atrás de “Mais opções”. Com “Ajustes mostrados” em Essencial somem também as de energia e a entrada sem buffer.'],
  igsave: ['app', 'Com “Guardar as mudanças para este jogo” ligado (o padrão), o limite de FPS e as opções de Imagem, de controles e o volume vão para o arquivo do jogo: veja na ficha, em TOML.'],
  iglive: ['app', 'Escala, antisserrilhamento, nitidez e pontilhamento mudam na hora; os outros ajustes de Imagem valem na próxima abertura.'],
  igfg: ['app', 'Geração de quadros: experimental, desligada a cada abertura e disponível em todas as builds. O LSFG só funciona com o Lossless.dll do próprio jogador, importado e convertido no aparelho.'],
  iggpu: ['dif', 'Pelo código desta versão, “MSAA 4× como 2×”, “Transparência recortada” e “Taxa de sombreamento” não estão no esquema de ajustes do app, então guardar essas três para o jogo pode falhar. A simulação não as grava no arquivo do jogo. Não conferido no aparelho.'],
  igshade: ['dif', 'Pelo código, a taxa de sombreamento só tem efeito com vulkan_fragment_shading_rate ligado na inicialização (desligado por padrão e fora das Configurações) e com suporte do driver.'],
  igphones: ['app', 'Telefones como controle: liga um servidor só na rede local e mostra o endereço e um código de 6 dígitos novos a cada vez. Os telefones entram como P2 a P4. Experimental.'],
  ighud: ['app', 'O HUD liga e desliga só por aqui e vale para todos os jogos; posição, tamanho e estilo ficam por jogo.'],
  hudlv: ['app', 'Formato vertical (uma caixa que se arrasta e muda de tamanho com pinça) ou horizontal (uma barra no topo ou na base); detalhe só FPS, métricas ou painel.'],
  hudex: ['sim', 'Os números do HUD são de exemplo.'],
  ighints: ['app', 'Dicas de botão no rodapé do menu: A seleciona, ◀ ▶ ajusta, LB/RB trocam de categoria, B fecha.'],
  igshot: ['app', 'Capturar tela salva a imagem do jogo, sem o menu e o HUD, em Imagens/Xendroid+.'],
  tcl: ['app', 'Controles de toque: somem enquanto um controle físico joga como P1 e voltam quando ele desconecta. Visual Moderno (padrão) ou Clássico.'],
  ldsteps: ['app', 'Etapas da abertura com o tempo de cada uma; na primeira vez de um jogo, os shaders estão sendo montados e demora mais.'],
  ldopts: ['app', 'Com que driver e limite o jogo abre, quantos ajustes próprios ele tem e se a abertura veio de “Iniciar com…”.'],
  failacts: ['app', 'Tentar de novo abre num processo novo; Compartilhar logs leva ao diagnóstico.'],
  faildrv: ['app', 'Quando a falha aconteceu com um driver personalizado, dá para tentar uma vez com o driver do sistema; o escolhido continua salvo.'],
  failex: ['sim', 'As falhas usam um jogo fictício e um log de exemplo.'],
  thermal: ['app', 'Avisos de calor do Android, no máximo um a cada 5 minutos. Eles só sugerem; nada muda sozinho.'],
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
/* opacidade e liga/desliga vêm dos ajustes gerais do toque; dim escurece com o menu aberto */
const touchOp = () => DEF['@touch.opacity'] ? globalOf('@touch.opacity') / 100 : .65;
function touchOverlay(dim) {
  if (!globalOf('HID.show_touch_overlay')) return '';
  const o = isPortrait() ? 'port' : 'land';
  return `<div class="tcl ${DEF['@touch.style'] && globalOf('@touch.style') === 'classic' ? 'classic' : ''}" style="--op:${(touchOp() * (dim || 1)).toFixed(2)}" data-note="tcl">${TOUCH_LAYOUT[o].filter(e => e.vis !== false).map(e => touchEl(e)).join('')}</div>`;
}

/* ---------- estado da sessão em jogo ---------- */
const IG = {
  menu: true, page: 'img', more: {}, tipShown: false,
  /* Imagem: -1 = como nos ajustes */
  display: 'fit', scaling: -1, aa: -1, sharp: -1, dither: -1, color: 0, stretch: false, tvMargin: 0,
  winfg: false, fgPreset: 2, lsfg: false, lsfgReady: false, lsfgMul: 2, lsfgTarget: 0,
  /* Desempenho */
  fps: null, hz: 0, smooth: true, msaa2x: false, cutout: false, shading: 0, sustained: false, adpf: true, bg: 0,
  /* HUD */
  hud: false, hudLayout: 'vertical', hudDetail: 'metrics', metrics: new Set(['cpu', 'gpu', 'ram', 'gmem', 'bat', 'soc', 'pwr', 'chg', 'time', 'graph', 'vk']), hudPos: 'top', hudLook: 'box', hudSize: 100, hudBg: 58, hudColors: 100,
  /* Controles */
  touch: true, ctlStyle: 'modern', adaptive: false, touchCam: false, split: 0, rumble: 2, phones: false, unbuffered: true, gyroCam: false, gyroAim: 0, gyroSens: 1,
  /* Sessão */
  volume: 100, muted: false, autosave: true, pauseOnOpen: true, scenes: 0, changes: 0, kept: 0,
  t0: Date.now(), thermal: 'near',
};
const IG_PAGES = [['img', 'Imagem', 'image'], ['perf', 'Desempenho', 'bolt'], ['hud', 'HUD', 'chart'], ['ctl', 'Controles', 'gamepad'], ['ses', 'Sessão', 'play']];
const AS_SET = 'como nos ajustes';
const EFFECTS = [['bilinear', 'Bilinear'], ['cas', 'CAS'], ['fsr', 'FSR'], ['sgsr', 'SGSR'], ['lanczos', 'Lanczos'], ['crt', 'CRT']];
const AAS = [['none', 'Desligado'], ['fxaa', 'FXAA'], ['fxaa_extreme', 'FXAA extremo']];
const SHARP = ['suave', 'baixa', 'média', 'alta', 'máxima'], CAS_V = ['0.0', '0.25', '0.5', '0.75', '1.0'], FSR_V = ['2.0', '1.0', '0.5', '0.2', '0.0'];
const COLORS = ['Desligado', 'Tons de cinza', 'Contraste', 'Quente', 'Vívido (falso HDR)'];
const TV_M = [0, 2.5, 5, 7.5, 10], FG_PRESETS = ['Qualidade', 'Equilíbrio', 'Desempenho'];
const LSFG_T = [['off', 'desligado: multiplicador manual'], ['60', '60 FPS'], ['90', '90 FPS'], ['120', '120 FPS'], ['screen', 'a tela, 120 Hz']];
const FPS_L = [['0', 'Sem limite'], ['30', '30'], ['45', '45'], ['60', '60'], ['90', '90'], ['120', '120']];
const HZ = ['Automático', '60 Hz', '90 Hz', '120 Hz'], SHADING = ['Todo pixel', '2×1', '1×2', '2×2'], BG = ['AUTO', 'MANUAL', 'NEVER'];
const SPLIT = ['Desligada', 'Em dobrável meio aberto', 'Sempre'], RUMBLE = ['Desligada', 'Baixa', 'Média', 'Alta'];
const AIM = ['sempre', 'segurando LT', 'segurando LB'], SENS = ['Baixa', 'Normal', 'Alta'];
const HUD_METRICS = [['cpu', 'CPU'], ['gpu', 'GPU'], ['ram', 'RAM'], ['gmem', 'Mem. GPU'], ['bat', 'Bateria'], ['soc', 'SoC'], ['pwr', 'Potência'], ['chg', 'Carga'], ['time', 'Autonomia'], ['graph', 'Gráfico de FPS'], ['vk', 'Vulkan', 'dev']];
const player = () => String(globalOf('@app.level')) === '1';
const DEV_ONLY = new Set(['sustained', 'adpf', 'bg', 'unbuffered']);
const fpsNow = g => (IG.fps != null ? IG.fps : String(effOf(g, 'GPU.framerate_limit')));

/* As linhas de cada categoria, nos grupos do app; adv = atrás de “Mais opções”. */
function igRows(g) {
  const drv = label(DEF['Vulkan.vulkan_lib_path'], effOf(g, 'Vulkan.vulkan_lib_path'));
  const fx = IG.scaling >= 0 ? EFFECTS[IG.scaling][0] : String(effOf(g, 'Display.postprocess_scaling_and_sharpening'));
  const sharpOk = fx === 'cas' || fx === 'fsr';
  const fps = fpsNow(g), cap = fps === '0' ? 60 : Number(fps);
  return {
    img: [
      ['Tela e efeitos', [
        { id: 'display', kind: 'choice', t: 'Tela', opts: [['fit', 'Ajustar'], ['fill', 'Preencher'], ['stretch', 'Esticar'], ['int', 'Inteira']] },
        { id: 'scaling', kind: 'cycle', t: 'Efeito de escala', vals: [AS_SET, ...EFFECTS.map(e => e[1])], from: -1, own: IG.scaling >= 0 },
        { id: 'aa', kind: 'cycle', t: 'Antisserrilhamento', vals: [AS_SET, ...AAS.map(a => a[1])], from: -1, own: IG.aa >= 0 },
        { id: 'sharp', kind: 'cycle', t: 'Nitidez', vals: [AS_SET, ...SHARP], from: -1, note: sharpOk ? '' : 'com CAS ou FSR', own: IG.sharp >= 0 },
        { id: 'dither', kind: 'cycle', t: 'Pontilhado', vals: [AS_SET, 'Desligado', 'Ligado'], from: -1, adv: 1, own: IG.dither >= 0 },
        { id: 'color', kind: 'cycle', t: 'Filtro de cor', vals: COLORS, adv: 1 },
        { id: 'stretch', kind: 'toggle', t: 'Esticar na tela toda', sub: 'A partir da próxima abertura', adv: 1 },
      ]],
      ['TV e tela externa', [
        { id: 'tv', kind: 'button', t: 'TV ou tela externa', sub: 'TV · tela do telefone', act: 'ig-toast', msg: 'No app, abre a escolha da tela de apresentação (TV ou monitor conectado).' },
        { id: 'tvMargin', kind: 'cycle', t: 'Margem da TV', vals: TV_M.map(v => String(v).replace('.', ',') + '%') },
      ]],
      ['Geração de quadros', [
        { id: 'winfg', kind: 'toggle', t: 'Win-FG 2×', badge: 'experimental', note: 'fg' },
        { id: 'fgPreset', kind: 'cycle', t: 'Predefinição do Win-FG', vals: FG_PRESETS },
        { id: 'lsfg', kind: 'toggle', t: 'LSFG nativo', disabled: !IG.lsfgReady, sub: IG.lsfgReady ? 'Com o seu Lossless.dll, convertido neste aparelho' : 'Importe o seu Lossless.dll em Mais opções' },
        { id: 'lsfgMul', kind: 'cycle', t: 'Multiplicador LSFG', vals: ['2×', '3×', '4×'], from: 2, adv: 1, badge: 'experimental' },
        { id: 'lsfgTarget', kind: 'cycle', t: 'Alvo do LSFG', vals: LSFG_T.map(x => x[1]), adv: 1, badge: 'experimental', note: IG.lsfgTarget ? lsfgPlan(cap) : '' },
        { id: 'lsfgdll', kind: 'button', t: 'Importar meu Lossless.dll', adv: 1, act: 'ig-lsfg-import' },
        { id: 'lsfgclr', kind: 'button', t: 'Remover o cache de shaders LSFG importado', adv: 1, act: 'ig-lsfg-clear', disabled: !IG.lsfgReady },
      ]],
      ['Driver', [{ id: 'drv', kind: 'info', t: `Driver: ${drv}` }]],
    ],
    perf: [
      ['Quadros por segundo', [
        { id: 'fps', kind: 'choice', t: 'Limite de FPS', opts: FPS_L, own: OV[g.id] && 'GPU.framerate_limit' in OV[g.id] },
        { id: 'hz', kind: 'cycle', t: 'Taxa de atualização da tela', vals: HZ, note: `Hz da tela · pedido ${IG.hz ? HZ[IG.hz] : 'Automático'} · efetivo ${IG.hz ? HZ[IG.hz] : '120 Hz'}` },
      ]],
      ['Desempenho da GPU', [
        { id: 'smooth', kind: 'toggle', t: 'Shaders sem travadas', sub: 'Nenhum quadro espera um shader novo: na primeira vez que algo aparece, um breve pop-in em vez de uma travada' },
        { id: 'msaa2x', kind: 'toggle', t: 'MSAA 4× como 2×', sub: 'Mais rápido nos jogos com MSAA 4×; bordas um pouco menos suaves', dnote: 'iggpu' },
        { id: 'cutout', kind: 'toggle', t: 'Transparência recortada', sub: 'Folhagens, grades e cabelos mais rápidos, com bordas duras' },
        { id: 'shading', kind: 'cycle', t: 'Taxa de sombreamento', vals: SHADING, note: 'Acima de 1 pixel, texturas e luz ficam mais grossas dentro das formas; as bordas continuam nítidas', dnote: 'igshade' },
      ]],
      ['Energia', [
        { id: 'sustained', kind: 'toggle', t: 'Desempenho sustentado', adv: 1 },
        { id: 'adpf', kind: 'toggle', t: 'Dicas ADPF do apresentador', adv: 1 },
        { id: 'bg', kind: 'cycle', t: 'Pausa em segundo plano', vals: BG, adv: 1, en: 1 },
      ]],
    ],
    hud: [
      [null, [
        { id: 'hud', kind: 'toggle', t: 'Mostrar o HUD', dnote: 'ighud' },
        { id: 'hudLayout', kind: 'choice', t: 'Formato', opts: [['vertical', 'Vertical'], ['horizontal', 'Horizontal']] },
        { id: 'hudDetail', kind: 'choice', t: 'Detalhe', opts: [['fps', 'Só FPS'], ['metrics', 'Métricas'], ['panel', 'Painel']] },
        { id: 'metrics', kind: 'multi', t: 'Métricas', opts: HUD_METRICS.filter(m => !(m[2] && player())) },
      ]],
      ['Aparência', [
        { id: 'hudPos', kind: 'choice', t: 'Posição da barra', opts: [['top', 'Topo'], ['bottom', 'Base']], disabled: IG.hudLayout !== 'horizontal', sub: IG.hudLayout === 'horizontal' ? '' : 'No formato vertical, arraste o HUD na tela; a pinça muda o tamanho.' },
        { id: 'hudLook', kind: 'choice', t: 'Estilo', opts: [['box', 'Caixa'], ['outline', 'Contorno'], ['plain', 'Texto']] },
        { id: 'hudSize', kind: 'slider', t: 'Tamanho', min: 50, max: 250, step: 10, unit: '%' },
        { id: 'hudBg', kind: 'slider', t: 'Fundo', min: 0, max: 100, step: 2, unit: '%', disabled: IG.hudLook !== 'box' },
        { id: 'hudColors', kind: 'slider', t: 'Cores', min: 0, max: 100, step: 5, unit: '%' },
      ]],
    ],
    ctl: [
      ['Na tela', [
        { id: 'touch', kind: 'toggle', t: 'Controles na tela' },
        { id: 'ctlStyle', kind: 'choice', t: 'Visual', opts: [['modern', 'Moderno'], ['classic', 'Clássico']] },
        { id: 'adaptive', kind: 'toggle', t: 'Analógicos adaptativos', own: IG.adaptive },
        { id: 'touchCam', kind: 'toggle', t: 'Câmera por toque', sub: 'O lado direito livre gira a câmera' },
        { id: 'edit', kind: 'button', t: 'Editar o layout', sub: 'Posição, tamanho, opacidade, ocultar', act: 'ig-edit' },
        { id: 'split', kind: 'cycle', t: 'Tela dividida', vals: SPLIT },
      ]],
      ['Controles físicos', [
        { id: 'rumble', kind: 'cycle', t: 'Vibração do controle', vals: RUMBLE, note: S.pad ? 'P1' : '' },
        { id: 'phones', kind: 'button', t: 'Telefones como controle', sub: IG.phones ? 'Telefones como controle · Ligado (ative para desligar)' : 'Telefones como controle · Desligado', act: 'ig-phones', dnote: 'igphones' },
        { id: 'unbuffered', kind: 'toggle', t: 'Entrada sem buffer', adv: 1 },
      ]],
      ['Giroscópio', [
        { id: 'gyroCam', kind: 'toggle', t: 'Câmera pelo giroscópio' },
        { id: 'gyroAim', kind: 'cycle', t: 'Mira pelo giroscópio', vals: AIM },
        { id: 'gyroSens', kind: 'cycle', t: 'Sensibilidade do giroscópio', vals: SENS },
        { id: 'gyroCal', kind: 'button', t: 'Calibrar o giroscópio', sub: 'Deixe o telefone parado ao fechar o menu', adv: 1, act: 'ig-toast', msg: 'O giroscópio é calibrado quando o menu fecha; deixe o telefone parado.' },
      ]],
    ],
    ses: [
      ['Som', [
        { id: 'volume', kind: 'slider', t: 'Volume', min: 0, max: 100, step: 5, unit: '%', disabled: IG.muted },
        { id: 'muted', kind: 'toggle', t: 'Sem som' },
      ]],
      ['Ajustes deste jogo', [
        { id: 'autosave', kind: 'toggle', t: 'Guardar as mudanças para este jogo', sub: IG.autosave ? `O que mudar aqui fica para ${g.name}` : 'As mudanças valem só nesta sessão', dnote: 'igsave' },
        { id: 'undo', kind: 'button', t: 'Desfazer as mudanças desta sessão', sub: IG.changes ? `${IG.changes} ${IG.changes === 1 ? 'mudança' : 'mudanças'} nesta sessão` : 'Nenhuma mudança nesta sessão', act: 'ig-undo', disabled: !IG.changes },
        { id: 'global', kind: 'button', t: 'Usar estas mudanças em todos os jogos', sub: 'Viram os ajustes globais; este jogo deixa de ter cópia própria', act: 'ig-global', disabled: !ovCount(g) },
      ]],
      ['Este menu', [{ id: 'pauseOnOpen', kind: 'toggle', t: 'Pausar o jogo ao abrir o menu', sub: 'Vale para todos os jogos' }]],
      ['Sessão', [
        { id: 'shot', kind: 'button', t: 'Capturar tela', sub: 'A imagem do jogo, sem o menu e o HUD, salva em Imagens/Xendroid+', act: 'ig-shot', dnote: 'igshot' },
        { id: 'mark', kind: 'button', t: 'Marcar cena', sub: `${IG.scenes} até agora, para comparar execuções`, adv: 1, act: 'ig-scene' },
        { id: 'logs', kind: 'button', t: 'Compartilhar logs de diagnóstico', act: 'ig-logs' },
        { id: 'resume', kind: 'button', t: 'Continuar', act: 'ig-close' },
        { id: 'quit', kind: 'button', t: 'Sair do jogo', act: 'ig-quit', danger: 1 },
      ]],
    ],
  };
}
function lsfgPlan(cap) {
  const t = LSFG_T[IG.lsfgTarget][0], target = t === 'screen' ? 120 : Number(t);
  const mul = [2, 3, 4].find(m => target / m <= cap) || 4, game = Math.floor(target / mul);
  return `${t === 'screen' ? 'a tela, 120 Hz' : target + ' FPS'} → ${mul}× com o jogo a ${game} FPS`;
}
const IG_NOTES = {
  img: ['Escala, antisserrilhamento, nitidez e pontilhamento mudam na hora; guarde para o jogo abrir assim da próxima vez. Os outros ajustes de Imagem valem na próxima abertura.', 'Interpolação experimental no host; cadência e latência no aparelho ainda não validadas.', 'Escolha o driver em Configurações → Driver Vulkan personalizado (ou nos ajustes deste jogo); ele vale quando um jogo abre.'],
  perf: [], hud: [], ses: [],
  ctl: [],
};

/* uma linha, como o MenuRow do app */
function igRowHTML(r) {
  const on = IG[r.id], dis = r.disabled ? ' disabled' : '', tag = r.own ? '<span class="tag">deste jogo</span>' : '', badge = r.badge ? ` <span class="badge warn">${r.badge}</span>` : '';
  const head = `<div class="igx-t"><b>${esc(r.t)}${badge}</b>${tag}${r.sub ? `<small>${esc(r.sub)}</small>` : ''}</div>`;
  const note = r.note && r.note !== 'fg' ? `<small class="igx-n">${esc(r.note)}</small>` : '';
  const dn = r.dnote ? ` data-note="${r.dnote}"` : '';
  if (r.kind === 'toggle') return `<div class="igx-row${r.disabled ? ' dis' : ''}"${dn}>${head}<button class="tg" role="switch" aria-checked="${!!on}" aria-label="${esc(r.t)}" data-act="igv" data-f="${r.id}" data-k="igx-${r.id}"${dis}></button>${note}</div>`;
  if (r.kind === 'choice') return `<div class="igx-row stack${r.disabled ? ' dis' : ''}"${dn}>${head}<div class="seg" role="group" aria-label="${esc(r.t)}">${r.opts.map(([v, t]) => `<button data-act="igv" data-f="${r.id}" data-v="${esc(v)}" aria-pressed="${String(r.id === 'fps' ? fpsNow(curGame()) : on) === String(v)}" data-k="igx-${r.id}-${esc(v)}"${dis}>${esc(t)}</button>`).join('')}</div>${note}</div>`;
  if (r.kind === 'cycle') { const i = on - (r.from != null ? r.from : 0); return `<div class="igx-row${r.disabled ? ' dis' : ''}"${dn}>${head}<span class="stp igx-cyc" role="group" aria-label="${esc(r.t)}"><button data-act="igc" data-f="${r.id}" data-d="-1" aria-label="Anterior" data-k="igx-${r.id}-p"${dis}>${ic('chevL', 16)}</button><b${r.en ? ' lang="en"' : ''}>${esc(r.vals[clamp(i, 0, r.vals.length - 1)])}</b><button data-act="igc" data-f="${r.id}" data-d="1" aria-label="Próximo" data-k="igx-${r.id}-n"${dis}>${ic('chevR', 16)}</button></span>${note}</div>`; }
  if (r.kind === 'slider') return `<div class="igx-row stack${r.disabled ? ' dis' : ''}"${dn}>${head}<span class="rng"><input type="range" min="${r.min}" max="${r.max}" step="${r.step}" value="${on}" aria-label="${esc(r.t)}" data-act-input="igr" data-f="${r.id}" data-k="igx-${r.id}"${dis}><output>${on}${r.unit}</output></span></div>`;
  if (r.kind === 'multi') return `<div class="igx-row stack"${dn}>${head}<div class="igx-chips">${r.opts.map(([v, t]) => `<button class="chip" data-act="igm" data-v="${v}" aria-pressed="${IG.metrics.has(v)}" data-k="igx-m-${v}">${esc(t)}</button>`).join('')}</div></div>`;
  if (r.kind === 'info') return `<div class="igx-row info"${dn}><div class="igx-t"><b>${esc(r.t)}</b></div></div>`;
  return `<button class="igx-row btnrow${r.danger ? ' danger' : ''}" data-act="${r.act}" data-k="igx-${r.id}"${r.msg ? ` data-msg="${esc(r.msg)}"` : ''}${dis}${dn}>${head}${ic('chevR', 16)}</button>`;
}
function igPageHTML(g) {
  const groups = igRows(g)[IG.page];
  const show = r => !(player() && DEV_ONLY.has(r.id));
  const common = [], adv = [];
  for (const [gt, rows] of groups) for (const r of rows.filter(show)) (r.adv ? adv : common).push([gt, r]);
  const render = list => { let last; return list.map(([gt, r]) => { const h = gt && gt !== last ? `<h6 class="igx-g">${esc(gt)}</h6>` : ''; last = gt; return h + igRowHTML(r); }).join(''); };
  const more = adv.length ? `<button class="igx-more" data-act="ig-more" data-k="igx-more" data-note="igmore">${ic(IG.more[IG.page] ? 'chevD' : 'chevR', 16)} ${IG.more[IG.page] ? 'Menos opções' : `Mais opções (${adv.length})`}</button>` : '';
  const notes = (IG_NOTES[IG.page] || []).concat(IG.page === 'ctl' && IG.phones ? ['No outro telefone: Controles → Celular como controle → 192.168.0.12:41234 (exemplo), código 482913, Wi-Fi.', 'Nenhum telefone conectado ainda. Os telefones jogam como P2–P4.', 'Telefones como controle: outros telefones no mesmo Wi-Fi ou ponto de acesso jogam como P2–P4 com o código mostrado aqui; experimental.'] : []);
  return (IG.page === 'hud' ? `<div class="igx-hudprev" data-note="hudex">${hudHTML(true)}</div>` : '') + render(common) + more + (IG.more[IG.page] ? render(adv) : '') + notes.map(n => `<p class="igx-note"${/^Interpola/.test(n) ? ' data-note="igfg"' : /^Escala/.test(n) ? ' data-note="iglive"' : ''}>${esc(n)}</p>`).join('');
}
function igStatus(g) {
  const st = perfOf(g), fps = fpsNow(g), f = st ? Math.min(st.p50, fps === '0' ? 60 : Number(fps)) : (fps === '0' ? 60 : Number(fps));
  return `<div class="igx-status" data-note="igstrip"><span><b>${f}</b> FPS</span><span>p99 <b>${st ? st.ft99 : 34}</b> ms</span><span><b>38</b> °C</span><span>bateria <b>72%</b></span><span>${esc(DRIVERS.lastRun.drv)}</span></div>`;
}
function igMenu(g) {
  if (!IG.menu) return '';
  const port = isPortrait(), c = isC();
  const pages = `${c ? '<span class="gb LB">LB</span>' : ''}${IG_PAGES.map(([v, t, i]) => `<button role="tab" aria-selected="${IG.page === v}" data-act="ig-page" data-v="${v}" data-k="igp-${v}"${IG.page === v ? ' data-autofocus' : ''}>${ic(i, port ? 16 : 20)}<span>${t}</span></button>`).join('')}${c ? '<span class="gb RB">RB</span>' : ''}`;
  const kept = IG.kept ? `<button class="igx-kept" data-act="ig-page" data-v="ses" data-k="igx-kept">${ic('save', 14)} ${IG.kept} ${IG.kept === 1 ? 'guardada' : 'guardadas'}</button>` : '';
  return `<aside class="igx ${port ? 'port' : 'land'}" role="dialog" aria-label="Menu do jogo">
    <div class="igx-head">${coverHTML(g)}<div class="tt"><h2>${esc(g.name)}</h2><small>${IG.pauseOnOpen ? 'Pausado · Voltar para fechar' : 'Rodando · Voltar para fechar'}</small></div>${!port ? kept : ''}${!port && !c ? `<button class="btn ghost sm" data-act="ig-quit" data-k="igx-quit">${ic('exit', 15)} Sair do jogo</button>` : ''}${c ? '' : `<button class="ibtn" data-act="ig-close" data-k="ig-x" aria-label="Continuar">${ic('x')}</button>`}</div>
    ${port && !isRoomy() ? '' : igStatus(g)}
    <div class="igx-body"><nav class="igx-pages" role="tablist" aria-label="Categorias" data-note="igrail">${pages}</nav>
      <div class="igx-rows" data-sk="igx-${IG.page}" data-note="igrows">${igPageHTML(g)}</div></div>
    ${port && IG.kept ? `<button class="igx-saved" data-act="ig-page" data-v="ses" data-k="igx-saved">${ic('save', 14)} Guardado para ${esc(g.name)}: ${IG.kept} ${IG.kept === 1 ? 'mudança' : 'mudanças'}</button>` : ''}
    ${c ? hints([['A', 'Selecionar', 'a'], ['◀/▶', 'Ajustar', 'a'], ['LB/RB', 'Categorias', 'tabs'], ['B', 'Fechar', 'back']], 'ighints') : port ? `<div class="igx-foot"><button class="btn primary" data-act="ig-close" data-k="ig-cont">${ic('play', 17)} Continuar</button><button class="btn ghost" data-act="ig-quit" data-k="ig-quit2">${ic('exit', 16)} Sair do jogo</button></div>` : ''}
  </aside>`;
}
const isRoomy = () => { const s = document.getElementById('screen'); return !s || s.clientHeight >= 460; };

/* o que o app guarda por jogo (InGameChanges.kt): chave do TOML e valor */
function igKeep(k, v) {
  const g = curGame();
  IG.changes++;
  if (!IG.autosave) return;
  const d = DEF[k]; if (!d) return;
  const o = OV[g.id] = OV[g.id] || {};
  if (v == null || String(coerce(d, v)) === String(globalOf(k))) delete o[k]; else o[k] = coerce(d, v);
  IG.kept = keptCount(g);
}
/* quantas chaves do arquivo do jogo esta sessão mudou ("N guardadas") */
function keptCount(g) {
  const a = IG.ovStart || {}, b = OV[g.id] || {};
  return [...new Set(Object.keys(a).concat(Object.keys(b)))].filter(k => JSON.stringify(a[k]) !== JSON.stringify(b[k])).length;
}
/* começo de uma sessão de jogo: o arquivo do jogo como estava, para “Desfazer” */
function igStart(g) {
  IG.gid = g.id; IG.ovStart = OV[g.id] ? JSON.parse(JSON.stringify(OV[g.id])) : null;
  IG.changes = 0; IG.kept = 0; IG.fps = null; IG.scaling = IG.aa = IG.sharp = IG.dither = -1; IG.winfg = IG.lsfg = false; IG.t0 = Date.now();
  IG.ctlStyle = DEF['@touch.style'] ? globalOf('@touch.style') : 'modern'; IG.touch = !!effOf(g, 'HID.show_touch_overlay'); IG.volume = Number(effOf(g, 'APU.volume'));
}
function igApply(f) {
  const g = curGame();
  switch (f) {
    case 'scaling': igKeep('Display.postprocess_scaling_and_sharpening', IG.scaling >= 0 ? EFFECTS[IG.scaling][0] : null); break;
    case 'aa': igKeep('Display.postprocess_antialiasing', IG.aa >= 0 ? AAS[IG.aa][0] : null); break;
    case 'sharp': igKeep('Display.postprocess_ffx_cas_additional_sharpness', IG.sharp >= 0 ? CAS_V[IG.sharp] : null); igKeep('Display.postprocess_ffx_fsr_sharpness_reduction', IG.sharp >= 0 ? FSR_V[IG.sharp] : null); IG.changes--; break;
    case 'dither': igKeep('Display.postprocess_dither', IG.dither >= 0 ? IG.dither === 1 : null); break;
    case 'stretch': GLOBAL['Display.present_letterbox'] = !IG.stretch; if (GLOBAL['Display.present_letterbox'] === DEF['Display.present_letterbox'].def) delete GLOBAL['Display.present_letterbox']; IG.changes++; break;
    case 'fps': igKeep('GPU.framerate_limit', IG.fps); break;
    case 'smooth': igKeep('Vulkan.vulkan_async_skip_draws', IG.smooth); break;
    case 'touch': igKeep('HID.show_touch_overlay', IG.touch); break;
    case 'volume': igKeep('APU.volume', IG.volume); break;
    case 'msaa2x': case 'cutout': case 'shading': IG.changes++; break;
    case 'ctlStyle': if (IG.ctlStyle === 'modern') delete GLOBAL['@touch.style']; else GLOBAL['@touch.style'] = IG.ctlStyle; IG.changes++; break;
    default: if (!['hud', 'hudLayout', 'hudDetail', 'hudPos', 'hudLook', 'hudSize', 'hudBg', 'hudColors', 'pauseOnOpen', 'autosave', 'phones'].includes(f)) IG.changes++;
  }
  if (OV[g.id] && !Object.keys(OV[g.id]).length) delete OV[g.id];
}
action('igv', el => {
  const f = el.dataset.f; let v = el.dataset.v;
  if (v === undefined) v = !IG[f]; else if (f !== 'fps' && /^-?\d+$/.test(v) && typeof IG[f] === 'number') v = Number(v);
  if (f === 'winfg' && v) IG.lsfg = false;
  if (f === 'lsfg' && v) IG.winfg = false;
  IG[f] = v; igApply(f); render();
  if (f === 'winfg' || f === 'lsfg') toast(v ? 'Geração de quadros ligada nesta sessão; ela começa desligada a cada abertura' : 'Geração de quadros desligada');
});
action('igc', el => {
  const g = curGame(), f = el.dataset.f, d = Number(el.dataset.d);
  const row = Object.values(igRows(g)).flat().flatMap(x => x[1]).find(r => r.id === f); if (!row) return;
  const lo = row.from != null ? row.from : 0, n = row.vals.length;
  IG[f] = ((IG[f] - lo + d) % n + n) % n + lo;
  igApply(f); render();
});
action('igm', el => { const v = el.dataset.v; if (IG.metrics.has(v)) IG.metrics.delete(v); else IG.metrics.add(v); render(); });
action('input:igr', el => { IG[el.dataset.f] = Number(el.value); const o = el.parentElement.querySelector('output'); if (o) o.textContent = el.value + '%'; });
action('change:igr', el => { IG[el.dataset.f] = Number(el.value); igApply(el.dataset.f); render(); });
action('ig-page', el => { IG.page = el.dataset.v; render(); const b = app.querySelector('.igx-rows'); if (b) b.scrollTop = 0; });
action('ig-more', () => { IG.more[IG.page] = !IG.more[IG.page]; render(); });
action('ig-toast', el => toast(el.dataset.msg || ''));
action('ig-edit', () => go('touchedit'));
action('ig-phones', () => { IG.phones = !IG.phones; toast(IG.phones ? 'Telefones como controle · Ligado' : 'Telefones como controle · Desligado'); render(); });
action('ig-lsfg-import', () => { IG.lsfgReady = true; toast('No app, você escolhe o Lossless.dll; ele é convertido aqui num cache de shaders e apagado em seguida.'); render(); });
action('ig-lsfg-clear', () => { IG.lsfgReady = false; IG.lsfg = false; toast('Cache de shaders LSFG removido'); render(); });
action('ig-scene', () => { IG.scenes++; toast(`Cena ${IG.scenes} marcada`); render(); });
action('ig-shot', () => toast('Captura salva em Imagens/Xendroid+'));
action('ig-undo', () => { const g = curGame(); if (IG.ovStart) OV[g.id] = JSON.parse(JSON.stringify(IG.ovStart)); else delete OV[g.id]; const keep = IG.ovStart; igStart(g); IG.ovStart = keep; toast('As mudanças desta sessão foram desfeitas'); render(); });
action('ig-global', () => { const g = curGame(), o = OV[g.id] || {}; for (const [k, v] of Object.entries(o)) { if (String(v) === String(DEF[k] && DEF[k].def)) delete GLOBAL[k]; else GLOBAL[k] = v; } delete OV[g.id]; IG.kept = keptCount(g); toast('Viraram os ajustes globais; este jogo deixou de ter cópia própria'); render(); });
action('ig-close', () => { IG.menu = false; S.modal = null; render(); if (!IG.tipShown) { IG.tipShown = true; toast('Para abrir o menu: Voltar (gesto da borda ou botão), deslizar a partir da borda esquerda ou o botão Guia do controle.'); } });
action('ig-open', () => { IG.menu = true; render(); });
action('ig-quit', () => { S.modal = 'ig-quit'; render(); });
action('ig-logs', () => { S.modal = 'ig-logs'; render(); });
action('ig-quit-go', () => { S.modal = null; IG.menu = true; goTop('library'); toast('Jogo encerrado'); });
modal('ig-quit', () => ({ html: sheetHead('Sair do jogo?') + `<p style="margin:0;font-size:13.5px;color:var(--fg2)">O progresso não salvo pode ser perdido.</p><div class="acts"><button class="btn ghost" data-act="close" data-k="q-cancel" data-autofocus>Cancelar</button><button class="btn danger" data-act="ig-quit-go" data-k="q-go">Sair do jogo</button></div>` }));
modal('ig-logs', () => ({ html: sheetHead('Compartilhar diagnóstico · escolha uma sessão', 'Escolha uma sessão; dados pessoais saem antes.') + `<ul class="menu"><li><button data-act="toast" data-msg="No app, gera a cópia limpa de todas as sessões e abre o compartilhamento do Android." data-k="lg-all" data-autofocus>${ic('layers', 19)}<span>Todas as sessões guardadas</span></button></li>${RUNS.slice(0, 4).map((r, i) => `<li><button data-act="toast" data-msg="No app, gera a cópia limpa desta sessão e abre o compartilhamento do Android." data-k="lg-${i}">${ic('timeline', 19)}<span>${esc(GBY[r.gid].name)} · ${esc(r.when)} · ${r.kb} KB</span></button></li>`).join('')}<li><button data-act="close" data-k="lg-back">${ic('back', 19)}<span>Voltar</span></button></li></ul>` }));

/* ---------- HUD (FpsOverlay.kt): números de exemplo ---------- */
function hudHTML(preview) {
  if (!preview && !IG.hud) return '';
  const g = curGame(), st = perfOf(g), fps = fpsNow(g), capF = fps === '0' ? 60 : Number(fps);
  const t = (Date.now() - IG.t0) / 1000, f = Math.max(1, Math.min(st ? st.p50 : capF, capF) - (Math.sin(t) > .85 ? 1 : 0));
  const ms = (1000 / f).toFixed(1).replace('.', ','), fg = IG.winfg ? 2 : IG.lsfg ? IG.lsfgMul : 1;
  const fpsLine = fg > 1 ? `<b>${f} → ${Math.min(120, f * fg)}</b> FPS` : `<b>${f}</b> FPS`;
  const M = { vk: ['Envios Vulkan', `${f}/s`], cpu: ['CPU', '42%'], gpu: ['GPU', '76%'], gmem: ['Mem. GPU (total)', '1,3 GB'], ram: ['RAM', '6,1 / 10,7 GB'], bat: ['Bateria', '38,5 °C'], soc: ['SoC', '64 °C'], pwr: ['Potência', '5,8 W'], chg: ['Carga', '81%'], time: ['Autonomia', '~2 h 22 min'] };
  const order = ['vk', 'cpu', 'gpu', 'gmem', 'ram', 'bat', 'soc', 'pwr', 'chg', 'time'].filter(k => IG.metrics.has(k) && !(k === 'vk' && player()));
  const style = `--hs:${IG.hudSize / 100};--hbg:${IG.hudBg / 100};--hc:${IG.hudColors / 100}`;
  const cls = `hud2 ${IG.hudLook} ${IG.hudLayout} ${IG.hudLayout === 'horizontal' ? IG.hudPos : ''}`;
  const graph = IG.metrics.has('graph') && IG.hudDetail !== 'fps' ? `<svg class="hud2-graph" viewBox="0 0 60 20" preserveAspectRatio="none" aria-hidden="true"><polyline fill="none" stroke="currentColor" stroke-width="1.4" points="${Array.from({ length: 60 }, (_, i) => `${i},${(4 + 3 * Math.sin(i / 3) + (i > 40 && i < 43 ? 8 : 0)).toFixed(1)}`).join(' ')}"/></svg>` : '';
  const note = preview ? '' : ' data-note="hudlv"';
  if (IG.hudDetail === 'fps') return `<div class="${cls}" style="${style}"${note}><div class="hud2-l f">${fpsLine} <span class="k">·</span> ${ms} ms</div></div>`;
  const lines = order.map(k => `<div class="hud2-l"><span class="k">${M[k][0]}</span><span${k === 'bat' ? ' class="w"' : ''}>${M[k][1]}</span></div>`).join('');
  if (IG.hudDetail === 'metrics') return `<div class="${cls}" style="${style}"${note}><div class="hud2-l f">${fpsLine} <span class="k">·</span> ${ms} ms</div>${lines}${graph}</div>`;
  return `<div class="${cls} panel" style="${style}"${note}>
    <section><h6>Agora</h6><div class="hud2-l f">${fpsLine} <span class="k">·</span> ${ms} ms</div>${lines}${graph}</section>
    <section><h6>Ritmo</h6><div>Últimos 10 s: p50 ${ms} · p99 ${st ? st.ft99 : 34}</div><div>Sessão: FPS mediano ${st ? st.p50 : f}, baixo ${st ? st.p5 : f - 2}</div></section>
    <section><h6>Trabalho</h6><div>Pipelines criados: ${nf(g.perf ? g.perf.pipes[0] : 1200)} (${g.perf ? String(g.perf.pipes[1]).replace('.', ',') : '4,0'} s)</div><div>Áudio: sem falhas</div><div class="w">Calor: perto do limite do aparelho</div></section>
    <section><h6>Ajustes em vigor</h6><div>Driver: ${esc(DRIVERS.lastRun.drv)}</div><div>Limite de FPS: ${fps === '0' ? 'Sem limite' : fps + ' FPS'} · tela 120 Hz</div><div>${inEffectLines(g).length ? `Alterados do padrão (${inEffectLines(g).length}):` : 'Alterados do padrão: nenhum'}</div></section>
  </div>`;
}
let hudTimer = 0;
function hudTick() {
  clearInterval(hudTimer);
  hudTimer = setInterval(() => {
    if (!['ingame', 'hud'].includes(S.route.name)) { clearInterval(hudTimer); return; }
    const h = app.querySelector('.ig > .hud2'); if (!h) return;
    const tmp = document.createElement('div'); tmp.innerHTML = hudHTML(false); if (tmp.firstElementChild) h.innerHTML = tmp.firstElementChild.innerHTML;
  }, 1000);
}

screen('ingame', {
  title: 'Menu em jogo',
  render() {
    const g = curGame();
    if (IG.gid !== g.id) igStart(g);
    return `<div class="ig ${IG.menu ? 'dim' : ''}" style="${dynVars(g)}"><img class="ig-scene" src="${g.scene || ''}" alt="">
      ${!isC() && IG.touch ? touchOverlay(IG.menu ? .45 : 1) : ''}
      ${IG.menu ? '' : hudHTML(false)}
      ${IG.menu ? '' : `<button class="ig-edge" data-act="ig-open" data-k="ig-edge" aria-label="Abrir o menu (borda esquerda)" data-note="igedge"></button><span class="ig-edge-tip" data-note="igopen">Borda esquerda, Voltar ou Guia: menu</span>`}
      ${igMenu(g)}
    </div>`;
  },
  after() { hudTick(); },
  onBack() { IG.menu = !IG.menu; render(); return true; },
  tabStep(d) { if (!IG.menu) return; const t = IG_PAGES.map(p => p[0]); IG.page = t[(t.indexOf(IG.page) + d + t.length) % t.length]; render(); focusKey('igp-' + IG.page); },
  onKey(k) { if (k === 'start' || k === 'view') { IG.menu = !IG.menu; render(); return true; } return false; },
});

/* ---------- carregamento ---------- */
const LD = { t0: 0, gid: null, timer: 0, done: false, route: null, demo: 'go', kind: 'first' };
const LD_T = { first: [800, 2200, 6000, 7000, 7600], cached: [800, 2200, 2900, 3300, 3700] };
function ldSteps(el) {
  const [a, b, c, d] = LD_T[LD.kind], s = x => Math.round(x / 1000) + ' s';
  const pipes = LD.kind === 'first' ? Math.min(412, Math.max(0, Math.round((el - b) * .11))) : Math.min(12, Math.max(0, Math.round((el - b) / 50)));
  return [
    ['Iniciando o emulador', el >= a, el < a, s(a)],
    ['Iniciando o jogo', el >= b, el >= a && el < b, s(b - a)],
    [el >= b ? `Preparando os gráficos: ${pipes} ${pipes === 1 ? 'pipeline criado' : 'pipelines criados'}` : 'Preparando os gráficos', el >= c, el >= b && el < c, el >= c ? s(c - b) : ''],
    ['Esperando o primeiro quadro', el >= d, el >= c && el < d, el >= d ? s(d - c) : ''],
  ];
}
function ldElapsed() { const el = Date.now() - LD.t0; return LD.demo === 'hold' ? Math.min(el, LD.kind === 'first' ? 4600 : 2600) : el; }
function ldStepsHTML(el) { return ldSteps(el).map(([t, done, now, tm]) => `<li class="${done ? 'done' : now ? 'now' : ''}">${done ? ic('checkC', 20) : now ? `<span class="spin" style="display:inline-grid">${ic('refresh', 20)}</span>` : ic('clock', 20)}<span>${t}</span><time>${tm}</time></li>`).join(''); }
function ldBody() {
  const g = GBY[LD.gid] || curGame(), el = ldElapsed();
  const drv = label(DEF['Vulkan.vulkan_lib_path'], effOf(g, 'Vulkan.vulkan_lib_path')), lim = String(effOf(g, 'GPU.framerate_limit')), own = ovCount(g);
  return `${coverHTML(g)}<div>
    <span class="eyebrow" id="ld-eb">Abrindo · ${Math.floor(el / 1000)} s</span><h1>${esc(g.name)}</h1><div class="who">Entra como ${esc(activeProfile().tag)} · Title ID ${g.id}</div>
    <ol class="steps" id="ld-steps" data-note="ldsteps">${ldStepsHTML(el)}</ol>
    <div class="bar ind" style="max-width:520px;margin-top:14px"><i></i></div>
    <p class="note" id="ld-note" style="margin-top:12px;max-width:520px"${el >= 4000 ? '' : ' hidden'}>Ainda iniciando. A primeira vez de um jogo demora mais: os shaders que ele usa estão sendo montados, e as próximas vezes os reaproveitam.</p>
    <div class="opts" data-note="ldopts"><span class="badge">${ic('chip', 13)} ${esc(drv)}</span><span class="badge">${lim === '0' ? 'Sem limite' : `Limite ${lim} FPS`}</span>${own ? `<span class="badge acc">${own} ${own === 1 ? 'ajuste deste jogo' : 'ajustes deste jogo'}</span>` : ''}</div>
  </div>`;
}
function ldLive() {
  const el = ldElapsed(), q = id => document.getElementById(id);
  if (!q('ld-steps')) return;
  q('ld-eb').textContent = `Abrindo · ${Math.floor(el / 1000)} s`; q('ld-steps').innerHTML = ldStepsHTML(el); q('ld-note').hidden = el < 4000;
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
      if (LD.demo === 'go' && Date.now() - LD.t0 > LD_T[LD.kind][4] && !LD.done) {
        LD.done = true; clearInterval(LD.timer);
        const g = GBY[LD.gid];
        if (g && g.fictional) { S.gameId = LD.gid; go('launchfail', { lf: 'driver' }, { replace: true }); return; }
        IG.menu = false; S.gameId = LD.gid; igStart(g); go('ingame', {}, { replace: true });
      }
    }, 250);
  },
  onBack() { clearInterval(LD.timer); LD.done = true; return false; },
});
action('ld-cancel', () => { clearInterval(LD.timer); LD.done = true; back(); });

/* ---------- falha ao abrir (sempre com o jogo fictício) ---------- */
const LF = [
  { id: 'driver', n: 'Com driver personalizado', t: 'O jogo não pôde iniciar', why: 'O núcleo do emulador não iniciou: vkCreateDevice failed (VK_ERROR_INITIALIZATION_FAILED)', log: 'E xe: vkCreateDevice: VK_ERROR_INITIALIZATION_FAILED\nI xe: driver: Turnip B (exemplo)\nI xe: The emulator core did not start (exemplo fictício)', drv: true },
  { id: 'core', n: 'Com o driver do sistema', t: 'O jogo não pôde iniciar', why: 'O núcleo do emulador não iniciou: vkCreateDevice failed (VK_ERROR_INITIALIZATION_FAILED)', log: 'E xe: vkCreateDevice: VK_ERROR_INITIALIZATION_FAILED\nI xe: The emulator core did not start (exemplo fictício)' },
  { id: 'busy', n: 'Dados ocupados', t: 'Os dados do jogo estão ocupados', why: 'Outra operação de save, perfil ou conteúdo ainda está usando os dados do jogo. Espere ela terminar e abra o jogo de novo.', log: '' },
  { id: 'recovery', n: 'Restauração interrompida', t: 'Uma restauração de save foi interrompida', why: 'Uma restauração de save interrompida não pôde ser desfeita (exemplo). Os dados de antes dessa restauração ficam em content/.save-transactions; nenhum jogo ou operação de save pode rodar até que sejam recuperados.', log: '' },
  { id: 'running', n: 'Outro jogo rodando', t: 'O jogo não pôde iniciar', why: 'Um jogo já está rodando. Saia dele (Voltar → Sair do jogo) antes de abrir outro.', log: '' },
];
screen('launchfail', {
  title: 'Falha ao abrir',
  variants: [{ label: 'Motivo', list: LF.map(x => [x.id, x.n]), get: () => S.route.p.lf || 'driver', set: v => { S.route.p.lf = v; } }],
  render() {
    const g = GBY[FICTIONAL], f = LF.find(x => x.id === (S.route.p.lf || 'driver')) || LF[0];
    return `<div class="stagebox"><div class="c-bg" style="${dynVars(g)}"><div class="bgimg" style="background-image:url('${art(g)}')"></div></div>
    <div class="fail"><section class="card" data-note="failex">
      <h1>${ic('alert', 26)} ${esc(f.t)}</h1>
      <p style="margin:0;font-size:14px;color:var(--fg2)">${esc(f.why)}</p>
      ${f.log ? `<pre lang="en">${esc(f.log)}</pre>` : ''}
      <div class="row" data-note="failacts">
        ${f.drv ? `<button class="btn primary" data-act="lf-sysdrv" data-k="lf-sys" data-autofocus data-note="faildrv">${ic('chip', 16)} Tentar com o driver do sistema</button>` : ''}
        <button class="btn${f.drv ? '' : ' primary'}" data-act="lf-retry" data-k="lf-retry"${f.drv ? '' : ' data-autofocus'}>${ic('refresh', 16)} Tentar de novo</button>
        <button class="btn ghost" data-act="go" data-v="diagnostics" data-p="${g.id}" data-k="lf-logs">${ic('share', 16)} Compartilhar logs de diagnóstico</button>
        <button class="btn ghost" data-act="back" data-k="lf-back">Voltar</button>
      </div>
      <p class="note">Tentar de novo abre num processo novo.${f.drv ? ' O driver do sistema vale só para esta abertura; o escolhido continua salvo.' : ''}</p>
    </section></div>${isC() ? `<div style="position:absolute;left:0;right:0;bottom:0">${hints([['A', 'Selecionar', 'a'], ['B', 'Voltar', 'back']])}</div>` : ''}</div>`;
  },
});
action('lf-sysdrv', () => toast('No app, o jogo abre de novo com o driver do sistema, só desta vez. O jogo deste exemplo é fictício e não abre.'));
action('lf-retry', () => toast('No app, o jogo abre de novo num processo novo. O jogo deste exemplo é fictício e não abre.'));

/* ---------- HUD ---------- */
const THERMAL = {
  near: 'O telefone está perto do limite de calor e vai reduzir o desempenho em breve. Resolução ou limite de FPS menores, ou desligar o clock máximo forçado da GPU ou a geração de quadros, o mantêm mais frio.',
  throttling: 'O telefone chegou ao limite de calor e está reduzindo o desempenho. Resolução ou limite de FPS menores, ou desligar o clock máximo forçado da GPU ou a geração de quadros, o mantêm mais frio.',
};
screen('hud', {
  title: 'HUD de desempenho',
  variants: [
    { label: 'Formato', list: [['vertical', 'Vertical'], ['horizontal', 'Horizontal']], get: () => IG.hudLayout, set: v => { IG.hudLayout = v; } },
    { label: 'Detalhe', list: [['fps', 'Só FPS'], ['metrics', 'Métricas'], ['panel', 'Painel']], get: () => IG.hudDetail, set: v => { IG.hudDetail = v; } },
    { label: 'Estilo', list: [['box', 'Caixa'], ['outline', 'Contorno'], ['plain', 'Texto']], get: () => IG.hudLook, set: v => { IG.hudLook = v; } },
    { label: 'Aviso de calor', list: [['near', 'Perto do limite'], ['throttling', 'Reduzindo'], ['off', 'Nenhum']], get: () => IG.thermal, set: v => { IG.thermal = v; } },
  ],
  render() {
    const g = curGame(), was = IG.hud; IG.hud = true;
    const h = hudHTML(false); IG.hud = was;
    return `<div class="ig"><img class="ig-scene" src="${g.scene || ''}" alt="">
      ${!isC() ? touchOverlay(.8) : ''}
      ${h.replace('class="hud2', 'data-hudex="1" class="hud2')}
      ${IG.thermal !== 'off' ? `<div class="ig-banner" data-note="thermal">${ic('thermo', 18)}<span>${THERMAL[IG.thermal]}</span><button class="ibtn" style="width:28px;height:28px;color:#ffe2a3;flex:none" data-act="hud-ban" data-k="hd-ban" aria-label="Fechar aviso">${ic('x', 16)}</button></div>` : ''}
    </div>`;
  },
  after() { hudTick(); },
});
action('hud-ban', () => { IG.thermal = 'off'; render(); });
