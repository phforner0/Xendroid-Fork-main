# Redesign B + C no app

**Estado:** implementado em Compose na branch `wip/ui-redesign`, do R0 ao lote 7. Os testes locais
passam (unitários, telas no JVM e a compilação dos testes instrumentados); **a validação no
aparelho está pendente em todos os lotes**. Sem PR, merge ou release.

A direção é a escolhida no protótipo ([`bc/`](bc/README.md)): **B como padrão (toque)** e **C como
modo controle**, ligado sozinho quando um controle está conectado (Configurações → App →
Interface: Automático, Sempre toque ou Sempre controle).

## Prints antes e depois

- `prints/app/<lote>/antes-*.png`: as telas como eram, desenhadas a partir do commit anterior ao
  redesenho (`545296bd3`).
- `prints/app/<lote>/depois-*.png`: as telas de agora, desenhadas pelos testes de tela
  (`app/src/testUitest/.../screens/BaseShots.kt`, `Lote1Shots.kt` … `Lote7Shots.kt`).
- Telefone 20:9 (914 × 411 dp deitado, 411 × 914 dp em pé, densidade 1,5×), tema escuro,
  português do Brasil. Os prints com `-controle` no nome estão no modo controle (cor da capa ao
  fundo, dicas dos botões embaixo).
- Os dados são de exemplo: biblioteca com arte gerada, núcleo nativo simulado (tabelas TOML,
  perfis, conteúdo), telefone descrito como POCO F7 com Adreno 740.

Para desenhar de novo (precisa do Android SDK, como no `BUILD.md`):

```sh
./gradlew :app:testUitestUnitTest -Pscreenshots="$PWD/docs/ui-redesign/prints/app"
# um lote só:
./gradlew :app:testUitestUnitTest --tests 'xendroid.compose.screens.Lote6Shots' -Pscreenshots="$PWD/docs/ui-redesign/prints/app"
```

Sem `-Pscreenshots` os mesmos testes desenham cada tela sem gravar arquivo (testes de fumaça).

## O que mudou, lote por lote

### R0: tema e peças base

- `ui/design`: os tokens de B (toque) e C (controle) num `ColorScheme` escuro; Barlow, Barlow Semi
  Condensed e JetBrains Mono (licenças OFL no APK, conferidas por `tools/check-apk.py`); os ícones
  de linha do protótipo; botões, chips, controle segmentado, interruptor, busca, seleção, cartões,
  linhas de lista, notas, selos, capas com a composição para jogos só com ícone, folhas, avisos e
  as molduras de B e C (trilho, barra inferior, telas com seções, menu do controle, dicas, menu do
  Start).
- Modo controle automático, sempre toque ou sempre controle.
- 29 cvars que o core já lê entraram no esquema de ajustes (mais o modo de vídeo personalizado e
  a gama personalizada); níveis Essencial, Avançado e Tudo; ajustes rápidos fixados.

### Base: biblioteca e ficha do jogo

- Biblioteca para toque (trilho, busca, ordem, tamanho das capas, filtros, grade e o painel do
  jogo escolhido com Jogar e ajustes rápidos) e para controle (abas por LB/RB, carrossel, cor da
  capa ao fundo, dicas, menu do Start).
- Ficha do jogo como tela: visão geral, cada grupo de ajustes do jogo com a troca para os valores
  globais, desempenho (gráficos e linha do tempo da última sessão), patches e conteúdo, saves e
  dados.
- **Iniciar com…**: perfil, driver, executável, sem patches, sem os ajustes do jogo e linha de
  comando extra, só para uma abertura (passados ao processo do jogo com o token desta abertura e
  uma lista fechada do que é aceito).

### Lote 1: Configurações e Drivers

- Configurações com o resumo do que está fora do padrão e dos jogos com ajustes próprios, os mesmos
  grupos da ficha (cada ajuste diz quantos jogos usam outro valor), interface, idioma, dados e
  backup, atualizações, comunidade e diagnóstico.
