# Site do Xendroid+

Site oficial do Xendroid+ (página inicial, documentação e simulador da interface), publicado
pelo GitHub Pages. É gerado a partir deste repositório: os dados do app são lidos do código
a cada build, e os textos ficam em Markdown aqui dentro. O build do app não depende desta pasta.

## Comandos

Precisa do Node.js 20 ou mais novo e do git (com a história do repositório: o build lê a
release estável num commit próprio).

```sh
cd website
npm ci                 # dependências
npm run build          # gera o site em website/dist
npm run serve          # serve dist em http://127.0.0.1:4173/Xendroid-Plus/, como o Pages
npm run check          # links, âncoras e recursos de dist
npm test               # testes no navegador (Playwright + axe)
npm run ci             # build estrito + check + testes, como no CI
```

- `node scripts/build.mjs --offline` não consulta a API do GitHub e usa a última cópia das
  releases em `.cache/releases.json`.
- `--strict` (usado no CI) faz qualquer erro parar o build.
- Os prints do app viram WebP com `python3` e Pillow (`pip install pillow`); sem eles, o build
  copia os PNG e avisa.
- `npm test` usa o Chromium do Playwright. Num ambiente sem o navegador baixado, aponte
  `PLAYWRIGHT_CHROMIUM` para um executável do Chromium.

## Estrutura

```
website/
  site.config.mjs        nome, repositório, endereço, seções da documentação
  content/
    docs/*.md            páginas da documentação (Markdown com front matter)
    prints.mjs           prints reais do app usados no site, com texto alternativo
  scripts/
    build.mjs            o build
    data.mjs             versões estável e de desenvolvimento, releases
    extract/             leitores do código: ajustes, jogos, fatos do app, releases
    validate/            conferência dos ajustes contra as cvars do núcleo
    lib/                 Kotlin, C++, git, Markdown, HTML, caminhos
    site/                modelos das páginas e blocos gerados
    serve.mjs            servidor local com o mesmo caminho do Pages
    check-site.mjs       verificação de links de dist
  src/
    css/, js/            estilo e scripts do site
    assets/brand/        logo, ícones e imagem de compartilhamento (scripts/brand-assets.py)
    tool/                simulador da interface
  tests/                 testes no navegador
```

## Documentação

Cada página é um arquivo em `content/docs/`. O cabeçalho:

```yaml
---
title: Título da página
description: Uma frase sobre o que a página cobre.
section: comecar        # comecar, usar, referencia ou projeto (site.config.mjs)
order: 2                # posição dentro da seção
navTitle: Título curto  # opcional, para a barra lateral
conferido: e179885e7    # commit com que a página foi revisada
fontes: [app/src/main/java/...]  # arquivos do app que a página descreve
---
```

No texto:

- `{{estavel.nome}}`, `{{app.pacote}}`, `{{contagens.ajustes}}`: valores dos dados do build
  (lista em `values` de `scripts/build.mjs`). Um nome desconhecido para o build.
- `{{> bloco arg=valor}}` numa linha sozinha: conteúdo gerado (`scripts/site/blocks.mjs`), como
  `{{> ajustes grupo=IMAGE}}`, `{{> correcoes}}`, `{{> releases}}` ou `{{> print id=ficha}}`.
- `[texto](doc:pagina#ancora)`, `[texto](repo:caminho/arquivo)`,
  `[texto](ferramenta:tela)`: links conferidos no build.
- `::: nota`, `::: aviso`, `::: dica` e `::: detalhes Título`: caixas.
- `::: versao desde=<commit>`: trecho que vale a partir da primeira release com aquele commit
  (o site escreve "Desde a build N" ou "Só no desenvolvimento").

### Quando o app muda

- Ajustes, padrões, opções, flags do Turnip, correções por jogo, patches, releases e números
  são lidos do código: o site atualiza sozinho no próximo build.
- Uma página cujas `fontes` mudaram depois do `conferido` mostra um aviso e aparece no log do
  build. Revise o texto contra o código novo e atualize o `conferido`.
- Um extrator que não reconhece mais o código (um arquivo mudou de forma) para o build com uma
  mensagem dizendo o arquivo e o que procurava. Ajuste o extrator em `scripts/extract/`.
- Uma inconsistência do app (ajuste sem cvar, tipo recusado pelo núcleo) não para o build: vai
  para a página "De onde vêm os dados do site".

## Publicação

O workflow `.github/workflows/pages.yml` gera o site com `npm run ci` e publica no GitHub Pages.
Ele roda:

- em pushes no `main` que mudam o site ou um arquivo que o site lê (a lista `sparsePaths` de
  `site.config.mjs`);
- quando o workflow do APK (“Xendroid+”) termina com sucesso no `main`, o que cobre as releases
  que ele publica (uma release criada com o token do próprio workflow não dispara outros
  workflows, mas o fim da execução dispara);
- uma vez por dia, para pegar releases publicadas ou editadas à mão;
- sob pedido, em **Actions → Site → Run workflow**.

Em pull requests ele só gera, confere e testa, sem publicar.

O checkout traz a história completa sem o conteúdo dos arquivos e baixa só os caminhos de
`sparsePaths`. Essa lista aparece três vezes no workflow (no checkout e nos filtros de push e
de pull request); o build confere que as três são iguais a `sparsePaths` e para se divergirem.
O único segredo é o `GITHUB_TOKEN` do próprio workflow, usado só no build para ler a API de
releases; nada dele vai para as páginas.

Para ativar: em **Settings → Pages** do repositório, escolha **GitHub Actions** como fonte.
O endereço fica `https://<dono>.github.io/<repositório>/`; o workflow passa esse endereço ao
build (`SITE_URL` e `SITE_BASE_PATH`), então o site funciona em qualquer subdiretório, e
um domínio próprio configurado no Pages muda os dois sozinho.
