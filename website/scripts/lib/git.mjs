// Acesso ao git do repositório para identificar versões e preparar a árvore da release estável.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

export function git(repo, args, { allowFail = false, input } = {}) {
  try {
    return execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'], input, maxBuffer: 64 * 1024 * 1024 }).trim();
  } catch (e) {
    if (allowFail) return null;
    throw new Error(`git ${args.join(' ')} falhou: ${(e.stderr || e.message || '').toString().trim()}`);
  }
}

export function headInfo(repo) {
  const sha = git(repo, ['rev-parse', 'HEAD']);
  const date = git(repo, ['log', '-1', '--format=%cI', 'HEAD']);
  const subject = git(repo, ['log', '-1', '--format=%s', 'HEAD']);
  return { sha, short: sha.slice(0, 9), date, subject };
}

export function hasCommit(repo, sha) {
  return git(repo, ['cat-file', '-e', `${sha}^{commit}`], { allowFail: true }) !== null;
}

export function isShallow(repo) {
  return git(repo, ['rev-parse', '--is-shallow-repository'], { allowFail: true }) === 'true';
}

/** O commit `a` está contido na história de `b`? null quando não dá para saber (história cortada). */
export function isAncestor(repo, a, b) {
  if (!hasCommit(repo, a) || !hasCommit(repo, b)) return null;
  try {
    execFileSync('git', ['-C', repo, 'merge-base', '--is-ancestor', a, b], { stdio: 'ignore' });
    return true;
  } catch (e) {
    if (e.status === 1) {
      // num clone raso, "não é ancestral" pode ser só falta de história
      return isShallow(repo) ? null : false;
    }
    return null;
  }
}

/** Tenta buscar um commit que não está no clone (clone raso no CI). */
export function fetchCommit(repo, sha) {
  if (hasCommit(repo, sha)) return true;
  git(repo, ['fetch', '--no-tags', '--depth=1', 'origin', sha], { allowFail: true });
  return hasCommit(repo, sha);
}

/**
 * Árvore de trabalho só-leitura de outro commit, com o checkout esparso dos caminhos dados
 * (padrões sem cone, como em .git/info/sparse-checkout). Reaproveita a pasta se já estiver lá.
 */
export function worktreeFor(repo, sha, dir, patterns) {
  const marker = path.join(dir, '.site-worktree');
  if (fs.existsSync(marker) && fs.readFileSync(marker, 'utf8').trim() === sha) return dir;
  if (fs.existsSync(dir)) {
    git(repo, ['worktree', 'remove', '--force', dir], { allowFail: true });
    fs.rmSync(dir, { recursive: true, force: true });
  }
  git(repo, ['worktree', 'prune']);
  git(repo, ['worktree', 'add', '--detach', '--no-checkout', dir, sha]);
  git(dir, ['sparse-checkout', 'init', '--no-cone']);
  git(dir, ['sparse-checkout', 'set', '--no-cone', ...patterns]);
  git(dir, ['checkout', '--quiet', sha]);
  fs.writeFileSync(marker, sha + '\n');
  return dir;
}

/** Commits do primeiro pai entre `from` (exclusivo) e `to`, com assunto e corpo. */
export function firstParentLog(repo, from, to) {
  const out = git(repo, ['log', '--first-parent', '--format=%H%x1f%cI%x1f%s%x1f%b%x1e', `${from}..${to}`], { allowFail: true });
  if (out == null) return null;
  return out.split('\x1e').map(s => s.trim()).filter(Boolean).map(rec => {
    const [sha, date, subject, body] = rec.split('\x1f');
    return { sha, date, subject, body: (body || '').trim() };
  });
}
