// Junta os dados do site: as releases do GitHub, a versão estável (o commit da última release,
// lido numa árvore de trabalho própria) e a de desenvolvimento (o commit em que o build roda,
// o main no CI). Cada canal é extraído do código daquela versão e conferido contra o core.
import fs from 'node:fs';
import path from 'node:path';
import { parse as parseToml } from 'smol-toml';
import { readReleases, stableRelease } from './extract/releases.mjs';
import { extractSettings, SETTINGS_SOURCES } from './extract/settings.mjs';
import { extractGames, GAME_SOURCES } from './extract/games.mjs';
import { extractApp, APP_SOURCES } from './extract/app.mjs';
import { indexCvars } from './lib/cpp.mjs';
import { checkConsistency, cvarTable } from './validate/consistency.mjs';
import { git, headInfo, fetchCommit, isAncestor, worktreeFor, firstParentLog } from './lib/git.mjs';

/** Caminhos do código que mudam os dados de um canal (fora deles, as duas versões dão o mesmo). */
const CHANNEL_PATHS = ['app/', 'emulator-core/', 'patches/', 'README.md', 'README.pt-BR.md', 'GAME_COMPAT.md', '.github/workflows/XenDroid.yml'];

function extractChannel(root) {
  const settings = extractSettings(root);
  const app = extractApp(root);
  const games = extractGames(root);
  const cvars = indexCvars(path.join(root, 'emulator-core/src/main/cpp'));
  const template = parseToml(fs.readFileSync(path.join(root, SETTINGS_SOURCES.template), 'utf8'), { integersAsBigInt: true });
  const consistency = checkConsistency({ settings, cvars, template, quirks: games.quirks });
  const problems = [...settings.problems, ...app.problems, ...games.problems].map(p => (typeof p === 'string' ? { level: 'error', msg: p } : p));
  return {
    settings,
    app,
    games,
    cvarCount: cvars.byName.size,
    cvars: cvarTable(settings, cvars),
    consistency,
    problems,
  };
}

/** Os dois canais dão os mesmos dados? (nenhum arquivo dos caminhos acima mudou entre eles) */
function sameData(root, a, b) {
  return git(root, ['diff', '--quiet', a, b, '--', ...CHANNEL_PATHS], { allowFail: true }) !== null;
}

/** Commits do main ainda sem release, sem os que só mexem no site. */
function unreleasedCommits(root, from) {
  const log = firstParentLog(root, from, 'HEAD');
  if (!log) return null;
  return log.filter(c => {
    const files = git(root, ['diff-tree', '--no-commit-id', '--name-only', '-r', '-m', '--first-parent', c.sha], { allowFail: true });
    if (files == null) return true;
    const list = files.split('\n').filter(Boolean);
    return list.length === 0 || list.some(f => !f.startsWith('website/') && !f.startsWith('.github/workflows/pages.yml'));
  });
}

export async function loadData({ root, config, strict, cacheDir, offline }) {
  const problems = [];
  const head = headInfo(root);
  let releases;
  try {
    releases = await readReleases({ repo: config.repo, token: process.env.GITHUB_TOKEN || null, cacheFile: path.join(cacheDir, 'releases.json'), strict: strict && !offline, offline });
  } catch (e) {
    problems.push({ level: 'error', msg: e.message });
    releases = { releases: [], source: 'nenhuma', fetchedAt: null };
  }
  const stable = stableRelease(releases.releases);
  if (!stable) problems.push({ level: 'error', msg: 'nenhuma release estável com APK publicada no GitHub' });

  let stableRoot = null;
  let same = false;
  if (stable && stable.commit) {
    if (!fetchCommit(root, stable.commit)) {
      problems.push({ level: 'error', msg: `o commit da release estável (${stable.commit}) não está no clone; o CI precisa da história completa` });
    } else {
      same = sameData(root, stable.commit, head.sha);
      stableRoot = same ? root : worktreeFor(root, stable.commit, path.join(cacheDir, 'worktrees', 'estavel'), config.sparsePaths);
      const inMain = isAncestor(root, stable.commit, head.sha);
      if (inMain === false) problems.push({ level: 'warn', msg: `a release estável (${stable.tag}) não está na história do commit do build` });
    }
  }

  const dev = extractChannel(root);
  const est = stableRoot ? (same ? dev : extractChannel(stableRoot)) : null;
  for (const [id, ch] of [['desenvolvimento', dev], ['estavel', est]]) {
    if (!ch) continue;
    for (const p of ch.problems) problems.push({ ...p, msg: `[${id}] ${p.msg}` });
    if (same && id === 'estavel') break;
  }

  const unreleased = stable && stable.commit && stableRoot ? unreleasedCommits(root, stable.commit) : null;

  return {
    head,
    releases,
    stable,
    sameChannels: same,
    channels: { estavel: est, desenvolvimento: dev },
    unreleased,
    sources: { settings: SETTINGS_SOURCES, games: GAME_SOURCES, app: APP_SOURCES },
    problems,
  };
}
