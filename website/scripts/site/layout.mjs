// Moldura comum das páginas: <head> com metadados, cabeçalho, rodapé e a busca.
// Todos os links são relativos à página (rel), então o site funciona em qualquer subdiretório.
import { html, raw, esc, icon, fmtDate } from '../lib/html.mjs';
import { rel } from '../lib/paths.mjs';

const NAV = [
  { id: 'docs', href: 'docs/', text: 'Documentação' },
  { id: 'simulador', href: 'simulador/', text: 'Simulador' },
  { id: 'historico', href: 'docs/historico/', text: 'Novidades' },
];

/** Script do tema antes da pintura: só lê a escolha guardada (auto, claro ou escuro). */
const THEME_BOOT = "try{var t=localStorage.getItem('xdr-theme');if(t==='light'||t==='dark')document.documentElement.dataset.theme=t}catch(e){}";

export function shell(site, page, content) {
  // a 404 é servida em qualquer endereço, então usa caminhos absolutos a partir da base do site
  const r = to => (page.absolute ? site.config.basePath + to.replace(/^\.\/?$/, '') : rel(page.path, to));
  const a = file => `${r('assets/' + file)}?v=${site.assetVersion}`;
  const url = site.config.url + page.path;
  const title = page.path === '' ? `${site.config.name}: ${page.title}` : `${page.title} · ${site.config.name}`;
  const stable = site.v.stable;
  const desc = page.description;
  const ogImage = site.config.url + 'assets/brand/og-image.png';
  return html`<!doctype html>
<html lang="pt-BR" data-root="${page.absolute ? site.config.basePath : r('')}" data-page="${page.path}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${title}</title>
<meta name="description" content="${desc}">
<link rel="canonical" href="${url}">
<meta name="color-scheme" content="dark light">
<meta name="theme-color" content="#0f1214" media="(prefers-color-scheme: dark)">
<meta name="theme-color" content="#f2f4f1" media="(prefers-color-scheme: light)">
<meta property="og:type" content="${page.ogType || 'website'}">
<meta property="og:site_name" content="${site.config.name}">
<meta property="og:locale" content="pt_BR">
<meta property="og:title" content="${title}">
<meta property="og:description" content="${desc}">
<meta property="og:url" content="${url}">
<meta property="og:image" content="${ogImage}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="Logo do Xendroid+ e o texto: Emulação de Xbox 360 no Android, ajustada para Snapdragon e Adreno.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="${title}">
<meta name="twitter:description" content="${desc}">
<meta name="twitter:image" content="${ogImage}">
<link rel="icon" href="${r('favicon.ico')}" sizes="32x32">
<link rel="icon" href="${r('assets/brand/favicon-32.png')}" type="image/png">
<link rel="apple-touch-icon" href="${r('assets/brand/apple-touch-icon.png')}">
<link rel="manifest" href="${r('site.webmanifest')}">
<link rel="preload" href="${r('assets/fonts/barlow-400.woff2')}" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="${r('assets/fonts/barlow-semi-condensed-700.woff2')}" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="${a('css/site.css')}">
${page.styles ? page.styles.map(s => raw(`<link rel="stylesheet" href="${a(s)}">\n`)) : ''}
<script>${raw(THEME_BOOT)}</script>
${page.head || ''}
</head>
<body class="${page.bodyClass || ''}">
<a class="skip-link" href="#conteudo">Pular para o conteúdo</a>
<header class="site-header">
  <div class="wrap">
    <a class="brand" href="${r('')}" aria-label="Xendroid+, página inicial">
      <img src="${r('assets/brand/mark-64.webp')}" width="34" height="34" alt="">
      <span>Xendroid<span class="plus">+</span></span>
    </a>
    <nav class="site-nav" id="site-nav" aria-label="Principal">
      ${NAV.map(n => html`<a href="${r(n.href)}"${page.section === n.id ? raw(' aria-current="page"') : ''}>${n.text}</a>`)}
      <a class="ext" href="${site.config.repoUrl}" rel="noopener">${icon('code', 18)}Código-fonte</a>
    </nav>
    <div class="header-tools">
      <button class="search-btn" type="button" data-open-search aria-haspopup="dialog" aria-controls="busca">${icon('search', 18)}<span class="label">Buscar</span><kbd>/</kbd></button>
      <button class="icon-btn" type="button" id="theme-btn" aria-label="Tema: automático" title="Tema: automático">${icon('monitor', 20)}</button>
      ${stable && stable.apk ? html`<a class="button primary small header-dl" href="${stable.apk.url}">${icon('download', 18)}Baixar</a>` : ''}
      <button class="icon-btn menu-btn" type="button" aria-expanded="false" aria-controls="site-nav" aria-label="Menu">${icon('menu', 22)}</button>
    </div>
  </div>
</header>
<main id="conteudo" tabindex="-1">
${content}
</main>
${footer(site, page, r)}
<dialog class="search-dialog" id="busca" aria-label="Buscar na documentação">
  <div class="search-field">
    ${icon('search', 20)}
    <label class="visually-hidden" for="busca-q">Buscar na documentação</label>
    <input id="busca-q" type="search" placeholder="Buscar na documentação e nos ajustes" autocomplete="off" spellcheck="false" aria-controls="busca-res" aria-describedby="busca-dica">
    <button class="icon-btn" type="button" data-close-search aria-label="Fechar a busca">${icon('x', 20)}</button>
  </div>
  <div class="search-results" id="busca-res" aria-live="polite"><p class="search-hint" id="busca-dica">Digite para buscar em todas as páginas da documentação, nos ajustes do app e nas chaves do TOML.</p></div>
  <div class="search-foot"><span><kbd>↑</kbd> <kbd>↓</kbd> escolher</span><span><kbd>Enter</kbd> abrir</span><span><kbd>Esc</kbd> fechar</span></div>
</dialog>
<script type="module" src="${a('js/site.js')}"></script>
${page.scripts || ''}
</body>
</html>
`;
}

