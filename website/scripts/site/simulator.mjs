// Simulador da interface: o protótipo B+C do app (docs/ui-redesign/bc), portado para o site em
// src/tool/proto, com os ajustes, as predefinições, as flags do Turnip, os botões e as cvars
// lidos do código da versão escolhida. Aqui o build gera os dados, copia os scripts, isola o
// CSS do protótipo sob .sim e monta a página.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { html, raw, esc, icon } from '../lib/html.mjs';
import { rel } from '../lib/paths.mjs';
import { SIM_GROUPS, SIM_SCREENS as META, README_TITLES } from '../../content/simulador.mjs';
import { PRINTS } from '../../content/prints.mjs';
import { textOf } from './settings-view.mjs';

export const SIM_SCREENS = SIM_GROUPS.flatMap(g => g[1]);

const CODE_BASE = 'app/src/main/java/xendroid/compose/';
const GROUP_IDS = { IMAGE: 'img', PERFORMANCE: 'perf', AUDIO: 'audio', INPUT: 'input', COMPAT: 'compat', DRIVER: 'drv', SYSTEM: 'sys', DEBUG: 'dbg' };
const GROUP_ICONS = { IMAGE: 'image', PERFORMANCE: 'chart', AUDIO: 'speaker', INPUT: 'gamepad', COMPAT: 'shield', DRIVER: 'chip', SYSTEM: 'cpu', DEBUG: 'flask' };
const LEVEL = { ESSENTIAL: 1, ADVANCED: 2, ALL: 3 };
const BUTTON_IDS = { 'D-Pad Left': 'LEFT', 'D-Pad Up': 'UP', 'D-Pad Right': 'RIGHT', 'D-Pad Down': 'DOWN', A: 'A', B: 'B', X: 'X', Y: 'Y', Back: 'BACK', Start: 'START', 'Left Shoulder': 'LB', 'Right Shoulder': 'RB', 'Left Thumb Press': 'L3', 'Right Thumb Press': 'R3', 'Left Trigger': 'LT', 'Right Trigger': 'RT' };
const JS_FILES = ['base.js', 'data.js', 'core.js', 'ui.js', 'screens/biblioteca.js', 'screens/configuracoes.js', 'screens/jogo.js', 'screens/controles.js', 'screens/perfis.js', 'screens/conteudo.js', 'screens/sistema.js', 'screens/paineis.js'];

const dotKey = k => (k.startsWith('@') ? k : k.replace('|', '.'));

/** Um ajuste do app no formato do protótipo (ui.js: k, g, lvl, ty, def, ap, t, d, o…). */
function simSetting(s) {
  const d = {
    k: dotKey(s.key),
    g: GROUP_IDS[s.group],
    lvl: LEVEL[s.level],
    t: textOf(s.title),
    d: textOf(s.desc),
    src: s.source,
  };
  if (s.title && s.title.lang === 'en') d.tEn = 1;
  if (s.desc && s.desc.lang === 'en') d.dEn = 1;
  if (s.isNew) d.n = 1;
  if (s.newOptions && s.newOptions.length) d.nOpt = s.newOptions[0];
  if (s.dependsOn) d.dep = [dotKey(s.dependsOn.key), String(s.dependsOn.value)];
  if (s.warning) d.w = textOf(s.warning);
  d.ap = s.live ? 'live' : 'boot';
  switch (s.type) {
    case 'bool': d.ty = 'bool'; d.def = String(s.default) === 'true'; break;
    case 'int': d.ty = 'int'; d.def = Number(s.default); d.min = s.min; d.max = s.max; d.step = s.step || 1; if (s.unit) d.unit = s.unit; break;
    case 'list': d.ty = 'list'; d.def = String(s.default ?? ''); d.o = s.options.map(o => [String(o.value), textOf(o.label)]); break;
    case 'text': d.ty = 'text'; d.def = String(s.default ?? ''); d.ph = s.placeholder || ''; break;
    case 'action':
      if (s.customDriver) { d.ty = 'list'; d.drv = 1; d.def = ''; d.o = [['', 'Driver do sistema']]; }
      else { d.ty = 'action'; d.ap = 'act'; d.def = null; }
      break;
    default: throw new Error(`simulador: tipo de ajuste desconhecido ${s.type} (${s.key})`);
  }
  return d;
}

