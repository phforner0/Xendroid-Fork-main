---
title: Referência de ajustes
description: Todos os ajustes do emulador que o app mostra, com o nome na interface, a chave do TOML, o padrão, as opções, o nível e quando cada um vale.
section: referencia
order: 1
---

Esta página é gerada a partir do código do app na {{estavel.nome}}: o esquema de ajustes, o catálogo da interface, os textos em português e o modelo `default_config.toml`. Ela muda sozinha quando o app muda.

- **Nome**: como aparece no app. Alguns ajustes ainda não têm tradução e aparecem em inglês, como no app.
- **Chave**: a seção e o nome no arquivo de configuração, como em `[GPU] framerate_limit`. A busca do site (<kbd>/</kbd>) acha um ajuste pela chave.
- **Padrão**: o valor que o app grava na primeira abertura. Quando o modelo `default_config.toml` traz a chave, vale o do modelo; senão, o do esquema do app.
- **Nível**: Essencial, Avançado ou Tudo (veja [níveis](doc:ajustes#niveis-essencial-avancado-e-tudo)).

{{> ajustes-resumo}}

## Diferenças entre as versões

{{> ajustes-diferencas}}

{{> ajustes}}
