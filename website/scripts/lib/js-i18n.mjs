// Tradução dos scripts do simulador no build. Os textos do protótipo (src/tool/proto) estão em
// português dentro do código: strings e template literals, muitas vezes com HTML. Aqui o código
// é lido com o acorn, cada string é quebrada em trechos de texto (entre as tags do HTML e nos
// atributos que aparecem para quem usa o app: aria-label, title, placeholder, alt, data-msg) e
// cada trecho é trocado pelo do dicionário (content/simulador.en.json). As interpolações ${…}
// de um trecho viram {0}, {1}… na chave e voltam, na ordem que a tradução pedir.
//
// Uma palavra que o app traduz de dois jeitos conforme o lugar tem uma chave com escopo:
// "@<arquivo>|texto" vale só naquele arquivo e "@<arquivo>#<função>|texto" só dentro da função
// (o arquivo relativo a src/tool, ex.: "@proto/screens/sistema.js#upCard|Procurando…").
//
// O que fica de fora do dicionário e parece texto para pessoas sai em `missing`; o build avisa.
import * as acorn from 'acorn';

const ATTRS = new Set(['aria-label', 'title', 'placeholder', 'alt', 'data-msg', 'aria-roledescription', 'aria-description']);
const MARK = i => `\u0001${i}\u0002`;
const MARK_RE = /\u0001(\d+)\u0002/g;

/** Nome de uma função (declarada, numa variável ou numa propriedade), para os escopos. */
function fnName(node, parent) {
  if (/^(FunctionDeclaration|FunctionExpression|ArrowFunctionExpression)$/.test(node.type)) {
    if (node.id) return node.id.name;
    if (parent && parent.type === 'VariableDeclarator' && parent.id.type === 'Identifier') return parent.id.name;
    if (parent && parent.type === 'Property' && !parent.computed) return parent.key.name || parent.key.value;
  }
  return null;
}

/** Percorre a árvore em pós-ordem (filhos antes do pai), com o pai e as funções em volta. */
function walk(node, parent, visit, scope = []) {
  if (!node || typeof node.type !== 'string') return;
  const name = fnName(node, parent);
  const inner = name ? [...scope, name] : scope;
  for (const key of Object.keys(node)) {
    if (key === 'loc' || key === 'start' || key === 'end') continue;
    const v = node[key];
    if (Array.isArray(v)) for (const c of v) { if (c && typeof c.type === 'string') walk(c, node, visit, inner); }
    else if (v && typeof v.type === 'string') walk(v, node, visit, inner);
  }
  visit(node, parent, scope);
}

/**
 * Quebra uma string (com marcadores das interpolações) em pedaços: 'raw' (marcação), 'text'
 * (texto entre tags) e 'attr' (valor de um atributo visível). Uma string pode começar no meio
 * de uma tag (o resto de um `<b title="` montado por concatenação) ou terminar nela.
 */
