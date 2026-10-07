---
title: Primeiros passos
description: Do APK instalado ao primeiro jogo aberto, passando pelo assistente da primeira abertura.
section: comecar
order: 1
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/ui/library/FirstRunAssistant.kt, app/src/main/java/xendroid/compose/ui/library/FirstRun.kt, app/src/main/java/xendroid/compose/data/StandardFolders.kt, app/src/main/res/values-pt-rBR/strings.xml, app/src/main/res/values-pt-rBR/strings_system.xml]
---

O Xendroid+ emula o Xbox 360 no Android. Ele não traz jogos: você usa cópias dos seus próprios discos ou do seu console. Esta página leva do APK instalado até o primeiro jogo aberto.

::: aviso Experimental
Como cada jogo roda depende do jogo, do aparelho e do driver da GPU. Alguns títulos rodam bem, outros ainda têm falhas ou não iniciam. Antes de instalar, confira os [requisitos](doc:requisitos).
:::

## 1. Instale o APK

Baixe o APK da {{estavel.nome}} pelo [link de download](doc:instalacao#baixar) ou nas releases do GitHub e instale. O passo a passo, com a conferência do SHA-256 e a permissão de instalar apps, está em [Instalar e atualizar](doc:instalacao).

## 2. Siga o assistente da primeira abertura

Na primeira vez que o app abre, o assistente aparece sozinho. São cinco passos, sempre com **Pular** e **Voltar** à mão; tudo pode ser mudado depois.

{{> print id=primeira-abertura legenda="Passo “Este telefone”: as verificações da GPU, do processador, do Android e da pasta de jogos."}}

1. **Este telefone.** O app confere se há GPU com Vulkan (sem ela os jogos não rodam), se o processador é ARM de 64 bits, a versão do Android e se já existe uma pasta de jogos. No Android 10 aparece um aviso: escolher uma pasta de jogos exige Android 11 ou mais novo.
2. **Seus jogos.** Escolha a pasta onde estão os seus jogos (ISO, XEX, ZAR ou pacotes). A busca acontece ali mesmo, com quantos jogos foram achados e as capas. Se ainda não tem uma pasta, **Criar pastas padrão** cria `XenDroid/Jogos`, `XenDroid/TU` e `XenDroid/DLC` no armazenamento interno: a primeira vira pasta de jogos; as outras duas, pastas de conteúdo (atualizações e DLC).
3. **Idioma e região.** O assistente propõe o idioma e o país do console a partir do idioma do telefone; **Usar nos jogos** grava os dois na configuração global. Se o idioma do telefone não existe no console, os jogos ficam em inglês.
4. **Perfil.** Os jogos salvam num perfil (gamertag, até 15 caracteres). Crie o seu ali mesmo; ele fica ativo como P1. Se você pular, o app cria um perfil chamado “XenDroid”.
5. **Como usar.** Escolha o modo da interface (Automático, Toque ou Controle) e quantos ajustes mostrar (Essencial, Avançado ou Tudo). O driver Vulkan do próprio telefone basta para começar; um driver Turnip pode ser instalado depois em [Drivers](doc:drivers).

O assistente reabre pelo menu ⋮ da biblioteca (**Assistente de configuração**) ou pela tela **Sobre**.

::: nota Acesso a todos os arquivos
A biblioteca lê as pastas de jogos pelo caminho real, o que no Android 11 ou mais novo exige a permissão **Acesso a todos os arquivos**. Quando ela falta, o app abre a tela do sistema para você permitir e confere de novo ao voltar.
:::

## 3. Abra um jogo

Toque num jogo da biblioteca. Em paisagem, o primeiro toque mostra o painel do jogo ao lado, com **Jogar** e os ajustes rápidos; um toque longo (ou **Ficha**) abre a ficha completa. **Jogar** começa o carregamento, que mostra as etapas da abertura.

A primeira abertura de um jogo costuma ser a mais lenta: o app ainda está montando os shaders e pipelines daquele jogo, e os engasgos diminuem nas sessões seguintes.

## 4. Durante o jogo

O menu em jogo abre de três jeitos:

- deslizando a partir da borda esquerda da tela;
- com o **Voltar** do Android (gesto ou botão);
- com o botão **Guia** do controle.

Por padrão, o jogo fica pausado enquanto o menu está aberto. Ali estão a imagem, o limite de FPS, o HUD de desempenho, os controles e a saída do jogo. Veja [o menu em jogo](doc:interface#menu-em-jogo) em detalhe.

## Próximos passos

- [Ajustes e efeitos](doc:ajustes): como os ajustes globais e por jogo funcionam.
- [Drivers da GPU](doc:drivers): quando vale trocar o driver do sistema por um Turnip.
- [Problemas e logs](doc:problemas): o que fazer quando um jogo não abre.
- O [simulador](ferramenta:firstrun) mostra o assistente e as outras telas no navegador.
