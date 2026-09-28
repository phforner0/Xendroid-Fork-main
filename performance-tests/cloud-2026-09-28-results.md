# Sessão na nuvem 2026-09-28 — bugs, opções novas e fila de A/B

**Branch:** `claude/pensive-franklin-7e25ew`. **Base:** `plano-proximos-passos.md`,
`ab4-results.md`, `ab3-results.md`, `ab3-optimization-ideas.md`,
`ab2-results.md`.

Sem celular: nada aqui é ganho medido no aparelho. Os números de shader são
**contagens estáticas de instruções ir3** do Turnip para Adreno 830 (Mesa
upstream, sem GPU, ver `tools/ir3_offline/`), e as estimativas de tempo são
derivadas da relação instruções → tempo que o AB4 mediu. Cada opção nova
nasce desligada; nenhuma muda outros jogos.

## Resumo

- **16 bugs corrigidos** em commits separados (1 alto só com
  `vulkan_in_pass_resolve`, 6 médios — um deles na ferramenta de A/B —, o
  resto baixo ou de diagnóstico) e 15 achados documentados sem correção, com
  proposta.
- **Três opções de pixel shader** prontas para A/B com reinício:
  `spirv_texture_sign_branch` (exata, conversão gama só num ramo uniforme),
  `spirv_fast_precision_rounding` (exata, −6,2% de instruções nos pixel
  shaders) e `spirv_ps_relaxed_math` (bits 1/2/8; 11 mediu −6% no passe
  principal no AB4). Mais `spirv_vs_math_experiment` para medir o teto dos
  vertex shaders.
- **Instrumentação nova, custo zero desligada:** resolve dividido em cópia ×
  clear, `VkMiscTime` (cargas de textura por formato/tamanho/origem e uploads
  da memória compartilhada — os ~10 ms fora de passes), `VkPassEnd` (o que
  encerra cada render pass), `TexSigns` e `AlphaModes` (decidem 1.5 e 1.6),
  resultado do turbo KGSL no log e estado de energia da GPU no roteiro de A/B.
- **Primeiro passo no aparelho:** a execução de diagnóstico (fila, item 0) e
  os A/B das duas opções exatas (itens 1 e 2).

## 1. Ambiente e o que foi compilado

- `tools/cloud-setup.sh` e o configure do Gradle funcionaram (o Maven Central
  respondeu HTTP 429 algumas vezes; repetir resolveu).
- **Biblioteca nativa (`libe.so`, arm64-v8a, Release): compilada e linkada**
  com o ninja do configure (`emulator-core/.cxx/Release/*/arm64-v8a`) depois de
  cada mudança nativa, até o último commit. Só avisos pré-existentes
  (`user_data.h`, `u8path`).
- **APK não empacotado:** `:app:assembleRelease` parou no HTTP 429 do Maven
  Central (POM do `kotlin-stdlib`) e, antes, num nome de arquivo não UTF-8
  (patch do Viva Piñata) — `LC_ALL=C.UTF-8` resolve o segundo. Nenhum código
  Kotlin/Java mudou; o build local normal gera o APK.
- **Tradutor SPIR-V no host (x86_64)** com a ferramenta de corpus: os 483
  shaders do cache do Forza (221 VS + 262 PS) traduzem sem falha e passam no
  `spirv-val --target-env vulkan1.0` em todas as variantes (base, cada opção
  nova, combinação), e o Turnip compila todos.

## 2. Bugs corrigidos

Linhas no estado final do branch.

