# Varredura de commits e changelogs das referências (2026-10-03)

**Pedido:** analisar commits e changelogs do Bannerlator, DroidDeck, "X360E" e Eden Nightly,
achar todas as features que podem entrar no XenDroid e comparar com o que já foi feito.
**Entrega:** pesquisa e comparação; nada foi implementado nesta fatia. Complementa o
[plano das cinco referências](plano-evolucao-cinco-referencias.md), que leu arquivos
escolhidos até 2026-09-30; aqui foi lido o histórico inteiro de cada projeto.

Regras que valem para tudo abaixo: frame generation continua experimental, desligada por
padrão e atrás dos gates atuais; número de outro projeto não prova ganho aqui; o que
depende de aparelho fica como validação no aparelho pendente.

## 1. Fontes e recortes

| Projeto | Repositório | O que foi lido | Recorte |
|---|---|---|---|
| **Bannerlator** | `github.com/The412Banner/Bannerlator` | 3.396 commits sem merge da `main` (HEAD `f79ce097`), tags até 3.1.4, README (lista de features) | 2026-06-18 (início do fork) a 2026-10-02 |
| **DroidDeck** | `github.com/Droid-Deck/DroidDeck` | 610 commits sem merge (HEAD `255c6455`), tags 0.2.0 e 0.3.0, README, `docs/progress-log.md` | 2026-09-19 (início) a 2026-10-02 |
| **"X360E" = aX360e** | `github.com/aenu1/ax360e` | 40 commits, da 0.14 à 1.19 (HEAD `b8c1838f`). As mensagens são só números de versão, então os diffs de app e de core foram lidos um a um | 2026-03-11 a 2026-08-16 |
| **X360 Mobile** | `github.com/Ashnar2602/X360-Mobile---OFFICIAL` | notas de todas as releases, v0.5 a v0.6.3 (código fechado) | todas |
| **Eden (Nightly)** | `git.eden-emu.dev/eden-emu/eden` | 1.949 commits sem merge (HEAD `d16735f5`), 369 deles depois da v0.2.0 | 2025-03-01 a 2026-10-02 |

- **"X360E":** lido como o **aX360e**, o port do Xenia para Android do autor do aPS3e. Nas
  conversas e no plano anterior, o emulador de Xbox de referência era o **X360 Mobile**,
  então ele entra também, separado.
- **Eden Nightly:** o repositório não oficial `pflyly/eden-nightly` não existe mais no
  GitHub. O changelog de cada nightly era a lista de commits do Eden entre dois builds,
  então a fonte é o próprio histórico do Eden.
- **Método:** filtrar as mensagens de commit por área e tirar o que é de outra
  arquitetura (lojas, Wine/Proton/DXVK/FEX/Box64, Wayland/X server, runtime Linux e o
  que é só do Switch). No aX360e, cada diff de core foi comparado com o Xenia do XenDroid.

**Como ler as tabelas:**
- **XenDroid:**
  - **Tem**: implementado e testado localmente, com validação no aparelho pendente como diz o [status](experiencia-em-jogo-status.md).
  - **Parcial**: existe uma parte.
  - **Não tem**.
- **Cabe?:** viabilidade (Alta/Média/Baixa) e esforço (P, M, G), com o que a feature exige.
- **Fonte:** projeto e commit (SHA curto e data) ou release.

## 2. Achados de core: aX360e × Xenia do XenDroid

O aX360e usa o mesmo core (Xenia, backend arm64), então os diffs dele foram conferidos no
código do XenDroid. Quase tudo já existe aqui, e às vezes de forma mais completa.

