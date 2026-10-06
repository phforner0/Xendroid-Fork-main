// Páginas da documentação: barra lateral por seção, conteúdo, índice da página e anterior/próxima.
import { html, raw, esc, icon, fmtDate } from '../lib/html.mjs';
import { rel } from '../lib/paths.mjs';

function sidebar(site, current) {
  const r = to => rel(current, to);
  return html`<aside class="docs-side" aria-label="Documentação">
  <button class="docs-side-toggle" type="button" aria-expanded="false" aria-controls="docs-nav">Páginas da documentação ${icon('arrowR', 18)}</button>
  <nav id="docs-nav">
    <ul><li><a class="home" href="${r('docs/')}"${current === 'docs/' ? raw(' aria-current="page"') : ''}>Visão geral</a></li></ul>
    ${site.config.docSections.map(sec => {
      const pages = site.docs.filter(d => d.section === sec.id);
      if (!pages.length) return '';
      return html`<div><h2>${sec.title}</h2><ul>${pages.map(d => html`<li><a href="${r(d.path)}"${d.path === current ? raw(' aria-current="page"') : ''}>${d.navTitle || d.title}</a></li>`)}</ul></div>`;
    })}
  </nav>
</aside>`;
}

function toc(headings) {
  const items = headings.filter(h => h.level === 2 || h.level === 3);
  if (items.length < 2) return html`<aside class="docs-toc" aria-hidden="true"></aside>`;
  return html`<aside class="docs-toc" aria-labelledby="toc-t">
  <h2 id="toc-t">Nesta página</h2>
  <ol>${items.map(h => html`<li class="l${h.level}"><a href="#${h.id}">${h.text}</a></li>`)}</ol>
</aside>`;
}

/** Linha de versão: para qual versão a página foi escrita e se o código mudou desde a revisão. */
function versionLine(site, doc) {
  const { stable } = site.v;
  const parts = [];
  if (stable) {
    parts.push(html`<span class="tag ok">${icon('tag', 14)}${stable.label}</span>`);
    parts.push(site.data.sameChannels
      ? html`<span>Vale para a ${stable.label.toLowerCase()} e para o main atual, que não mudou o app depois dela.</span>`
      : html`<span>Escrita para a ${stable.label.toLowerCase()}; trechos marcados valem só no desenvolvimento.</span>`);
  }
  return html`<p class="page-meta">${parts}</p>`;
}

function reviewLine(site, doc) {
  const rv = doc.review;
  const bits = [];
  if (rv && rv.date) bits.push(html`<span>Revisada com o código de ${fmtDate(rv.date)} (<span class="u-mono">${rv.short}</span>)</span>`);
  bits.push(html`<a href="${site.config.repoUrl}/edit/main/website/content/docs/${doc.file}" rel="noopener">${icon('external', 14)} Editar esta página</a>`);
  return html`<p class="page-meta">${bits}</p>`;
}

function staleNotice(doc) {
  const rv = doc.review;
  if (!rv || !rv.changed || !rv.changed.length) return '';
  const list = rv.changed.slice(0, 6);
  return html`<div class="callout aviso" role="note"><p class="callout-title">${icon('warn', 18)}<span>O código mudou depois da revisão</span></p>
<p>Arquivos do app citados nesta página mudaram depois da última revisão: ${list.map((f, i) => html`${i ? ', ' : ''}<code>${f}</code>`)}${rv.changed.length > list.length ? ` e mais ${rv.changed.length - list.length}` : ''}. Confira na versão que você usa; a página pode estar desatualizada nesses pontos.</p></div>`;
}

export function docPage(site, doc) {
  const i = site.docs.indexOf(doc);
  const prev = site.docs[i - 1];
  const next = site.docs[i + 1];
  const r = to => rel(doc.path, to);
  const sec = site.config.docSections.find(s => s.id === doc.section);
  const page = {
    path: doc.path,
    section: doc.path === 'docs/historico/' ? 'historico' : 'docs',
    title: doc.title,
    description: doc.description,
    ogType: 'article',
    bodyClass: 'is-docs',
  };
  const content = html`<div class="wrap docs">
${sidebar(site, doc.path)}
<article class="docs-main">
  <nav class="crumbs" aria-label="Caminho"><a href="${r('docs/')}">Documentação</a><span aria-hidden="true">›</span><span>${sec ? sec.title : ''}</span></nav>
  <header>
    <h1>${doc.title}</h1>
    ${doc.description ? html`<p class="desc">${doc.description}</p>` : ''}
    ${versionLine(site, doc)}
    ${reviewLine(site, doc)}
  </header>
  ${staleNotice(doc)}
  <div class="prose">
${raw(doc.html)}
  </div>
  <nav class="pager" aria-label="Páginas vizinhas">
    ${prev ? html`<a class="prev" href="${r(prev.path)}"><small>Anterior</small>${prev.title}</a>` : html`<span></span>`}
    ${next ? html`<a class="next" href="${r(next.path)}"><small>Próxima</small>${next.title}</a>` : ''}
  </nav>
</article>
${toc(doc.headings)}
</div>`;
  return { page, content };
}

export function docsIndex(site) {
  const path = 'docs/';
  const r = to => rel(path, to);
  const page = {
    path,
    section: 'docs',
    title: 'Documentação',
    description: 'Documentação do Xendroid+ em português: instalação, requisitos, interface, ajustes, drivers, controles, compatibilidade, problemas e logs, compilação e histórico.',
    bodyClass: 'is-docs',
  };
  const { stable } = site.v;
  const content = html`<div class="wrap docs">
${sidebar(site, path)}
<article class="docs-main">
  <header>
    <h1>Documentação</h1>
    <p class="desc">Como instalar, configurar e diagnosticar o Xendroid+, escrito a partir do código ${stable ? `da ${stable.label.toLowerCase()}` : 'publicado'}. Use a busca (<kbd>/</kbd>) para achar um ajuste pelo nome ou pela chave do TOML.</p>
    ${versionLine(site, null)}
  </header>
  <div class="doc-index">
    ${site.config.docSections.map(sec => {
      const pages = site.docs.filter(d => d.section === sec.id);
      if (!pages.length) return '';
      return html`<section aria-labelledby="sec-${sec.id}"><h2 id="sec-${sec.id}">${sec.title}</h2><ul>${pages.map(d => html`<li><a href="${r(d.path)}"><b>${d.title}</b><span>${d.description}</span></a></li>`)}</ul></section>`;
    })}
  </div>
</article>
<aside class="docs-toc" aria-hidden="true"></aside>
</div>`;
  return { page, content };
}
