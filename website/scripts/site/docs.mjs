// Páginas da documentação: barra lateral por seção, conteúdo, índice da página e anterior/próxima.
// Cada idioma tem as suas páginas (site.docs[lang]); `doc.key` é o slug em português, que pareia
// a página com a do outro idioma.
import { html, raw, icon } from '../lib/html.mjs';
import { rel } from '../lib/paths.mjs';

const sectionTitle = (site, sec, L) => (L.pt ? sec.title.pt : sec.title.en);

function sidebar(site, current, L) {
  const { tx } = L;
  const r = to => rel(current, to);
  return html`<aside class="docs-side" aria-label="${tx('Documentation', 'Documentação')}">
  <button class="docs-side-toggle" type="button" aria-expanded="false" aria-controls="docs-nav">${tx('Documentation pages', 'Páginas da documentação')} ${icon('chevD', 18)}</button>
  <nav id="docs-nav">
    <ul><li><a class="home" href="${r(L.docs)}"${current === L.docs ? raw(' aria-current="page"') : ''}>${tx('Overview', 'Visão geral')}</a></li></ul>
    ${site.config.docSections.map(sec => {
      const pages = site.docs[L.id].filter(d => d.section === sec.id);
      if (!pages.length) return '';
      return html`<div><h2>${sectionTitle(site, sec, L)}</h2><ul>${pages.map(d => html`<li><a href="${r(d.path)}"${d.path === current ? raw(' aria-current="page"') : ''}>${d.navTitle || d.title}</a></li>`)}</ul></div>`;
    })}
  </nav>
</aside>`;
}

function toc(headings, L) {
  const items = headings.filter(h => h.level === 2 || h.level === 3);
  if (items.length < 2) return html`<aside class="docs-toc" aria-hidden="true"></aside>`;
  return html`<aside class="docs-toc" aria-labelledby="toc-t">
  <h2 id="toc-t">${L.tx('On this page', 'Nesta página')}</h2>
  <ol>${items.map(h => html`<li class="l${h.level}"><a href="#${h.id}">${h.text}</a></li>`)}</ol>
</aside>`;
}

/** Linha de versão: para qual versão a página foi escrita e se o código mudou desde a revisão. */
function versionLine(site, L) {
  const { stable } = site.v;
  const { tx } = L;
  const parts = [];
  if (stable) {
    const name = stable.label.toLowerCase();
    parts.push(html`<span class="tag ok">${icon('tag', 14)}${stable.label}</span>`);
    parts.push(site.data.sameChannels
      ? html`<span>${tx(`Applies to ${name} and to the current main, which has the same settings and data.`, `Vale para a ${name} e para o main atual, que tem os mesmos ajustes e dados.`)}</span>`
      : html`<span>${tx(`Written for ${name}; marked passages apply only to development.`, `Escrita para a ${name}; trechos marcados valem só no desenvolvimento.`)}</span>`);
  }
  return html`<p class="page-meta">${parts}</p>`;
}

function reviewLine(site, doc, L) {
  const { tx } = L;
  const rv = doc.review;
  const bits = [];
  if (rv && rv.date) bits.push(html`<span>${tx('Reviewed against the code of', 'Revisada com o código de')} ${L.date(rv.date)} (<span class="u-mono">${rv.short}</span>)</span>`);
  bits.push(html`<a href="${site.config.repoUrl}/edit/main/website/${doc.file}" rel="noopener">${icon('external', 14)} ${tx('Edit this page', 'Editar esta página')}</a>`);
  return html`<p class="page-meta">${bits}</p>`;
}

function staleNotice(doc, L) {
  const { tx } = L;
  const rv = doc.review;
  if (!rv || !rv.changed || !rv.changed.length) return '';
  const list = rv.changed.slice(0, 6);
  const files = list.map((f, i) => html`${i ? ', ' : ''}<code>${f}</code>`);
  const more = rv.changed.length > list.length ? tx(` and ${rv.changed.length - list.length} more`, ` e mais ${rv.changed.length - list.length}`) : '';
  return html`<div class="callout aviso" role="note"><p class="callout-title">${icon('warn', 18)}<span>${tx('The code changed after the review', 'O código mudou depois da revisão')}</span></p>
<p>${tx(html`App files cited on this page changed after its last review: ${files}${more}. Check against the version you use; the page may be out of date on those points.`, html`Arquivos do app citados nesta página mudaram depois da última revisão: ${files}${more}. Confira na versão que você usa; a página pode estar desatualizada nesses pontos.`)}</p></div>`;
}