| Mudança | Fonte | XenDroid |
|---|---|---|
| Thread criada suspensa arma `suspend_count_` no mesmo lock que publica o estado (senão um Resume no meio a deixa presa) | aX360e `4c257e4` (2026-07-17) | **Tem**: `threading_posix.cc`, `ThreadStartRoutine`, com comentário explicando a corrida |
| Semáforo de apresentação por imagem da swapchain, não por submissão | aX360e `6fc56da` (2026-07-15) | **Tem**: `swapchain_image_present_semaphores` |
| `XThread` vivo até a thread nativa terminar ("pthread_mutex_lock called on a destroyed mutex") | aX360e `c9669c8`, `c86c98b` | **Tem**, por outro caminho: no POSIX o handle não é solto antes de `Thread::Exit` |
| `FiberReentryException` escapando vira `terminate`; fallback com setjmp/longjmp | aX360e `c9669c8` | **Tem** algo mais completo: cvar `fiber_reentry_longjmp`, ligado por padrão |
| Arquivo do guest aberto para leitura e escrita virava só escrita ("Dirty Disc") | X360 Mobile v0.6.1 | **Tem**: `FileHandle::OpenExisting` trata O_RDONLY/O_WRONLY/O_RDWR como modo de 2 bits |
| Bibliotecas nativas alinhadas a 16 KB | DroidDeck `06e67cd` | **Tem**: todas as `.so` do APK com `LOAD` alinhado a `0x4000` |
| ADPF reportando o tempo de trabalho, não o intervalo entre quadros | DroidDeck `e217cbe` (tirou a sessão que fazia errado) | **Tem** certo: `PresenterPerformanceHints` reporta o trabalho medido do apresentador |
| **APC (`kThreadUserCallback`) entregue à thread que recebe o sinal, sem slot global** | aX360e `a95d465` (2026-07-14) | **Não tem.** O ramo `XE_PLATFORM_xendroid` ainda passa o alvo por `g_thr_user_callback`. Hoje o único uso é acordar uma espera alertável com callback vazio (`xboxkrnl_threading.cc:1497`). Se dois alertas a threads diferentes se cruzam, um handler pode ler ponteiro nulo e cair. A correção é pequena: usar `current_thread_`, como o ramo macOS já faz. **Alta / P**, com teste em `threading_test.cc` e no aparelho |

O resto do aX360e já existe no XenDroid: overlay de FPS (imgui), teclado do guest, editor
de controles, DocumentsProvider e ajustes por jogo. A exceção são os **25 idiomas** contra
2 aqui, e os **204 cvars** expostos contra 135 chaves aqui (seções 3.4, 3.7 e 3.15).

## 3. Matriz por área

### 3.1 Menu em jogo e ajustes rápidos

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Menu por abas com controles ao vivo | Bannerlator (drawer), DroidDeck `500f4c6`, Eden `29fad5a8` | **Tem** (U01, lotes 10a e 12b) | — |
| Ajuda "?" por opção e glossário | Bannerlator `1933903c` (89 variáveis), `33e7d975` | **Parcial**: descrições nas Configurações; no menu em jogo só notas por aba | Alta / P–M |
| Dizer por que uma opção está indisponível, em vez de falhar calada | Bannerlator `4cd5ea9c` | **Parcial**: driver (U03), estado do Win-FG, LSFG sem DLL | Alta / P |
| Painel de desempenho único (CPU, GPU, temperaturas, ajustes ativos) | Bannerlator `83d0c1b9` | **Parcial**: HUD, página Sessão e comparação de runs (C07) | Média / M |
| Escala/upscaler no menu, aplicado ao vivo | DroidDeck `d413018` | **Tem** (Bilinear/CAS/FSR) | — |

### 3.2 HUD de desempenho

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| FPS, CPU, GPU (KGSL/devfreq), RAM, temperaturas | Bannerlator `484532f5`, DroidDeck `ab8542e`, Eden `fe51be43` | **Tem** (`HudMetric`, estilos compacto/completo) | — |
| Potência em watts e estimativa de bateria/tempo de carga | Bannerlator `6a14c4a2` (detecta mA × µA), DroidDeck `2792071`, Eden `347d54bc` | **Não tem** | **Alta / P** |
| Memória de GPU via KGSL | DroidDeck `0cc3fe6` | **Não tem** | Média / P |
| FPS que cai a 0 quando não chega quadro novo (não congela no último valor) | Bannerlator `f520027c` | A conferir no HUD (o run já conta segundos sem quadro como ociosos) | Alta / P |
| Tamanhos, posição e contorno do HUD por jogo | Bannerlator (Fusion HUD pill/mega) | **Parcial**: dois estilos, posição fixa | Média / P–M |
| Cores de alerta por temperatura e °C/°F | Bannerlator `f6733d01` | **Não tem** | Alta / P (valor baixo) |
| Linha de identificação (build, driver, SoC) | Eden `64ff59e8`, `2665c5cc` | **Parcial**: driver no menu (12b), build no Sobre | Alta / P |

