# Redesign da UI: protótipos da biblioteca e da ficha do jogo

**Estado:** protótipo em HTML, nada mudou no app. Branch `wip/ui-redesign`, criada a partir de
`wip/experiencia-em-jogo` (`7bb34099d`). Sem PR, merge ou release.

**Pedido:** redesign começando pela biblioteca e pela ficha do jogo; tema escuro, capas grandes,
visual moderno e funcional; expor ajustes que o core já tem e a UI não mostra; muito controle do
usuário sobre as opções do emulador. Primeiro 2–3 direções em protótipo; depois, implementação tela
por tela com prints antes/depois a cada lote.

## Como abrir

- `prototipo.html`: um arquivo só, sem build e sem dependências (HTML, CSS e JS nativos). Abra no
  Chrome ou Edge. Só as fontes vêm da internet (Google Fonts); sem rede, cai nas fontes do sistema.
- Barra do topo: direção **A/B/C**, **Biblioteca/Ficha do jogo**, **Paisagem/Retrato/Tela cheia** e
  **Anotações** (numera na tela o que já existe no app, o que é proposta nova e o que é ajuste do
  core exposto agora; a lista aparece abaixo do aparelho).
- Teclado: setas, Enter, Esc, Q/E (LB/RB), F (favoritar, Y), I (ficha, X), / (buscar).
- Controle: pela Gamepad API (A, B, X, Y, LB/RB, Start, View), abrindo o arquivo direto no navegador.
- No celular, **Tela cheia** usa a tela inteira como a do aparelho; em paisagem dá a sensação real.
- Prints de cada direção em `prints/prototipo/` (paisagem e retrato, folhas e anotações).
- Capas, números de sessão e notas de compatibilidade são **dados de exemplo**. Títulos e Title IDs
  vêm dos arquivos de `patches/` e do `GAME_COMPAT.md`; os ajustes por jogo do Ninja Gaiden II são
  os do `GAME_COMPAT.md`.

## Referências

Esta sessão não fez busca na web. As referências vêm da pesquisa já registrada no repositório
([plano das cinco referências](../plano-evolucao-cinco-referencias.md) e
[varredura de 2026-10-03](../varredura-referencias-2026-10-03.md)) e do que esses apps mostram
publicamente. O que cada uma trouxe para a interface:

| Referência | O que entrou |
|---|---|
| **GameHub** (GameSir) | Biblioteca organizada com destaque “continuar”, capas grandes, coleções |
| **DroidDeck** | Biblioteca no estilo Steam Deck: trilho, filtros, grade densa; foco por controle |
| **Bannerlator** | Opções com ajuda em cada item, motivo de indisponibilidade, controle fino de tudo |
| **X360 Mobile** | Biblioteca para controle (Blades/Metro), recentes e “continuar”, capas por Title ID, centro de compatibilidade |
| **Eden** | Ajustes rápidos com indicador global/por jogo (`QuickSettings.kt`) |

## As três direções

| | A · Vitrine | B · Estante | C · Console |
|---|---|---|---|
| Ideia | Destaque do jogo em foco + prateleiras | Trilho, filtros e grade densa + painel lateral | Carrossel para controle, cor tirada da capa |
| Biblioteca | Início (destaque, recentes, favoritos, coleções), Biblioteca (grade com filtros), Coleções | Grade com capas P/M/G, filtros sempre à vista, Jogar e ajustes rápidos no painel (paisagem) | Abas por LB/RB (recentes, favoritos, todos, cada coleção), dicas de botão sempre visíveis |
| Ficha do jogo | Capa grande à esquerda, abas Visão geral / Ajustes / Desempenho / Conteúdo / Dados | Tela de ajustes: seções à esquerda com a contagem do que o jogo muda | Menu vertical grande; o conteúdo aparece ao lado ao mover o foco; ajustes em linhas ◀ valor ▶ |
| Ganha | Caminho mais curto até o jogo da vez | Mais jogos por tela, ajustes a um toque | Melhor com controle e na TV |
| Custa | Menos jogos por tela | Visual menos cinematográfico | Um jogo em foco por vez |
| Em Compose | LazyColumn + LazyRow, SharedTransitionLayout, fundo desfocado | NavigationRail/Bar, ListDetailPaneScaffold, LazyVerticalGrid adaptativa | LazyRow com snap, androidx.palette, focusRestorer, reaproveita o carrossel 15a |

Sugestão: **B como padrão** (toque, densidade, ajustes à mão) e **C como modo para controle**,
ligado sozinho quando um controle está conectado. O carrossel do lote 15a já é uma visão separada,
então isso é evolução do que existe, não uma segunda biblioteca do zero.

## O que é comum às três

- **Ficha do jogo** no lugar do bottom sheet atual (até 17 itens em lista em
  `GameLibraryScreen.kt`), com Jogar sempre à vista e o perfil que entra escrito ao lado.
