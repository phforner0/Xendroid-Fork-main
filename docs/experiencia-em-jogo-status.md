# Execução do plano de experiência em jogo

Referências: [plano mestre das cinco referências](plano-evolucao-cinco-referencias.md),
[plano inicial](plano-experiencia-em-jogo.md) e
[objetivo operacional](cloud-goal-experiencia-em-jogo.md).

## Sessão na nuvem — 2026-10-02 (lote 7 em diante)

Ambiente: container Linux da sessão na nuvem (4 vCPU, 15 GB de RAM, sem telefone e sem o
pod de build do usuário), preparado com `bash tools/cloud-setup.sh`: OpenJDK 21.0.12.1 do
Ubuntu 24.04, SDK 35, NDK 29.0.14206865, CMake 3.30.3, glslang/SPIRV-Tools do Ubuntu.
O Gradle precisa de `LC_ALL=C.UTF-8`: com o locale POSIX a JVM não abre
`patches/xenia-canary/patches/4D5307F2 - Viva Piñata.patch.toml` e `:app:syncGamePatches`
falha ("Failed to create MD5 hash … as it does not exist"). O script agora avisa.

| Fatia | Estado | Verificação |
|---|---|---|
| 7a — testes do companion | Impl. + Local | Linha de base `:app:testDebugUnitTest` com o lote 7 já na árvore: **226 testes, 46 suítes, 0 falhas, 0 erros, 0 ignorados** (2m21s a frio). `CompanionProtocolTest` (9) e `CompanionHostTest` (11, clientes reais por loopback) passaram na primeira compilação, sem avisos do compilador nesses arquivos. Ajuste de contrato: um quadro cortado no meio também vira `CompanionProtocolException` (antes `EOFException` crua), com um caso novo no teste; suíte de novo 226/46, 0 falhas |

## Estado atual — 2026-10-01 (auditoria S0)

Auditoria completa do checkout e primeiro lote de correções/implementação. Detalhes,
evidências e roteiro de aparelho: [auditoria-s0-2026-10-01.md](auditoria-s0-2026-10-01.md);
estado por item do plano: seção 6.1 do [plano mestre](plano-evolucao-cinco-referencias.md).