### 3.3 Geração de quadros (experimental, desligada por padrão)

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Win-FG/LSFG com multiplicador | Bannerlator, DroidDeck, Eden `2000fdfb` | **Tem**, experimental (F0x) | — |
| Leitura ao vivo "base → mostrado" e quadros perdidos | Bannerlator `4341db5a`, DroidDeck (linha de desempenho) | **Parcial**: tempo de GPU ao vivo; submetidos, sintéticos e perdidos só no registro do run (F08) | Alta / P |
| Aviso quando FPS × multiplicador passa do Hz da tela, e ajuste do Hz | Bannerlator `a6cdc8e0`, `2c57b3f8` | **Parcial**: o governador marca cadência × multiplicador acima do Hz, sem aviso no menu | Alta / P (aviso) |
| Governador probe/backoff com corte térmico | Bannerlator `1c18ff56`, Eden (`frame_gen_pacer`) | **Parcial** por desenho: F04 é consultivo e nunca age | Média / M, só com A/B no aparelho |
| Cadência pelos quadros do jogo; desarmar no resize da Surface | DroidDeck `05933a5`, `565987e` | **Parcial**: agenda por quadro guest (A02/F02); resize a conferir | Média / P |
| LSFG por FPS-alvo (60/90/120), além do multiplicador | Eden `df05d3de` | **Não tem** | Média / M |
| Resolução de captura menor para o LSFG | Bannerlator `28ae0197` | **Não tem** | Baixa / M |
| LSFG desabilitado até haver DLL | DroidDeck | **Tem** | — |

### 3.4 Escala, filtros e efeitos

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Bilinear, CAS, FSR1 | Bannerlator, Eden | **Tem** (cvar e menu em jogo) | — |
| Escala de resolução interna (supersampling) | Bannerlator `c3cbe491` | **Tem** (`draw_resolution_scale_x/y`) | — |
| Modos Ajustar/Preencher/Esticar/Inteiro | Bannerlator `886b7708` | **Tem** | — |
| **Filtragem anisotrópica por jogo** | Bannerlator `035d7165`; aX360e expõe `anisotropic_override` | **Não tem** (o cvar existe no Xenia, mas não está no esquema) | **Alta / P** |
| Margem de TV (overscan) | Bannerlator (barras de área segura) | **Não tem** | Média / P. Atenção: o `present_safe_area_x/y` do Xenia (exposto pelo aX360e) é outra coisa, quanto da imagem pode ser cortado para evitar faixas; esse entrou no lote 14b |
| Debanding | Bannerlator `e836265d` | **Tem** (revisto no lote 14j): o "debanding" do Bannerlator é um dither terminal antes dos 8 bits, o mesmo que o `postprocess_dither` do Xenia (ruído azul), que não fazia nada até o lote 14b e agora também está no modo Jogador ("Reduzir faixas de cor") | — |
| SGSR / SGSR HQ | Bannerlator `7e71cb8f` | **Impl. + Local no lote 14j** (SGSR 1, BSD-3-Clause com aviso no APK; testado em Vulkan por software; custo no aparelho a medir); SGSR HQ (direção de borda) não | Média / M |
| NIS, Lanczos, Spline, cúbico, MMPX, área | Bannerlator `6ff165f7`; Eden `f33a771d`, `b66adfe0`, `dbeae7ad`, `2946cdbd` | **Não tem** | Média / M (escolher um ou dois) |
| Efeitos (CRT, cor, fake-HDR), presets de um toque, shaders de pós-processamento | Bannerlator `a05106c5`; Eden `ed566919` | **Parcial**: filtro de cor (Desligado/Cinza/Contraste/Quente) | Média / M |
| ReShade (.fx) com catálogo | Bannerlator `edf46af9` | **Não tem** | Baixa / G |