| # | Sev. | Onde | Cenário | Correção | Commit |
|---|---|---|---|---|---|
| 1 | alta (só com `vulkan_in_pass_resolve`, desligado por padrão) | `gpu/vulkan/vulkan_texture_cache.cc:1461` | `MarkResolveDestWritten` não marcava a textura promovida como usada; uma textura gravada todo frame pelo resolve no passe, mas amostrada por outras chaves, envelhecia no LRU e era destruída (imagem, view, memória) com submissões ainda gravando nela: use-after-free | `MarkAsUsed()` | `31cab647` |
| 2 | média (caminho padrão) | `gpu/shader.h:800,854`, `gpu/shader_translator.cc:704`, `gpu/spirv_shader_translator.cc:1266` | `is_translated_` era `bool` comum, gravado antes de `is_valid_` e dos bindings, sem release/acquire. Com compilação assíncrona (padrão), uma thread de criação podia ver o shader "traduzido" e tomá-lo por inválido (slot de pipeline nulo pela sessão) ou ler bindings vazios (layout incompleto); no ARM64 podia ver a flag antes do binário | flags atômicas, `is_translated_` gravado por último com release; tradução concorrente espera os bindings. SPIR-V idêntico (483/483) | `01d57d4e` |
| 3 | média | `gpu/command_processor.cc:454`, `vulkan_command_processor.cc:247` | `debug.xendroid.readback_resolve` passava por `SetReadbackResolveMode`, que grava o config por jogo: o braço de um A/B virava configuração permanente (e reescrevia o arquivo do editor pela thread da GPU; uma falha de escrita lança exceção ali) | parâmetro `persist` falso no caminho da propriedade | `a83088bf` |
| 4 | média (rara) | `vulkan_render_target_cache.cc:2827` | com `vulkan_in_pass_transfers` (padrão), se o `IssueDraw` falha depois do `Update()` e o próximo pacote é um resolve, o resolve lia o alvo antes da cópia enfileirada (textura resolvida velha; com clear, o `Update()` seguinte copiava o dado velho por cima) | o resolve executa as transferências pendentes antes | `babac108` |
| 5 | média (rara) | `vulkan_render_target_cache.cc:1648` | a fila de transferências no passe guarda ponteiros crus; um `ClearCache` entre o draw que falhou e o próximo `Update()` deixava ponteiros para render targets apagados | esvaziar a fila no `ClearCache` | `7db65ecf` |
| 6 | média (só com `debug.xendroid.fence_collect=1`, experimental) | `vulkan_command_processor.cc:5392` | no modo limitado a submissão concluída em cache só avança até o alvo do throttle; um destino resolvido a cada 1–2 frames nunca era relido e a RAM do guest ficava com o dado da primeira leitura: o braço mudava o que o jogo lê | destino sem leitura há `kMaxFramesInFlight` frames espera sua última escrita (só no modo limitado) | `72123eb7` |
| 7 | baixa–média (só com resolve no passe) | `vulkan_texture_cache.cc:609` | `kResolveDestStorage → kResolveDestStorage` tem máscaras e layout iguais, e o `skip_if_equal` padrão descartava a barreira entre o `imageStore` no passe e a amostragem | barreira sem `skip_if_equal` | `b48e87ad` |
| 8 | baixa | `gpu/pm4_command_processor_implement.h:935,1091,1161` | `WAIT_REG_MEM`, `REG_TO_MEM` e `COND_WRITE` indexavam o arquivo de registradores (0x5003 entradas) com o número vindo do stream PM4, com só `assert` (removido no Release): pacote malformado lia até 16 GiB além | como `WriteRegister`: aviso e valor 0 | `df32c540` |
| 9 | baixa | `cpu/backend/a64/a64_seq_memory.cc:344`, `a64_backend.h:104` | o estado da espera estacionada ficava em `thread_local`, mas fibers migram entre threads: uma fiber podia herdar a geração vista por outra e dormir até 500 µs com o fence já escrito; após migrar, o compilador reutilizava o ponteiro TLS de antes da troca | estado no `A64BackendContext` da fiber (`static_assert` ≤ 256 bytes) | `7aa8fd6f` |
| 10 | baixa | `kernel/guest_scheduler.cc:838` | o teste de migração do `YieldCurrentThread` lia `t_current_cpu` depois da troca, mas a função lê o ponteiro TLS uma vez na entrada (conferido no disassembly da `libe.so`); `NtYieldExecution` dizia que nada rodou e a espera estacionava em vez de reverificar | comparar `links.cpu` | `e048e2e0` |
| 11 | baixa (diag.) | `pm4_command_processor_implement.h:951` | `wrm_log` imprimia `final=` sem troca de endianness | valor trocado | `b518b047` |
| 12 | baixa (diag.) | `vulkan_render_target_cache.cc:2805` | falha cedo no `Resolve()` deixava `last_resolve_key_` do resolve anterior: tempo no balde errado de `VkResolveTime` | zerar na entrada | `b33ce20f` |
| 13 | baixa | `vulkan_render_target_cache.cc:4034` | slots de cor não usados abaixo do último usado sem `sType` (VUID-VkRenderingAttachmentInfo-sType-sType) | `sType` em todos | `41ffbbf0` |
| 14 | baixa (não afeta Android) | `ui/vulkan/vulkan_presenter.cc:1121` | superfície com um único formato qualquer caía no caminho "aceita qualquer formato" | só `UNDEFINED` sozinho | `676d1b9f` |
| 15 | baixa | `ui/vulkan/vulkan_device.cc:1384` | `VK_INCOMPLETE` da segunda `vkGetDeviceFaultInfoEXT` (binário do fornecedor omitido) tratado como falha: perda de dispositivo sem descrição | aceitar `VK_INCOMPLETE` | `4613b2cf` |
| 16 | média (ferramenta de A/B) | `tools/fh_auto.sh` | propriedades `debug.xendroid.*` duram até o reboot e vencem o config por jogo após 30 frames; um A/B com reinício depois de um A/B em tempo real tinha todos os braços forçados ao último valor | o roteiro limpa as propriedades antigas antes de relançar e a sua ao fim | `f0192af3` |

