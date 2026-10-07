// Página inicial. Números e fatos vêm dos dados extraídos do código (versão estável);
// o que não dá para conferir no repositório não entra.
import { html, raw, esc, icon, fmtBytes, fmtDate } from '../lib/html.mjs';
import { rel } from '../lib/paths.mjs';

const PATH = '';

function tourPanels(site) {
  const s = site.stats;
  return [
    {
      id: 'biblioteca', tab: 'Biblioteca e ficha', shot: 'ficha', title: 'Os seus jogos numa biblioteca que entende o Xbox 360',
      points: [
        'Encontra ISO, ZAR, GOD, pastas com default.xex e pacotes XBLA nas suas pastas, com busca por nome ou Title ID, favoritos, coleções e filtro por formato.',
        'A ficha de cada jogo junta Jogar, Iniciar com… (perfil, driver, executável, sem patches ou sem os ajustes do jogo, só nesta abertura), as sessões, os patches e os saves.',
        'Com um controle conectado, a interface troca sozinha para o modo controle: carrossel de capas, abas por LB e RB e dicas de botão sempre à vista.',
      ],
      links: [['docs/interface/', 'Interface do app'], ['simulador/#library', 'Ver no simulador']],
    },
    {
      id: 'menu', tab: 'Menu em jogo e HUD', shot: 'menu-imagem', title: 'Ajustes no meio do jogo, guardados para ele',
      points: [
        'O menu abre deslizando da borda esquerda, pelo Voltar ou pelo botão Guia do controle, e pausa o jogo por padrão.',
        'Cinco categorias: Imagem, Desempenho, HUD, Controles e Sessão. Escala, antisserrilhamento, nitidez e pontilhado mudam na hora.',
        'O que você muda fica guardado para aquele jogo, com Desfazer as mudanças da sessão e Usar em todos os jogos. O HUD mostra FPS, tempo de quadro, CPU, GPU, memória, temperaturas e bateria.',
      ],
      links: [['docs/interface/#menu-em-jogo', 'Menu em jogo'], ['simulador/#ingame', 'Ver no simulador']],
    },
    {
      id: 'ajustes', tab: 'Ajustes', shot: 'configuracoes', title: `${s.settings} ajustes, cada um dizendo quando vale`,
      points: [
        `Três níveis de detalhe: Essencial (${s.essential}), Avançado (${s.advanced}) e Tudo (${s.settings}). Cada linha diz se o valor é o padrão, se mudou e se vale na hora ou na próxima abertura.`,
        'Os mesmos ajustes valem para todos os jogos em Configurações ou só para um jogo na ficha, gravados em config/<Title ID>.config.toml, o arquivo que o núcleo lê.',
        'Predefinições Desempenho, Equilíbrio e Qualidade aplicam vários ajustes de uma vez, com Desfazer.',
      ],
      links: [['docs/referencia-de-ajustes/', 'Referência de ajustes'], ['simulador/#settings', 'Ver no simulador']],
    },
    {
      id: 'drivers', tab: 'Drivers', shot: 'drivers', title: 'Drivers Turnip por jogo, conferidos antes de instalar',
      points: [
        'Baixe pacotes de fontes no GitHub (a padrão é K11MCH1/AdrenoToolsDrivers) ou importe um ZIP; o app confere a biblioteca Vulkan arm64 e o SHA-256 quando a release publica um.',
        'Escolha um driver para todos os jogos, um por jogo ou um só para a próxima abertura; o driver do sistema fica sempre a um toque.',
        `As ${s.turnipFlags} flags do Turnip (TU_DEBUG) aparecem uma a uma, com sysmem como padrão. Drivers personalizados só carregam em GPUs Adreno.`,
      ],
      links: [['docs/drivers/', 'Drivers da GPU'], ['simulador/#drivers', 'Ver no simulador']],
    },
    {
      id: 'controles', tab: 'Controles', shot: 'controles-moderno', title: 'Toque, controle físico e até outro celular',
      points: [
        'Controles de toque no visual Moderno ou Clássico, com editor de layout, layouts salvos por jogo e por orientação, câmera por toque e giroscópio.',
        'Controles físicos jogam como P1 a P4, com mapeamento dos 16 botões, vibração por controle e uma tela para testar cada botão.',
        'Celular como controle (experimental): outro telefone na mesma rede local entra como P2 a P4 com um código de 6 dígitos.',
      ],
      links: [['docs/controles/', 'Controles'], ['simulador/#controls', 'Ver no simulador']],
    },
    {
      id: 'diagnostico', tab: 'Diagnóstico', shot: 'diagnostico', title: 'Cada sessão deixa um rastro que dá para compartilhar',
      points: [
        `O app guarda as últimas sessões (${s.logSessions} por padrão), com o log do emulador, o logcat do app e como cada uma terminou.`,
        'Compartilhar gera uma cópia limpa: caminhos, gamertags, XUIDs, endereços IP, e-mails, senhas e tokens saem antes.',
        'No fim de uma sessão com problema, o app sugere até três mudanças com o motivo em números, e Comparar execuções mede uma mudança por vez (A B B A).',
      ],
      links: [['docs/problemas/', 'Problemas e logs'], ['simulador/#diagnostics', 'Ver no simulador']],
    },
  ];
}

