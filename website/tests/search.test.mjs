// Busca, nos dois idiomas (um índice para cada): acha páginas e ajustes pelo nome, pela chave do
// TOML e sem acento; diz quando não há nada; e cada resultado leva a uma página e âncora que existem.
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

const CASES = {
  'docs/': [
    ['framerate_limit', /^[^]*\/docs\/settings-reference\/#setting-/, 'chave do TOML'],
    ['Frame rate limit', /\/docs\/(settings-reference|settings|performance)\//, 'nome do ajuste'],
    ['Limite de FPS', /\/docs\/settings-reference\/#/, 'o nome em português também acha o ajuste'],
    ['turnip', /\/docs\/drivers\//, 'página'],
    ['resolution', /\/docs\/(settings-reference|settings)\//, 'termo'],
    ['sha-256', /\/docs\/installation\//, 'conferir o APK'],
    ['Title ID', /\/docs\//, 'termo do app'],
  ],
  'pt-br/docs/': [
    ['framerate_limit', /\/pt-br\/docs\/referencia-de-ajustes\/#ajuste-/, 'chave do TOML'],
    ['Limite de FPS', /\/pt-br\/docs\/(referencia-de-ajustes|ajustes|desempenho)\//, 'nome do ajuste'],
    ['turnip', /\/pt-br\/docs\/drivers\//, 'página'],
    ['resolucao', /\/pt-br\/docs\/(referencia-de-ajustes|ajustes)\//, 'sem acento'],
    ['sha-256', /\/pt-br\/docs\/instalacao\//, 'conferir o APK'],
    ['Title ID', /\/pt-br\/docs\//, 'termo do app'],
  ],
};

for (const [from, cases] of Object.entries(CASES)) test(`cada busca acha o lugar certo (${from})`, async () => {
  const page = await site.open(from);
  try {
    for (const [q, want, why] of cases) {
      const hits = await search(page, q);
      assert.ok(hits.length, `nada para "${q}" (${why})`);
      assert.ok(hits.slice(0, 5).some(h => want.test(h.href)), `"${q}" (${why}): ${hits.slice(0, 5).map(h => h.href).join(', ')}`);
    }
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});

test('sem resultado, a busca diz isso, no idioma da página', async () => {
  for (const [from, msg] of [['', /Nothing found/], ['pt-br/', /Nada encontrado/]]) {
    const page = await site.open(from);
    try {
      assert.deepEqual(await search(page, 'qzxwvjk'), []);
      assert.match(await page.locator('#busca-res .search-empty').innerText(), msg);
    } finally {
      await page.done();
    }
  }
});

for (const [from, words] of [['docs/', ['vulkan', 'profile', 'controller', 'logs', 'zar', 'patch']], ['pt-br/docs/', ['vulkan', 'perfil', 'controle', 'logs', 'zar', 'patch']]]) test(`os resultados levam a páginas e âncoras que existem (${from})`, async () => {
  const page = await site.open(from);
  try {
    const seen = new Set();
    for (const q of words) for (const h of await search(page, q)) {
      seen.add(h.href);
      // os resultados ficam no idioma da página
      assert.equal(new URL(h.href, page.url()).pathname.includes('/pt-br/'), from.startsWith('pt-br/'), h.href);
    }
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
