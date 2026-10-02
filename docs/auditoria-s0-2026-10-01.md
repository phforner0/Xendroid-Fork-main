# Auditoria S0 — 2026-10-01

Registro da auditoria do checkout, das correções do primeiro lote e do que ficou
pendente. Complementa o [status](experiencia-em-jogo-status.md) e o
[plano mestre](plano-evolucao-cinco-referencias.md). Classificação usada:

- **reproduzido** — falha observada nesta sessão;
- **defeito por leitura** — demonstrável pelo código citado, sem execução;
- **hipótese** — plausível, exige teste (geralmente no aparelho);
- **incompleta** — funcionalidade prometida pelo plano/UI sem backend completo;
- **melhoria** — arquitetura, desempenho ou energia.

Nenhum item abaixo foi validado no aparelho nesta sessão.

## 1. Identificação

| Item | Estado |
|---|---|
| Repositório | `C:\Users\Administrator\Desktop\Xendroid-Fork-main` (`/mnt/c/...` no WSL) |
| Branch / HEAD | `main` @ `77011a0c` (confirmado; igual ao snapshot do prompt) |
| `origin/main` | `77d38efe` (PR #11) no início da auditoria, **46 commits à frente**; durante a sessão avançou para `6c772efb6` (PR #12, plan-v6-ab11), **51 commits à frente**. 0 commits locais à frente. São as PRs #4–#12 de desempenho (resolve/transfer/texload, A2C, VRS, especialização de sinais de textura, quirks Forza). O trabalho de UX/FG inteiro está **não commitado** sobre a base antiga. |
| Base do fork | Histórico próprio desde `de222128` ("Initial commit", snapshot do XenDroid). Não há remote do upstream; "mudanças do fork" = histórico deste repo + working tree. |
| Working tree | 54 arquivos rastreados modificados (2 são ruído de build: `third_party/{snappy,zlib-ng}/build.ninja`), 1 removido (`RebuildPlanTest.kt`), ~190 não rastreados (UX, saves, drivers, Win-FG, LSFG, testes, docs e artefatos CMake em `third_party/zlib-ng`). |
| Sobreposição com `origin/main` | Só `ui/vulkan/vulkan_device.cc` (região diferente, trivial). `gpu/vulkan/vulkan_pipeline_cache.cc` mudou upstream (nome do cache por `ir3_debug`) na mesma função tocada pelo A06: conflito pequeno e manual na integração. |
| Árvore nativa compilada | `emulator-core/src/main/cpp/CMakeLists.txt` → `add_subdirectory(xenia)`. **Não existe `xenia-canary`**; `BUILD.md` citava o nome antigo e submódulos (o `third_party` é vendorizado, `.gitmodules` é vestigial). |
| Cópia de build | `/home/administrator/xendroid-stage-0929`: cópia byte a byte (CRLF preservado), confirmada idêntica às fontes por `rsync -c` antes das mudanças. |
| Toolchain | JDK `/home/administrator/xendroid-toolchain/jdk-21.0.12.1+1`, SDK 35, NDK 29.0.14206865, CMake 3.30.3, `XENDROID_NINJA_JOBS=2` (WSL com ~3 GB). Sem compilador C++ do host nem headers da glibc: testes C++ de lógica rodam como executáveis **estáticos x86_64 do NDK** (`tools/test-native-logic.sh`); os testes Vulkan/llvmpipe de `test-presentation-host.sh` não puderam ser reexecutados. |

Linha de base desta sessão, antes de qualquer mudança: `:app:testDebugUnitTest`
= **117 testes, 27 suítes, 0 falhas** (agora reproduzido, não só histórico).

## 2. Achados por subsistema

### 2.1 Configuração

| ID | Classe | Evidência | Situação |
|---|---|---|---|
| CFG-01 | defeito por leitura (alto) | `profile_manager.cc:119` faz login do slot 0 no construtor do kernel; `Login→UpdateConfig→config::SaveConfig()` (`profile_manager.cc:652`). Sem config por jogo/quirk (`game_config_loaded=false`), o `:emu` reescrevia **o config global inteiro** em todo boot, com `fopen("wb")`, fora do lock do frontend: perda de edições feitas desde o boot e leitura truncada possível. Contradiz a regra declarada em `config.cc:57-64`. | **Corrigido**: `SaveConfig()` é no-op em `XE_PLATFORM_xendroid` (log "SaveConfig skipped"). |
| CFG-02 | defeito por leitura (médio) | `SaveGameConfig` (`config.cc`, usado por `xconfig.cc:253`) gravava o TOML por jogo com `ofstream` direto. | **Corrigido**: temporário + `rename`. Resta: sem lock comum com o editor Java no mesmo processo (perda de atualização rara — hipótese). |
| CFG-03 | defeito por leitura (alto) | `ConfigStore.openLive()` trocava um config global que não parseia pelo template (todas as configurações perdidas) e gravava sem lock/atomicidade; usado por `seedTouchOverlayDefault` e `ProfileBootstrap`. | **Corrigido**: `openLive()`/`closeFile()` removidos; ambos usam `editLiveConfig`; criação do arquivo ausente agora sob lock e nunca substitui arquivo existente. 2 testes instrumentados novos (compilados, **não executados**). |
| CFG-04 | incompleta | `SettingContract` só tem escopo/momento de aplicação; requested/effective/capability/reason por opção não existem (U03). | Pendente (S3). |

### 2.2 Saves, armazenamento e perfis

| ID | Classe | Evidência | Situação |
|---|---|---|---|
| SAV-01 | defeito por leitura (alto) | `EmulatorSession.bootOnce()` fazia recuperação + lease dentro de `surfaceCreated`; qualquer falha virava `Log.e` + `finish()` silencioso. Backup automático (`MainActivity.onStart`) segura o lease e podia derrubar o lançamento seguinte. | **Corrigido**: `prepareStorage()` antes da Surface, espera limitada (15 s, `acquireWithRetry`), recuperação sob o mesmo lease e diálogo com a causa. |
| SAV-02 | defeito por leitura (alto) | `recoverTransactionsLocked()` abortava no primeiro journal ruim: as demais transações não eram revertidas e todo export/prepare/boot falhava. | **Corrigido**: recuperação isolada por transação, relatório (`RecoveryReport`), falha preservada (nunca apagada); boot e operações de save continuam bloqueados enquanto houver falha (segurança dos dados), com mensagem. |
| SAV-03 | melhoria | Diretórios de preview sem journal (prepare cancelado/morte do processo) acumulavam para sempre. | **Corrigido**: descartados após 24 h, só se não houver `original/`. |
| SAV-04 | incompleta | Transações confirmadas guardam as cópias pré-restore sem UI, cota ou retenção. | Pendente (UI de backups/retensão). |
| SAV-05 | hipótese (refutada em parte) | `.save-transactions`/lixeira dentro de `content/`: o `FindProfiles` do Xenia filtra `[0-9A-F]{16}` (`profile_manager.cc:420`), então não aparecem como perfis. Outras enumerações do content root não auditadas. | Registrado. |
| SAV-06 | melhoria | `BackupSync.publishAutomatic` roda em todo `onStart` da biblioteca: exporta, faz hash e relê o arquivo remoto inteiro para verificar, mesmo sem mudança. | Pendente: pular quando a impressão digital da árvore de saves não mudou desde o último envio verificado. |
| PRF-01 (A09) | defeito por leitura (alto) | `ProfileManagerViewModel.delete()` removia `content/<XUID>` inteiro (conta + saves de todos os jogos) de forma permanente, com aviso vago; erro no meio deixava remoção parcial. | **Corrigido**: prévia com jogos/arquivos/bytes afetados, mudança atômica para `content/.xendroid-trash/profiles/<XUID>-<ms>`, restaurar/remover definitivamente na tela, XUID de máquina recusado. |
| PRF-02 (A09) | defeito por leitura (médio) | `writeAvatar`: decode sem limite (OOM), PNG gravado direto, falha do avatar relatada como falha do perfil já criado, imagem inválida ignorada em silêncio. | **Corrigido**: leitura ≤32 MB, bounds-only + `inSampleSize`, decode antes de criar o perfil, gravação atômica, mensagem distinta para avatar. |
| PRF-03 | defeito por leitura (médio) | `setActive()` não tratava exceção do config → crash no `viewModelScope`. | **Corrigido**. |
| PRF-04 | defeito por leitura (baixo) | `ProfileBootstrap` criava perfil sem lease e gravava config sem lock. | **Corrigido**. |

### 2.3 Sessão, lifecycle e IPC

| ID | Classe | Evidência | Situação |
|---|---|---|---|
| SES-01 | defeito por leitura (médio) | `configChanges` da `EmulatorHostActivity` sem `density/fontScale/colorMode/...`: mudar tamanho de fonte/tela com o jogo em segundo plano recria a Activity e `onDestroy` mata o processo `:emu` (jogo perdido). | **Corrigido** no manifest; validar no aparelho. |
| SES-02 | defeito por leitura (médio) | `MainActivity` resolvia URIs `content://` no processo principal e podia repassar `/proc/self/fd/N` ao `:emu`, onde o descritor não existe. | **Corrigido**: `FrontendLaunch.resolveForHandOff` repassa caminho real ou a URI com grant. |
| SES-03 | hipótese | Com a task do XenDroid em segundo plano (mesma affinity, `singleTask`), sair de um jogo lançado por frontend sem `CLEAR_TASK` pode voltar à biblioteca, não ao frontend. | Roteiro de teste no aparelho; candidata: affinity própria. |
| SES-04 | melhoria | Segundo intent de lançamento com jogo aberto é ignorado sem aviso (`singleTask`, sem `onNewIntent`). | Pendente. |
| SES-05 | incompleta | Não há SessionRunId nem estado de sessão durável; só arquivamento de logs por app-session e lista de Title IDs. | Próxima fatia (S1). |
| SES-06 | melhoria (energia, pré-existente) | Loop de UI nativo (`xendroid_emu.cpp` `main_loop`) acorda a cada 1 ms quando ocioso. | Candidato a condvar; não alterado sem validação no aparelho (risco de travar a pintura). |
| SES-07 | observação | IPC entre processos é só o vínculo de vida (`MainAliveService`) + arquivos com lock; nenhum singleton Kotlin é usado como IPC. | OK. |

### 2.4 Entrada

| ID | Classe | Evidência | Situação |
|---|---|---|---|
| IN-01 | defeito por leitura (médio) | `ae::key_event` repassava índice vindo do Java (keymap/layout editável) para `key_status_[24]` sem checagem. | **Corrigido**: bounds check em `xendroid_emu.cpp`. |
| IN-02 | defeito por leitura (baixo) | Gyro reenviava o eixo direito a cada amostra, zerando um stick direito de toque segurado (só o físico tinha prioridade). | **Corrigido**. |
| IN-03 | hipótese | Prioridade menu/prompts/toque/físico, repetição, hotplug e prompts guest só testados por input sintético. | Validação no aparelho pendente. |

### 2.5 Logs e diagnóstico

| ID | Classe | Evidência | Situação |
|---|---|---|---|
| LOG-01 | defeito por leitura (médio, privacidade) | O redator não cobria os formatos reais do Xenia: `Loaded <gamertag> (GUID: <xuid>)`, `User <nome> (XUID: …)`, `Loading Account: <xuid>`, `logged_profile_slot_0_xuid = "…"` do dump de config. | **Corrigido** + testes (hashes de shader sem palavra-chave continuam legíveis). |
| LOG-02 | defeito por leitura (baixo) | Sobras de arquivamento viravam entradas `120000-xe.log`, recusadas no export; e o export parava no primeiro item não permitido, perdendo `exit-info.txt` após um tombstone binário. | **Corrigido** + testes. |

### 2.6 Drivers, cache Vulkan e updater

| ID | Classe | Evidência | Situação |
|---|---|---|---|
| DRV-01 (A06) | defeito por leitura (médio) | `vulkan_pipeline_cache.cc`: leitura sem limite nem checksum, gravação `wb` direta na thread da GPU, arquivo só por Title ID — trocar de driver descartava o cache do outro a cada troca. | **Corrigido**: `vulkan_pipeline_cache_file.h` (envelope com tamanho/XXH3, limite 256 MiB, temporário+rename, identidade vendor/device/`pipelineCacheUUID`, arquivo de outro driver movido para `<stem>.<tag>.vk.bin`, no máximo 4 por título, formato antigo aceito). Nome principal mantido (scripts de A/B que trocam `4D5309C9…vk.bin` continuam valendo). |
| DRV-02 | incompleta | Instalador de driver correto (endereçado por SHA, ABI ELF64 AArch64), mas sem DriverIdentity (driverID/versão/API/UUID) nem catálogo por hardware. | Pendente (S4). |
| UPD-01 | defeito por leitura (médio) | Updater consultava `rfandango/XenDroid` para todo pacote ≠ `.fork` (oferecendo builds do upstream a `.fork.opt`/sem sufixo), nunca consultava o feed do próprio fork para `.fork`, comparava strings (sem ordem), registrava corpos HTTP inteiros no logcat (que vai para o diagnóstico) e mostrava "sem atualizações" a cada checagem automática. `versionCode` fixo em 1. | **Corrigido em parte (R01)**: feed do fork só para `.fork`, `versionCode` por `-PxendroidVersionCode` (CI passa o run number), tag `XenDroid-v<n>-<sha>`, ordem estrita, log `NONE` no release, diálogo só para atualização real. Pendente R02–R04 (canais, download/hash/PackageInstaller). |
| LSFG-01 | hipótese refutada | Reimportar a mesma DLL sobrescreveria o cache em uso: `lsfg_dll.cpp writeCache` já usa temporário + `fsync` + `rename`. | OK. |

### 2.7 Apresentação e frame generation

| ID | Classe | Evidência | Situação |
|---|---|---|---|
| FG-01 | defeito por leitura (baixo-médio) | Cap temporário de FPS do FG só era devolvido com o menu aberto; FG que parasse sozinho (falha, cadência) com o menu fechado deixava o cap. | **Corrigido** (`GenerationCap` + testes JVM). |
| FG-02 (A02) | melhoria | Política de tempo embutida na thread Vulkan, sem teste. | **Extraída** para `FrameGenerationSchedule` (`presentation_runtime.h`) com testes de relógio fake; saídas sintéticas atrasadas mais de meio passo são puladas (sem rajada), com contador `late_synthetic_skips` exposto no estado/HUD. |
| FG-03 (A03) | observação + melhoria | Vida útil das imagens-fonte correta por leitura (barreiras `ALL_COMMANDS` + `shared_ptr` até `AwaitAllSubmissions`). Custos: espera global a cada pintura agendada e `DeviceWaitIdle` em resize/preset/destroy do Win-FG. Ponteiro estático do device Win-FG não era limpo. | Ordem de locks documentada no código; ponteiro limpo no destrutor. Reduzir waits (F03) segue pendente e exige prova de vida útil. |
| FG-04 | hipótese | Ligar/desligar FG troca a política do swapchain e depende do pedido de pintura ao loop de UI para reconectar; o caminho existe (`main_loop` EVENT_PAINT). | Validar no aparelho. |

### 2.8 Ambiente e repositório

- `third_party/{snappy,zlib-ng}/build.ninja` modificados e `third_party/zlib-ng/CMake*`
  criados por um CMake rodado dentro da árvore em 29/09: ruído, não incluir em commit.
- `BUILD.md` descrevia `xenia-canary` e submódulos: corrigido nesta sessão.
- Integração com `origin/main` recomendada antes de qualquer commit deste trabalho
  (decisão do usuário; não feita aqui). **Ensaio sem efeito colateral** (`git stash
  create` + `git merge-tree --write-tree origin/main`, sem alterar HEAD, índice ou
  working tree): o diff rastreado mescla **sem conflitos** (auto-merge em
  `vulkan_pipeline_cache.{cc,h}` e `vulkan_device.{cc,h}`; o resultado combina o nome
  por `ir3_debug` do upstream com o store do A06, como esperado), e nenhum dos 211
  arquivos não rastreados existe em `origin/main`. Repetido contra `6c772efb6`
  (PR #12, que voltou a mexer nesses quatro arquivos): continua sem conflitos e sem
  colisão semântica (o upstream só acrescentou campos de shading rate). Repetido de novo
  no fim do lote 2 (após `git fetch`, `origin/main` ainda em `6c772efb6`): sem
  conflitos, e nenhum dos 221 arquivos não rastreados existe em `origin/main`. Como esses 4 arquivos estão sujos e
  mudaram upstream, `git merge --ff-only` recusaria; caminho sugerido: criar uma branch,
  commitar este trabalho (sem os `build.ninja`/artefatos CMake) e então `git merge
  origin/main`.

### 2.9 Empacotamento e JNI (lote 5, 2026-10-02)

| ID | Classe | Achado | Ação |
|---|---|---|---|
| PKG-01 | defeito por leitura | O APK declarava `native-code: arm64-v8a armeabi-v7a x86 x86_64` (bibliotecas nativas auxiliares do AndroidX), mas o core `libe.so` só existe para arm64: um aparelho 32 bits/x86 instalaria o app e travaria ao carregar o core. | `ndk { abiFilters 'arm64-v8a' }` no `app`; `tools/check-apk.py` exige só arm64. |
| PKG-02 | risco sem verificação | Uma entrada de `RegisterNatives` que não casa com um `native` Java faz o registro da classe inteira falhar no load (app não abre); um `native` sem registro dá `UnsatisfiedLinkError` na primeira chamada. Nada verificava isso antes do aparelho, e os lotes 2–4 acrescentaram 6 JNIs. | `tools/check-jni.py` (+ autoteste) no workflow Checks: árvore atual consistente (81 nativos = 80 registros + 1 exportado). |
| PKG-03 | observação | A ponte TOML de `emulator.cpp` passa `toml::table*` como `long` Java: correto em arm64 (único ABI do app), truncaria em 32 bits. | Documentado pelo verificador (nota explícita); sem mudança. |
| PKG-04 | observação | Comentário em `app/build.gradle` diz que `libe.so` fica "stored uncompressed", mas `jniLibs.useLegacyPackaging = true` comprime e extrai as libs na instalação (o APK mostra `libe.so` deflated). | Só registrado: mudar o empacotamento afeta tamanho/instalação e pede medição. |

## 3. Verificações deste lote

Registradas no [status](experiencia-em-jogo-status.md#estado-atual--2026-10-01-auditoria-s0),
com comandos e resultados exatos.

## 4. Roteiro de validação no aparelho (pendente)

1. Config: editar uma opção global na biblioteca enquanto um jogo **sem** config por
   jogo inicia; conferir no `xe.log` "SaveConfig skipped" e que a edição persiste.
2. Lease: iniciar um jogo logo após voltar à biblioteca com backup automático ativo;
   esperado: aviso "Waiting for a save…" e boot normal, ou diálogo explicativo.
3. Recuperação: com um journal de teste corrompido em `content/.save-transactions`,
   abrir um jogo: esperado diálogo nomeando a transação; dados originais intactos.
4. Perfis: excluir perfil de teste (prévia lista os jogos), restaurar, excluir
   definitivamente; avatar enorme/arquivo não imagem → mensagem, sem crash.
5. Lifecycle: jogo em segundo plano, mudar tamanho da fonte/tela nas configurações,
   voltar: o jogo deve continuar (sem reinício do processo `:emu`).
6. Frontend: ES-DE/Daijishō com e sem `CLEAR_TASK`, URI `content://` sem caminho real;
   anotar para onde volta ao sair (SES-03).
7. Cache Vulkan: abrir Forza com Turnip V37, sair, trocar para V36, abrir, voltar
   para V37: o `xe.log` deve mostrar "restored this driver's archive" e o tempo de
   compilação não deve repetir o primeiro boot.
8. FG (debug): ligar Win-FG 2×, forçar parada (cadência > Hz) com menu fechado e
   conferir que o limite de FPS anterior volta; contador de "late frames skipped".
9. Lote 2 — run e ficha: jogar Forza ~2 min, ir para o fundo e voltar, sair pelo menu.
   Na ficha: "Last run: ended normally after …", FPS de 1 s, "Guest frame time: 99th
   percentile under … ms", driver com "· package xxxxxxxx" quando Turnip instalado pelo
   app, e "Last run timeline" com foreground/background, pausa/retomada, Surface,
   térmico e saída. Avaliar o jogo (Compatibility) e conferir build/driver/media no
   relato.
10. Lote 2 — interrupção: com o jogo aberto, `adb shell am force-stop` no pacote; ao
   reabrir a biblioteca, o run aparece como interrompido e a linha do tempo termina no
   último evento gravado (até 30 s antes).
11. Lote 3 — boot: abrir Forza com o cache de pipelines apagado; antes do primeiro
   quadro o rótulo deve mostrar "Preparing graphics: N pipelines created… X s" e sumir
   com a imagem; na ficha, "First frame after X s", "Pipelines created: …" e, na linha
   do tempo, rajadas de criação de pipelines. Abrir de novo com o cache: menos
   pipelines/tempo e primeiro quadro mais cedo.
12. Lote 4 — áudio e relatório: jogar ~2 min e sair; a ficha deve mostrar "Audio ·
   AAudio: …" (com quedas de FPS, blocos ocultados > 0 e rajadas "audio" na linha do
   tempo). "Share last run report": conferir a prévia (sem caminho do jogo, sem nomes)
   e abrir o ZIP compartilhado. Erro fatal: se ocorrer um device lost, a ficha deve
   dizer "failed …: fatal error: Graphics device lost …" em vez de "native crash".
13. Lote 5 — pacote de configurações: em Configurações, "Back up or move settings" →
   Export; mudar uma opção global e o layout de toque; Import do arquivo exportado: a
   prévia deve listar exatamente essas mudanças e o backup; depois, os valores voltam.
   Conferir que `vulkan_lib_path`/`content_root` deste aparelho não mudaram e que um
   arquivo qualquer (não bundle) é recusado com mensagem.
14. Lote 7 — companion LAN (dois telefones com este APK na mesma rede: "host" roda o
   jogo, "cliente" é o outro; de preferência um jogo com multijogador local/split-screen):
   a. Host, com o jogo já rodando: menu (Back) → Controls → "Phone controllers". Esperado
      "Phone controllers · On (activate to turn off)" e o bloco "… → 192.168.x.y:porta,
      code NNNNNN (Wi-Fi)" com "No phone connected yet". Só com dados móveis (Wi-Fi e
      hotspot desligados): "Off: no Wi-Fi, hotspot or Ethernet network". Antes do jogo
      rodar (tela preta do boot): aviso "…once the game is running".
   b. Cliente: Biblioteca → ⋮ → "Use this phone as a controller", digitar endereço e
      código → pad em paisagem com "P2 · N ms · Leave". No host (menu aberto), a linha
      "P2 <nome> (N ms)"; o jogo vê o controle 2 conectar (tela de entrada do P2).
   c. Código errado → "Wrong code" no cliente; dez erros → "Pairing is closed…" e, no host,
      "Pairing locked…"; desligar e ligar no host gera código novo, que funciona.
   d. Com o menu do host aberto, o P2 não age no jogo; ao fechar o menu, o estado atual do
      cliente vale (um stick ainda segurado continua movendo, sem precisar soltar).
   e. Desligar o Wi-Fi do cliente em jogo: em ~1 s o P2 solta tudo (personagem para) e o
      host deixa de listá-lo; religar e reconectar: o mesmo telefone volta como P2, sem
      botão preso nem "fantasma".
   f. Rumble: um evento que vibra o controle do P2 vibra o telefone cliente conforme a
      intensidade escolhida nele (Off não vibra); abrir o menu do host para a vibração.
   g. Terceiro telefone → P3; com P2–P4 ocupados, o próximo recebe "All player slots are
      taken". Um controle físico conectado depois pega o slot livre seguinte (P1 se livre).
   h. Sair do jogo no host: o cliente mostra "Disconnected: …" em até ~1 s. Na ficha do jogo,
      "Last run timeline" mostra "companion · phone controllers on (Wi-Fi)", "phone
      controller joined as P2" e "… left: …", nunca o IP, a porta ou o código.
   i. Hotspot: repetir (b) com o host como hotspot e o cliente conectado a ele.
   Registrar aparelhos, Android, rede (Wi-Fi/hotspot), jogo e as latências mostradas.
15. Lote 8 — FG observável (APK debug; FG continua off por padrão): num jogo com folga de
   GPU (Forza é o controle negativo), Graphics → Win-FG 2× On por ~2 min, depois sair.
   a. Menu, página Graphics: "Win-FG: active · requested 2× · X ms GPU" com X variando; se o
      driver não der timestamps, "GPU timing unavailable" (nunca um número parado). Logo
      abaixo, "Budget (advisory, never acts): fits · generation X ms of Y ms between
      outputs" (ou o motivo: generation over budget / synthetic outputs late / thermal /
      more outputs than the display shows). Anotar X, Y, o Hz e o FPS do jogo.
   b. Ficha do jogo → último run: "Frame generation (experimental) · N synthetic frames over
      S s (of M slots: …) · GPU per generation pass: median …, 95th …, 99th … (T timed…)";
      conferir que a mediana bate com o X visto no menu e que M ≈ quadros-fonte × (2−1).
      Na linha do tempo, "fg budget · …" quando o veredito mudou.
   c. LSFG (com a DLL do usuário importada), 3×: mesmos campos; M ≈ 2 por quadro-fonte e
      "… painted without a generated frame" só no aquecimento.
   d. Com o veredito "over budget" ou "late": registrar se a imagem engasga (os limiares do
      governador são hipótese; ele não age sozinho). Device, driver, jogo, Hz e temperatura.
