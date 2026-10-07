// Cada página gerada, em três larguras: carrega sem erro, não passa da largura da tela e tem
// os metadados, o título e as imagens descritas que uma página publicada precisa.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import config from '../site.config.mjs';
import { useSite, pages, OVERFLOW_PROBE, DIST } from './_site.mjs';

const site = useSite();
const SIZES = [[360, 740], [820, 1180], [1366, 900]];

test('as páginas esperadas existem', () => {
  const list = pages();
  for (const p of ['', 'docs/', 'docs/instalacao/', 'docs/referencia-de-ajustes/', 'docs/simulador/', 'simulador/']) assert.ok(list.includes(p), `falta a página ${p || '(inicial)'}`);
  assert.ok(fs.existsSync(path.join(DIST, '404.html')), 'falta a 404.html');
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
        assert.equal(info.lang, 'pt-BR');
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
});

test('sitemap e robots usam o endereço publicado', () => {
  const sitemap = fs.readFileSync(path.join(DIST, 'sitemap.xml'), 'utf8');
  const locs = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1]);
  assert.ok(locs.length >= pages().length, 'o sitemap não lista todas as páginas');
  for (const l of locs) assert.ok(l.startsWith(config.url), l);
  const robots = fs.readFileSync(path.join(DIST, 'robots.txt'), 'utf8');
  assert.match(robots, new RegExp(`Sitemap: ${config.url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}sitemap\\.xml`));
});
