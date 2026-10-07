#!/usr/bin/env node
// Confere o site gerado em dist: cada link interno, recurso (src, href, srcset, url() no CSS)
// e âncora tem que existir; nenhum caminho absoluto a partir da raiz do domínio (o site roda
// num subdiretório), exceto na 404, que usa a base configurada.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import config from '../site.config.mjs';

const SITE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIST = path.join(SITE, 'dist');
const errors = [];
const files = [];
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p); else files.push(p);
  }
})(DIST);

const idsCache = new Map();
function idsOf(file) {
  if (!idsCache.has(file)) {
    const ids = new Set();
    const src = fs.readFileSync(file, 'utf8');
    for (const m of src.matchAll(/\sid="([^"]+)"/g)) ids.add(m[1]);
    // telas do simulador: rotas no #hash, declaradas em data-routes
    for (const m of src.matchAll(/\sdata-routes="([^"]+)"/g)) for (const r of m[1].split(/\s+/)) ids.add(r);
    idsCache.set(file, ids);
  }
  return idsCache.get(file);
}

function check(from, raw, kind) {
  if (!raw || /^(https?:|mailto:|data:|blob:|javascript:|tel:)/.test(raw)) return;
  const url = raw.replace(/&amp;/g, '&');
  const is404 = path.basename(from) === '404.html';
  if (url.startsWith('/') && !(is404 && url.startsWith(config.basePath))) {
    errors.push(`${path.relative(DIST, from)}: caminho absoluto "${url}" (${kind})`);
    return;
  }
  const [pathPart, hash] = url.split('#');
  const clean = pathPart.split('?')[0];
  let target;
  if (clean === '') target = from;
  else if (url.startsWith('/')) target = path.join(DIST, clean.slice(config.basePath.length));
  else target = path.resolve(path.dirname(from), decodeURIComponent(clean));
  if (fs.existsSync(target) && fs.statSync(target).isDirectory()) target = path.join(target, 'index.html');
  if (!fs.existsSync(target)) { errors.push(`${path.relative(DIST, from)}: ${kind} "${url}" não existe`); return; }
  if (hash && target.endsWith('.html') && !idsOf(target).has(decodeURIComponent(hash))) {
    errors.push(`${path.relative(DIST, from)}: âncora "#${hash}" não existe em ${path.relative(DIST, target)}`);
  }
}

let links = 0;
for (const file of files) {
  if (file.endsWith('.html')) {
    const src = fs.readFileSync(file, 'utf8');
    for (const m of src.matchAll(/\s(href|src)="([^"]*)"/g)) { links++; check(file, m[2], m[1]); }
    for (const m of src.matchAll(/\ssrcset="([^"]*)"/g)) for (const part of m[1].split(',')) { links++; check(file, part.trim().split(/\s+/)[0], 'srcset'); }
    if (!/<html lang="pt-BR"/.test(src)) errors.push(`${path.relative(DIST, file)}: sem lang="pt-BR"`);
    if (!/<title>[^<]+<\/title>/.test(src)) errors.push(`${path.relative(DIST, file)}: sem <title>`);
    if (!/<meta name="description" content="[^"]+"/.test(src)) errors.push(`${path.relative(DIST, file)}: sem descrição`);
    const ids = [...src.matchAll(/\sid="([^"]+)"/g)].map(m => m[1]);
    const dup = ids.filter((id, i) => ids.indexOf(id) !== i);
    if (dup.length) errors.push(`${path.relative(DIST, file)}: ids repetidos: ${[...new Set(dup)].join(', ')}`);
  } else if (file.endsWith('.css')) {
    const src = fs.readFileSync(file, 'utf8');
    for (const m of src.matchAll(/url\(\s*["']?([^"')]+)["']?\s*\)/g)) { links++; check(file, m[1], 'url()'); }
  }
}

if (errors.length) {
  for (const e of errors) console.error(`erro: ${e}`);
  console.error(`${errors.length} problemas em ${files.length} arquivos`);
  process.exit(1);
}
console.log(`${links} links e recursos conferidos em ${files.length} arquivos, nenhum quebrado`);
