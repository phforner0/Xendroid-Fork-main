---
title: Interface do app
description: Modo toque e modo controle, biblioteca, ficha do jogo, menu em jogo, HUD, perfis e saves.
navTitle: Interface
section: usar
order: 1
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/ui/library/LibraryTouch.kt, app/src/main/java/xendroid/compose/ui/game/GameScreen.kt, app/src/main/java/xendroid/compose/ui/game/LaunchSheet.kt, app/src/main/java/xendroid/compose/ui/ingame/InGameMenu.kt, app/src/main/java/xendroid/compose/ui/ingame/InGameMenuState.kt, app/src/main/java/xendroid/compose/settings/InGameChanges.kt, app/src/main/java/xendroid/compose/FpsOverlay.kt, app/src/main/java/xendroid/compose/core/HudPlacement.kt, app/src/main/java/xendroid/compose/ui/profile/ProfilesScreen.kt, app/src/main/java/xendroid/compose/ui/saves/SaveManagerScreen.kt]
---

A interface tem dois modos com as mesmas áreas e telas. As imagens desta página vêm dos testes de tela do app e usam jogos, capas e números de exemplo.

## Modo toque e modo controle

- **Toque**: trilho com as áreas do app à esquerda (em pé, uma barra embaixo), telas com seções à esquerda e conteúdo à direita.
- **Controle**: menus verticais grandes, abas e seções trocadas por LB e RB, ajustes em linhas ◀ valor ▶, dicas de botão sempre visíveis, a cor tirada da capa do jogo e um menu global no botão Start (≡).

Em **Configurações → App → Interface → Modo da interface**: **Automático** (padrão: usa o modo controle enquanto um controle está conectado), **Toque** ou **Controle**. **Botões nos menus** troca A e B nos menus do app, sem mudar os botões dos jogos.

## Biblioteca

{{> print id=biblioteca legenda="Biblioteca no modo toque, deitada: trilho de áreas, busca, filtros, grade de capas e o painel do jogo escolhido."}}

- **Busca** por nome ou Title ID.
- **Ordem**: Nome A–Z (padrão), Nome Z–A, Formato ou Jogados recentemente.
- **Tamanho das capas**: P, M ou G.
- **Filtros** sempre à vista: Todos, Favoritos, cada coleção e, quando há mais de um, cada formato (ISO, ZAR, GOD, XBLA, Pasta XEX).
- **Coleções**: até 50, cada uma com até 5.000 jogos; um jogo continua na coleção mesmo se o arquivo mudar de lugar.

Deitado, o primeiro toque num jogo mostra o painel ao lado (Jogar, Ficha, favorito, tempo jogado, última sessão, patches, com qual perfil entra e os ajustes rápidos); um toque longo abre a ficha. Com controle, Y favorita e X abre a ficha.

O menu ⋮ da biblioteca reúne **Adicionar pasta de jogos**, **Pastas de jogos**, **Jogos que saíram da biblioteca**, **Procurar de novo nas pastas**, **Assistente de configuração**, **Abrir dados do usuário** e **Procurar atualizações**.

## Ficha do jogo

{{> print id=ficha legenda="Ficha do jogo: seções à esquerda e, no topo, Iniciar com… e Jogar."}}

As seções da ficha, no modo toque:

- **Visão geral**: última sessão, ajustes rápidos, compatibilidade, patches e conteúdo.
- **Todos os ajustes** e um item por grupo (Imagem, Desempenho, Áudio, Controles, Compatibilidade, Driver e Vulkan, Console e sistema, Depuração): os ajustes **deste jogo**, com a troca para ver os globais. Veja [Ajustes e efeitos](doc:ajustes).
- **Desempenho**: a sessão escolhida, com gráficos, a linha do tempo e os ajustes em vigor; um toque lista todas as sessões do jogo.
- **Patches e conteúdo**: patches em grupos (para a sua versão do jogo, para outras versões, sem versão indicada), title updates e DLC.
- **Saves e dados**: saves, cache de shaders, capa, comprimir para .zar (só ISO), atalho na tela inicial e as sessões no diagnóstico.

O menu ⋮ da ficha tem **Trocar capa**, **Usar o ícone do próprio jogo**, **Coleções**, **Criar atalho**, **Comprimir para .zar** e **Avaliar compatibilidade**. A avaliação fica só no seu aparelho, guardada com a versão do app, a GPU e o driver daquele momento.

### Iniciar com… {#iniciar-com}

Abre o jogo uma vez com opções que **não são salvas**:

- **Perfil** que entra no jogo (com mais de um perfil);
- **Driver**: como está configurado, o do sistema ou um pacote instalado;
- **Executável**: outro `.xex` do disco ou do pacote (`launch_module`);
- **Sem patches**: abre sem os patches ativos;
- **Ignorar os ajustes deste jogo**: usa só os globais, para descobrir se um ajuste causa o problema;
- **Linha de comando extra**, repassada ao jogo.

Essas opções só valem quando o jogo é aberto pela biblioteca; frontends e atalhos não conseguem passá-las.

## Menu em jogo {#menu-em-jogo}

{{> print id=menu-imagem legenda="Menu em jogo na categoria Imagem, com as categorias à esquerda e o valor de cada linha."}}

O menu abre deslizando a partir da borda esquerda da tela, com o **Voltar** do Android ou com o botão **Guia** do controle (que também fecha). Na primeira sessão, um aviso explica isso. Por padrão o jogo fica pausado enquanto o menu está aberto (**Sessão → Pausar o jogo ao abrir o menu**, que vale para todos os jogos).