## 3. Achados não corrigidos (com proposta)

| Sev. | Onde | Achado | Proposta / por que não |
|---|---|---|---|
| média, depende do título | `vulkan_command_processor.cc:5009` | as transferências de render target codificadas **dentro** do passe do guest rodam com o segmento de query de oclusão (ZPD) aberto e contam amostras na query do jogo (confirmado por verificação adversarial) → flares/visibilidade errados | suspender/retomar o segmento em volta de `EncodePendingDrawPassTransfers`. Não aplicado: precisa de um título com queries de oclusão para validar |
| média, driver do sistema | `vulkan_command_processor.cc:~5385` | em drivers cujo `vkGetFenceStatus` não bloqueia, com a GPU limitando, resolves por frame nunca são relidos no modo `uma` (a última escrita é sempre mais nova que a concluída); no Turnip a releitura ainda acontece a cada ~3 frames, quando o throttle drena | o mesmo teto de `72123eb7` sem exigir o modo limitado (acrescenta uma espera). Não aplicado: muda o tempo do caminho padrão |
| baixa (não padrão) | `deferred_command_buffer.cc:145`, `vulkan_pipeline_cache.cc:77` | com `vulkan_async_skip_draws=false` a submissão espera a criação dos pipelines, mas os draws gravados com handle nulo são descartados no replay mesmo assim; a descrição da opção ("nenhum draw é perdido") está errada. O padrão do Android é `true` | gravar o ponteiro do slot e resolver no replay quando o layout não mudou, ou não esperar |
| baixa | `PollDebugPropertyOverrides` | apagar uma `debug.xendroid.*` não restaura o valor do config (fica até relançar) | guardar os valores do config e restaurar quando a propriedade esvazia; o roteiro já limpa |
| baixa (diag.) | anéis de timestamps | com ≳11 submissões/frame junto de `fence_collect` limitado, o anel é reutilizado antes da leitura | anel maior ou leitura antes da reutilização |
| baixa (desempenho) | PM4 | `NotifyGuestGpuProgress` (acorda a espera estacionada) não é chamado em escritas de `REG_TO_MEM`, `COND_WRITE`, ZPD e interrupção: uma espera estacionada nelas dorme até o timeout (≤500 µs) | chamar nessas escritas |
| baixa (latência) | `a64_seq_memory.cc` | fiber pronta durante um estacionamento espera até 500 µs | acordar pelo agendador |
| baixa | `pm4_command_processor_implement.h:434` | pacote tipo 1 lê 2 palavras sem checar o restante; handlers de tamanho fixo ignoram `count` (só `assert`) → stream malformado é mal interpretado (sem leitura fora da memória: o leitor dá a volta no anel) | checar `count` |
| baixa (diag.) | BinTrace | endereço do `SET_BIN_*` errado quando o pacote termina no fim do anel/IB; string de exemplos por endereço sem limite | — |
| baixa | `guest_scheduler.cc` | o risco "TLS lido depois da troca de fiber" é geral; só `YieldCurrentThread` lia TLS depois de `YieldToScheduler` no mesmo frame | auditoria; acessores `noinline` após trocas |
| baixa (só escala > 1) | `vulkan_render_target_cache.cc` | resolve direto ignora a classe de escala nativa; falha do buffer escalado escreve além do intervalo; `copy_dest_info_out` não inicializado nos caminhos no passe/direto; chave de transferência do `Preflight` difere | — (o celular roda em escala 1) |
| baixa | `vulkan_pipeline_cache.cc` | `Shutdown` salva o `VkPipelineCache` antes de juntar os workers e trata `VK_INCOMPLETE` como falha; `vulkan_pipeline_ir_dump` não vazio desliga o cache de todos os pipelines e colide nomes de arquivo | ordem do shutdown; aceitar `VK_INCOMPLETE` |
| média (só resolve no passe) | `vulkan_texture_cache.cc` | `TryServeFromResolveDest` pode servir dado velho depois de um resolve que não grava; wrapper 3D-como-2D destruído depois de uma carga gravada (corrida estreita) | — |
| baixa (upstream/desempenho) | `texture_cache.cc`, `shared_memory.cc` | `~Texture` lê handles de watch sem o lock global; `textures_to_load` com duplicatas; varredura morta da fila de promoção; atalho de chunk sujo × `clear_memory_page_state`; filtrabilidade rápida rejeita 1D largo; mips escalados; offset de downloads no trace | — |
| a verificar | turbo KGSL | o estado de `KGSL_PROP_PWRCTRL` é do dispositivo; se o kernel o mantém depois que o app fecha, "Force max clocks" deixa a GPU forçada após sair (bateria, calor, protocolo de resfriamento) | a linha nova "GPU power with the app stopped" mostra; se `force_clk_on=1` com o app fechado, chamar `adrenotools_set_turbo(false)` ao sair |