### 3.5 Tela, Hz e TV

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Hz da tela acompanhando o FPS automaticamente, ou escolha entre modos suportados | Bannerlator `fa77da60`; Eden `e28b0d25`; DroidDeck `e217cbe` | **Parcial**: escolha manual e limite do refresh do guest (C07/K11); sem automático | Alta / P–M |
| Jogo na TV com o aparelho ao lado | Bannerlator `a1206106` | **Tem** (tela externa, 12m); pausar ao desconectar a conferir | — |
| Cast (Chromecast) | Bannerlator `1357d1c5` | **Não tem** | Baixa / G (só vídeo e latência alta no próprio Bannerlator) |
| Formato por aparelho (painel 4:3/3:2 exato, 16:9 fixo, esticar) | DroidDeck `9c47095`, `5179b54` | **Parcial**: modos de tela e letterbox | Média / P |
| Jogo numa metade da tela e controles na outra (dobráveis, telas altas) | Bannerlator `440d3dc4` | **Não tem** | Média / M |
| Escala da interface (UI e fonte) | Bannerlator `d4b33955` | **Não tem** (segue a fonte do sistema) | Média / P |
| As duas orientações paisagem | X360 Mobile v0.6.1 | **Tem** | — |

### 3.6 Limite de FPS, ritmo e velocidade

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Limite ao vivo com atalhos e salvo por jogo | Bannerlator `c0645622`, `f8d75986` | **Tem** (30/45/60/90/120/ilimitado, por jogo ou global) | — |
| VBlank por prazos absolutos, ritmo por FPS-alvo | X360 Mobile v0.6.2; Eden `5f676a6a`, `8f770618` | **Parcial**: K11 (prazos e resync) | — |
| Fallback de vsync Immediate → Mailbox → FIFO | Eden `abb616c3` | **Parcial**: modos permitidos configuráveis (Desenvolvedor) | Média / P |
| Turbo/Lento (velocidade da emulação) | Eden `2b979024` | **Não tem** | Baixa / M (jogos de 360 contam tempo real; risco para áudio e física) |