- Área Drivers: o escolhido e o que a última sessão carregou, pacotes instalados, downloads com
  SHA-256 conferido, fontes e flags do Turnip.

### Lote 2: jogo aberto

- Carregamento com as etapas do boot e o tempo de cada uma, e com o que o jogo leva nesta abertura.
- Falha ao abrir com o motivo numa frase e o próximo passo certo (tentar uma vez com o driver do
  sistema, ir aos saves, tentar de novo).
- Menu em jogo redesenhado, com a linha de estado e a navegação por controle de antes.

### Lote 3: Controles

- Controles virou uma área do app: quem joga como P1–P4, opções e layouts do toque, cada controle
  físico com a própria vibração, ajustes do core para controles, telefones como controle e as
  quatro ferramentas (o editor de toque e o celular como controle estavam sem caminho desde que o
  menu antigo da biblioteca saiu).
- Mapeamento de teclas com o controle desenhado, editor de toque com painel do controle escolhido
  e desfazer, teste de controles que acende o que é apertado, celular como controle redesenhado.

### Lote 4: Perfis e saves

- Perfis como área: cartões (avatar, P1 ativo, idioma, região, saves por jogo), quem joga (P1–P4,
  perguntar antes de jogar) e a lixeira de perfis; criar e editar numa folha só.
- Jogar como numa folha; os saves de um jogo com gamertag e avatar em vez do XUID, os nomes dos
  saves lidos dos cabeçalhos, exportar e importar, e a pasta de sincronização numa seção própria.

### Lote 5: Conteúdo, Diagnóstico e Comparar execuções

- Conteúdo como área: DLC e title updates de todos os jogos, a lixeira com a cota, instalar com os
  pacotes achados em Downloads; pela ficha, só o daquele jogo.
- Diagnóstico com as sessões guardadas, os jogos de cada uma e como terminou; o resumo antes de
  compartilhar e o que sai do arquivo.
- Comparar execuções com as execuções (A / – / B) ao lado do resultado e dos avisos.

### Lote 6: primeira abertura e telas do app

- **Primeira abertura** (`FirstRunAssistant.kt`): cinco passos com Pular e Voltar sempre à mão:
  este telefone (GPU Vulkan, ARM de 64 bits, Android, pasta), seus jogos (a pasta escolhida, a busca
  com quantos jogos e as capas, adicionar outra), idioma e região dos jogos, o perfil criado ali
  mesmo (vira P1) e como usar (modo da interface, ajustes mostrados, driver opcional). Volta ao
  mesmo passo depois do navegador de pastas; com controle, cada passo começa no botão principal;
  reabre pelo menu da biblioteca ou por Sobre.
- **Pastas de jogos** (`GameFoldersScreen.kt`, no lugar do `GameFoldersDialog.kt`): quantos jogos
  cada pasta tem, a que recebe as instalações ("Instalar aqui" troca), as indisponíveis agora com o
  motivo, adicionar, procurar de novo e remover com Desfazer (os arquivos nunca são tocados).
- **Navegador de pastas** (`FolderBrowserScreen.kt`): armazenamento interno e cartões SD/USB como
  chips, o caminho em passos clicáveis, quantos jogos há em cada pasta antes de entrar
  (`FolderGames.kt`, contado em segundo plano e com limite; um ISO só conta com a assinatura de
  disco do Xbox onde o núcleo a procura, então o ISO de outro console não vira "1 jogo") e na
  pasta atual; também escolhe um arquivo (pacote a instalar, jogo que mudou de lugar).
- Caminhos ditos como o app Arquivos diz: "Armazenamento interno › Games › Xbox 360"
  (`StoragePaths.kt`).