16. Lote 9 — modo Jogador (L02): Configurações → "Interface: Developer" → "Switch to Player":
   a lista vira uma só categoria "Essentials" (19 itens); mudar o limite de FPS ali e voltar a
   Developer: o mesmo valor aparece em GPU → Frame rate limit, e um ajuste avançado mudado
   antes (ex.: Vulkan → Validation layers) continua com o valor dele. Abrir um jogo em Player:
   no menu em jogo não aparecem Win-FG/LSFG, "Presenter ADPF hints", "Host submissions",
   "Pause on background" nem "Sustained performance", e D-pad/LB/RB percorrem só o que
   aparece. Voltar a Developer e abrir outro jogo: tudo volta. Configurações por jogo
   (ficha → Settings) seguem o mesmo modo.
17. Lote 9 — assistente inicial (L01): com o app recém-instalado (ou apagando os dados do
   pacote `.debug`), abrir a biblioteca: aparece "Welcome to XenDroid" com ✓ na GPU (nome do
   Adreno), em 64-bit ARM e no Android; "Game folder" com "!" e o botão que leva ao fluxo de
   All Files Access/pasta; com o telefone em português do Brasil, "From this phone: language
   pt · region BR" → "Use them for games" → "Games will use them from the next launch" e, em
   Configurações (Developer) → Console, User language = pt e User country = BR. "Open
   Profiles" abre Perfis e, ao voltar, o assistente continua; "Done" sem escolher modo deixa
   Player. Reabrir a biblioteca: não aparece de novo; ⋮ → "Setup assistant" reabre.
