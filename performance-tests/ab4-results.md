# Forza Horizon — rodada pós-AB3 no POCO F7 (sessão de 2026-09-28, tarde)

Continuação de `ab3-results.md`, cobrindo também as hipóteses de
`ab3-optimization-ideas.md`. Mesmo aparelho, driver (Turnip Gen8 V36,
`TU_DEBUG=sysmem`), save e cena de referência (Viper parado junto à defensa,
2,5 mi). APK de teste: `performance-tests/apk/XenDroid-fork-opt-test-3.apk`
(SHA-256 `7CA833529CBD21F30A939290ED75A39784DDD92EA2C4EE6823EE541E38ADBBE0`),
pacote `xendroid.compose.fork.opt`. Todas as opções novas vêm desligadas.

Os A/B em tempo real usam braços intercalados de 40 s na mesma sessão. O
aparelho esquenta ao longo da sessão (cooling device `gpu` de 4 até 6–8), então
os números absolutos caem com o tempo; compare cada braço com a média dos
vizinhos.

## Resumo

| Frente | O que foi feito | Resultado | Decisão |
|---|---|---|---|
| Espera ativa do D3D (ideia 1) | Estacionar **todo** spin-backoff após 24 voltas | 24,5 → **1,8 FPS** | Rejeitado |
| | Estacionar só o passo de espera do D3D (`sub_829F04A8`), acordando pelo progresso publicado pela thread de comandos | Guest CPU 0: 97% → 37%; FPS 24,5 (estaciona) vs 23,1 (só mede) | Opção nova, desligada por padrão |
| Coleta de fences (ideia 2) | Coleta limitada, alternável em tempo real | Bloqueio em `vkGetFenceStatus` 12,6 → 2,1 ms/frame, mas FPS 21,4 vs 21,5 | Neutro (GPU-bound) |
| Readback `none` vs `uma` | A/B em tempo real | 21,2 vs 21,6 FPS | Manter `uma` |
| Draws por submissão | 0 / 1300 / 2500 em tempo real | 17,9 / **18,4** / 18,0 FPS | Manter 1300 |
| `WAIT_REG_MEM` | Log dos que não passam de primeira | Sempre a mesma flag do jogo (físico `0x1FCA4004 == 0`), 1–11 ms | Diagnóstico |
| Estatísticas de shader (item 4) | `VK_KHR_pipeline_executable_properties` + dump do assembly ir3 | FS com mediana de ~1.000 instruções; o custo é emulação de semântica do Xenos | Base do próximo passo |
| Mapa de custo da GPU | Timestamps por passe e por tipo de resolve | Passe principal 18,5 ms; resolves 8,4 ms; ~10 ms fora de ambos | Prioridades abaixo |
| Teto da limpeza de ALU nos pixel shaders | Opção de diagnóstico `spirv_ps_math_experiment` | 15: **−9% GPU, +7,7% FPS**; 11 (sem mudar texturas): −6% no passe principal, sem artefatos | Próximo passo |
| R11G11B10 em movimento | Fotos em movimento R11G11B10 vs RGBA16F | Sem diferença visual; resolves −2,7 ms/frame | Recomendado ligar no Forza |
| Teste térmico longo | Cancelado a pedido | Uma execução anômala registrada (ver abaixo) | — |

## Onde vai o tempo de GPU

Com o celular frio, a GPU já fica ~96% ocupada (4 submissões × ~9,8 ms em frames
de 40,6 ms); quente, ~98%. Por isso tudo o que só alivia a thread de comandos
(coleta de fences, readback, backoff) não muda o FPS.

Timestamps por passe e por resolve (média de 22 relatórios, celular morno; os
timestamps serializam os passes, então valem as proporções):

| Onde | ms/frame | Observação |
|---|---:|---|
| Passe principal 1280×512 | 18,5 | 1.878 draws em 5,2 instâncias (faixas do tiling) |
| Passe 1280×2048 | 7,0 | 552 draws em 36 instâncias, quase só profundidade |
| Demais passes | 5,5 | pós-processamento, cubemap, sombras |
| Resolves (100/frame) | 8,4 | ver tabela abaixo |
| Fora de passes e resolves | ~10 | cargas de textura após resolves, uploads, barreiras |
| **Total** | **49,6** | intervalo de frame 52,0 ms |

Resolves que mais custam:

| Resolve | Por frame | ms cada | ms/frame |
|---|---:|---:|---:|
| cor + clear 1280×256 (faixas) | 2 | 0,69 | 1,36 |
| profundidade 512×512 | 1 | 1,17 | 1,17 |
| profundidade 1280×256 (faixas) | 2 | 0,41 | 0,82 |
| cor + clear 256×256 (faces do cubemap) | 7 | 0,10 | 0,72 |
| cor + clear 1280×208 (última faixa) | 1 | 0,57 | 0,57 |
| cor + clear 8×8 a 128×128 (mipmaps do cubemap) | ~57 | 0,02–0,035 | ~1,2 |

A cadeia de mipmaps do cubemap dinâmico (6 faces × 8 níveis, cada nível com
passe, resolve + clear e transferência de posse) explica metade da contagem de
resolves, mas custa pouco (~20–35 µs cada). O custo está nos resolves grandes.