export function landing(site) {
  const page = {
    path: PATH,
    section: 'inicio',
    title: 'emulador de Xbox 360 para Android',
    description: `Emulador de Xbox 360 para Android, focado em Snapdragon e GPUs Adreno. Baixe a ${site.v.stable ? site.v.stable.label.toLowerCase() : 'versão mais recente'}, veja requisitos e limitações, a documentação e um simulador da interface.`,
    head: jsonLd(site),
  };
  const r = to => rel(PATH, to);
  const { stable } = site.v;
  const s = site.stats;
  const est = site.ch.est;
  const panels = tourPanels(site);
  const app = est.app;

  const content = html`
<section class="hero">
  <div class="wrap">
    <div class="hero-copy">
      <span class="eyebrow">Emulador de Xbox 360 · Android ${app.minAndroid}+ · ${app.abi}</span>
      <h1>Xendroid<span class="plus">+</span></h1>
      <p class="lead">Emulação de Xbox 360 no Android, ajustada para <strong>Snapdragon e GPUs Adreno</strong>. Continua o XenDroid sobre o núcleo do Xenia, com uma interface para toque e controle, ajustes por jogo aplicados na abertura e o diagnóstico de cada sessão no próprio aparelho.</p>
      ${stable && stable.apk ? html`<div class="download">
        <a class="button primary" href="${stable.apk.url}">${icon('download', 22)}Baixar a ${stable.label.toLowerCase()}</a>
        <p class="download-meta">
          <span>APK · ${fmtBytes(stable.apk.size)}</span>
          <span>publicada em ${fmtDate(stable.date)}</span>
          ${stable.apk.sha256 ? html`<span>SHA-256 <span class="u-mono" title="${stable.apk.sha256}">${stable.apk.sha256.slice(0, 12)}…</span> <button class="button quiet small" type="button" data-copy="${stable.apk.sha256}" data-copied="SHA-256 copiado">Copiar</button></span>` : ''}
        </p>
        <p class="download-meta"><a href="${stable.url}" rel="noopener">Notas desta versão</a><a href="${site.config.repoUrl}/releases" rel="noopener">Todas as versões</a><a href="${r('docs/instalacao/')}">Como instalar e atualizar</a></p>
      </div>` : html`<p class="hero-note">${icon('warn', 18)}<span>Nenhuma release publicada foi encontrada neste build. Veja <a href="${site.config.repoUrl}/releases" rel="noopener">as releases no GitHub</a>.</span></p>`}
      <p class="hero-note">${icon('warn', 18)}<span><b>Experimental.</b> Como cada jogo roda depende do jogo, do aparelho e do driver da GPU: alguns títulos rodam bem, outros ainda têm falhas ou não iniciam.</span></p>
      <p class="hero-links"><a href="${r('docs/primeiros-passos/')}">${icon('book', 18)}Primeiros passos</a><a href="${r('simulador/')}">${icon('phone', 18)}Explorar a interface no simulador</a></p>
    </div>
    <div class="hero-shot">${site.shot(PATH, 'biblioteca', { eager: true, caption: raw('<b>A biblioteca no modo toque.</b> Imagem gerada pelos testes de tela do app, com capas e jogos de exemplo.') })}</div>
  </div>
</section>

<section class="section" aria-labelledby="tour-t">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Por dentro do app</span>
      <h2 id="tour-t">As telas do Xendroid+</h2>
      <p>Imagens geradas a partir do código da interface pelos testes de tela do projeto. Os jogos, capas e números nelas são dados de exemplo.</p>
    </div>
    <div class="tour" data-tabs>
      <div class="tour-tabs" role="tablist" aria-label="Telas do app" hidden>
        ${panels.map((p, i) => html`<button type="button" role="tab" id="tab-${p.id}" aria-controls="painel-${p.id}" aria-selected="${i === 0}" tabindex="${i === 0 ? 0 : -1}">${p.tab}</button>`)}
      </div>
      ${panels.map((p, i) => html`<div class="tour-panel" id="painel-${p.id}" role="tabpanel" aria-labelledby="tab-${p.id}">
        ${site.shot(PATH, p.shot, {})}
        <div>
          <h3>${p.title}</h3>
          <ul>${p.points.map(t => html`<li>${t}</li>`)}</ul>
          <p class="more-links">${p.links.map(([href, t]) => html`<a href="${r(href)}">${t}</a>`)}</p>
        </div>
      </div>`)}
    </div>
  </div>
</section>

<section class="section" aria-labelledby="diff-t">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">O que é diferente</span>
      <h2 id="diff-t">Feito para o aparelho, conferido no código</h2>
      <p>Cada número abaixo é contado no código da ${stable ? stable.label.toLowerCase() : 'versão publicada'} quando este site é gerado.</p>
    </div>
    <ol class="facts">
      <li>
        <span class="fig">${s.quirkTitles}<small>jogos com correções</small></span>
        <div><h3>Correções por jogo aplicadas na abertura</h3><p>O núcleo traz ${s.quirkEntries} ajustes para ${s.quirkTitles} jogos específicos, como ${s.quirkExamples}. Eles entram quando o título inicia, com a prioridade de um ajuste do próprio jogo, sem você configurar nada.</p></div>
        <a class="src" href="${r('docs/compatibilidade/#correcoes-automaticas')}">Ver a lista</a>
      </li>
      <li>
        <span class="fig">${s.settings}<small>ajustes</small></span>
        <div><h3>Cada ajuste vem do esquema do app</h3><p>Imagem, desempenho, áudio, controles, compatibilidade, driver, sistema e depuração, globais ou por jogo. O arquivo por jogo é gravado no formato que o núcleo lê, e o simulador deste site gera o mesmo arquivo.</p></div>
        <a class="src" href="${r('docs/referencia-de-ajustes/')}">Referência completa</a>
      </li>
      <li>
        <span class="fig">${s.patchTitles}<small>jogos com patches</small></span>
        <div><h3>Patches de jogos dentro do APK</h3><p>Os ${s.patchFiles} arquivos de patch do Xenia Canary vão no app. A ficha separa os patches feitos para a sua versão do jogo dos que valem para outras versões.</p></div>
        <a class="src" href="${r('docs/jogos-e-arquivos/#patches')}">Como funcionam</a>
      </li>
      <li>
        <span class="fig">${s.turnipFlags}<small>flags do Turnip</small></span>
        <div><h3>Gerenciador de drivers Adreno</h3><p>Pacotes Turnip baixados do GitHub ou importados, conferidos antes de instalar, escolhidos para todos os jogos, por jogo ou só numa abertura.</p></div>
        <a class="src" href="${r('docs/drivers/')}">Drivers da GPU</a>
      </li>
      <li>
        <span class="fig">A/B<small>comparar execuções</small></span>
        <div><h3>Medir antes de mudar</h3><p>Cada sessão grava FPS por segundo, tempo de quadro, pipelines criados, falhas de áudio e temperatura. Comparar execuções só dá um veredito quando as execuções seguem a ordem A B B A e diferem em uma coisa só.</p></div>
        <a class="src" href="${r('docs/desempenho/')}">Desempenho</a>
      </li>
    </ol>
  </div>
</section>

${measured(site, r)}

<section class="section" aria-labelledby="req-t">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Antes de instalar</span>
      <h2 id="req-t">Requisitos e limitações</h2>
    </div>
    <div class="req-grid">
      <div>
        <h3>O que o aparelho precisa</h3>
        <dl class="spec">
          <dt>Android</dt><dd>${app.minAndroid} ou mais novo. Escolher a pasta de jogos exige Android 11.</dd>
          <dt>Processador</dt><dd>ARM de 64 bits (<span class="u-mono">${app.abi}</span>). O APK não instala em outras arquiteturas.</dd>
          <dt>GPU</dt><dd>Vulkan, obrigatório. O foco são Adreno 7xx e 8xx com o driver Turnip; recomenda-se um Snapdragon 8 Gen 2 ou mais novo.</dd>
          <dt>Jogos</dt><dd>Os seus, extraídos de discos ou consoles seus: ISO, ZAR, GOD, pastas com default.xex e pacotes XBLA.</dd>
          <dt>Arquivos</dt><dd>Acesso a todos os arquivos, para a biblioteca ler as pastas de jogos.</dd>
        </dl>
        <p class="u-muted" style="margin-top:12px"><a href="${r('docs/requisitos/')}">Requisitos em detalhe</a></p>
      </div>
      <div>
        <h3>O que ainda limita</h3>
        <ul class="limits">
          <li>${icon('warn', 18)}<span><b>Compatibilidade varia.</b> Não há lista pública de compatibilidade do Xendroid+: a avaliação de cada jogo fica no seu aparelho.</span></li>
          <li>${icon('warn', 18)}<span><b>Drivers personalizados só em Adreno.</b> Em outras GPUs os jogos rodam no driver Vulkan do sistema.</span></li>
          <li>${icon('warn', 18)}<span><b>Geração de quadros é experimental.</b> Win-FG 2× e LSFG começam desligados a cada abertura; o LSFG precisa do seu próprio Lossless.dll.</span></li>
          <li>${icon('warn', 18)}<span><b>No Android 10</b> o app instala, mas só abre jogos vindos de um frontend: escolher pastas exige Android 11.</span></li>
          <li>${icon('warn', 18)}<span><b>Ajustes da comunidade e catálogo de compatibilidade</b> existem no código, mas ficam desligados nas versões publicadas: não há servidor.</span></li>
        </ul>
        <div class="planned">
          <h4><span class="tag plain">Fora do app</span>Propostas registradas, ainda não implementadas</h4>
          <ul>
            <li>Um mapa de teclas para cada controle (hoje um mapa vale para todos).</li>
            <li>Achar o jogo na rede e ler um QR code no celular como controle.</li>
            <li>Procurar o arquivo do disco quando a troca de disco não acha nenhum.</li>
          </ul>
          <p class="u-muted" style="margin-top:8px;font-size:var(--step--1)">Registradas em <a href="${site.config.repoUrl}/blob/main/docs/ui-redesign/app.md#propostas-do-prot%C3%B3tipo-que-ficaram-de-fora" rel="noopener">docs/ui-redesign/app.md</a>, sem data prevista.</p>
        </div>
      </div>
    </div>
  </div>
</section>

<section class="section" aria-labelledby="go-t">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Próximos passos</span>
      <h2 id="go-t">Documentação e simulador</h2>
    </div>
    <div class="split">
      <section>
        <h3>${icon('book', 22)}Documentação</h3>
        <p>Da instalação à coleta de logs, escrita a partir do código da ${stable ? stable.label.toLowerCase() : 'versão publicada'}, com busca e links para cada seção.</p>
        <ul>
          ${[['docs/primeiros-passos/', 'Primeiros passos'], ['docs/instalacao/', 'Instalar e atualizar'], ['docs/ajustes/', 'Ajustes e efeitos'], ['docs/problemas/', 'Problemas e logs'], ['docs/compilar-e-contribuir/', 'Compilar e contribuir']]
            .map(([h, t]) => html`<li><a href="${r(h)}">${t}${icon('arrowR', 18)}</a></li>`)}
        </ul>
      </section>
      <section>
        <h3>${icon('phone', 22)}Simulador da interface</h3>
        <p>Navegue pelas telas do app no navegador, no modo toque ou no modo controle, com teclado ou um controle de verdade. Os ajustes são os ${s.settings} da ${stable ? stable.label.toLowerCase() : 'versão publicada'}, e o arquivo de configuração de um jogo sai no formato que o núcleo lê.</p>
        <p><a class="button" href="${r('simulador/')}">${icon('play', 18)}Abrir o simulador</a></p>
        <p class="u-muted" style="font-size:var(--step--1)">Jogos, capas, perfis e sessões do simulador são exemplos; nada roda um jogo.</p>
      </section>
    </div>
  </div>
</section>

<section class="section" aria-labelledby="proj-t">
  <div class="wrap project">
    <div>
      <span class="eyebrow">O projeto</span>
      <h2 id="proj-t" style="margin-bottom:16px">Código aberto, feito no aparelho</h2>
      <p>O Xendroid+ é criado e mantido por <a href="https://github.com/phforner0" rel="noopener">phforner0</a>. Dá continuidade ao XenDroid, o port original para Android de <a href="https://github.com/rfandango" rel="noopener">rfandango</a>, e usa o núcleo do <a href="https://github.com/xenia-project/xenia" rel="noopener">Xenia</a> e do Xenia Canary.</p>
      <p>O trabalho de desempenho é medido quadro a quadro em aparelhos reais, com métodos e resultados em <a href="${site.config.repoUrl}/tree/main/performance-tests" rel="noopener">performance-tests/</a>, e cada versão publicada traz as notas do que mudou. Componentes de terceiros mantêm as próprias licenças, listadas no <a href="${site.config.repoUrl}/blob/main/THIRD-PARTY-NOTICES.md" rel="noopener">THIRD-PARTY-NOTICES.md</a>.</p>
    </div>
    <ul class="link-list">
      <li><a href="${site.config.repoUrl}" rel="noopener">${icon('code', 20)}<span>Código-fonte<small>${site.config.repo} no GitHub</small></span>${icon('external', 16)}</a></li>
      <li><a href="${site.config.repoUrl}/releases" rel="noopener">${icon('tag', 20)}<span>Releases<small>APKs e notas de cada build</small></span>${icon('external', 16)}</a></li>
      <li><a href="${r('docs/historico/')}">${icon('history', 20)}<span>Novidades e histórico<small>o que mudou em cada versão, em português</small></span>${icon('arrowR', 16)}</a></li>
      <li><a href="${r('docs/compilar-e-contribuir/')}">${icon('wrench', 20)}<span>Compilar e contribuir<small>JDK 21, Android SDK e NDK, testes e CI</small></span>${icon('arrowR', 16)}</a></li>
      <li><a href="${site.config.discord}" rel="noopener">${icon('chat', 20)}<span>Comunidade<small>conversa no Discord</small></span>${icon('external', 16)}</a></li>
    </ul>
  </div>
</section>
`;
  return { page, content };
}