18. Lote 9 — várias pastas (L03): com a pasta de antes, ⋮ → "Game folders" lista ela como a
   primeira ("installs go here"). "Add folder" → uma pasta no cartão SD com outros jogos: a
   biblioteca mostra os dois conjuntos, sem repetir. Adicionar uma subpasta da primeira (e
   `/sdcard/…` para a mesma pasta): nada se repete. Tirar o cartão (ou revogar o acesso) e
   puxar para atualizar: aparece "1 game folder(s) not available now…", os jogos da memória
   interna continuam; recolocar e atualizar: voltam sem demora de extração. "Remove" numa
   pasta: os jogos dela somem da lista e os arquivos continuam no lugar (conferir no
   gerenciador de arquivos).
19. Lote 9 — capas (L05): toque longo num jogo: a ficha mostra a capa ao lado do nome.
   "Change cover" → escolher uma foto grande (ex.: 4000×3000) e, noutro jogo, uma foto
   vertical da câmera (girada pelo EXIF): o tile e a ficha mudam na hora, na orientação certa,
   e `files/covers/<TITLEID>.custom.png` tem no máximo 512 px no lado maior (`run-as
   xendroid.compose.debug ls -l files/covers`). Num jogo de dois discos, os dois tiles mudam.
   Renomear a ISO (ou movê-la para outra pasta da biblioteca) e atualizar: a capa continua.
   Configurações do Android → app → "Limpar cache" e reabrir: a capa escolhida continua e os
   outros jogos mostram o próprio ícone. "Create shortcut": o atalho usa a capa escolhida.
   "Use the game's own icon": volta o ícone do jogo. Escolher um arquivo que não é imagem (ou
   um PNG corrompido): "Could not use that image: …" e nada muda. Nenhum acesso à rede.