- **Ajustes por jogo dentro da ficha**, com:
  - escopo em cada linha (**Este jogo** ou **Global**) e edição de qualquer um dos dois na mesma tela;
  - **Ao vivo** ou **Próxima abertura** em cada linha (o `SettingContract` já tem isso);
  - busca por nome, descrição **e chave do TOML**; filtros “mudados neste jogo”, “novos”, “ao vivo”, “fixados”;
  - níveis **Essencial / Avançado / Tudo** (hoje: Jogador com 23 ajustes, Desenvolvedor com 146);
  - **alfinete** para escolher o que aparece nos ajustes rápidos;
  - **predefinições** com desfazer (Desempenho, Equilíbrio, Qualidade; perfis recomendados C05 e da comunidade 15b no mesmo lugar);
  - o **TOML** que será gravado (`config/<TITLEID>.config.toml`, só o que o jogo muda);
  - o **motivo** quando um ajuste não vale (depende de outro ajuste ou do driver).
- **Iniciar com…**: perfil, driver, executável (`launch_module`), sem patches, sem os ajustes do
  jogo e linha de comando extra, **só para uma abertura**.
- **Desempenho** com os histogramas que cada sessão já grava (`RunPerformance`: FPS por segundo,
  tempo de quadro), pipelines, áudio, temperatura e a linha do tempo.
- **Capas grandes**: com capa própria, a arte ocupa a tela; só com o ícone 64×64 do jogo, a capa é
  composta (fundo desfocado, ícone nítido e nome).
- **Tema escuro** com tokens únicos (fundo, superfícies, texto, destaque, estados de compatibilidade)
  que viram o `ColorScheme` do Compose.

## Ajustes do core que passam a aparecer

Dos cerca de 250 cvars definidos no core e ausentes do `SettingsSchema`, ficaram de fora o que é de
outra plataforma (D3D12, Win32, macOS/MoltenVK), de depuração interna, de rastreio e de testes. Os
textos traduzem a descrição do próprio core. São **29 cvars novas** e **2 valores novos** em listas
que já existem:

| Grupo | Ajuste | Chave | Nível | Aplica | Tipo |
|---|---|---|---|---|---|
| Imagem | Modo de vídeo informado ao jogo | `Console.internal_display_resolution = 17` | Essencial | Próxima abertura | valor novo |
| Imagem | Largura personalizada | `Video.internal_display_resolution_x` | Avançado | Próxima abertura | cvar nova |
| Imagem | Altura personalizada | `Video.internal_display_resolution_y` | Avançado | Próxima abertura | cvar nova |
| Imagem | Não ampliar superfícies estreitas | `GPU.draw_resolution_scale_threshold` | Avançado | Próxima abertura | cvar nova |
| Imagem | FSR: passadas de ampliação | `Display.postprocess_ffx_fsr_max_upsampling_passes` | Avançado | Próxima abertura | cvar nova |
| Imagem | Curva de gama da TV | `Kernel.kernel_display_gamma_type = 3` | Avançado | Próxima abertura | valor novo |
| Imagem | Gama personalizada | `Kernel.kernel_display_gamma_power` | Avançado | Próxima abertura | cvar nova |
| Desempenho | Interpretar vertex shaders enquanto compilam | `GPU.async_shader_vs_interpreter` | Avançado | Próxima abertura | cvar nova |
| Desempenho | Pular desenhos até o shader ficar pronto | `GPU.async_shader_skip_draws` | Avançado | Próxima abertura | cvar nova |
| Desempenho | Pré-criar pipelines ao abrir o jogo | `GPU.pipeline_storage_precreate` | Avançado | Próxima abertura | cvar nova |
| Desempenho | Esperas precisas do jogo | `Kernel.precise_guest_delays` | Avançado | Próxima abertura | cvar nova |
| Desempenho | Dica de desempenho para o áudio (ADPF) | `APU.apu_performance_hint` | Avançado | Próxima abertura | cvar nova |
| Desempenho | Repetir o pedido de clocks máximos a cada | `Vulkan.adrenotools_turbo_reassert_seconds` | Avançado | Próxima abertura | cvar nova |
| Áudio | Volume | `APU.volume` | Essencial | Ao vivo | cvar nova |
| Controles | Vibração do controle | `HID.vibration` | Essencial | Próxima abertura | cvar nova |
| Controles | Enviar o botão Guia ao jogo | `HID.guide_button` | Avançado | Próxima abertura | cvar nova |
| Compatibilidade | Sincronizar a leitura de resolves | `GPU.readback_resolve_sync` | Avançado | Próxima abertura | cvar nova |
| Compatibilidade | Interpolação precisa | `GPU.precise_interpolation` | Avançado | Próxima abertura | cvar nova |
| Compatibilidade | Profundidade de decalques no shader | `GPU.depth_bias_shader_offset` | Avançado | Próxima abertura | cvar nova |
| Compatibilidade | Memexport visível para a CPU | `GPU.memexport_enable` | Tudo | Próxima abertura | cvar nova |
| Compatibilidade | Profundidade invertida direto no driver | `Vulkan.vulkan_allow_reverse_z` | Tudo | Próxima abertura | cvar nova |
| Compatibilidade | Multiplicador da pilha (hack) | `Kernel.stack_size_multiplier_hack` | Tudo | Próxima abertura | cvar nova |
| Driver e Vulkan | Dynamic rendering | `Vulkan.vulkan_dynamic_rendering` | Tudo | Próxima abertura | cvar nova |
| Driver e Vulkan | Evitar geometry shaders | `Vulkan.vulkan_avoid_geometry_shaders` | Tudo | Próxima abertura | cvar nova |
| Driver e Vulkan | Profundidade D24 nativa | `Vulkan.vulkan_depth_unorm24` | Tudo | Próxima abertura | cvar nova |
| Console e sistema | Conquista na posição que o jogo pede | `UI.achievement_notification_position_by_game` | Avançado | Próxima abertura | cvar nova |
| Console e sistema | Executável a iniciar | `General.launch_module` | Avançado | Próxima abertura | cvar nova |
| Console e sistema | Montar unidade de memória (MU) | `Storage.mount_memory_unit` | Avançado | Próxima abertura | cvar nova |
| Console e sistema | Tipo de console | `Kernel.console_type` | Tudo | Próxima abertura | cvar nova |
| Console e sistema | Linha de comando extra para o jogo | `Kernel.cl` | Tudo | Próxima abertura | cvar nova |
| Console e sistema | Encerrar quando uma thread do jogo falhar | `General.guest_crash_is_fatal` | Tudo | Próxima abertura | cvar nova |