- **Jogos que saíram** (`MissingGamesScreen.kt`, no lugar do `MissingGamesDialog.kt`): capa
  guardada, motivo (arquivo sumiu, pasta indisponível, fora das pastas), tempo jogado, Title ID e o
  caminho de antes; "Onde ele está agora?" abre o navegador perto do caminho antigo e adiciona a
  pasta do arquivo escolhido; adicionar a pasta; remover só da lista.
- **Sem Vulkan** (`NoVulkanScreen.kt`): o porquê, as verificações, Sair e copiar os dados do
  aparelho.
- **Atualizações do app** (`UpdateScreen.kt`): versão, canal e última procura com "Procurar
  agora", o canal, e o cartão da atualização (notas, tamanho, SHA-256) com as etapas baixar →
  conferir o SHA-256 → instalar, progresso e cancelar, pular esta versão e os erros numa frase. O
  mesmo cartão aparece na folha da procura automática ao abrir o app e em Configurações. Um build
  sem canal diz de onde vêm as atualizações.
- **Sobre** (`AboutScreen.kt`, `DeviceInfo.kt`): versão, o aparelho em tabela com copiar tudo,
  atalhos (atualizações, diagnóstico, assistente) e créditos e licenças.

### Lote 7: os painéis que o jogo pede

- Moldura comum (`GuestPanelFrame.kt`): painel no topo sobre o jogo parado, dizendo qual jogo
  pergunta (capa e nome) e o que ele pede; as dicas do controle embaixo no modo controle; compacto
  num telefone deitado. Continua sem ser um `Dialog`, que tiraria o foco da janela e pausaria o
  jogo.
- **Mensagem** (`GuestMessageBoxPanel.kt`): título, texto que rola quando é longo com as opções
  sempre à vista, opções em linhas inteiras e "O jogo fica parado até você escolher".
- **Teclado** (`GuestKeyboardPanel.kt`): o pedido do jogo, o campo com o limite contado como o jogo
  conta, as teclas de comando em português (Shift, Espaço, Pronto, Cancelar; ícones quando a
  grade é estreita), o cursor do controle só no modo controle e os atalhos do Xbox 360 à vista (A
  digita, X apaga, Y espaço, LB/RB cursor, L3 Shift, R3 símbolos, ≡ pronto). Por toque, o teclado
  do telefone digita no campo.
- **Troca de disco** (`DiscSwapPanel.kt`): o disco pedido no título e já escolhido na lista, os
  discos achados com o arquivo de cada um, o que estava no drive marcado "o de antes" (a escolha
  fica à vista quando a lista rola) e Cancelar por último, avisando que o jogo já ejetou o disco.

### Ajustes 1: o que veio do aparelho

Retorno de um Xiaomi com Android 16 e Adreno 825. Os "antes" são as capturas do próprio aparelho,
que não entram no repositório (mostram a barra de status de quem testou).

- **Botões de baixo fora da tela** em folhas e diálogos: no Android 15+ o Compose 1.7 dimensiona a
  janela de um diálogo de largura total pela altura da tela *com* as barras do sistema, e a janela
  ainda respeitava as barras, então o rodapé caía para fora. `XdDialogEdgeToEdge()` (em
  `XdSheet.kt`) tira os insets da janela do diálogo; a folha, o guia do controle e o assistente já
  aplicam os insets eles mesmos.
- **Drivers com texto quebrado letra por letra** (retrato): os selos e os botões espremiam o título.
  `XdListRow` ganhou selos que quebram de linha ao lado do título e `actionsBelow`, que põe as
  ações embaixo do texto quando a tela é estreita (menos de 520 dp).
- **HUD de desempenho no visual novo** (`FpsOverlay.kt`): linhas de rótulo e valor na fonte mono,
  FPS em verde, calor perto ou acima do limite em amarelo ou vermelho, métricas que o aparelho não
  informa ficam de fora (em vez de "N/A") e o painel com títulos por grupo (Agora, Ritmo, Trabalho,
  Ajustes em vigor). Números no formato do idioma do aparelho.
