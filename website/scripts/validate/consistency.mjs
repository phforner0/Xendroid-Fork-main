// Confere os ajustes do app, o modelo default_config.toml e as quirks por jogo contra as cvars
// que o core define no build Android. O core lê cada cvar em "[categoria] nome"
// (config.cc: toml::path(category + "." + name)) e descarta um valor de tipo errado; então uma
// seção diferente ou um tipo recusado quer dizer que o valor gravado não vale.
// O resultado vai para o relatório publicado em "Dados do site"; nada aqui conserta o app.
import { nativeStore, coreRejects, rawFromToml } from '../../src/tool/lib/config-shape.js';

/** Chaves antigas que o core ainda entende sem cvar própria (config.cc, MigrateLegacyCvars). */
const LEGACY = { mute: 'o core lê "mute = true" como volume 0 (MigrateLegacyCvars, config.cc)' };

/** Valores que um ajuste pode gravar: o padrão e cada opção da lista. */
function storedValues(s) {
  const raws = new Set();
  if (s.default != null && s.default !== '') raws.add(String(s.default));
  for (const o of s.options || []) if (o.value !== '') raws.add(String(o.value));
  if (s.type === 'bool') { raws.add('true'); raws.add('false'); }
  return [...raws];
}

/** Padrão literal do core em texto comparável com o do app ("true", "60", "0.2", "fsr"). */
function coreDefault(cv) {
  if (!cv.defaultIsLiteral) return null;
  return String(cv.defaultRaw).replace(/^"(.*)"$/s, '$1').replace(/^u8"(.*)"$/s, '$1').replace(/[fF]$/, '');
}

function sameValue(a, b) {
  if (a === b) return true;
  const na = Number(a), nb = Number(b);
  return a !== '' && b !== '' && Number.isFinite(na) && Number.isFinite(nb) && na === nb;
}

/**
 * @param settings  saída de extractSettings
 * @param cvars     { byName } de indexCvars
 * @param template  default_config.toml já lido (smol-toml, integersAsBigInt)
 * @param quirks    entradas de readQuirks
 */
export function checkConsistency({ settings, cvars, template, quirks }) {
  const warnings = [];
  const infos = [];
  const coreDefaults = [];
  const warn = (code, msg, source) => warnings.push({ code, msg, source });

  for (const s of settings.settings) {
    if (!s.section || s.type === 'action') continue;
    const cv = cvars.byName.get(s.name);
    if (!cv) { warn('ajuste-sem-cvar', `${s.key}: o core não define a cvar "${s.name}"; o valor gravado é ignorado`, s.source); continue; }
    if (cv.category !== s.section) {
      warn('secao-diferente', `${s.key}: o app grava em [${s.section}], mas o core lê "${s.name}" em [${cv.category}]`, s.source);
    }
    for (const raw of storedValues(s)) {
      const why = coreRejects(cv.type, nativeStore(raw));
      if (why) warn('tipo-recusado', `${s.key} = ${JSON.stringify(raw)}: ${why}`, s.source);
    }
    const cd = coreDefault(cv);
    if (cd != null && s.default != null && !sameValue(String(s.default), cd)) {
      coreDefaults.push({ key: s.key, app: String(s.default), appFrom: s.defaultFrom, core: cd, cvarSource: `${cv.file}:${cv.line}` });
    }
  }

  for (const [section, table] of Object.entries(template || {})) {
    if (!table || typeof table !== 'object') continue;
    for (const [name, value] of Object.entries(table)) {
      const cv = cvars.byName.get(name);
      const where = `emulator-core/src/main/assets/config/default_config.toml [${section}] ${name}`;
      if (!cv && LEGACY[name]) { infos.push({ code: 'modelo-legado', msg: `${where}: ${LEGACY[name]}`, source: where }); continue; }
      if (!cv) { warn('modelo-sem-cvar', `${where}: o core não define essa cvar`, where); continue; }
      if (cv.category !== section) warn('modelo-secao', `${where}: o core lê em [${cv.category}]`, where);
      if (typeof value === 'object' && value !== null) continue;
      const stored = typeof value === 'boolean' ? { type: 'boolean', value }
        : typeof value === 'bigint' ? { type: 'integer', value: Number(value) }
        : typeof value === 'number' ? { type: 'float', value }
        : { type: 'string', value: String(value) };
      const why = coreRejects(cv.type, stored);
      if (why) warn('modelo-tipo', `${where} = ${rawFromToml(value)}: ${why}`, where);
    }
  }

  for (const q of quirks) {
    const cv = cvars.byName.get(q.cvar);
    const where = `${q.file}:${q.line}`;
    if (!cv) { warn('quirk-sem-cvar', `quirk de ${q.titleId}: a cvar "${q.cvar}" não existe no core`, where); continue; }
    const t = q.type;
    const ok = (t === 'bool' && cv.type === 'bool') || (t === 'string' && (cv.type === 'string' || cv.type === 'path'))
      || (t === 'int' && /int/.test(cv.type)) || (t === 'double' && cv.type === 'double') || (t === 'int' && cv.type === 'double');
    if (!ok) warn('quirk-tipo', `quirk de ${q.titleId}: ${q.cvar} recebe ${t}, mas a cvar é ${cv.type}`, where);
  }

  const differs = settings.settings.filter(s => s.schemaDefaultDiffers);
  for (const s of differs) infos.push({ code: 'padrao-modelo', msg: `${s.key}: o modelo default_config.toml traz ${JSON.stringify(s.templateRaw)}, o esquema do app ${JSON.stringify(s.schemaDefault)}; vale o do modelo`, source: s.source });

  return { warnings, infos, coreDefaults };
}

/** Mapa compacto das cvars usadas pelos ajustes, para o simulador conferir o TOML no navegador. */
export function cvarTable(settings, cvars) {
  const out = {};
  for (const s of settings.settings) {
    if (!s.section) continue;
    const cv = cvars.byName.get(s.name);
    if (cv) out[s.name] = [cv.category, cv.type];
  }
  return out;
}