function footer(site, page, r) {
  const { stable, dev } = site.v;
  return html`<footer class="site-footer">
  <div class="wrap">
    <div class="pins" aria-hidden="true"></div>
    <div class="footer-cols">
      <section>
        <h2>Xendroid+</h2>
        <ul>
          ${stable && stable.apk ? html`<li><a href="${stable.apk.url}">Baixar a ${stable.label.toLowerCase()} (APK)</a></li>` : ''}
          <li><a href="${site.config.repoUrl}/releases" rel="noopener">Todas as releases</a></li>
          <li><a href="${r('docs/historico/')}">Novidades e histórico</a></li>
          <li><a href="${r('simulador/')}">Simulador da interface</a></li>
        </ul>
      </section>
      <section>
        <h2>Documentação</h2>
        <ul>
          <li><a href="${r('docs/primeiros-passos/')}">Primeiros passos</a></li>
          <li><a href="${r('docs/requisitos/')}">Requisitos</a></li>
          <li><a href="${r('docs/problemas/')}">Problemas e logs</a></li>
          <li><a href="${r('docs/referencia-de-ajustes/')}">Referência de ajustes</a></li>
        </ul>
      </section>
      <section>
        <h2>Projeto</h2>
        <ul>
          <li><a href="${site.config.repoUrl}" rel="noopener">Código-fonte no GitHub</a></li>
          <li><a href="${r('docs/compilar-e-contribuir/')}">Compilar e contribuir</a></li>
          <li><a href="${site.config.discord}" rel="noopener">Comunidade no Discord</a></li>
          <li><a href="${r('docs/dados-do-site/')}">De onde vêm os dados do site</a></li>
        </ul>
      </section>
    </div>
    <div class="footer-legal">
      <p>O Xendroid+ é um projeto de código aberto, sem fins comerciais, criado e mantido por <a href="https://github.com/phforner0" rel="noopener">phforner0</a>. Continua o XenDroid, de rfandango, e usa o núcleo do <a href="https://github.com/xenia-project/xenia" rel="noopener">Xenia</a>. Não tem vínculo com a Microsoft nem é endossado por ela; Xbox e Xbox 360 são marcas da Microsoft Corporation.</p>
      <p>Nenhum jogo, imagem de disco, firmware, arquivo de sistema ou chave é incluído ou distribuído. Use apenas cópias de jogos que você possui.</p>
    </div>
    <p class="footer-meta">Gerado em ${fmtDate(site.builtAt)} a partir do commit ${dev.short}.${stable ? ` Estável: ${stable.label.toLowerCase()} (${stable.short}).` : ''}${site.data.sameChannels ? ' O main não tem mudanças no app depois dela.' : ''}</p>
  </div>
</footer>`;
}