Com controle: ↑ e ↓ andam entre as linhas, ◀ e ▶ mudam o valor, A aciona, LB e RB trocam de categoria e B fecha.

| Categoria | O que tem |
|---|---|
| Imagem | modo de tela (Ajustar, Preencher, Esticar, Inteira), efeito de escala, antisserrilhamento, nitidez, pontilhado, filtro de cor, TV ou tela externa, [geração de quadros](doc:ajustes#geracao-de-quadros) e o driver em uso |
| Desempenho | limite de FPS, taxa de atualização da tela, opções da GPU ao vivo e, no modo com mais ajustes, energia |
| HUD | mostrar o HUD, formato, detalhe, métricas e aparência |
| Controles | controles na tela, visual Moderno ou Clássico, analógicos adaptativos, câmera por toque, editar o layout, tela dividida, vibração, telefones como controle e giroscópio |
| Sessão | volume, ajustes guardados para o jogo, pausar ao abrir, capturar tela, marcar cena, compartilhar logs, continuar e sair do jogo |

Escala, antisserrilhamento, nitidez e pontilhado mudam na hora; os outros ajustes de imagem valem na próxima abertura. Opções avançadas ficam atrás de **Mais opções** em cada categoria.

::: versao desde=2fb01a6d4
**Desempenho → Desempenho da GPU** muda quatro opções com o jogo rodando: Shaders sem travadas, MSAA 4× como 2×, Transparência recortada e Taxa de sombreamento. **Sessão → Capturar tela** salva a imagem do jogo, sem menu nem HUD, em PNG na pasta `Imagens/Xendroid+`.
:::

### O que fica guardado para o jogo

Com **Guardar as mudanças para este jogo** ligado (padrão), o que você muda no menu vai para a configuração daquele jogo: limite de FPS, efeito de escala, antisserrilhamento, nitidez, pontilhado, controles na tela e volume, além do modo de tela, do filtro de cor e da taxa de atualização pedida. As linhas com valor próprio dizem “deste jogo”. Em **Sessão** ficam **Desfazer as mudanças desta sessão** e **Usar estas mudanças em todos os jogos**, que as torna globais.

A geração de quadros não é guardada: começa desligada a cada abertura. Ligar o HUD e escolher as métricas vale para todos os jogos.

::: aviso Opções da GPU ao vivo
Pela leitura do código, “MSAA 4× como 2×”, “Transparência recortada” e “Taxa de sombreamento” não estão no esquema de ajustes do app, então guardar essas três para o jogo falha (o app avisa que a mudança vale só na sessão). A “Taxa de sombreamento” também depende de uma opção do núcleo que vem desligada. Isso não foi conferido num aparelho.
:::

## HUD de desempenho {#hud}

{{> print id=hud legenda="HUD em barra no topo da tela."}}

O HUD liga em **menu em jogo → HUD → Mostrar o HUD** (desligado por padrão) e vale para todos os jogos.

- **Formato**: Vertical (uma caixa que se arrasta e muda de tamanho com pinça) ou Horizontal (uma barra no topo ou na base).
- **Detalhe**: Só FPS, Métricas (padrão) ou Painel.
- **Métricas**: CPU, GPU, RAM, memória da GPU, temperatura da bateria e do SoC, potência, carga, autonomia e o gráfico de FPS. O que o aparelho não informa fica de fora.
- **Aparência**: estilo (Caixa, Contorno, Texto), tamanho de 50 a 250 %, fundo e intensidade das cores.

As cores avisam: a bateria fica amarela a partir de 40 °C e vermelha a partir de 45 °C; o SoC, a partir de 80 e 95 °C. O **Painel** soma o ritmo dos últimos 10 segundos e da sessão, o trabalho (pipelines criados, áudio atrasado, calor) e os ajustes em vigor.

::: versao desde=59037dc11
Com a geração de quadros ligada, o FPS aparece como “jogo → tela” (por exemplo, 29 → 57), e a energia virou três métricas separadas: Potência, Carga e Autonomia.
:::

## Perfis e saves {#perfis-e-saves}

{{> print id=perfis legenda="Perfis: avatar, gamertag, idioma, região e o perfil ativo como P1."}}

- **Perfis**: gamertag de 1 a 15 caracteres, começando com letra; avatar, idioma e região por perfil.
- **Quem joga**: o perfil ativo é o P1; os outros jogadores entram com o perfil escolhido para P2 a P4. Com mais de um perfil, o app pergunta quem joga antes de cada jogo (dá para desligar).
- **Lixeira**: apagar um perfil manda ele e os saves para a lixeira, de onde dá para restaurar ou remover de vez.

Os **saves** de cada jogo ficam na ficha → **Saves e dados**: por perfil, com tamanho e data. Dá para exportar os escolhidos num ZIP (com ou sem o perfil), importar com uma revisão antes de restaurar e escolher uma **pasta de sincronização**, local ou de nuvem pelo seletor do Android, onde o app grava backups conferidos por SHA-256 quando você toca em **Sincronizar agora** ou, se ligar a opção, ao voltar para a biblioteca. Feche o jogo antes de fazer backup ou restaurar.

## Ver no simulador

O [simulador](ferramenta:library) mostra estas telas no navegador, nos dois modos. Comece pela [biblioteca](ferramenta:library), pela [ficha](ferramenta:game) ou pelo [menu em jogo](ferramenta:ingame).