## 4. Mudanças

Opções de tradução (seção `[GPU]` do config por jogo
`config/4D5309C9.config.toml`, lidas ao traduzir shaders → A/B **com
reinício**; o cache de pipelines no disco guarda descrições, não SPIR-V, e o
`VkPipelineCache` do driver é indexado pelo SPIR-V, então trocar a opção
retraduz). Estimativa de tempo: o AB4 mediu −12% no passe principal com o
bit 4 de `spirv_ps_math_experiment`, que remove 30,5% das instruções offline,
e −6% com 11, que remove 18,7% — ou seja, **cada 1% de instruções removidas
vale ~0,3–0,4% do tempo do passe principal**.

### 4.1 `spirv_texture_sign_branch = true` (plano 1.1) — exata

- **O que faz:** em vez do `switch` por componente sobre o sinal da textura
  (que o NIR achata, então a conversão gama PWL roda sempre e é descartada),
  usa `select` para os sinais baratos (signed, biased) e um `if` uniforme
  `DontFlatten` sobre "algum componente usado é gama". Mesma imagem.
- **Offline (262 PS):** caminho sem gama −21,2% de instruções (mediana 0,799);
  caminho gama +4,9% (o ramo custa ~3,5 instruções por fetch); empate com
  ~77% dos fetches gama. Teto do AB4 para toda a semântica de sinais (bit 4):
  −12% no passe principal (17,4 → 15,3 ms).
- **Esperado:** −8% (nenhuma textura gama) a −3% (metade gama) no passe
  principal, 1,5–0,5 ms/frame dos 18,5 ms; a linha `TexSigns` (item 0 da
  fila) dá a parcela. Com ≳75% de texturas gama o ramo não paga e o caminho é
  o 1.5 (variante por draw), que precisa ampliar a chave de modificação.

### 4.2 `spirv_fast_precision_rounding = true` — exata

- **O que faz:** o arredondamento para 21 bits de mantissa depois de
  exp/log/sqrt/rsq/rcp vira `(bits + meio ulp) & máscara`, mantendo o valor
  truncado só quando isso viraria infinito a partir de um finito. Resultado
  idêntico ao atual para **todas as 2³² entradas** (checado
  exaustivamente para 1, 10, 21 e 22 bits); 5 instruções Adreno em vez de 12.
