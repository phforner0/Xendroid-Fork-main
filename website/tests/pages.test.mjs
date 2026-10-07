// Cada página gerada, nos dois idiomas e em três larguras: carrega sem erro, não passa da largura
// da tela e tem os metadados, o título e as imagens descritas que uma página publicada precisa.
// O seletor de idioma leva à mesma página no outro idioma, os endereços antigos do português
// redirecionam para pt-br/ e a 404 fala os dois idiomas.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import config from '../site.config.mjs';
import { useSite, pages, redirects, langOf, OVERFLOW_PROBE, DIST } from './_site.mjs';

const site = useSite();
const SIZES = [[360, 740], [820, 1180], [1366, 900]];

test('as páginas esperadas existem', () => {
  const list = pages();
  for (const p of ['', 'docs/', 'docs/installation/', 'docs/settings-reference/', 'docs/simulator/', 'simulator/', 'pt-br/', 'pt-br/docs/', 'pt-br/docs/instalacao/', 'pt-br/docs/referencia-de-ajustes/', 'pt-br/docs/simulador/', 'pt-br/simulador/']) assert.ok(list.includes(p), `falta a página ${p || '(inicial)'}`);
  assert.ok(fs.existsSync(path.join(DIST, '404.html')), 'falta a 404.html');
  // as duas línguas têm as mesmas páginas
  const en = list.filter(p => langOf(p) === 'en'), pt = list.filter(p => langOf(p) === 'pt-BR');
  assert.equal(en.length, pt.length, `inglês ${en.length}, português ${pt.length}`);
});

for (const p of pages()) {
  test(`página ${p || '(inicial)'}`, async () => {
    for (const [width, height] of SIZES) {
      const page = await site.open(p, { width, height });
      try {
        assert.equal(page.lastResponse.status(), 200);
        const info = await page.evaluate(() => {
          const meta = sel => (document.querySelector(sel) || {}).content || '';
          return {
            lang: document.documentElement.lang,
            title: document.title,
            description: meta('meta[name="description"]'),
            canonical: (document.querySelector('link[rel="canonical"]') || {}).href || '',
            ogUrl: meta('meta[property="og:url"]'),
            og: ['og:title', 'og:description', 'og:image', 'og:type'].filter(k => !meta(`meta[property="${k}"]`)),
            h1: [...document.querySelectorAll('h1')].filter(h => !h.closest('#app')).length,
            noAlt: [...document.images].filter(i => !i.hasAttribute('alt')).map(i => i.getAttribute('src')),
            unnamed: [...document.querySelectorAll('a[href], button')].filter(el => !el.closest('#app') && el.getClientRects().length && !(el.getAttribute('aria-label') || el.getAttribute('aria-labelledby') || el.textContent.trim() || el.getAttribute('title') || [...el.querySelectorAll('img[alt]')].some(i => i.alt.trim()))).map(el => el.outerHTML.slice(0, 80)),
            mainLandmark: !!document.querySelector('main#conteudo'),
          };
        });
        assert.equal(info.lang, langOf(p));
        assert.ok(info.title.length > 3 && info.title.includes('Xendroid+'), `título: ${info.title}`);
        assert.ok(info.description.length >= 50, `descrição curta: ${info.description}`);
        assert.ok(info.canonical.startsWith(config.url), `canonical fora do site: ${info.canonical}`);
        assert.equal(info.ogUrl, info.canonical);
        assert.deepEqual(info.og, [], 'metadados Open Graph faltando');
        assert.equal(info.h1, 1, 'a página precisa de um h1');
        assert.deepEqual(info.noAlt, [], 'imagens sem alt');
        assert.deepEqual(info.unnamed, [], 'links ou botões sem nome');
        assert.ok(info.mainLandmark);
        assert.deepEqual(await page.evaluate(OVERFLOW_PROBE), [], `passa da largura em ${width}px`);
        assert.deepEqual(page.problems, [], `erros em ${width}px`);
      } finally {
        await page.done();
      }
    }
  });
}

test('o seletor de idioma leva à mesma página no outro idioma, e volta', async () => {
  for (const [from, to] of [['', 'pt-br/'], ['docs/installation/', 'pt-br/docs/instalacao/'], ['simulator/', 'pt-br/simulador/'], ['pt-br/docs/referencia-de-ajustes/', 'docs/settings-reference/']]) {
    const page = await site.open(from);
    try {
      const sw = page.locator('[data-lang-switch]');
      assert.equal(await sw.getAttribute('lang'), langOf(to));
      await Promise.all([page.waitForURL(u => new URL(u).pathname === site.basePath + to), sw.click()]);
      assert.equal(await page.evaluate(() => document.documentElement.lang), langOf(to));
      // e de volta, pela mesma marcação
      assert.equal(new URL(await page.locator('[data-lang-switch]').evaluate(a => a.href)).pathname, site.basePath + from);
      assert.deepEqual(page.problems, []);
    } finally {
      await page.done();
    }
  }
});

