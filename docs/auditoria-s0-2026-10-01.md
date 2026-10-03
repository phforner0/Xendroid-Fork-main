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
| STO-01 (L07, 2026-10-02) | defeito por leitura (alto) | `DocumentsProvider` (então em `:emulator-core`): o id de documento é o caminho absoluto e `getFileForDocId` fazia só `new File(docId)`; `isChildDocument` comparava texto (`startsWith(parent + "/")`). Um app com permissão numa pasta do provider podia pedir `<raiz>/../../…` (ou seguir um link) e ler/gravar qualquer arquivo do app, inclusive o armazenamento interno; também expunha os caches do emulador (shaders derivados de jogos) e mudava saves com o jogo aberto. | **Corrigido**: provider reescrito em `:app` com o mesmo nome e autoridade; `UserDataFiles` aceita só a raiz e o que está dentro dela com `..`/links resolvidos, comparação por segmento, nomes de um só segmento, nada substituído, caches e controles internos escondidos, toda mudança (e escrita aberta, até fechar) sob o lease; testado na JVM. |
| STO-02 (2026-10-02) | defeito reproduzido (alto) | `ContentLease.acquire` abria o arquivo de trava e, ao falhar (lease já do mesmo processo), fechava-o: fechar qualquer descritor de um arquivo solta todas as travas POSIX do processo, então a primeira lease deixava de valer para o processo do jogo. Reproduzido no contêiner (Java + `fcntl` de outro processo: "other process: GOT the lock"). | **Corrigido**: lista em memória das travas do processo, recusa antes de abrir o arquivo; `close` idempotente. Teste `aRefusedSecondLeaseInTheSameProcessDoesNotFreeTheFirst` falha no código antigo ("expected busy but was free") e passa no novo. |

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

### 4.0 Testes automatizados do roteiro (2026-10-03)

Parte do roteiro virou teste instrumentado em `app/src/androidTest/java/xendroid/compose/roadmap/`,
uma classe por item. Eles rodam num pacote próprio, `xendroid.compose.uitest` (build type `uitest`):
instalar e rodar **não toca** nos jogos, saves nem ajustes do `xendroid.compose.debug`, e o Gradle
desinstala o pacote de teste no fim. Com o telefone conectado por adb (WSL ou pod):

```bash
./gradlew --no-daemon --console=plain :app:connectedUitestAndroidTest
# só um item:
./gradlew :app:connectedUitestAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=xendroid.compose.roadmap.Item24UserDataProviderTest
```

Relatório: `app/build/reports/androidTests/connected/uitest/index.html`. **Estado: escritos e
compilados na nuvem; ainda não rodados** (sem telefone). Um item automatizado só passa a "Validado no
aparelho" depois de rodar verde no telefone; uma falha lá é defeito do app ou do teste, a investigar.
O que cada teste não alcança continua na lista abaixo, como antes.

