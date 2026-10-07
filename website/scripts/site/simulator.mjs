// Simulador da interface: o protótipo B+C do app (docs/ui-redesign/bc), portado para o site em
// src/tool/proto, com os ajustes, as predefinições, as flags do Turnip, os botões e as cvars
// lidos do código da versão escolhida. Aqui o build gera os dados, copia os scripts, isola o
// CSS do protótipo sob .sim e monta a página.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { html, raw, esc, icon } from '../lib/html.mjs';
import { rel } from '../lib/paths.mjs';
import { readStringsDir } from '../lib/android-strings.mjs';
import { translateJs } from '../lib/js-i18n.mjs';
import { SIM_GROUPS, SIM_SCREENS as META, README_TITLES } from '../../content/simulador.mjs';
import { SIM_GROUP_TITLES_EN, SIM_SCREENS_EN } from '../../content/simulador.en.mjs';
import { PRINTS } from '../../content/prints.mjs';
import { PRINT_ALT_EN } from '../../content/prints.en.mjs';
import { textOf } from './settings-view.mjs';

export const SIM_SCREENS = SIM_GROUPS.flatMap(g => g[1]);

const CODE_BASE = 'app/src/main/java/xendroid/compose/';
const GROUP_IDS = { IMAGE: 'img', PERFORMANCE: 'perf', AUDIO: 'audio', INPUT: 'input', COMPAT: 'compat', DRIVER: 'drv', SYSTEM: 'sys', DEBUG: 'dbg' };
const GROUP_ICONS = { IMAGE: 'image', PERFORMANCE: 'chart', AUDIO: 'speaker', INPUT: 'gamepad', COMPAT: 'shield', DRIVER: 'chip', SYSTEM: 'cpu', DEBUG: 'flask' };
const LEVEL = { ESSENTIAL: 1, ADVANCED: 2, ALL: 3 };
const BUTTON_IDS = { 'D-Pad Left': 'LEFT', 'D-Pad Up': 'UP', 'D-Pad Right': 'RIGHT', 'D-Pad Down': 'DOWN', A: 'A', B: 'B', X: 'X', Y: 'Y', Back: 'BACK', Start: 'START', 'Left Shoulder': 'LB', 'Right Shoulder': 'RB', 'Left Thumb Press': 'L3', 'Right Thumb Press': 'R3', 'Left Trigger': 'LT', 'Right Trigger': 'RT' };
const JS_FILES = ['base.js', 'data.js', 'core.js', 'ui.js', 'screens/biblioteca.js', 'screens/configuracoes.js', 'screens/jogo.js', 'screens/controles.js', 'screens/perfis.js', 'screens/conteudo.js', 'screens/sistema.js', 'screens/paineis.js'];

const dotKey = k => (k.startsWith('@') ? k : k.replace('|', '.'));

/** Um ajuste do app no formato do protótipo (ui.js: k, g, lvl, ty, def, ap, t, d, o…), no idioma `lang`. */
function simSetting(s, texts, lang) {
  const d = {
    k: dotKey(s.key),
    g: GROUP_IDS[s.group],
    lvl: LEVEL[s.level],
    t: textOf(s.title, lang),
    d: textOf(s.desc, lang),
    src: s.source,
  };
  // no português, o que o app ainda mostra em inglês fica marcado (lang="en" na tela)
  if (lang === 'pt' && s.title && s.title.lang === 'en') d.tEn = 1;
  if (lang === 'pt' && s.desc && s.desc.lang === 'en') d.dEn = 1;
  if (s.isNew) d.n = 1;
  if (s.newOptions && s.newOptions.length) d.nOpt = s.newOptions[0];
  if (s.dependsOn) d.dep = [dotKey(s.dependsOn.key), String(s.dependsOn.value)];
  if (s.warning) d.w = textOf(s.warning, lang);
  d.ap = s.live ? 'live' : 'boot';
  switch (s.type) {
    case 'bool': d.ty = 'bool'; d.def = String(s.default) === 'true'; break;
    case 'int': d.ty = 'int'; d.def = Number(s.default); d.min = s.min; d.max = s.max; d.step = s.step || 1; if (s.unit) d.unit = s.unit; break;
    case 'list': d.ty = 'list'; d.def = String(s.default ?? ''); d.o = s.options.map(o => [String(o.value), textOf(o.label, lang)]); break;
    case 'text': d.ty = 'text'; d.def = String(s.default ?? ''); d.ph = s.placeholder || ''; break;
    case 'action':
      if (s.customDriver) { d.ty = 'list'; d.drv = 1; d.def = ''; d.o = [['', textOf(texts.driverSystem, lang)]]; }
      else { d.ty = 'action'; d.ap = 'act'; d.def = null; }
      break;
    default: throw new Error(`simulador: tipo de ajuste desconhecido ${s.type} (${s.key})`);
  }
  return d;
}