function channelData(ch, label) {
  const st = ch.settings;
  const resX = st.settings.find(s => s.key === 'GPU|draw_resolution_scale_x');
  return {
    label,
    grupos: st.groups.map(g => [GROUP_IDS[g.id], textOf(g.title), GROUP_ICONS[g.id] || 'gear']),
    ajustes: st.settings.slice().sort((a, b) => (Object.keys(GROUP_IDS).indexOf(a.group) - Object.keys(GROUP_IDS).indexOf(b.group)) || (a.order - b.order)).map(simSetting),
    resolucao: { k: '@res', g: 'img', lvl: 1, ty: 'list', def: '1', ap: 'boot', t: 'Escala de resolução', o: resX ? resX.options.map(o => [String(o.value), textOf(o.label)]) : [['1', '1x']], d: 'Largura e altura juntas (os dois ajustes de escala de resolução).' },
    predefinicoes: st.presets.map(p => ({ id: p.id, t: textOf(p.title), d: textOf(p.description), set: Object.fromEntries(Object.entries(p.values).map(([k, v]) => [dotKey(k), v])) })),
    fixados: st.pinnedDefault.map(k => (k === st.resolutionKey ? '@res' : dotKey(k))),
    turnip: st.turnipFlags.map(f => ({ f: f.name, h: textOf(f.help), x: f.exclusive ? 1 : 0 })),
    cvars: ch.cvars,
    // padrões do núcleo que diferem dos do app ("Ajustes em vigor" compara com o núcleo)
    padraoNucleo: Object.fromEntries(ch.consistency.coreDefaults.map(c => [dotKey(c.key), c.core])),
  };
}

/** Mediana e mínimo de uma linha da tabela do README ("27–30 fps", "Cai a 19 fps"). */
function readmeNumbers(row) {
  const fps = row['Taxa de quadros'] || '';
  const nums = (fps.match(/\d+/g) || []).map(Number);
  if (!nums.length) return null;
  const range = nums.length >= 2 ? [Math.min(nums[0], nums[1]), Math.max(nums[0], nums[1])] : null;
  const t = Math.round(range ? (range[0] + range[1]) / 2 : nums[0]);
  const dip = /cai a (\d+)/i.exec(row['Observações'] || '');
  // sem queda publicada, low fica nulo: o simulador não inventa uma medição
  return { t, range, low: dip ? Number(dip[1]) : range ? range[0] : null, lowShare: dip ? 0.08 : 0.05 };
}

/** Isola o CSS do protótipo sob um seletor: :root, html e body viram o próprio seletor. */
export function scopeCss(css, scope) {
  const out = [];
  let i = 0;
  const n = css.length;
  const splitSelectors = sel => {
    const parts = [];
    let depth = 0, start = 0;
    for (let k = 0; k < sel.length; k++) {
      const c = sel[k];
      if (c === '(' || c === '[') depth++;
      else if (c === ')' || c === ']') depth--;
      else if (c === ',' && depth === 0) { parts.push(sel.slice(start, k)); start = k + 1; }
    }
    parts.push(sel.slice(start));
    return parts.map(s => s.trim()).filter(Boolean);
  };
  const prefix = s => {
    if (/^:root\b/.test(s)) return scope + s.slice(5);
    if (/^html\b/.test(s)) return scope + s.slice(4);
    if (/^body\b/.test(s)) return scope + s.slice(4);
    return `${scope} ${s}`;
  };
  const readBlock = () => { // a partir de '{', devolve o conteúdo até a '}' correspondente
    let depth = 0, start = i;
    for (; i < n; i++) {
      if (css[i] === '/' && css[i + 1] === '*') { i = css.indexOf('*/', i + 2) + 1; continue; }
      if (css[i] === '"' || css[i] === "'") { const q = css[i]; i++; while (i < n && css[i] !== q) { if (css[i] === '\\') i++; i++; } continue; }
      if (css[i] === '{') depth++;
      else if (css[i] === '}') { depth--; if (depth === 0) { i++; return css.slice(start + 1, i - 1); } }
    }
    throw new Error('CSS do simulador com chaves desbalanceadas');
  };
  while (i < n) {
    if (/\s/.test(css[i])) { i++; continue; }
    if (css.startsWith('/*', i)) { const end = css.indexOf('*/', i + 2); i = end < 0 ? n : end + 2; continue; }
    const brace = css.indexOf('{', i);
    const semi = css.indexOf(';', i);
    if (css[i] === '@' && semi >= 0 && (brace < 0 || semi < brace)) { out.push(css.slice(i, semi + 1)); i = semi + 1; continue; }
    if (brace < 0) break;
    const head = css.slice(i, brace).trim();
    i = brace;
    const body = readBlock();
    if (/^@(media|supports|container|layer)\b/.test(head)) out.push(`${head}{${scopeCss(body, scope)}}`);
    else if (head.startsWith('@')) out.push(`${head}{${body}}`);
    else out.push(`${splitSelectors(head).map(prefix).join(',')}{${body}}`);
  }
  return out.join('\n');
}

