// Dados por jogo tirados do repositório: patches (patches/xenia-canary/patches), ajustes que
// o core aplica sozinho (game_quirks.cc), notas do GAME_COMPAT.md e as medições do README.
import fs from 'node:fs';
import path from 'node:path';
import { parse as parseToml } from 'smol-toml';
import { readQuirks } from '../lib/cpp.mjs';

export const GAME_SOURCES = {
  patches: 'patches/xenia-canary/patches',
  quirks: 'emulator-core/src/main/cpp/xenia/src/xenia/game_quirks.cc',
  compat: 'GAME_COMPAT.md',
  readmePt: 'README.pt-BR.md',
};

/** Rótulo de versão no nome do arquivo: "Halo 4 (TU10).patch.toml" → "TU10". */
function fileLabel(base) {
  const m = /\(([^()]+)\)\s*$/.exec(base);
  return m ? m[1] : null;
}

function asList(v) { return v == null ? [] : Array.isArray(v) ? v : [v]; }

/** Lê um arquivo de patch; se o TOML não for válido, cai para uma leitura por linhas. */
function readPatchFile(file) {
  const text = fs.readFileSync(file, 'utf8');
  try {
    const t = parseToml(text);
    return {
      ok: true,
      titleName: t.title_name || null,
      titleId: t.title_id ? String(t.title_id).toUpperCase() : null,
      hashes: asList(t.hash).map(String),
      mediaIds: asList(t.media_id).map(String),
      patches: asList(t.patch).map(p => ({ name: p.name || '', desc: p.desc || '', author: asList(p.author).join(', '), enabled: !!p.is_enabled })),
    };
  } catch (e) {
    const get = k => (new RegExp(`^\\s*${k}\\s*=\\s*"([^"]*)"`, 'm').exec(text) || [])[1] || null;
    const patches = [...text.matchAll(/^\s*name\s*=\s*"([^"]*)"/gm)].map(m => ({ name: m[1], desc: '', author: '', enabled: false }));
    return { ok: false, error: e.message.split('\n')[0], titleName: get('title_name'), titleId: (get('title_id') || '').toUpperCase() || null, hashes: [], mediaIds: [], patches };
  }
}

export function extractGames(root) {
  const problems = [];
  const dir = path.join(root, GAME_SOURCES.patches);
  const byId = new Map();
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.patch.toml')).sort();
  for (const f of files) {
    const m = /^([0-9A-Fa-f]{8}) - (.+)\.patch\.toml$/.exec(f);
    if (!m) { problems.push({ level: 'warn', msg: `patch com nome fora do padrão "<TITLEID> - <nome>.patch.toml": ${f}` }); continue; }
    const id = m[1].toUpperCase();
    const p = readPatchFile(path.join(dir, f));
    if (!p.ok) problems.push({ level: 'warn', msg: `${f}: TOML inválido (${p.error}); só os nomes dos patches foram lidos` });
    if (p.titleId && p.titleId !== id) problems.push({ level: 'warn', msg: `${f}: title_id ${p.titleId} difere do nome do arquivo` });
    const g = byId.get(id) || { titleId: id, names: [], files: [] };
    if (!g.names.includes(m[2].replace(/\s*\([^()]*\)\s*$/, ''))) g.names.push(m[2].replace(/\s*\([^()]*\)\s*$/, ''));
    g.files.push({ file: f, label: fileLabel(m[2]), titleName: p.titleName, hashes: p.hashes, mediaIds: p.mediaIds, patches: p.patches });
    byId.set(id, g);
  }

  // ajustes do core por jogo
  const quirksFile = path.join(root, GAME_SOURCES.quirks);
  const quirks = fs.existsSync(quirksFile) ? readQuirks(fs.readFileSync(quirksFile, 'utf8'), GAME_SOURCES.quirks) : [];
  if (!fs.existsSync(quirksFile)) problems.push({ level: 'warn', msg: `${GAME_SOURCES.quirks} não existe nesta versão` });

  // GAME_COMPAT.md: seções "### Nome — `TITLEID`"
  const compatNotes = [];
  const compatFile = path.join(root, GAME_SOURCES.compat);
  if (fs.existsSync(compatFile)) {
    const md = fs.readFileSync(compatFile, 'utf8');
    const lines = md.split('\n');
    lines.forEach((l, i) => {
      const m = /^###\s+(.+?)\s+[—-]\s+`([0-9A-Fa-f]{8})`/.exec(l);
      if (m) compatNotes.push({ titleId: m[2].toUpperCase(), name: m[1].trim(), line: i + 1, anchor: m[0] });
    });
  }

  // medições do README em pt-BR (tabela da seção "## Desempenho")
  const perf = readPerfTable(path.join(root, GAME_SOURCES.readmePt));

  return {
    patches: [...byId.values()].sort((a, b) => a.titleId.localeCompare(b.titleId)),
    patchFileCount: files.length,
    quirks,
    compatNotes,
    perf,
    problems,
  };
}

/** Tabela de medições do README.pt-BR.md, com o parágrafo de contexto que a precede. */
export function readPerfTable(file) {
  if (!fs.existsSync(file)) return null;
  const md = fs.readFileSync(file, 'utf8');
  const head = /^## Desempenho\s*$/m.exec(md);
  if (!head) return null;
  const start = head.index;
  const section = md.slice(start, md.indexOf('\n## ', start + 5) < 0 ? undefined : md.indexOf('\n## ', start + 5));
  const lines = section.split('\n');
  const tableAt = lines.findIndex(l => l.trim().startsWith('|'));
  if (tableAt < 0) return null;
  const context = lines.slice(1, tableAt).join(' ').replace(/\s+/g, ' ').trim();
  const rows = [];
  let header = null;
  for (let i = tableAt; i < lines.length && lines[i].trim().startsWith('|'); i++) {
    const cells = lines[i].trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim());
    if (!header) { header = cells; continue; }
    if (cells.every(c => /^:?-+:?$/.test(c))) continue;
    rows.push(Object.fromEntries(header.map((h, k) => [h, cells[k] ?? ''])));
  }
  const after = lines.slice(tableAt + rows.length + 2).join(' ').replace(/\s+/g, ' ').trim();
  return { context, header, rows, after, source: 'README.pt-BR.md' };
}
