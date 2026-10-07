// Acessibilidade automática (axe, regras WCAG 2.1 A e AA, contraste incluído) em cada página,
// nos temas claro e escuro, no celular e no computador; e no simulador, em cada tela do app.
import test from 'node:test';
import assert from 'node:assert/strict';
import { AxeBuilder } from '@axe-core/playwright';
import { useSite, pages } from './_site.mjs';

const site = useSite();
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const summary = r => r.violations.map(v => `${v.id} (${v.impact}): ${v.nodes.length}× — ${v.nodes.slice(0, 3).map(n => n.target.join(' ')).join(' | ')}`);

async function axe(page) { return summary(await new AxeBuilder({ page }).withTags(TAGS).analyze()); }

for (const p of pages()) {
  test(`axe em ${p || '(inicial)'}`, async () => {
    for (const [colorScheme, width, height] of [['light', 1280, 800], ['dark', 1280, 800], ['dark', 390, 844]]) {
      const page = await site.open(p, { colorScheme, width, height });
      try {
        assert.deepEqual(await axe(page), [], `${colorScheme}, ${width}px`);
      } finally {
        await page.done();
      }
    }
  });
}

test('axe na 404', async () => {
  const page = await site.open('nada-aqui/', { allow404: true });
  try { assert.deepEqual(await axe(page), []); } finally { await page.done(); }
});

test('axe nas telas do simulador, nos modos toque e controle', async () => {
  const page = await site.open('simulador/', { colorScheme: 'dark' });
  try {
    const routes = (await page.locator('#sim').getAttribute('data-routes')).split(/\s+/);
    for (const mode of ['b', 'c']) {
      await page.click(`[data-cact="mode"][data-v="${mode}"]`);
      for (const r of routes) {
        await page.evaluate(h => { location.hash = h; }, r);
        await page.waitForTimeout(150);
        assert.deepEqual(await axe(page), [], `tela ${r}, modo ${mode === 'b' ? 'toque' : 'controle'}`);
      }
    }
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});
