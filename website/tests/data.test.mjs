// Dados contra as fontes: os números e fatos que as páginas publicam, nos dois idiomas, são
// conferidos de novo, com leituras simples e independentes do código da release estável
// (git show/ls-tree) e das releases que o build usou.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { SITE, DIST, readJson } from './_site.mjs';

const ROOT = path.resolve(SITE, '..');
const rel = readJson('dados/relatorio.json').estavel;
const est = readJson('dados/estavel.json');
const git = (...args) => execFileSync('git', ['-C', ROOT, ...args], { encoding: 'utf8', maxBuffer: 64 << 20 });
const atStable = file => git('show', `${rel.commit}:${file}`);
const text = p => fs.readFileSync(path.join(DIST, p), 'utf8').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ');
const landing = { en: text('index.html'), pt: text('pt-br/index.html') };
const fmtMB = (n, locale) => `${(n / 1024 / 1024).toLocaleString(locale, { maximumFractionDigits: 1 })} MB`;

test('a release estável existe no repositório e está na história do main', () => {
  assert.match(rel.tag, /^XenDroid-v\d+-[0-9a-f]{8}$/);
  assert.ok(rel.tag.endsWith(rel.short) && rel.commit.startsWith(rel.short));
  assert.equal(git('cat-file', '-t', rel.commit).trim(), 'commit');
  execFileSync('git', ['-C', ROOT, 'merge-base', '--is-ancestor', rel.commit, 'HEAD']);
});

test('a página inicial oferece o APK da release estável, com tamanho, data e SHA-256 certos', () => {
  assert.match(rel.apk.url, new RegExp(`/releases/download/${rel.tag}/${rel.apk.name.replace(/\./g, '\\.')}$`));
  assert.match(rel.apk.sha256, /^[0-9a-f]{64}$/);
  for (const [lang, file, locale, dateOpts] of [['en', 'index.html', 'en-US', { month: 'short', day: 'numeric', year: 'numeric' }], ['pt', 'pt-br/index.html', 'pt-BR', {}]]) {
    const html = fs.readFileSync(path.join(DIST, file), 'utf8');
    assert.ok(html.includes(`href="${rel.apk.url}"`), `${lang}: o botão de download aponta para o APK`);
    assert.ok(html.includes(rel.apk.sha256), `${lang}: o SHA-256 completo está na página (para copiar)`);
    assert.ok(landing[lang].includes(fmtMB(rel.apk.size, locale)), `${lang}: tamanho ${fmtMB(rel.apk.size, locale)}`);
    const day = new Intl.DateTimeFormat(locale, { timeZone: 'America/Sao_Paulo', ...dateOpts }).format(new Date(rel.date));
    assert.ok(landing[lang].includes(day), `${lang}: data ${day}`);
    assert.ok(landing[lang].includes(`build ${rel.build}`), `${lang}: build ${rel.build}`);
  }
});

