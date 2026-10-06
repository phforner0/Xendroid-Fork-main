---
title: Compatibilidade e limitações
description: As correções que o núcleo aplica sozinho por jogo, as notas de compatibilidade do projeto, como avaliar um jogo no seu aparelho e o que ainda limita o Xendroid+.
section: referencia
order: 2
conferido: e179885e7
fontes: [emulator-core/src/main/cpp/xenia/src/xenia/game_quirks.cc, GAME_COMPAT.md, app/src/main/java/xendroid/compose/compatibility/CompatibilityStore.kt, app/build.gradle, docs/ui-redesign/app.md]
---

O Xendroid+ não tem uma lista pública de compatibilidade. O que roda e como roda depende do jogo, do aparelho, do driver da GPU e da versão do app. Esta página reúne o que o projeto documenta.

## Correções automáticas por jogo {#correcoes-automaticas}

O núcleo traz **{{contagens.correcoes}} ajustes para {{contagens.jogosCorrecoes}} jogos** (`game_quirks.cc`). Eles entram quando o título inicia, com a prioridade de um ajuste do próprio jogo: acima da config global e abaixo do arquivo `config/<TITLE ID>.config.toml`. Você não precisa configurar nada, e um valor posto por você no arquivo do jogo vence a correção. O log do emulador registra cada uma como `Game quirk for <TITLE ID>: <cvar>`.

{{> correcoes}}

A maioria corrige travamentos e falhas de desenho no driver Turnip ou ganha tempo de GPU medido num POCO F7; os comentários de cada entrada, em [game_quirks.cc](repo:emulator-core/src/main/cpp/xenia/src/xenia/game_quirks.cc), trazem a medida. Os nomes dos jogos vêm dos arquivos de patch do repositório ou, quando não há patch, da base de títulos do Xenia que acompanha o núcleo, usada aqui só para o nome.

## Notas do projeto por jogo

O [GAME_COMPAT.md](repo:GAME_COMPAT.md) documenta dois jogos com mais detalhe:

- **Ninja Gaiden II** (`544307D5`): jogável com dois ajustes no arquivo do jogo, `clear_memory_page_state = true` (traz de volta os modelos dos personagens) e `depth_float24_convert_in_pixel_shader = true` (evita travamentos da GPU no Adreno), ambos na seção `[GPU]`.
- **Fable II** (`4D5307F1`): roda sem ajuste especial desde junho de 2026, com problemas conhecidos (chão transparente em alguns lugares, falhas ocasionais no carregamento e cortes de áudio).

O mesmo documento traz notas para drivers e aparelhos: `vulkan_mid_frame_submission_draws` em alguns Adreno 830, `depth_float24_convert_in_pixel_shader` global em drivers que falham no caminho float32 e `vulkan_dynamic_constant_buffers` desligado nos drivers da Qualcomm.

::: aviso Partes desatualizadas do GAME_COMPAT.md
- O caminho certo do arquivo de cada jogo é `{{app.configJogo}}`; o documento cita outro pacote e outra pasta.
- O patch do Fable II que o documento diz estar no repositório (`patches/4D5307F1.patch.toml`) não existe; o catálogo tem outros patches para esse jogo.
- O padrão de `vulkan_mid_frame_submission_draws` hoje é 1300, não 0.
:::

## Avaliar um jogo no seu aparelho

Na ficha → menu ⋮ → **Avaliar compatibilidade**, escolha como o jogo rodou: não abre, abre sem imagem, só abertura ou menus, no jogo com problemas ou jogável. A avaliação fica só no seu aparelho, com a versão do app, a GPU, o driver e a data, sem rede. Ela entra no [backup dos ajustes](doc:jogos-e-arquivos#backup-dos-ajustes).

## Limitações conhecidas

- **Experimental.** Alguns jogos rodam bem, outros ainda têm falhas ou não iniciam.
- **Drivers personalizados só em Adreno** com KGSL. Nas outras GPUs, só o driver do sistema.
- **Geração de quadros** é experimental, começa desligada a cada abertura e o LSFG precisa do seu `Lossless.dll`. Cadência e latência no aparelho ainda não foram validadas.
- **Celular como controle** é experimental e só funciona na rede local.
- **Android 10** só abre jogos vindos de um frontend.
- **Ajustes da comunidade** e o **catálogo de compatibilidade** existem no código, mas ficam desligados nas versões publicadas: nenhuma tem servidor configurado.
- **Perfis recomendados por jogo**: a lista que vem no app está vazia.
- **Interface nova** (biblioteca, ficha, menu em jogo e as demais telas): o documento do redesenho mantém uma lista de itens ainda por conferir num aparelho, por lote. Veja [docs/ui-redesign/app.md](repo:docs/ui-redesign/app.md).

## Propostas fora do app

Do protótipo da interface, três propostas ficaram de fora, sem data prevista:

- um mapa de teclas para cada controle;
- achar o jogo na rede e ler um QR code no celular como controle;
- procurar o arquivo do disco quando a troca de disco não acha nenhum (hoje, Cancelar é a saída, e o disco aparece depois de colocado numa pasta de jogos).

## Ajudar com relatos

O repositório tem um modelo de issue para relatos de desempenho, que pede CPU, GPU, fabricante, versão do Android, jogo e notas. Inclua também a versão do app (em **Sobre**), o driver e o Title ID, e anexe o [diagnóstico da sessão](doc:problemas#compartilhar-um-diagnostico). A conversa da comunidade fica no [Discord]({{discord}}).
