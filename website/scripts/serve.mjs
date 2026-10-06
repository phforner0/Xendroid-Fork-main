#!/usr/bin/env node
// Servidor local que imita o GitHub Pages: serve website/dist sob o mesmo caminho do site
// publicado (ex.: /Xendroid-Plus/), resolve pastas para index.html e responde a 404.html
// com status 404 para o que não existe.
//
//   node scripts/serve.mjs [--port 4173] [--base /Xendroid-Plus/] [--dir dist]
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import config from '../site.config.mjs';

const SITE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const arg = (name, def) => { const i = process.argv.indexOf(name); return i >= 0 ? process.argv[i + 1] : def; };
const port = Number(arg('--port', 4173));
const base = arg('--base', config.basePath).replace(/\/?$/, '/');
const dir = path.resolve(SITE, arg('--dir', 'dist'));

const TYPES = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp',
  '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8', '.xml': 'application/xml; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8', '.toml': 'text/plain; charset=utf-8',
};

export function startServer({ port: p = port, root = dir, basePath = base, quiet = false } = {}) {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    let pathname = decodeURIComponent(url.pathname);
    const send = (file, status = 200) => {
      res.writeHead(status, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
      fs.createReadStream(file).pipe(res);
    };
    const notFound = () => send(path.join(root, '404.html'), 404);
    if (pathname === basePath.replace(/\/$/, '')) { res.writeHead(301, { Location: basePath }); res.end(); return; }
    if (!pathname.startsWith(basePath)) return notFound();
    const relPath = pathname.slice(basePath.length);
    let file = path.join(root, relPath);
    if (!file.startsWith(root)) return notFound();
    if (fs.existsSync(file) && fs.statSync(file).isDirectory()) {
      if (!pathname.endsWith('/')) { res.writeHead(301, { Location: `${pathname}/${url.search}` }); res.end(); return; }
      file = path.join(file, 'index.html');
    }
    if (!fs.existsSync(file)) return notFound();
    send(file);
  });
  return new Promise(resolve => server.listen(p, '127.0.0.1', () => {
    if (!quiet) console.log(`site em http://127.0.0.1:${p}${basePath}`);
    resolve(server);
  }));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) startServer();