20. Lote 9 — jogos que saíram da biblioteca (L06): jogar um pouco um jogo, sair, renomear a
   ISO para fora do padrão (ex.: `.iso.bak`) e puxar para atualizar: aparece "1 game(s) you
   played or added are no longer in the library. Review"; o diálogo mostra a capa, "File not
   found…", o caminho antigo e o tempo de jogo. Tirar o cartão SD com jogos: esses não entram
   no aviso (só no aviso de pasta) e aparecem em ⋮ → "Games no longer in the library" como
   "Its game folder is not available now". "Remove" num jogo: some da lista e do aviso, e na
   ficha de outro disco/título nada muda; voltar o nome `.iso` e atualizar: o jogo volta com o
   mesmo tempo de jogo e relato. Remover uma pasta (L03) de jogos já jogados: eles aparecem
   como "outside your game folders". Nenhum arquivo é apagado.
21. Lote 9 — ficha e coleções (L06): na ficha de um jogo com TU e DLC instalados, "Manage
   content" mostra "Title update: <nome> · 2 DLC" e "Game patches" mostra "N of M enabled"
   (ligar um patch, voltar: o número muda). "Collections" → criar "RPGs": o jogo entra; criar
   "rpgs" de novo: recusa com mensagem; marcar outro jogo em "RPGs"; na biblioteca, o seletor
   "All collections" → "RPGs (2)" mostra só os dois; mover a ISO de um deles para outra pasta
   da biblioteca e atualizar: continua na coleção. "Delete" na coleção: confirma, a coleção some
   e os jogos continuam. Exportar o pacote de dados (Configurações), apagar a coleção, importar:
   a prévia diz "Collections: 1 new, …" e ela volta.
