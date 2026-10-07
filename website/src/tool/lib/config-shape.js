// Regras do Xendroid+ para valores de configuração, compartilhadas pelo build do site (Node)
// e pela ferramenta (navegador). Cada função cita o código que ela reproduz.

/* ---------------------------------------------------------------------------------------
 * app/src/main/java/xendroid/compose/settings/ConfigValueShape.kt
 * ------------------------------------------------------------------------------------- */

/** ConfigValueShape.parseBool: só "true" e "false" contam; o resto volta ao padrão. */
export function parseBool(raw, def) {
  if (raw === 'true') return true;
  if (raw === 'false') return false;
  return def;
}

/** ConfigValueShape.parseInt: inteiro, ou um número que voltou do nativo como double ("8.0"). */
export function parseIntShape(raw, def) {
  if (raw == null) return def;
  const s = String(raw);
  if (/^[+-]?\d+$/.test(s)) {
    const v = Number(s);
    if (Number.isSafeInteger(v) && v >= -2147483648 && v <= 2147483647) return v;
  }
  const d = kotlinToDouble(s);
  if (d != null && Number.isFinite(d)) return Math.trunc(d);
  return def;
}

/** String.toDoubleOrNull do Kotlin (aceita "1", "1.0", "1e3", "NaN", "Infinity", ".5"). */
export function kotlinToDouble(s) {
  if (typeof s !== 'string') return null;
  const t = s.trim();
  if (t !== s || t === '') return null;
  if (/^[+-]?(NaN|Infinity)$/.test(t)) return Number(t.replace('+', ''));
  if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?[fFdD]?$/.test(t)) return null;
  return Number(t.replace(/[fFdD]$/, ''));
}

/**
 * ConfigValueShape.listOption: o valor guardado como uma das opções; um número lido do
 * nativo ("0.100000" para "0.1") vira a opção de mesmo valor; o resto fica como está.
 */
export function listOption(options, raw) {
  if (raw == null || options.includes(raw)) return raw;
  const n = kotlinToDouble(raw);
  if (n == null) return raw;
  const hit = options.find(o => { const v = kotlinToDouble(o); return v != null && Math.abs(v - n) < 1e-6; });
  return hit ?? raw;
}

/* ---------------------------------------------------------------------------------------
 * emulator-core/src/main/cpp/emulator.cpp, j_save_config_entry (ramo TOML):
 * o nativo deduz o tipo TOML pela forma da string.
 * ------------------------------------------------------------------------------------- */

const FLT_MAX = 3.4028234663852886e38;
const FLT_MIN_NORMAL = 1.1754943508222875e-38;