function channelData(ch, label, L) {
  const st = ch.settings;
  const lang = L.id;
  const resX = st.settings.find(s => s.key === 'GPU|draw_resolution_scale_x');
  return {
    label,
    grupos: st.groups.map(g => [GROUP_IDS[g.id], textOf(g.title, lang), GROUP_ICONS[g.id] || 'gear']),
    ajustes: st.settings.slice().sort((a, b) => (Object.keys(GROUP_IDS).indexOf(a.group) - Object.keys(GROUP_IDS).indexOf(b.group)) || (a.order - b.order)).map(s => simSetting(s, st.texts, lang)),
    resolucao: { k: '@res', g: 'img', lvl: 1, ty: 'list', def: '1', ap: 'boot', t: L.tx('Resolution scale', 'Escala de resolução'), o: resX ? resX.options.map(o => [String(o.value), textOf(o.label, lang)]) : [['1', '1x']], d: L.tx('Width and height together (the two resolution scale settings).', 'Largura e altura juntas (os dois ajustes de escala de resolução).') },
    predefinicoes: st.presets.map(p => ({ id: p.id, t: textOf(p.title, lang), d: textOf(p.description, lang), set: Object.fromEntries(Object.entries(p.values).map(([k, v]) => [dotKey(k), v])) })),
    fixados: st.pinnedDefault.map(k => (k === st.resolutionKey ? '@res' : dotKey(k))),
    turnip: st.turnipFlags.map(f => ({ f: f.name, h: textOf(f.help, lang), x: f.exclusive ? 1 : 0 })),
    cvars: ch.cvars,
    // padrões do núcleo que diferem dos do app ("Ajustes em vigor" compara com o núcleo)
    padraoNucleo: Object.fromEntries(ch.consistency.coreDefaults.map(c => [dotKey(c.key), c.core])),
  };
}

/** Mediana e mínimo de uma linha da tabela do README ("27–30 fps", "Cai a 19 fps" / "Dips to 19 fps").
 *  As colunas são lidas pela posição: jogo, onde foi testado, taxa de quadros, observações. */
function readmeNumbers(fps, notes) {
  const nums = (fps.match(/\d+/g) || []).map(Number);
  if (!nums.length) return null;
  const range = nums.length >= 2 ? [Math.min(nums[0], nums[1]), Math.max(nums[0], nums[1])] : null;
  const t = Math.round(range ? (range[0] + range[1]) / 2 : nums[0]);
  const dip = /(?:cai a|dips to) (\d+)/i.exec(notes || '');
  // sem queda publicada, low fica nulo: o simulador não inventa uma medição
  return { t, range, low: dip ? Number(dip[1]) : range ? range[0] : null, lowShare: dip ? 0.08 : 0.05 };
}

/** As linhas do cartão de atualização do app (updater.kt, updateNotes e cleanChangelog), em pt-BR. */
export function updateNotes(text, language = 'pt-BR') {
  const summaries = {};
  for (const m of String(text || '').matchAll(/<!--\s*update-summary:([A-Za-z-]+)\s*\n([\s\S]*?)-->/g)) summaries[m[1]] = m[2];
  const base = language.split('-')[0].toLowerCase();
  const summary = summaries[language]
    ?? (Object.entries(summaries).find(([k]) => k.split('-')[0].toLowerCase() === base) || [])[1]
    ?? summaries.en
    ?? Object.values(summaries)[0];
  return (summary ?? cleanChangelog(text || '')).split('\n')
    .map(l => l.trim().replace(/^•/, '').trim().replace(/^\* /, '').replace(/^- /, '').trim())
    .filter(Boolean);
}

