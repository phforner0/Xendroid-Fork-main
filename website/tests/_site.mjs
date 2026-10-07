// Apoio dos testes no navegador: serve website/dist como o GitHub Pages, no mesmo caminho do
// site publicado, e abre um Chromium do Playwright. PLAYWRIGHT_CHROMIUM aponta outro executável.
import { before, after } from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import config from '../site.config.mjs';
import { startServer } from '../scripts/serve.mjs';

export const SITE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const DIST = path.join(SITE, 'dist');
export const BASE = config.basePath;

function indexPages() {
  const out = [];
  (function walk(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name === 'index.html') out.push({ path: path.relative(DIST, dir).split(path.sep).join('/'), redirect: /<meta http-equiv="refresh"/.test(fs.readFileSync(p, 'utf8')) });
    }
  })(DIST);
  return out.map(p => ({ ...p, path: p.path ? `${p.path}/` : '' })).sort((a, b) => a.path.localeCompare(b.path));
}

/** As páginas geradas, como caminhos relativos à base ('' é a página inicial; pt-br/ é o português). */
export function pages() { return indexPages().filter(p => !p.redirect).map(p => p.path); }

/** Os endereços antigos (português na raiz, antes da versão bilíngue) que só redirecionam. */
export function redirects() { return indexPages().filter(p => p.redirect).map(p => p.path); }

/** Idioma de uma página pelo caminho: pt-br/ é português, o resto é inglês. */
export const langOf = p => (p.startsWith('pt-br/') ? 'pt-BR' : 'en');

export function readJson(rel) { return JSON.parse(fs.readFileSync(path.join(DIST, rel), 'utf8')); }

/** Elementos que passam da largura da tela sem estar dentro de algo que rola ou corta. */
export const OVERFLOW_PROBE = () => {
  const W = document.documentElement.clientWidth;
  const inBox = el => {
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      if (/(auto|scroll|hidden|clip)/.test(getComputedStyle(p).overflowX)) return true;
    }
    return false;
  };
  const out = [];
  for (const el of document.body.querySelectorAll('*')) {
    const r = el.getBoundingClientRect();
    if (!r.width || !r.height || (r.right <= W + 1 && r.left >= -1)) continue;
    if (el.closest('.visually-hidden, .skip-link, dialog:not([open]), [hidden]') || inBox(el)) continue;
    out.push(`${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''}${typeof el.className === 'string' && el.className ? '.' + el.className.trim().split(/\s+/).join('.') : ''} (${Math.round(r.left)} a ${Math.round(r.right)} de ${W})`);
  }
  return out.slice(0, 8);
};

export function useSite({ basePath = BASE } = {}) {
  const site = { basePath };
  before(async () => {
    if (!fs.existsSync(path.join(DIST, 'index.html'))) throw new Error('website/dist está vazio: rode npm run build antes dos testes');
    site.server = await startServer({ port: 0, root: DIST, basePath, quiet: true });
    site.origin = `http://127.0.0.1:${site.server.address().port}`;
    site.browser = await chromium.launch(process.env.PLAYWRIGHT_CHROMIUM ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM } : {});
  });
  after(async () => {
    if (site.browser) await site.browser.close();
    if (site.server) await new Promise(r => site.server.close(r));
  });
  site.url = (p = '') => `${site.origin}${basePath}${p}`;
  /**
   * Abre uma página num contexto novo. page.problems junta exceções, erros do console e
   * respostas 4xx/5xx (a 404 esperada de uma navegação pode ser liberada com allow404).
   */
  site.open = async (p = '', opts = {}) => {
    const { width = 1280, height = 800, colorScheme = 'dark', reducedMotion = 'no-preference', allow404 = false, wait = 'networkidle', ...rest } = opts;
    const context = await site.browser.newContext({ viewport: { width, height }, colorScheme, reducedMotion, acceptDownloads: true, ...rest });
    const page = await context.newPage();
    page.problems = [];
    page.on('pageerror', e => page.problems.push(`exceção: ${e.message}`));
    page.on('console', m => {
      if (m.type() !== 'error') return;
      if (allow404 && /status of 404/.test(m.text())) return;
      page.problems.push(`console: ${m.text()}`);
    });
    page.on('response', r => {
      if (r.status() < 400) return;
      if (allow404 && r.status() === 404 && r.request().isNavigationRequest()) return;
      page.problems.push(`${r.status()} ${r.url()}`);
    });
    page.done = () => context.close();
    if (p != null) page.lastResponse = await page.goto(site.url(p), { waitUntil: wait });
    return page;
  };
  return site;
}
