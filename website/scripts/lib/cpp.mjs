// Leitura das cvars do core (macros DEFINE_*) e dos ajustes por jogo (game_quirks.cc).
import fs from 'node:fs';
import path from 'node:path';

/**
 * Macros definidas no build Android do core: platform.h define XE_PLATFORM_xendroid e
 * XE_PLATFORM_LINUX para __ANDROID__ (XE_PLATFORM_ANDROID fica comentado), em arm64 com clang.
 */
export const ANDROID_DEFINES = Object.freeze({
  __ANDROID__: 1, __linux__: 1, __aarch64__: 1, __clang__: 1,
  XE_PLATFORM_xendroid: 1, XE_PLATFORM_LINUX: 1, XE_ARCH_ARM64: 1,
  XE_COMPILER_CLANG: 1, XE_COMPILER_HAS_CLANG_EXTENSIONS: 1, XE_COMPILER_HAS_GNU_EXTENSIONS: 1,
});

/** Avalia a expressão de um #if/#elif (nomes desconhecidos valem 0, como no pré-processador). */
function evalCondition(expr, defines) {
  const toks = expr.replace(/\/\/.*$/, '').replace(/\/\*[\s\S]*?\*\//g, '')
    .match(/defined|[A-Za-z_][A-Za-z0-9_]*|0[xX][0-9a-fA-F]+|\d+[uUlL]*|&&|\|\||==|!=|<=|>=|[!()<>+\-*]/g) || [];
  let p = 0;
  const peek = () => toks[p];
  const next = () => toks[p++];
  const num = t => (/^0[xX]/.test(t) ? parseInt(t, 16) : parseInt(t, 10));
  function primary() {
    const t = next();
    if (t === '(') { const v = or(); next(); return v; }
    if (t === '!') return primary() ? 0 : 1;
    if (t === '-') return -primary();
    if (t === 'defined') {
      let name = next();
      if (name === '(') { name = next(); next(); }
      return name in defines ? 1 : 0;
    }
    if (t === undefined) return 0;
    if (/^\d|^0[xX]/.test(t)) return num(t);
    const v = defines[t];
    return v === undefined ? 0 : Number(v);
  }
  function mul() { let v = primary(); while (peek() === '*') { next(); v *= primary(); } return v; }
  function add() { let v = mul(); while (peek() === '+' || peek() === '-') { const o = next(); v = o === '+' ? v + mul() : v - mul(); } return v; }
  function cmp() {
    let v = add();
    while (['<', '>', '<=', '>=', '==', '!='].includes(peek())) {
      const o = next(); const r = add();
      v = { '<': v < r, '>': v > r, '<=': v <= r, '>=': v >= r, '==': v === r, '!=': v !== r }[o] ? 1 : 0;
    }
    return v;
  }
  function and() { let v = cmp(); while (peek() === '&&') { next(); const r = cmp(); v = v && r ? 1 : 0; } return v; }
  function or() { let v = and(); while (peek() === '||') { next(); const r = and(); v = v || r ? 1 : 0; } return v; }
  return or();
}

/**
 * Tokens de C/C++ suficientes para ler chamadas de macro. As diretivas são avaliadas com
 * `defines` (#if/#ifdef/#elif/#else/#endif e #define simples): código de outras plataformas
 * não vira token. Sem `defines`, todo o código é lido.
 */
export function tokenizeCpp(src, defines = null) {
  const tokens = [];
  let i = 0;
  let line = 1;
  let lineStart = true;
  const n = src.length;
  const local = defines ? { ...defines } : null;
  // pilha de #if: { active, taken, parent }
  const stack = [];
  const active = () => (stack.length ? stack[stack.length - 1].active : true);
  const push = t => { if (!local || active()) tokens.push(t); };
  while (i < n) {
    const c = src[i];
    if (c === '\n') { line++; i++; lineStart = true; continue; }
    if (c === ' ' || c === '\t' || c === '\r' || c === '\f' || c === '\v') { i++; continue; }
    if (c === '#' && lineStart) {
      // diretiva até o fim da linha, respeitando continuações com "\"
      let text = '';
      i++;
      while (i < n) {
        if (src[i] === '\\' && src[i + 1] === '\n') { i += 2; line++; text += ' '; continue; }
        if (src[i] === '\\' && src[i + 1] === '\r' && src[i + 2] === '\n') { i += 3; line++; text += ' '; continue; }
        if (src[i] === '\n') break;
        if (src[i] === '/' && src[i + 1] === '*') {
          const end = src.indexOf('*/', i + 2);
          const chunk = src.slice(i, end < 0 ? n : end + 2);
          line += (chunk.match(/\n/g) || []).length;
          i = end < 0 ? n : end + 2;
          text += ' ';
          continue;
        }
        if (src[i] === '/' && src[i + 1] === '/') { while (i < n && src[i] !== '\n') i++; break; }
        text += src[i];
        i++;
      }
      if (local) {
        const m = /^\s*(\w+)\s*([\s\S]*)$/.exec(text) || [];
        const dir = m[1];
        const rest = (m[2] || '').trim();
        const parent = active();
        if (dir === 'if' || dir === 'ifdef' || dir === 'ifndef') {
          const v = dir === 'if' ? !!evalCondition(rest, local) : dir === 'ifdef' ? rest.split(/\s/)[0] in local : !(rest.split(/\s/)[0] in local);
          stack.push({ active: parent && v, taken: v, parent });
        } else if (dir === 'elif' || dir === 'elifdef' || dir === 'elifndef') {
          const top = stack[stack.length - 1];
          if (top) {
            if (top.taken) top.active = false;
            else {
              const v = dir === 'elif' ? !!evalCondition(rest, local) : dir === 'elifdef' ? rest in local : !(rest in local);
              top.active = top.parent && v;
              top.taken = v;
            }
          }
        } else if (dir === 'else') {
          const top = stack[stack.length - 1];
          if (top) { top.active = top.parent && !top.taken; top.taken = true; }
        } else if (dir === 'endif') {
          stack.pop();
        } else if (dir === 'define' && parent) {
          const d = /^([A-Za-z_]\w*)(?!\()\s*(.*)$/.exec(rest);
          if (d) local[d[1]] = d[2] === '' ? 1 : (/^\d+$/.test(d[2]) ? Number(d[2]) : d[2]);
        } else if (dir === 'undef' && parent) {
          delete local[rest.split(/\s/)[0]];
        }
      }
      continue;
    }
    lineStart = false;
    if (c === '/' && src[i + 1] === '/') { while (i < n && src[i] !== '\n') i++; continue; }
    if (c === '/' && src[i + 1] === '*') {
      const end = src.indexOf('*/', i + 2);
      const chunk = src.slice(i, end < 0 ? n : end + 2);
      line += (chunk.match(/\n/g) || []).length;
      i = end < 0 ? n : end + 2;
      continue;
    }
    // literais de string, inclusive com prefixo (u8, L, R"(...)")
    const pre = /^(u8|u|U|L)?R?"/.exec(src.slice(i, i + 4));
    if (pre && (c === '"' || /[uUL]/.test(c) || c === 'R')) {
      const isRaw = pre[0].includes('R"');
      let j = i + pre[0].length;
      if (isRaw) {
        const open = src.indexOf('(', j);
        const delim = src.slice(j, open);
        const close = src.indexOf(')' + delim + '"', open);
        const v = src.slice(open + 1, close);
        push({ t: 'str', v, line });
        line += (v.match(/\n/g) || []).length;
        i = close + delim.length + 2;
        continue;
      }
      let out = '';
      while (j < n && src[j] !== '"') {
        if (src[j] === '\\') {
          const e = src[j + 1];
          const map = { n: '\n', t: '\t', r: '\r', '"': '"', "'": "'", '\\': '\\', 0: '\0' };
          if (e === '\n') { j += 2; line++; continue; }
          out += e in map ? map[e] : e;
          j += 2;
          continue;
        }
        out += src[j];
        j++;
      }
      push({ t: 'str', v: out, line });
      i = j + 1;
      continue;
    }
    if (c === "'") {
      let j = i + 1;
      while (j < n && src[j] !== "'") j += src[j] === '\\' ? 2 : 1;
      push({ t: 'char', v: src.slice(i, j + 1), line });
      i = j + 1;
      continue;
    }
    if (/[0-9]/.test(c) || (c === '.' && /[0-9]/.test(src[i + 1]))) {
      const m = /^(0[xX][0-9a-fA-F']+|[0-9][0-9']*(\.[0-9']*)?([eE][+-]?[0-9]+)?|\.[0-9]+([eE][+-]?[0-9]+)?)[uUlLfF]*/.exec(src.slice(i));
      push({ t: 'num', v: m[0], line });
      i += m[0].length;
      continue;
    }
    if (/[A-Za-z_]/.test(c)) {
      const m = /^[A-Za-z_][A-Za-z0-9_]*/.exec(src.slice(i));
      push({ t: 'id', v: m[0], line });
      i += m[0].length;
      continue;
    }
    const two = src.slice(i, i + 2);
    if (['::', '->', '<<', '>>', '==', '!=', '<=', '>=', '&&', '||', '++', '--'].includes(two)) {
      push({ t: 'p', v: two, line });
      i += 2;
      continue;
    }
    push({ t: 'p', v: c, line });
    i++;
  }
  return tokens;
}

/** Argumentos de uma chamada que começa no token "(" em `open`: listas de tokens no nível zero. */
function callArgs(tokens, open) {
  const args = [[]];
  let depth = 0;
  for (let k = open; k < tokens.length; k++) {
    const v = tokens[k].v;
    if (tokens[k].t === 'p' && (v === '(' || v === '{' || v === '[')) { depth++; if (depth === 1 && v === '(') continue; }
    if (tokens[k].t === 'p' && (v === ')' || v === '}' || v === ']')) { depth--; if (depth === 0) return { args, end: k }; }
    if (depth === 1 && tokens[k].t === 'p' && v === ',') { args.push([]); continue; }
    args[args.length - 1].push(tokens[k]);
  }
  throw new Error('chamada sem fechamento');
}

const joinRaw = toks => toks.map(t => (t.t === 'str' ? JSON.stringify(t.v) : t.v)).join(' ').replace(/ ?:: ?/g, '::').replace(/- (\d)/, '-$1');
const strings = toks => toks.filter(t => t.t === 'str').map(t => t.v).join('');

const DEFINE = /^DEFINE_(transient_)?(bool|int32|uint32|int64|uint64|double|string|path)(_advanced)?$/;

/** Todas as cvars definidas num arquivo de código. */
export function cvarsInSource(src, file, defines = ANDROID_DEFINES) {
  const tokens = tokenizeCpp(src, defines);
  const out = [];
  for (let k = 0; k < tokens.length - 1; k++) {
    const m = tokens[k].t === 'id' ? DEFINE.exec(tokens[k].v) : null;
    if (!m || tokens[k + 1].v !== '(') continue;
    // "DEFINE_bool(" precedido de "#define" já foi pulado como diretiva
    const { args, end } = callArgs(tokens, k + 1);
    if (args.length < 4) continue;
    const [nameT, defT, descT, catT] = args;
    if (nameT.length !== 1 || nameT[0].t !== 'id') continue;
    out.push({
      name: nameT[0].v,
      type: m[2],
      transient: !!m[1],
      advanced: !!m[3],
      defaultRaw: m[2] === 'string' || m[2] === 'path' ? strings(defT) : joinRaw(defT),
      defaultIsLiteral: defT.length > 0 && defT.every(t => t.t === 'str' || t.t === 'num' || (t.t === 'id' && /^(true|false)$/.test(t.v)) || (t.t === 'p' && t.v === '-')),
      description: strings(descT),
      category: strings(catT),
      file,
      line: tokens[k].line,
    });
    k = end;
  }
  return out;
}

/** Arquivos que não entram no build Android (outras plataformas, testes, demos, ferramentas). */
const NOT_ANDROID = [
  /(^|\/)(testing|d3d12|xaudio2|sdl|winkey|xinput|metal|wx|tests?)(\/|$)/,
  /_(main|demo|test|tests|benchmark|win|mac|gtk|x11|wx)\.(cc|cpp|h)$/,
];

/**
 * Índice das cvars que o core Android define: a cola nativa do app (`cpp/*.cpp|h`) e
 * `xenia/src/xenia/**`, sem os arquivos de outras plataformas e avaliando os #if do Android.
 * Uma cvar definida duas vezes fica com a da cola nativa; as demais repetições são relatadas.
 */
export function indexCvars(cppRoot, { rel = cppRoot } = {}) {
  const byName = new Map();
  const dupes = [];
  const add = (cv, glue) => {
    if (byName.has(cv.name)) {
      const prev = byName.get(cv.name);
      if (glue && !prev.glue) { byName.set(cv.name, { ...cv, glue }); dupes.push([cv, prev, 'cola nativa prevalece']); }
      else dupes.push([prev, cv, 'repetida']);
    } else byName.set(cv.name, { ...cv, glue });
  };
  const scanFile = (p, glue) => {
    const src = fs.readFileSync(p, 'utf8');
    if (!src.includes('DEFINE_')) return;
    for (const cv of cvarsInSource(src, path.relative(rel, p))) add(cv, glue);
  };
  for (const e of fs.readdirSync(cppRoot, { withFileTypes: true })) {
    if (e.isFile() && /\.(cpp|h)$/.test(e.name)) scanFile(path.join(cppRoot, e.name), true);
  }
  const walk = dir => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      const r = path.relative(cppRoot, p);
      if (e.isDirectory()) {
        if (e.name === 'third_party' || e.name === 'build' || e.name.startsWith('.')) continue;
        if (NOT_ANDROID[0].test(r)) continue;
        walk(p);
      } else if (/\.(cc|cpp|h|hpp|inc)$/.test(e.name) && !NOT_ANDROID.some(rx => rx.test(r))) {
        scanFile(p, false);
      }
    }
  };
  walk(path.join(cppRoot, 'xenia', 'src', 'xenia'));
  return { byName, dupes };
}

/** Valor de um ajuste por jogo: tokens da entrada → {type, value}. */
function quirkValue(toks) {
  if (toks.length === 1 && toks[0].t === 'id' && /^(true|false)$/.test(toks[0].v)) return { type: 'bool', value: toks[0].v === 'true' };
  if (toks.every(t => t.t === 'str')) return { type: 'string', value: strings(toks) };
  // int64_t(32)
  if (toks.length === 4 && toks[0].t === 'id' && /int/.test(toks[0].v) && toks[1].v === '(' && toks[3].v === ')') return { type: 'int', value: Number(toks[2].v.replace(/[uUlL']/g, '')) };
  if (toks.length === 5 && toks[0].t === 'id' && /int/.test(toks[0].v) && toks[2].v === '-') return { type: 'int', value: -Number(toks[3].v) };
  if (toks.length === 1 && toks[0].t === 'num') {
    const raw = toks[0].v.replace(/[fF']/g, '');
    return raw.includes('.') || /e/i.test(raw) ? { type: 'double', value: Number(raw) } : { type: 'int', value: Number(raw) };
  }
  throw new Error(`valor de ajuste por jogo não reconhecido: ${joinRaw(toks)}`);
}

/** Entradas de `kQuirks[]` com o comentário que as precede. */
export function readQuirks(src, file) {
  const tokens = tokenizeCpp(src, ANDROID_DEFINES);
  const k = tokens.findIndex((t, i) => t.v === 'kQuirks' && tokens[i + 1] && tokens[i + 1].v === '[');
  if (k < 0) throw new Error(`${file}: tabela kQuirks não encontrada`);
  let open = k;
  while (!(tokens[open].t === 'p' && tokens[open].v === '{')) open++;
  const lines = src.split('\n');
  const entries = [];
  let depth = 0;
  let cur = null;
  for (let i = open; i < tokens.length; i++) {
    const t = tokens[i];
    if (t.t === 'p' && t.v === '{') { depth++; if (depth === 2) cur = { toks: [[]], line: t.line }; continue; }
    if (t.t === 'p' && t.v === '}') {
      depth--;
      if (depth === 1 && cur) {
        const [idT, cvT, valT, noteT] = cur.toks;
        if (!idT || idT.length !== 1 || idT[0].t !== 'num') throw new Error(`${file}:${cur.line}: Title ID não reconhecido`);
        // comentário logo acima da entrada (linhas // contíguas)
        const comment = [];
        for (let l = cur.line - 2; l >= 0; l--) {
          const s = lines[l].trim();
          if (s.startsWith('//')) comment.unshift(s.replace(/^\/\/\s?/, ''));
          else break;
        }
        entries.push({
          titleId: Number(idT[0].v).toString(16).toUpperCase().padStart(8, '0'),
          cvar: strings(cvT),
          ...quirkValue(valT),
          note: strings(noteT),
          comment: comment.join(' '),
          file,
          line: cur.line,
        });
        cur = null;
      }
      if (depth === 0) break;
      continue;
    }
    if (depth === 2 && cur) {
      if (t.t === 'p' && t.v === ',') { cur.toks.push([]); continue; }
      cur.toks[cur.toks.length - 1].push(t);
    }
  }
  return entries;
}