- **Offline:** −6,2% de instruções nos pixel shaders, nenhum maior.
- **Esperado:** −2% a −2,5% no passe principal (~0,4 ms/frame). Candidata a
  padrão global depois do A/B (resultado bit a bit igual) — recomendação, não
  aplicada.

### 4.3 `spirv_ps_relaxed_math = <bits>` (plano 1.2) — muda a matemática

- **Bits:** 1 = sem "0 × qualquer = 0" do SM3 (jogos que dependem de
  0 × Inf = 0 podem mostrar pixels pretos/brancos); 2 = sem o arredondamento
  de 21 bits; 8 = FMA (sem efeito no código do Turnip).
- **Offline:** 1 → −4,8%; 2 → −13,3%; 8 → 0%; 11 → −18,7% de instruções.
- **Medido no AB4 (como `spirv_ps_math_experiment`):** 11 = −6% no passe
  principal sem diferença visível nas cenas testadas.
- Com a 4.2 ligada, o bit 2 só remove o que sobrou do arredondamento (−7,5%
  estático a mais); o A/B da fila mede isso.

### 4.4 `spirv_vs_math_experiment = 11` (plano 4.1) — só medição

Aplica os bits aos vertex shaders: −7,8% de instruções nos 221 VS. Posições
podem deixar de coincidir entre passes (z-fighting): **não usar para jogar**.
Serve para saber quanto o VS pesa antes de investir na fase 4.

### 4.5 Instrumentação (ligada só com as opções de log)

Com `log_gpu_frame_time_breakdown_passes = true` (o `-Passes` do script):

- **Resolves (2.1):** cada resolve ganhou um terceiro timestamp no fim da
  cópia; `VkResolveTime ... | copy X ms/fr clear Y ms/fr`.
- **Fora de passes (2.4):** `VkMiscTime` — cargas de textura (untiling +
  cópia) por formato, tamanho (2^N texels), origem (`gpu` = faixa escrita por
  resolve, `cpu`), base/mips; e o command buffer de setup (uploads da memória
  compartilhada). Anel de timestamps 96 → 192 pares por submissão.
- `tools/forza_passres.py` mostra as tabelas (resolve com cópia/clear,
  `VkMiscTime`, fins de passe).

Com `log_gpu_frame_time_breakdown = true`:

- **Fins de render pass (2.6):** `VkPassEnd: per frame: render_targets=…
  resolve=… textures=… shared_memory=… primitives=… query=… submission=…
  other=… | barriers: buffer=… image=… both=…`.

Com `adb shell setprop debug.xendroid.pm4_bin_trace N` (N frames):

- **`TexSigns`** por passe: parcela de texturas unsigned/signed/biased/gama
  vinculadas pelos pixel shaders (decide 1.1 × 1.5).
- **`AlphaModes`** por passe: draws especializados sem alfa, só teste alfa,
  alpha-to-coverage e genéricos por outro motivo (decide 1.6).
- `tools/forza_pipestats.py` mostra as duas por passe.

Turbo da GPU (3.1):

- O log do emulador diz se o pedido `KGSL_PROP_PWRCTRL` foi aceito:
  `GPU clocks: KGSL power control forced on at the maximum clock` /
  `returned to the kernel governor` ou `... failed: <errno>`.
- `fh_auto.sh` registra `gpuclk`, `max_gpuclk`, `thermal_pwrlevel`,
  `max/min_pwrlevel`, `force_clk_on` e o governor com o app parado, na tela de
  título, em cada amostra sustentada e no fim de cada braço ("?" se o sysfs
  não for legível pelo `shell`).

### 4.6 Ferramentas

- `tools/ir3_offline/`: tradutor no host + `spirv-val` + Turnip sem GPU;
  contagens por shader e comparação de variantes. README com o setup do Mesa.
- `fh_auto.sh`/`forza_auto_ab.ps1`: limpeza de propriedades antigas, estado de
  energia KGSL; a documentação do `-Config` diz que o relançamento substitui o
  config por jogo (as configurações comuns aos braços vão no `-Config`).