function measured(site, r) {
  const perf = site.ch.dev.games.perf;
  if (!perf || !perf.rows.length) return '';
  const cols = perf.header;
  return html`<section class="section" aria-labelledby="perf-t">
  <div class="wrap measured">
    <div class="section-head" style="margin-bottom:8px">
      <span class="eyebrow">Desempenho medido</span>
      <h2 id="perf-t">Medições publicadas pelo projeto</h2>
      <p>${perf.context.replace(/\*\*/g, '')}</p>
    </div>
    <div class="table-wrap" tabindex="0" role="region" aria-label="Medições de desempenho">
      <table>
        <thead><tr>${cols.map(c => html`<th scope="col">${c}</th>`)}</tr></thead>
        <tbody>${perf.rows.map(row => html`<tr>${cols.map((c, i) => (i === 0 ? html`<th scope="row">${row[c]}</th>` : html`<td>${row[c] || '—'}</td>`))}</tr>`)}</tbody>
      </table>
    </div>
    <p class="context">
      <span>Fonte: <a href="${site.config.repoUrl}/blob/main/${perf.source}#desempenho" rel="noopener">${perf.source}</a> e <a href="${site.config.repoUrl}/tree/main/performance-tests" rel="noopener">performance-tests/</a>.</span>
      <span>Não são garantia: o seu aparelho, o driver e a versão do jogo mudam estes números.</span>
    </p>
  </div>
</section>`;
}

/** Dados estruturados da aplicação (só fatos que o build conhece). */
function jsonLd(site) {
  const { stable } = site.v;
  const app = site.ch.est.app;
  const d = {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'Xendroid+',
    applicationCategory: 'GameApplication',
    operatingSystem: `Android ${app.minAndroid}+`,
    processorRequirements: app.abi,
    url: site.config.url,
    codeRepository: site.config.repoUrl,
    inLanguage: ['pt-BR', 'en'],
    isAccessibleForFree: true,
    offers: { '@type': 'Offer', price: '0', priceCurrency: 'BRL' },
    author: { '@type': 'Person', name: 'phforner0', url: 'https://github.com/phforner0' },
  };
  if (stable && stable.apk) {
    d.softwareVersion = `build ${stable.build}`;
    d.downloadUrl = stable.apk.url;
    d.fileSize = `${Math.round(stable.apk.size / 1024 / 1024)}MB`;
    d.datePublished = stable.date;
  }
  return raw(`<script type="application/ld+json">${JSON.stringify(d).replace(/</g, '\\u003c')}</script>`);
}
