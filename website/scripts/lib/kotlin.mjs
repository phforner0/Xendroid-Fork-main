// Leitor mínimo de Kotlin para extrair dados declarados no código do app.
// Não é um compilador: reconhece literais, nomes, chamadas, pares `a to b`,
// argumentos nomeados, espalhamento `*x` e concatenação de strings com `+`.
// Quando encontra algo fora disso, lança um erro com arquivo e linha, para que
// uma mudança no app não passe despercebida pelo build do site.

export class KotlinSyntaxError extends Error {
  constructor(message, file, line) {
    super(`${file}:${line}: ${message}`);
    this.file = file;
    this.line = line;
  }
}

const PUNCT = ['->', '::', '?.', '?:', '==', '!=', '<=', '>=', '&&', '||', '..',
  '(', ')', '[', ']', '{', '}', ',', '.', '*', ':', '?', '=', '<', '>', '-', '+', '|', '!', '%', '/', '&', ';', '@'];

/** Divide o código em tokens, pulando comentários. */
export function tokenize(src, file = '<kotlin>') {
  const tokens = [];
  let i = 0;
  let line = 1;
  const n = src.length;
  while (i < n) {
    const c = src[i];
    if (c === '\n') { line++; i++; continue; }
    if (c === ' ' || c === '\t' || c === '\r') { i++; continue; }
    if (c === '/' && src[i + 1] === '/') { while (i < n && src[i] !== '\n') i++; continue; }
    if (c === '/' && src[i + 1] === '*') {
      let depth = 1; i += 2;
      while (i < n && depth) {
        if (src[i] === '\n') line++;
        if (src[i] === '/' && src[i + 1] === '*') { depth++; i += 2; continue; }
        if (src[i] === '*' && src[i + 1] === '/') { depth--; i += 2; continue; }
        i++;
      }
      continue;
    }
    if (c === '"') {
      const startLine = line;
      if (src.startsWith('"""', i)) {
        const end = src.indexOf('"""', i + 3);
        if (end < 0) throw new KotlinSyntaxError('string bruta sem fim', file, line);
        const raw = src.slice(i + 3, end);
        line += (raw.match(/\n/g) || []).length;
        tokens.push({ t: 'str', v: raw, line: startLine, template: raw.includes('$') });
        i = end + 3;
        continue;
      }
      let j = i + 1; let out = ''; let template = false;
      while (j < n && src[j] !== '"') {
        if (src[j] === '\n') throw new KotlinSyntaxError('quebra de linha dentro de string', file, line);
        if (src[j] === '\\') {
          const e = src[j + 1];
          const map = { n: '\n', t: '\t', r: '\r', b: '\b', '"': '"', "'": "'", '\\': '\\', $: '$' };
          if (e === 'u') { out += String.fromCharCode(parseInt(src.slice(j + 2, j + 6), 16)); j += 6; continue; }
          if (!(e in map)) throw new KotlinSyntaxError(`escape desconhecido \\${e}`, file, line);
          out += map[e]; j += 2; continue;
        }
        if (src[j] === '$') template = true;
        out += src[j]; j++;
      }
      if (j >= n) throw new KotlinSyntaxError('string sem fim', file, line);
      tokens.push({ t: 'str', v: out, line: startLine, template });
      i = j + 1;
      continue;
    }
    if (c === "'") {
      let j = i + 1; let v = src[j];
      if (v === '\\') { v = src[j + 1]; j++; }
      tokens.push({ t: 'char', v, line });
      i = j + 2;
      continue;
    }
    if (/[0-9]/.test(c) || (c === '.' && /[0-9]/.test(src[i + 1]))) {
      const m = /^(0[xX][0-9a-fA-F_]+[uUL]*|[0-9][0-9_]*(\.[0-9_]+)?([eE][+-]?[0-9]+)?[fFdDLuU]*|\.[0-9]+[fF]?)/.exec(src.slice(i));
      tokens.push({ t: 'num', v: m[0], line });
      i += m[0].length;
      continue;
    }
    if (/[A-Za-z_`]/.test(c)) {
      if (c === '`') { const end = src.indexOf('`', i + 1); tokens.push({ t: 'id', v: src.slice(i + 1, end), line }); i = end + 1; continue; }
      const m = /^[A-Za-z_][A-Za-z0-9_]*/.exec(src.slice(i));
      tokens.push({ t: 'id', v: m[0], line });
      i += m[0].length;
      continue;
    }
    const p = PUNCT.find(s => src.startsWith(s, i));
    if (!p) throw new KotlinSyntaxError(`caractere inesperado ${JSON.stringify(c)}`, file, line);
    tokens.push({ t: 'p', v: p, line });
    i += p.length;
  }
  tokens.push({ t: 'eof', v: '', line });
  return tokens;
}

/**
 * Parser de expressões sobre a lista de tokens. Cada nó tem `kind`:
 * str, num, bool, null, name (path), call (callee, args[{name, value}]), pair, spread,
 * concat (partes), neg.
 */
export class KotlinReader {
  constructor(src, file = '<kotlin>') {
    this.file = file;
    this.src = src;
    this.tokens = tokenize(src, file);
  }

  error(msg, tok) { return new KotlinSyntaxError(msg, this.file, tok ? tok.line : 0); }

  /** Índice do primeiro token depois de uma sequência de valores (ex.: ['val', 'categories']). */
  find(seq, from = 0) {
    outer: for (let i = from; i < this.tokens.length; i++) {
      for (let k = 0; k < seq.length; k++) {
        const tok = this.tokens[i + k];
        if (!tok || tok.v !== seq[k]) continue outer;
      }
      return i;
    }
    return -1;
  }

  /** Lê a expressão que inicia o valor de `val <nome> ... = <expr>` (ou `const val`). */
  valueOf(name, { from = 0 } = {}) {
    const i = this.find(['val', name], from);
    if (i < 0) throw this.error(`declaração "val ${name}" não encontrada`, this.tokens[0]);
    let j = i + 2;
    // pula o tipo opcional até o "=" no nível zero
    let depth = 0;
    while (j < this.tokens.length) {
      const tok = this.tokens[j];
      if (tok.v === '<' || tok.v === '(') depth++;
      else if (tok.v === '>' || tok.v === ')') depth--;
      else if (tok.v === '=' && depth === 0) break;
      j++;
    }
    this.pos = j + 1;
    return this.parseExpr();
  }

  peek(o = 0) { return this.tokens[this.pos + o]; }
  next() { return this.tokens[this.pos++]; }
  expect(v) {
    const tok = this.next();
    if (tok.v !== v) throw this.error(`esperava "${v}", achou "${tok.v}"`, tok);
    return tok;
  }

  parseExpr() {
    let left = this.parseConcat();
    while (this.peek().t === 'id' && this.peek().v === 'to') {
      this.next();
      const right = this.parseConcat();
      left = { kind: 'pair', left, right, line: left.line };
    }
    return left;
  }

  parseConcat() {
    let node = this.parseUnary();
    if (this.peek().v === '+') {
      const parts = [node];
      while (this.peek().v === '+') { this.next(); parts.push(this.parseUnary()); }
      node = { kind: 'concat', parts, line: parts[0].line };
    }
    return node;
  }

  parseUnary() {
    const tok = this.peek();
    if (tok.v === '*') { this.next(); return { kind: 'spread', value: this.parseUnary(), line: tok.line }; }
    if (tok.v === '-') { this.next(); const v = this.parseUnary(); return { kind: 'neg', value: v, line: tok.line }; }
    return this.parsePostfix(this.parsePrimary());
  }

  parsePrimary() {
    const tok = this.next();
    if (tok.t === 'str') {
      if (tok.template) throw this.error('string com template ($) não é suportada aqui', tok);
      return { kind: 'str', value: tok.v, line: tok.line };
    }
    if (tok.t === 'num') return { kind: 'num', value: tok.v, line: tok.line };
    if (tok.t === 'id') {
      if (tok.v === 'true' || tok.v === 'false') return { kind: 'bool', value: tok.v === 'true', line: tok.line };
      if (tok.v === 'null') return { kind: 'null', line: tok.line };
      return { kind: 'name', path: [tok.v], line: tok.line };
    }
    if (tok.v === '(') { const e = this.parseExpr(); this.expect(')'); return e; }
    throw this.error(`expressão inesperada "${tok.v}"`, tok);
  }

  parsePostfix(node) {
    for (;;) {
      const tok = this.peek();
      if (tok.v === '.' && node.kind === 'name' && this.peek(1).t === 'id') {
        this.next();
        node = { kind: 'name', path: [...node.path, this.next().v], line: node.line };
        continue;
      }
      if (tok.v === '(' && node.kind === 'name') {
        this.next();
        const args = [];
        while (this.peek().v !== ')') {
          let name = null;
          if (this.peek().t === 'id' && this.peek(1).v === '=' && this.peek(2).v !== '=') {
            name = this.next().v; this.next();
          }
          args.push({ name, value: this.parseExpr() });
          if (this.peek().v === ',') this.next();
          else if (this.peek().v !== ')') throw this.error(`esperava "," ou ")", achou "${this.peek().v}"`, this.peek());
        }
        this.next();
        node = { kind: 'call', callee: node.path.join('.'), args, line: node.line };
        continue;
      }
      return node;
    }
  }
}

/** Converte um nó literal em valor JS (string, número, booleano, lista de pares...). */
export function literal(node, reader) {
  switch (node.kind) {
    case 'str': return node.value;
    case 'num': return node.value;
    case 'bool': return node.value;
    case 'null': return null;
    case 'neg': return '-' + literal(node.value, reader);
    case 'concat': return node.parts.map(p => {
      const v = literal(p, reader);
      if (typeof v !== 'string') throw reader.error('concatenação com algo que não é string', { line: node.line });
      return v;
    }).join('');
    default: throw reader.error(`esperava um literal, achou ${node.kind}`, { line: node.line });
  }
}

/** Número Kotlin ("4", "2.0f", "1_000") como número JS. */
export function kotlinNumber(raw) {
  const s = String(raw).replace(/_/g, '').replace(/[fFdDLuU]+$/, '');
  return s.startsWith('0x') || s.startsWith('0X') ? parseInt(s, 16) : Number(s);
}

/** Lista os argumentos de uma chamada `listOf(...)`/`arrayOf(...)`/`setOf(...)`. */
export function listItems(node, reader, callees = ['listOf', 'arrayOf', 'setOf', 'mutableListOf']) {
  if (node.kind !== 'call' || !callees.includes(node.callee)) {
    throw reader.error(`esperava ${callees.join('/')}(...), achou ${node.kind} ${node.callee || ''}`, { line: node.line });
  }
  return node.args.map(a => a.value);
}

/** Pares `chave to valor` de um `mapOf(...)`. */
export function mapEntries(node, reader) {
  if (node.kind !== 'call' || node.callee !== 'mapOf') throw reader.error('esperava mapOf(...)', { line: node.line });
  return node.args.map(a => {
    if (a.value.kind !== 'pair') throw reader.error('esperava par "a to b" em mapOf', { line: a.value.line });
    return [a.value.left, a.value.right];
  });
}

/** Caminho de um nome (ex.: R.string.set_fps) como string. */
export function namePath(node, reader) {
  if (node.kind !== 'name') throw reader.error(`esperava um nome, achou ${node.kind}`, { line: node.line });
  return node.path.join('.');
}

/**
 * Corpo de uma função `fun nome(...)` como intervalo de tokens [início, fim) do bloco `{ ... }`
 * ou da expressão depois de `=` (até o fim da expressão no nível zero).
 */
export function functionTokens(reader, name) {
  const i = reader.find(['fun', name]);
  if (i < 0) throw reader.error(`função ${name} não encontrada`, reader.tokens[0]);
  let j = i + 2;
  // pula a lista de parâmetros
  while (reader.tokens[j].v !== '(') j++;
  let depth = 0;
  for (; j < reader.tokens.length; j++) {
    if (reader.tokens[j].v === '(') depth++;
    if (reader.tokens[j].v === ')') { depth--; if (depth === 0) break; }
  }
  j++;
  // tipo de retorno opcional
  while (reader.tokens[j].v !== '{' && reader.tokens[j].v !== '=') j++;
  const start = j;
  let d = 0;
  for (; j < reader.tokens.length; j++) {
    const v = reader.tokens[j].v;
    if (v === '{' || v === '(' || v === '[') d++;
    if (v === '}' || v === ')' || v === ']') { d--; if (d === 0 && reader.tokens[start].v === '{') return { start: start + 1, end: j }; }
    if (reader.tokens[start].v === '=' && d === 0 && j > start + 1 && reader.tokens[j].t === 'id' && reader.tokens[j].v === 'fun') return { start: start + 1, end: j };
  }
  return { start: start + 1, end: j };
}