function cleanChangelog(text) {
  return text
    .replace(/\*\*Full Changelog\*\*:[\s\S]*/, '')
    .replace(/<!--[\s\S]*?-->|<details>[\s\S]*?<\/details>/g, '')
    .replace(/^\* (.+) by @[\w-]+ in https?:\/\/\S+$/gm, '* $1')
    .replace(/^\* @[\w-]+ made their first contribution.*$/gm, '')
    .replace(/^\s*(#|<|\||!\[).*$/gm, '')
    .replace(/!\[[^\]]*]\([^)]*\)/g, '')
    .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
    .replace(/https?:\/\/\S+/g, '')
    .replace(/^\s*>\s?/gm, '')
    .replaceAll('**', '').replaceAll('`', '')
    .replace(/\* /g, '• ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/** O que o atualizador simulado mostra: a release estável oferecida a quem tem a anterior. */
function updateData(releases, stable, language = 'pt-BR') {
  const numbered = releases.filter(r => !r.prerelease && !r.draft && r.build != null && r.apk);
  const offer = stable && numbered.find(r => r.tag === stable.tag);
  if (!offer) return null;
  const before = numbered.find(r => r.build < offer.build) || null;
  const pick = r => ({ build: r.build, commit: r.tagSha || r.commit.slice(0, 8), tag: r.tag });
  return {
    instalada: before ? pick(before) : pick(offer),
    oferecida: {
      ...pick(offer),
      titulo: offer.name || offer.tag,
      url: offer.url,
      apk: { nome: offer.apk.name, bytes: offer.apk.size, sha256: offer.apk.sha256 || null },
      notas: updateNotes(offer.body, language).slice(0, 12),
    },
  };
}

/** A lista de licenças que o app abre em Sobre (assets/licenses.html, em inglês). */
function licensesFromHtml(htmlText) {
  const strip = s => s.replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/\s+/g, ' ').trim();
  const lists = [...htmlText.matchAll(/<ul>([\s\S]*?)<\/ul>/g)].map(m => [...m[1].matchAll(/<li>([\s\S]*?)<\/li>/g)].map(x => x[1]));
  const projetos = (lists[0] || []).map(li => {
    const a = /<a href="([^"]+)">([^<]+)<\/a>/.exec(li);
    return a ? { n: strip(a[2]), url: a[1] } : { n: strip(li), url: null };
  });
  const motores = (lists[1] || []).map(strip);
  const notas = [...htmlText.matchAll(/<p>([\s\S]*?)<\/p>/g)].map(m => strip(m[1])).slice(1);
  return { projetos, motores, notas };
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


/**
 * Dicionário do simulador em inglês: o escrito à mão (content/simulador.en.json) e, para o que
 * ele não traz, os pares do próprio app (values-pt-rBR → values) com exatamente o mesmo texto.
 */
function englishDictionary(SITE, ROOT) {
  const file = path.join(SITE, 'content/simulador.en.json');
  const manual = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {};
  const res = path.join(ROOT, 'app/src/main/res');
  const en = readStringsDir(path.join(res, 'values'));
  const pt = readStringsDir(path.join(res, 'values-pt-rBR'));
  const auto = new Map();
  const conflict = new Set();
  const add = (ptText, enText) => {
    if (!ptText || !enText) return;
    const k = ptText.replace(/\s+/g, ' ').trim();
    if (auto.has(k) && auto.get(k) !== enText) conflict.add(k);
    else auto.set(k, enText);
  };
  for (const [name, v] of pt.strings) if (en.strings.has(name)) add(v.value, en.strings.get(name).value);
  for (const [name, v] of pt.plurals) {
    const e = en.plurals.get(name);
    if (e) for (const [q, text] of Object.entries(v.items)) add(text, e.items[q] || e.items.other);
  }
  for (const k of conflict) auto.delete(k);
  return { dict: { ...Object.fromEntries(auto), ...manual }, manual };
}