export function writeSimulator(site, { SITE, ROOT, write, copy, error }) {
  const est = site.ch.est;
  const dev = site.ch.dev;
  const same = site.data.sameChannels;
  const { stable } = site.v;

  // metadados das telas: cada tela tem texto, código e prints que existem
  for (const id of SIM_SCREENS) {
    const m = META[id];
    if (!m) { error(`simulador: tela "${id}" sem texto em content/simulador.mjs`); continue; }
    for (const f of m.code) if (!fs.existsSync(path.join(ROOT, CODE_BASE, f))) error(`simulador: ${id}: o arquivo ${CODE_BASE}${f} não existe`);
    for (const p of m.prints) if (!PRINTS[p]) error(`simulador: ${id}: print "${p}" não está em content/prints.mjs`);
  }

  // jogos de exemplo: Title IDs do README e os do data.js, com patches e correções reais
  const dataJs = fs.readFileSync(path.join(SITE, 'src/tool/proto/data.js'), 'utf8');
  const demoIds = [...dataJs.matchAll(/\{ id: '([0-9A-F]{8})'/g)].map(m => m[1]);
  const jogos = {};
  for (const id of demoIds) {
    const p = est.games.patches.find(x => x.titleId === id);
    const q = est.games.quirkTitles.find(x => x.titleId === id);
    if (!p && !q) continue;
    // como a ficha do app: um grupo por arquivo, com o rótulo do nome do arquivo ("Halo 4 (TU10)")
    const arquivos = (p ? p.files : []).map(f => ({
      rotulo: f.file.replace(/^[0-9A-Fa-f]{8} - /, '').replace(/\.patch\.toml$/, ''),
      entradas: f.patches.filter(x => x.name).map(x => ({ n: x.name, d: x.desc || '', on: x.enabled ? 1 : 0 })),
    })).filter(f => f.entradas.length);
    jogos[id] = { nome: p ? [...p.names].sort((a, b) => a.length - b.length)[0] : q.name, arquivos, correcoes: q ? q.entries.length : 0 };
  }
  const medidas = {};
  const perf = dev.games.perf;
  for (const row of (perf ? perf.rows : [])) {
    const id = README_TITLES[row.Jogo];
    if (!id) { error(`simulador: o jogo "${row.Jogo}" do README não tem Title ID em content/simulador.mjs`); continue; }
    if (!demoIds.includes(id)) error(`simulador: ${row.Jogo} (${id}) não está na biblioteca de exemplo (src/tool/proto/data.js)`);
    if (!jogos[id]) error(`simulador: ${row.Jogo} (${id}) não tem patch nem correção no catálogo`);
    const nums = readmeNumbers(row);
    if (nums) medidas[id] = { jogo: row.Jogo, testado: row['Testado em'], fps: row['Taxa de quadros'], obs: row['Observações'], ...nums };
  }

  const botoes = est.settings.buttons.map(b => {
    if (!BUTTON_IDS[b.englishLabel]) error(`simulador: botão "${b.englishLabel}" do GameButtons.kt sem correspondente`);
    return { id: BUTTON_IDS[b.englishLabel] || b.englishLabel, label: b.englishLabel, tecla: b.defaultKey.replace(/^KEYCODE_/, '') };
  });

  const estLabel = stable ? stable.label : 'Versão publicada';
  const canais = { estavel: channelData(est, estLabel) };
  if (!same) canais.desenvolvimento = channelData(dev, `Desenvolvimento (${site.v.dev.short})`);
  for (const [id, c] of Object.entries(canais)) {
    for (const k of ['GPU.framerate_limit', 'GPU.draw_resolution_scale_x', 'GPU.draw_resolution_scale_y', 'Vulkan.vulkan_lib_path', 'Vulkan.turnip_debug', 'HID.show_touch_overlay', 'Display.postprocess_scaling_and_sharpening', 'GPU.anisotropic_override', 'APU.volume', 'Console.user_language', 'Console.user_country']) {
      if (!c.ajustes.some(d => d.k === k)) error(`simulador: [${id}] o ajuste ${k}, usado pelas telas, não existe no app`);
    }
  }

  const payload = {
    padrao: 'estavel',
    canais,
    mesmoApp: same,
    versoes: { estavel: stable ? { label: stable.label, commit: stable.short, data: stable.date } : null, desenvolvimento: { label: 'Desenvolvimento', commit: site.v.dev.short, data: site.v.dev.date } },
    jogos,
    medidas,
    botoes,
    fonteDrivers: est.app.driverSource,
    maxFontes: est.app.driverSourcesMax,
    app: { pacote: est.app.package, pastaDados: est.app.dataRoot, pastaConfig: est.app.gameConfigDir, configGlobal: est.app.globalConfig },
    telas: Object.fromEntries(SIM_SCREENS.map(id => [id, { ...META[id], prints: (META[id] || { prints: [] }).prints.map(p => ({ id: p, alt: PRINTS[p] ? PRINTS[p].alt : '', ...(site.printFiles[p] || {}) })) }])),
    grupos: SIM_GROUPS,
    repo: site.config.repoUrl,
    ref: stable ? stable.commit : 'main',
  };
  const dataText = `window.XDR_SIM = ${JSON.stringify(payload).replace(/</g, '\\u003c')};\n`;
  write('simulador/js/dados.js', dataText);

  // scripts do protótipo e o módulo que liga o formato do TOML
  let jsAll = dataText;
  for (const f of JS_FILES) {
    const text = fs.readFileSync(path.join(SITE, 'src/tool/proto', f), 'utf8');
    write(`simulador/js/${f}`, text);
    jsAll += text;
  }
  for (const f of ['config-shape.js']) copy(path.join(SITE, 'src/tool/lib', f), `simulador/js/${f}`);
  copy(path.join(SITE, 'src/tool/sim-main.js'), 'simulador/js/sim-main.js');
  jsAll += fs.readFileSync(path.join(SITE, 'src/tool/sim-main.js'), 'utf8');

  // CSS: o do protótipo isolado sob .sim, mais a moldura do site
  const css = scopeCss(fs.readFileSync(path.join(SITE, 'src/tool/proto/style.css'), 'utf8'), '.sim') + '\n' + fs.readFileSync(path.join(SITE, 'src/css/tool.css'), 'utf8');
  write('assets/css/simulador.css', css);

  const hash = crypto.createHash('sha1').update(jsAll + css).digest('hex').slice(0, 10);
  return { hash, files: JS_FILES };
}

export function simulatorPage(site, assets) {
  const P = 'simulador/';
  const r = to => rel(P, to);
  const v = `?v=${assets.hash}`;
  const { stable } = site.v;
  const scripts = raw([
    `<script src="js/dados.js${v}"></script>`,
    ...assets.files.map(f => `<script src="js/${f}${v}"></script>`),
    `<script type="module" src="js/sim-main.js${v}"></script>`,
  ].join('\n'));
  const page = {
    path: P,
    section: 'simulador',
    title: 'Simulador da interface',
    description: `Navegue pelas telas do Xendroid+ no navegador, no modo toque ou no modo controle, com os ajustes reais da ${stable ? stable.label.toLowerCase() : 'versão publicada'} e o arquivo de configuração por jogo no formato do núcleo.`,
    styles: ['css/simulador.css'],
    scripts,
    bodyClass: 'is-sim',
  };
  const content = html`<div class="sim" id="sim" data-routes="${SIM_SCREENS.join(' ')}">
  <section class="sim-intro wrap">
    <div>
      <span class="eyebrow">Simulador</span>
      <h1>A interface do Xendroid+, no navegador</h1>
      <p class="lead">As telas do app, nos modos toque e controle, com teclado ou um controle de verdade. Os ${site.stats.settings} ajustes, as predefinições e as flags do Turnip são os da ${stable ? stable.label.toLowerCase() : 'versão publicada'}; o botão <b>TOML</b> da ficha gera o arquivo de configuração de um jogo no formato que o núcleo lê.</p>
    </div>
    <p class="sim-note" role="note">${icon('info', 18)}<span><b>Dados de exemplo.</b> Os jogos são os medidos no README, mas formatos, sessões, saves, perfis, drivers e números aqui são ilustrativos, e falhas e comparações usam um jogo fictício. Nada roda um jogo nem muda o seu aparelho.</span></p>
  </section>
  <noscript><div class="wrap"><p class="sim-note">O simulador precisa de JavaScript. Os prints reais de cada tela estão na <a href="${r('docs/interface/')}">documentação da interface</a>.</p></div></noscript>
  <header class="ch-bar" id="ch-bar" aria-label="Controles do simulador">
    <label class="ch-field"><span>Tela</span><select class="ch-sel" id="ch-screen"></select></label>
    <span class="ch-vars" id="ch-vars"></span>
    <div class="ch-seg" role="group" aria-label="Modo da interface">
      <button type="button" data-cact="mode" data-v="auto">Automático</button>
      <button type="button" data-cact="mode" data-v="b">Toque</button>
      <button type="button" data-cact="mode" data-v="c">Controle</button>
    </div>
    <div class="ch-seg" role="group" aria-label="Aparelho">
      <button type="button" data-cact="orient" data-v="land">Deitado</button>
      <button type="button" data-cact="orient" data-v="port">Em pé</button>
      <button type="button" data-cact="full">Tela cheia</button>
    </div>
    <button type="button" class="ch-tg" data-cact="notes" aria-pressed="false"><i aria-hidden="true"></i>Anotações</button>
    <span class="ch-ver" id="ch-ver"></span>
    <span class="ch-pad" id="ch-pad" aria-live="polite"></span>
  </header>
  <section class="stage" id="stage" aria-label="Aparelho simulado">
    <div class="device-wrap" id="dwrap">
      <div class="device" id="device">
        <div class="screen" id="screen" tabindex="0" role="application" aria-roledescription="tela do aparelho" aria-label="Tela do aparelho simulado. Use as setas, Enter, Esc e as teclas indicadas abaixo; Tab sai.">
          <div id="app" class="app"></div>
          <div id="pins"></div>
        </div>
      </div>
    </div>
    <div class="full-exit" id="full-exit" role="group" aria-label="Simulador em tela cheia">
      <button type="button" data-cact="mode" data-v="b" aria-label="Modo toque">B</button>
      <button type="button" data-cact="mode" data-v="c" aria-label="Modo controle">C</button>
      <button type="button" data-cact="full" aria-label="Sair da tela cheia">${icon('x', 16)}</button>
    </div>
  </section>
  <section class="ch-info wrap" id="ch-info">
    <article class="ch-card" id="ch-dir" aria-live="polite"></article>
    <aside class="ch-card">
      <h2>Anotações desta tela</h2>
      <div class="legend"><span class="k-app"><i></i>Como o app faz</span><span class="k-dif"><i></i>Diferente no app</span><span class="k-sim"><i></i>Só na simulação</span></div>
      <div id="ch-notes"></div>
    </aside>
    <article class="ch-card ch-span"><h2>Todas as telas</h2><div class="lotes" id="ch-lotes"></div></article>
    <article class="ch-card ch-span" id="ch-help">
      <h2>Como usar</h2>
      <div class="ch-keys"><span><kbd>←</kbd><kbd>→</kbd><kbd>↑</kbd><kbd>↓</kbd> mover</span><span><kbd>Enter</kbd> abrir ou mudar</span><span><kbd>Esc</kbd> voltar</span><span><kbd>Q</kbd><kbd>E</kbd> abas e seções (LB/RB)</span><span><kbd>F</kbd> favoritar (Y)</span><span><kbd>I</kbd> ficha (X)</span><span><kbd>M</kbd> menu (Start)</span><span><kbd>/</kbd> buscar jogos</span></div>
      <p>Clique na tela do aparelho (ou chegue nela com <kbd>Tab</kbd>) para usar o teclado; <kbd>Tab</kbd> sai dela. Um controle conectado ao computador funciona pela Gamepad API e, em Automático, troca para o modo controle, como no app.</p>
      <p>Mudanças ficam só nesta página: recarregar volta aos exemplos. O endereço guarda a tela aberta, para dar para mandar um link direto (por exemplo, <a href="#drivers">#drivers</a>).</p>
    </article>
  </section>
</div>`;
  return { page, content };
}