- **Opções de Imagem no menu em jogo, aplicadas na hora**: Suavização (FXAA, FXAA extremo),
  Nitidez (CAS ou FSR, cinco níveis) e Pontilhado, além do efeito de escala que já existia. O núcleo
  recebe os valores por `set_image_tuning` e o apresentador os usa no quadro seguinte; "Guardar esta
  imagem para o jogo" grava efeito, suavização, nitidez e pontilhado na config do jogo.

## Testes

Rodados em 2026-10-04, no fim do lote 7:

- `./gradlew :app:testDebugUnitTest`: 570 testes, nenhuma falha. Inclui os novos do redesenho
  (`FolderGamesTest`, `LibraryRootsTest`, `RequestedDiscTest`, `ContentCatalogTest`,
  `DiagnosticsSessionsTest`, `KeyboardGridTest`) e a paridade de textos en/pt-BR
  (`StringResourcesTest`).
- `./gradlew :app:testUitestUnitTest`: 681 testes de tela no JVM (Robolectric + Roborazzi),
  nenhuma falha.
- `./gradlew :app:compileUitestAndroidTestKotlin`: os testes instrumentados compilam
  (`Item38TouchLayoutsTest` foi ajustado ao `onMessage` do diálogo de layouts, do lote 3). Rodá-los
  exige um aparelho (`./gradlew :app:connectedUitestAndroidTest`) e não foi feito.

## Validação no aparelho (pendente)

| Lote | O que conferir |
|---|---|
| R0 / Base | Modo controle ligando e desligando sozinho ao conectar um controle; capas reais; Jogar e Iniciar com… abrindo o jogo |
| 1 | Ajustes gravados no TOML global e do jogo; download de driver com SHA-256 |
| 2 | Etapas do carregamento com um jogo real; falha ao abrir com o driver do sistema; menu em jogo com controle |
| 3 | Captura de tecla com controle real; arrastar no editor de toque; teste de controles; celular como controle na rede |
| 4 | Criar, editar e apagar perfis; Jogar como; exportar, importar e sincronizar saves |
| 5 | Instalar conteúdo de Downloads; lixeira; sessões do diagnóstico com jogos reais; comparar execuções |
| 6 | Assistente numa instalação limpa (acesso a todos os arquivos, Android 10 e 14); navegador com cartão SD e USB (nome do volume); "Onde ele está agora?"; atualização num build `.fork` com canal (baixar, conferir, permissão do instalador); copiar os dados em Sobre |
| 7 | Mensagem, teclado e troca de disco em jogos reais: seleção pelo direcional vinda do host, teclado do telefone sem cobrir o campo, atalhos do Xbox 360, troca num jogo de vários discos |
| Ajustes 1 | Rodapé das folhas visível no Android 15 e 16; Suavização, Nitidez e Pontilhado mudando a imagem na hora num jogo real; "Guardar esta imagem para o jogo" valendo na sessão seguinte |

## Propostas do protótipo que ficaram de fora

- **Mapa de teclas por controle** (lote 3): o host aplica um mapa só a todos os controles; um mapa
  por aparelho precisa da identidade do controle no caminho da entrada.
- **Achar o jogo na rede e ler o QR code** (celular como controle, lote 3): precisa de descoberta
  na rede local (NSD/mDNS) dos dois lados e de câmera, uma permissão nova.
- **Procurar o arquivo do disco** quando nenhum é achado (troca de disco, lote 7): o painel roda
  dentro do jogo; entregar ao core um caminho escolhido ali exige conferir no host que o arquivo é
  um disco daquele título. Ficou de fora para não abrir esse caminho sem a conferência; hoje
  Cancelar continua sendo a saída, e o disco aparece depois de colocado numa pasta de jogos.

## Índice dos prints

