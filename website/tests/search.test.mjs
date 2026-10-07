// Busca: acha páginas e ajustes pelo nome, pela chave do TOML e sem acento; diz quando não há
// nada; e cada resultado leva a uma página e âncora que existem.
import test from 'node:test';
import assert from 'node:assert/strict';
import { useSite } from './_site.mjs';

const site = useSite();

async function search(page, q) {
  await page.evaluate(() => { const d = document.getElementById('busca'); if (!d.open) d.showModal(); });
  await page.fill('#busca-q', q);
  await page.waitForFunction(() => document.querySelector('#busca-res [role="option"], #busca-res .search-empty'));
  await page.waitForTimeout(120);
  return page.$$eval('#busca-res [role="option"]', els => els.map(a => ({ href: a.getAttribute('href'), page: a.querySelector('.r-page').textContent, title: a.querySelector('.r-title').textContent })));
}

const CASES = [
  ['framerate_limit', /docs\/referencia-de-ajustes\/#/, 'chave do TOML'],
  ['Limite de FPS', /docs\/(referencia-de-ajustes|ajustes|desempenho)\//, 'nome do ajuste'],
  ['turnip', /docs\/drivers\//, 'página'],
  ['resolucao', /docs\/(referencia-de-ajustes|ajustes)\//, 'sem acento'],
  ['sha-256', /docs\/instalacao\//, 'conferir o APK'],
  ['Title ID', /docs\//, 'termo do app'],
];

test('cada busca acha o lugar certo', async () => {
  const page = await site.open('docs/');
  try {
    for (const [q, want, why] of CASES) {
      const hits = await search(page, q);
      assert.ok(hits.length, `nada para "${q}" (${why})`);
      assert.ok(hits.slice(0, 5).some(h => want.test(h.href)), `"${q}" (${why}): ${hits.slice(0, 5).map(h => h.href).join(', ')}`);
    }
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});

test('sem resultado, a busca diz isso', async () => {
  const page = await site.open('');
  try {
    assert.deepEqual(await search(page, 'qzxwvjk'), []);
    assert.match(await page.locator('#busca-res .search-empty').innerText(), /Nada encontrado/);
  } finally {
    await page.done();
  }
});

test('os resultados levam a páginas e âncoras que existem', async () => {
  const page = await site.open('docs/');
  try {
    const seen = new Set();
    for (const q of ['vulkan', 'perfil', 'controle', 'logs', 'zar', 'patch']) for (const h of await search(page, q)) seen.add(h.href);
    const check = await site.open(null);
    try {
      for (const href of seen) {
        const url = new URL(href, page.url());
        await check.goto('about:blank');
        const res = await check.goto(url.href, { waitUntil: 'domcontentloaded' });
        assert.equal(res.status(), 200, href);
        const id = decodeURIComponent(url.hash.slice(1));
        if (id) assert.ok(await check.locator(`[id="${id}"]`).count(), `${href}: a âncora não existe`);
      }
    } finally {
      await check.done();
    }
  } finally {
    await page.done();
  }
});
