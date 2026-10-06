---
title: Drivers da GPU
description: Driver do sistema ou Turnip, onde trocar, como os pacotes são baixados e conferidos, e as flags do Turnip.
section: usar
order: 4
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/ui/drivers/DriversScreen.kt, app/src/main/java/xendroid/compose/driver/DriverSources.kt, app/src/main/java/xendroid/compose/driver/DriverPackageInstaller.kt, app/src/main/java/xendroid/compose/driver/DriverSuggestion.kt, app/src/main/java/xendroid/compose/driver/CustomDrivers.kt, app/src/main/java/xendroid/compose/settings/TurnipFlags.kt, app/src/main/res/values-pt-rBR/strings_app.xml]
---

O Xendroid+ desenha com Vulkan. Por padrão usa o **driver Vulkan do sistema**, que vem com o Android e está sempre disponível. Em GPUs Adreno dá para trocar por um driver personalizado, como o **Turnip** (o driver Vulkan livre do Mesa para Adreno), que é o foco das otimizações do projeto.

::: nota Só em Adreno
Drivers personalizados só carregam em GPUs Adreno com o driver de kernel KGSL da Qualcomm. Nos outros aparelhos a tela Drivers avisa que todos os jogos usam o driver do sistema.
:::

{{> print id=drivers legenda="Drivers de GPU: o escolhido e o que a última sessão carregou, instalados, para baixar, fontes e opções do Turnip."}}

## Onde trocar

- **Área Drivers** (no trilho, deitado; no menu Start, com controle; em pé, por **Configurações → Resumo → Driver → Gerenciar drivers**). A escolha vale para **todos os jogos a partir da próxima abertura**; a anterior fica a um toque, e **Usar o driver do sistema** volta ao padrão.
- **Por jogo**: na ficha → **Driver e Vulkan**.
- **Só numa abertura**: na ficha → [Iniciar com…](doc:interface#iniciar-com) → **Driver**.

A seção **Em uso** mostra o driver escolhido e o que a última sessão de fato carregou. Se um driver personalizado não carregou (arquivo ausente ou recusado), ela avisa que o do sistema foi usado. Quando um jogo não abre com um driver personalizado, a tela de falha oferece **Tentar com o driver do sistema** só naquela abertura.

## Baixar e instalar

- **Fontes**: repositórios do GitHub com releases de drivers. A fonte padrão é `K11MCH1/AdrenoToolsDrivers`; dá para ter até 8. O app lê as releases publicadas (ignora rascunhos e prévias) e só os arquivos `.zip`.
- **Sugestão para a sua GPU**: pelo nome dos arquivos e das releases, o modelo exato vem antes da família (por exemplo, a7xx), sempre da mesma família e o mais novo primeiro. O app avisa que é uma escolha pelos nomes, não uma garantia.
- **Importar ZIP**: instala um pacote que você já tem.

Todo pacote é conferido antes de instalar:

1. o **SHA-256**, quando a release publica um: o arquivo baixado tem que bater; o selo diz “SHA-256 publicado” ou “Sem checksum publicado”;
2. a biblioteca tem que ser Vulkan para ARM de 64 bits;
3. o ZIP tem limites de tamanho (64 MB compactado, 256 MB descompactado) e precisa indicar a biblioteca no `meta.json` ou ter um único `.so`.

O pacote em uso não pode ser removido. Remover um pacote apaga os arquivos; para usar de novo, baixe ou importe outra vez.

## Opções do Turnip {#opcoes-do-turnip}

As flags do Turnip (a variável `TU_DEBUG`) são passadas ao driver quando o próximo jogo inicia. Valem para todos os jogos, e cada jogo pode ter as próprias na ficha. Drivers da Qualcomm ignoram essas flags. O padrão do Xendroid+ é **sysmem**.

{{> turnip-flags}}

`sysmem` e `gmem` se excluem. Flags que a versão do app não conhece ficam como estão. A última linha da tela mostra o valor final, como `TU_DEBUG=sysmem`.

## Quando trocar

- **Comece pelo driver do sistema.** O assistente da primeira abertura lembra que ele basta para começar.
- Um Turnip recente costuma ser o caminho das otimizações do projeto em Adreno 7xx e 8xx, e as [medições publicadas](doc:desempenho#medicoes-publicadas) foram feitas com Turnip.
- Se o jogo fecha ou trava com um driver personalizado, teste o do sistema. O app sugere isso sozinho depois de um travamento nativo com driver personalizado (veja [sugestões ao fim da sessão](doc:desempenho#sugestoes-ao-fim-da-sessao)).

O [simulador](ferramenta:drivers) mostra a tela Drivers com o catálogo de exemplo e as flags reais.
