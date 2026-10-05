/* Dados de exemplo do protótipo B+C. Títulos e Title IDs: patches/ e GAME_COMPAT.md; o resto é inventado. */
/* ============ dados de exemplo ============
   Títulos e Title IDs dos arquivos de patch e do GAME_COMPAT.md do repositório.
   Media IDs, tempos, notas e números de sessão são inventados para o protótipo. */
const COLS = ['Co-op local', 'Corrida', 'Zerar em 2026', 'Arcade'];
const FMT = { ISO: 'ISO', ZAR: 'ZAR', GOD: 'GOD', XEX: 'Pasta XEX', STFS: 'XBLA' };
const GAMES = [
  { id: '4D5307E6', name: 'Halo 3', fmt: 'ISO', size: '6,9 GB', media: '7A1C3E52', discs: 1, pal: ['#081f2b', '#1d6f86', '#f0c96a'], motif: 'rings', last: 1, ago: 'há 2 h', playMin: 872, runs: 23, fav: 1, cols: ['Co-op local'],
    compat: { s: 'playable', date: '01/10/2026', build: 'v412 · 5b62653', drv: 'Turnip 25.3.0 r2', gpu: 'Adreno 825', note: 'Campanha inteira a 30 FPS. Na tela dividida cai para perto de 24.' },
    patches: ['Aspect Ratio', 'Disable Lens Flares', '16x Anisotropic Filtering', 'Allow public IP addresses.', 'Disable XStringVerify'], pOn: ['Aspect Ratio', '16x Anisotropic Filtering'],
    tu: 'TU13', dlc: ['Pacote de mapas 1', 'Pacote de mapas 2', 'Pacote de mapas 3'], saves: 4, backup: 'há 3 dias', cache: [3, 48],
    perf: { t: 30, sd: .7, low: 24, lowP: .07, sec: 2890, cap: 30, first: 41, pipes: [1830, 6.4], audio: ['AAudio', 173400, 38], bat: [31, 41, 38], hz: 120, lim: 30, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Halo 3.iso' },
  { id: '4D5309C9', name: 'Forza Horizon', fmt: 'ISO', size: '7,8 GB', media: '2B90D4A1', discs: 1, pal: ['#260d06', '#c4501c', '#ffd27a'], motif: 'sun', last: 2, ago: 'ontem', playMin: 395, runs: 31, fav: 1, cols: ['Corrida'],
    compat: { s: 'ingame', date: '29/09/2026', build: 'v409 · 2d61c0e', drv: 'Turnip 25.3.0 r2', gpu: 'Adreno 825', note: 'GPU no limite: 24 a 30 FPS; na cidade cai para perto de 20.' },
    patches: ['Disable Demo Boundaries'], pOn: [], tu: null, dlc: [], saves: 2, backup: 'há 1 semana', cache: [5, 112],
    perf: { t: 27, sd: 2.1, low: 19, lowP: .14, sec: 1980, cap: 30, first: 58, pipes: [2410, 9.8], audio: ['AAudio', 118800, 214], bat: [33, 45, 44], hz: 120, lim: 30, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Forza Horizon.iso' },
  { id: '5454082B', name: 'Red Dead Redemption', sub: 'GOTY', fmt: 'ISO', size: '7,1 GB', media: 'D41E0C77', discs: 2, pal: ['#2a1005', '#8e3a14', '#f2b35a'], motif: 'mountains', last: 4, ago: 'há 3 dias', playMin: 1934, runs: 41, fav: 1, cols: ['Zerar em 2026'],
    compat: { s: 'ingame', date: '27/09/2026', build: 'v409 · 2d61c0e', drv: 'Turnip 25.3.0 r2', gpu: 'Adreno 825', note: 'Jogável, com engasgos ao chegar nas cidades.' },
    patches: ['Unlock FPS', 'Disable Depth of Field & Motion Blur', '16x Anisotropic Filtering', '360p Mode', '480p Mode', '540p Mode, 4x MSAA', 'Disable Sun Flare', 'No Trees - Performance Mode', 'Skip Grass Occlusion', 'Disable Shadows', 'Higher Quality Post Processing', 'Skip Intro', 'Alternative Script Timing & Asset Garbage Collection', 'Use Separate Audio Heap', 'Aspect Ratio', 'Infinite Horse Stamina', 'Bottomless Clip / Infinite Gun Magazine Size', 'Infinite Ammo'],
    pOn: ['Disable Depth of Field & Motion Blur', '16x Anisotropic Filtering', 'Skip Intro'], tu: 'TU2', dlc: ['Undead Nightmare', 'Pacote de missões'], saves: 6, backup: 'ontem', cache: [7, 164],
    perf: { t: 29, sd: 1.6, low: 22, lowP: .1, sec: 3510, cap: 30, first: 64, pipes: [3120, 12.1], audio: ['AAudio', 210600, 96], bat: [32, 43, 42], hz: 120, lim: 30, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Red Dead Redemption (Disc 1).iso' },
  { id: '544307D5', name: 'Ninja Gaiden II', fmt: 'ISO', size: '6,6 GB', media: '9C2240B8', discs: 1, pal: ['#14070a', '#7d1020', '#f4e2c0'], motif: 'slash', last: 5, ago: 'há 5 dias', playMin: 268, runs: 9, fav: 0, cols: [],
    compat: { s: 'playable', date: '12/06/2026', build: 'v388 · 5b41135', drv: 'Turnip 25.1.0', gpu: 'Adreno 830', note: 'Jogável com os dois ajustes deste jogo: modelos voltam e a GPU para de travar.' },
    patches: ['1280x720 Resolution', 'Chapter 12 Crash Workaround'], pOn: ['Chapter 12 Crash Workaround'], tu: 'TU3', dlc: [], saves: 3, backup: 'há 2 semanas', cache: [4, 71],
    perf: { t: 57, sd: 2.8, low: 44, lowP: .09, sec: 1340, cap: 60, first: 37, pipes: [1290, 4.2], audio: ['AAudio', 80400, 12], bat: [30, 39, 37], hz: 120, lim: 60, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Ninja Gaiden II.iso' },
  { id: '584108FF', name: 'Geometry Wars: Retro Evolved 2', fmt: 'STFS', size: '33 MB', media: '00000000', discs: 1, pal: ['#04050a', '#1c0f3a', '#3ef0ff'], iconStyle: 'neon', last: 3, ago: 'há 4 dias', playMin: 77, runs: 12, fav: 1, cols: ['Arcade'],
    compat: { s: 'playable', date: '30/09/2026', build: 'v410 · 9e3f2aa', drv: 'Sistema (Qualcomm)', gpu: 'Adreno 825', note: '60 FPS travados.' },
    patches: [], pOn: [], tu: null, dlc: [], saves: 1, backup: 'nunca', cache: [1, 6],
    perf: { t: 60, sd: .35, low: 58, lowP: .02, sec: 940, cap: 60, first: 9, pipes: [212, .6], audio: ['AAudio', 56400, 0], bat: [29, 33, 33], hz: 120, lim: 60, drv: 'Sistema (Qualcomm 819.0)' },
    path: '/storage/emulated/0/Games/Xbox 360/XBLA/584108FF' },
  { id: '4D5307F1', name: 'Fable II', fmt: 'GOD', size: '6,8 GB', media: '41E7B0C3', discs: 1, pal: ['#0d1a0f', '#2f6b3a', '#e8d58a'], motif: 'bokeh', last: 7, ago: 'há 2 semanas', playMin: 610, runs: 14, fav: 0, cols: ['Zerar em 2026'],
    compat: { s: 'playable', date: '13/06/2026', build: 'v389 · 7a0c113', drv: 'Turnip 25.1.0', gpu: 'Adreno 830', note: 'Sem ajuste especial.' },
    patches: ['60 FPS', '1280x720 Resolution', 'Disable MSAA (Multi-Sample Anti-Aliasing)', '21:9 Widescreen', '32:9 Ultrawidescreen'], pOn: ['1280x720 Resolution'], tu: null, dlc: ['Knothole Island', 'See the Future'], saves: 2, backup: 'há 2 semanas', cache: [3, 52],
    perf: { t: 30, sd: 1.1, low: 25, lowP: .05, sec: 2210, cap: 30, first: 52, pipes: [1505, 5.1], audio: ['AAudio', 132600, 21], bat: [31, 40, 39], hz: 120, lim: 30, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Fable II' },
  { id: '545407F2', name: 'Grand Theft Auto IV', fmt: 'ZAR', size: '5,4 GB', media: 'A07F11D2', discs: 1, pal: ['#0c1418', '#3a5260', '#e9e2cf'], motif: 'grid', last: 8, ago: 'há 3 semanas', playMin: 130, runs: 6, fav: 0, cols: [],
    compat: { s: 'ingame', date: '10/09/2026', build: 'v401 · 0ab3e1f', drv: 'Turnip 25.3.0 r2', gpu: 'Adreno 825', note: 'Roda, mas parte do trânsito some.' },
    patches: ['Unlock FPS', 'Skip Legal Screens', 'Disable blur filter', 'Aspect Ratio'], pOn: ['Skip Legal Screens'], tu: 'TU8', dlc: [], saves: 1, backup: 'nunca', cache: [4, 88],
    perf: { t: 24, sd: 2.6, low: 15, lowP: .16, sec: 1460, cap: 30, first: 71, pipes: [2050, 8.7], audio: ['AAudio', 87600, 64], bat: [32, 42, 41], hz: 120, lim: 30, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/GTA IV.zar' },
  { id: '4D530805', name: 'Alan Wake', fmt: 'ISO', size: '7,3 GB', media: '6E31A9F0', discs: 1, pal: ['#05080e', '#1b2c4a', '#cfe1ff'], motif: 'bokeh', last: 0, ago: null, playMin: 0, runs: 0, fav: 0, cols: ['Zerar em 2026'],
    compat: null, patches: ['60 FPS', 'Disable Motion Blur', 'Disable Lens Flares', 'Skip intro'], pOn: [], tu: null, dlc: ['The Signal', 'The Writer'], saves: 0, backup: 'nunca', cache: [0, 0], perf: null,
    path: '/storage/emulated/0/Games/Xbox 360/Alan Wake.iso' },
  { id: '4D5307DF', name: 'Blue Dragon', fmt: 'ISO', size: '7,5 GB', media: '18BD02E4', discs: 3, pal: ['#061a2d', '#1a5a9a', '#9fe3ff'], motif: 'bands', last: 10, ago: 'há 1 mês', playMin: 44, runs: 3, fav: 0, cols: ['Zerar em 2026'],
    compat: { s: 'intro', date: '02/09/2026', build: 'v398 · c71e2b0', drv: 'Turnip 25.3.0 r2', gpu: 'Adreno 825', note: 'Abre e mostra o menu; trava na primeira batalha.' },
    patches: ['Enable Wireframe', 'Enable Camera Bounding Box', '60 FPS (WIP)', 'Debug Menu'], pOn: [], tu: null, dlc: [], saves: 1, backup: 'nunca', cache: [2, 19],
    perf: { t: 22, sd: 4.5, low: 8, lowP: .28, sec: 610, cap: 30, first: 88, pipes: [640, 3.3], audio: ['AAudio', 36600, 140], bat: [30, 36, 36], hz: 120, lim: 30, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Blue Dragon (Disc 1).iso' },
  { id: '4E4D0859', name: 'Tekken Tag Tournament 2', fmt: 'ISO', size: '7,6 GB', media: '55C0E81B', discs: 1, pal: ['#190606', '#a8161b', '#ffe08a'], motif: 'slash', last: 6, ago: 'há 1 semana', playMin: 58, runs: 4, fav: 0, cols: ['Arcade'],
    compat: { s: 'ingame', date: '24/09/2026', build: 'v407 · 1c9b5d3', drv: 'Turnip 25.3.0 r2', gpu: 'Adreno 825', note: 'Lutas a 60 FPS, mas o começo de cada luta engasga.' },
    patches: ['Reduce input lag'], pOn: ['Reduce input lag'], tu: 'TU1', dlc: [], saves: 1, backup: 'nunca', cache: [3, 61],
    perf: { t: 56, sd: 3.6, low: 38, lowP: .12, sec: 980, cap: 60, first: 46, pipes: [1720, 7.9], audio: ['AAudio', 58800, 31], bat: [31, 40, 40], hz: 120, lim: 60, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Tekken Tag Tournament 2.iso' },
  { id: '4D5307F2', name: 'Viva Piñata', fmt: 'XEX', size: '6,2 GB', media: 'C3A90F14', discs: 1, pal: ['#2a0a2d', '#c2338f', '#ffd84a'], iconStyle: 'pinata', last: 0, ago: null, playMin: 0, runs: 0, fav: 0, cols: [],
    compat: null, patches: ['60 FPS'], pOn: [], tu: null, dlc: [], saves: 0, backup: 'nunca', cache: [0, 0], perf: null,
    path: '/storage/emulated/0/Games/Xbox 360/Viva Pinata' },
  { id: '4D53082D', name: 'Gears of War 2', fmt: 'ISO', size: '6,9 GB', media: '0F6D2C95', discs: 1, pal: ['#121314', '#4a4f52', '#c9a46a'], motif: 'shards', last: 11, ago: 'há 2 meses', playMin: 21, runs: 2, fav: 0, cols: ['Co-op local'],
    compat: { s: 'boots', date: '05/08/2026', build: 'v380 · e1d9a37', drv: 'Turnip 25.1.0', gpu: 'Adreno 825', note: 'Abre e fica em tela preta depois do logo.' },
    patches: ['Unlock FPS', 'Black Shading Fix', 'Ambient Occlusion - Option A: Upscaling Fix', 'Ambient Occlusion - Option B: Disable', 'Disable Motion Blur', 'Performance Patch: Optimized Settings (Smart Shadow Cuts)', '16x Anisotropic Filtering', 'Texture Streaming Fix', 'Disable Lens Flares', 'Depth of Field, Option A - Disable Gaussian Blur (Upscaling Fix)', 'Depth of Field, Option B - Disable Entire DoF Pipeline', 'Disable Bloom', 'Disable Coalesced Hash Check', 'Show FPS', '21:9 Ultrawide Aspect Ratio Support', 'Steam Deck / 16:10 Aspect Ratio Support'],
    pOn: [], tu: 'TU6', dlc: [], saves: 0, backup: 'nunca', cache: [1, 9], perf: null, noFrames: 'A última execução não chegou a mostrar um quadro.',
    path: '/storage/emulated/0/Games/Xbox 360/Gears of War 2.iso' },
  { id: '4D5308AB', name: 'Gears of War 3', fmt: 'ISO', size: '7,9 GB', media: 'E28A6B30', discs: 1, pal: ['#191312', '#5b1a17', '#d8d1c4'], motif: 'shards', last: 9, ago: 'há 1 semana', playMin: 95, runs: 5, fav: 0, cols: ['Co-op local'],
    compat: { s: 'intro', date: '25/09/2026', build: 'v408 · 4be0d1c', drv: 'Turnip 25.3.0 r2', gpu: 'Adreno 825', note: 'Menus e cenas animadas; trava ao carregar a campanha.' },
    patches: ['Unlock FPS', 'Disable Lens Flares', 'Disable Ambient Occlusion', 'Disable Motion Blur', '16x Anisotropic Filtering', 'Resolution Scaling Fix / Disable All Post-Processing', 'Disable Depth of Field', 'Disable Bloom', 'Performance Patch, Option A: Low Settings (No Dynamic Shadows)', 'Performance Patch, Option B: Optimized Settings (Smart Shadow Cuts)', 'Disable Coalesced hash check', 'Show FPS'],
    pOn: [], tu: 'TU6', dlc: [], saves: 1, backup: 'nunca', cache: [6, 133],
    perf: { t: 31, sd: 6, low: 12, lowP: .2, sec: 480, cap: 60, first: 79, pipes: [2890, 14.6], audio: ['AAudio', 28800, 210], bat: [33, 42, 42], hz: 120, lim: 60, drv: 'Turnip 25.3.0 r2' },
    path: '/storage/emulated/0/Games/Xbox 360/Gears of War 3.iso' },
  { id: '425607E5', name: 'Toy Story 3', fmt: 'ZAR', size: '4,1 GB', media: '7D02B6EE', discs: 1, pal: ['#0a2246', '#2f6fd1', '#ffd34d'], iconStyle: 'toy', last: 12, ago: 'há 3 meses', playMin: 6, runs: 1, fav: 0, cols: ['Co-op local'],
    compat: { s: 'nothing', date: '01/07/2026', build: 'v371 · 8f2e6d4', drv: 'Turnip 25.1.0', gpu: 'Adreno 825', note: 'Fecha logo depois de abrir (falha nativa).' },
    patches: ['60 FPS', '1024x576 Resolution'], pOn: [], tu: null, dlc: [], saves: 0, backup: 'nunca', cache: [0, 0], perf: null, noFrames: 'Encerrada por falha nativa (sinal 11) antes do primeiro quadro.',
    path: '/storage/emulated/0/Games/Xbox 360/Toy Story 3.zar' },
];
const GBY = Object.fromEntries(GAMES.map(g => [g.id, g]));
const CS = { playable: ['Jogável', 'Jogável'], ingame: ['No jogo, com problemas', 'Com problemas'], intro: ['Só abertura ou menus', 'Só menus'], boots: ['Abre, sem imagem', 'Sem imagem'], nothing: ['Não abre', 'Não abre'], none: ['Sem avaliação', 'Sem avaliação'] };
const stOf = g => g.compat ? g.compat.s : 'none';
const fmtLabel = g => FMT[g.fmt] || g.fmt;

/* ============ ajustes ============
   k = Seção.nome do TOML. ap: live = também muda com o jogo aberto (menu do jogo); boot = próxima abertura.
   n = cvar do core sem UI hoje (exposta agora). nOpt = valor do core que a lista atual não oferece.
   Textos dos ajustes que já existem vêm do strings.xml pt-BR; os novos traduzem a descrição do próprio core. */
const GROUPS = [['img', 'Imagem', 'image'], ['perf', 'Desempenho', 'chart'], ['scr', 'Tela e HUD', 'monitor'], ['audio', 'Áudio', 'speaker'], ['input', 'Controles', 'gamepad'], ['compat', 'Compatibilidade', 'shield'], ['drv', 'Driver e Vulkan', 'chip'], ['sys', 'Console e sistema', 'cpu'], ['dbg', 'Depuração', 'flask']];
const GNAME = Object.fromEntries(GROUPS.map(g => [g[0], g[1]]));
const DZ = [['0.0', 'Desligada'], ['0.05', '5%'], ['0.1', '10%'], ['0.15', '15%'], ['0.2', '20%'], ['0.25', '25%'], ['0.3', '30%']];
const SET = [
  // Imagem
  { k: 'Display.postprocess_scaling_and_sharpening', g: 'img', lvl: 1, ty: 'list', vt: 'str', def: 'bilinear', ap: 'live', t: 'Escala e nitidez', o: [['bilinear', 'Bilinear'], ['cas', 'AMD CAS'], ['fsr', 'FSR 1'], ['sgsr', 'SGSR (experimental)'], ['lanczos', 'Lanczos'], ['crt', 'CRT']], d: 'Filtro de ampliação da imagem final, do bilinear simples à nitidez AMD CAS, à ampliação FSR, ao SGSR da Qualcomm (experimental), ao Lanczos (nítido, sem halos) ou a um visual de CRT.' },
  { k: 'GPU.draw_resolution_scale_x', g: 'img', lvl: 1, ty: 'list', vt: 'int', def: '1', ap: 'boot', t: 'Escala de resolução, largura', o: [['1', '1x'], ['2', '2x'], ['3', '3x']], d: 'Desenha o jogo neste múltiplo da largura nativa. Só passos inteiros. Custa tempo de GPU e memória.' },
  { k: 'GPU.draw_resolution_scale_y', g: 'img', lvl: 1, ty: 'list', vt: 'int', def: '1', ap: 'boot', t: 'Escala de resolução, altura', o: [['1', '1x'], ['2', '2x'], ['3', '3x']], d: 'Desenha o jogo neste múltiplo da altura nativa. Só passos inteiros. Custa tempo de GPU e memória.' },
  { k: 'GPU.anisotropic_override', g: 'img', lvl: 1, ty: 'list', vt: 'int', def: '-1', ap: 'boot', t: 'Filtragem anisotrópica', o: [['-1', 'O jogo decide'], ['0', 'Desligada'], ['1', '1x'], ['2', '2x'], ['3', '4x'], ['4', '8x'], ['5', '16x']], d: 'Mantém nítidas as texturas vistas de lado (chão, estradas, paredes). 16x gasta um pouco de banda da GPU.' },
  { k: 'Display.postprocess_antialiasing', g: 'img', lvl: 1, ty: 'list', vt: 'str', def: 'none', ap: 'boot', t: 'Antisserrilhado', o: [['none', 'Desligado'], ['fxaa', 'FXAA'], ['fxaa_extreme', 'FXAA extremo']], d: 'Aplica o antisserrilhado FXAA de pós-processamento para suavizar bordas; recomendado com CAS ou FSR ativos.' },
  { k: 'Display.postprocess_dither', g: 'img', lvl: 1, ty: 'bool', vt: 'bool', def: false, ap: 'boot', t: 'Reduzir faixas de cor', d: 'Acrescenta um ruído fino à imagem final para os degradês (céu, neblina, menus) não mostrarem degraus em telas de 8 bits.' },
  { k: 'Display.present_letterbox', g: 'img', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Barras pretas (letterbox)', d: 'Mantém a proporção do jogo com barras pretas em vez de esticar para preencher a tela.' },
  { k: 'Console.widescreen', g: 'img', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Tela larga (widescreen)', d: 'Informa aos jogos uma tela 16:9 em vez de 4:3, o que define a proporção deles.' },
  { k: 'Console.internal_display_resolution', g: 'img', lvl: 1, ty: 'list', vt: 'int', def: '8', ap: 'boot', nOpt: '17', t: 'Modo de vídeo informado ao jogo', o: [['0', '640×480'], ['2', '720×480'], ['6', '1024×768'], ['8', '1280×720'], ['10', '1280×960'], ['15', '1920×540'], ['16', '1920×1080'], ['17', 'Personalizado']], d: 'Não é um ampliador: só diz ao jogo qual é a TV. A maioria dos títulos ignora. “Personalizado” usa a largura e a altura abaixo.' },
  { k: 'Video.internal_display_resolution_x', g: 'img', lvl: 2, ty: 'int', vt: 'int', def: 1280, min: 640, max: 1920, step: 16, unit: ' px', ap: 'boot', n: 1, dep: ['Console.internal_display_resolution', '17'], t: 'Largura personalizada', d: 'Largura informada ao jogo quando o modo de vídeo é Personalizado. O core aceita de 1 a 1920.' },
  { k: 'Video.internal_display_resolution_y', g: 'img', lvl: 2, ty: 'int', vt: 'int', def: 720, min: 360, max: 1080, step: 8, unit: ' px', ap: 'boot', n: 1, dep: ['Console.internal_display_resolution', '17'], t: 'Altura personalizada', d: 'Altura informada ao jogo quando o modo de vídeo é Personalizado. O core aceita de 1 a 1080.' },
  { k: 'GPU.draw_resolution_scale_threshold', g: 'img', lvl: 2, ty: 'list', vt: 'int', def: '0', ap: 'boot', n: 1, t: 'Não ampliar superfícies estreitas', o: [['0', 'Desligado'], ['80', 'até 80 px'], ['160', 'até 160 px'], ['320', 'até 320 px'], ['640', 'até 640 px']], d: 'Com a escala acima de 1x, superfícies até esta largura ficam na resolução nativa. Bloom e profundidade de campo costumam quebrar quando ampliados. Use o menor valor que conserta o efeito.' },
  { k: 'Display.postprocess_ffx_cas_additional_sharpness', g: 'img', lvl: 2, ty: 'list', vt: 'float', def: '0.0', ap: 'boot', dep: ['Display.postprocess_scaling_and_sharpening', 'cas'], t: 'CAS: nitidez extra', o: [['0.0', '0'], ['0.25', '0,25'], ['0.5', '0,5'], ['0.75', '0,75'], ['1.0', '1']], d: 'Quanto de nitidez o AMD CAS acrescenta além do padrão.' },
  { k: 'Display.postprocess_ffx_fsr_sharpness_reduction', g: 'img', lvl: 2, ty: 'list', vt: 'float', def: '0.2', ap: 'boot', dep: ['Display.postprocess_scaling_and_sharpening', 'fsr'], t: 'FSR: redução de nitidez', o: [['0.0', '0'], ['0.1', '0,1'], ['0.2', '0,2'], ['0.5', '0,5'], ['1.0', '1'], ['2.0', '2']], d: 'Quanto o FSR suaviza a nitidez na passada final; 0 é o mais nítido.' },
  { k: 'Display.postprocess_ffx_fsr_max_upsampling_passes', g: 'img', lvl: 2, ty: 'int', vt: 'int', def: 1, min: 1, max: 4, step: 1, ap: 'boot', n: 1, dep: ['Display.postprocess_scaling_and_sharpening', 'fsr'], t: 'FSR: passadas de ampliação', d: 'Quantas vezes o FSR pode ampliar em sequência quando a tela é muito maior que o jogo. Mais passadas custam GPU.' },
  { k: 'Kernel.kernel_display_gamma_type', g: 'img', lvl: 2, ty: 'list', vt: 'int', def: '2', ap: 'boot', nOpt: '3', t: 'Curva de gama da TV', o: [['0', 'Linear'], ['1', 'sRGB (CRT)'], ['2', 'BT.709 (HDTV)'], ['3', 'Potência personalizada']], d: 'Gama da tela informada aos jogos. BT.709 é a mais parecida com um Xbox 360 ligado numa HDTV.' },
  { k: 'Kernel.kernel_display_gamma_power', g: 'img', lvl: 2, ty: 'num', vt: 'float', def: 2.22, min: 1.6, max: 2.8, step: .02, ap: 'boot', n: 1, dep: ['Kernel.kernel_display_gamma_type', '3'], t: 'Gama personalizada', d: 'Expoente usado quando a curva é Potência personalizada. O padrão do core é 2,22.' },
  { k: 'Display.present_safe_area_x', g: 'img', lvl: 2, ty: 'int', vt: 'int', def: 100, min: 50, max: 100, step: 1, unit: '%', ap: 'boot', t: 'Manter pelo menos esta largura', d: 'Com barras pretas, abaixo de 100% as bordas podem ser cortadas no lugar das barras.' },
  // Desempenho
  { k: 'GPU.framerate_limit', g: 'perf', lvl: 1, ty: 'list', vt: 'int', def: '60', ap: 'live', t: 'Limite de FPS', o: [['30', '30'], ['45', '45'], ['60', '60'], ['90', '90'], ['120', '120'], ['0', 'Sem limite']], d: 'Limita quantos quadros por segundo o emulador mostra. O console nunca passou de 60; acima da tela é só calor e bateria.' },
  { k: 'GPU.guest_display_refresh_cap', g: 'perf', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Limitar a atualização de tela do jogo (VSync)', d: 'Limita os vblanks à taxa de 50/60 Hz do console; desligado deixa o jogo rodar o mais rápido possível.' },
  { k: 'GPU.async_shader_compilation', g: 'perf', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Compilação assíncrona de shaders', d: 'Compila shaders novos em segundo plano. Desligada, cria cada pipeline na hora: mais engasgos, sem artefatos.' },
  { k: 'GPU.async_shader_vs_interpreter', g: 'perf', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, dep: ['GPU.async_shader_compilation', true], t: 'Interpretar vertex shaders enquanto compilam', d: 'Desenha vertex shaders novos com o interpretador até a versão compilada ficar pronta, em vez de travar na tradução.' },
  { k: 'GPU.async_shader_skip_draws', g: 'perf', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Pular desenhos até o shader ficar pronto', d: 'Pula desenhos cujo shader ainda não pode ser usado (tesselação, por exemplo) até ele compilar em segundo plano. Evita engasgos, mas a geometria aparece alguns quadros depois.' },
  { k: 'GPU.pipeline_storage_precreate', g: 'perf', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Pré-criar pipelines ao abrir o jogo', d: 'Cria na abertura os pipelines que as sessões anteriores usaram, para não compilarem no meio do jogo. Desligado, ainda reaproveita os shaders guardados.' },
  { k: 'Kernel.precise_guest_delays', g: 'perf', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Esperas precisas do jogo', d: 'Atende as esperas do jogo com precisão abaixo de 1 ms. Uma espera comum do sistema passa do ponto e aumenta o tempo de cada quadro.' },
  { k: 'APU.apu_performance_hint', g: 'perf', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Dica de desempenho para o áudio (ADPF)', d: 'Registra uma sessão ADPF nas threads de despacho e de áudio para o sistema segurar os clocks no prazo do áudio.' },
  { k: 'Vulkan.adrenotools_force_max_clocks', g: 'perf', lvl: 2, ty: 'bool', vt: 'bool', def: false, ap: 'boot', t: 'Forçar clocks máximos da GPU', d: 'Com driver personalizado, pede ao KGSL a GPU no clock máximo. Esquenta mais.' },
  { k: 'Vulkan.adrenotools_turbo_reassert_seconds', g: 'perf', lvl: 2, ty: 'int', vt: 'int', def: 5, min: 0, max: 30, step: 1, unit: ' s', ap: 'boot', n: 1, dep: ['Vulkan.adrenotools_force_max_clocks', true], t: 'Repetir o pedido de clocks máximos a cada', d: 'O pedido se perde quando o app sai de foco: num POCO F7, depois de abrir a barra de notificações, o Forza Horizon rodou à metade dos FPS até reiniciar. 0 pede só na abertura.' },
  { k: 'GPU.vulkan_mid_frame_submission_draws', g: 'perf', lvl: 3, ty: 'int', vt: 'int', def: 1300, min: 0, max: 4096, step: 50, ap: 'boot', t: 'Enviar à GPU a cada N desenhos', d: 'Divide o quadro em envios para a GPU trabalhar enquanto a CPU monta o resto. 0 desliga; valores pequenos prejudicam GPUs por tiles.' },
  { k: 'Kernel.guest_scheduler', g: 'perf', lvl: 3, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Agendador do jogo (fibras cooperativas)', d: 'Executa as threads do jogo como fibras cooperativas.' },
  // Tela e HUD (do menu do jogo; salvos pelo app, fora do TOML)
  { k: '@display', g: 'scr', lvl: 1, ty: 'list', vt: 'app', def: 'fit', ap: 'live', t: 'Modo de exibição', o: [['fit', 'Ajustar'], ['fill', 'Preencher'], ['stretch', 'Esticar'], ['int', 'Inteiro']], d: 'Também muda ao vivo no menu do jogo; aqui fica o valor de partida deste jogo.' },
  { k: '@color', g: 'scr', lvl: 1, ty: 'list', vt: 'app', def: 'off', ap: 'live', t: 'Filtro de cor', o: [['off', 'Desligado'], ['gray', 'Cinza'], ['contrast', 'Contraste'], ['warm', 'Quente'], ['vivid', 'Vívido']], d: 'Filtro aplicado na saída. “Vívido” é o falso HDR em telas SDR.' },
  { k: '@hud', g: 'scr', lvl: 1, ty: 'list', vt: 'app', def: 'off', ap: 'live', t: 'HUD de desempenho', o: [['off', 'Desligado'], ['compact', 'Compacto'], ['full', 'Completo'], ['panel', 'Painel']], d: 'FPS e tempo de quadro; o completo e o painel mostram CPU, GPU, RAM, temperaturas e ajustes em vigor.' },
  { k: '@fg', g: 'scr', lvl: 2, ty: 'list', vt: 'app', def: 'off', ap: 'live', t: 'Geração de quadros (experimental)', o: [['off', 'Desligada'], ['winfg', 'Win-FG 2×'], ['lsfg', 'LSFG']], w: 'Experimental e desligada por padrão. LSFG só funciona depois de importar o seu Lossless.dll.', d: 'Gera quadros intermediários. Latência e artefatos ainda dependem de validação no aparelho.' },
  // Áudio
  { k: 'APU.volume', g: 'audio', lvl: 1, ty: 'int', vt: 'int', def: 100, min: 0, max: 100, step: 5, unit: '%', ap: 'live', n: 1, t: 'Volume', d: 'Volume de toda a saída de áudio. O menu do jogo continua mudando ao vivo; aqui fica o valor de partida.' },
  { k: 'APU.mute', g: 'audio', lvl: 1, ty: 'bool', vt: 'bool', def: false, ap: 'boot', t: 'Sem som', d: 'Silencia toda a saída de áudio.' },
  { k: 'APU.apu_aaudio_buffer_bursts', g: 'audio', lvl: 2, ty: 'int', vt: 'int', def: 4, min: 2, max: 8, step: 1, ap: 'boot', t: 'Profundidade do buffer de áudio', d: 'Mais blocos no buffer evitam estalos e aumentam a latência.' },
  { k: 'APU.apu_aaudio_adaptive_buffer', g: 'audio', lvl: 2, ty: 'bool', vt: 'bool', def: false, ap: 'boot', t: 'Buffer de áudio adaptativo', d: 'Cresce um bloco a cada falha de áudio e volta depois de 30 s calmos, até 3×.' },
  { k: 'APU.xma_decoder', g: 'audio', lvl: 3, ty: 'list', vt: 'str', def: 'new', ap: 'boot', t: 'Decodificador XMA', o: [['new', 'new'], ['old', 'old'], ['master', 'master'], ['fake', 'fake']], d: 'Implementação usada para decodificar o áudio XMA.' },
  // Controles
  { k: 'HID.show_touch_overlay', g: 'input', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'live', t: 'Mostrar controle na tela', d: 'Desenha o controle na tela durante o jogo; some quando um controle físico joga como P1.' },
  { k: 'HID.vibration', g: 'input', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Vibração do controle', d: 'Liga ou desliga a vibração pedida pelo jogo.' },
  { k: 'HID.left_stick_deadzone_percentage', g: 'input', lvl: 1, ty: 'list', vt: 'float', def: '0.0', ap: 'boot', t: 'Zona morta do stick esquerdo', o: DZ, d: 'Ignora movimentos pequenos do stick esquerdo, para um controle com drift.' },
  { k: 'HID.right_stick_deadzone_percentage', g: 'input', lvl: 1, ty: 'list', vt: 'float', def: '0.0', ap: 'boot', t: 'Zona morta do stick direito', o: DZ, d: 'Ignora movimentos pequenos do stick direito, para um controle com drift.' },
  { k: 'HID.guide_button', g: 'input', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Enviar o botão Guia ao jogo', d: 'Repassa ao jogo os toques no botão Guia do controle.' },
  // Compatibilidade
  { k: 'GPU.clear_memory_page_state', g: 'compat', lvl: 2, ty: 'bool', vt: 'bool', def: false, ap: 'boot', t: 'Atualizar o estado das páginas de memória', d: 'Torna visíveis as escritas da GPU que o jogo lê de volta. Conserta modelos sumidos em títulos da Team Ninja. Custa uma passada por quadro: use por jogo.' },
  { k: 'GPU.depth_float24_convert_in_pixel_shader', g: 'compat', lvl: 2, ty: 'bool', vt: 'bool', def: false, ap: 'boot', t: 'Converter profundidade float24 no pixel shader', d: 'Evita travamentos da GPU (device lost) em Adreno com Turnip em alguns jogos, como Ninja Gaiden II.' },
  { k: 'GPU.occlusion_query', g: 'compat', lvl: 2, ty: 'list', vt: 'str', def: 'fast', ap: 'boot', t: 'Consulta de oclusão', o: [['fake', 'Simulada'], ['fast', 'Rápida'], ['fast-alt', 'Rápida, zeros precisos'], ['strict', 'Estrita']], d: 'Como o jogo descobre se algo está visível. Simulada inventa o resultado (mais rápida); Estrita espera a GPU responder.' },
  { k: 'GPU.readback_resolve_sync', g: 'compat', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Sincronizar a leitura de resolves', d: 'Espera a GPU depois de cada cópia para a memória do jogo ficar coerente no mesmo quadro. Desligado copia sem esperar.' },
  { k: 'GPU.precise_interpolation', g: 'compat', lvl: 2, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, w: 'Precisa de baricêntricas no driver (VK_KHR_fragment_shader_barycentric).', t: 'Interpolação precisa', d: 'Interpola as entradas dos pixel shaders exatamente como o console. Conserta ruído em jogos como Perfect Dark e Tenchu Z.' },
  { k: 'GPU.depth_bias_shader_offset', g: 'compat', lvl: 2, ty: 'bool', vt: 'bool', def: false, ap: 'boot', n: 1, t: 'Profundidade de decalques no shader', d: 'Evita Z-fighting em jogos que usam valores mínimos de depth bias. Custo pequeno.' },
  { k: 'GPU.readback_resolve', g: 'compat', lvl: 3, ty: 'list', vt: 'str', def: 'uma', ap: 'boot', t: 'Leitura de resolves pela CPU', o: [['uma', 'UMA (direto)'], ['fast', 'Rápida'], ['all', 'Todos'], ['none', 'Desligada']], d: 'UMA lê direto da memória compartilhada; é o único modo que funciona em Adreno.' },
  { k: 'GPU.memexport_enable', g: 'compat', lvl: 3, ty: 'bool', vt: 'bool', def: false, ap: 'boot', n: 1, w: 'No Turnip não muda nada: falta VK_EXT_external_memory_host.', t: 'Memexport visível para a CPU', d: 'Para jogos que leem na CPU o que a GPU exportou.' },
  { k: 'Vulkan.vulkan_allow_reverse_z', g: 'compat', lvl: 3, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Profundidade invertida direto no driver', d: 'Passa ao driver as faixas de profundidade invertidas (inverse-Z). Desligue se o driver errar o desenho.' },
  { k: 'Kernel.stack_size_multiplier_hack', g: 'compat', lvl: 3, ty: 'int', vt: 'int', def: 1, min: 1, max: 8, step: 1, unit: '×', ap: 'boot', n: 1, t: 'Multiplicador da pilha (hack)', d: 'Hack para jogos com problemas de setjmp/longjmp.' },
  { k: 'CPU.collapse_memory_delay_spins', g: 'compat', lvl: 3, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Encurtar esperas por contagem', d: 'Pula o tempo real de laços de espera do jogo. Desligue neste jogo se ele depender desse tempo.' },
  // Driver e Vulkan
  { k: 'Vulkan.vulkan_lib_path', g: 'drv', lvl: 1, ty: 'list', vt: 'str', def: '', ap: 'boot', t: 'Driver Vulkan', o: [['', 'Sistema'], ['turnip-25.3.0-r2', 'Turnip 25.3.0 r2'], ['turnip-26.0-dev', 'Turnip 26.0 dev']], d: 'Driver personalizado (por exemplo Turnip) para carregar no lugar do driver do sistema (Qualcomm 819.0 neste aparelho). Só em GPUs Adreno.' },
  { k: 'Vulkan.turnip_debug', g: 'drv', lvl: 3, ty: 'list', vt: 'str', def: 'sysmem', ap: 'boot', t: 'Modo de depuração do Turnip', o: [['', 'Nenhum (GMEM)'], ['sysmem', 'sysmem'], ['sysmem,nolrz', 'sysmem + nolrz'], ['sysmem,noubwc', 'sysmem + noubwc']], d: 'sysmem renderiza sem tiles: mais lento, mas evita uma classe de travamentos da GPU Adreno.' },
  { k: 'Vulkan.vulkan_dynamic_rendering', g: 'drv', lvl: 3, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Dynamic rendering', d: 'Usa VK_KHR_dynamic_rendering no lugar dos render passes tradicionais. Pode melhorar ou piorar conforme o driver.' },
  { k: 'Vulkan.vulkan_avoid_geometry_shaders', g: 'drv', lvl: 3, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Evitar geometry shaders', d: 'Em GPUs por tiles (Adreno/Turnip), desvia sprites de pontos e quad lists por caminhos mais baratos. Lido ao iniciar a GPU.' },
  { k: 'Vulkan.vulkan_depth_unorm24', g: 'drv', lvl: 3, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Profundidade D24 nativa', d: 'Usa o formato D24_UNORM_S8_UINT quando o driver suporta. Desligado força a emulação em float32, como no driver proprietário da Adreno.' },
  // Console e sistema
  { k: 'Console.user_language', g: 'sys', lvl: 1, ty: 'list', vt: 'int', def: '1', ap: 'boot', t: 'Idioma do console', o: [['1', 'Inglês'], ['2', 'Japonês'], ['3', 'Alemão'], ['4', 'Francês'], ['5', 'Espanhol'], ['6', 'Italiano'], ['9', 'Português'], ['12', 'Russo']], d: 'Idioma do console informado aos jogos.' },
  { k: 'Console.user_country', g: 'sys', lvl: 1, ty: 'list', vt: 'int', def: '103', ap: 'boot', t: 'País do console', o: [['13', 'Brasil'], ['84', 'Portugal'], ['103', 'Estados Unidos'], ['35', 'Reino Unido'], ['53', 'Japão'], ['24', 'Alemanha']], d: 'País/região do console informado aos jogos.' },
  { k: 'General.apply_patches', g: 'sys', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Aplicar patches', d: 'Aplica os arquivos de patch aos jogos quando eles carregam.' },
  { k: 'UI.android_soft_keyboard', g: 'sys', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Teclado do Android para texto do jogo', d: 'Abre o teclado do Android quando o jogo pede para digitar (gamertags, nomes de save).' },
  { k: 'UI.android_message_box', g: 'sys', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Diálogo do Android para mensagens do jogo', d: 'Mostra as perguntas do próprio jogo num diálogo que você responde.' },
  { k: 'UI.show_achievement_notification', g: 'sys', lvl: 1, ty: 'bool', vt: 'bool', def: true, ap: 'boot', t: 'Mostrar notificação de conquista', d: 'Mostra um aviso na tela quando você desbloqueia uma conquista.' },
  { k: 'UI.achievement_notification_position_by_game', g: 'sys', lvl: 2, ty: 'bool', vt: 'bool', def: false, ap: 'boot', n: 1, t: 'Conquista na posição que o jogo pede', d: 'Desligado, a notificação aparece sempre embaixo, no centro.' },
  { k: 'General.launch_module', g: 'sys', lvl: 2, ty: 'text', vt: 'str', def: '', ph: 'default.xex', ap: 'boot', n: 1, t: 'Executável a iniciar', d: 'Executável do disco ou pacote a iniciar no lugar do default.xex. Em branco usa o padrão.' },
  { k: 'Storage.mount_memory_unit', g: 'sys', lvl: 2, ty: 'bool', vt: 'bool', def: false, ap: 'boot', n: 1, t: 'Montar unidade de memória (MU)', d: 'Monta uma unidade de memória como a do console.' },
  { k: 'Kernel.console_type', g: 'sys', lvl: 3, ty: 'list', vt: 'int', def: '-1', ap: 'boot', n: 1, t: 'Tipo de console', o: [['-1', 'Retail'], ['0', 'Kit de desenvolvimento'], ['1', 'Kit de teste']], d: 'Tipo de console informado ao jogo.' },
  { k: 'Kernel.cl', g: 'sys', lvl: 3, ty: 'text', vt: 'str', def: '', ph: 'sem argumentos', ap: 'boot', n: 1, t: 'Linha de comando extra para o jogo', d: 'Argumentos repassados ao jogo na abertura.' },
  { k: 'General.guest_crash_is_fatal', g: 'sys', lvl: 3, ty: 'bool', vt: 'bool', def: true, ap: 'boot', n: 1, t: 'Encerrar quando uma thread do jogo falhar', d: 'Grava um relatório completo e encerra, em vez de deixar a thread parada (o que parece um travamento). Desligue só para inspecionar.' },
  // Depuração
  { k: 'Logging.log_level', g: 'dbg', lvl: 3, ty: 'list', vt: 'int', def: '2', ap: 'boot', t: 'Nível de log', o: [['0', 'Erro'], ['1', 'Aviso'], ['2', 'Info'], ['3', 'Depuração']], d: 'Quanto o emulador escreve no xe.log.' },
  { k: 'Logging.log_mask', g: 'dbg', lvl: 3, ty: 'list', vt: 'int', def: '0', ap: 'boot', t: 'Filtro de log', o: [['0', 'Tudo'], ['7', 'Só GPU'], ['13', 'Só áudio'], ['14', 'Só kernel'], ['11', 'Só CPU']], d: 'Mantém no log só as linhas de um subsistema.' },
  { k: 'Display.show_debug_overlay', g: 'dbg', lvl: 3, ty: 'bool', vt: 'bool', def: false, ap: 'boot', t: 'Sobreposição de depuração', d: 'Mostra a sobreposição de depuração do core por cima do jogo.' },
  { k: 'Logging.dump_session_logs', g: 'dbg', lvl: 1, ty: 'action', vt: 'none', def: null, ap: 'act', t: 'Exportar logs da sessão para Downloads', d: 'Exporta o log do emulador e o logcat desta sessão num zip em Downloads.' },
];
const RES = { k: '@res', g: 'img', lvl: 1, ty: 'list', vt: 'virtual', def: '1', ap: 'boot', t: 'Escala de resolução', o: [['1', '1x'], ['2', '2x'], ['3', '3x']], d: 'Largura e altura juntas.' };
const DEF = Object.fromEntries(SET.concat([RES]).map(d => [d.k, d]));
const NEW_COUNT = SET.filter(d => d.n).length;
const NEWOPT_COUNT = SET.filter(d => d.nOpt).length;

const PRESETS = [
  { id: 'perf', t: 'Desempenho', d: 'Menos trabalho de GPU por quadro.', set: { 'GPU.draw_resolution_scale_x': '1', 'GPU.draw_resolution_scale_y': '1', 'Display.postprocess_scaling_and_sharpening': 'bilinear', 'GPU.anisotropic_override': '0', 'Display.postprocess_antialiasing': 'none' } },
  { id: 'bal', t: 'Equilíbrio', d: 'FSR na saída e anisotrópica 4x.', set: { 'GPU.draw_resolution_scale_x': '1', 'GPU.draw_resolution_scale_y': '1', 'Display.postprocess_scaling_and_sharpening': 'fsr', 'GPU.anisotropic_override': '3' } },
  { id: 'qual', t: 'Qualidade', d: 'Escala 2x, Lanczos e anisotrópica 16x. Custa GPU e memória.', set: { 'GPU.draw_resolution_scale_x': '2', 'GPU.draw_resolution_scale_y': '2', 'Display.postprocess_scaling_and_sharpening': 'lanczos', 'GPU.anisotropic_override': '5', 'GPU.draw_resolution_scale_threshold': '160' } },
];


/* ============ opções do próprio app (só globais; guardadas pelo app, fora do TOML) ============ */
const APPSET = [
  { k: '@app.level', g: 'app', lvl: 1, ty: 'list', vt: 'app', def: '2', ap: 'live', n: 1, t: 'Quantos ajustes mostrar', o: [['1', 'Essencial'], ['2', 'Avançado'], ['3', 'Tudo']], d: 'Essencial mostra o que os jogadores mudam; Avançado soma imagem, desempenho e compatibilidade; Tudo mostra também os internos do motor e a depuração. Hoje são dois modos: Jogador e Desenvolvedor.' },
  { k: '@app.mode', g: 'app', lvl: 1, ty: 'list', vt: 'app', def: 'auto', ap: 'live', n: 1, t: 'Modo controle', o: [['auto', 'Automático'], ['b', 'Sempre toque'], ['c', 'Sempre controle']], d: 'Com um controle conectado, a interface muda para o modo controle: carrossel de capas, abas por LB/RB e ajustes em linhas ◀ ▶. Sem controle, volta ao toque.' },
  { k: '@app.confirm', g: 'app', lvl: 1, ty: 'list', vt: 'app', def: 'a', ap: 'live', t: 'Botões nos menus', o: [['a', 'A confirma, B volta'], ['b', 'B confirma, A volta']], d: 'Para a biblioteca, o menu do jogo e o editor de layout; os botões do próprio jogo nunca mudam.' },
  { k: '@app.uisize', g: 'app', lvl: 1, ty: 'int', vt: 'app', def: 100, min: 85, max: 130, step: 5, unit: '%', ap: 'live', t: 'Tamanho da interface', d: 'Aumenta ou diminui as telas do app e o menu em jogo. Os controles de toque, o HUD e o editor de toque mantêm o tamanho próprio.' },
  { k: '@app.textsize', g: 'app', lvl: 1, ty: 'int', vt: 'app', def: 100, min: 85, max: 150, step: 5, unit: '%', ap: 'live', t: 'Tamanho do texto', d: 'Além do tamanho de fonte do aparelho.' },
  { k: '@app.lang', g: 'app', lvl: 1, ty: 'list', vt: 'app', def: 'sys', ap: 'live', t: 'Idioma do app', o: [['sys', 'Idioma do aparelho'], ['pt', 'Português (Brasil)'], ['en', 'English']], d: 'O idioma do próprio app. O idioma que os jogos veem é o ajuste “Idioma do console”.' },
  { k: '@app.updates', g: 'app', lvl: 1, ty: 'list', vt: 'app', def: 'stable', ap: 'live', t: 'Atualizações do app', o: [['stable', 'Estável'], ['preview', 'Prévia'], ['off', 'Desligado']], d: 'Estável oferece novas versões, cada uma conferida com o SHA-256 publicado e instalada só depois que você confirma. Prévia oferece também versões menos testadas.' },
];
for (const a of APPSET) DEF[a.k] = a;

/* ============ drivers (catálogo de exemplo; a fonte padrão é a do DriverSources.DEFAULT) ============ */
const DRIVERS = {
  sources: ['K11MCH1/AdrenoToolsDrivers'],
  gpu: 'Adreno 825', system: 'Qualcomm 819.0',
  installed: [
    { id: 'turnip-25.3.0-r2', name: 'Turnip 25.3.0 r2', state: 'verified', from: 'K11MCH1/AdrenoToolsDrivers', size: '14,2 MB', date: '02/09/2026' },
    { id: 'turnip-26.0-dev', name: 'Turnip 26.0 dev', state: 'unverified', from: 'ZIP importado', size: '15,0 MB', date: '20/09/2026' },
    { id: 'turnip-24.3-r9', name: 'Turnip 24.3 r9', state: 'damaged', from: 'K11MCH1/AdrenoToolsDrivers', size: '12,8 MB', date: '11/05/2026' },
  ],
  available: [
    { id: 'turnip-26.0.0-r1', name: 'Turnip 26.0.0 r1', from: 'K11MCH1/AdrenoToolsDrivers', size: '15,4 MB', date: '28/09/2026', sha: true, suggested: true },
    { id: 'turnip-25.3.1-r3', name: 'Turnip 25.3.1 r3', from: 'K11MCH1/AdrenoToolsDrivers', size: '14,4 MB', date: '15/09/2026', sha: true },
    { id: 'turnip-25.2.4', name: 'Turnip 25.2.4', from: 'K11MCH1/AdrenoToolsDrivers', size: '14,1 MB', date: '03/08/2026', sha: false },
  ],
  previous: 'turnip-26.0-dev', selectedAt: '28/09/2026 20:05',
  lastRun: { drv: 'Turnip 25.3.0 r2', game: 'Halo 3', when: 'hoje, 13:10' },
};

/* ============ perfis (U11) e saves ============ */
const PROFILES = [
  { xuid: 'E03000A1B2C3D4E5', tag: 'XenPlayer', c: ['#2f8f4e', '#0f3d22'], lang: 'Português', region: 'Brasil', active: true, slot: 1, games: 9, files: 61, mb: 41 },
  { xuid: 'E03000F6A7B8C9D0', tag: 'Lucas', c: ['#2f6fd1', '#0a2246'], lang: 'Português', region: 'Brasil', active: false, slot: 2, games: 3, files: 9, mb: 12 },
  { xuid: 'E030001122334455', tag: 'Convidado', c: ['#b23a3f', '#3a0608'], lang: 'Inglês', region: 'Estados Unidos', active: false, slot: null, games: 1, files: 2, mb: 2 },
];
const PROFILE_TRASH = [{ xuid: 'E0300099AABBCCDD', tag: 'Antigo', c: ['#7a6633', '#2a2210'], removed: '12/09/2026', games: 2, files: 14, mb: 6 }];
const SAVES = {
  '4D5307E6': [{ xuid: 'E03000A1B2C3D4E5', files: 6, kb: 2240, when: 'hoje, 13:58' }, { xuid: 'E03000F6A7B8C9D0', files: 2, kb: 512, when: '21/09/2026' }],
  '5454082B': [{ xuid: 'E03000A1B2C3D4E5', files: 12, kb: 8960, when: 'há 3 dias' }],
  '4D5309C9': [{ xuid: 'E03000A1B2C3D4E5', files: 3, kb: 1310, when: 'ontem' }, { xuid: 'E030001122334455', files: 1, kb: 260, when: '02/09/2026' }],
};
const SAVE_BACKUPS = [
  { name: '4D5307E6 · 2026-10-01 22:10.zip', when: '01/10/2026 22:10', size: '2,7 MB' },
  { name: '4D5307E6 · 2026-09-24 19:30.zip', when: '24/09/2026 19:30', size: '2,6 MB' },
];

/* ============ conteúdo instalado (TU e DLC) e lixeira ============ */
const CONTENT_TRASH = [{ gid: '4D5307E6', name: 'Title Update 12', type: 'tu', size: '5,9 MB', removed: '14/08/2026' }, { gid: '5454082B', name: 'Pacote de missões (antigo)', type: 'dlc', size: '402 MB', removed: '02/09/2026' }];
const TRASH_LIMIT = '2 GB';

/* ============ sessões guardadas (diagnóstico) e comparação A/B ============ */
const RUNS = [
  { gid: '4D5307E6', when: 'hoje, 13:10', dur: '48 min', end: 'ok', kb: 412, drv: 'Turnip 25.3.0 r2' },
  { gid: '4D5309C9', when: 'ontem, 21:44', dur: '33 min', end: 'ok', kb: 655, drv: 'Turnip 25.3.0 r2' },
  { gid: '584108FF', when: 'há 4 dias', dur: '16 min', end: 'ok', kb: 140, drv: 'Sistema' },
  { gid: '544307D5', when: 'há 5 dias', dur: '22 min', end: 'ok', kb: 301, drv: 'Turnip 25.3.0 r2' },
  { gid: '4D5308AB', when: 'há 1 semana', dur: '8 min', end: 'interrupted', kb: 188, drv: 'Turnip 25.3.0 r2', why: 'Encerrada pelo Android (pouca memória)' },
  { gid: '425607E5', when: '01/07/2026', dur: '9 s', end: 'fail', kb: 96, drv: 'Turnip 25.1.0', why: 'Falha nativa (sinal 11) antes do primeiro quadro' },
];
const BENCH = {
  gid: '4D5309C9', dim: 'driver',
  runs: [
    { id: 1, when: '28/09 20:01', side: 'A', drv: 'Turnip 25.3.0 r2', lim: 30, hz: 120, p50: 27, p5: 21, p99: 46, sec: 180, temp: 31, marks: 2 },
    { id: 2, when: '28/09 20:09', side: 'B', drv: 'Turnip 26.0 dev', lim: 30, hz: 120, p50: 28, p5: 22, p99: 44, sec: 176, temp: 34, marks: 2 },
    { id: 3, when: '28/09 20:17', side: 'B', drv: 'Turnip 26.0 dev', lim: 30, hz: 120, p50: 28, p5: 23, p99: 43, sec: 181, temp: 36, marks: 2 },
    { id: 4, when: '28/09 20:25', side: 'A', drv: 'Turnip 25.3.0 r2', lim: 30, hz: 120, p50: 26, p5: 20, p99: 47, sec: 179, temp: 37, marks: 2 },
    { id: 5, when: '29/09 19:40', side: null, drv: 'Turnip 26.0 dev', lim: 60, hz: 120, p50: 31, p5: 24, p99: 41, sec: 95, temp: 30, marks: 0 },
  ],
};

/* ============ controles conectados e mapeamento (U04, U05, U11) ============ */
const PADS = [
  { id: 'p1', name: '8BitDo Ultimate 2C', vidpid: '2DC8:301A', src: ['gamepad', 'joystick', 'direcional'], conn: 'Bluetooth', motor: true, gyro: false, rumble: null },
  { id: 'p2', name: 'DualSense Wireless Controller', vidpid: '054C:0CE6', src: ['gamepad', 'joystick'], conn: 'USB', motor: true, gyro: true, rumble: 'low' },
];
const INPUT_OTHER = [{ name: 'Teclado Bluetooth', src: ['teclado'], conn: 'Bluetooth' }];
const PAD_EVENTS = [['DualSense Wireless Controller', 'reconectado'], ['DualSense Wireless Controller', 'desconectado'], ['DualSense Wireless Controller', 'conectado'], ['8BitDo Ultimate 2C', 'conectado']];
/* os 16 botões do mapeamento, na ordem do app (GameButtons.ALL): id, nome, tecla padrão */
const KM_BTNS = [
  ['LEFT', 'Direcional para a esquerda', 'DPAD_LEFT'], ['UP', 'Direcional para cima', 'DPAD_UP'], ['RIGHT', 'Direcional para a direita', 'DPAD_RIGHT'], ['DOWN', 'Direcional para baixo', 'DPAD_DOWN'],
  ['A', 'A', 'BUTTON_A'], ['B', 'B', 'BUTTON_B'], ['X', 'X', 'BUTTON_X'], ['Y', 'Y', 'BUTTON_Y'], ['BACK', 'Back', 'BUTTON_SELECT'], ['START', 'Start', 'BUTTON_START'],
  ['LB', 'Botão superior esquerdo (LB)', 'BUTTON_L1'], ['RB', 'Botão superior direito (RB)', 'BUTTON_R1'],
  ['L3', 'Apertar o analógico esquerdo (L3)', 'BUTTON_THUMBL'], ['R3', 'Apertar o analógico direito (R3)', 'BUTTON_THUMBR'],
  ['LT', 'Gatilho esquerdo (LT)', 'BUTTON_L2'], ['RT', 'Gatilho direito (RT)', 'BUTTON_R2'],
];
const KM_DEF = Object.fromEntries(KM_BTNS.map(b => [b[0], b[2]]));
/* layouts de toque salvos (lp_): o que mudam em relação ao atual, por orientação: movidos, com outro tamanho, mostrados, escondidos */
const LAYOUTS = [
  { id: 'big', name: 'Polegares grandes', when: '12/09/2026', land: [3, 4, 0, 0], port: [2, 4, 0, 0] },
  { id: 'race', name: 'Corrida: gatilhos embaixo', when: '20/09/2026', land: [4, 0, 0, 1], port: null },
  { id: 'tab', name: 'Tablet compacto', when: '01/10/2026', land: [6, 6, 0, 0], port: [5, 5, 1, 0] },
];