## Espera ativa do D3D (ideia 1)

`sub_829F04A8` é o passo de espera do Direct3D do jogo: sled de dicas de
prioridade, leitura do contador de progresso da GPU (`*(device+0x2B10)`),
carimbo de progresso e timeout de travamento. `sub_823E91F0` o chama até o
contador passar o alvo (espera por fence/posição da GPU).

- **Versão genérica** (qualquer spin-backoff dorme 30 µs após 24 voltas): atinge
  também spinlocks e filas curtas do jogo; esperas de microssegundos viram
  ~80 µs, milhares de vezes por frame. FPS 24,5 → 1,8.
- **Versão direcionada** (`spin_park_guest_functions = "829F04A8"`,
  `spin_park_mode = 1`): só esse passo estaciona, depois de 50 µs de espera;
  oferece a CPU a outra fiber pronta ou dorme num futex que a thread de comandos
  acorda ao publicar progresso (`EVENT_WRITE_SHD`, `MEM_WRITE`, writeback de
  scratch e do ponteiro de leitura), com timeout de 500 µs. O jogo e seus
  timeouts continuam checando a condição a cada volta.

| Braço | Modo | FPS | Guest CPU 0 | Tempo estacionado |
|---|---|---:|---:|---:|
| 1 | estaciona | 24,85 | 33% | 57% |
| 2 | só mede | 24,33 | 103% | 0% |
| 3 | estaciona | 24,77 | 41% | 57% |
| 4 | só mede | 23,58 | 93% | 0% |
| 5 | estaciona | 23,99 | 37% | 59% |
| 6 | só mede | 21,52 | 97% | 0% |

Medições do modo "só mede": ~48 esperas/s, ~14 ms cada; a thread de render do
jogo passa ~70% do tempo esperando a GPU. O tempo de `IssueDraw` na thread de
comandos cai nos braços com estacionamento (13–18 vs 18–21 ms/frame): o núcleo
liberado ajuda a thread de comandos (orçamento de energia compartilhado).
Com a deriva térmica, cada braço "estaciona" fica +0,5 a +1,4 FPS acima da média
dos vizinhos. É a proposta da ideia 1 em versão reduzida (acordar pelo escritor
real, preservar timeouts, não segurar o lock global); a generalização por
padrão de instruções fica para depois.

## Coleta de fences (ideia 2)

Confirmado no Turnip: `vkGetFenceStatus` → `wait_timestamp_safe` com timeout
0 → no KGSL, timeout 0 = esperar para sempre. `UpdateCompletedSubmission()`
percorria todas as fences pendentes, então cada coleta esperava a GPU esvaziar.

Com `debug.xendroid.fence_collect = 1` (esperas só até a submissão pedida; ao
reciclar fences, só as mais antigas que as 4 últimas):

| | Limitada | Original |
|---|---:|---:|
| Bloqueio em polls de fence | 2,1 ms/frame | 12,6 ms/frame |
| "Replay" | 7,6 ms/frame | 17,8 ms/frame |
| Swap | 13,9 ms/frame | 3,9 ms/frame |
| FPS | 21,4 | 21,5 |

A espera só muda de lugar: a thread de comandos corre à frente até o limite de
3 frames em voo e espera no início do frame. Com a GPU no limite, não há ganho.
Volta a ser útil quando a GPU ficar mais rápida que a thread de comandos.

## Shaders (item 4)

Com `vulkan_pipeline_statistics = true`, cada pipeline registra as estatísticas
do Turnip (`PipeStats`); `pm4_bin_trace` registra o uso por par de shaders e
tamanho de passe (`PipeUse`); `tools/forza_pipestats.py` junta os dois.

- 341 pares de shaders; FS: mediana de 1.003 instruções (p90 1.846, máx.
  2.934), ~24% NOPs, mediana de 14 amostragens de textura; VS do passe principal
  com 560–960 instruções. Quase todo shader mantém o laço principal `DontUnroll`
  do tradutor.
- O assembly ir3 (`vulkan_pipeline_ir_dump`, arquivos em `shader_ir/`) do par
  mais usado (390 draws/frame, FS com 2 amostragens) tem ~180 instruções de
  preâmbulo (uma vez por draw, decodificando fetch constants) e ~285 por pixel,
  quase todas emulação: conversão de gama PWL das texturas calculada sempre e
  descartada por `sel` conforme constantes uniformes, multiplicação "±0 ×
  qualquer = 0" (3 instruções extras por produto), arredondamento para 21 bits
  após `rcp/rsq/sqrt/exp/log` (~10 instruções cada), teste alfa/alpha-to-
  coverage e gama de saída em ramos uniformes.

Teto medido com `spirv_ps_math_experiment` (bitmask, só pixel shaders, lida na
tradução): 1 = sem "0 × qualquer = 0", 2 = sem arredondamento de 21 bits, 4 =
ignora sinal/gama das texturas (cores erradas, só medição), 8 = permite FMA.

