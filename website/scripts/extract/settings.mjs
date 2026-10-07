// Extrai os ajustes do app de uma árvore do repositório (uma versão): esquema, catálogo do
// redesenho, textos em pt-BR, rótulos de opções, unidades, passos, padrões do modelo de
// configuração, predefinições, ajustes fixados, flags do Turnip e botões mapeáveis.
import fs from 'node:fs';
import path from 'node:path';
import { parse as parseToml } from 'smol-toml';
import { KotlinReader, literal, listItems, mapEntries, namePath, kotlinNumber, functionTokens } from '../lib/kotlin.mjs';
import { readAppStrings, formatAndroid } from '../lib/android-strings.mjs';
import { parseBool, parseIntShape, listOption, rawFromToml, nativeStore } from '../../src/tool/lib/config-shape.js';

const APP = 'app/src/main/java/xendroid/compose';
export const SETTINGS_SOURCES = {
  schema: `${APP}/settings/SettingsSchema.kt`,
  descriptions: `${APP}/settings/SettingDescriptions.kt`,
  catalog: `${APP}/settings/SettingCatalog.kt`,
  contract: `${APP}/settings/SettingContract.kt`,
  texts: `${APP}/ui/settings/SettingTexts.kt`,
  xd: `${APP}/ui/settings/XdSettings.kt`,
  turnip: `${APP}/settings/TurnipFlags.kt`,
  drivers: `${APP}/ui/drivers/DriversScreen.kt`,
  buttons: `${APP}/data/GameButtons.kt`,
  res: 'app/src/main/res',
  template: 'emulator-core/src/main/assets/config/default_config.toml',
};

function reader(root, rel) {
  const file = path.join(root, rel);
  return new KotlinReader(fs.readFileSync(file, 'utf8'), rel);
}

const LEVELS = { E: 'ESSENTIAL', A: 'ADVANCED', X: 'ALL' };
const ICONS = { image: 'image', chart: 'chart', speaker: 'speaker', gamepad: 'gamepad', shield: 'shield', chip: 'chip', cpu: 'cpu', flask: 'flask' };

/** Textos de "Section|name" → recurso de string (mapas TITLES/DESCRIPTIONS de SettingTexts.kt). */
function stringMap(r, name) {
  const node = r.valueOf(name);
  const out = new Map();
  for (const [k, v] of mapEntries(node, r)) {
    const p = namePath(v, r);
    if (!p.startsWith('R.string.')) throw r.error(`${name}: esperava R.string.*`, { line: v.line });
    out.set(literal(k, r), p.slice('R.string.'.length));
  }
  return out;
}

/** Ramos de um `when (alvo) { ... }`: [{ conds: [strings] | 'else', tokens: [...] }]. */
function whenBranches(r, start, end, subject) {
  const t = r.tokens;
  let i = start;
  while (i < end && !(t[i].v === 'when' && t[i + 1].v === '(' && t[i + 2].v === subject.split('.')[0])) i++;
  if (i >= end) throw r.error(`when (${subject}) não encontrado`, t[start]);
  while (t[i].v !== '{') i++;
  const close = matchBrace(t, i);
  const branches = [];
  let k = i + 1;
  while (k < close) {
    // condições até "->"
    const conds = [];
    let isElse = false;
    while (t[k].v !== '->') {
      if (t[k].t === 'str') conds.push(t[k].v);
      else if (t[k].v === 'else') isElse = true;
      else if (t[k].v !== ',') throw r.error(`condição inesperada "${t[k].v}" no when (${subject})`, t[k]);
      k++;
    }
    k++;
    // corpo até o próximo início de ramo no nível zero
    const bodyStart = k;
    let depth = 0;
    for (; k < close; k++) {
      const v = t[k].v;
      if (v === '(' || v === '{' || v === '[') depth++;
      else if (v === ')' || v === '}' || v === ']') depth--;
      if (depth === 0 && k > bodyStart && t[k].line > t[k - 1].line && (t[k].t === 'str' || t[k].v === 'else') && branchStartsAt(t, k)) break;
    }
    branches.push({ conds: isElse ? 'else' : conds, tokens: t.slice(bodyStart, k), line: t[bodyStart].line });
  }
  return branches;
}