test('os números da página inicial batem com o código da release', () => {
  // -z: nomes com acento saem sem as aspas e os escapes do git
  const patches = git('ls-tree', '-z', '--name-only', `${rel.commit}`, 'patches/xenia-canary/patches/').split('\0').filter(f => f.endsWith('.patch.toml'));
  const titles = new Set(patches.map(f => path.basename(f).slice(0, 8).toUpperCase()));
  assert.ok(landing.pt.includes(`${patches.length} arquivos de patch`), `${patches.length} arquivos de patch`);
  assert.ok(landing.pt.includes(`${titles.size} jogos com patches`), `${titles.size} jogos com patches`);
  assert.ok(landing.en.includes(`${patches.length} Xenia Canary patch files`), `${patches.length} patch files`);
  assert.ok(landing.en.includes(`${titles.size} games with patches`), `${titles.size} games with patches`);

  const quirks = atStable('emulator-core/src/main/cpp/xenia/src/xenia/game_quirks.cc');
  const table = quirks.slice(quirks.indexOf('kQuirks[]'));
  const entries = [...table.matchAll(/^\s*\{0x([0-9A-Fa-f]{8}),/gm)].map(m => m[1].toUpperCase());
  assert.ok(landing.pt.includes(`${entries.length} ajustes para ${new Set(entries).size} jogos`), `${entries.length} ajustes para ${new Set(entries).size} jogos`);
  assert.ok(landing.en.includes(`${entries.length} settings for ${new Set(entries).size} specific games`), `${entries.length} settings for ${new Set(entries).size} games`);
  assert.equal(est.correcoesPorJogo.reduce((s, g) => s + g.entries.length, 0), entries.length);

  assert.ok(landing.pt.includes(`${est.ajustes.length} ajustes`));
  assert.ok(landing.en.includes(`${est.ajustes.length} settings`));
  for (const file of ['docs/settings-reference/index.html', 'pt-br/docs/referencia-de-ajustes/index.html']) {
    const reference = fs.readFileSync(path.join(DIST, file), 'utf8');
    for (const s of est.ajustes) assert.ok(reference.includes(s.name), `${s.section}|${s.name} falta em ${file}`);
  }
});

test('pacote, Android mínimo e ABI vêm do build.gradle da release', () => {
  const gradle = atStable('app/build.gradle');
  const appId = /applicationId\s+'([\w.]+)'/.exec(gradle)[1];
  const suffix = /xendroidReleaseIdSuffix'\)\s*\?:\s*'([\w.]+)'/.exec(gradle)[1];
  const minSdk = Number(/^\s*minSdk\s+(\d+)/m.exec(gradle)[1]);
  assert.equal(est.app.package, appId + suffix);
  assert.equal(est.app.minSdk, minSdk);
  assert.match(gradle, new RegExp(`abiFilters[^\\n]*'${est.app.abi}'`));
  for (const [req, install] of [['docs/requirements/', 'docs/installation/'], ['pt-br/docs/requisitos/', 'pt-br/docs/instalacao/']]) {
    const t = text(`${req}index.html`);
    assert.ok(t.includes(`API ${minSdk}`) && t.includes(`Android ${est.app.minAndroid}`), `${req} cita o Android mínimo`);
    assert.ok(t.includes(est.app.abi), `${req} cita a ABI`);
    assert.ok(text(`${install}index.html`).includes(est.app.package), `${install} cita o pacote`);
  }
});

test('cada ajuste publicado existe no esquema do app da release', () => {
  const schema = atStable('app/src/main/java/xendroid/compose/settings/SettingsSchema.kt');
  for (const s of est.ajustes) assert.ok(schema.includes(`"${s.name}"`), `${s.section}|${s.name} não está no SettingsSchema.kt`);
});

test('os textos em inglês dos ajustes são os do app (values/), e os em português os de values-pt-rBR', () => {
  const res = 'app/src/main/res';
  const strings = dir => git('ls-tree', '--name-only', `${rel.commit}`, `${res}/${dir}/`).split('\n').filter(f => /\/strings[^/]*\.xml$/.test(f)).map(f => atStable(f)).join('\n');
  const en = strings('values'), pt = strings('values-pt-rBR');
  const xml = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const has = (src, t) => src.includes(`>${xml(t)}<`) || src.includes(`>${xml(t).replace(/'/g, "\\'")}<`) || src.includes(`>"${xml(t)}"<`);
  let checked = 0;
  for (const s of est.ajustes) {
    if (s.title.lang !== 'pt-BR' || /%|\\/.test(s.title.text + s.title.en)) continue;
    assert.ok(has(pt, s.title.text), `${s.key}: "${s.title.text}" não está em values-pt-rBR`);
    assert.ok(has(en, s.title.en), `${s.key}: "${s.title.en}" não está em values`);
    checked++;
  }
  assert.ok(checked > 50, `ajustes conferidos: ${checked}`);
  // e as páginas mostram cada um no seu idioma
  const refEn = fs.readFileSync(path.join(DIST, 'docs/settings-reference/index.html'), 'utf8');
  const refPt = fs.readFileSync(path.join(DIST, 'pt-br/docs/referencia-de-ajustes/index.html'), 'utf8');
  const fr = est.ajustes.find(s => s.key === 'GPU|framerate_limit');
  assert.ok(refEn.includes(xml(fr.title.en)) && refPt.includes(xml(fr.title.text)));
});

test('duas versões só contam como diferentes quando os dados publicados mudam', async () => {
  const { fingerprint } = await import('../scripts/data.mjs');
  const base = { settings: { settings: [{ key: 'GPU|framerate_limit', default: '60', source: 'SettingsSchema.kt:10' }] }, app: { package: 'x', sources: { appId: 'build.gradle:66' } }, games: { names: new Set(['b', 'a']) }, template: { v: 1n }, problems: [{ msg: 'a' }] };
  const moved = { ...base, settings: { settings: [{ ...base.settings.settings[0], source: 'SettingsSchema.kt:12' }] }, app: { ...base.app, sources: { appId: 'build.gradle:70' } }, games: { names: new Set(['a', 'b']) }, problems: [] };
  assert.equal(fingerprint(moved), fingerprint(base), 'linhas do código, avisos e a ordem de um conjunto não contam');
  const changed = { ...base, settings: { settings: [{ ...base.settings.settings[0], default: '30' }] } };
  assert.notEqual(fingerprint(changed), fingerprint(base), 'um padrão diferente conta');
  assert.notEqual(fingerprint({ ...base, template: { v: 2n } }), fingerprint(base), 'números grandes entram na comparação');
});
