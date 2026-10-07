// Página 404 do GitHub Pages: servida em qualquer endereço que não existe, por isso os links
// são absolutos (config.basePath) e o script tenta levar endereços antigos ao lugar certo.
import { html, icon } from '../lib/html.mjs';

export function notFound(site) {
  const b = site.config.basePath;
  const page = {
    path: '404.html',
    absolute: true,
    noindex: true,
    section: '',
    title: 'Página não encontrada',
    description: 'Este endereço não existe no site do Xendroid+.',
    head: html`<meta name="robots" content="noindex">`,
  };
  const content = html`<section class="section">
  <div class="wrap" style="max-width:760px">
    <span class="eyebrow">Erro 404</span>
    <h1 style="font-size:var(--step-4);margin-bottom:16px">Esta página não existe</h1>
    <p style="color:var(--fg-2);font-size:var(--step-1);margin-bottom:24px">O endereço pode ter mudado ou estar digitado errado. A busca (<kbd>/</kbd>) acha qualquer página da documentação e qualquer ajuste do app.</p>
    <p style="display:flex;flex-wrap:wrap;gap:10px">
      <a class="button primary" href="${b}">${icon('arrowR', 18)}Página inicial</a>
      <a class="button" href="${b}docs/">${icon('book', 18)}Documentação</a>
      <button class="button" type="button" data-open-search>${icon('search', 18)}Buscar</button>
    </p>
  </div>
</section>`;
  return { page, content };
}