**Base:** `main` @ `77011a0c` com o trabalho de UX/FG. Em 2026-10-02, a pedido do
usuário, todo esse trabalho foi publicado como um commit na branch
`wip/experiencia-em-jogo` (a partir de `77011a0c`; a `main` não foi tocada) para
continuar em sessões na nuvem. `origin/main` está 51 commits à frente (`6c772efb6`,
PRs #4–#12 de desempenho): a integração (A13) continua pendente e é decisão do usuário;
o ensaio de merge do lote 2 não teve conflitos (auditoria §2.8). Nenhum release publicado.

Lote 1 = correções S0 e base de sessões. Lote 2 = relatório por run (C02), flight
recorder (C01), Compatibility Center local (C03), identidade de driver e de jogo.
Lote 3 = criação de pipelines no relatório/linha do tempo e estado do boot (U09, parte).
Lote 4 = áudio no relatório, causa de erro fatal no run e exportação por run (C06).
Lote 5 = verificação estática de JNI e de empacotamento (A08), APK só arm64, pacote de
configurações portável (L08 v1).
Lote 6 = driver pedido × carregado (U03), layouts de toque por jogo (U06), atualizador
com canal/verificação/instalador (R02–R04), jogadores P1–P4 e rumble por controle
(I01–I04).
Lote 7 (em andamento) = companion LAN v1 (I06–I09): protocolo, host, cliente e testes
compilados e passando na nuvem (fatia 7a, seção acima); falta a integração Android (ver
Retomada).

| Frente | Feito neste lote | Estado |
|---|---|---|
| Configuração | `SaveConfig()` nativo desligado no Android (reescrevia o config global a cada boot de jogo sem config por jogo); `SaveGameConfig` atômico; `openLive()` destrutivo removido, criação do config sob lock | Impl. + build; instrumentados novos compilados, não executados |
| Saves / boot | Lease + recuperação antes da Surface, espera limitada e diálogo; recuperação isolada por transação; previews abandonados descartados; backup automático só quando os saves mudaram | Impl. + JVM |
| Perfis (A09) | Prévia dos saves afetados, lixeira atômica restaurável, avatar com decode limitado e escrita atômica, `setActive` sem crash | Impl. + JVM |
| Sessões (S1 base) | `SessionRunStore`: run por boot, finalização única entre processos, reconciliação pela causa de saída; biblioteca com "Recently played" e tempo de jogo | Impl. + JVM |
| Lote 2 — Sessões | Origem do launch (biblioteca/atalho/frontend), heartbeat de 30 s com resumo de desempenho e driver, launch com falha visível gravado como `FAILED` | Impl. + JVM |
| Lote 2 — Relatório por run (C02) | `RunPerformance`: histograma de FPS em janelas de 1 s (pausa/fundo/segundo sem quadro guest novo = ocioso — repaints do host não contam mais como quadro), submissões de present e sintéticas, segundos de FG, bateria início/máx./fim; **histograma de frametime por quadro guest** (1 ms, contador atômico no core) com percentis 99/99,9; mostrado nos detalhes do jogo | Impl. + JVM; contador nativo compilado, não executado no aparelho |
| Lote 2 — Flight recorder (C01 v1) | Anel limitado (200 eventos, detalhe ≤ 160 caracteres) de eventos raros do host por run: lifecycle, Surface, foco, pausa, menu, mudança de estado de apresentação/FG, travamentos sem quadro guest ≥ 3 s, status térmico do Android, trim de memória, controles (só vendor/product, nunca o nome), prompts do guest (sem texto), erros e saída. Gravado ao lado do run a cada heartbeat, ao ir para o fundo e em eventos críticos; ficha do jogo mostra a linha do tempo do último run | Impl. + JVM |
| Lote 2 — Compatibility Center local (C03) | Status Doesn't boot/Boots/Intro/In-game/Playable + nota, escolhidos só pelo usuário, por Title ID; cada relato guarda build, GPU, driver do último run e edição (Media ID)/disco; diálogo nos detalhes; último run descrito à parte | Impl. + JVM |
| Lote 2 — Identidade de driver (S4 base) | Presenter publica vendor/device/driverVersion/API/driverID/nome/info/GPU/UUID do driver carregado, loader sistema/custom e a biblioteca que o adrenotools carregou; `DriverIdentity` guarda só o nome do arquivo e o SHA-256 do pacote instalado, com chave estável, gravada no run e no relato | Impl. + JVM; nativo compilado, não executado no aparelho |
| Lote 3 — Criação de pipelines (C02/C01) | Contadores nativos de pipelines criados e do tempo gasto em `vkCreateGraphicsPipelines` (inclui acertos do cache: é tempo da thread de GPU, não só compilação do driver); resumo do run com total e segundos; na linha do tempo, "rajadas" (segundos com ≥ 100 ms criando pipelines), com o início gravado em disco na hora | Impl. + JVM; nativo compilado, não executado no aparelho |
| Lote 6 — Driver pedido × carregado (U03) | A linha do driver em Configurações compara a seleção com o driver que o último run do escopo (global ou do jogo) carregou de fato: avisa quando o custom não carregou e o sistema foi usado, quando era outra biblioteca, ou que nenhum jogo rodou desde a troca | Impl. + JVM |
| Lote 6 — Layouts de toque por jogo (U06) | No editor dentro do jogo, "This game only" grava um layout daquele Title ID por orientação; sem layout próprio vale o compartilhado; o overlay usa o do jogo em execução; formato antigo continua lendo e o pacote L08 leva os layouts por jogo | Impl. + JVM |
| Lote 6 — Atualizador (R02–R04) | Canal Stable/Preview/Off; release escolhida por `versionCode` com APK único; download limitado com SHA-256 do asset; pacote/versão/certificado conferidos; instalação pelo `PackageInstaller` com confirmação do sistema; "Skip this version"/"Later" | Impl. + JVM (lógica); instalação só testável no aparelho com o pacote `.fork` |
| Lote 6 — Jogadores P1–P4 e rumble (I01–I04) | Driver nativo com 4 slots (P1 inalterado), P2–P4 por controle físico com conexão/desconexão visível ao jogo, roteamento por controle com as regras do P1, release só do controle perdido; rumble do jogo no controle do slot com intensidade Off/Low/Medium/High no menu | Impl. + JVM; nativo compilado; multiplayer real só no aparelho com dois controles |
| Lote 7 — Companion LAN v1 (I06–I09) | `companion/CompanionProtocol.kt` (frames, prova HMAC do código de 6 dígitos, estado completo com sequência, `PadKeys.diff/apply`), `CompanionHost.kt` (bind só na LAN dada, P2–P4, heartbeat 250 ms, timeout 1 s solta input, reconexão começa solta, trava após 10 códigos errados, rumble por slot fora da main thread), `CompanionClient.kt` (envio por thread própria); `ControllerSlots` thread-safe com `connectRemote` (nunca P1). Testes `CompanionProtocolTest` e `CompanionHostTest` (clientes reais por loopback) | Impl. + JVM (2026-10-02, nuvem: 9 + 11 testes passando); falta tela do cliente, ação no menu do host e encaminhamento do rumble |
| Lote 5 — JNI e empacotamento (A08) | `tools/check-jni.py` compara os `native` Java (com herança) com as tabelas `RegisterNatives`, os `Java_*` exportados e os tipos C++ de cada função (autoteste com 7 erros plantados); `tools/check-apk.py` confere ZIP, `JNI_OnLoad` no `libe.so` AArch64, só arm64, bloco de assinatura v2+, as 6 licenças e ausência de DLL/cache/imagem/perfil. Ambos nos workflows (Checks e job de APK). O APK declarava 4 ABIs sem ter o core para 3 delas (PKG-01): agora só arm64-v8a | Impl. + Local (scripts rodados aqui; workflows não executados no GitHub) |
| Lote 5 — Pacote de configurações (L08 v1) | Exportar/importar config global e por jogo, controles de toque, favoritos/ordenação e relatos de compatibilidade; importação validada (formato, checksums, TOML pelo parser do emulador), com prévia e backup do estado atual; caminhos do aparelho nunca saem e são mantidos na importação | Impl. + JVM (núcleo); fluxo SAF não testado no aparelho |
| Lote 4 — Áudio no relatório (C02/C01) | Contadores nativos por processo (AAudio e OpenSL ES): blocos tocados após o primeiro bloco do guest, blocos ocultados porque o emulador atrasou (o que se ouve como falha/estalo) e xruns do stream (só AAudio, acumulados entre reconstruções do stream). Pausa para o stream, então não conta. Resumo "AAudio: N de M blocos ocultados (x%)"; na linha do tempo, rajadas de underrun que só terminam após 5 s sem falhas (picos esparsos não lotam o anel) | Impl. + JVM; nativo compilado, não executado no aparelho |
| Lote 4 — Causa de erro fatal (C01) | No Android o `FatalError` do core (ex.: "Graphics device lost") não mostrava nada e chamava `abort()`: o run ficava só como "native crash". Agora o core grava a mensagem em `<run>.fatal` (temp + rename, ≤ 4 KB) antes do abort, e a reconciliação marca o run `FAILED` com "fatal error: <mensagem>" | Impl. + JVM; nativo compilado, não executado no aparelho |
| Lote 4 — Relatório por run (C06 v1) | "Share last run report" na ficha: prévia de tudo o que vai (aparelho sem identificadores, run, driver, desempenho, linha do tempo, seus relatos de compatibilidade), ZIP com JSON + linha do tempo em texto, caminho do jogo reduzido ao formato, texto livre pelo redator; sem logs (o Diagnostics compartilha esses); sai só pela folha de compartilhamento do Android | Impl. + JVM |
| Lote 3 — Estado do boot (U09, parte) | Rótulo sobre a tela preta até os primeiros quadros guest: "Starting the game… / Preparing graphics: N pipelines created… / Waiting for the first frame…" com segundos; some no primeiro quadro; tempo até o primeiro quadro no resumo e na linha do tempo | Impl. + JVM |
| Lote 2 — Identidade de jogo | `Game.identityKey` (`title:<TID>:<media>:<disco>` ou URI); favoritos por identidade; os antigos por URI continuam valendo e são trocados pela identidade ao alternar | Impl. + JVM |
| Lote 2 — Menu LSFG | Cache LSFG verificado uma vez por (caminho, tamanho, mtime, checksum) em vez de hash de até 64 MB a cada abertura do menu | Impl. |
| Cache Vulkan (A06) | Envelope verificado, limite, gravação atômica, arquivo por identidade de driver | Impl. + teste nativo de lógica |
| FG (A02/A03) | `FrameGenerationSchedule` testável; saídas atrasadas puladas e contadas; cap de FPS devolvido também com menu fechado | Impl. + testes nativos/JVM |
| Lifecycle / launch | `configChanges` completos; URI de frontend repassada ao `:emu` sem fd; aviso de jogo já em execução | Impl. |
| Entrada / privacidade | Bounds check JNI de tecla; stick de toque com prioridade sobre o gyro; redator cobre XUID/gamertag do Xenia | Impl. + JVM |
| Updater / identidade | Feed do fork só para `.fork`, ordem por `versionCode`; `versionName` `<commit>+local.<digest>` | Impl. + JVM |

### Verificação desta entrega (2026-10-01)

Ambientes (sem `-PgitHash` em nenhum: o `versionName` vem de `tools/build-identity.sh`):

- **WSL** (ciclos 1–4): cópia `/home/administrator/xendroid-stage-0929` sincronizada do
  checkout (somente caminhos do `git status`, comparação por checksum), JDK
  `jdk-21.0.12.1+1`, SDK 35, NDK 29.0.14206865, CMake 3.30.3, glslang 16.2.0 /
  SPIRV-Tools 2026.1 (pacotes Debian), `XENDROID_NINJA_JOBS=2`, 3 GB de RAM compartilhados.
- **Pod de build RunPod** (ciclo 5, a pedido do usuário): pod só CPU, 16 vCPU, 32 GB,
  disco local de 30 GB, Ubuntu 20.04. Mesmas versões de JDK (Temurin 21.0.12.1), SDK,
  NDK e CMake; glslang 16.2.0 e SPIRV-Tools v2026.1 compilados do fonte para casar com
  o WSL. Fonte: commit base `77011a0c` baixado do GitHub pelo pod
  (`core.autocrlf=true`, como o checkout Windows) + `rsync -c` da cópia WSL (só os 66
  arquivos de conteúdo diferente subiram); árvore final idêntica à cópia WSL, sem
  `local.properties`, chaves, DLLs nem imagens de jogo. APKs re-assinados localmente
  com a chave debug do WSL (a mesma dos APKs anteriores).

| Comando | Resultado |
|---|---|
| Linha de base antes das mudanças: `:app:testDebugUnitTest` | 117 testes, 27 suítes, 0 falhas (reproduzido) |
| Ciclo 1: `:app:testDebugUnitTest :app:assembleDebug` | BUILD SUCCESSFUL (14m51s, inclui toda a recompilação nativa arm64); 139 testes, 30 suítes, 0 falhas |
| Ciclo 2 (estado final do código): `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | BUILD SUCCESSFUL (7m50s); **150 testes, 32 suítes, 0 falhas, 0 ignorados**; lint 0 erros (50 avisos, nenhum bloqueante; os dos arquivos novos são sugestões KTX) |
| Ciclo 3 (WSL, lote 2 sem os últimos itens): mesmas tarefas do ciclo 2 | BUILD SUCCESSFUL (30m29s); 161 testes, 35 suítes, 0 falhas; lint sem erros |
| Ciclo 4 (WSL) | Interrompido pelo usuário durante o nativo (WSL sem resposta); substituído pelo ciclo 5 |
| **Ciclo 5 (pod, lotes 1–3, estado atual do código):** `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | BUILD SUCCESSFUL (**4m36s**, a frio, inclui todo o nativo arm64); **176 testes, 37 suítes, 0 falhas, 0 ignorados**; lint **0 erros** (50 avisos, 8 dicas) |
| **Ciclo 6 (pod, lotes 1–4, estado atual do código):** mesmas tarefas | BUILD SUCCESSFUL (46 s, incremental: 4 unidades nativas + link); **183 testes, 38 suítes, 0 falhas, 0 ignorados**; lint **0 erros** (50 avisos, 8 dicas) |
| Ciclo 7 (pod reiniciado do zero: setup 93 s + fonte 3 min) | BUILD SUCCESSFUL (4m25s a frio); 183 testes, 0 falhas; `check-apk`: só arm64-v8a (1005 entradas, antes 1011) |
| **Ciclo 8 (pod, lotes 1–5, estado atual do código):** mesmas tarefas | BUILD SUCCESSFUL (38 s, incremental); **189 testes, 39 suítes, 0 falhas, 0 ignorados**; lint **0 erros** (50 avisos, 8 dicas) |
| Ciclos 9–11 (pod, lote 6 em fatias: driver U03, layouts U06, atualizador) | BUILD SUCCESSFUL; 194, 198 e 204 testes, 0 falhas |
| **Ciclo 12 (pod, lotes 1–6):** mesmas tarefas | BUILD SUCCESSFUL; **206 testes, 44 suítes, 0 falhas**; lint **0 erros** (54 avisos). APK debug 45.432.027 bytes, SHA-256 `62d0c40c444a62a57cacace9b8806393e15fdb683f583246579d41adaea5ecd7`, só na saída do build no WSL (não entregue nem instalado) |
| Lote 7 (companion) | Escrito depois do ciclo 12; compilado e testado na nuvem em 2026-10-02 (seção "Sessão na nuvem") |
| `python3 tools/check-jni-selftest.py && python3 tools/check-jni.py` | 8 de 8 casos do autoteste; árvore consistente (ciclo 12): 84 nativos Java = 83 registros + 1 exportado; nota: 11 funções TOML passam ponteiro como `long` (só arm64) |
| `python3 tools/check-apk.py <APK> --aapt2 …` | APK do ciclo 8 aprovado; cópias adulteradas (sem assinatura, com `Lossless.dll`, sem licença, targetSdk errado) reprovadas |
| `XENDROID_NDK=… bash tools/test-native-logic.sh` | `presentation policy: passed`, `pipeline cache file: passed` (executáveis estáticos x86_64 do NDK; no WSL nos ciclos 1–2 e no pod nos ciclos 5–8) |
| `bash tools/test-presentation-host.sh` | **Não executado**: o WSL não tem compilador C++ do host, e no pod não foi tentado (Vulkan/llvmpipe não instalado; o Ubuntu 20.04 traz g++ 9, e o teste pede C++20 e Vulkan 1.3); a síntese Vulkan/llvmpipe (Win-FG/LSFG/filtros) não foi reexecutada nesta sessão |
| Testes instrumentados (`ConfigTransactionsInstrumentedTest`, 10 casos) | APK de teste compilado; **não executado** (sem aparelho nesta sessão) |
| Integração com `origin/main` | Ensaio `git merge-tree`: sem conflitos; repetido no fim do lote 2 contra `6c772efb6` (ver auditoria §2.8) |

**APK atual (ciclo 8):** `C:\Users\Administrator\Downloads\XenDroid_77011a0c+local.f1918ec25d50_Debug.apk`,
45.317.339 bytes, pacote `xendroid.compose.debug`, `versionName
77011a0c+local.f1918ec25d50-debug`, `versionCode 1`, minSdk 29/target 35, código
nativo só arm64-v8a. SHA-256
`3acdb373d5c569f4c9c1c11a1b9708ef4dc27b9c982743ff5bb93445e7887916` (conferido também no
Windows após a cópia). `apksigner verify`: certificado Android Debug (SHA-256
`edebab7b…c626`, o mesmo dos APKs anteriores, então instala por cima). `check-apk`:
aprovado (core AArch64 com `JNI_OnLoad`, assinado, seis licenças, nenhuma DLL, cache
LSFG, imagem de jogo ou dado de perfil). APK de testes:
`XenDroid_77011a0c+local.f1918ec25d50_DebugAndroidTest.apk`, 832.267 bytes, SHA-256
`cf3b39a394f32b3bf9b5bb06358d22419c3bf37795921c11e3ec76f5232dbab1`. O digest
`f1918ec25d50` é o das fontes (sem docs) no início do ciclo 8.
**Não instalado nem testado no telefone.** Substitui os APKs dos ciclos 6
(`…5fc7b1a4466b…`, SHA-256 `f5a222d6…7f82`), 5 e 2.

### Retomada (atualizada em 2026-10-02 para sessões na nuvem)

Trabalho na branch `wip/experiencia-em-jogo`. Ordem pedida pelo usuário: armazenamento/
registro/UX/drivers/updater → slots P1–P4 → companion → estabilização de FG. Lotes 1–6
estão implementados e verificados por build (ciclo 12); o companion está no meio.

1. **Primeiro: fechar o lote 7 (companion).** ~~Compilar e rodar `CompanionProtocolTest` e
   `CompanionHostTest`~~ (feito em 2026-10-02: passam). Depois, a integração Android:
   - Host (`EmulatorHostActivity`, processo `:emu`): ação `PHONE_CONTROLLERS` na página
     CONTROLS do menu em jogo (`InGameMenuState.kt`/`InGameMenu.kt`), desligada a cada
     boot; ao ligar, escolher um IPv4 privado de interface Wi-Fi/Ethernet/hotspot ativa
     (nunca `0.0.0.0`, nem 100.64/10), criar `CompanionHost` com `claimSlot =
     controllerSlots.connectRemote(key)` + `session.setSlotConnected(slot, true, "Phone
     P<n>")` (nome genérico: o nome do telefone pode trazer o do dono), `releaseSlot =
     controllerSlots.disconnect(key)` + `setSlotConnected(slot, false, …)`, `onInput =
     session.keyEventSlot(slot, key, pressed, value)` e `onEvent` no flight recorder;
     rótulo com IP:porta, código, jogadores e latência (e "travado" quando `locked`);
     fechar em `onDestroy`/fim da sessão. No laço de rumble de 50 ms, para slots com
     chave `companion:`, mandar `host.sendRumble(slot, esquerdo, direito)` só quando
     mudar (o `rumbleState()` traz `esquerdo shl 16 or direito`).
   - Cliente (processo principal): tela "Use this phone as a controller" a partir da
     biblioteca: endereço `IP:porta`, código e nome; `connect()` fora da main thread;
     `GamepadOverlay` em paisagem com `PadKeys.apply` → `client.update(pad)`; vibrar com
     `rumbleAmplitude` e a intensidade local; `clientId` estável (16 bytes em
     SharedPreferences); mensagens de `CompanionRejected`/`onClosed` na tela.
   - Docs: este status, §8.2 do plano mestre e um item no roteiro de aparelho da auditoria
     (dois telefones na mesma rede: parear, código errado, desligar Wi-Fi do cliente →
     solta em 1 s, reconectar, rumble).
2. Depois, estabilização de FG (S7) no que não depende do aparelho (observabilidade F08,
   testes de lógica); A/B e gates de FG só com o telefone. Itens L/U/C/K restantes do §6.1
   do plano mestre (L01–L03, L05–L07, L09–L12, U01, U02, U04, U05, U07, U10, U11, C04,
   C05, C07) entram em fatias pequenas pela mesma ordem.
3. Verificação na nuvem: `bash tools/cloud-setup.sh` (ver o objetivo operacional) e
   `./gradlew --no-daemon --console=plain :app:testDebugUnitTest` para cada fatia; o build
   nativo/APK completo quando o ambiente aguentar; `python3 tools/check-jni-selftest.py &&
   python3 tools/check-jni.py` sempre que tocar JNI. O pod RunPod usado até o ciclo 12 é
   do usuário e não está disponível na nuvem.
4. Aparelho (só com o usuário): roteiro da [auditoria](auditoria-s0-2026-10-01.md#4-roteiro-de-validação-no-aparelho-pendente)
   (itens 1–13), testes instrumentados (`am instrument -w -e class
   xendroid.compose.settings.ConfigTransactionsInstrumentedTest
   xendroid.compose.debug.test/androidx.test.runner.AndroidJUnitRunner`); o pacote
   `.debug` precisa que o usuário conceda "All files access" manualmente. Nunca instalar
   por cima do `xendroid.compose.fork.opt` do telefone (ele tem os PRs #4–#12 de
   desempenho que esta base não tem).
5. A13 (integrar `origin/main`) é decisão do usuário; não fazer merge/rebase sem pedido.

## Entrega anterior — 2026-09-30 (histórico)

**Artefato entregue:** `C:\Users\Administrator\Downloads\XenDroid_77011a0c_UX_20260930_Debug.apk`,
~44 MB, versão `77011a0c-ux-20260930-debug`, pacote `xendroid.compose.debug`, minSdk 29.
SHA-256: `604e3c2d93a7fe14b68d3a6ff51aa0977f5c26029dbe1521c488d4a13b5f18d8`.
Cópia idêntica em `app/build/outputs/apk/debug/app-debug.apk`.
Assinatura verificada com `apksigner`, CRC do APK válido e cópia/hash conferidos;
não foi instalado nem testado no telefone nesta rodada. As opções FG ficam
ativas somente em builds debug/developer, desligadas no boot. Releases normais
mantêm esses controles desabilitados até os gates de hardware.

| Frente | Implementação atual | Verificação feita / pendência |
|---|---|---|
| Configuração/menu | Contrato de aplicação para todo schema, pesquisa, FPS session/global/per-game, Title ID nativo, editor de layout dentro do jogo, métricas opcionais, informações do GPU/driver ativo quando expostas pelo Vulkan. | JVM e build; testes JNI anteriores continuam sendo histórico. Input físico e lifecycle completo precisam de aparelho. |
| Diagnóstico | Escolha de sessão no menu e tela fora do jogo; indexação por Title ID, exportação sanitizada de sessão específica ou histórico. | Testes de redator/ZIP, estados de navegação e build; casos de logs reais adicionais pendentes. |
| Saves | Backup de `00000001` e `Headers/00000001`, perfil/XUID opcional, manifesto/hashes, staging, confirmação de conflitos, journal, rollback e recuperação antes do próximo boot. Lock de armazenamento exclui emulação e operações de conteúdo. | Fixtures JVM: round-trip, conflito, erro/process death, recuperação, perfil opt-in, estágio alterado e hash determinístico. SAF/FUSE e saves reais pendentes. |
| Drivers | ZIP limitado, caminhos/CRC/ABI ELF64 AArch64, pacotes imutáveis por SHA-256, verificação de instalação existente e seleção reversível. Novo fluxo substitui o instalador legado na UI. | Fixtures de ABI/hash/traversal/cancelamento e build; load de drivers reais pendente. |
| Apresentação | Fit/Fill/Stretch/Integer live, bilinear/CAS/FSR live, contadores separados, worker host com retenção de imagens e negociação FIFO ao ativar FG. | Políticas C++ e build nativo; integração Android, scanout e latência física pendentes. |
| Win-FG | Motor MIT adaptado ao presenter, 2× experimental, presets, erro/fallback, timing GPU quando disponível. Desligado por padrão. | Síntese 64×64 e leitura de pixels em llvmpipe; sem benchmark de jogo ou Adreno. |
| LSFG | Port GPL autorizado, importação SAF da DLL do usuário, extração/tradução/cache privado, backend comum e seleção explícita 2×/3×/4× experimental. DLL/shaders importados não entram no APK. | DLL local: 25 módulos traduzidos; síntese e readback no llvmpipe para 2×/3×/4×; cache malformado/limites testados. Android/Adreno e qualidade em movimento pendentes. |
| Extensões | Áudio live/mute, Hz compatíveis sem trocar resolução implicitamente, sustained mode, ADPF do presenter, política de background, gyro/calibração/sensibilidade, display externo com retorno ao telefone e filtros SDR. | Compilação/lint e testes de matemática/calibração; serviços/sensores, display/áudio externo e custos dependem de ambiente Android/aparelho. |
| Sincronização | Provedor SAF opt-in, backups remotos imutáveis por hash, leitura de verificação e restore com preview; opção de copiar ao retornar à biblioteca com jogo fechado. | Fixtures de deduplicação e upload incompleto; provedores locais/cloud reais pendentes. |

### Verificação desta entrega

- `:app:testDebugUnitTest`: **117 testes, 27 suítes, 0 falhas, 0 erros, 0 ignorados**.
- `:app:assembleDebug` e `:app:assembleDebugAndroidTest`: **passaram**; APK de testes compilado, não executado nesta rodada sem telefone.
- `:app:lintDebug`: **passou**, após corrigir guards de API ADPF e PixelFormat. Warnings de dependências/deprecações existentes não significam teste em dispositivo. Exceção de lint restrita aos overrides públicos de `Activity.dispatchKeyEvent`, cuja implementação interna AndroidX é anotada RestrictTo.
- `tools/test-presentation-host.sh`: políticas de escala/cadência, síntese Win-FG, três filtros SDR, parser/cache LSFG e síntese LSFG 2×/3×/4× **passaram** em Vulkan por software (llvmpipe LLVM 21.1.8). O relógio LSFG é controlado no teste para que a lentidão do software não seja interpretada como pausa; runtime usa relógio real. Resultados de pixels constantes não provam qualidade em movimento.
- Inspeção do APK: seis assets de licenças/avisos presentes; zero DLLs e zero caches/SPIR-V LSFG importados. A licença do port é documentada em [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md). LSFG-VK v2 CC BY-NC-ND **não** foi incorporado; pesquisa em [lsfg-vk-referencia.md](lsfg-vk-referencia.md).
- Build em `/home/administrator/xendroid-stage-0929`, espelhando os fontes locais, com JDK persistente `/home/administrator/xendroid-toolchain/jdk-21.0.12.1+1`, SDK 35, NDK 29, Ninja limitado a 2 jobs. O JDK antigo em `/home/administrator/jdk21` era symlink para `/tmp` e deixou de funcionar após reinício do WSL.

### Gates que continuam abertos

O código experimental não é um padrão de desempenho aprovado. Precisam de telefone/ambiente Android: controle físico, prompts, superfície/TV e desconexão, áudio, serviços ADPF/sensores, SAF com provedores reais, cadência/scanout, qualidade em movimento, térmica e A/B pareado Win-FG vs LSFG. Melhorias específicas de FP16 e perfis de optical flow, estabilização de cadência e qualquer mudança de prioridade/padrão devem seguir essas medições. Não houve commit, push ou publicação de release.

## Pesquisa e planejamento — 2026-10-01

Concluído o [plano mestre](plano-evolucao-cinco-referencias.md) com X360 Mobile,
Bannerlator, DroidDeck, Eden e GameHub: fontes/revisões, lacunas locais, backlog
priorizado, fases S0–S10, dependências e critérios de implementação/validação.
O próximo trabalho recomendado é S0/A06 (cache Vulkan identificado/atômico),
com A02/A04 (scheduler e recuperação), seguido de sessões/compatibilidade.
Esta entrega é documental; os resultados de build/APK acima são de 2026-09-30.

As seções abaixo preservam o **histórico** das builds anteriores, não descrevem o APK atual.

## 2026-09-29 — início

| Fase | Estado | Evidência / próximo gate |
|---|---|---|
| 0 — inventário | Base implementada, A/B pendente | Rotas de menu/input/setters e persistência mapeadas. Compilação nativa, JNI e testes de configuração reais passaram. O contador guest existente e o novo contador de envios Vulkan foram observados separadamente no HUD do POCO F7 (~30 FPS e ~30 envios/s na introdução de Forza). Sem benchmark de gameplay nem prova de scanout. |
| 1 — menu único | Implementado, validação parcial no aparelho | `ui/ingame/InGameMenu*`: Gráficos/HUD/Controles/Sessão, foco por página, Back/Guide/gesto, L1/R1 e confirmação de saída. Em Forza no POCO F7, Back abriu e pausou o menu; D-pad sintético via ADB atingiu a opção inferior e a lista rolou até o item selecionado; R1 sintético chegou à aba Sessão; Back abriu o share sheet e, após uma correção de interceptação no host, fechou chooser e menu retomando o jogo. Toque na alça abriu o menu com o jogo rodando. **Observação:** swipe ADB iniciado em x=10 na borda do sistema disparou o gesto Android de Back e abriu o menu pausado, não o gesto live da alça; há botão de acesso live funcional. **Pendentes:** controle físico/USB/Bluetooth, prompts guest, ciclo de vida e frontend externo. Componentes antigos permanecem para rollback. |
| 2 — configuração/HUD | Implementação avançada, validação parcial | FPS live continua somente da sessão; ações explícitas salvam o limite atual neste jogo/globalmente ou removem somente o override FPS. Title ID vem do core, não do intent; `4D5309C9` foi observado no menu real. Global/per-game editors aplicam somente chaves alteradas sobre o TOML mais recente, sob lock e replace atômico; 8 testes JNI passaram com preservação de dados desconhecidos e erros. HUD compacto/detalhado com seleção de métricas; coleta suspensa com menu aberto/background. Últimas opções de métricas e lifecycle estão compiladas, não validadas no aparelho. Ainda falta classificar requested/effective/capability de todas as opções do schema, além das ações comuns. |
| 3 — biblioteca/logs | Parcial, share e ZIP real verificados | Busca por nome/Title ID, foco visual nas capas e botão X/Menu para ficha; pacote de diagnóstico em cache para compartilhar via `FileProvider`, achatando sessões anteriores e ocultando padrões de IP, identidade, credenciais e caminhos. Testes JVM com ZIP antigo+atual passaram. Em Forza, Compartilhar abriu o chooser com ZIP sem enviá-lo. Uma cópia temporária do ZIP real foi verificada localmente e removida: CRC válido, 8 entradas (duas sessões antigas + execução atual), ~5,4 MB descompactados, 8.883 marcadores de ocultação e zero correspondências com os padrões de IPv4 ou caminhos `/storage`/`/sdcard` auditados. Isso não prova ausência de todos os dados pessoais; ampliar casos reais e testar navegação da biblioteca por controle físico. |
| 4 — controles/saves/drivers | Parcial, novidades compiladas | Catálogo lê SHA-256 publicado quando presente; download usa ZIP temporário verificado e UI distingue a ausência de hash. Retorno à seleção anterior de driver por escopo global/Title ID, sem excluir binários. Sticks adaptativos per-game opt-in e cleanup de claims/eixos em cancel/resize. Novidades aguardam aparelho. **Pendentes:** backup/restauração de saves e validação/staging/ABI do instalador ZIP legado; desfazer seleção não equivale a manter versões binárias imutáveis. |
| 5 — apresentação/Win-FG | Instrumentação inicial, motor pendente | `vkQueuePresentKHR` aceitos (SUCCESS/SUBOPTIMAL) são contados atomicamente e lidos por JNI como envios ao compositor. Medidor separado observado em Forza. Falta timing GPU, medir scanout, relógio host independente, retenção de dois frames e spike de cadência antes do Win-FG. |
| 5b — LSFG Native | Pendente | Licenças e capacidade Vulkan; depende da infraestrutura de apresentação. |
| 6 — experimentos | Pendente | Gates separados conforme plano. |

### Ambiente e baseline

- `git log -1`: `77011a0c` (plano e ajuste Forza); sem alterações rastreadas em `app/src/main/java/xendroid/compose` ou `docs` no início.
- `bash tools/cloud-setup.sh`: baixou JDK 21; `apt-get` não pôde instalar pacotes porque `sudo -n` exige autenticação e `/opt/android-sdk` não pôde ser criado. Existe toolchain completo em `/home/administrator/xendroid-toolchain`; `local.properties` gerado pelo setup foi apontado para esse SDK (arquivo local ignorado).
- Baseline `:app:testDebugUnitTest`: **passou** antes de alterações no app. Após implementação, `:app:testDebugUnitTest` voltou a **passar**, incluindo novos testes de estado do menu, ZIP compartilhável e SHA-256 de assets GitHub.
- Builds iniciais em `/mnt/c` não concluíram a compilação nativa dentro do tempo limite. Após espelhar o código **com as alterações locais** em `/home/administrator/xendroid-stage-0929` (cópia temporária de build em disco Linux), `:app:assembleDebug` gerou `app-debug.apk` de 42 MB, pacote `xendroid.compose.debug`, minSdk 29; `apksigner verify` confirmou a assinatura debug. Teste real encontrou seleção por D-pad fora da tela e Back interceptado pelo host; ambos foram corrigidos no checkout original, sincronizados para a cópia de build, compilados com `:app:testDebugUnitTest :app:assembleDebug` (**BUILD SUCCESSFUL**) e reinstalados. SHA-256 **final**: `df5a7d578cffbc95b05759c2b2c576dbe40f62c133e70c93d777e402cd97f576`. Cópia idêntica em `app/build/outputs/apk/debug/app-debug.apk` deste checkout (pasta ignorada pelo Git). O APK não contém FG/LSFG.
- Instalação no POCO F7, ADB do Windows `e11d1729` (modelo `25053PC47G`): primeira tentativa `adb install -r` foi recusada com `INSTALL_FAILED_USER_RESTRICTED`; depois que o usuário autorizou "Instalar via USB", o APK debug e suas duas correções posteriores foram instalados via `adb install -r` (**Success**). `pm path xendroid.compose.debug` confirmou o pacote; `am start -W -n xendroid.compose.debug/xendroid.compose.MainActivity` retornou **Status: ok**. Forza foi iniciado no processo `xendroid.compose.debug:emu`; abertura do menu, rolagem por D-pad sintético, R1 sintético, chooser e Back de volta ao jogo foram observados por capturas locais. Após o retorno, o processo `:emu` continuava ativo, o HUD mostrava ~30 FPS no menu inicial de Forza e não havia `AndroidRuntime:E` nas linhas consultadas. São **smoke tests funcionais**, não benchmark de FPS, latência, estabilidade prolongada nem prova de controle físico. As instalações existentes `xendroid.compose` e `xendroid.compose.fork.opt` foram preservadas.

### Validação no aparelho pendente

Preparar APK e roteiro: jogo lançado pela biblioteca e por frontend externo; Back/Guide, gesto do canto esquerdo, D-pad/A/B/LB/RB, prompts de teclado/disco/mensagem, Home/retorno, controlador USB/Bluetooth, overlay touch, FPS efetivo (inclusive 0), HUD compacto/detalhado e saída confirmada. Na biblioteca: pesquisa e tecla X para ficha. Compartilhar o ZIP e conferir seu conteúdo antes de publicar. Registrar jogo, dispositivo, driver, Hz e resultados reais antes de marcar qualquer fase como validada. Para FG/LSFG, dependência do presenter e do A/B pareado conforme plano; não há motor novo implementado.

## Continuação — configuração segura e novas funcionalidades

- **Configuração:** novos `ConfigFileTransaction`, APIs JNI de remover chave/consultar tabela vazia e `InGameConfigRepository`. Removido o rebuild destrutivo baseado só no schema; snapshots são mesclados por chaves editadas, sob lock de arquivo entre processos e replace atômico. Falhas de parsing/gravação são mostradas na UI e não resetam o arquivo no editor. O flush final por jogo deixa de depender de um `viewModelScope` já cancelado.
- **Menu:** cabeçalho/abas/Continuar/Sair fixos, página e foco lembrados ao reabrir; salvamento FPS explícito por jogo/global e herança. Abertura live, pausa de menu e pausa de lifecycle têm ownership separado. Novas rotas de Home/foco/surface estão compiladas, mas a rodada manual foi interrompida a pedido do usuário antes da validação completa.
- **Biblioteca:** favoritos e ordenação persistidos no DataStore; filtro de favoritos, Play e favorito na ficha, restauração de foco/grid, teclas A/X/Y para jogar/ficha/favorito e A/B traduzidos para as teclas padrão de clique/voltar nas telas da Activity principal. Não foi validado comportamento com todos os controles físicos ou janelas de diálogo.
- **Driver:** histórico da seleção em preferências com chave por escopo. Pode alternar com o anterior/sistema no próximo boot se o arquivo ainda existe. O instalador legado ainda não garante extração limitada/ABI nem evita sobregravar um driver importado com o mesmo nome; isso continua uma entrega separada.
- **Controles/HUD:** sticks adaptativos desligados por padrão e salvos pelo Title ID; zonas próximas às posições do layout, prioridade de botões/D-pad e um dono por stick adaptativo; limpeza em cancel/resize. HUD detalhado permite desligar fontes individualmente e não mantém percentuais GPU antigos quando a leitura falha. Essas alterações não receberam novos testes após a solicitação de pausa dos testes.

### Evidência antes de o usuário pedir para pular os testes

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL** na cópia de disco Linux.
- APK com configuração segura `525089e4e74fe64d35575a826b954c4d4435218325db7d4451fb6187f2a0dc7a` foi instalado no POCO F7. APK instrumentado instalado após autorização de "Instalar via USB". Comando `am instrument -w -r -e class xendroid.compose.settings.ConfigTransactionsInstrumentedTest xendroid.compose.debug.test/androidx.test.runner.AndroidJUnitRunner`: **OK (8 tests)**, usando somente fixtures no cache privado. Nenhum save foi acessado por esses testes.
- Casos: tipos/tabelas desconhecidos preservados, arquivo realmente vazio removido, editores desatualizados mesclados, TOML inválido mantido e mudança pendente reaplicável após reparo, herança FPS isolada e Title ID sem escape de diretório.
- Contador Vulkan validado no build anterior `32e2a0b24f152565379aab8d8f4471ffdfdb36b09b8e53e873640ab92817a7e0`: aproximadamente 30 FPS guest/30 envios Vulkan por segundo na introdução. A UI do build com scopes mostrou Title ID nativo e valores globais/por jogo. A validação do salvamento por UI foi encerrada; enviado o comando para voltar à herança global e fechar o menu, desfazendo o ajuste FPS temporário.

### Estado após “vamos pular os testes”

Prosseguida a implementação sem novas rodadas de testes no aparelho ou de suíte JVM. Feitas compilação e revisão dos diffs. O build mais recente executou:

```bash
env JAVA_HOME=/home/administrator/jdk21 \
  PATH="/home/administrator/xendroid-toolchain/usr/bin:/home/administrator/jdk21/bin:$PATH" \
  XENDROID_NINJA_JOBS=2 ./gradlew --no-daemon --console=plain \
  -PgitHash=77011a0c-ux-dev :app:assembleDebug
```

**BUILD SUCCESSFUL**, versão `77011a0c-ux-dev-debug`, pacote `xendroid.compose.debug`.
APK atualizado: `app/build/outputs/apk/debug/app-debug.apk` (ignorado pelo Git),
SHA-256 `6d2f52c371b0a18fbb1e69eeb3b1c7d1963055ae75661e5179be1c4bbbc9e2d9`.
Ainda não foi instalado esse último build; o telefone mantém a versão anterior com scopes/testes JNI. Não há Win-FG/LSFG funcionando nesta build, nem restauração de saves.

**Próximas entregas:** backup/restauração com manifesto/XUID e staging; instalador de driver limitado e verificação ABI; classificação completa de capabilities; depois spike de cadência Vulkan e integração Win-FG. LSFG segue condicionado à licença, DLL do usuário e apresentação comum. Reativar a validação em aparelho/fixtures antes de considerar satisfeitos os gates de dados e FG.
