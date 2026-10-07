---
title: Instalar e atualizar
description: Baixar o APK publicado, conferir o SHA-256, instalar e manter o app atualizado pelo próprio Xendroid+.
section: comecar
order: 2
conferido: 29ef9e7c7
fontes: [app/build.gradle, app/src/main/java/xendroid/compose/updater/updater.kt, app/src/main/java/xendroid/compose/updater/ReleaseFeed.kt, app/src/main/java/xendroid/compose/updater/ReleaseTags.kt, app/src/main/java/xendroid/compose/updater/UpdateInstaller.kt, app/src/main/java/xendroid/compose/updater/UpdateScreen.kt, .github/workflows/XenDroid.yml]
---

## Baixar {#baixar}

Cada versão publicada fica nas [releases do GitHub]({{repo}}/releases) do projeto, com um único APK chamado `XenDroid_Release_<commit>.apk`. A versão estável atual é a **{{estavel.nome}}**, de {{estavel.data}}:

| O quê | Valor |
|---|---|
| Arquivo | `{{estavel.apk.nome}}` |
| Tamanho | {{estavel.apk.tamanho}} |
| SHA-256 | `{{estavel.apk.sha256}}` |
| Pacote | `{{app.pacote}}` |

O APK só traz o núcleo para ARM de 64 bits (`{{app.abi}}`) e pede Android {{app.androidMin}} ou mais novo; em outros aparelhos o Android recusa a instalação. Veja os [requisitos](doc:requisitos).

## Conferir o SHA-256

O GitHub publica o SHA-256 de cada APK. Conferir o arquivo baixado garante que ele é o mesmo que o projeto publicou:

```sh
# Linux
sha256sum XenDroid_Release_*.apk
# macOS
shasum -a 256 XenDroid_Release_*.apk
# Windows (PowerShell ou cmd)
certutil -hashfile XenDroid_Release_<commit>.apk SHA256
```

O resultado tem que ser igual ao da tabela acima (ou ao da página da release).

## Instalar

1. Abra o APK no telefone, pelo navegador ou pelo gerenciador de arquivos.
2. O Android pede para permitir que esse app instale apps de fora da loja. Permita só para ele.
3. Confirme a instalação.

Atualizar por cima de uma versão anterior mantém os seus dados (jogos encontrados, ajustes, perfis e saves), desde que as duas estejam assinadas com a mesma chave. Desinstalar o app apaga a pasta de dados dele (`{{app.pastaDados}}`), com os saves e os ajustes por jogo: faça um backup antes (veja [backup dos ajustes](doc:jogos-e-arquivos#backup-dos-ajustes) e [saves](doc:interface#perfis-e-saves)).

::: nota Pacote de teste
Os builds dos pull requests geram outro pacote, `{{app.pacoteTeste}}`, que instala ao lado do app normal e é depurável. Ele é feito para testes e não procura atualizações.
:::

## Atualizar pelo app

O pacote publicado (`{{app.pacote}}`) procura versões novas nas releases do GitHub e só instala o que passa por três conferências:

1. o download tem que bater com o SHA-256 publicado; sem SHA-256, o app só oferece a página da versão, para você instalar à mão;
2. a versão nova tem de ser do mesmo pacote e ter um número de build maior;
3. ela tem de estar assinada com a mesma chave; se não estiver, o app explica que o Android recusaria a atualização.

{{> print id=atualizador legenda="Atualizações do app: a versão instalada, o canal e o cartão da atualização com as etapas baixar, conferir o SHA-256 e instalar."}}

### Canais

Em **Configurações → App → Atualizações**:

- **Estável** (padrão): oferece a última versão publicada.
- **Prévia**: também oferece as prévias, que podem ser menos testadas.
- **Desligado**: nunca procura.

### Quando o app procura

- Ao abrir o app, no máximo a cada 5 minutos, e só mostra algo se houver versão nova. Não procura em builds de depuração nem quando o app foi aberto por um frontend.
- Na hora que você pedir: menu ⋮ da biblioteca → **Procurar atualizações**, na tela **Sobre** ou em **Configurações → App → Atualizações → Procurar agora**.

**Pular esta versão** faz o app não oferecer de novo essa versão nem as anteriores.

### Permissão para instalar

A primeira atualização pede a permissão **Instalar apps desconhecidos** para o Xendroid+. O app abre a tela do sistema; permita e toque de novo em **Baixar e instalar**. O instalador do Android sempre pede a confirmação final.

::: versao desde=7104f6341
O cartão da atualização mostra o resumo das mudanças no idioma do app, tirado das notas da release.
:::

### Versões que o app não oferece

- Releases antigas com tag `XenDroid-<commit>`, sem número de build: elas nunca aparecem como atualização. Para sair delas, instale uma versão nova à mão.
- Builds compilados por você (número de build 1): nunca recebem oferta.

::: aviso Repositório consultado
O app procura as releases em `{{app.repoAtualizacoes}}`, o nome anterior do repositório; as releases ficam hoje em `phforner0/Xendroid-Plus`. O GitHub costuma redirecionar o nome antigo para o novo, mas este site não conseguiu conferir isso. Se **Procurar atualizações** não achar uma versão que já está nas releases, baixe o APK pela página de releases.
:::

## O que cada versão mudou

As notas de cada versão, com o resumo em português, estão em [Novidades e histórico](doc:historico).
