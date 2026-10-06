---
title: Jogos, pastas e arquivos
description: Formatos aceitos, pastas de jogos, atualizações e DLC, patches, arquivos de configuração, pasta de dados, backup dos ajustes e frontends externos.
navTitle: Jogos e arquivos
section: usar
order: 2
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/data/GameFormat.kt, app/src/main/java/xendroid/compose/data/LibraryWalker.kt, app/src/main/java/xendroid/compose/data/GameLibraryRepository.kt, app/src/main/java/xendroid/compose/ui/content/InstallContentViewModel.kt, app/src/main/java/xendroid/compose/saves/ContentTrash.kt, app/src/main/java/xendroid/compose/patches/PatchStore.kt, app/src/main/java/xendroid/compose/settings/ConfigStore.kt, app/src/main/java/xendroid/compose/bundle/DataBundle.kt, app/src/main/java/xendroid/compose/bundle/DataBundleIo.kt, app/src/main/java/xendroid/compose/core/FrontendLaunch.kt, app/build.gradle]
---

## Formatos de jogo

| Formato | Como a biblioteca reconhece |
|---|---|
| ISO | arquivo `.iso` (imagem de disco) |
| ZAR | arquivo `.zar` (disco compactado) |
| GOD | arquivo sem extensão com cabeçalho Games on Demand |
| XBLA | arquivo sem extensão com cabeçalho STFS de jogo arcade |
| Pasta XEX | uma pasta com `default.xex`; o conteúdo dela não é varrido |

Arquivos sem extensão têm o cabeçalho lido antes: DLC, title updates, perfis e saves são recusados como jogo, e entram só os tipos de jogo completo (Games on Demand, título de Xbox 360, arcade, demo e jogos da comunidade XNA).

## Pastas de jogos

A biblioteca procura em cada pasta de jogos e nas pastas dentro dela, até 12 níveis e 100.000 entradas. Pastas ocultas e as pastas `*.data` dos jogos GOD são puladas; uma pasta dentro de outra é varrida uma vez só. Se a busca parar no limite, o app avisa que a lista está incompleta.

Em **menu ⋮ da biblioteca → Pastas de jogos** (Android 11 ou mais novo):

- adicione várias pastas e veja quantos jogos cada uma tem;
- a primeira pasta recebe os jogos completos instalados de pacotes (**Instalar aqui** troca qual é);
- remover uma pasta só para de procurar nela; os arquivos ficam onde estão;
- **Criar pastas padrão** cria `XenDroid/Jogos`, `XenDroid/TU` e `XenDroid/DLC` no armazenamento interno (o nome da pasta de jogos segue o idioma do app).

**Jogos que saíram da biblioteca** lista os jogos já vistos que a busca não achou mais, com o motivo (arquivo movido ou apagado, pasta indisponível, arquivo fora das pastas). **Onde ele está agora?** abre o navegador de pastas perto do caminho antigo; **Remover da lista** só esconde o jogo.

### Comprimir para .zar

Só para jogos ISO, pela ficha (menu ⋮ ou **Saves e dados**). O `.zar` é criado num arquivo temporário, conferido e copiado ao lado do `.iso`. O `.iso` original fica intacto até o app perguntar se pode apagá-lo; fechar a pergunta mantém o `.iso`.

## Atualizações e DLC {#atualizacoes-e-dlc}

A área **Conteúdo** mostra o que está instalado por jogo (DLC e title updates, com tamanhos) e instala pacotes novos:

1. Em **Conteúdo → Instalar**, o app lista os pacotes encontrados em Downloads e nas **pastas de conteúdo** (com as subpastas), ou você escolhe o arquivo.
2. Ele confere de qual jogo o pacote é e o tipo: DLC, title update ou jogo completo. Pacotes são reconhecidos pelo cabeçalho `CON `, `LIVE` ou `PIRS`.
3. O conteúdo vale na próxima vez que o jogo abrir. Um jogo completo (XBLA, GOD) é copiado para a primeira pasta de jogos.

Antes de instalar, o app confere se há espaço livre com 10 % de folga. Aberto a partir de um jogo, aceita só DLC e title updates daquele jogo.

Remover um conteúdo manda ele para uma **lixeira com cota de 4 GiB** para todos os jogos, de onde dá para restaurar ou apagar de vez. Nada é apagado sozinho; se a lixeira estiver cheia, o app pede para esvaziá-la ou apagar o item de vez. Os arquivos de jogo das suas pastas nunca são tocados.

## Patches {#patches}

O APK traz os {{contagens.arquivosPatch}} arquivos de patch do catálogo Xenia Canary Game Patches, para {{contagens.jogosPatches}} jogos, todos desligados. Na ficha, **Patches e conteúdo** mostra os patches do jogo em três grupos:

- **Para a sua versão**: o arquivo traz o hash do executável que o jogo carregou na última sessão;
- **Para outras versões**: valem para outra versão do jogo (outra title update, por exemplo) e não serão aplicados na sua;
- **Sem versão indicada**.

Ligar um patch copia o arquivo para a pasta de patches do app e muda só a linha `is_enabled` daquele patch. Os patches valem a partir da próxima abertura e só se combinarem com a versão do seu jogo. O ajuste **Aplicar patches** (Console e sistema) liga ou desliga todos. Também dá para importar um arquivo `.patch.toml` seu, do mesmo Title ID, que o app confere antes de aceitar.

::: nota Três arquivos com Title ID trocado
No catálogo, `534507D4 - Syndicate` traz `title_id` 45410923 e os dois arquivos `464507E1 - Farming Simulator` trazem 464507DC. O app lista os patches pelo nome do arquivo e o núcleo aplica pelo conteúdo, então esses três podem não aparecer no jogo esperado. O site confere isso a cada build (veja [Dados do site](doc:dados-do-site#avisos-do-codigo)).
:::

## Arquivos de configuração {#arquivos-de-configuracao}

O app guarda os ajustes nos arquivos que o núcleo do emulador lê:

{{> pastas}}

- A **config global** recebe os ajustes de **Configurações**. Na primeira abertura, o app a cria a partir do modelo `default_config.toml` que vem no APK.
- Cada jogo com ajustes próprios tem um arquivo `config/<TITLE ID>.config.toml`, só com as chaves que mudam. O Title ID vai em maiúsculas.
- A ordem de prioridade, do mais fraco ao mais forte: padrão do núcleo, config global, [correções automáticas por jogo](doc:compatibilidade#correcoes-automaticas) e o arquivo do jogo.

Um arquivo de jogo tem este formato, que o [simulador](ferramenta:game) gera do mesmo jeito:

```toml
# Game-specific config overrides
# Title ID: 4D5309C9

[Display]
postprocess_scaling_and_sharpening = 'fsr'

[GPU]
framerate_limit = 30
```

Cada chave fica na seção (`[GPU]`, `[Display]`…) em que o núcleo a procura; numa seção errada, ou com um valor do tipo errado, o núcleo ignora o valor. O app grava texto entre aspas simples, números sem aspas e `true`/`false` para ligado e desligado.

::: dica Editar à mão
A pasta de dados aparece no gerenciador de arquivos do Android em **Configurações → App → Dados e backup → Abrir no gerenciador de arquivos**. Com nenhum jogo aberto, dá para copiar um arquivo `.config.toml` para a pasta `config`. O app regrava só as chaves que você muda nele e mantém as que não conhece.
:::

## Backup dos ajustes {#backup-dos-ajustes}

**Configurações → App → Dados e backup → Fazer backup ou levar os ajustes** exporta um ZIP (`xendroid-settings-<data>.zip`) com:

- a config global e as configs por jogo;
- o layout dos controles de toque;
- favoritos, ordem da biblioteca e coleções;
- as suas notas de compatibilidade.

Saves, perfis, jogos, drivers, dados do LSFG, logs e o histórico de jogo **não** entram. Caminhos que só valem no aparelho de origem (pastas de armazenamento e o driver personalizado) também não saem. Ao importar, o app mostra antes o que muda e guarda um backup dos ajustes atuais, para dar para desfazer.

## Frontends externos {#frontends-externos}

Outros apps (ES-DE, Daijishō, Beacon e parecidos) podem abrir um jogo direto no Xendroid+. O componente é `xendroid.compose.EmulatorHostActivity`, no pacote instalado (`{{app.pacote}}` no APK publicado), com a ação `xendroid.intent.action.xendroid` ou `android.intent.action.VIEW`. O jogo vai, nesta ordem:

1. no extra de texto `game_uri`;
2. no extra `AutoStartFile` (a convenção do Dolphin);
3. na URI de dados do intent.

O valor pode ser um caminho absoluto (o mais seguro), `file://` ou `content://`. Por exemplo, pelo adb:

```sh
adb shell am start -n {{app.pacote}}/xendroid.compose.EmulatorHostActivity \
  -a xendroid.intent.action.xendroid \
  --es game_uri '/storage/emulated/0/Games/Xbox 360/Jogo.iso'
```

Um processo roda um jogo só; outro pedido com um jogo aberto vai para a sessão existente. Frontends não conseguem passar as opções de [Iniciar com…](doc:interface#iniciar-com). No Android 10, esse é o único jeito de abrir jogos.

::: aviso Pacote no documento de frontends
O [documento de integração do repositório](repo:docs/frontend-integration.md) usa o pacote `xendroid.compose`, que nenhuma versão publicada usa. Use o pacote instalado, como no exemplo acima. Este caminho não foi testado com um frontend por este site.
:::