function branchStartsAt(t, k) {
  // "x", "y" -> ...   ou   else -> ...
  let j = k;
  while (t[j] && (t[j].t === 'str' || t[j].v === ',' || t[j].v === 'else')) j++;
  return t[j] && t[j].v === '->';
}

function matchBrace(t, open) {
  let d = 0;
  for (let k = open; k < t.length; k++) {
    if (t[k].v === '{') d++;
    if (t[k].v === '}') { d--; if (d === 0) return k; }
  }
  throw new Error('chave sem fechamento');
}

/** Texto de um valor de ramo: t(R.string.x) ou "literal" ou context.getString(R.string.x). */
function textOf(tokens) {
  const vs = tokens.map(x => x.v);
  if (tokens.length === 1 && tokens[0].t === 'str') return { literal: tokens[0].v };
  const rs = vs.indexOf('R');
  if (rs >= 0 && vs[rs + 1] === '.' && vs[rs + 2] === 'string' && vs[rs + 3] === '.') {
    if ((vs[0] === 't' || vs[0] === 'getString') && vs[1] === '(') return { res: vs[rs + 4] };
  }
  return null;
}

/** optionText (XdSettings.kt): rótulo de cada opção como o app mostra. */
function optionTextRules(xd) {
  const fn = functionTokens(xd, 'optionText');
  const branches = whenBranches(xd, fn.start, fn.end, 's.key');
  const rules = new Map();
  for (const b of branches) {
    if (b.conds === 'else') continue;
    const toks = b.tokens;
    const vs = toks.map(x => x.v);
    let rule;
    if (vs[0] === 'if' && vs[1] === '(' && vs[2] === 'value' && vs[3] === '==' && toks[4].t === 'str' && vs[5] === ')') {
      // if (value == "V") X else english
      const elseAt = vs.lastIndexOf('else');
      if (vs.slice(elseAt + 1).join('') !== 'english') throw xd.error('optionText: ramo if sem "else english"', toks[0]);
      const x = textOf(toks.slice(6, elseAt));
      if (!x) throw xd.error('optionText: texto não reconhecido', toks[0]);
      rule = { kind: 'map', map: { [toks[4].v]: x } };
    } else if (vs[0] === 'when' && vs[1] === '(' && vs[2] === 'value' && vs[3] === ')') {
      const map = {};
      let k = 5; // depois de "{"
      while (k < toks.length - 1) {
        const conds = [];
        let isElse = false;
        while (vs[k] !== '->') {
          if (toks[k].t === 'str') conds.push(toks[k].v);
          else if (vs[k] === 'else') isElse = true;
          k++;
        }
        k++;
        const bodyStart = k;
        let depth = 0;
        while (k < toks.length - 1) {
          const v = vs[k];
          if (v === '(') depth++;
          if (v === ')') depth--;
          if (depth === 0 && (v === ';' || (toks[k].line > toks[k - 1].line && (toks[k].t === 'str' || v === 'else')))) break;
          k++;
        }
        const body = toks.slice(bodyStart, k);
        if (vs[k] === ';') k++;
        if (isElse) {
          if (body.map(x => x.v).join('') !== 'english') throw xd.error('optionText: else diferente de english', body[0]);
          continue;
        }
        const x = textOf(body);
        if (!x) throw xd.error(`optionText: texto não reconhecido (${body.map(t => t.v).join(' ')})`, body[0]);
        for (const c of conds) map[c] = x;
      }
      rule = { kind: 'map', map };
    } else if (vs.includes('TurnipFlags')) {
      const none = textOf(toks.slice(vs.lastIndexOf('t')));
      rule = { kind: 'turnip', none };
    } else if (vs.includes('xd_opt_up_to_px')) {
      if (!vs.includes('set_option_off')) throw xd.error('optionText: ramo "até N px" mudou', toks[0]);
      rule = { kind: 'upToPx', off: 'set_option_off', template: 'xd_opt_up_to_px' };
    } else if (vs.includes('displayLanguage')) {
      rule = { kind: 'language' };
    } else if (vs.includes('displayCountry')) {
      rule = { kind: 'country' };
    } else {
      throw xd.error(`optionText: ramo de ${b.conds.join(', ')} não reconhecido; atualize scripts/extract/settings.mjs`, toks[0]);
    }
    for (const key of b.conds) rules.set(key, rule);
  }
  return rules;
}

