---
title: Requisitos do aparelho
description: Android, processador, GPU com Vulkan, drivers e permissões que o Xendroid+ usa, e o que o app confere na primeira abertura.
section: comecar
order: 3
conferido: e179885e7
fontes: [app/build.gradle, app/src/main/AndroidManifest.xml, app/src/main/java/xendroid/compose/ui/library/FirstRun.kt, app/src/main/java/xendroid/compose/driver/CustomDrivers.kt, app/src/main/java/xendroid/compose/core/AllFilesAccess.kt, README.pt-BR.md]
---

{{> requisitos}}

## Android

O app instala a partir do Android {{app.androidMin}} (API {{app.apiMin}}), mas o fluxo completo pede **Android 11 ou mais novo**:

- A biblioteca lê as pastas de jogos pelo caminho real, com a permissão **Acesso a todos os arquivos**, que só existe a partir do Android 11.
- No Android 10, o app não escolhe pastas de jogos: a biblioteca mostra “Escolher uma pasta de jogos exige Android 11 ou mais novo” e os itens de pastas somem do menu. Jogos abertos por um [frontend externo](doc:jogos-e-arquivos#frontends-externos) continuam funcionando.
- A opção “Entrada sem buffer” dos controles também só funciona a partir do Android 11.

## Processador

Só **ARM de 64 bits** (`{{app.abi}}`). O núcleo do emulador é compilado só para essa arquitetura, e o APK declara só ela: em aparelhos de 32 bits ou x86 o Android não instala.

## GPU e Vulkan

A GPU precisa ter **Vulkan**. Sem nenhum dispositivo Vulkan, a biblioteca vira a tela “Este aparelho não tem GPU Vulkan”, com as verificações e um botão para copiar os dados do aparelho; nada é apagado, e se uma atualização do Android trouxer Vulkan o app volta a funcionar.

O foco do projeto são as GPUs **Adreno 7xx e 8xx** com o driver **Turnip**, e o README recomenda um **Snapdragon 8 Gen 2 ou mais novo**. As [medições publicadas](doc:desempenho#medicoes-publicadas) foram feitas num POCO F7 (Snapdragon 8s Gen 4, Adreno 825).

### Drivers personalizados

Drivers como o Turnip só carregam em GPUs **Adreno com o driver de kernel KGSL** da Qualcomm: o app confere se o aparelho tem `/dev/kgsl-3d0`. Nos outros aparelhos todos os jogos usam o driver Vulkan do sistema, e a tela Drivers avisa isso. Veja [Drivers da GPU](doc:drivers).

## O que o app confere na primeira abertura

O primeiro passo do assistente mostra quatro verificações:

| Verificação | Bloqueia? | O que diz |
|---|---|---|
| GPU Vulkan | sim | sem dispositivo Vulkan, os jogos não rodam neste telefone |
| ARM de 64 bits | sim | o núcleo é feito só para arm64-v8a |
| Android | não | no Android 10, avisa que escolher pasta de jogos exige Android 11 |
| Pasta de jogos | não | diz se já há uma pasta escolhida |

## Permissões

| Permissão | Para quê |
|---|---|
| Acesso a todos os arquivos | ler as pastas de jogos pelo caminho, criar as pastas padrão |
| Instalar apps desconhecidos | instalar as atualizações que o próprio app baixa |
| Internet e estado da rede | procurar atualizações, baixar drivers, celular como controle na rede local |
| Vibração | vibrar o telefone nos controles de toque |

O acesso ao armazenamento antigo (leitura e gravação) só é pedido nas versões do Android que ainda usam esse modelo.

## Calor e bateria

Emular um Xbox 360 exige muito da GPU e da CPU. O app avisa quando o telefone chega perto do limite de calor e quando começa a reduzir o desempenho; o HUD mostra as temperaturas e a bateria. Limites de FPS menores, escala de resolução 1× e não forçar os clocks máximos da GPU mantêm o aparelho mais frio.

## O que não está definido

O projeto não publica uma lista de aparelhos testados nem um mínimo de memória RAM. As únicas medições publicadas são as do README, num aparelho só.
