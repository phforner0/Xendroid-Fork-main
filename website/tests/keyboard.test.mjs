// Teclado e foco: o link para pular ao conteúdo, o anel de foco, o menu e a navegação da
// documentação no celular, as abas da página inicial, o tema e a busca (abrir, escolher, fechar).
import test from 'node:test';
import assert from 'node:assert/strict';
import { useSite } from './_site.mjs';

const site = useSite();
const active = page => page.evaluate(() => {
  const el = document.activeElement;
  return { tag: el.tagName.toLowerCase(), id: el.id, cls: el.className && typeof el.className === 'string' ? el.className : '', text: (el.textContent || '').trim().slice(0, 40) };
});

test('o primeiro Tab mostra "Pular para o conteúdo" (Skip to content), que leva ao conteúdo', async () => {
  const page = await site.open('docs/installation/');
  try {
    await page.keyboard.press('Tab');
    const a = await active(page);
    assert.match(a.cls, /skip-link/);
    const box = await page.locator('.skip-link').boundingBox();
    assert.ok(box && box.y >= 0 && box.x >= 0, 'o link aparece na tela quando recebe o foco');
    await page.keyboard.press('Enter');
    assert.equal((await active(page)).id, 'conteudo');
  } finally {
    await page.done();
  }
});

test('o foco do teclado sempre aparece', async () => {
  const page = await site.open('');
  try {
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press('Tab');
      const ring = await page.evaluate(() => {
        const s = getComputedStyle(document.activeElement);
        return (s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) > 0) || s.boxShadow !== 'none';
      });
      assert.ok(ring, `sem indicação de foco em ${JSON.stringify(await active(page))}`);
    }
  } finally {
    await page.done();
  }
});

test('menu do celular abre e fecha pelo teclado', async () => {
  const page = await site.open('', { width: 390, height: 844 });
  try {
    const btn = page.locator('.menu-btn');
    assert.ok(await btn.isVisible());
    await btn.focus();
    await page.keyboard.press('Enter');
    assert.equal(await btn.getAttribute('aria-expanded'), 'true');
    assert.ok(await page.locator('#site-nav a').first().isVisible());
    await page.keyboard.press('Escape');
    assert.equal(await btn.getAttribute('aria-expanded'), 'false');
    assert.match((await active(page)).cls, /menu-btn/);
  } finally {
    await page.done();
  }
});

test('a lista de páginas da documentação abre no celular', async () => {
  const page = await site.open('docs/drivers/', { width: 390, height: 844 });
  try {
    const btn = page.locator('.docs-side-toggle');
    assert.ok(await btn.isVisible());
    await btn.focus();
    await page.keyboard.press('Enter');
    assert.equal(await btn.getAttribute('aria-expanded'), 'true');
    const current = page.locator('#docs-nav [aria-current="page"]');
    assert.ok(await current.isVisible());
    assert.match(await current.innerText(), /drivers/i);
  } finally {
    await page.done();
  }
});

test('as abas da página inicial seguem as setas', async () => {
  const page = await site.open('');
  try {
    const tabs = page.locator('[role="tab"]');
    const n = await tabs.count();
    assert.ok(n >= 2, 'a página inicial tem abas');
    await tabs.first().focus();
    await page.keyboard.press('ArrowRight');
    assert.equal(await tabs.nth(1).getAttribute('aria-selected'), 'true');
    assert.equal(await tabs.first().getAttribute('aria-selected'), 'false');
    const panel = await tabs.nth(1).getAttribute('aria-controls');
    assert.ok(await page.locator(`#${panel}`).isVisible());
    await page.keyboard.press('End');
    assert.equal(await tabs.nth(n - 1).getAttribute('aria-selected'), 'true');
  } finally {
    await page.done();
  }
});

test('o tema troca entre automático, claro e escuro e fica guardado', async () => {
  const page = await site.open('docs/');
  try {
    const theme = () => page.evaluate(() => document.documentElement.dataset.theme || 'auto');
    assert.equal(await theme(), 'auto');
    await page.click('#theme-btn');
    assert.equal(await theme(), 'light');
    await page.reload({ waitUntil: 'networkidle' });
    assert.equal(await theme(), 'light', 'a escolha volta depois de recarregar');
    await page.click('#theme-btn');
    assert.equal(await theme(), 'dark');
    await page.click('#theme-btn');
    assert.equal(await theme(), 'auto');
  } finally {
    await page.done();
  }
});

test('busca: "/" abre, as setas escolhem, Enter abre e Esc fecha devolvendo o foco', async () => {
  const page = await site.open('docs/');
  try {
    await page.keyboard.press('/');
    assert.equal(await page.locator('#busca').evaluate(d => d.open), true);
    assert.equal((await active(page)).id, 'busca-q');
    await page.keyboard.type('framerate_limit');
    await page.locator('#busca-res [role="option"]').first().waitFor();
    await page.keyboard.press('ArrowDown');
    assert.equal(await page.locator('#busca-q').getAttribute('aria-activedescendant'), 'r-0');
    const href = await page.locator('#r-0').getAttribute('href');
    assert.match(href, /docs\/settings-reference\//);
    await Promise.all([page.waitForURL(/settings-reference/), page.keyboard.press('Enter')]);
    const hash = new URL(page.url()).hash.slice(1);
    if (hash) assert.ok(await page.locator(`[id="${decodeURIComponent(hash)}"]`).count(), `a âncora #${hash} existe`);

    const btn = page.locator('.search-btn');
    await btn.focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('#busca').evaluate(d => d.open), true);
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#busca').evaluate(d => d.open), false);
    assert.match((await active(page)).cls, /search-btn/);
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});