| Lote | Antes | Depois |
|---|---|---|
| Base: biblioteca e ficha | [biblioteca paisagem](prints/app/base/antes-biblioteca-paisagem.png), [biblioteca retrato](prints/app/base/antes-biblioteca-retrato.png), [carrossel paisagem](prints/app/base/antes-carrossel-paisagem.png), [ficha paisagem](prints/app/base/antes-ficha-paisagem.png), [ficha retrato](prints/app/base/antes-ficha-retrato.png) | [biblioteca controle retrato](prints/app/base/depois-biblioteca-controle-retrato.png), [biblioteca controle](prints/app/base/depois-biblioteca-controle.png), [biblioteca paisagem](prints/app/base/depois-biblioteca-paisagem.png), [biblioteca retrato](prints/app/base/depois-biblioteca-retrato.png), [ficha ajustes](prints/app/base/depois-ficha-ajustes.png), [ficha conteúdo](prints/app/base/depois-ficha-conteudo.png), [ficha controle ajustes](prints/app/base/depois-ficha-controle-ajustes.png), [ficha controle](prints/app/base/depois-ficha-controle.png), [ficha dados](prints/app/base/depois-ficha-dados.png), [ficha desempenho](prints/app/base/depois-ficha-desempenho.png), [ficha paisagem](prints/app/base/depois-ficha-paisagem.png), [ficha retrato](prints/app/base/depois-ficha-retrato.png), [iniciar com…](prints/app/base/depois-iniciar-com.png) |
| Lote 1: Configurações e Drivers | [ajustes do jogo](prints/app/lote1/antes-ajustes-do-jogo.png), [configurações](prints/app/lote1/antes-configuracoes.png), [drivers](prints/app/lote1/antes-drivers.png) | [configurações controle](prints/app/lote1/depois-configuracoes-controle.png), [configurações imagem](prints/app/lote1/depois-configuracoes-imagem.png), [configurações interface](prints/app/lote1/depois-configuracoes-interface.png), [configurações retrato](prints/app/lote1/depois-configuracoes-retrato.png), [configurações](prints/app/lote1/depois-configuracoes.png), [drivers controle](prints/app/lote1/depois-drivers-controle.png), [drivers retrato](prints/app/lote1/depois-drivers-retrato.png), [drivers](prints/app/lote1/depois-drivers.png) |
| Lote 2: jogo aberto | [carregamento](prints/app/lote2/antes-carregamento.png), [menu em jogo](prints/app/lote2/antes-menu-em-jogo.png) | [carregamento controle](prints/app/lote2/depois-carregamento-controle.png), [carregamento retrato](prints/app/lote2/depois-carregamento-retrato.png), [carregamento](prints/app/lote2/depois-carregamento.png), [falha ao abrir](prints/app/lote2/depois-falha-ao-abrir.png), [menu desempenho](prints/app/lote2/depois-menu-desempenho.png), [menu em jogo controle](prints/app/lote2/depois-menu-em-jogo-controle.png), [menu em jogo retrato](prints/app/lote2/depois-menu-em-jogo-retrato.png), [menu em jogo](prints/app/lote2/depois-menu-em-jogo.png) |
| Lote 3: Controles | [celular como controle](prints/app/lote3/antes-celular-como-controle.png), [editor de toque](prints/app/lote3/antes-editor-de-toque.png), [mapeamento](prints/app/lote3/antes-mapeamento.png), [testar controles](prints/app/lote3/antes-testar-controles.png) | [celular como controle](prints/app/lote3/depois-celular-como-controle.png), [celular jogando](prints/app/lote3/depois-celular-jogando.png), [controles controle](prints/app/lote3/depois-controles-controle.png), [controles fisicos](prints/app/lote3/depois-controles-fisicos.png), [controles movimento](prints/app/lote3/depois-controles-movimento.png), [controles retrato](prints/app/lote3/depois-controles-retrato.png), [controles telefones](prints/app/lote3/depois-controles-telefones.png), [controles toque](prints/app/lote3/depois-controles-toque.png), [controles](prints/app/lote3/depois-controles.png), [editor de toque controle](prints/app/lote3/depois-editor-de-toque-controle.png), [editor de toque gerais](prints/app/lote3/depois-editor-de-toque-gerais.png), [editor de toque](prints/app/lote3/depois-editor-de-toque.png), [mapeamento captura](prints/app/lote3/depois-mapeamento-captura.png), [mapeamento controle](prints/app/lote3/depois-mapeamento-controle.png), [mapeamento retrato](prints/app/lote3/depois-mapeamento-retrato.png), [mapeamento](prints/app/lote3/depois-mapeamento.png), [testar controles retrato](prints/app/lote3/depois-testar-controles-retrato.png), [testar controles](prints/app/lote3/depois-testar-controles.png) |
| Lote 4: Perfis e saves | [jogar como](prints/app/lote4/antes-jogar-como.png), [perfis](prints/app/lote4/antes-perfis.png), [saves](prints/app/lote4/antes-saves.png) | [editar perfil](prints/app/lote4/depois-editar-perfil.png), [jogar como](prints/app/lote4/depois-jogar-como.png), [lixeira de perfis](prints/app/lote4/depois-lixeira-de-perfis.png), [perfis controle](prints/app/lote4/depois-perfis-controle.png), [perfis retrato](prints/app/lote4/depois-perfis-retrato.png), [perfis](prints/app/lote4/depois-perfis.png), [quem joga](prints/app/lote4/depois-quem-joga.png), [saves controle](prints/app/lote4/depois-saves-controle.png), [saves do perfil](prints/app/lote4/depois-saves-do-perfil.png), [saves retrato](prints/app/lote4/depois-saves-retrato.png), [saves sincronização](prints/app/lote4/depois-saves-sincronizacao.png), [saves](prints/app/lote4/depois-saves.png) |
| Lote 5: Conteúdo, Diagnóstico e Comparar | [comparar execuções](prints/app/lote5/antes-comparar-execucoes.png), [conteúdo](prints/app/lote5/antes-conteudo.png), [diagnóstico](prints/app/lote5/antes-diagnostico.png), [instalar conteúdo](prints/app/lote5/antes-instalar-conteudo.png) | [como medir](prints/app/lote5/depois-como-medir.png), [comparar execuções controle](prints/app/lote5/depois-comparar-execucoes-controle.png), [comparar execuções retrato](prints/app/lote5/depois-comparar-execucoes-retrato.png), [comparar execuções](prints/app/lote5/depois-comparar-execucoes.png), [comparar sem marcar](prints/app/lote5/depois-comparar-sem-marcar.png), [conteúdo controle](prints/app/lote5/depois-conteudo-controle.png), [conteúdo do jogo atualizacoes](prints/app/lote5/depois-conteudo-do-jogo-atualizacoes.png), [conteúdo do jogo controle](prints/app/lote5/depois-conteudo-do-jogo-controle.png), [conteúdo do jogo](prints/app/lote5/depois-conteudo-do-jogo.png), [conteúdo lixeira](prints/app/lote5/depois-conteudo-lixeira.png), [conteúdo retrato](prints/app/lote5/depois-conteudo-retrato.png), [conteúdo](prints/app/lote5/depois-conteudo.png), [diagnóstico controle](prints/app/lote5/depois-diagnostico-controle.png), [diagnóstico do jogo](prints/app/lote5/depois-diagnostico-do-jogo.png), [diagnóstico resumo](prints/app/lote5/depois-diagnostico-resumo.png), [diagnóstico retrato](prints/app/lote5/depois-diagnostico-retrato.png), [diagnóstico](prints/app/lote5/depois-diagnostico.png), [instalar conteúdo retrato](prints/app/lote5/depois-instalar-conteudo-retrato.png), [instalar conteúdo](prints/app/lote5/depois-instalar-conteudo.png), [remover conteúdo](prints/app/lote5/depois-remover-conteudo.png) |
| Lote 6: primeira abertura e telas do app | [atualizador](prints/app/lote6/antes-atualizador.png), [jogos que saíram](prints/app/lote6/antes-jogos-que-sairam.png), [navegador de pastas](prints/app/lote6/antes-navegador-de-pastas.png), [pastas](prints/app/lote6/antes-pastas.png), [primeira abertura](prints/app/lote6/antes-primeira-abertura.png), [sem vulkan](prints/app/lote6/antes-sem-vulkan.png), [sobre](prints/app/lote6/antes-sobre.png) | [atualizador baixando](prints/app/lote6/depois-atualizador-baixando.png), [atualizador controle](prints/app/lote6/depois-atualizador-controle.png), [atualizador retrato](prints/app/lote6/depois-atualizador-retrato.png), [atualizador](prints/app/lote6/depois-atualizador.png), [jogos que saíram](prints/app/lote6/depois-jogos-que-sairam.png), [navegador arquivo retrato](prints/app/lote6/depois-navegador-arquivo-retrato.png), [navegador de pastas controle](prints/app/lote6/depois-navegador-de-pastas-controle.png), [navegador de pastas](prints/app/lote6/depois-navegador-de-pastas.png), [pastas retrato](prints/app/lote6/depois-pastas-retrato.png), [pastas](prints/app/lote6/depois-pastas.png), [primeira abertura como usar](prints/app/lote6/depois-primeira-abertura-como-usar.png), [primeira abertura idioma](prints/app/lote6/depois-primeira-abertura-idioma.png), [primeira abertura jogos](prints/app/lote6/depois-primeira-abertura-jogos.png), [primeira abertura perfil](prints/app/lote6/depois-primeira-abertura-perfil.png), [primeira abertura retrato](prints/app/lote6/depois-primeira-abertura-retrato.png), [primeira abertura](prints/app/lote6/depois-primeira-abertura.png), [sem vulkan retrato](prints/app/lote6/depois-sem-vulkan-retrato.png), [sem vulkan](prints/app/lote6/depois-sem-vulkan.png), [sobre controle](prints/app/lote6/depois-sobre-controle.png), [sobre retrato](prints/app/lote6/depois-sobre-retrato.png), [sobre](prints/app/lote6/depois-sobre.png) |
| Lote 7: painéis do jogo | [mensagem](prints/app/lote7/antes-mensagem.png), [troca de disco](prints/app/lote7/antes-troca-de-disco.png) | [mensagem controle](prints/app/lote7/depois-mensagem-controle.png), [mensagem longa retrato](prints/app/lote7/depois-mensagem-longa-retrato.png), [mensagem](prints/app/lote7/depois-mensagem.png), [teclado controle](prints/app/lote7/depois-teclado-controle.png), [teclado símbolos retrato](prints/app/lote7/depois-teclado-simbolos-retrato.png), [teclado](prints/app/lote7/depois-teclado.png), [troca de disco controle](prints/app/lote7/depois-troca-de-disco-controle.png), [troca de disco sem disco retrato](prints/app/lote7/depois-troca-de-disco-sem-disco-retrato.png), [troca de disco](prints/app/lote7/depois-troca-de-disco.png) |
| Ajustes 1: retorno do aparelho | — | [drivers para baixar retrato](prints/app/ajustes-1/depois-drivers-para-baixar-retrato.png), [drivers para baixar](prints/app/ajustes-1/depois-drivers-para-baixar.png), [hud](prints/app/ajustes-1/depois-hud.png), [menu imagem controle](prints/app/ajustes-1/depois-menu-imagem-controle.png), [menu imagem retrato](prints/app/ajustes-1/depois-menu-imagem-retrato.png), [menu imagem](prints/app/ajustes-1/depois-menu-imagem.png) |