| Item | Teste | Cobre | Continua manual |
|---|---|---|---|
| 3 | `Item03SaveRecoveryTest` | journal danificado nomeado e mantido; ao abrir um jogo (processo `:emu` de verdade, com um arquivo que não é jogo: o armazenamento é preparado antes de abrir o jogo) o diálogo nomeia a transação; o save original fica | — |
| 4 | `Item04ProfilesTest` | prévia da exclusão na tela Perfis com o jogo dos saves; lixeira, restaurar com os saves, excluir de vez (código nativo de perfis); imagem enorme e arquivo que não é imagem dão a mensagem e não criam nada; foto 4000×3000 vira o avatar | — |
| 13 | `Item13DataBundleTest` | exportar, mudar, importar pela seção real (o seletor do sistema respondido pelo Espresso-Intents): a prévia lista exatamente as mudanças e o backup, os valores voltam, o caminho do driver deste aparelho fica, um arquivo que não é pacote é recusado com a mensagem | — |
| 16 | `Item16PlayerModeTest` | "Jogador" = 19 ajustes em uma seção, o limite de FPS mudado ali aparece em Developer → GPU, ajuste avançado mantido; ajustes do jogo seguem o modo; menu em jogo sem Win-FG, LSFG, ADPF, submissões, pausa em segundo plano e modo sustentado (e com eles em Developer) | o menu dentro de um jogo rodando (grupo C) |
| 17 | `Item17FirstRunAssistantTest` | assistente na `MainActivity` a partir do estado de instalação nova: checagens (GPU, arm64, Android), "!" e o botão da pasta (o voltar do telefone volta ao assistente), idioma/região pt/BR gravados no console, Perfis e volta, "Pronto" sem modo = Jogador, só uma vez, ⋮ reabre | — |
| 24 | `Item24UserDataProviderTest` | o DocumentsProvider como o app Arquivos o chama: caches e controles internos ocultos, cópia vira "(1)", renomear e apagar, tudo somente leitura com o lease ocupado (ler continua), "Jogos por título" com pastas de reunião sem renomear/mover/apagar | as telas do Arquivos, um app de terceiros com a raiz, a permissão sobreviver à atualização do APK |
| 26 | `Item26UserPatchesTest` | arquivo próprio entra com os patches desligados e "adicionado por você", repetido vira "(1)", de outro jogo é recusado com o Title ID, escrita sem `address` recusada com a linha, aviso de conflito com o endereço, remover | jogar com o patch e o `xe.log` (grupo C) |
| 28 | `Item28PortugueseTest` | app em pt-BR pelo idioma do app (Android 13+): menu em jogo em todas as abas sem texto cortado, "☰" como botão "Abrir menu", opção destacada anunciada como selecionada, rodapé visível com a maior fonte em tela pequena, inglês ao voltar; ⋮ da biblioteca e cada tela que ele abre com o título em português e nada cortado; Configurações, "1 ajuste"/"N ajustes" e a busca "tela" achando "Tela larga" | a fala do TalkBack; o menu dentro de um jogo (grupo C) |
| 35 | `Item35RecommendedSettingsTest` | `teste.json` oferecido ("Do arquivo teste.json…"), prévia 60 → 30 com o widescreen do jogador mantido, aplicar, restaurar, valor mudado depois mantido; versão 2 e campo desconhecido recusados; "Mali" num Adreno: "Não oferecido neste telefone" | o jogo abrir a 30 FPS (grupo C) |
| 38 | `Item38TouchLayoutsTest` | salvar "Corrida", prévia "2 movidos", aplicar, exportar, importar, recusas de versão 2 e de controle fora da tela, "Só este jogo" | arrastar no editor e o jogo usar o layout (grupo C) |

O `ConfigTransactionsInstrumentedTest` (configuração por JNI/TOML) também roda ali, agora no pacote
`.uitest`.

**Grupo B (precisa de arquivo de jogo, sem iniciar o jogo).** A pasta de jogos do telefone vai como
argumento (caminho do telefone, nunca no git); sem ele esses testes ficam "ignorados", não falham:

```bash
./gradlew :app:connectedUitestAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.gameDir=/sdcard/Games
# opcionais: scratchDir (pasta com uma CÓPIA de um jogo pequeno, que os testes renomeiam e
# devolvem), dlcPackage (um DLC ou title update de um jogo da pasta), scanWholeStorage=true
```

A pasta `gameDir` só é listada e lida; biblioteca, capas, conteúdo e histórico escritos são os do
pacote `.uitest`. Medidas (tempos de varredura) vão para `Download/xendroid-roadmap/results.txt` e
para o logcat (`XendroidRoadmap`).

| Item | Teste | Cobre | Continua manual |
|---|---|---|---|
| 18 | `Item18GameFoldersTest` | a mesma pasta por outro caminho (`/sdcard` ↔ `/storage/emulated/0`) e uma subpasta não repetem jogos; pasta vazia não acrescenta; uma pasta que some mostra "1 pasta de jogos não está disponível…" e o resto fica, e volta quando ela volta; o diálogo marca "a instalação vai aqui" e "não disponível agora"; remover a pasta tira os jogos e não mexe em arquivo | cartão SD de verdade (e a velocidade da volta), revogar o acesso |
| 19 | `Item19CoversTest` | foto 4000×3000 vira capa de no máximo 512 px; foto em pé com giro EXIF sai em pé; arquivo que não é imagem e PNG corrompido dão a mensagem e nada muda; limpar o cache mantém a capa; todos os discos do título a mostram; "Usar o ícone do jogo" a remove | renomear/mover o arquivo do jogo (é do usuário), o atalho, olhar o tile |
| 20 | `Item20MissingGamesTest` (`scratchDir`) | jogo renomeado para `.bak` sai da lista e o aviso "1 jogo … não está mais na biblioteca" abre o diálogo com "Arquivo não encontrado" e o caminho antigo; o nome de volta traz o jogo; a pasta inteira longe (outra pasta disponível) só aparece no aviso de pasta e em ⋮ → "Jogos que saíram" como "pasta não disponível"; pasta tirada da biblioteca = "fora das suas pastas"; "Remover" esconde; nenhum arquivo é apagado | tempo de jogo/relato mantidos (precisa de run, grupo C) |
| 21 | `Item21GameSheetTest` | a ficha (toque longo) diz "N de M ligados" e o número segue um patch ligado; coleções: "RPGs", "rpgs" recusado, segundo jogo, o filtro "RPGs (2)" mostra só eles, apagar mantém os jogos; a coleção vai no pacote de dados ("Coleções — novas: 1…") e volta | TU/DLC na ficha (o item 23 instala um), "Jogado por último como" (grupo C), mover arquivo |
| 22 | `Item22ScanTest` | (a) reaberto, a lista aparece na hora (tempo anotado); (b) com o cache limpo, "Parar" no meio dá "Varredura interrompida…" e "Tentar de novo" termina; (d) com `scanWholeStorage`, o armazenamento inteiro e o aviso de lista parcial; (e) varredura quente de 1.000 e 10.000 arquivos cronometrada | (c) mover uma pasta de jogos, (f) apagar um jogo com o app em segundo plano |
| 23 | `Item23ContentTrashTest` (`dlcPackage`) | instalar pelo fluxo real, "Mover para a lixeira" (some da aba, na lixeira com tamanho e data), "Restaurar", reinstalado → "Restaurar" recusa com "…está instalado de novo…", "Apagar" de vez; com o lease ocupado (jogo aberto) "Feche o jogo em execução antes." e nada se move | o jogo ver ou não o pacote (grupo C), matar o app no meio |
| 33 | `Item33PlayAsTest` | com A como P1 e B como P2: tocar no jogo pergunta "Jogar como" com os dois e B "· P2"; escolher B avisa que vai para P1; Cancelar não inicia nada; a ficha diz "Entra como A" | o jogo abrir com o perfil, "Não perguntar de novo", P2 logado no jogo (grupo C) |