| Lançamento | FPS | GPU/frame | Passe principal | Imagem |
|---|---:|---:|---:|---|
| A: normal | 19,2 | 49,6 ms | 18,5 ms | referência |
| C: 15 (tudo) | 20,7 | 45,0 ms | 15,3 ms | cores erradas |
| D: 11 (sem mexer em texturas) | 19,0 | 49,6 ms | 17,4 ms | idêntica nas fotos |

C veio depois de A (celular mais quente) e ainda assim ganhou 9% de GPU. D foi o
último lançamento (o mais quente); o passe principal caiu 6% e não houve
artefatos (pontos pretos/brancos, bloom, céu) nas fotos em movimento. O maior
pedaço (~11% do passe principal) está no tratamento de sinal/gama das texturas,
que pode ser otimizado **sem mudar o resultado**: o tradutor já emite um
`OpSwitch` com `DontFlatten`, mas o NIR converte o switch em `if`s sem esse
controle e os achata em `sel`. Emitir `if`s uniformes com `DontFlatten` faria a
GPU pular a conversão quando a textura não é gama/biased.

## R11G11B10 em movimento

Seis fotos a cada 4 s acelerando a partir do ponto de referência, com
RGBA16F e com R11G11B10: céu com nuvens e sol, bloom, vegetação, reflexos no
carro e HUD equivalentes, sem faixas no céu. Na mesma sessão, os resolves
caíram de 11,2 para 8,4 ms/frame (leitura de 32 em vez de 64 bpp) e a GPU de
53,9 para 49,6 ms/frame (parte disso é deriva térmica; o A/B intercalado do AB3
deu +3,1% de FPS).

## Coisas descartadas ou neutras nesta rodada

- `readback_resolve = none`: 21,2 vs 21,6 FPS com `uma`. Economiza ~2 ms de
  CPU na thread de comandos, mas a GPU é o limite. Não vale o risco de leitura
  desatualizada.
- `vulkan_mid_frame_submission_draws`: 1300 (atual) é o melhor dos três.
- `WAIT_REG_MEM`: a única espera não satisfeita é uma flag gravada pela CPU do
  jogo (`0x1FCA4004`, espera 0), com o anel ainda cheio de comandos; a GPU tem
  fila e absorve.

## Execução térmica anômala

Uma execução iniciada após ~5 min com o jogo fechado (tela apagando pelo
timeout) rodou com a GPU ~2,5× mais lenta desde a tela de título (8,8 vs ~3 ms
por frame no título; 25 vs ~10 ms por submissão no jogo), sem throttling
registrado (cooling `gpu` 4, GPU a 46 °C). O `adrenotools_set_turbo` é aplicado
uma vez na criação da instância Vulkan. Lançamentos com a tela ligada voltaram
ao normal. Protocolo de resfriamento com o jogo fechado não é confiável neste
aparelho.

## Configuração recomendada para o Forza

No config por jogo `config/4D5309C9.config.toml`:

```toml
[Vulkan]
render_target_7e3_as_r11g11b10 = true

[CPU]
spin_park_guest_functions = "829F04A8"
spin_park_mode = 1
```

Opcional, experimental (visualmente igual nas cenas testadas, −6% no passe
principal): `spirv_ps_math_experiment = 11` na seção `[GPU]`. O valor 15 é só
para medição.

As propriedades `debug.xendroid.*` sobrescrevem as opções em tempo real
enquanto estiverem definidas; foram todas limpas no fim da sessão (exceto
`spirv_specialize_no_alpha = 1`, que já estava).

## Próximos passos, pelo ganho medido

1. **Sinal/gama das texturas em ramo uniforme** (sem mudar a imagem): teto de
   ~−11% no passe principal.
2. **Transformar a matemática relaxada em opção de usuário** (hoje é o bitmask
   de diagnóstico), com a ressalva de que alguns jogos dependem de
   "0 × Inf = 0".
3. **Resolves grandes**: faixas de cor com clear (0,69 ms cada) e profundidade
   512×512 (1,17 ms) — examinar o shader de resolve de profundidade e o clear
   que abre um render pass próprio.
4. **~10 ms/frame fora de passes e resolves**: instrumentar as cargas de
   textura (untiling) após resolves e os uploads.
5. Ideias 3 e 4 do documento (constantes float por diferença, plano de draw)
   reduzem CPU e calor, mas não FPS enquanto a GPU for o limite.

## Ferramentas e diagnósticos novos

- `tools/forza_auto_ab.ps1 -Passes`: liga tempos por passe (`VkPassTime`) e por
  tipo/tamanho de resolve (`VkResolveTime`).
- `tools/forza_segstats.py`: colunas novas (swap, replay, polls de fence,
  esperas do jogo e tempo estacionado).
- `tools/forza_pipestats.py`: estatísticas do Turnip × uso por passe.
- Propriedades: `debug.xendroid.spin_park` (0/1/2), `debug.xendroid.fence_collect`,
  `debug.xendroid.wrm_log N`, `debug.xendroid.submit_draws`.
- Opções: `vulkan_pipeline_statistics`, `vulkan_pipeline_ir_dump`
  (hashes de PS ou `min_fs=N`), `spirv_ps_math_experiment`,
  `spin_park_guest_functions`, `spin_park_mode`.
