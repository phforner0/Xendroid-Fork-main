// Simulador: cada tela abre nos dois modos e nas duas orientações; o teclado só age dentro do
// aparelho e Tab sai dele; e o arquivo de configuração de um jogo sai no formato que o núcleo
// da versão escolhida aceita (cada chave existe, na seção certa e com o tipo certo), com o
// Title ID conferido, o mesmo texto ao baixar e o arquivo global também válido.
import test from 'node:test';
import assert from 'node:assert/strict';
import { parse } from 'smol-toml';
import { useSite, readJson } from './_site.mjs';

const site = useSite();
// o simulador desenha a arte de exemplo antes da primeira tela (ready, em core.js)
const ready = page => page.waitForFunction(() => typeof ready !== 'undefined' && ready === true && document.querySelector('#app').children.length > 0);

test('cada tela abre nos modos toque e controle, deitado e em pé', async () => {
  const page = await site.open('simulador/');
  try {
    await ready(page);
    const routes = (await page.locator('#sim').getAttribute('data-routes')).split(/\s+/);
    assert.ok(routes.length >= 20, `telas: ${routes.length}`);
    for (const [mode, orient] of [['b', 'land'], ['c', 'land'], ['b', 'port'], ['c', 'port']]) {
      await page.click(`[data-cact="mode"][data-v="${mode}"]`);
      await page.click(`[data-cact="orient"][data-v="${orient}"]`);
      for (const r of routes) {
        await page.evaluate(h => { location.hash = h; }, r);
        await page.waitForTimeout(60);
        const state = await page.evaluate(() => ({ cls: document.getElementById('app').className, size: document.getElementById('app').innerHTML.length, screen: document.getElementById('ch-screen').value }));
        assert.equal(state.screen, r, `a barra mostra a tela ${r}`);
        assert.ok(state.cls.includes(`mode-${mode}`), `${r}: modo ${mode}`);
        assert.ok(state.size > 400, `${r} (${mode}, ${orient}) está vazia`);
        if (orient === 'port') {
          // em pé, nenhuma área da tela fica espremida numa coluna (larguras de layout, sem a escala do aparelho)
          const narrow = await page.evaluate(() => {
            const W = document.getElementById('app').clientWidth;
            return [...document.querySelectorAll('#app .shell > *, #app .b-lib > *, #app .shell .body:not(.single) > *')]
              .filter(el => getComputedStyle(el).display !== 'none' && el.offsetWidth > 0 && el.offsetWidth < W * 0.9)
              .map(el => `${el.className || el.tagName.toLowerCase()} com ${el.offsetWidth}px de ${W}px`);
          });
          assert.deepEqual(narrow, [], `${r} em pé (${mode})`);
        }
      }
      const box = await page.locator('#screen').boundingBox();
      assert.equal(box.height > box.width, orient === 'port', `orientação ${orient}`);
    }
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});

test('o teclado só age dentro do aparelho, e Tab entra e sai dele numa parada só', async () => {
  const page = await site.open('simulador/#library');
  try {
    await ready(page);
    // fora do aparelho: M e as setas não mexem no simulador, e "/" abre a busca do site
    await page.locator('#ch-help').click();
    await page.keyboard.press('m');
    assert.equal(await page.locator('#app .guide').count(), 0);
    await page.keyboard.press('/');
    assert.equal(await page.locator('#busca').evaluate(d => d.open), true);
    await page.keyboard.press('Escape');
    // dentro: M abre o menu, Esc fecha, "/" não abre a busca do site
    await page.locator('#screen').focus();
    await page.keyboard.press('m');
    assert.equal(await page.locator('#app .guide').count(), 1);
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#app .guide').count(), 0);
    await page.keyboard.press('ArrowRight');
    assert.ok(await page.evaluate(() => document.getElementById('app').contains(document.activeElement)), 'as setas levam o foco para dentro do app');
    await page.keyboard.press('/');
    assert.equal(await page.locator('#busca').evaluate(d => d.open), false);
    // Tab sai do aparelho; Shift+Tab volta ao aparelho, e não ao último botão dele
    await page.keyboard.press('Tab');
    assert.equal(await page.evaluate(() => document.getElementById('screen').contains(document.activeElement)), false, 'Tab sai do aparelho');
    await page.keyboard.press('Shift+Tab');
    assert.equal(await page.evaluate(() => document.activeElement.id), 'screen');
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});

/** Muda, no jogo aberto, todos os ajustes que vão para o arquivo; devolve as chaves mudadas. */
const CHANGE_ALL = () => {
  const g = curGame(), changed = [];
  for (const d of SET) {
    if (d.k.startsWith('@') || d.drv || d.ty === 'action') continue;
    let v;
    if (d.ty === 'bool') v = !d.def;
    else if (d.ty === 'list') { const o = d.o.find(x => String(x[0]) !== String(d.def)); if (!o) continue; v = o[0]; }
    else if (d.ty === 'int') v = d.max != null && d.def !== d.max ? d.max : d.min != null && d.def !== d.min ? d.min : d.def + 1;
    else if (d.ty === 'text') v = 'texto de teste';
    else continue;
    setVal(g, d.k, v);
    changed.push(d.k);
  }
  render();
  return changed;
};

/** Confere um arquivo contra as cvars: chave conhecida, seção certa, tipo aceito pelo núcleo. */
function checkToml(text, cvars) {
  const doc = parse(text, { integersAsBigInt: true });
  const keys = [];
  for (const [section, table] of Object.entries(doc)) {
    assert.equal(typeof table, 'object', `[${section}] é uma tabela`);
    for (const [name, value] of Object.entries(table)) {
      const cv = cvars[name];
      assert.ok(cv, `${section}.${name}: o núcleo desta versão não tem essa cvar`);
      assert.equal(cv[0], section, `${name}: o núcleo lê em [${cv[0]}]`);
      const type = cv[1];
      if (type === 'bool') assert.equal(typeof value, 'boolean', `${name}: bool`);
      else if (type === 'string' || type === 'path') assert.equal(typeof value, 'string', `${name}: texto`);
      else if (type === 'double') assert.ok(typeof value === 'number' || typeof value === 'bigint', `${name}: número`);
      else {
        assert.equal(typeof value, 'bigint', `${name}: inteiro (${type})`);
        const [lo, hi] = { int32: [-2147483648n, 2147483647n], uint32: [0n, 4294967295n], int64: [-(2n ** 63n), 2n ** 63n - 1n], uint64: [0n, 2n ** 64n - 1n] }[type] || [];
        assert.ok(lo != null, `${name}: tipo ${type} desconhecido`);
        assert.ok(value >= lo && value <= hi, `${name}: ${value} fora de ${type}`);
      }
      keys.push(`${section}.${name}`);
    }
  }
  return keys;
}

test('o arquivo de um jogo sai no formato que o núcleo aceita', async () => {
  const page = await site.open('simulador/#game');
  try {
    await ready(page);
    const changed = await page.evaluate(CHANGE_ALL);
    assert.ok(changed.length > 100, `ajustes mudados: ${changed.length}`);
    const { cvars, id, notes } = await page.evaluate(() => ({ cvars: VERSION.cvars, id: curGame().id }));
    await page.click('[data-k="bs-set"]');
    await page.click('[data-k="b-toml"]');
    const text = await page.locator('#toml-pre').innerText();
    assert.match(text, new RegExp(`^# Game-specific config overrides\\n# Title ID: ${id}\\n`));
    const keys = checkToml(text, cvars);
    // o que muda e não vai para o arquivo aparece explicado embaixo dele
    const outside = await page.locator('.tnotes li').allInnerTexts();
    for (const k of changed) assert.ok(keys.includes(k) || outside.some(x => x.startsWith(k)), `${k} não está no arquivo nem explicado fora dele`);
    assert.ok(keys.length > 100, `chaves no arquivo: ${keys.length}`);
    void notes;

    // Title ID: inválido avisa; minúsculas valem e viram maiúsculas
    for (const bad of ['XYZ', '00000000', '1234567', 'G2345678']) {
      await page.fill('#toml-id', bad);
      assert.match(await page.locator('#toml-body [role="alert"]').innerText(), /Title ID inválido/, bad);
      assert.equal(await page.locator('#toml-pre').count(), 0);
    }
    await page.fill('#toml-id', 'abcdef12');
    assert.match(await page.locator('#toml-pre').innerText(), /# Title ID: ABCDEF12/);

    // baixar dá o mesmo texto, com o nome que o app usa
    const [download] = await Promise.all([page.waitForEvent('download'), page.click('[data-k="m-dl"]')]);
    assert.equal(download.suggestedFilename(), 'ABCDEF12.config.toml');
    const chunks = [];
    for await (const c of await download.createReadStream()) chunks.push(c);
    const file = Buffer.concat(chunks).toString('utf8');
    assert.equal(file.trim(), (await page.locator('#toml-pre').innerText()).trim());
    checkToml(file, cvars);
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});

test('um jogo sem ajustes próprios não gera arquivo, e o arquivo global é válido', async () => {
  const page = await site.open('simulador/#game');
  try {
    await ready(page);
    await page.evaluate(() => { OV[curGame().id] = {}; render(); });
    await page.click('[data-k="bs-set"]');
    await page.click('[data-k="b-toml"]');
    assert.equal(await page.locator('#toml-pre').count(), 0);
    assert.match(await page.locator('#toml-body').innerText(), /não grava arquivo/);
    await page.click('[data-k="m-ok"]');

    await page.evaluate(h => { location.hash = h; }, 'settings');
    await page.waitForTimeout(100);
    const cvars = await page.evaluate(() => { S.editScope = 'global'; setVal(null, 'GPU.framerate_limit', DEF['GPU.framerate_limit'].o ? DEF['GPU.framerate_limit'].o.find(o => String(o[0]) !== String(DEF['GPU.framerate_limit'].def))[0] : 30); S.modal = 'toml-global'; S.mp = {}; render(); return VERSION.cvars; });
    const text = await page.locator('#toml-pre').innerText();
    assert.ok(checkToml(text, cvars).includes('GPU.framerate_limit'));
    assert.deepEqual(page.problems, []);
  } finally {
    await page.done();
  }
});

test('os dados do simulador são os do build: ajustes, jogos do README e a release estável', async () => {
  const page = await site.open('simulador/');
  try {
    await ready(page);
    const est = readJson('dados/estavel.json');
    const rel = readJson('dados/relatorio.json').estavel;
    const sim = await page.evaluate(() => ({
      settings: XDR_SIM.canais.estavel.ajustes.length,
      keys: XDR_SIM.canais.estavel.ajustes.map(d => d.k),
      games: GAMES.map(g => g.id),
      measured: Object.keys(XDR_SIM.medidas),
      upd: XDR_SIM.atualizacao && XDR_SIM.atualizacao.oferecida,
      intro: document.querySelector('.sim-intro .lead').textContent,
    }));
    assert.equal(sim.settings, est.ajustes.length);
    assert.deepEqual(sim.keys.slice().sort(), est.ajustes.map(a => a.key.replace('|', '.')).sort());
    assert.match(sim.intro, new RegExp(`Os ${est.ajustes.length} ajustes`));
    for (const id of sim.measured) assert.ok(sim.games.includes(id), `jogo medido ${id} fora da biblioteca de exemplo`);
    assert.ok(sim.upd, 'o atualizador tem a release estável');
    assert.equal(sim.upd.tag, rel.tag);
    assert.equal(sim.upd.build, rel.build);
    assert.equal(sim.upd.apk.bytes, rel.apk.size);
    assert.equal(sim.upd.apk.sha256, rel.apk.sha256);
    assert.ok(sim.upd.notas.length > 0, 'o resumo da release foi lido');
  } finally {
    await page.done();
  }
});