/** Prefixo que strtof/strtod aceitariam (std::stof ignora espaços iniciais e o resto). */
function cFloatPrefix(str) {
  const m = /^[ \t\n\v\f\r]*([+-]?(?:0[xX](?:[0-9a-fA-F]+\.?[0-9a-fA-F]*|\.[0-9a-fA-F]+)(?:[pP][+-]?\d+)?|(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?|inf(?:inity)?|nan(?:\([0-9A-Za-z_]*\))?))/i.exec(str);
  return m ? m[1] : null;
}

function cParse(prefix) {
  const p = prefix.toLowerCase();
  const sign = p.startsWith('-') ? -1 : 1;
  const body = p.replace(/^[+-]/, '');
  if (body.startsWith('inf')) return sign * Infinity;
  if (body.startsWith('nan')) return NaN;
  if (body.startsWith('0x')) {
    const m = /^0x([0-9a-f]*)\.?([0-9a-f]*)(?:p([+-]?\d+))?$/.exec(body);
    const int = m[1] ? parseInt(m[1], 16) : 0;
    const frac = m[2] ? parseInt(m[2], 16) / Math.pow(16, m[2].length) : 0;
    return sign * (int + frac) * Math.pow(2, m[3] ? Number(m[3]) : 0);
  }
  return sign * Number(body);
}

/** is_float_number: exatamente um "." e std::stof sem exceção (sem conversão ou fora da faixa). */
export function isFloatNumber(str) {
  if ((str.match(/\./g) || []).length !== 1) return false;
  const prefix = cFloatPrefix(str);
  if (prefix == null) return false;
  const v = cParse(prefix);
  if (Number.isNaN(v)) return true;
  const a = Math.abs(v);
  if (a > FLT_MAX && a !== Infinity) return false;
  if (a !== 0 && a < FLT_MIN_NORMAL) return false;
  return true;
}

/** is_int_number: só dígitos, com um "-" opcional quando há mais de um caractere. */
export function isIntNumber(str) {
  if (str.length === 0) return false;
  const body = str.length > 1 && str[0] === '-' ? str.slice(1) : str;
  return /^[0-9]+$/.test(body);
}

/**
 * O valor que o nativo guarda para `raw`: { type: boolean|float|integer|string, value }.
 * "true"/"false" → booleano; float → std::stod; inteiro que cabe em int32 (std::stoi) →
 * inteiro; o resto (inclusive inteiros grandes demais) → string.
 */
export function nativeStore(raw) {
  const str = String(raw);
  if (str === 'true' || str === 'false') return { type: 'boolean', value: str === 'true' };
  if (isFloatNumber(str)) return { type: 'float', value: cParse(cFloatPrefix(str)) };
  if (isIntNumber(str)) {
    const v = Number(str);
    if (v >= -2147483648 && v <= 2147483647) return { type: 'integer', value: v };
    return { type: 'string', value: str };
  }
  return { type: 'string', value: str };
}

/* ---------------------------------------------------------------------------------------
 * j_load_config_entry: como o nativo devolve um valor lido do TOML para o app.
 * ------------------------------------------------------------------------------------- */

/** std::to_string(double) usa "%f": seis casas decimais. */
export function cppDoubleToString(v) {
  if (Number.isNaN(v)) return v < 0 ? '-nan' : 'nan';
  if (!Number.isFinite(v)) return v < 0 ? '-inf' : 'inf';
  const s = v.toFixed(6);
  return s === '-0.000000' ? '-0.000000' : s;
}

/**
 * Valor de um nó TOML como a string que o app recebe do nativo. Inteiros chegam como BigInt
 * (smol-toml com integersAsBigInt) e viram std::to_string(int64); números de ponto flutuante
 * viram std::to_string(double).
 */
export function rawFromToml(value) {
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'bigint') return value.toString();
  if (typeof value === 'number') return cppDoubleToString(value);
  if (typeof value === 'string') return value;
  return null;
}

/* ---------------------------------------------------------------------------------------
 * Saída TOML como o toml++ 3.4 do core imprime (toml_formatter com default_flags):
 * tabelas e chaves em ordem de bytes (std::map), strings literais com aspas simples quando
 * possível, linha em branco entre tabelas.
 * ------------------------------------------------------------------------------------- */

const BARE = /^[A-Za-z0-9_-]+$/;

function byteCompare(a, b) {
  const ea = new TextEncoder().encode(a);
  const eb = new TextEncoder().encode(b);
  const n = Math.min(ea.length, eb.length);
  for (let i = 0; i < n; i++) if (ea[i] !== eb[i]) return ea[i] - eb[i];
  return ea.length - eb.length;
}

function isControl(cp) { return (cp <= 0x08) || (cp >= 0x0B && cp <= 0x1F) || cp === 0x7F; }

/** formatter::print_string para valores (nunca "bare"). */
export function tomlString(str) {
  if (str === '') return "''";
  let control = false;
  let lineBreak = false;
  let tab = false;
  let single = false;
  for (const ch of str) {
    const cp = ch.codePointAt(0);
    if (ch === '\n') lineBreak = true;
    else if (ch === '\t') tab = true;
    else if (ch === "'") single = true;
    else if (isControl(cp) || cp === 0x85 || cp === 0x2028 || cp === 0x2029) control = true;
  }
  // allow_literal_strings, allow_multi_line_strings e allow_real_tabs_in_strings ligados
  const multi = lineBreak;
  const literal = !control && (!single || multi) && (!lineBreak || multi);
  if (literal) return multi ? `'''${str}'''` : `'${str}'`;
  const q = multi ? '"""' : '"';
  let out = '';
  for (const ch of str) {
    const cp = ch.codePointAt(0);
    if (ch === '"') out += '\\"';
    else if (ch === '\\') out += '\\\\';
    else if (ch === '\n') out += multi ? '\n' : '\\n';
    else if (ch === '\t') out += '\t';
    else if (isControl(cp)) out += '\\u' + cp.toString(16).toUpperCase().padStart(4, '0');
    else out += ch;
  }
  return q + out + q;
}

