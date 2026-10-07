// Página 404 do GitHub Pages: servida em qualquer endereço que não existe, por isso os links
// são absolutos (config.basePath). Uma página só para os dois idiomas: inglês e, logo abaixo,
// português (o script do site põe o português primeiro quando o endereço é de pt-br/).
import { html, raw, icon } from '../lib/html.mjs';

export function notFound(site) {
  const b = site.config.basePath;
  const page = {
    path: '404.html',
    lang: 'en',
    absolute: true,
    noindex: true,
    section: '',
    title: 'Page not found',
    description: 'This address does not exist on the Xendroid+ site.',
    head: html`<meta name="robots" content="noindex">`,
  };
  // um só h1: o do idioma que vem primeiro (o script troca os níveis quando põe o português antes)
  const block = (L, lang, texts, home, docs, h) => html`<div class="nf-block" lang="${lang}" data-nf="${L}">
    <span class="eyebrow">${texts.eyebrow}</span>
    ${raw(`<${h} style="font-size:var(--step-4);margin-bottom:16px">`)}${texts.title}${raw(`</${h}>`)}
    <p style="color:var(--fg-2);font-size:var(--step-1);margin-bottom:24px">${texts.lead}</p>
    <p style="display:flex;flex-wrap:wrap;gap:10px">
      <a class="button primary" href="${home}">${icon('arrowR', 18)}${texts.home}</a>
      <a class="button" href="${docs}">${icon('book', 18)}${texts.docs}</a>
      <button class="button" type="button" data-open-search>${icon('search', 18)}${texts.search}</button>
    </p>
  </div>`;
  const content = html`<section class="section">
  <div class="wrap nf" style="max-width:760px;display:grid;gap:48px">
    ${block('en', 'en', { eyebrow: 'Error 404', title: 'This page does not exist', lead: html`The address may have changed or been mistyped. Search (<kbd>/</kbd>) finds any documentation page and any app setting.`, home: 'Home page', docs: 'Documentation', search: 'Search' }, b, `${b}docs/`, 'h1')}
    ${block('pt', 'pt-BR', { eyebrow: 'Erro 404', title: 'Esta página não existe', lead: html`O endereço pode ter mudado ou estar digitado errado. A busca (<kbd>/</kbd>) acha qualquer página da documentação e qualquer ajuste do app.`, home: 'Página inicial', docs: 'Documentação', search: 'Buscar' }, `${b}pt-br/`, `${b}pt-br/docs/`, 'h2')}
  </div>
</section>`;
  return { page, content };
}