## 5. Itens do plano

| Item | Estado |
|---|---|
| 1.1 sinal/gama em ramo uniforme | opção `spirv_texture_sign_branch`; A/B pendente |
| 1.2 matemática relaxada | opção `spirv_ps_relaxed_math` + `spirv_fast_precision_rounding` (exata) |
| 1.3 laço principal sem saltos | medido offline: sem o laço `DontUnroll` nos shaders sem rótulos, laços no FS 259 → 43, instruções +0,0% (FS) e −0,4% (VS). Não compensa; não commitado |
| 1.4 chave de modificação cheia | mantidas opções de tradução com A/B com reinício: as opções são globais do tradutor, e variantes alternáveis em tempo real dobrariam os pipelines e exigiriam novo `kVersion`. Ampliar só se o 1.5 for necessário |
| 1.5 variante por sinal de textura | depende de `TexSigns` |
| 1.6 variante só teste alfa | contadores `AlphaModes` prontos; decidir com a parcela `test_only` do passe principal |
| 1.7 `precise_interpolation` | sem efeito no aparelho: o tradutor só emite com `VK_KHR_fragment_shader_barycentric`, que o Turnip (Mesa main) não expõe. Se o log do aparelho listar a extensão, reavaliar |
| 2.1 cópia × clear | feito (`VkResolveTime`) |
| 2.2 resolve de profundidade | aguardando a divisão cópia × clear. Estimativa grosseira do shader direto (8 pixels por thread, 24–48 divisões inteiras e 16 fetches por thread): ALU ≈ 0,03–0,1 ms e tráfego ≈ 2,4 MB para 512×512 — longe do 1,17 ms medido, o que aponta para custo fixo (barreiras, transição do depth, clear), não para o shader (hipótese) |
| 2.3 clear das faixas | decidir com a divisão: se o clear dominar, fazer no `loadOp` do passe seguinte |
| 2.4 os ~10 ms fora de passes | feito (`VkMiscTime`) |
| 2.5 resolve direto para textura | decidir com `VkMiscTime` (cargas `gpu`) |
| 2.6 motivos de fim de passe | feito (`VkPassEnd`); barreiras por intervalo se `barriers: buffer` dominar |
| 3.1 turbo | diagnóstico (log do pedido + estado KGSL); reaplicar periodicamente só se o log mostrar o turbo perdido (sem evidência hoje) |
| 3.2 Turnip mais novo | A/B manual (troca do driver no app) |
| 4.1 teto do VS | opção `spirv_vs_math_experiment` |
| 4.2 fetch de vértice | não iniciado (grande) |
| 4.3 preâmbulo por draw | medido offline: média de 327 instruções de preâmbulo por FS (mediana 302), uma vez por draw, contra ~990 por pixel (mediana 952). Pouco valor; não implementado |
| Fase 5 (CPU) | não iniciada: a GPU limita |

## 6. Fila de A/B (priorizada)

Cena: carro parado na defensa a 2,5 mi; braços de 40 s intercalados; sem
tocar no celular; sem teste térmico longo. Configuração comum (vai no
`-Config`, porque o relançamento substitui o config por jogo):

```powershell
$cfg = '[Vulkan]\nrender_target_7e3_as_r11g11b10 = true\n[CPU]\nspin_park_guest_functions = \042829F04A8\042\nspin_park_mode = 1'
$adb = 'C:\Users\Administrator\Downloads\scrcpy-win64-v4.1\adb.exe'
$py = 'C:\Users\Administrator\AppData\Local\Python\bin\python.exe'
```

As linhas de cada braço vêm antes do `-Config` e, sem cabeçalho, caem em
`[GPU]` — não repita cabeçalhos (`[GPU]`, `[Vulkan]`) nos braços.

**0. Diagnóstico (1 lançamento, decide 1.5, 1.6, 2.2, 2.3, 2.5 e 2.6).**