Observações que vão para a implementação:
- `GPU.memexport_enable` não muda nada no Turnip (falta `VK_EXT_external_memory_host`), e
  `GPU.precise_interpolation` precisa de baricêntricas no driver; a linha diz isso em vez de fingir efeito.
- `Vulkan.adrenotools_turbo_reassert_seconds` só vale com “Forçar clocks máximos da GPU” ligado.
- `APU.volume` é o valor de partida; o menu do jogo continua mudando ao vivo.
- Cada cvar nova precisa de texto en/pt-BR, teste do esquema e do contrato, e validação no aparelho.

## Decisões que dependem de você

1. **Direção:** A, B, C ou B + C (sugerida).
2. **Níveis:** trocar Jogador/Desenvolvedor por Essencial/Avançado/Tudo?
3. **Fontes:** empacotar as do protótipo (Saira Semi Condensed nos títulos, Hanken Grotesk no
   texto, JetBrains Mono nos IDs; algumas centenas de KB no APK) ou ficar na fonte do sistema?
4. **Itens do menu do jogo por jogo:** modo de exibição, filtro de cor, HUD e geração de quadros
   aparecem na ficha como padrão do jogo. Antes de implementar, conferir o que já é salvo por jogo
   hoje e o que é só da sessão.
5. **Iniciar com…:** exige mandar ajustes de uma abertura só para o processo `:emu` (hoje pelo
   `EmuProcessLink`), sem gravar no TOML. Seguir com isso?
6. **Prints antes/depois:** testes de captura do Compose (Roborazzi, sem aparelho; exige instalar o
   Android SDK na sessão, como no `BUILD.md`) ou prints tirados por você no aparelho?

## Plano de implementação (depois da escolha)

Cada lote com prints antes/depois, testes locais e a validação no aparelho marcada como pendente,
como no resto do projeto.

| Lote | Tela | Onde mexe hoje |
|---|---|---|
| R0 | Tema e peças base: `ColorScheme` escuro, tipografia, formas; capa com fallback composto, selo de compatibilidade, linha de ajuste com escopo e aplicação, controle segmentado | `ui/theme/Theme.kt`, `ui/settings/SettingRows.kt`, `data/CoverStore.kt` |
| R1 | Biblioteca na direção escolhida (grade/prateleiras/carrossel, filtros, busca, painel) | `ui/library/GameLibraryScreen.kt`, `GameCarousel.kt`, `GameLibraryViewModel.kt` |
| R2 | Ficha do jogo como tela: visão geral, desempenho com histogramas, conteúdo, dados | `GameLibraryScreen.kt` (bottom sheet), `RunReportDialog.kt`, `RunTimelineDialog.kt`, `sessions/RunPerformance.kt` |
| R3 | Ajustes na ficha: escopo, níveis, filtros, busca por chave, alfinete, TOML; cvars novas | `settings/SettingsSchema.kt`, `SettingContract.kt`, `SettingDescriptions.kt`, `ui/settings/PerGameSettingsScreen.kt`, `SettingTexts.kt`, strings en/pt-BR |
| R4 | Iniciar com… e predefinições com desfazer | `core/EmuProcessLink.kt`, `RecommendedProfilesSection.kt` |