**Grupo C (o jogo roda de verdade).** O teste inicia o jogo no processo `:emu` do pacote `.uitest` com o
mesmo pedido da biblioteca e o conduz como um jogador pelo UiAutomator (Back abre o menu, "Sair do
jogo" e a confirmação, Home, teclas de controle); o que aconteceu é lido do registro do run que o
processo do jogo escreve (o mesmo da ficha). O jogo é `-e game <arquivo>` ou o primeiro de `-e gameDir`
(só lido). O primeiro boot no pacote de teste compila os pipelines (minutos). Argumentos extras:
`playSeconds` (item 9, padrão 120), `longRuns=true` e `runSeconds` (item 34, quatro boots). Evite
espaços no caminho passado ao Gradle (o `am instrument` corta no espaço); com espaços no nome do
jogo, use `gameDir`.

```bash
./gradlew :app:connectedUitestAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.gameDir=/sdcard/Games
```

| Item | Teste | Cobre | Continua manual |
|---|---|---|---|
| 2 | `Item02LeaseTest` | um job de save/conteúdo curto segurando o armazenamento é esperado e o jogo inicia; um longo dá o diálogo "Outra operação de save, perfil ou conteúdo…" e nenhum run começa | o backup automático real disputando o início; o toast "Aguardando…" (toast não é legível pelo teste) |
| 5 | `Item05BackgroundTest` | fonte 1,3× e tamanho de tela +10% com o jogo em segundo plano: na volta é o mesmo processo do jogo e o run segue; os valores do telefone voltam | — |
| 9 e 12 | `Item09RunRecordTest` | jogado (`playSeconds`), Home e volta, Sair pelo menu: "ended normally after", FPS em janelas de 1 s, frame time, áudio, driver, linha do tempo com fundo/frente, Surface e saída, a linha "Last run" na ficha; o relatório e o ZIP compartilhado têm o Title ID e nunca o caminho, o nome do arquivo ou o perfil | se os números fazem sentido para o jogo; avaliar o jogo; "device lost" |
| 10 | `Item10InterruptedRunTest` | SIGKILL no processo do jogo (o que o force-stop faz com ele): o run vira "interrupted", o último sinal de vida é o heartbeat de até 30 s antes e a linha do tempo termina no último evento gravado (o que o heartbeat salvou, como o primeiro quadro, está lá); distâncias anotadas | — |
| 11 | `Item11BootLabelTest` | sem caches: um rótulo antes do primeiro quadro ("Iniciando o jogo…", "Preparando os gráficos: N pipelines…", "Esperando o primeiro quadro…") que some com a imagem, o run com o tempo do primeiro quadro e pipelines criados; com cache: os mesmos números anotados ao lado (primeiro quadro, quantidade e tempo de pipelines) | olhar o rótulo; comparar frio × com cache (o contador do core conta acerto de cache como criação, só mais rápida); as rajadas na linha do tempo |
| 15 | `Item15FrameGenerationTest` | fumaça (build debug; FG continua desligado por padrão): Win-FG 2× ligado pelo menu, a linha de estado anotada (o tempo de GPU não fica parado), a linha de FG do run anotada | qualidade da imagem, LSFG com a DLL do usuário, veredito × engasgo, Forza como controle negativo |
| 25 | `Item25PatchVersionTest` | antes de rodar a versão é desconhecida; um run grava os hashes do jogo; o arquivo do catálogo da versão vira "Para a versão que você jogou por último" e o `xe.log` mostra "Patcher: Applying patch for" | APK novo mantendo um patch ligado e o aviso "Atualizado para os patches…" (duas versões do app) |
| 27 (+16, +28) | `Item27InGameMenuTest` | abas Gráficos · Sistema · Controles · Sessão, linha do driver, RB/LB, D-pad até "Mais opções (N)", A abre e fica aberto ao trocar de aba; em Jogador, 2 avançados em Gráficos, nenhum em Sessão e nada de Win-FG; com o app em pt-BR, abas e "Sair do jogo?" em português | tela 4:3 ou dividida, a fala do TalkBack, o Esc do teclado |
| 34 | `Item34CompareRunsTest` (`longRuns`) | 4 runs ABBA (B com Win-FG 2×), cada um com marcador de cena; comparados como na tela "Comparar execuções": ordem equilibrada, B − A por par e veredito anotados | a mesma cena à mão, esfriar a bateria entre runs, variações de driver e de limite |
| 37 | `Item37VblankResyncTest` | Home por 10 s e volta: "Guest vblank resynced after a stall" no `xe.log`; FPS anotado | comparar a mesma cena com outra build |
| 39 | `Item39ExitByOriginTest` | da biblioteca, "Sair" volta à biblioteca; de fora do app (`am start` com o que o ES-DE/Daijishō mandam: componente, ação e `game_uri`), "Cancelar" no carregamento, depois que o run começou, volta para a tela inicial e o run, externo, diz "cancelled while starting" | frontend real (ES-DE/Daijishō) com a biblioteca aberta atrás; Recentes; "Cancelar" no primeiro segundo (antes de o run existir): fecha sem run e sem iniciar o jogo |
| 42 | `Item42NativeCrashTest` | SIGSEGV no processo do jogo: run FAILED com "native crash: SIGSEGV (code 0) at 0x…, thread '…', pc …" | crash real dentro do `libe.so` × tombstone; erro fatal do core |

**Grupo D (continua manual):** 1 (editar config durante o boot), 6 (frontends reais), 7 (cache Vulkan
entre drivers Turnip), 8 (FG forçado a parar), 14 (dois telefones), 30 (controle Bluetooth), 31
(câmera por toque com o dedo), 32 (teclado do jogo com controle), 36 (catálogo remoto, só com
publicador), 40 (aparelho sem Adreno), 41 (dois controles com vibração) e as partes visuais dos
itens 8, 15 e 37.

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
   Lote 12p: jogar com o perfil A ("Play as"), voltar à biblioteca: a ficha diz "Last played as A"
   (pt-BR: "Jogado por último como A"); jogar com B: passa a dizer B; apagar o perfil B: a linha
   some. "Share last run report" desse jogo não contém o XUID do perfil.
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
24. Lote 9 — dados no gerenciador de arquivos (L07): ⋮ → "Open user data": o app de arquivos
   do sistema mostra a raiz "XenDroid" com config, `content`, `patches`, logs, e **não** mostra
   `cache`, `cache0`, `cache1` nem `content/.xendroid-trash`. Copiar um patch `.toml` para
   `patches/`: aparece; copiar de novo: vira "… (1).toml" (nada é substituído). Renomear e
   apagar um arquivo de teste: funcionam. Com um jogo aberto, tentar copiar/apagar algo:
   falha com erro (o arquivo continua); fechar o jogo e repetir: funciona. Abrir `xe.log` com
   o jogo aberto: lê. Num app de terceiros que pede uma pasta (ex.: um editor de texto com
   "abrir pasta"), conceder a raiz do XenDroid e conferir que ele só vê o mesmo conteúdo.
   Depois de atualizar o APK, um atalho/permissão antiga para a pasta continua abrindo.
   Lote 12r: na raiz aparece "Games by title" (pt-BR: "Jogos por título"): uma pasta por jogo com
   saves ou config, com o nome do jogo já jogado; dentro dela, "Saves and data · profile <XUID>"
   abre a pasta real do jogo naquele perfil, "DLC and title updates" a do console, e aparecem a
   config do jogo e os arquivos de patch. Essas pastas de reunião não oferecem renomear, mover
   nem apagar. Com um jogo aberto, nenhum item oferece apagar/renomear/mover e a raiz diz
   "Read-only while a game runs…"; copiar um save para Downloads funciona; fechar o jogo e
   reabrir a pasta: as opções voltam.
25. Lote 9 — catálogo de patches (L10): com o APK anterior, ligar um patch de um jogo; instalar
   este APK por cima e abrir Game patches: o patch continua ligado e, se o catálogo do jogo
   mudou, aparece "Updated to the patches of this app version… Undo/OK" (Undo volta ao arquivo
   anterior). Editar à mão o `.patch.toml` desse jogo pelo gerenciador (L07), instalar um APK com
   catálogo diferente e abrir a tela: aparece a prévia com "Update"/"Keep mine" e o arquivo não
   muda até a escolha. Jogar com o patch ligado e conferir no `xe.log` "PatchDB: Loaded patches"
   e o patch aplicado. Matar o app logo depois de um toque: o arquivo continua legível.
   Lote 12q: antes de jogar, Game patches mostra "Play the game once here to see which patch files
   are for your version."; depois de jogar uma vez e fechar, o arquivo do catálogo cujo hash bate
   com o jogo diz "For the version you last played" (o `xe.log` mostra "Patcher: Applying patch
   for:" só para patches desse arquivo) e um arquivo de outra versão (ex.: TU diferente) diz "For
   another version of the game: it will not apply to yours".
26. Lote 9 — patches do usuário (L11): Game patches → "+" → um `.patch.toml` válido do mesmo
   jogo: "Added …", aparece "… · added by you" com os patches desligados; ligar um, jogar e
   conferir no `xe.log` que ele foi aplicado. Importar o mesmo arquivo de novo: vira "(1)".
   Importar um arquivo de outro jogo: recusa citando o Title ID; um arquivo com uma escrita sem
   `address` (editar à mão): recusa "Line N: the write has no address" — e o jogo continua
   abrindo. Ligar um patch do catálogo e um seu que escrevam no mesmo endereço: aparece o aviso
   de conflito com o endereço; desligar um: some. "Remove": o arquivo sai da pasta `patches`.
27. Lote 10 — menu em jogo (U01): abrir o menu num jogo: abas Graphics · System · Controls ·
   Session. Graphics mostra modos de tela, escala, TV, (Developer) FG e (lote 12b) "Driver: <driver
   carregado>" — com um driver personalizado que não carrega, "(the selected custom driver did not
   load)"; trocando o driver nos ajustes com o jogo aberto, "(another driver is selected for the
   next start)"; A no item mostra onde trocar. System mostra o limite de
   FPS com "Live limit/Next launch", HUD e taxa de atualização. Com controle: LB/RB troca de aba,
   D-pad desce até "More options (N)", A abre — a seleção fica no item, que vira "Fewer options",
   e os avançados aparecem abaixo; trocar de aba e voltar: continua aberto naquela aba. Em
   Player, "More options" de Graphics mostra só "Stretch next launch" e o filtro de cor, e a aba
   Session não tem o item. Em tela pequena (4:3 ou dividida), a lista rola e o rodapé
   Continue/Exit continua visível.
28. Lote 10 — menu em pt-BR (U02): com o telefone em português (ou Configurações → Apps →
   XenDroid → Idioma → Português (Brasil), Android 13+), abrir o menu num jogo: abas Gráficos ·
   Sistema · Controles · Sessão, "Mais opções (N)", "Sair do jogo?" e os rótulos em português, sem
   texto cortado nas opções de duas linhas; trocar para English: tudo em inglês. Com o TalkBack:
   tocar no "☰" anuncia "Abrir menu, botão"; navegar com o controle anuncia a opção destacada
   como selecionada. Fonte do sistema no máximo: o rodapé Continuar/Sair do jogo continua visível.
   Lote 12h: com o telefone em português, a biblioteca inteira (menu ⋮, ficha do jogo, diálogos,
   assistente inicial, navegador de pastas) aparece em português, sem texto cortado; o bloco técnico
   do último run continua em inglês.
   Lote 12i: Configurações e ajustes do jogo em português (seções, os 19 ajustes do modo Jogador
   com descrição no toque longo, "Salvo para todos os jogos · vale na próxima abertura…", gerenciador
   de drivers, pacote de dados, atualizações, ajustes recomendados); buscar "tela" acha "Tela larga".
   Lote 12j: Perfis (criar, editar, lixeira, outros jogadores), Saves (exportar, importar, restaurar)
   e Conteúdo (instalar um DLC, mandar para a lixeira, lixeira cheia) em português, inclusive as
   mensagens de erro.
   Lote 12k: com o telefone em português, Patches de um jogo (adicionar um .patch.toml, aviso de
   conflito), Testar controles ("Vibração no jogo: padrão (menu do jogo)" → Desligada → Baixa…),
   Comparar execuções, Usar este telefone como controle (um código errado diz "Código errado"; com o
   jogo fechado, "Não deu para alcançar o jogo em …"), Diagnóstico, Sobre, Mapeamento de teclas,
   pausa, troca de disco, a faixa "Iniciando o jogo… N s" antes do primeiro quadro, comprimir um .iso
   e Procurar atualizações aparecem em português; o aviso de pausa do atualizador quebra em duas
   linhas.
   Lote 12l: Controles de toque → editor em português (Paisagem/Retrato, Alinhar à grade, Gerais,
   Salvar e sair); Layouts: salvar um, aplicar outro (a prévia diz "Paisagem: Controles: N movido(s)…"),
   exportar e importar o arquivo; importar um arquivo que não é layout diz "Não importado: Não é um
   arquivo de layout."; nenhum botão da barra cortado em tela pequena.
   Lote 12m: Comparar execuções com A e B marcados mostra o veredito e os avisos em português; Testar
   controles lista "<controle>: conectado/desconectado/reconectado"; no menu em jogo, Controles →
   Telefones como controle mostra "Telefones como controle · Ligado (ative para desligar)" com
   "No outro telefone: Biblioteca → ⋮ → …, código …"; com o Wi-Fi desligado, "… · Desligado: nenhuma
   rede Wi-Fi, ponto de acesso ou Ethernet"; a linha da TV diz "TV · tela do telefone".
   Lote 12s: com 1 jogo fora da biblioteca, o aviso diz "1 jogo que você jogou ou adicionou não está
   mais na biblioteca" (en: "1 game … is no longer"); com 2, "2 jogos … não estão mais"; uma seção
   de Configurações com um ajuste diz "1 ajuste" e as outras "N ajustes"; nenhum "(s)" sobrou nas
   telas traduzidas.
29. Lote 10 — navegação por controle (U04): na biblioteca, A abre o jogo e B volta; em
   Configurações → "Menus: A confirms…" → "Swap": agora B abre e A volta na hora. Abrir um jogo:
   no menu em jogo, B ativa e A fecha/cancela; dentro do jogo os botões continuam os de sempre.
   Segurar o D-pad (e depois o analógico) numa lista longa: move um, para um instante e segue
   num ritmo legível; soltar para na hora. Com um teclado USB/Bluetooth: Enter ativa, Esc fecha o
   menu e, na pergunta "Exit the game?" com "Exit game" destacado, Esc **cancela** (antes saía).
30. Lote 10 — teste de controles (U05): ⋮ → "Test controllers" com um controle Bluetooth:
   aparece o cartão com nome e vendor:product; cada botão acende enquanto segura e fica com ✓;
   mover os analógicos: o ponto anda sobre o círculo e, dentro do círculo pequeno (zona morta),
   "Game gets" mostra 0; puxar LT até a metade: "(pressed)". Desligar o controle: "disconnected"
   e nada fica aceso; religar: "reconnected" e os ✓ continuam. "Vibrate 0.3 s" vibra só ao tocar
   (anotar se o controle não vibra pelo Android). Com DualSense/Switch Pro (Android 12+): linha do
   giroscópio muda ao mexer. Apertar B não volta (é teste); segurar B por 1 s sai; o gesto de
   voltar do telefone também sai.
   Lote 12o: com dois controles ligados, cada cartão diz "In a game started now: P1"/"P2" (em
   pt-BR, "Num jogo aberto agora: P1"); abrir um jogo com os dois: o P1 previsto controla o P1 e o
   outro, o P2 (o menu em jogo, Controls → jogadores, mostra a mesma ordem).
31. Lote 10 — câmera por toque (U07): num jogo em 3ª pessoa com controles de toque, menu →
   Controls → "Touch camera · free right side: On". Deslizar o dedo no lado direito longe dos
   botões: a câmera gira na direção e na velocidade do dedo; parar o dedo (sem soltar): a câmera
   para. Tocar A/B/X/Y nesse lado: continuam sendo botões. Segurar o analógico direito da tela:
   ele manda, sem briga com a câmera. Abrir o menu no meio do gesto e voltar: a câmera não fica
   girando sozinha. Girar a tela (se o jogo permitir): nada fica preso. Anotar se 1,2 dp/ms é
   rápido/lento demais. Lote 12o: no editor de toque → Globals, "Touch camera speed" em 200%
   gira o mesmo deslize duas vezes mais rápido e em 50% pela metade; "Touch camera area" em 30%
   deixa um toque no meio da tela sem girar a câmera, em 70% ele passa a girar.
32. Lote 10 — teclado do jogo (U10): num jogo que pede nome (ex.: criar perfil/save com
   nome), só com controle: o painel mostra a grade com "q" destacado; D-pad/analógico movem
   (segurar: ritmo legível), A digita, L3 deixa a próxima letra maiúscula, R3 vai aos símbolos,
   X/B apagam, LB/RB movem o cursor dentro do texto, Start conclui e o jogo recebe o nome. Num
   prompt com limite curto (ex.: 15), digitar além: para no limite. Digitar um emoji pelo teclado
   do sistema e concluir: o jogo não trava nem mostra lixo. B com o texto vazio cancela e o jogo
   segue sem botão preso. Por toque: tocar no campo abre o teclado do sistema e tocar nas teclas
   da grade também digita.
33. Lote 10 — perfil antes do boot (U11): com dois perfis (Perfis → criar outro), tocar num
   jogo: aparece "Play as" com o ativo marcado; escolher o outro → o jogo abre com ele (conferir
   o gamertag no jogo/dashboard e que os saves são os dele). A ficha passa a dizer "Signs in as
   <outro>". Marcar "Don't ask again": o próximo jogo abre direto; em Perfis, "Ask who plays…"
   desligado; ligar de novo: volta a perguntar. Mandar o perfil ativo para a lixeira e abrir um
   jogo com um só perfil restante: abre com ele, sem erro de perfil. Lote 12c: com três perfis e
   dois controles, Perfis → "Other players" → Player 2 → outro perfil; abrir um jogo multijogador
   local: o P2 entra logado com esse perfil (gamertag na tela do jogo) e os saves dele são os dele.
   Escolher no "Play as" o perfil do P2: o diálogo avisa e o P2 fica sem ninguém. Mandar o perfil do
   P2 para a lixeira: em Perfis, Player 2 volta a "Nobody signs in".
34. Lote 11 — comparar runs (C07): num jogo, fazer 4 runs da mesma cena (≥ 30 s cada), na ordem
   A (FG off) · B (FG on) · B · A, fechando o jogo entre eles e esperando a bateria voltar à mesma
   temperatura; em cada um, Session → "Mark scene…" no começo da cena. ⋮ → "Compare runs" → o jogo:
   marcar os runs A/B: sem avisos, "B − A per pair" com dois números e o veredito. Marcar na ordem
   AABB (ou trocar o driver num run): aparecem os avisos e "Fix the warnings first". Anotar o
   resultado no relatório de FG (é a forma de A/B que o plano pede). Lote 12a: repetir com o
   driver como única mudança (A sistema, B Turnip): sem avisos e "What changed: driver (…)"; mudar
   também o limite de FPS no B: "A and B differ in more than one thing". Cada run da lista mostra
   "limit … · vblank capped · … Hz"; mudar o limite no meio de um run gera o aviso de ritmo.
35. Lote 11 — perfis recomendados (C05): com o Files app (ou `adb push` para
   `/sdcard/Android/data/<pacote>/files/compose/settings-profiles/`), pôr um arquivo
   `teste.json` com `{"format":"xendroid-settings-profiles","version":1,"profiles":[{"id":"teste-30",
   "name":"Teste 30 FPS","titleIds":["<Title ID de um jogo instalado>"],"reason":"teste do mecanismo",
   "settings":{"GPU|framerate_limit":"30","Console|widescreen":"true"}}]}`. Em Ajustes do jogo deve
   aparecer "Recommended settings" com "From the file teste.json…; not reviewed by XenDroid". Definir
   antes, só para esse jogo, `Console|widescreen` = off: a prévia mostra o limite 60 → 30 e
   "Widescreen: stays Off, as you chose". Aplicar: em GPU, "Frame rate limit" do jogo passa a 30 e o
   widescreen continua off; o jogo abre a 30 FPS. "Restore previous settings": o limite volta a seguir
   o global. Repetir mudando o limite à mão depois de aplicar: restaurar mantém o valor escolhido.
   Trocar `"version":1` por 2 ou acrescentar um campo desconhecido em `requires`: o cartão mostra o
   arquivo como recusado. Pôr `"requires":{"gpuContains":["Mali"]}` num Adreno: "Not offered on this phone".
36. Lote 11 — catálogo remoto (C04), só quando houver um publicador: gerar a chave
   (`openssl ecparam -name prime256v1 -genkey -noout -out chave.pem`; pública:
   `openssl ec -in chave.pem -pubout -outform DER | base64 -w0`), montar `payload.json`
   (`{"format":"xendroid-compat-catalog","version":1,"origin":"<URL exata>","sequence":1,
   "generatedAt":<ms>,"expiresAt":<ms>,"titles":{"<Title ID>":[{"status":"PLAYABLE","build":"<versionName
   do APK>","gpu":"<GPU do aparelho>","date":"AAAA-MM-DD"}]}}`), assinar
   (`openssl dgst -sha256 -sign chave.pem -out sig.der payload.json`), publicar o envelope
   (`{"format":"xendroid-catalog-envelope","version":1,"keyId":"k1","payload":"<base64 payload.json>",
   "signature":"<base64 sig.der>"}`) numa URL HTTPS e compilar com `-PxendroidCatalogUrl=<URL>
   -PxendroidCatalogKeys=k1:<pública>`. Ficha do jogo → "Catalog results" → "No catalog downloaded
   yet" → Refresh → "This build and GPU: Playable ×1". Modo avião: a cópia continua. Publicar com
   `sequence` 0: "Catalog not taken: older than the copy…". Mudar um byte do payload sem reassinar:
   "the signature does not match". A chave privada nunca entra no repositório.
37. Lote 11 — VBlank do guest (K11): num jogo que roda a 30 ou 60 FPS travados com "Cap guest
   display refresh" ligado, jogar 5 min a mesma cena que um run anterior (outra build) e comparar na
   ficha a mediana e o 5º percentil de FPS e o limite de 99% dos quadros: iguais ou melhores (nunca
   uma queda para a metade). Ir para a Home por ~10 s e voltar: o jogo segue sem acelerar para
   "recuperar" o tempo, e o `xe.log` tem uma linha "Guest vblank resynced after a stall (1 so far, …)".
   Com o cap desligado, o comportamento ilimitado não muda.
38. Lote 12 — layouts de toque (U06): no editor (biblioteca ou dentro do jogo), mover dois botões,
   "Layouts" → nome "Corrida" → Save; Reset; "Layouts" → Corrida → Apply…: a prévia diz "Controls: 2
   moved"; aplicar e "Save & Quit": o jogo usa o layout. Export → salvar o arquivo; noutro aparelho
   (ou depois de apagar o layout) Import → o mesmo layout volta. Editar o arquivo pondo `"version":2`:
   "Made by a newer XenDroid"; pôr `"x":1.5` num botão: "… is off the screen". Com "This game only",
   salvar e aplicar mexem só no layout do jogo.
39. Lote 12 — sair conforme a origem (U09): abrir um jogo por um frontend (ES-DE/Daijisho) com a
   biblioteca fechada; no carregamento tocar "Cancel": volta ao frontend e o XenDroid não aparece
   vazio em Recentes. Repetir com a biblioteca aberta antes (Home → frontend → jogo): ao sair pelo
   menu (Exit), volta ao frontend e, em Recentes, a biblioteca continua lá. Pela biblioteca, sair
   volta à biblioteca. Por um atalho fixado, sair volta à tela inicial.
40. Lote 12 — driver indisponível (U03), só num aparelho sem GPU Adreno (Mali/Xclipse/PowerVR):
   Configurações → Vulkan → "Custom Vulkan driver" aparece com o título e "Unavailable: custom
   drivers load only on Adreno GPUs…; Games run on the system driver.", sem botão de escolher
   arquivo; o mesmo nos ajustes de um jogo. Num Adreno, nada muda.
41. Lote 12 — vibração por controle (U08): com dois controles, Test controllers → no primeiro,
   "Game rumble" até High; no segundo, até Off. "Vibrate 0.3 s" do primeiro vibra forte e o do
   segundo fica desabilitado. Num jogo com vibração e os dois controles como P1/P2: só o P1 vibra; o
   menu (Controls) mostra "… · P1 (High), P2 (Off)". Mudar o padrão no menu não altera esses dois.
   Desconectar e reconectar o primeiro: continua High.
42. Lote 12n — linha do crash nativo (C01): build debug com um jogo rodando, provocar a falha com
   `adb shell run-as xendroid.compose.debug kill -SEGV $(adb shell pidof xendroid.compose.debug:emu)`
   (no release, `xendroid.compose`/`xendroid.compose:emu` não aceitam run-as: usar um crash real). O jogo
   fecha com tombstone do Android; ao voltar à biblioteca, a ficha mostra "Last run: failed …: native
   crash: SIGSEGV (code 0) at 0x…, thread '…', pc 0x…" (o kill vem de fora, então o código é 0 e o pc
   costuma estar fora do libe.so). Num crash real dentro do core, o "pc libe.so+0x…" da linha bate com o
   "#00 pc …  …/libe.so" do tombstone. Um erro fatal do core (ex.: GPU device lost) continua com as
   palavras do core ("fatal error: …"), não com a linha do crash.
