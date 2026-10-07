#!/usr/bin/env node
// Confere o site gerado em dist: cada link interno, recurso (src, href, srcset, url() no CSS)
// e âncora tem que existir; nenhum caminho absoluto a partir da raiz do domínio (o site roda
// num subdiretório), exceto na 404, que usa a base configurada. Cada página declara o idioma do
// lugar onde está (pt-br/ em português, o resto em inglês) e aponta para a versão no outro idioma,
// que aponta de volta (hreflang).
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

/** Página de dist a partir de um endereço público do site (config.url + caminho). */
const pageOf = url => {
  if (!url.startsWith(config.url)) return null;
  const p = path.join(DIST, url.slice(config.url.length));
  return url.endsWith('/') ? path.join(p, 'index.html') : p;
};
const alternatesOf = src => Object.fromEntries([...src.matchAll(/<link rel="alternate" hreflang="([^"]+)" href="([^"]+)">/g)].map(m => [m[1], m[2]]));

let links = 0;
for (const file of files) {
  if (file.endsWith('.html')) {
    const src = fs.readFileSync(file, 'utf8');
    const rel = path.relative(DIST, file);
    for (const m of src.matchAll(/\s(href|src)="([^"]*)"/g)) { links++; check(file, m[2], m[1]); }
    for (const m of src.matchAll(/\ssrcset="([^"]*)"/g)) for (const part of m[1].split(',')) { links++; check(file, part.trim().split(/\s+/)[0], 'srcset'); }
    // endereços antigos (antes da versão bilíngue) só redirecionam para a página em pt-br/
    if (/<meta http-equiv="refresh"/.test(src)) {
      if (!/<html lang="pt-BR"/.test(src)) errors.push(`${rel}: redirecionamento sem lang="pt-BR"`);
      continue;
    }
    const lang = (/<html lang="([^"]+)"/.exec(src) || [])[1];
    const want = rel.startsWith('pt-br/') ? 'pt-BR' : 'en';
    if (lang !== want) errors.push(`${rel}: lang="${lang}", mas a página está em ${want === 'en' ? 'inglês (raiz)' : 'português (pt-br/)'}`);
    if (!/<title>[^<]+<\/title>/.test(src)) errors.push(`${rel}: sem <title>`);
    if (!/<meta name="description" content="[^"]+"/.test(src)) errors.push(`${rel}: sem descrição`);
    if (rel !== '404.html') {
      const alt = alternatesOf(src);
      const self = (/<link rel="canonical" href="([^"]+)">/.exec(src) || [])[1];
      if (!alt.en || !alt['pt-BR'] || !alt['x-default']) errors.push(`${rel}: falta hreflang en, pt-BR ou x-default`);
      else {
        if (alt[want] !== self) errors.push(`${rel}: o hreflang ${want} (${alt[want]}) não é o próprio endereço (${self})`);
        const other = pageOf(alt[want === 'en' ? 'pt-BR' : 'en']);
        if (!other || !fs.existsSync(other)) errors.push(`${rel}: a versão no outro idioma (${alt[want === 'en' ? 'pt-BR' : 'en']}) não existe`);
        else if (alternatesOf(fs.readFileSync(other, 'utf8'))[want] !== self) errors.push(`${rel}: ${path.relative(DIST, other)} não aponta de volta (hreflang ${want})`);
      }
    }
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