### 3.7 Controles

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| P1–P4 com hot-plug | Bannerlator (#333), DroidDeck | **Tem** (lote 6, U11) | — |
| **Esconder/mostrar os controles de toque ao vivo quando um controle físico é usado ou conectado** | Bannerlator `f76b16e1`; Eden `e4dccd5a`; DroidDeck `432e197` | **Parcial**: decide uma vez por instalação se começam visíveis e somem depois de 8 s sem toque | **Alta / P** |
| Atalho para ligar/desligar os controles de toque | DroidDeck `e4b0338` | **Tem** | — |
| Teste de controle com entrada ao vivo e rumble de teste | Bannerlator `5ddd0f6d`, `967a15dc`; X360 Mobile v0.6.0 | **Tem** (U05, U08); bateria por controle não | — |
| Zona morta dos sticks físicos ajustável | aX360e (`left/right_stick_deadzone_percentage`) | **Não tem** (fixa em 8%) | **Alta / P** |
| Giroscópio só enquanto um gatilho é segurado; modo orientação | Bannerlator `7cf04b57`, `c575091c` | **Parcial**: câmera por giroscópio com sensibilidade e calibração | Alta / P |
| Entrada sem buffer (`requestUnbufferedDispatch`) | Bannerlator `3b08d65e` | **Não tem** | Alta / P (medir latência no aparelho) |
| Remapear tocando no botão desenhado; modelo "qualquer controle"; copiar mapeamento | Bannerlator `4344ef49`, `4811d46c` | **Parcial**: tela de mapeamento global | Média / M |
| Editor de toque: grupos, zona morta por elemento, ajuste à grade | Bannerlator `9dd238bb`, `a20fca5a`; Eden `76be55bc` | **Parcial**: posição, escala 0,5–3×, visibilidade, layouts por jogo, edição pela biblioteca (U06) | Média / M |
| Deslizar o dedo de um botão para outro | Bannerlator `72db7f4d` | **Não tem** | Média / M |
| Sticks adaptativos e câmera por toque | DroidDeck `9530e7b`, `659ff48`; X360 Mobile v0.6.2 | **Tem** (sticks adaptativos, U07) | — |
| Soltar o que está pressionado quando o toque é cancelado ou um diálogo assume | DroidDeck `c66e4ba`; X360 Mobile v0.6.2 | A conferir | Alta / P |
| Troca A/B | Eden `978ba3ed` | **Tem** (U04) | — |

### 3.8 Desempenho e energia

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Modo sustentado e ADPF | Bannerlator `cb19ec8e`; DroidDeck `06e67cd`, depois removidos em `e217cbe` | **Tem** (opções; ADPF reporta trabalho real) | Manter o sustentado desligado por padrão: o DroidDeck tirou porque "limita os clocks na maioria dos HALs" |
| **Declarar o app como jogo** (`appCategory`/`isGame`, `game_mode_config` recusando limite de FPS e redução de resolução do sistema, `GameManager` em gameplay) | DroidDeck `06e67cd`; Bannerlator `35517de9` | **Não tem** | **Alta / P** (efeito depende do fabricante; medir) |
| Watchdog térmico pelos pontos de corte do aparelho | Bannerlator `445d6655` | **Parcial**: estado térmico no run; governador de FG consultivo | Média / M |
| Afinidade e núcleos grandes | Bannerlator `3c4b8983`; Eden `1925726b` | **Não exposto** | Baixa / M (o Eden fixou nos núcleos 0–3; não copiar sem medir) |
| GPU travada no clock máximo via KGSL | Bannerlator `0f693dd5` | **Não tem** | Baixa / M (térmico; só opt-in) |
| SDK de desempenho da Samsung | Bannerlator `f50065d0` | **Não tem** | Baixa / M (SDK fechado) |

### 3.9 Áudio

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Seguir troca de rota (fone, USB, BT, HDMI) | Bannerlator `88410253` | **Tem**: callback de erro do AAudio e thread que reconstrói o stream | — |
| Buffer adaptativo com histerese de underrun | X360 Mobile v0.6.1/v0.6.2; presets do Bannerlator | **Parcial**: buffer configurável e blocos ocultados contados no run | Média / M |
| Volume do jogo e mudo | Eden `93318ef6` | **Tem** | — |

### 3.10 Biblioteca e app

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Grade, ordenação, filtros, coleções, favoritos | todos | **Tem** (L06) | — |
| Recentes e tempo de jogo | Eden `6bdf4794` | **Tem** ("Jogados recentemente", tempo por título) | — |
| Capas por Title ID e capa própria | X360 Mobile v0.6.0 | **Tem** (L05, local); fonte remota não | — |
| Atalhos na tela inicial | X360 Mobile v0.6.3 | **Tem** | — |
| Carrossel, XMB, tela "Big Picture" para controle | Eden `61ffb309`; Bannerlator `16c292d2`, `223b1b8c` | **Impl. + Local no lote 15a** (carrossel salvo ao lado da grade, controle e toque; aparelho pendente, roteiro 52) | Média / G |
| Tela de carregamento com a arte do jogo | Bannerlator `e2acda52`, `da778368` | **Parcial**: rótulo de boot e Cancelar (U09) | Média / P |
| Tela de fim de sessão com "tentar de novo" e "compartilhar logs" | DroidDeck `02f356f` | **Parcial**: diálogo de falha; relatório do run na ficha | Média / P |
| Mais idiomas; idioma escolhido dentro do app | aX360e (25), X360 Mobile (5), DroidDeck `c711c62`; Eden `cfbef5c4` | **Parcial**: en e pt-BR; idioma por app do Android | Média / M por idioma |
| Temas e cor de destaque | Bannerlator `5d75439f` | **Parcial**: claro/escuro do sistema | Baixa / P |
| Informações do sistema | Eden `b9655669` | **Tem** (Diagnóstico) | — |
| App como tela inicial do aparelho | DroidDeck `12c1036` | **Não tem** | Baixa / M |
| Sons de interface | X360 Mobile v0.5.3 | **Não tem** | Baixa / P |

### 3.11 Saves e dados

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Backup e restauração de saves por jogo (ZIP), lixeira | DroidDeck `d1dec8d`; Bannerlator; X360 Mobile | **Tem** (backups, transações com recuperação, lixeira) | — |
| Backup automático ao sair do jogo | Bannerlator `00ec3575` | **Parcial**: publicação automática para uma pasta SAF (pode ser nuvem) ao abrir o app | Alta / P (publicar também ao sair, sob o lease) |
| Config por jogo compartilhável | Eden `e2a8f315` | **Tem** (pacote de dados, L08) | — |
| Pasta de dados escolhida pelo usuário, com migração | Eden `b0cd47c0` | **Não tem** | Baixa / G (o DocumentsProvider já dá acesso) |

### 3.12 Drivers e caches

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Catálogo de drivers com download | Eden `b60d0aab`, `8663d7fa`; Bannerlator `54c1dd35` | **Tem** (`drivers.json` e download) | — |
| Fontes de driver do usuário; driver sugerido por GPU | Bannerlator `54c1dd35`; DroidDeck `f13c885` | **Não tem** | Média / M |
| Cache invalidado quando o driver muda | Eden `1643d876`, `c2794985`; X360 Mobile v0.6.2 | **Tem** (A06: o arquivo do cache nomeia vendor, device e `pipelineCacheUUID`) | — |
| **Limpar o cache de shaders de um jogo pela interface** | Eden `97a8470b`; X360 Mobile v0.5.2 | **Não tem** (caches ocultos em Dados do usuário) | **Alta / P** |
| Variáveis do Turnip com ajuda | Eden `87d4c673`; Bannerlator `1933903c` | **Parcial**: `turnip_debug` | Média / P |

### 3.13 Logs, crashes e relatórios

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Logs por sessão, manter N, limpar, compartilhar | DroidDeck `d6e4ab1`; Bannerlator `96742d22` | **Tem** | — |
| Endereços, contas e identificadores apagados | Bannerlator `5149117f`; DroidDeck `3efa5ac`; Eden `bc55ed49` | **Tem** (A07, A11) | — |
| Motivo da saída do processo (sinal ou código) | Bannerlator `4a76d7ca` | **Tem** (ApplicationExitInfo; linha do crash nativo, 12n) | — |
| **Backtrace completo do tombstone** (ApplicationExitInfo, Android 12+) | Bannerlator `d883d01d`, `9a68627c` | **Não tem**: só a linha que o hook do core grava | **Alta / M** |
| **Ajustes alterados no topo do log e no relatório do run** | Eden `cbb92e75`; X360 Mobile v0.6.0 (cvars não padrão na revisão) | **Não tem** | **Alta / P** |
| Filtro e nível de log ajustáveis | Eden `b7f0f985`, `74ccea3d` | **Parcial** | Média / P |
| Automação para testes | DroidDeck `54adf9a` (ponte de debug) | **Tem** por outro caminho (suíte `.uitest`, lote 13) | — |

### 3.14 Atualizador

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Canais, SHA-256 obrigatório, checagem de pacote e assinatura | DroidDeck `6beeb29`, `af7dc40`; Eden `79b162a3` | **Tem** (Stable/Preview/Off) | — |
| Dizer "não dá para instalar" quando o Android recusaria | DroidDeck `fbc0014` | A conferir | Alta / P |
| Mensagens assinadas no app; rollout escalonado; builds bloqueadas | X360 Mobile v0.6.2 | **Não tem** | Baixa / M (exige publicador) |

### 3.15 Configurações e comunidade

| Feature | Fonte | XenDroid | Cabe? |
|---|---|---|---|
| Ajustes por jogo, presets, importar/exportar | todos | **Tem** (L08, C05, U06) | — |
| **Expor mais cvars do Xenia** | aX360e `8025979` (204 cvars) | **Parcial**: 135 chaves | **Alta / P–M**: começar por `anisotropic_override`, `present_safe_area_x/y`, zonas mortas dos sticks, `async_shader_compilation` (com aviso) e os `occlusion_query_*` (compatibilidade) |
| Configs da comunidade (buscar, "combina com meu aparelho", votos, aplicar só o que muda, enviar) | Bannerlator `09a319ff`, `8b0eea95` | **Impl. + Local no lote 15b**, desligado por padrão: cliente (buscar, ranking pelo aparelho, votos, aplicar pelo C05, enviar com consentimento, apagar) e servidor de referência com testes; falta alguém hospedar um servidor e o uso no aparelho (roteiro 53) | Média / G (exige servidor) |

### 3.16 Não se aplica

- **Bannerlator e DroidDeck:** lojas e contas (Steam, Epic, GOG, Amazon, chat, conquistas e nuvem
  de loja), Wine/Proton/DXVK/VKD3D/FEX/Box64 e containers, Wayland/X server/compositor,
  runtime Linux/proot/Flatpak, gerenciador de tarefas do Windows, DirectAudio/PulseAudio,
  wrapper BCn para DX12 em Mali, HDR10 para jogos Windows.
- **Eden:** o que é do Switch (Amiibo, NCE, MediaCodec, LDN/netplay, applets, firmware e chaves).

## 4. O que as referências desfizeram

| O quê | Fonte | Para o XenDroid |
|---|---|---|
| Tirou o modo sustentado ("limita clocks na maioria dos HALs") e uma sessão ADPF que reportava o intervalo do quadro como trabalho | DroidDeck `e217cbe` (uma semana depois de ligar em `06e67cd`) | Manter o sustentado desligado por padrão; o ADPF daqui já reporta trabalho real |
| Reverteu "manter o áudio do guest vivo em segundo plano" | Bannerlator `e936b4c2` | Não tratar isso como recurso comprovado |
| Aposentou o lsfg-vk e passou ao LSFG Native; governador desligado por padrão | Bannerlator `2ec0187a`, `8e67b586` | Coerente com o F04 consultivo daqui |
| Removeu todo o frame skip e a interpolação antigos antes do LSFG-VK | Eden `4cc9aa69` | — |
| Desligou a passada de rescale de resolução no Android por artefatos | Eden `9aa8e9b0` | Medir antes de mudar o padrão de escala |
| Manteve o envio online de compatibilidade desligado | X360 Mobile v0.6.0–v0.6.3 | Coerente com o C04 desligado até haver publicador |

## 5. Próximas fatias sugeridas

Em ordem de valor por esforço. Todas podem ser feitas e testadas na nuvem; a validação no
aparelho fica pendente.

1. **Core:** APC sem slot global (aX360e `a95d465`), com caso no `threading_test.cc`.
2. **Cvars por jogo:** `anisotropic_override`, `present_safe_area_x/y`, zonas mortas dos sticks
   e `async_shader_compilation` (com aviso), com textos en/pt-BR. (Feito no lote 14b; os
   limites do modo de oclusão falso ficaram de fora porque este fork não os usa.)
3. **Diagnóstico:** ajustes alterados no topo do log e no relatório do run.
4. **Controles de toque:** escondidos e mostrados ao vivo quando um controle físico é usado
   ou conectado.
5. **Crash:** backtrace do tombstone (ApplicationExitInfo, Android 12+) no registro do run.
6. **HUD:** potência em watts e estimativa de bateria; FPS que cai a 0 sem quadro novo.
7. **Android:** declarar o app como jogo (`appCategory`, `game_mode_config`, `GameManager`),
   medindo o efeito no aparelho.
8. **Entrada:** sem buffer (`requestUnbufferedDispatch`), medindo a latência no aparelho.
9. **Giroscópio:** "segurar para mirar".
10. **Cache:** "Limpar cache de shaders" na ficha do jogo.
11. **FG (continua experimental):** aviso de FPS × multiplicador acima do Hz e leitura
    "base → mostrado" no menu.
12. **Imagem:** debanding e, depois de decidir licença e medir custo, SGSR.

Maiores, para depois: carrossel/XMB, configs da comunidade (exige servidor), divisão da tela
em dobráveis, mais idiomas, LSFG por FPS-alvo.

## 6. O que o XenDroid já tem e as referências não

- Comparação A/B/ABBA com avisos de ordem e de "uma mudança só" (C07).
- Registro de cada run com o destino do processo e os números de desempenho.
- Patches conferidos contra a versão do jogo jogada (L10).
- Transações de save com recuperação (A04, A09).
- DocumentsProvider com lease e somente leitura com o jogo aberto (L07).
- Teclado do guest por controle (U10).
- Roteiro de aparelho em testes instrumentados num pacote separado (lote 13).
- Companion LAN para P2–P4, que entre as referências só o X360 Mobile tem.