export function htmlPieces(v) {
  const pieces = [];
  const push = (type, s) => { if (s) pieces.push({ type, s }); };
  let i = 0;
  const n = v.length;
  const lt = v.indexOf('<'), gt = v.indexOf('>');
  let inTag = gt >= 0 && (lt < 0 || gt < lt) && /(^\s*["'/]|=\s*["']|^\s+[\w:-]+\s*=)/.test(v.slice(0, gt));
  if (inTag && /^\s*["']/.test(v)) { // fecha o valor de um atributo aberto na string anterior
    const q = v.trimStart()[0];
    const end = v.indexOf(q, v.indexOf(q)) + 1;
    push('raw', v.slice(0, end));
    i = end;
  }
  while (i < n) {
    if (!inTag) {
      let j = i;
      while (j < n && !(v[j] === '<' && /[A-Za-z/!]/.test(v[j + 1] || ''))) j++;
      push('text', v.slice(i, j));
      i = j;
      if (i < n) inTag = true;
      continue;
    }
    // dentro de uma tag: nome, atributos, até o '>'
    let j = i;
    if (v[j] === '<') {
      if (v.startsWith('<!--', j)) {
        const end = v.indexOf('-->', j);
        const stop = end < 0 ? n : end + 3;
        push('raw', v.slice(j, stop));
        i = stop; inTag = false; continue;
      }
      j++;
      while (j < n && /[A-Za-z0-9/!:-]/.test(v[j])) j++;
    }
    let rawStart = i;
    while (j < n && v[j] !== '>') {
      const am = /^([\w:-]+)(\s*=\s*)(["'])/.exec(v.slice(j));
      if (am) {
        const q = am[3];
        const vStart = j + am[0].length;
        const close = v.indexOf(q, vStart);
        if (ATTRS.has(am[1].toLowerCase()) && close >= 0) {
          push('raw', v.slice(rawStart, vStart));
          push('attr', v.slice(vStart, close));
          rawStart = close;
        }
        j = close < 0 ? n : close + 1;
        continue;
      }
      j++;
    }
    if (j < n) j++; // o '>'
    push('raw', v.slice(rawStart, j));
    i = j;
    inTag = false;
  }
  return pieces;
}

/** Chave de um trecho: sem os espaços das pontas, espaços internos juntos, marcadores como {0}. */
function runKey(s) {
  const lead = /^\s*/.exec(s)[0];
  const trail = /\s*$/.exec(s.slice(lead.length))[0];
  const core = s.slice(lead.length, s.length - trail.length);
  const order = [];
  const key = core.replace(/\s+/g, ' ').replace(MARK_RE, (m, d) => `{${order.push(Number(d)) - 1}}`);
  return { lead, trail, key, order };
}

// palavras do português que não são nomes de classe, chaves ou palavras do inglês: um texto em
// minúsculas sem acento ("nunca", "jogo usa", "sem limite") só é reconhecido por elas
const PT_WORDS = new Set(('jogo jogos ajuste ajustes controle controles toque arquivo arquivos nenhum nenhuma nunca ontem hoje '
  + 'mudado mudados mudada mudadas sem limite quadros mediana segue seguem modo usa usam ativo ativos ativa ativas com para '
  + 'pelo pela pelos pelas dos das uma um umas uns mais menos ainda agora depois antes aqui ali sempre tudo todos todas '
  + 'abrir fechar voltar sair salvar apagar mudar usar ver ligado desligado ligar desligar padrão minuto minutos hora horas '
  + 'dia dias semana semanas mês meses segundo segundos vez vezes este esta esse essa isso isto quando onde como porque '
  + 'perfil perfis capa capas pasta pastas sessão sessões execução execuções versão padrões outro outra '
  + 'outros outras cada qual quais nada algum alguma alguns algumas jogado jogada jogados tempo atual novo nova novos novas').split(' '));

/** Texto para pessoas? Tem letra fora dos {n} e não é um identificador, caminho ou seletor. */
export function looksHuman(key) {
  const letters = key.replace(/\{\d+\}/g, '0').trim();
  if (!/[A-Za-zÀ-ÿ]/.test(letters)) return false;
  if (/[À-ÿ]/.test(letters)) return true;
  // seletores CSS, declarações de estilo e fontes de canvas não são texto
  const css = /^[#.[]|[{}]|^[a-z-]+\s*:\s*[^\s]|;\s*[a-z-]+\s*:|\bpx\b|^\d{3}\s|^[a-z-]+\(/.test(letters);
  // palavras soltas: um pedaço com hífen, ponto ou sublinhado ("ch-ver", "a.b") é um nome, não uma palavra
  const words = letters.toLowerCase().split(/[\s,;:!?()"“”'…·/]+/).filter(w => /^[a-zà-ÿ]+\.?$/.test(w)).map(w => w.replace(/\.$/, ''));
  if (!css && words.some(w => PT_WORDS.has(w))) return true;
  if (css) return false;
  if (/\s/.test(letters)) {
    // listas de classes ("chip on", "b-card d-M") não são texto
    return /[A-Za-z]{2}/.test(letters) && !/^[a-z0-9_]+(-[A-Za-z0-9_]*)*( [a-z0-9_]+(-[A-Za-z0-9_]*)*)+$/.test(letters);
  }
  return /^[A-ZÀ-Þ][a-zà-ÿ]+[.!?…:]?$/.test(letters);
}

const escTemplate = s => s.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${');

/**
 * Traduz o código. `dict` é { "texto em pt": "texto em en" }; devolve { code, used, missing }:
 * as chaves usadas e os trechos com cara de texto que não estão no dicionário.
 */
export function translateJs(code, dict, file = '') {
  const ctxFile = file.replace(/^src\/tool\//, '');
  const has = k => Object.prototype.hasOwnProperty.call(dict, k);
  /** A chave do dicionário para um texto: com o escopo mais justo que existir. */
  const keyFor = (key, scope) => {
    for (let i = scope.length - 1; i >= 0; i--) if (has(`@${ctxFile}#${scope[i]}|${key}`)) return `@${ctxFile}#${scope[i]}|${key}`;
    if (has(`@${ctxFile}|${key}`)) return `@${ctxFile}|${key}`;
    return has(key) ? key : null;
  };
  let ast;
  try { ast = acorn.parse(code, { ecmaVersion: 'latest', sourceType: 'script', allowHashBang: true }); }
  catch { ast = acorn.parse(code, { ecmaVersion: 'latest', sourceType: 'module' }); }
  const used = new Set();
  const missing = new Map();
  let edits = []; // { start, end, text }, sem sobreposição
  const line = pos => code.slice(0, pos).split('\n').length;
  const slice = (start, end) => {
    let out = '', at = start;
    for (const e of edits.filter(x => x.start >= start && x.end <= end).sort((a, b) => a.start - b.start)) {
      out += code.slice(at, e.start) + e.text;
      at = e.end;
    }
    return out + code.slice(at, end);
  };

  /** Traduz a string virtual; devolve a nova ou null se nada mudou. */
  const translate = (v, node, scope) => {
    let changed = false;
    const out = htmlPieces(v).map(p => {
      if (p.type === 'raw') return p.s;
      const { lead, trail, key, order } = runKey(p.s);
      if (!key) return p.s;
      const dk = keyFor(key, scope);
      if (!dk) {
        if (looksHuman(key) && !missing.has(key)) missing.set(key, `${file}:${line(node.start)}`);
        return p.s;
      }
      used.add(dk);
      const en = dict[dk];
      const seen = new Set();
      const body = en.replace(/\{(\d+)\}/g, (m, d) => {
        const k = Number(d);
        if (k >= order.length || seen.has(k)) throw new Error(`${file}:${line(node.start)}: a tradução de "${key}" usa {${k}} a mais`);
        seen.add(k);
        return MARK(order[k]);
      });
      if (seen.size !== order.length) throw new Error(`${file}:${line(node.start)}: a tradução de "${key}" perdeu um {n}`);
      if (lead + body + trail !== p.s) changed = true;
      return lead + body + trail;
    }).join('');
    return changed ? out : null;
  };

  walk(ast, null, (node, parent, scope) => {
    if (node.type === 'Literal' && typeof node.value === 'string') {
      if (parent && parent.type === 'Property' && parent.key === node && !parent.computed) return;
      if (parent && /^(Import|Export)/.test(parent.type)) return;
      if (parent && parent.type === 'ExpressionStatement' && parent.directive) return;
      const out = translate(node.value, node, scope);
      if (out != null) edits.push({ start: node.start, end: node.end, text: JSON.stringify(out) });
      return;
    }
    if (node.type === 'TemplateLiteral') {
      if (parent && parent.type === 'TaggedTemplateExpression') return;
      const v = node.quasis.map((q, i) => q.value.cooked + (i < node.expressions.length ? MARK(i) : '')).join('');
      const out = translate(v, node, scope);
      if (out == null) return;
      const exprs = node.expressions.map(e => slice(e.start, e.end));
      const parts = out.split(MARK_RE); // texto, índice, texto, índice…
      let text = '`';
      for (let k = 0; k < parts.length; k++) text += k % 2 ? `\${${exprs[Number(parts[k])]}}` : escTemplate(parts[k]);
      text += '`';
      edits = edits.filter(e => !(e.start >= node.start && e.end <= node.end));
      edits.push({ start: node.start, end: node.end, text });
    }
  });

  edits.sort((a, b) => b.start - a.start);
  let out = code;
  for (const e of edits) out = out.slice(0, e.start) + e.text + out.slice(e.end);
  return { code: out, used, missing };
}

/** Todos os trechos com cara de texto de um arquivo (para montar e revisar o dicionário). */
export function extractRuns(code, file = '') {
  const { missing } = translateJs(code, {}, file);
  return missing;
}
