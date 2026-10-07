---
title: De onde vêm os dados do site
description: Quais dados do site são lidos do código e quais são escritos à mão, como as versões estável e de desenvolvimento são separadas, o que o build confere e como o site é publicado e mantido.
navTitle: Dados do site
section: projeto
order: 3
---

Este site é gerado a partir do repositório do Xendroid+, em dois idiomas: o inglês na raiz do endereço e o português em `pt-br/`. Cada página existe nos dois, com o mesmo conteúdo, e o seletor de idioma do cabeçalho leva à mesma página no outro idioma. O que dá para ler do código é lido do código a cada build; o que precisa de explicação é escrito à mão, em Markdown, e conferido contra o código quando possível. Nada garante que um texto escrito à mão continue certo depois que o app muda: por isso cada página diz com qual versão foi revisada e avisa quando o código citado mudou depois disso.

## Duas versões

- **Estável**: o commit da release mais recente publicada no GitHub, hoje a {{estavel.nome}} (`{{estavel.commit}}`). É a versão que a página inicial oferece para baixar e a base da documentação e do simulador.
- **Desenvolvimento**: o commit do `main` em que o site é gerado (`{{dev.commit}}`).

O build lê as duas separadamente: a estável numa árvore de trabalho própria, no commit da release. Quando as duas dão os mesmos dados (o `main` mudou só o site, um workflow ou comentários do código), o site mostra uma versão só e diz isso. Quando mudou, a referência de ajustes lista as diferenças, os trechos da documentação marcados com uma versão dizem a partir de qual build valem, e os dados das duas são publicados em `dados/estavel.json` e `dados/desenvolvimento.json`.

## O que é lido do código

{{> fontes}}

Os textos dos ajustes são os do próprio app: em português, os de `values-pt-rBR` (quando o app não tem tradução, o texto aparece em inglês, marcado como tal); em inglês, os de `values`. O resumo de cada release vem do bloco `update-summary:pt-BR` das notas nas páginas em português e do `update-summary:en` nas em inglês, e a tabela de desempenho vem do `README.pt-BR.md` e do `README.md`. A base de títulos do Xenia que acompanha o núcleo serve só para dar nome a um Title ID: o estado de compatibilidade dela é do Xenia para PC e não vale para o Xendroid+.

## O que é escrito à mão

- As páginas da documentação, em `website/content/docs/*.md` (português) e `website/content/docs/en/*.md` (inglês).
- Os textos da página inicial e as legendas dos prints, em `website/scripts/site/landing.mjs`, `website/content/prints.mjs` e `website/content/prints.en.mjs`.
- As anotações e os dados de exemplo do simulador (jogos, sessões, perfis), marcados como exemplo na própria ferramenta. O simulador é escrito em português; o build gera a versão em inglês trocando cada texto pelo do dicionário `website/content/simulador.en.json`, ou pelo texto do próprio app em inglês quando é o mesmo rótulo do app, e para se faltar a tradução de algum texto.

Cada página da documentação diz no cabeçalho o commit com que foi revisada (`conferido`) e lista os arquivos do app que ela descreve (`fontes`). Se algum desses arquivos mudou entre a revisão e a versão estável, a página mostra um aviso no topo e o build registra o caso. Valores como a versão, o tamanho do APK, o pacote e as contagens entram nas páginas por referência aos dados (`\{{estavel.build}}`, por exemplo), e não escritos no texto.

## O que o build confere {#conferencias}

A cada build, o site confere:

- se cada ajuste do app tem uma cvar no núcleo, na mesma seção, e se os valores que o app grava são do tipo que o núcleo aceita;
- se as chaves do modelo `default_config.toml` existem no núcleo;
- se as correções automáticas por jogo apontam para cvars que existem;
- se os arquivos de patch têm o Title ID do nome;
- se cada link interno, âncora, arquivo do repositório citado, print e tela do simulador existe;
- se cada página tem a versão no outro idioma e se as duas apontam uma para a outra.

Um problema que torna o site errado (um dado que não pôde ser lido, um link quebrado) para a publicação. Uma inconsistência do próprio app não para o site: vai para a lista abaixo, que é o retrato do código da {{estavel.nome}}.

{{> consistencia}}

## Publicação {#publicacao}

O site é publicado pelo GitHub Pages com o workflow `.github/workflows/pages.yml`, que roda:

- a cada push no `main` que muda o site ou um arquivo que ele lê: o app, o núcleo, os patches, os README e os documentos;
- quando o workflow do APK termina com sucesso no `main`, o que cobre as releases novas (as releases criadas pelo próprio CI não disparam outros workflows);
- uma vez por dia, para pegar releases publicadas ou editadas à mão;
- sob pedido, pela aba Actions.

Num pull request, o workflow só gera e confere o site, sem publicar. O token do GitHub Actions é usado só durante o build, para ler as releases; ele nunca chega às páginas.

## Manter o site

- **Gerar e ver localmente**: em `website/`, `npm ci`, `npm run build` e `npm run serve`, que serve o site no mesmo caminho do GitHub Pages.
- **Conferir**: `npm run check` procura links e recursos quebrados; `npm test` roda os testes no navegador (layout, teclado, busca, simulador).
- **Uma página nova** são dois arquivos Markdown, um em `website/content/docs/` e outro em `website/content/docs/en/`, com título, descrição, seção e ordem no cabeçalho; o inglês leva também `pt: <nome do arquivo em português>`, que pareia as duas.
- **Depois de revisar uma página** contra o código atual, atualize o `conferido` dela.

O guia de manutenção completo está no [README do site](repo:website/README.md).
