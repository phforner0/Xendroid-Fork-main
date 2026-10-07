// Dados contra as fontes: os números e fatos que as páginas publicam são conferidos de novo,
// com leituras simples e independentes do código da release estável (git show/ls-tree) e
// das releases que o build usou.
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
const landing = text('index.html');

test('a release estável existe no repositório e está na história do main', () => {
  assert.match(rel.tag, /^XenDroid-v\d+-[0-9a-f]{8}$/);
  assert.ok(rel.tag.endsWith(rel.short) && rel.commit.startsWith(rel.short));
  assert.equal(git('cat-file', '-t', rel.commit).trim(), 'commit');
  execFileSync('git', ['-C', ROOT, 'merge-base', '--is-ancestor', rel.commit, 'HEAD']);
});

test('a página inicial oferece o APK da release estável, com tamanho, data e SHA-256 certos', () => {
  const html = fs.readFileSync(path.join(DIST, 'index.html'), 'utf8');
  assert.match(rel.apk.url, new RegExp(`/releases/download/${rel.tag}/${rel.apk.name.replace(/\./g, '\\.')}$`));
  assert.ok(html.includes(`href="${rel.apk.url}"`), 'o botão de download aponta para o APK');
  assert.match(rel.apk.sha256, /^[0-9a-f]{64}$/);
  assert.ok(html.includes(rel.apk.sha256), 'o SHA-256 completo está na página (para copiar)');
  const mb = (rel.apk.size / 1024 / 1024).toLocaleString('pt-BR', { maximumFractionDigits: 1 });
  assert.ok(landing.includes(`${mb} MB`), `tamanho ${mb} MB`);
  const day = new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo' }).format(new Date(rel.date));
  assert.ok(landing.includes(day), `data ${day}`);
  assert.ok(landing.includes(`build ${rel.build}`), `build ${rel.build}`);
});

test('os números da página inicial batem com o código da release', () => {
  // -z: nomes com acento saem sem as aspas e os escapes do git
  const patches = git('ls-tree', '-z', '--name-only', `${rel.commit}`, 'patches/xenia-canary/patches/').split('\0').filter(f => f.endsWith('.patch.toml'));
  const titles = new Set(patches.map(f => path.basename(f).slice(0, 8).toUpperCase()));
  assert.ok(landing.includes(`${patches.length} arquivos de patch`), `${patches.length} arquivos de patch`);
  assert.ok(landing.includes(`${titles.size} jogos com patches`), `${titles.size} jogos com patches`);

  const quirks = atStable('emulator-core/src/main/cpp/xenia/src/xenia/game_quirks.cc');
  const table = quirks.slice(quirks.indexOf('kQuirks[]'));
  const entries = [...table.matchAll(/^\s*\{0x([0-9A-Fa-f]{8}),/gm)].map(m => m[1].toUpperCase());
  assert.ok(landing.includes(`${entries.length} ajustes para ${new Set(entries).size} jogos`), `${entries.length} ajustes para ${new Set(entries).size} jogos`);
  assert.equal(est.correcoesPorJogo.reduce((s, g) => s + g.entries.length, 0), entries.length);

  assert.ok(landing.includes(`${est.ajustes.length} ajustes`));
  const reference = fs.readFileSync(path.join(DIST, 'docs/referencia-de-ajustes/index.html'), 'utf8');
  for (const s of est.ajustes) assert.ok(reference.includes(s.name), `${s.section}|${s.name} falta na referência`);
});

test('pacote, Android mínimo e ABI vêm do build.gradle da release', () => {
  const gradle = atStable('app/build.gradle');
  const appId = /applicationId\s+'([\w.]+)'/.exec(gradle)[1];
  const suffix = /xendroidReleaseIdSuffix'\)\s*\?:\s*'([\w.]+)'/.exec(gradle)[1];
  const minSdk = Number(/^\s*minSdk\s+(\d+)/m.exec(gradle)[1]);
  assert.equal(est.app.package, appId + suffix);
  assert.equal(est.app.minSdk, minSdk);
  assert.match(gradle, new RegExp(`abiFilters[^\\n]*'${est.app.abi}'`));
  const req = text('docs/requisitos/index.html');
  assert.ok(req.includes(`API ${minSdk}`) && req.includes(`Android ${est.app.minAndroid}`), 'requisitos citam o Android mínimo');
  assert.ok(req.includes(est.app.abi));
  assert.ok(text('docs/instalacao/index.html').includes(est.app.package));
});

test('cada ajuste publicado existe no esquema do app da release', () => {
  const schema = atStable('app/src/main/java/xendroid/compose/settings/SettingsSchema.kt');
  for (const s of est.ajustes) assert.ok(schema.includes(`"${s.name}"`), `${s.section}|${s.name} não está no SettingsSchema.kt`);
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