```powershell
.\tools\forza_auto_ab.ps1 -Name cloud-diag -ArmSeconds 40 -Passes -Config $cfg -RestartArms @('base=')
# Com o jogo ainda na cena (o script termina com ele rodando):
& $adb -s e11d1729 shell setprop debug.xendroid.pm4_bin_trace 3
Start-Sleep 5
& $adb -s e11d1729 pull /sdcard/Android/data/xendroid.compose.fork.opt/files/compose/xe.log performance-tests\cloud-diag\xe-trace.log
& $py tools\forza_pipestats.py performance-tests\cloud-diag\xe-trace.log
```

Ler: as tabelas que o script imprime no fim (resolves com cópia × clear,
`VkMiscTime`, fins de passe), `TexSigns`/`AlphaModes` do passe 1280×512 no
`forza_pipestats.py`, as linhas `GPU power` de
`performance-tests\cloud-diag\driver-status-arm1.txt` e a linha
`GPU clocks:` do `xe.log`.

**1. Sinal/gama em ramo (exata).** Esperado −3% a −8% no passe principal.

```powershell
.\tools\forza_auto_ab.ps1 -Name cloud-signbr -ArmSeconds 40 -Passes -Config $cfg -RestartArms @(
  'base=', 'signbr=spirv_texture_sign_branch = true',
  'base=', 'signbr=spirv_texture_sign_branch = true')
```

**2. Arredondamento rápido (exata).** Esperado −2% a −2,5% no passe principal.

```powershell
.\tools\forza_auto_ab.ps1 -Name cloud-fastround -ArmSeconds 40 -Passes -Config $cfg -RestartArms @(
  'base=', 'fast=spirv_fast_precision_rounding = true',
  'base=', 'fast=spirv_fast_precision_rounding = true')
```

**3. As duas exatas juntas** (confirma que somam; vira a recomendação do Forza
se 1 e 2 forem positivos).

```powershell
.\tools\forza_auto_ab.ps1 -Name cloud-exact -ArmSeconds 40 -Passes -Config $cfg -RestartArms @(
  'base=', 'exact=spirv_texture_sign_branch = true\nspirv_fast_precision_rounding = true',
  'base=', 'exact=spirv_texture_sign_branch = true\nspirv_fast_precision_rounding = true')
```

**4. Matemática relaxada por bit, sobre as exatas** (muda a matemática:
conferir a imagem em movimento antes de recomendar).

```powershell
$cfgExact = 'spirv_texture_sign_branch = true\nspirv_fast_precision_rounding = true\n' + $cfg
.\tools\forza_auto_ab.ps1 -Name cloud-relax -ArmSeconds 40 -Passes -Config $cfgExact -RestartArms @(
  'base=', 'r1=spirv_ps_relaxed_math = 1', 'r2=spirv_ps_relaxed_math = 2',
  'base=', 'r1=spirv_ps_relaxed_math = 1', 'r2=spirv_ps_relaxed_math = 2')
```

**5. Teto dos vertex shaders (só medição).**

```powershell
.\tools\forza_auto_ab.ps1 -Name cloud-vs -ArmSeconds 40 -Passes -Config $cfg -RestartArms @(
  'base=', 'vs11=spirv_vs_math_experiment = 11',
  'base=', 'vs11=spirv_vs_math_experiment = 11')
```

Se algum braço mostrar `WARNING: GPU slow at the title screen`, descartar a
execução e comparar as linhas `GPU power` dela com as de uma normal.

## 7. Recomendações (não aplicadas)

- Depois dos itens 1–3: se positivos, `spirv_texture_sign_branch = true` e
  `spirv_fast_precision_rounding = true` no config do Forza; a segunda pode
  virar padrão global (resultado idêntico para toda entrada) depois de um A/B
  em outro jogo.
- `spirv_ps_relaxed_math` só por jogo e só depois de validação visual em
  cidade, noite, túneis, menus, modo foto e replays.
- Com `TexSigns`: se ≳75% das texturas do passe principal forem gama, o
  ramo não paga e o caminho é a variante por draw (1.5), com a chave de
  modificação ampliada.
- Com a divisão dos resolves e `VkPassEnd`: atacar o maior entre o clear das
  faixas (2.3), as cargas de texturas resolvidas (2.5) e os fins de passe por
  barreira de buffer inteiro (2.6).