export function writeSimulator(site, L, { SITE, ROOT, write, copy, error, warn }) {
  const { tx } = L;
  const lang = L.id;
  const est = site.ch.est;
  const dev = site.ch.dev;
  const same = site.data.sameChannels;
  const { stable } = site.v;
  const dir = L.simPath;

  // metadados das telas: cada tela tem texto, código e prints que existem (nos dois idiomas)
  for (const id of SIM_SCREENS) {
    const m = META[id];
    if (!m) { error(`simulador: tela "${id}" sem texto em content/simulador.mjs`); continue; }
    if (lang === 'en' && !SIM_SCREENS_EN[id]) error(`simulador: tela "${id}" sem texto em inglês em content/simulador.en.mjs`);
    for (const f of m.code) if (!fs.existsSync(path.join(ROOT, CODE_BASE, f))) error(`simulador: ${id}: o arquivo ${CODE_BASE}${f} não existe`);
    for (const p of m.prints) {
      if (!PRINTS[p]) error(`simulador: ${id}: print "${p}" não está em content/prints.mjs`);
      if (lang === 'en' && !PRINT_ALT_EN[p]) error(`simulador: ${id}: print "${p}" sem texto alternativo em content/prints.en.mjs`);
    }
  }
  if (lang === 'en' && SIM_GROUP_TITLES_EN.length !== SIM_GROUPS.length) error('simulador: content/simulador.en.mjs não tem um título por grupo de telas');

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
  // medições do README do idioma (as colunas: jogo, onde foi testado, taxa de quadros, observações)
  const medidas = {};
  const perf = site.perf(lang);
  for (const row of (perf ? perf.rows : [])) {
    const [cGame, cTested, cFps, cNotes] = perf.header;
    const id = README_TITLES[row[cGame]];
    if (!id) { error(`simulador: o jogo "${row[cGame]}" do ${perf.source} não tem Title ID em content/simulador.mjs`); continue; }
    if (!demoIds.includes(id)) error(`simulador: ${row[cGame]} (${id}) não está na biblioteca de exemplo (src/tool/proto/data.js)`);
    if (!jogos[id]) error(`simulador: ${row[cGame]} (${id}) não tem patch nem correção no catálogo`);
    const nums = readmeNumbers(row[cFps] || '', row[cNotes] || '');
    if (nums) medidas[id] = { jogo: row[cGame], testado: row[cTested], fps: row[cFps], obs: row[cNotes], ...nums };
  }

  const botoes = est.settings.buttons.map(b => {
    if (!BUTTON_IDS[b.englishLabel]) error(`simulador: botão "${b.englishLabel}" do GameButtons.kt sem correspondente`);
    return { id: BUTTON_IDS[b.englishLabel] || b.englishLabel, label: b.englishLabel, tecla: b.defaultKey.replace(/^KEYCODE_/, '') };
  });

  const licPath = path.join(ROOT, 'emulator-core/src/main/assets/licenses.html');
  const licencas = fs.existsSync(licPath) ? licensesFromHtml(fs.readFileSync(licPath, 'utf8')) : null;
  if (!licencas || !licencas.projetos.length) error('simulador: a lista de licenças do app (emulator-core/src/main/assets/licenses.html) não foi lida');
  const atualizacao = updateData(site.data.releases.releases, stable, lang === 'en' ? 'en' : 'pt-BR');
  if (stable && !atualizacao) error(`simulador: a release estável ${stable.tag} não tem número de build ou APK para o atualizador`);

  const estLabel = stable ? stable.label : tx('Published version', 'Versão publicada');
  const canais = { estavel: channelData(est, estLabel, L) };
  if (!same) canais.desenvolvimento = channelData(dev, `${tx('Development', 'Desenvolvimento')} (${site.v.dev.short})`, L);
  for (const [id, c] of Object.entries(canais)) {
    for (const k of ['GPU.framerate_limit', 'GPU.draw_resolution_scale_x', 'GPU.draw_resolution_scale_y', 'Vulkan.vulkan_lib_path', 'Vulkan.turnip_debug', 'HID.show_touch_overlay', 'Display.postprocess_scaling_and_sharpening', 'GPU.anisotropic_override', 'APU.volume', 'Console.user_language', 'Console.user_country']) {
      if (!c.ajustes.some(d => d.k === k)) error(`simulador: [${id}] o ajuste ${k}, usado pelas telas, não existe no app`);
    }
  }

  const screenText = id => (lang === 'en' ? { ...META[id], ...(SIM_SCREENS_EN[id] || {}) } : META[id]) || { prints: [] };
  const payload = {
    idioma: L.html,
    raiz: rel(dir, ''),
    padrao: 'estavel',
    canais,
    mesmoApp: same,
    versoes: { estavel: stable ? { label: stable.label, commit: stable.short, data: stable.date } : null, desenvolvimento: { label: tx('Development', 'Desenvolvimento'), commit: site.v.dev.short, data: site.v.dev.date } },
    jogos,
    medidas,
    botoes,
    fonteDrivers: est.app.driverSource,
    maxFontes: est.app.driverSourcesMax,
    app: { pacote: est.app.package, pastaDados: est.app.dataRoot, pastaConfig: est.app.gameConfigDir, configGlobal: est.app.globalConfig, repoAtualizacoes: est.app.updateRepo, minSdk: est.app.minSdk },
    atualizacao,
    licencas,
    telas: Object.fromEntries(SIM_SCREENS.map(id => {
      const m = screenText(id);
      return [id, { ...m, prints: (m.prints || []).map(p => ({ id: p, alt: lang === 'en' ? PRINT_ALT_EN[p] || '' : PRINTS[p] ? PRINTS[p].alt : '', ...(site.printFiles[p] || {}) })) }];
    })),
    grupos: SIM_GROUPS.map((g, i) => [lang === 'en' ? SIM_GROUP_TITLES_EN[i] || g[0] : g[0], g[1]]),
    repo: site.config.repoUrl,
    ref: stable ? stable.commit : 'main',
  };
  const dataText = `window.XDR_SIM = ${JSON.stringify(payload).replace(/</g, '\\u003c')};\n`;
  write(`${dir}js/dados.js`, dataText);

  // scripts do protótipo e o módulo que liga o formato do TOML; em inglês, traduzidos pelo dicionário
  const english = lang === 'en' ? englishDictionary(SITE, ROOT) : null;
  const used = new Set();
  const missing = new Map();
  /** Grava js/<nome> a partir de src/tool/<fonte>; em inglês, com os textos traduzidos. */
  const emit = (name, source) => {
    const text = fs.readFileSync(path.join(SITE, 'src/tool', source), 'utf8');
    let out = text;
    if (english) {
      const r = translateJs(text, english.dict, `src/tool/${source}`);
      out = r.code;
      for (const k of r.used) used.add(k);
      for (const [k, where] of r.missing) if (!missing.has(k)) missing.set(k, where);
    }
    write(`${dir}js/${name}`, out);
    return out;
  };
  let jsAll = dataText;
  for (const f of JS_FILES) jsAll += emit(f, `proto/${f}`);
  jsAll += emit('config-shape.js', 'lib/config-shape.js');
  jsAll += emit('sim-main.js', 'sim-main.js');
  if (english) {
    if (missing.size) {
      const list = [...missing].slice(0, 12).map(([k, w]) => `"${k.length > 70 ? k.slice(0, 70) + '…' : k}" (${w})`).join('; ');
      error(`simulador em inglês: ${missing.size} texto(s) sem tradução em content/simulador.en.json: ${list}${missing.size > 12 ? '; …' : ''}`);
    }
    const unused = Object.keys(english.manual).filter(k => !used.has(k));
    if (unused.length) warn(`content/simulador.en.json: ${unused.length} tradução(ões) que o código não usa mais (ex.: "${unused[0].slice(0, 60)}")`);
  }

  // CSS: o do protótipo isolado sob .sim, mais a moldura do site (o mesmo nos dois idiomas)
  const css = scopeCss(fs.readFileSync(path.join(SITE, 'src/tool/proto/style.css'), 'utf8'), '.sim') + '\n' + fs.readFileSync(path.join(SITE, 'src/css/tool.css'), 'utf8');
  write('assets/css/simulador.css', css);

  const hash = crypto.createHash('sha1').update(jsAll + css).digest('hex').slice(0, 10);
  return { hash, files: JS_FILES };
}

