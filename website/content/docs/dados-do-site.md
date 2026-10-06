---
title: De onde vêm os dados do site
description: Quais dados do site são lidos do código e quais são escritos à mão, como as versões estável e de desenvolvimento são separadas, o que o build confere e como o site é publicado e mantido.
navTitle: Dados do site
section: projeto
order: 3
---

Este site é gerado a partir do repositório do Xendroid+. O que dá para ler do código é lido do código a cada build; o que precisa de explicação é escrito à mão, em Markdown, e conferido contra o código quando possível. Nada garante que um texto escrito à mão continue certo depois que o app muda: por isso cada página diz com qual versão foi revisada e avisa quando o código citado mudou depois disso.

## Duas versões

- **Estável**: o commit da release mais recente publicada no GitHub, hoje a {{estavel.nome}} (`{{estavel.commit}}`). É a versão que a página inicial oferece para baixar e a base da documentação e do simulador.
- **Desenvolvimento**: o commit do `main` em que o site é gerado (`{{dev.commit}}`).

O build lê as duas separadamente: a estável numa árvore de trabalho própria, no commit da release. Quando o `main` não mudou o app depois da release, as duas dão os mesmos dados e o site diz isso. Quando mudou, a referência de ajustes lista as diferenças, os trechos da documentação marcados com uma versão dizem a partir de qual build valem, e os dados das duas são publicados em `dados/estavel.json` e `dados/desenvolvimento.json`.

## O que é lido do código

{{> fontes}}

Os textos dos ajustes são os do próprio app em português; quando o app não tem tradução, o texto aparece em inglês, marcado como tal. A base de títulos do Xenia que acompanha o núcleo serve só para dar nome a um Title ID: o estado de compatibilidade dela é do Xenia para PC e não vale para o Xendroid+.

## O que é escrito à mão

- As páginas da documentação, em `website/content/docs/*.md`.
- Os textos da página inicial e as legendas dos prints, em `website/scripts/site/landing.mjs` e `website/content/prints.mjs`.
- As anotações e os dados de exemplo do simulador (jogos, sessões, perfis), marcados como exemplo na própria ferramenta.

Cada página da documentação diz no cabeçalho o commit com que foi revisada (`conferido`) e lista os arquivos do app que ela descreve (`fontes`). Se algum desses arquivos mudou entre a revisão e a versão estável, a página mostra um aviso no topo e o build registra o caso. Valores como a versão, o tamanho do APK, o pacote e as contagens entram nas páginas por referência aos dados (`\{{estavel.build}}`, por exemplo), e não escritos no texto.

## O que o build confere {#conferencias}

A cada build, o site confere:

- se cada ajuste do app tem uma cvar no núcleo, na mesma seção, e se os valores que o app grava são do tipo que o núcleo aceita;
- se as chaves do modelo `default_config.toml` existem no núcleo;
- se as correções automáticas por jogo apontam para cvars que existem;
- se os arquivos de patch têm o Title ID do nome;
- se cada link interno, âncora, arquivo do repositório citado, print e tela do simulador existe.

Um problema que torna o site errado (um dado que não pôde ser lido, um link quebrado) para a publicação. Uma inconsistência do próprio app não para o site: vai para a lista abaixo, que é o retrato do código da {{estavel.nome}}.

{{> consistencia}}

## Publicação {#publicacao}

O site é publicado pelo GitHub Pages com o workflow `.github/workflows/pages.yml`, que roda:

- a cada push no `main` que muda o site, o app, o núcleo, os patches ou o README;
- quando o workflow do APK termina no `main`, o que cobre as releases novas (as releases criadas pelo próprio CI não disparam outros workflows);
- quando uma release é publicada ou editada à mão;
- sob pedido, pela aba Actions.

Num pull request, o workflow só gera e confere o site, sem publicar. O token do GitHub Actions é usado só durante o build, para ler as releases; ele nunca chega às páginas.

## Manter o site

- **Gerar e ver localmente**: em `website/`, `npm ci`, `npm run build` e `npm run serve`, que serve o site no mesmo caminho do GitHub Pages.
- **Conferir**: `npm run check` procura links e recursos quebrados; `npm test` roda os testes no navegador (layout, teclado, busca, simulador).
- **Uma página nova** é um arquivo Markdown em `website/content/docs/` com título, descrição, seção e ordem no cabeçalho.
- **Depois de revisar uma página** contra o código atual, atualize o `conferido` dela.

O guia de manutenção completo está no [README do site](repo:website/README.md).