/** Ramos "k1", "k2" -> número de stepOf (XdSettings.kt). */
function stepRules(xd) {
  const fn = functionTokens(xd, 'stepOf');
  const out = new Map();
  for (const b of whenBranches(xd, fn.start, fn.end, 's.key')) {
    if (b.conds === 'else') continue;
    const num = b.tokens.find(x => x.t === 'num');
    for (const k of b.conds) out.set(k, kotlinNumber(num.v));
  }
  return out;
}

/** Nome do recurso e do ícone de cada grupo (groupTitle/groupIcon em XdSettings.kt). */
function groupRules(xd) {
  const titles = new Map();
  const icons = new Map();
  for (const [fname, target] of [['groupTitle', titles], ['groupIcon', icons]]) {
    const fn = functionTokens(xd, fname);
    const t = xd.tokens;
    for (let k = fn.start; k < fn.end; k++) {
      if (t[k].v === 'SettingGroup' && t[k + 1].v === '.' && t[k + 3].v === '->') {
        const g = t[k + 2].v;
        if (target === titles) target.set(g, t[k + 8].v); // R . string . NAME
        else target.set(g, t[k + 6].v); // XdIcons . name
      }
    }
  }
  return { titles, icons };
}

export function extractSettings(root) {
  const S = SETTINGS_SOURCES;
  const problems = [];
  const strings = readAppStrings(path.join(root, S.res));
  const str = name => {
    const v = strings.get(name);
    if (!v) problems.push({ level: 'error', msg: `string "${name}" não existe em ${S.res}` });
    return v || { text: name, lang: 'en' };
  };

  // ---------- descrições (inglês, SettingDescriptions.kt) ----------
  const dr = reader(root, S.descriptions);
  const descByName = new Map(mapEntries(dr.valueOf('byName'), dr).map(([k, v]) => [literal(k, dr), literal(v, dr)]));

  // ---------- esquema ----------
  const sr = reader(root, S.schema);
  const deadZones = listItems(sr.valueOf('DEAD_ZONES'), sr).map(p => [literal(p.left, sr), literal(p.right, sr)]);
  const isoCountries = listItems(sr.valueOf('USER_COUNTRY_ISO'), sr).map(n => literal(n, sr));
  // userCountryOptions(): valores 1..109 pulando 17 e 94, na ordem de USER_COUNTRY_ISO
  {
    const fn = functionTokens(sr, 'userCountryOptions');
    const body = sr.tokens.slice(fn.start, fn.end).map(t => t.v).join(' ');
    if (!/value == 17 \|\| value == 94/.test(body)) {
      throw sr.error('userCountryOptions mudou (valores pulados); atualize scripts/extract/settings.mjs', sr.tokens[fn.start]);
    }
  }
  const countryOptions = [];
  for (let iso = 0, value = 1; iso < isoCountries.length;) {
    if (value === 17 || value === 94) { value++; continue; }
    countryOptions.push([String(value), isoCountries[iso]]);
    value++; iso++;
  }
  const spreads = { DEAD_ZONES: deadZones, userCountryOptions: countryOptions };

  const categories = listItems(sr.valueOf('categories'), sr);
  const settings = [];
  const devCategories = [];
  for (const cat of categories) {
    if (cat.kind !== 'call' || cat.callee !== 'SettingsCategory') throw sr.error('esperava SettingsCategory(...)', cat);
    const title = literal(cat.args[0].value, sr);
    devCategories.push(title);
    for (const node of listItems(cat.args[1].value, sr)) {
      if (node.kind !== 'call') throw sr.error('ajuste fora do formato b/i/l/t/Action', node);
      const a = node.args.map(x => x.value);
      const base = { section: literal(a[0], sr), name: literal(a[1], sr), englishTitle: literal(a[2], sr), devCategory: title, line: node.line };
      let s;
      switch (node.callee) {
        case 'b': s = { ...base, type: 'bool', schemaDefault: literal(a[3], sr) ? 'true' : 'false' }; break;
        case 'i': s = { ...base, type: 'int', schemaDefault: String(kotlinNumber(literal(a[3], sr))), min: kotlinNumber(literal(a[4], sr)), max: kotlinNumber(literal(a[5], sr)) }; break;
        case 'l': {
          const options = [];
          for (const o of a.slice(4)) {
            if (o.kind === 'pair') options.push([literal(o.left, sr), literal(o.right, sr)]);
            else if (o.kind === 'spread') {
              const name = o.value.kind === 'call' ? o.value.callee : namePath(o.value, sr);
              if (!spreads[name]) throw sr.error(`opções espalhadas de "${name}" não reconhecidas`, o);
              options.push(...spreads[name]);
            } else throw sr.error('opção de lista fora do formato "valor" to "rótulo"', o);
          }
          s = { ...base, type: 'list', schemaDefault: literal(a[3], sr), englishOptions: options };
          break;
        }
        case 't': s = { ...base, type: 'text', schemaDefault: literal(a[3], sr), placeholder: literal(a[4], sr) }; break;
        case 'Action': s = { ...base, type: 'action', schemaDefault: literal(a[3], sr) }; break;
        default: throw sr.error(`construtor de ajuste desconhecido: ${node.callee}`, node);
      }
      s.key = `${s.section}|${s.name}`;
      s.englishDesc = descByName.get(s.name) || '';
      settings.push(s);
    }
  }
  const byKey = new Map(settings.map(s => [s.key, s]));
  for (const s of settings) if (settings.filter(x => x.key === s.key).length > 1) problems.push({ level: 'error', msg: `${s.key} aparece mais de uma vez no esquema` });
  const playerKeys = listItems(sr.valueOf('playerKeys'), sr).map(n => literal(n, sr));

  // ---------- catálogo do redesenho ----------
  const cr = reader(root, S.catalog);
  const groupsOrder = [];
  const meta = new Map();
  for (const [gk, list] of mapEntries(cr.valueOf('order'), cr)) {
    const group = namePath(gk, cr).replace('SettingGroup.', '');
    groupsOrder.push(group);
    listItems(list, cr).forEach((e, index) => {
      if (e.kind !== 'call' || e.callee !== 'e') throw cr.error('esperava e(...)', e);
      const key = literal(e.args[0].value, cr);
      const lv = namePath(e.args[1].value, cr);
      const m = { group, level: LEVELS[lv], order: index, live: false, isNew: false, newOptions: [], dependsOn: null, warning: null };
      if (!m.level) throw cr.error(`nível desconhecido ${lv}`, e);
      for (const arg of e.args.slice(2)) {
        switch (arg.name) {
          case 'live': m.live = literal(arg.value, cr); break;
          case 'isNew': m.isNew = literal(arg.value, cr); break;
          case 'newOptions': m.newOptions = listItems(arg.value, cr).map(n => literal(n, cr)); break;
          case 'dependsOn': m.dependsOn = { key: literal(arg.value.left, cr), value: literal(arg.value.right, cr) }; break;
          case 'warning': m.warning = namePath(arg.value, cr).replace('Warning.', ''); break;
          default: throw cr.error(`argumento desconhecido em e(...): ${arg.name}`, arg.value);
        }
      }
      if (meta.has(key)) problems.push({ level: 'error', msg: `${key} aparece em mais de um grupo do catálogo` });
      meta.set(key, m);
    });
  }
  const resolution = literal(cr.valueOf('RESOLUTION'), cr);
  const pinnedDefault = listItems(cr.valueOf('DEFAULT'), cr).map(n => (n.kind === 'name' && n.path[0] === 'RESOLUTION' ? resolution : literal(n, cr)));

  // ---------- contrato (aplicação) ----------
  const ctr = reader(root, S.contract);
  const ctrSrc = ctr.src;
  const actionName = /val action = setting\.name == "([^"]+)"/.exec(ctrSrc)?.[1];
  if (!actionName) problems.push({ level: 'error', msg: 'SettingContract: ajuste de ação não encontrado' });
  const liveSetters = Object.fromEntries([...ctrSrc.matchAll(/"([A-Za-z]+\|[A-Za-z0-9_]+)" -> LiveSetter\.([A-Z_]+)/g)].map(m => [m[1], m[2]]));
  const driverGate = /val supported = setting\.name != "([^"]+)"/.exec(ctrSrc)?.[1];
  const unsupportedDriver = literal(ctr.valueOf('UNSUPPORTED_DRIVER'), ctr);

  // ---------- textos ----------
  const tr = reader(root, S.texts);
  const titleRes = stringMap(tr, 'TITLES');
  const descRes = stringMap(tr, 'DESCRIPTIONS');

  // ---------- XdSettings: unidades, passos, rótulos, grupos, predefinições ----------
  const xd = reader(root, S.xd);
  const units = new Map(mapEntries(xd.valueOf('UNITS'), xd).map(([k, v]) => [literal(k, xd), literal(v, xd)]));
  const steps = stepRules(xd);
  const optRules = optionTextRules(xd);
  const { titles: groupTitleRes, icons: groupIcons } = groupRules(xd);
  const presetsAt = xd.find(['object', 'SettingsPresets']);
  if (presetsAt < 0) throw xd.error('object SettingsPresets não encontrado', xd.tokens[0]);
  const presets = listItems(xd.valueOf('all', { from: presetsAt }), xd).map(p => {
    if (p.kind !== 'call' || p.callee !== 'SettingsPreset') throw xd.error('esperava SettingsPreset(...)', p);
    const [id, t, d, values] = p.args.map(a => a.value);
    return {
      id: literal(id, xd),
      title: str(namePath(t, xd).replace('R.string.', '')),
      description: str(namePath(d, xd).replace('R.string.', '')),
      values: Object.fromEntries(mapEntries(values, xd).map(([k, v]) => [literal(k, xd), literal(v, xd)])),
    };
  });

  // ---------- modelo de configuração (padrões do app) ----------
  const templateText = fs.readFileSync(path.join(root, S.template), 'utf8');
  const template = parseToml(templateText, { integersAsBigInt: true });

  const label = (s, value) => optionLabel(s, value, optRules.get(s.key), str);

  const out = [];
  for (const s of settings) {
    const m = meta.get(s.key);
    if (!m) { problems.push({ level: 'error', msg: `${s.key} não está em SettingCatalog (todo ajuste deve ter grupo e nível)` }); continue; }
    const titleR = titleRes.get(s.key);
    const descR = descRes.get(s.key);
    const tplNode = template[s.section]?.[s.name];
    const templateRaw = tplNode === undefined ? null : rawFromToml(tplNode);
    if (tplNode !== undefined && templateRaw == null) problems.push({ level: 'warn', msg: `${s.key}: valor do modelo com tipo não suportado` });
    const o = {
      key: s.key, section: s.section, name: s.name, type: s.type, devCategory: s.devCategory, player: playerKeys.includes(s.key),
      title: titleR ? str(titleR) : { text: s.englishTitle, lang: 'en' },
      desc: descR ? str(descR) : { text: s.englishDesc, lang: 'en' },
      englishTitle: s.englishTitle,
      group: m.group, level: m.level, order: m.order, live: m.live, isNew: m.isNew, newOptions: m.newOptions,
      dependsOn: m.dependsOn,
      warning: m.warning ? { id: m.warning, ...str({ NEEDS_BARYCENTRICS: 'xd_set_warn_barycentrics', NO_EFFECT_ON_TURNIP: 'xd_set_warn_turnip', NEEDS_MAX_CLOCKS: 'xd_set_warn_max_clocks' }[m.warning]) } : null,
      apply: s.name === actionName ? 'action' : 'next',
      liveSetter: liveSetters[s.key] || null,
      customDriver: s.name === driverGate,
      schemaDefault: s.schemaDefault,
      templateRaw,
      source: `${S.schema}:${s.line}`,
    };
    if (s.type === 'int') Object.assign(o, { min: s.min, max: s.max, step: steps.get(s.key) || 1, unit: units.get(s.key) || '' });
    if (s.type === 'text') Object.assign(o, { placeholder: s.placeholder });
    if (s.type === 'list') {
      o.options = s.englishOptions.map(([value, english]) => ({ value, english, label: label(s, value) }));
    }
    // padrão do app: o do modelo, senão o do esquema (SettingsRepository.defaultRaw), na forma em que a tela mostra
    const raw = templateRaw ?? s.schemaDefault;
    o.default = normalize(s, raw);
    o.defaultFrom = templateRaw == null ? 'esquema' : 'modelo';
    if (templateRaw != null && normalize(s, templateRaw) !== normalize(s, s.schemaDefault)) {
      o.schemaDefaultDiffers = true;
    }
    out.push(o);
  }
  for (const key of meta.keys()) if (!byKey.has(key)) problems.push({ level: 'error', msg: `SettingCatalog lista ${key}, que não existe no esquema` });
  for (const key of pinnedDefault) if (key !== resolution && !byKey.has(key)) problems.push({ level: 'error', msg: `PinnedSettings.DEFAULT lista ${key}, que não existe` });
  for (const p of presets) for (const key of Object.keys(p.values)) if (!byKey.has(key)) problems.push({ level: 'error', msg: `predefinição ${p.id} usa ${key}, que não existe` });
  for (const key of playerKeys) if (!byKey.has(key)) problems.push({ level: 'error', msg: `playerKeys lista ${key}, que não existe` });

  const groups = groupsOrder.map(g => ({ id: g, title: str(groupTitleRes.get(g)), icon: ICONS[groupIcons.get(g)] || 'gear' }));

  // ---------- flags do Turnip ----------
  const tf = reader(root, S.turnip);
  const exclusive = listItems(tf.valueOf('EXCLUSIVE'), tf).map(n => literal(n, tf));
  // a tela Drivers mostra a ajuda de cada flag no idioma do app (turnipHelp em DriversScreen.kt)
  const helpRes = new Map();
  const drv = fs.readFileSync(path.join(root, S.drivers), 'utf8');
  const helpFn = /fun turnipHelp\([^)]*\)[^{=]*=\s*when\s*\(\w+\)\s*\{([\s\S]*?)\n\}/.exec(drv);
  if (helpFn) for (const m of helpFn[1].matchAll(/"([\w-]+)"\s*->\s*stringResource\(R\.string\.(\w+)\)/g)) helpRes.set(m[1], m[2]);
  else problems.push({ level: 'warn', msg: `${S.drivers}: turnipHelp não encontrado; as flags ficam com a ajuda em inglês` });
  const turnipFlags = listItems(tf.valueOf('KNOWN'), tf).map(f => {
    const name = literal(f.args[0].value, tf);
    const res = helpRes.get(name);
    const pt = res ? strings.get(res) : null;
    return {
      name,
      help: pt || { text: literal(f.args[1].value, tf), lang: 'en' },
      helpEnglish: literal(f.args[1].value, tf),
      exclusive: exclusive.includes(name),
    };
  });

  // ---------- botões mapeáveis ----------
  const gb = reader(root, S.buttons);
  const buttons = listItems(gb.valueOf('ALL'), gb).map(b => {
    const [index, keyCode, androidKey, englishLabel] = b.args.map(a => a.value);
    return { index: kotlinNumber(literal(index, gb)), keyCode: kotlinNumber(literal(keyCode, gb)), defaultKey: namePath(androidKey, gb).replace('KeyEvent.', ''), englishLabel: literal(englishLabel, gb) };
  });

  const texts = Object.fromEntries(Object.entries({
    on: 'xd_set_on', off: 'xd_set_off', live: 'xd_set_live', nextLaunch: 'xd_set_next_launch', action: 'xd_set_action',
    scopeGlobal: 'xd_set_scope_global', srcGame: 'xd_set_src_game', srcGlobal: 'xd_set_src_global', srcDefault: 'xd_set_src_default',
    srcChanged: 'xd_set_src_changed', badgeNew: 'xd_set_badge_new', badgeNewOption: 'xd_set_badge_new_option', depends: 'xd_set_depends',
    defaultValue: 'xd_set_default_value', valueNone: 'xd_set_value_none', driverSystem: 'drv_system', driverUnavailable: 'set_driver_unavailable',
    levelEssential: 'xd_set_level_essential', levelAdvanced: 'xd_set_level_advanced', levelAll: 'xd_set_level_all',
    turnipNone: 'xd_drv_turnip_none', recommendedNone: 'xd_preset_recommended_none', communityOff: 'xd_preset_community_off',
  }).map(([k, res]) => [k, str(res)]));

  return {
    settings: out,
    groups,
    devCategories,
    pinnedDefault,
    resolutionKey: resolution,
    presets,
    turnipFlags,
    buttons,
    texts,
    unsupportedDriver: { text: unsupportedDriver, lang: 'en' },
    sources: Object.values(S),
    problems,
  };
}

