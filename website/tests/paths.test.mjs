// Caminhos: o site gerado para /Xendroid-Plus/ funciona em outro subdiretório (todos os links
// são relativos), cada página abre direto pelo endereço e volta igual ao recarregar, e os links
// diretos para seções e telas do simulador funcionam, nos dois idiomas.
import test from 'node:test';
import assert from 'node:assert/strict';
import { useSite, pages } from './_site.mjs';

const moved = useSite({ basePath: '/outro/caminho/' });

test('em outro subdiretório, cada página carrega sem recurso quebrado', async () => {
  for (const p of pages()) {
    const page = await moved.open(p);
    try {
      assert.equal(page.lastResponse.status(), 200, p);
      // todo link interno continua dentro do subdiretório
      const outside = await page.$$eval('a[href]', (as, base) => as.map(a => a.href).filter(h => h.startsWith(location.origin) && !new URL(h).pathname.startsWith(base)), moved.basePath);
      assert.deepEqual(outside, [], `${p}: links saem do subdiretório`);
      assert.deepEqual(page.problems, [], p);
    } finally {
      await page.done();
    }
  }
});

for (const doc of ['docs/installation/', 'pt-br/docs/instalacao/']) test(`um link direto para uma seção de ${doc} abre nela, e recarregar mantém a seção`, async () => {
  const probe = await moved.open(doc);
  let id;
  try {
    id = await probe.locator('main h2[id]').nth(1).getAttribute('id');
  } finally {
    await probe.done();
  }
  // numa aba nova, como quem recebe o link
  const page = await moved.open(`${doc}#${id}`);
  try {
    await page.evaluate(() => document.fonts.ready);
    const top = await page.locator(`[id="${id}"]`).evaluate(el => el.getBoundingClientRect().top);
    assert.ok(top >= 0 && top < 200, `a seção #${id} abre no topo (top ${top})`);
    // ao recarregar, a rolagem é do navegador (ele pode restaurar a anterior); o alvo do endereço continua o mesmo
    await page.reload({ waitUntil: 'networkidle' });
    assert.equal(await page.evaluate(() => (document.querySelector(':target') || {}).id), id);
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});

for (const sim of ['simulator/', 'pt-br/simulador/']) test(`o endereço do simulador (${sim}) guarda a tela, e um link direto abre nela`, async () => {
  const page = await moved.open(`${sim}#drivers`);
  try {
    await page.waitForFunction(() => document.querySelector('#app .topbar h1, #app .c-ghead h1'));
    assert.match(await page.locator('#ch-screen').inputValue(), /drivers/);
    await page.selectOption('#ch-screen', 'about');
    await page.waitForFunction(() => location.hash === '#about');
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForFunction(() => typeof ready !== 'undefined' && ready === true);
    assert.equal(await page.locator('#ch-screen').inputValue(), 'about');
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});