export function docPage(site, doc, L) {
  const { tx } = L;
  const list = site.docs[L.id];
  const i = list.indexOf(doc);
  const prev = list[i - 1];
  const next = list[i + 1];
  const r = to => rel(doc.path, to);
  const sec = site.config.docSections.find(s => s.id === doc.section);
  const page = {
    path: doc.path,
    lang: L.id,
    alternates: site.alternatesOf(doc.key),
    section: doc.key === 'historico' ? 'historico' : 'docs',
    title: doc.title,
    description: doc.description,
    ogType: 'article',
    bodyClass: 'is-docs',
  };
  const content = html`<div class="wrap docs">
${sidebar(site, doc.path, L)}
<article class="docs-main">
  <nav class="crumbs" aria-label="${tx('Breadcrumb', 'Caminho')}"><a href="${r(L.docs)}">${tx('Documentation', 'Documentação')}</a><span aria-hidden="true">›</span><span>${sec ? sectionTitle(site, sec, L) : ''}</span></nav>
  <header>
    <h1>${doc.title}</h1>
    ${doc.description ? html`<p class="desc">${doc.description}</p>` : ''}
    ${versionLine(site, L)}
    ${reviewLine(site, doc, L)}
  </header>
  ${staleNotice(doc, L)}
  <div class="prose">
${raw(doc.html)}
  </div>
  <nav class="pager" aria-label="${tx('Neighboring pages', 'Páginas vizinhas')}">
    ${prev ? html`<a class="prev" href="${r(prev.path)}"><small>${tx('Previous', 'Anterior')}</small>${prev.title}</a>` : html`<span></span>`}
    ${next ? html`<a class="next" href="${r(next.path)}"><small>${tx('Next', 'Próxima')}</small>${next.title}</a>` : ''}
  </nav>
</article>
${toc(doc.headings, L)}
</div>`;
  return { page, content };
}

export function docsIndex(site, L) {
  const { tx } = L;
  const path = L.docs;
  const r = to => rel(path, to);
  const page = {
    path,
    lang: L.id,
    alternates: site.alternatesOf(''),
    section: 'docs',
    title: tx('Documentation', 'Documentação'),
    description: tx(
      'Xendroid+ documentation: installation, requirements, interface, settings, drivers, controls, compatibility, troubleshooting and logs, building and release history.',
      'Documentação do Xendroid+ em português: instalação, requisitos, interface, ajustes, drivers, controles, compatibilidade, problemas e logs, compilação e histórico.'),
    bodyClass: 'is-docs',
  };
  const { stable } = site.v;
  const from = stable ? stable.label.toLowerCase() : null;
  const content = html`<div class="wrap docs">
${sidebar(site, path, L)}
<article class="docs-main">
  <header>
    <h1>${tx('Documentation', 'Documentação')}</h1>
    <p class="desc">${tx(
      html`How to install, configure and troubleshoot Xendroid+, written from the code of ${from || 'the published version'}. Use search (<kbd>/</kbd>) to find a setting by its name or its TOML key.`,
      html`Como instalar, configurar e diagnosticar o Xendroid+, escrito a partir do código ${from ? `da ${from}` : 'publicado'}. Use a busca (<kbd>/</kbd>) para achar um ajuste pelo nome ou pela chave do TOML.`)}</p>
    ${versionLine(site, L)}
  </header>
  <div class="doc-index">
    ${site.config.docSections.map(sec => {
      const pages = site.docs[L.id].filter(d => d.section === sec.id);
      if (!pages.length) return '';
      return html`<section aria-labelledby="sec-${sec.id}"><h2 id="sec-${sec.id}">${sectionTitle(site, sec, L)}</h2><ul>${pages.map(d => html`<li><a href="${r(d.path)}"><b>${d.title}</b><span>${d.description}</span></a></li>`)}</ul></section>`;
    })}
  </div>
</article>
<aside class="docs-toc" aria-hidden="true"></aside>
</div>`;
  return { page, content };
}
