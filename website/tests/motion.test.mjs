// Movimento reduzido: com "reduzir movimento" ligado no sistema, nada anima nem desliza, no
// site e no simulador.
import test from 'node:test';
import assert from 'node:assert/strict';
import { useSite } from './_site.mjs';

const site = useSite();

const MOVING = () => {
  const secs = v => v.split(',').map(x => (x.trim().endsWith('ms') ? parseFloat(x) / 1000 : parseFloat(x)));
  const out = [];
  for (const el of document.querySelectorAll('*')) {
    for (const pseudo of [null, '::before', '::after']) {
      const s = getComputedStyle(el, pseudo);
      const anim = s.animationName !== 'none' && secs(s.animationDuration).some(d => d > 0.02) && s.animationPlayState === 'running';
      const trans = secs(s.transitionDuration).some(d => d > 0.02) && s.transitionProperty !== 'none';
      if (anim || trans) out.push(`${el.tagName.toLowerCase()}.${String(el.className).split(' ')[0]}${pseudo || ''}: ${anim ? 'animação ' + s.animationName : 'transição ' + s.transitionProperty}`);
    }
  }
  return { moving: out.slice(0, 8), scroll: getComputedStyle(document.documentElement).scrollBehavior };
};

for (const p of ['', 'docs/interface/', 'simulador/']) {
  test(`nada se move em ${p || '(inicial)'} com movimento reduzido`, async () => {
    const page = await site.open(p, { reducedMotion: 'reduce' });
    try {
      await page.waitForTimeout(300);
      const r = await page.evaluate(MOVING);
      assert.deepEqual(r.moving, []);
      assert.equal(r.scroll, 'auto');
    } finally {
      await page.done();
    }
  });
}

test('sem a preferência, o site ainda tem transições (o teste acima não é vazio)', async () => {
  const page = await site.open('');
  try {
    assert.ok((await page.evaluate(MOVING)).moving.length > 0);
  } finally {
    await page.done();
  }
});
