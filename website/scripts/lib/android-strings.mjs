// Leitura dos recursos de texto do Android (res/values*/strings*.xml).
import fs from 'node:fs';
import path from 'node:path';

const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };

function decodeEntities(s) {
  return s.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|[a-z]+);/g, (m, e) => {
    if (e[0] === '#') return String.fromCodePoint(e[1] === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10));
    return e in ENTITIES ? ENTITIES[e] : m;
  });
}

/** Valor de um <string> como o Android o mostra: escapes, aspas e espaços tratados. */
export function androidText(raw) {
  // xliff:g e outras marcações viram só o texto interno
  let s = raw.replace(/<xliff:g[^>]*>([\s\S]*?)<\/xliff:g>/g, '$1').replace(/<\/?(b|i|u)>/g, '');
  s = decodeEntities(s);
  let out = '';
  let quoted = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === '\\') {
      const e = s[i + 1];
      const map = { n: '\n', t: '\t', "'": "'", '"': '"', '\\': '\\', '@': '@', '?': '?' };
      if (e === 'u') { out += String.fromCharCode(parseInt(s.slice(i + 2, i + 6), 16)); i += 5; continue; }
      out += e in map ? map[e] : e;
      i++;
      continue;
    }
    if (c === '"') { quoted = !quoted; continue; }
    if (!quoted && /\s/.test(c)) {
      if (!out.endsWith(' ')) out += ' ';
      continue;
    }
    out += c;
  }
  return out.trim();
}

/** Lê todos os <string>/<plurals> de uma pasta values*. */
export function readStringsDir(dir) {
  const strings = new Map();
  const plurals = new Map();
  const files = fs.readdirSync(dir).filter(f => /^strings.*\.xml$/.test(f)).sort();
  for (const f of files) {
    const xml = fs.readFileSync(path.join(dir, f), 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    for (const m of xml.matchAll(/<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
      strings.set(m[1], { value: androidText(m[3]), file: f });
    }
    for (const m of xml.matchAll(/<string\s+name="([^"]+)"[^>]*\/>/g)) strings.set(m[1], { value: '', file: f });
    for (const m of xml.matchAll(/<plurals\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
      const items = {};
      for (const it of m[2].matchAll(/<item\s+quantity="([a-z]+)"\s*>([\s\S]*?)<\/item>/g)) items[it[1]] = androidText(it[2]);
      plurals.set(m[1], { items, file: f });
    }
  }
  return { strings, plurals };
}

/** Textos em pt-BR com o inglês como reserva, como o Android resolve num aparelho em português. */
export function readAppStrings(resDir) {
  const en = readStringsDir(path.join(resDir, 'values'));
  const pt = readStringsDir(path.join(resDir, 'values-pt-rBR'));
  return {
    /** Texto mostrado num aparelho em pt-BR e o idioma em que ele está. */
    get(name) {
      if (pt.strings.has(name)) return { text: pt.strings.get(name).value, lang: 'pt-BR' };
      if (en.strings.has(name)) return { text: en.strings.get(name).value, lang: 'en' };
      return null;
    },
    has(name) { return pt.strings.has(name) || en.strings.has(name); },
    en, pt,
  };
}

/** Formata "%1$s"/"%1$d"/"%s" como String.format faria com os argumentos dados. */
export function formatAndroid(template, ...args) {
  let next = 0;
  return template.replace(/%(?:(\d+)\$)?([sd%])/g, (m, idx, conv) => {
    if (conv === '%') return '%';
    const v = idx ? args[Number(idx) - 1] : args[next++];
    return String(v);
  });
}