22. Lote 9 — varredura (L09): (a) com uma biblioteca grande (centenas de jogos), fechar o app
   pelo multitarefa e abrir: a grade aparece na hora com a lista anterior e o indicador de
   atualização; a lista final substitui sem pular. (b) Limpar o cache do app e abrir: aparece
   "Looking through N files…" e depois "Reading <jogo> (i of N)"; "Stop" no meio: a tela
   mostra "Scan stopped…", e "Retry" continua mais rápido (o já extraído ficou). (c) Mover uma
   pasta com jogos para outra pasta da biblioteca (gerenciador de arquivos) e atualizar: os
   jogos voltam sem "Reading…" (sem nova extração). (d) Adicionar como pasta de jogos o
   armazenamento interno inteiro: a varredura para em 100.000 entradas com o aviso de lista
   parcial; remover essa pasta. (e) Anotar o tempo da varredura quente de 1k e 10k arquivos
   (cronômetro do "Looking through…") no aparelho. (f) Apagar uma ISO pelo gerenciador com o
   app em segundo plano e, ao voltar, tocar nela antes de a varredura terminar: aviso "…is not
   where it was…" e nova varredura, sem abrir o emulador.
23. Lote 9 — lixeira de conteúdo (L12): num jogo com uma DLC instalada, Manage content → DLC →
   lixeira na DLC → "Move to trash": some da aba DLC e aparece em "Trash (1)" com tamanho e
   data; abrir o jogo: a DLC não está disponível. "Restore": volta para a aba DLC e o jogo a
   vê de novo (conferir no jogo). Mover de novo, reinstalar o mesmo pacote e tentar "Restore":
   recusa com "…is installed again…". "Delete" na lixeira: pede confirmação e apaga. Com o
   jogo aberto (outro processo), tentar mover: "Close the running game first.". Matar o app
   (`am kill`/forçar parada) logo após tocar em "Move to trash" com uma TU grande: ao reabrir o
   gerenciador, a TU está ou instalada ou na lixeira, nunca sumida.