/** Número de ponto flutuante como o toml++ imprime (o mais curto que volta ao mesmo valor). */
export function tomlFloat(v) {
  if (Number.isNaN(v)) return 'nan';
  if (!Number.isFinite(v)) return v < 0 ? '-inf' : 'inf';
  let s = String(v);
  if (/e/.test(s)) {
    const [m, e] = s.split('e');
    s = `${m}e${e[0] === '-' ? '-' : '+'}${e.replace(/^[+-]/, '').padStart(2, '0')}`;
    return s;
  }
  if (!s.includes('.')) s += '.0';
  return s;
}

export function tomlKey(k) { return BARE.test(k) ? k : tomlString(k); }

/** Um valor guardado (de nativeStore) em texto TOML. */
export function tomlValue(stored) {
  switch (stored.type) {
    case 'boolean': return stored.value ? 'true' : 'false';
    case 'integer': return String(stored.value);
    case 'float': return tomlFloat(stored.value);
    default: return tomlString(stored.value);
  }
}

/**
 * Texto de uma tabela { Seção: { chave: stored } } como o toml++ imprime, sem quebra de
 * linha final (ConfigHandle.closeString devolve exatamente isso).
 */
export function formatTomlTables(tables) {
  const sections = Object.keys(tables).filter(s => Object.keys(tables[s]).length).sort(byteCompare);
  const parts = sections.map(s => {
    const keys = Object.keys(tables[s]).sort(byteCompare);
    return `[${tomlKey(s)}]\n` + keys.map(k => `${tomlKey(k)} = ${tomlValue(tables[s][k])}`).join('\n');
  });
  return parts.join('\n\n');
}

/**
 * Arquivo config/<TITLE ID>.config.toml como o core grava em SaveGameConfig (config.cc):
 * duas linhas de comentário, uma em branco, a tabela e uma quebra de linha.
 */
export function gameConfigFile(titleId, tables, extraComments = []) {
  const id = normalizeTitleId(titleId);
  if (!id) throw new Error('Title ID inválido: use 8 dígitos hexadecimais, diferente de 00000000.');
  const head = ['# Game-specific config overrides', `# Title ID: ${id}`, ...extraComments.map(c => `# ${c}`)].join('\n');
  return `${head}\n\n${formatTomlTables(tables)}\n`;
}

/** ConfigStore.perGameConfigFile: 8 dígitos hexadecimais, não 00000000, em maiúsculas. */
export function normalizeTitleId(raw) {
  const s = String(raw || '').trim();
  if (!/^[0-9a-fA-F]{8}$/.test(s) || s === '00000000') return null;
  return s.toUpperCase();
}

/* ---------------------------------------------------------------------------------------
 * Compatibilidade com o tipo da cvar no core (cvar.h, ConfigVar<T>::LoadGameConfigValue usa
 * toml::node::value<T>(): inteiro → double é aceito; double → inteiro não; string só em string).
 * ------------------------------------------------------------------------------------- */

const INT_RANGES = {
  int32: [-2147483648, 2147483647],
  uint32: [0, 4294967295],
  int64: [-Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER],
  uint64: [0, Number.MAX_SAFE_INTEGER],
};

/** O core aceita este valor guardado para uma cvar deste tipo? Devolve null ou o motivo. */
export function coreRejects(cvarType, stored) {
  switch (cvarType) {
    case 'bool': return stored.type === 'boolean' ? null : `a cvar é booleana e o valor seria ${stored.type}`;
    case 'double': return stored.type === 'float' || stored.type === 'integer' ? null : `a cvar é double e o valor seria ${stored.type}`;
    case 'string':
    case 'path': return stored.type === 'string' ? null : `a cvar é texto e o valor seria ${stored.type}`;
    default: {
      const range = INT_RANGES[cvarType];
      if (!range) return `tipo de cvar desconhecido (${cvarType})`;
      if (stored.type !== 'integer') return `a cvar é ${cvarType} e o valor seria ${stored.type}`;
      if (stored.value < range[0] || stored.value > range[1]) return `${stored.value} fora da faixa de ${cvarType}`;
      return null;
    }
  }
}