export function simulatorPage(site, assets, L) {
  const { tx } = L;
  const P = L.simPath;
  const r = to => rel(P, to);
  const v = `?v=${assets.hash}`;
  const { stable } = site.v;
  const name = stable ? stable.label.toLowerCase() : null;
  const scripts = raw([
    `<script src="js/dados.js${v}"></script>`,
    ...assets.files.map(f => `<script src="js/${f}${v}"></script>`),
    `<script type="module" src="js/sim-main.js${v}"></script>`,
  ].join('\n'));
  const page = {
    path: P,
    lang: L.id,
    alternates: site.alternatesOf('sim'),
    section: 'simulador',
    title: tx('Interface simulator', 'Simulador da interface'),
    description: tx(
      `Browse the Xendroid+ screens in the browser, in touch or controller mode, with the real settings of ${name || 'the published version'} and the per-game config file in the core’s format.`,
      `Navegue pelas telas do Xendroid+ no navegador, no modo toque ou no modo controle, com os ajustes reais da ${name || 'versão publicada'} e o arquivo de configuração por jogo no formato do núcleo.`),
    styles: ['css/simulador.css'],
    scripts,
    bodyClass: 'is-sim',
  };
  const content = html`<div class="sim" id="sim" data-routes="${SIM_SCREENS.join(' ')}">
  <section class="sim-intro wrap">
    <div>
      <span class="eyebrow">${tx('Simulator', 'Simulador')}</span>
      <h1>${tx('The Xendroid+ interface, in the browser', 'A interface do Xendroid+, no navegador')}</h1>
      <p class="lead">${tx(
        html`The app’s screens, in touch and controller modes, with a keyboard or a real controller. The ${site.stats.settings} settings, the presets and the Turnip flags are those of ${name || 'the published version'}; the <b>TOML</b> button on a game’s details generates that game’s config file in the format the core reads.`,
        html`As telas do app, nos modos toque e controle, com teclado ou um controle de verdade. Os ${site.stats.settings} ajustes, as predefinições e as flags do Turnip são os da ${name || 'versão publicada'}; o botão <b>TOML</b> da ficha gera o arquivo de configuração de um jogo no formato que o núcleo lê.`)}</p>
    </div>
    <p class="sim-note" role="note">${icon('info', 18)}<span>${tx(
      html`<b>Sample data.</b> The games are the ones measured in the README, but formats, sessions, saves, profiles, drivers and numbers here are illustrative, and failures and comparisons use a fictional game. Nothing runs a game or changes your device.`,
      html`<b>Dados de exemplo.</b> Os jogos são os medidos no README, mas formatos, sessões, saves, perfis, drivers e números aqui são ilustrativos, e falhas e comparações usam um jogo fictício. Nada roda um jogo nem muda o seu aparelho.`)}</span></p>
  </section>
  <noscript><div class="wrap"><p class="sim-note">${tx(html`The simulator needs JavaScript. Real screenshots of every screen are in the <a href="${r(site.docPath(L.id, 'interface'))}">interface documentation</a>.`, html`O simulador precisa de JavaScript. Os prints reais de cada tela estão na <a href="${r(site.docPath(L.id, 'interface'))}">documentação da interface</a>.`)}</p></div></noscript>
  <header class="ch-bar" id="ch-bar" aria-label="${tx('Simulator controls', 'Controles do simulador')}">
    <label class="ch-field"><span>${tx('Screen', 'Tela')}</span><select class="ch-sel" id="ch-screen"></select></label>
    <span class="ch-vars" id="ch-vars"></span>
    <div class="ch-seg" role="group" aria-label="${tx('Interface mode', 'Modo da interface')}">
      <button type="button" data-cact="mode" data-v="auto">${tx('Automatic', 'Automático')}</button>
      <button type="button" data-cact="mode" data-v="b">${tx('Touch', 'Toque')}</button>
      <button type="button" data-cact="mode" data-v="c">${tx('Controller', 'Controle')}</button>
    </div>
    <div class="ch-seg" role="group" aria-label="${tx('Device', 'Aparelho')}">
      <button type="button" data-cact="orient" data-v="land">${tx('Landscape', 'Deitado')}</button>
      <button type="button" data-cact="orient" data-v="port">${tx('Portrait', 'Em pé')}</button>
      <button type="button" data-cact="full">${tx('Full screen', 'Tela cheia')}</button>
    </div>
    <button type="button" class="ch-tg" data-cact="notes" aria-pressed="false"><i aria-hidden="true"></i>${tx('Annotations', 'Anotações')}</button>
    <span class="ch-ver" id="ch-ver"></span>
    <span class="ch-pad" id="ch-pad" aria-live="polite"></span>
  </header>
  <section class="stage" id="stage" aria-label="${tx('Simulated device', 'Aparelho simulado')}">
    <div class="device-wrap" id="dwrap">
      <div class="device" id="device">
        <div class="screen" id="screen" tabindex="0" role="application" aria-roledescription="${tx('device screen', 'tela do aparelho')}" aria-label="${tx('Screen of the simulated device. Use the arrow keys, Enter, Esc and the keys listed below; Tab leaves it.', 'Tela do aparelho simulado. Use as setas, Enter, Esc e as teclas indicadas abaixo; Tab sai.')}">
          <div id="app" class="app"></div>
          <div id="pins"></div>
        </div>
      </div>
    </div>
    <div class="full-exit" id="full-exit" role="group" aria-label="${tx('Simulator in full screen', 'Simulador em tela cheia')}">
      <button type="button" data-cact="mode" data-v="b" aria-label="${tx('Touch mode', 'Modo toque')}">B</button>
      <button type="button" data-cact="mode" data-v="c" aria-label="${tx('Controller mode', 'Modo controle')}">C</button>
      <button type="button" data-cact="full" aria-label="${tx('Leave full screen', 'Sair da tela cheia')}">${icon('x', 16)}</button>
    </div>
  </section>
  <section class="ch-info wrap" id="ch-info">
    <article class="ch-card" id="ch-dir" aria-live="polite"></article>
    <aside class="ch-card">
      <h2>${tx('Annotations for this screen', 'Anotações desta tela')}</h2>
      <div class="legend"><span class="k-app"><i></i>${tx('How the app does it', 'Como o app faz')}</span><span class="k-dif"><i></i>${tx('Different in the app', 'Diferente no app')}</span><span class="k-sim"><i></i>${tx('Simulation only', 'Só na simulação')}</span></div>
      <div id="ch-notes"></div>
    </aside>
    <article class="ch-card ch-span"><h2>${tx('All screens', 'Todas as telas')}</h2><div class="lotes" id="ch-lotes"></div></article>
    <article class="ch-card ch-span" id="ch-help">
      <h2>${tx('How to use it', 'Como usar')}</h2>
      <div class="ch-keys">${tx(
        html`<span><kbd>←</kbd><kbd>→</kbd><kbd>↑</kbd><kbd>↓</kbd> move</span><span><kbd>Enter</kbd> open or change</span><span><kbd>Esc</kbd> back</span><span><kbd>Q</kbd><kbd>E</kbd> tabs and sections (LB/RB)</span><span><kbd>F</kbd> favorite (Y)</span><span><kbd>I</kbd> details (X)</span><span><kbd>M</kbd> menu (Start)</span><span><kbd>/</kbd> search games</span>`,
        html`<span><kbd>←</kbd><kbd>→</kbd><kbd>↑</kbd><kbd>↓</kbd> mover</span><span><kbd>Enter</kbd> abrir ou mudar</span><span><kbd>Esc</kbd> voltar</span><span><kbd>Q</kbd><kbd>E</kbd> abas e seções (LB/RB)</span><span><kbd>F</kbd> favoritar (Y)</span><span><kbd>I</kbd> ficha (X)</span><span><kbd>M</kbd> menu (Start)</span><span><kbd>/</kbd> buscar jogos</span>`)}</div>
      <p>${tx(
        html`Click the device screen (or reach it with <kbd>Tab</kbd>) to use the keyboard; <kbd>Tab</kbd> leaves it. A controller connected to the computer works through the Gamepad API and, in Automatic, switches to controller mode, as in the app.`,
        html`Clique na tela do aparelho (ou chegue nela com <kbd>Tab</kbd>) para usar o teclado; <kbd>Tab</kbd> sai dela. Um controle conectado ao computador funciona pela Gamepad API e, em Automático, troca para o modo controle, como no app.`)}</p>
      <p>${tx(
        html`Changes stay on this page only: reloading goes back to the examples. The address keeps the open screen, so you can send a direct link (for example, <a href="#drivers">#drivers</a>).`,
        html`Mudanças ficam só nesta página: recarregar volta aos exemplos. O endereço guarda a tela aberta, para dar para mandar um link direto (por exemplo, <a href="#drivers">#drivers</a>).`)}</p>
      <p>${tx('What is real data and what is an example, the annotations and the per-game config file:', 'O que é dado real e o que é exemplo, as anotações e o arquivo de configuração por jogo:')} <a href="${r(site.docPath(L.id, 'simulador'))}">${tx('Using the simulator', 'Usar o simulador')}</a>.</p>
    </article>
  </section>
</div>`;
  return { page, content };
}
