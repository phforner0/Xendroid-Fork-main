// Markdown da documentação: markdown-it com algumas convenções do site.
//
//  ---                       front matter (title, description, section, order, ...)
//  {{caminho.do.valor}}      valor dos dados do build (ex.: {{estavel.build}}); desconhecido = erro
//  {{> bloco arg=valor}}     bloco gerado a partir dos dados (sozinho numa linha)
//  ::: nota | aviso | dica   caixas de destaque; "::: versao desde=<sha>" marca um trecho por versão
//  [texto](doc:pagina#ancora)       link para outra página da documentação
//  [texto](repo:caminho/arquivo)    arquivo ou pasta do repositório no GitHub
//  [texto](ferramenta:tela)         tela da ferramenta interativa
import MarkdownIt from 'markdown-it';
import { uniqueSlugger } from './paths.mjs';
import { esc } from './html.mjs';

/** Front matter simples (chave: valor, listas [a, b], números e booleanos). */
export function frontMatter(src, file) {
  const m = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(src);
  if (!m) return { data: {}, body: src };
  const data = {};
  for (const line of m[1].split(/\r?\n/)) {
    if (!line.trim() || line.trim().startsWith('#')) continue;
    const kv = /^([A-Za-z_][\w-]*):\s*(.*)$/.exec(line);
    if (!kv) throw new Error(`${file}: linha de front matter inválida: ${line}`);
    let v = kv[2].trim();
    if (/^\[.*\]$/.test(v)) v = v.slice(1, -1).split(',').map(s => s.trim().replace(/^["']|["']$/g, '')).filter(Boolean);
    else if (/^-?\d+(\.\d+)?$/.test(v)) v = Number(v);
    else if (v === 'true' || v === 'false') v = v === 'true';
    else v = v.replace(/^["']|["']$/g, '');
    data[kv[1]] = v;
  }
  return { data, body: src.slice(m[0].length) };
}

/** Bloco ::: tipo args ... ::: */
function containerPlugin(md) {
  md.block.ruler.before('fence', 'xdr_container', (state, startLine, endLine, silent) => {
    const start = state.bMarks[startLine] + state.tShift[startLine];
    const max = state.eMarks[startLine];
    const line = state.src.slice(start, max);
    const m = /^:::\s*([a-z-]+)\s*(.*)$/.exec(line);
    if (!m) return false;
    if (silent) return true;
    let next = startLine;
    let depth = 1;
    while (++next < endLine) {
      const s = state.src.slice(state.bMarks[next] + state.tShift[next], state.eMarks[next]).trim();
      if (/^:::\s*[a-z-]+/.test(s)) depth++;
      else if (s === ':::') { depth--; if (depth === 0) break; }
    }
    if (depth !== 0) throw new Error(`bloco "::: ${m[1]}" sem ":::" de fechamento (linha ${startLine + 1})`);
    const args = Object.fromEntries([...m[2].matchAll(/([a-z]+)=("[^"]*"|\S+)/g)].map(a => [a[1], a[2].replace(/^"|"$/g, '')]));
    const title = m[2].replace(/([a-z]+)=("[^"]*"|\S+)/g, '').trim();
    const open = state.push('xdr_container_open', 'div', 1);
    open.info = m[1];
    open.meta = { args, title };
    open.map = [startLine, next];
    const oldParent = state.parentType;
    const oldLine = state.lineMax;
    state.parentType = 'container';
    state.lineMax = next;
    state.md.block.tokenize(state, startLine + 1, next);
    state.parentType = oldParent;
    state.lineMax = oldLine;
    state.push('xdr_container_close', 'div', -1).info = m[1];
    state.line = next + 1;
    return true;
  }, { alt: ['paragraph', 'reference', 'blockquote', 'list'] });
}

const CALLOUT = { nota: ['Nota', 'info'], aviso: ['Atenção', 'warn'], dica: ['Dica', 'check'] };

/**
 * Cria o renderizador. `ctx` traz: data (para {{ }}), resolveDoc(slug, anchor) → url ou erro,
 * repoUrl(path) → url, toolUrl(tela) → url, version(sha) → { label, kind } e icon(name).
 */
export function createMarkdown(ctx) {
  const md = new MarkdownIt({ html: true, linkify: false, typographer: false });
  containerPlugin(md);

  // ids e âncoras nos títulos h2–h4
  md.core.ruler.push('xdr_heading_ids', state => {
    const slug = uniqueSlugger();
    const toks = state.tokens;
    for (let i = 0; i < toks.length; i++) {
      const t = toks[i];
      if (t.type !== 'heading_open' || !['h2', 'h3', 'h4'].includes(t.tag)) continue;
      const inline = toks[i + 1];
      const text = inline.children.filter(c => c.type === 'text' || c.type === 'code_inline').map(c => c.content).join('');
      const explicit = /\s*\{#([a-z0-9-]+)\}\s*$/.exec(inline.content);
      let id;
      if (explicit) {
        id = explicit[1];
        const last = inline.children[inline.children.length - 1];
        last.content = last.content.replace(/\s*\{#[a-z0-9-]+\}\s*$/, '');
      } else id = slug(text);
      t.attrSet('id', id);
      t.meta = { id, text: text.replace(/\s*\{#[a-z0-9-]+\}\s*$/, '') };
    }
  });

  md.renderer.rules.heading_open = (tokens, idx, opts, env, self) => {
    const t = tokens[idx];
    if (!t.meta) return self.renderToken(tokens, idx, opts);
    return `<${t.tag} id="${t.meta.id}">`;
  };
  md.renderer.rules.heading_close = (tokens, idx) => {
    const open = tokens.slice(0, idx).reverse().find(t => t.type === 'heading_open');
    if (!open || !open.meta) return `</${tokens[idx].tag}>\n`;
    return ` <a class="anchor" href="#${open.meta.id}" aria-label="Link para esta seção: ${esc(open.meta.text)}">#</a></${tokens[idx].tag}>\n`;
  };

  md.renderer.rules.xdr_container_open = (tokens, idx) => {
    const t = tokens[idx];
    const { args, title } = t.meta;
    if (t.info === 'versao') {
      const v = ctx.version(args);
      return `<div class="callout version ${v.kind}" role="note"><p class="callout-title">${ctx.icon(v.kind === 'dev' ? 'flask' : 'tag', 18)}<span>${esc(v.label)}</span></p>\n`;
    }
    if (t.info === 'detalhes') return `<details class="more"><summary>${esc(title || 'Mais detalhes')}</summary>\n`;
    const c = CALLOUT[t.info];
    if (!c) throw new Error(`tipo de bloco desconhecido: ::: ${t.info}`);
    return `<div class="callout ${t.info}" role="note"><p class="callout-title">${ctx.icon(c[1], 18)}<span>${esc(title || c[0])}</span></p>\n`;
  };
  md.renderer.rules.xdr_container_close = (tokens, idx) => (tokens[idx].info === 'detalhes' ? '</details>\n' : '</div>\n');

  // links com esquemas do site
  const defaultLink = md.renderer.rules.link_open || ((tokens, idx, opts, env, self) => self.renderToken(tokens, idx, opts));
  md.renderer.rules.link_open = (tokens, idx, opts, env, self) => {
    const t = tokens[idx];
    let href = t.attrGet('href') || '';
    let external = false;
    if (href.startsWith('doc:')) {
      const [slug, anchor] = href.slice(4).split('#');
      href = ctx.resolveDoc(slug, anchor, env);
    } else if (href.startsWith('repo:')) {
      href = ctx.repoUrl(href.slice(5), env);
      external = true;
    } else if (href.startsWith('ferramenta:')) {
      href = ctx.toolUrl(href.slice(11), env);
    } else if (/^https?:/.test(href)) {
      external = true;
    } else if (href.startsWith('#')) {
      (env.anchorsUsed ||= []).push(href.slice(1));
    } else {
      throw new Error(`${env.file}: link "${href}" sem esquema; use doc:, repo:, ferramenta:, #âncora ou https://`);
    }
    t.attrSet('href', href);
    if (external) { t.attrSet('rel', 'noopener'); t.attrJoin('class', 'ext'); }
    return defaultLink(tokens, idx, opts, env, self);
  };

  // tabelas roláveis no celular
  md.renderer.rules.table_open = () => '<div class="table-wrap" tabindex="0"><table>\n';
  md.renderer.rules.table_close = () => '</table></div>\n';

  // blocos de código com botão de copiar (o JS do site acrescenta o botão)
  md.renderer.rules.fence = (tokens, idx) => {
    const t = tokens[idx];
    const lang = (t.info || '').trim().split(/\s+/)[0];
    const code = highlight(t.content, lang);
    return `<div class="code"${lang ? ` data-lang="${esc(lang)}"` : ''}><pre><code${lang ? ` class="language-${esc(lang)}"` : ''}>${code}</code></pre></div>\n`;
  };

  return {
    /** Renderiza o corpo (sem front matter) e devolve html, títulos e seções para a busca. */
    render(body, env) {
      const withData = body.replace(/\{\{(?!>)\s*([a-zA-Z0-9_.]+)\s*\}\}/g, (m, p) => {
        const v = p.split('.').reduce((o, k) => (o == null ? undefined : o[k]), ctx.data);
        if (v === undefined || v === null) throw new Error(`${env.file}: valor desconhecido {{${p}}}`);
        return String(v);
      });
      const blocks = [];
      const withBlocks = withData.replace(/^\{\{>\s*([a-z0-9-]+)((?:\s+[a-z]+=(?:"[^"]*"|\S+))*)\s*\}\}\s*$/gm, (m, name, argStr) => {
        const args = Object.fromEntries([...argStr.matchAll(/([a-z]+)=("[^"]*"|\S+)/g)].map(a => [a[1], a[2].replace(/^"|"$/g, '')]));
        blocks.push({ name, args });
        return `\n<div data-gerado="${blocks.length - 1}"></div>\n`;
      });
      const tokens = md.parse(withBlocks, env);
      let out = md.renderer.render(tokens, md.options, env);
      out = out.replace(/<div data-gerado="(\d+)"><\/div>/g, (m, i) => {
        const b = blocks[Number(i)];
        const gen = ctx.blocks[b.name];
        if (!gen) throw new Error(`${env.file}: bloco gerado desconhecido {{> ${b.name}}}`);
        const res = gen(b.args, env);
        (env.generatedHeadings ||= []).push(...(res.headings || []));
        return res.html;
      });
      const headings = tokens.filter(t => t.type === 'heading_open' && t.meta).map(t => ({ level: Number(t.tag[1]), id: t.meta.id, text: t.meta.text }));
      return { html: out, headings: headings.concat(env.generatedHeadings || []), sections: searchSections(tokens, env) };
    },
  };
}

/** Texto de cada seção (por h2/h3) para o índice de busca. */
function searchSections(tokens, env) {
  const sections = [];
  let cur = { id: '', title: env.title || '', text: [] };
  const push = () => { if (cur.text.length || cur.id) sections.push({ id: cur.id, title: cur.title, text: cur.text.join(' ').replace(/\s+/g, ' ').trim() }); };
  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i];
    if (t.type === 'heading_open' && t.meta && (t.tag === 'h2' || t.tag === 'h3')) {
      push();
      cur = { id: t.meta.id, title: t.meta.text, text: [] };
      i++;
      continue;
    }
    if (t.type === 'inline') cur.text.push(t.children.filter(c => c.type === 'text' || c.type === 'code_inline').map(c => c.content).join(''));
    if (t.type === 'fence') cur.text.push(t.content.slice(0, 400));
  }
  push();
  return sections;
}

/** Realce mínimo para TOML e shell: comentários, seções, chaves, strings e números. */
export function highlight(code, lang) {
  const e = esc(code);
  if (lang === 'toml') {
    return e.split('\n').map(l => {
      if (/^\s*#/.test(l)) return `<span class="t-c">${l}</span>`;
      if (/^\s*\[.+\]\s*$/.test(l)) return `<span class="t-s">${l}</span>`;
      const m = /^(\s*)([A-Za-z0-9_.-]+)(\s*=\s*)(.*?)(\s*#.*)?$/.exec(l);
      if (!m) return l;
      return `${m[1]}<span class="t-k">${m[2]}</span>${m[3]}<span class="t-v">${m[4]}</span>${m[5] ? `<span class="t-c">${m[5]}</span>` : ''}`;
    }).join('\n');
  }
  if (lang === 'sh' || lang === 'bash' || lang === 'shell') {
    return e.split('\n').map(l => (/^\s*#/.test(l) ? `<span class="t-c">${l}</span>` : l)).join('\n');
  }
  return e;
}