test('com o navegador em português, a página em inglês oferece a versão em português uma vez', async () => {
  const page = await site.open('docs/installation/', { locale: 'pt-BR' });
  try {
    const hint = page.locator('.lang-hint');
    assert.equal(await hint.count(), 1);
    assert.equal(new URL(await hint.locator('a').evaluate(a => a.href)).pathname, `${site.basePath}pt-br/docs/instalacao/`);
    await hint.locator('button').click();
    assert.equal(await hint.count(), 0);
    await page.reload({ waitUntil: 'networkidle' });
    assert.equal(await page.locator('.lang-hint').count(), 0, 'fechado, o aviso não volta');
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
  // em inglês, ou já na página em português, não há aviso
  for (const [p, locale] of [['docs/installation/', 'en-US'], ['pt-br/docs/instalacao/', 'pt-BR']]) {
    const other = await site.open(p, { locale });
    try { assert.equal(await other.locator('.lang-hint').count(), 0, `${p} (${locale})`); } finally { await other.done(); }
  }
});

test('os endereços antigos do português levam à página em pt-br/, com a âncora', async () => {
  const list = redirects();
  assert.ok(list.includes('simulador/') && list.includes('docs/instalacao/'), `redirecionamentos: ${list.join(', ')}`);
  const page = await site.open('docs/instalacao/#baixar');
  try {
    await page.waitForURL(u => new URL(u).pathname === `${site.basePath}pt-br/docs/instalacao/`);
    assert.equal(new URL(page.url()).hash, '#baixar');
    assert.equal(await page.evaluate(() => document.documentElement.lang), 'pt-BR');
  } finally {
    await page.done();
  }
  const sim = await site.open('simulador/#drivers');
  try {
    await sim.waitForURL(u => new URL(u).pathname === `${site.basePath}pt-br/simulador/`);
    await sim.waitForFunction(() => document.getElementById('ch-screen') && document.getElementById('ch-screen').value === 'drivers');
  } finally {
    await sim.done();
  }
});

test('a 404 responde 404, explica e leva de volta', async () => {
  const page = await site.open('pagina-que-nao-existe/', { allow404: true });
  try {
    assert.equal(page.lastResponse.status(), 404);
    assert.match(await page.locator('h1').innerText(), /\S/);
    // a 404 é servida em qualquer caminho, então usa a base configurada nos links
    const home = await page.locator('main a[href]').first().getAttribute('href');
    assert.ok(home.startsWith(config.basePath), `link da 404: ${home}`);
    await page.locator('main a[href]').first().click();
    await page.waitForLoadState('networkidle');
    assert.equal(new URL(page.url()).pathname.startsWith(config.basePath), true);
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
  // num endereço de pt-br/, o português vem primeiro
  const pt = await site.open('pt-br/pagina-que-nao-existe/', { allow404: true });
  try {
    assert.equal(pt.lastResponse.status(), 404);
    assert.equal(await pt.evaluate(() => document.documentElement.lang), 'pt-BR');
    assert.match(await pt.locator('main h1').first().innerText(), /não existe/);
    assert.equal(await pt.locator('main a[href]').first().getAttribute('href'), `${config.basePath}pt-br/`);
  } finally {
    await pt.done();
  }
});

test('sitemap e robots usam o endereço publicado', () => {
  const sitemap = fs.readFileSync(path.join(DIST, 'sitemap.xml'), 'utf8');
  const locs = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1]);
  assert.deepEqual(locs.map(l => l.slice(config.url.length)).sort(), pages().slice().sort(), 'o sitemap lista as páginas, sem os redirecionamentos');
  for (const l of locs) assert.ok(l.startsWith(config.url), l);
  // cada endereço com as duas versões e o x-default (o inglês)
  for (const entry of sitemap.split('<url>').slice(1)) {
    const alt = Object.fromEntries([...entry.matchAll(/hreflang="([^"]+)" href="([^"]+)"/g)].map(m => [m[1], m[2]]));
    assert.ok(alt.en && alt['pt-BR'] && alt['x-default'] === alt.en, entry.slice(0, 120));
  }
  const robots = fs.readFileSync(path.join(DIST, 'robots.txt'), 'utf8');
  assert.match(robots, new RegExp(`Sitemap: ${config.url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}sitemap\\.xml`));
});
