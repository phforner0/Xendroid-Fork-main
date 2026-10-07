---
title: Usar o simulador
description: O que o simulador da interface mostra, o que nele é dado real e o que é exemplo, e como usar teclado, controle e o arquivo de configuração por jogo.
navTitle: Simulador
section: usar
order: 7
---

O [simulador](ferramenta:library) é a interface do Xendroid+ rodando no navegador: as telas do app nos modos toque e controle, deitado ou em pé. Ele serve para conhecer o app antes de instalar, achar onde fica um ajuste e montar o arquivo de configuração de um jogo. Ele não roda jogos, não baixa nada e não mexe no seu aparelho.

## O que é real e o que é exemplo

Vem do código da {{estavel.nome}}, lido no build do site:

- os {{contagens.ajustes}} ajustes, com grupo, nível, textos, opções, padrões e quais valem ao vivo;
- as predefinições, os ajustes fixados no painel rápido, as flags do Turnip e os botões do controle;
- as chaves e os tipos que o núcleo lê, usados para montar o arquivo `.config.toml` de cada jogo;
- os patches e as correções automáticas dos jogos da biblioteca, do catálogo que vai no APK;
- os textos das telas, copiados do app em português;
- no atualizador, a release estável: título, tamanho e SHA-256 do APK e o resumo em português que a própria release publica;
- a lista de licenças que o app mostra em **Sobre**.

São exemplos:

- os jogos da biblioteca são os medidos no README, com os Title IDs reais, mas formato, tamanho, sessões, saves, coleções e capas são ilustrativos. A mediana de FPS da última sessão segue a tabela de desempenho do README; o resto dos números, não;
- nenhum jogo real recebe avaliação de compatibilidade. Falhas, sessões interrompidas e a comparação A/B usam jogos fictícios (Title IDs `FFFF0001` a `FFFF0004`);
- as capas são marcadores com as iniciais do jogo, e a tela em jogo mostra um quadro neutro no lugar da imagem;
- perfis, drivers (Turnip A a F), a GPU, os dados do aparelho, as pastas e os arquivos.

A barra do simulador traz o seletor **Tela** e, em algumas telas, seletores de exemplo (por exemplo, o estado do atualizador ou o cartão SD dentro ou fora).

## Anotações

O botão **Anotações** marca na tela o que cada parte faz, em três cores:

- **Como o app faz**: o comportamento do app nesta tela, com o arquivo do código que a implementa;
- **Diferente no app**: onde o app real difere do que o simulador mostra;
- **Só na simulação**: o que existe só para a demonstração funcionar.

Embaixo do aparelho, cada tela diz onde ela fica no app, como ela muda no modo controle e quais prints reais existem dela na [documentação da interface](doc:interface).

## Teclado, controle e toque

Clique na tela do aparelho, ou chegue nela com <kbd>Tab</kbd>, para usar o teclado; <kbd>Tab</kbd> sai dela.

| Tecla | No controle | O que faz |
|---|---|---|
| <kbd>←</kbd> <kbd>→</kbd> <kbd>↑</kbd> <kbd>↓</kbd> | direcional | move o foco |
| <kbd>Enter</kbd> | A | abre, escolhe ou muda o valor |
| <kbd>Esc</kbd> | B | volta |
| <kbd>Q</kbd> <kbd>E</kbd> | LB e RB | troca de aba ou de seção |
| <kbd>F</kbd> | Y | favorita o jogo |
| <kbd>I</kbd> | X | abre a ficha |
| <kbd>M</kbd> | Start | abre o menu |
| <kbd>/</kbd> | — | busca jogos |

Um controle ligado ao computador funciona pela Gamepad API do navegador. Em **Automático**, o simulador troca para o modo controle quando ele aparece, como o app faz. **Deitado**, **Em pé** e **Tela cheia** mudam o aparelho.

## Arquivo de configuração de um jogo

Na ficha de um jogo, mude ajustes em **Este jogo** e abra **TOML**. O simulador mostra o arquivo que o app gravaria em `{{app.pastaConfigJogos}}/<TITLE ID>.config.toml`, só com as chaves que mudam e no formato que o núcleo lê. Dá para trocar o Title ID (8 dígitos hexadecimais, diferente de `00000000`), copiar ou baixar o arquivo. Quando nenhuma chave muda, não há arquivo, como no app. O formato e cada chave estão em [Ajustes](doc:ajustes) e na [referência](doc:referencia-de-ajustes).

## Links diretos e o que fica guardado

O endereço guarda a tela aberta: [simulador#drivers](ferramenta:drivers) abre direto em Drivers, e [simulador#update](ferramenta:update) no atualizador. O modo, a orientação e as anotações ficam guardados neste navegador. Os ajustes, perfis e pastas que você muda valem só enquanto a página está aberta: recarregar volta aos exemplos.

::: nota Sem JavaScript
O simulador precisa de JavaScript. Sem ele, os prints reais de cada tela estão na [documentação da interface](doc:interface).
:::