/** Valor bruto na forma que a linha mostra (parseBool/parseInt/listOption do ConfigValueShape). */
export function normalize(s, raw) {
  switch (s.type) {
    case 'bool': return parseBool(raw, s.schemaDefault === 'true') ? 'true' : 'false';
    case 'int': return String(parseIntShape(raw, Number(s.schemaDefault)));
    case 'list': return listOption((s.englishOptions || s.options).map(o => (Array.isArray(o) ? o[0] : o.value)), raw) ?? s.schemaDefault;
    default: return raw ?? '';
  }
}

/** optionText: o rótulo de uma opção num aparelho em pt-BR (text) e num em inglês (en). */
function optionLabel(s, value, rule, str) {
  const english = (s.englishOptions.find(o => o[0] === value) || [value, value])[1];
  const en = { text: english, lang: 'en' };
  if (!rule) return en;
  switch (rule.kind) {
    case 'map': {
      const x = rule.map[value];
      if (!x) return en;
      return x.res ? str(x.res) : { text: x.literal, lang: 'pt-BR', en: x.literal };
    }
    case 'turnip': {
      const flags = value.split(',').map(f => f.trim().toLowerCase()).filter(f => /^[a-z0-9_]{1,40}$/.test(f));
      return flags.length ? { text: [...new Set(flags)].join(', '), lang: 'en' } : str(rule.none.res);
    }
    case 'upToPx': {
      const n = parseInt(value, 10);
      if (Number.isNaN(n)) return en;
      const tpl = str(rule.template);
      return n === 0 ? str(rule.off) : { text: formatAndroid(tpl.text, n), lang: 'pt-BR', en: formatAndroid(tpl.en ?? tpl.text, n) };
    }
    case 'language': {
      // java.util.Locale(english).displayLanguage com a primeira letra maiúscula
      const display = loc => { try { return new Intl.DisplayNames([loc], { type: 'language' }).of(english) || ''; } catch { return ''; } };
      const up = s => s[0].toUpperCase() + s.slice(1);
      const name = display('pt-BR'), nameEn = display('en');
      if (!name || name === english) return nameEn && nameEn !== english ? { text: english, lang: 'en', en: up(nameEn) } : en;
      return { text: up(name), lang: 'pt-BR', en: nameEn && nameEn !== english ? up(nameEn) : english };
    }
    case 'country': {
      const display = loc => { try { return new Intl.DisplayNames([loc], { type: 'region' }).of(english) || ''; } catch { return ''; } };
      const name = display('pt-BR'), nameEn = display('en');
      return name && name !== english ? { text: name, lang: 'pt-BR', en: nameEn && nameEn !== english ? nameEn : english } : { ...en, en: nameEn && nameEn !== english ? nameEn : english };
    }
    default: return en;
  }
}

/** O valor que o app grava para um ajuste (ConfigHandle.putSetting) e o que o nativo guarda. */
export function storedFor(s, raw) {
  switch (s.type) {
    case 'bool': return nativeStore(parseBool(raw, s.schemaDefault === 'true') ? 'true' : 'false');
    case 'int': return nativeStore(String(parseIntShape(raw, Number(s.schemaDefault))));
    default: return nativeStore(String(raw));
  }
}
