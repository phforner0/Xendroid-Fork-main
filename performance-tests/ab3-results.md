# Forza Horizon — passos 2 a 6 no POCO F7 (sessão de 2026-09-28)

Continuação de `ab2-results.md`. Mesmo aparelho, driver (Turnip Gen8 V36,
`TU_DEBUG=sysmem`), save e cena de referência (Viper parado junto à defensa,
2,5 mi). APK de teste: `performance-tests/apk/XenDroid-fork-opt-test-2.apk`
(SHA-256 `F1E1747D86293AD6D8E960975DD2131E10F1C44AC0A04611B235223B76937593`),
pacote `xendroid.compose.fork.opt`.

## Resumo

| Passo | O que foi feito | Resultado | Decisão |
|---|---|---|---|
| 2 — early-Z/LRZ | `TU_DEBUG=perf` + leitura do `tu_lrz.cc` | LRZ perdido em quase todo pass por causas do emulador **e** do jogo | Sem ganho possível; o ganho de early-Z já veio da variante sem alfa |
| 3 — perfil de CPU | simpleperf (on-CPU, DWARF e off-CPU) + dump do código PPC | "GPU Commands" usa ~45% de um núcleo; "Guest CPU 0" é espera ativa | Diagnóstico; `readback_resolve` testável em tempo real |
| 4 — tiling predicado | Instrumentação PM4 + experimento de áreas falsas | Teto de −25% de draws e −6 a −12% de GPU; FPS não sobe com o celular frio | Não implementar agora |
| 5 — thread de replay | Perfil off-CPU | Premissa errada: o "replay" é espera por fence, não trabalho de CPU | Não implementado |
| 6 — HDR 7e3 em 32 bpp | Opção `render_target_7e3_as_r11g11b10` + A/B com reinício | **+3,1% FPS, −3,5% GPU**, sem artefato visível na cena | Opção desligada por padrão; pode ligar por jogo |
| Extra — `WAIT_REG_MEM` | Backoff fino + estatística | Neutro | Desligado por padrão |

## Passo 2 — early-Z e LRZ

`turnip_debug = 'sysmem,perf'` faz o Turnip escrever no logcat (tag `TU`) por que
desliga o LRZ. Em 5 s na cena (~96 frames): 9.635 avisos, ~100 por frame.

| Motivo | Ocorrências | Efeito |
|---|---:|---|
| `stencil write based on depth test` | 2.946 | sem escrita de LRZ até o fim do pass |
| `Depth write + ALWAYS/NOT_EQUAL` | 2.574 | LRZ desligado até o fim do pass |
| `FS writes depth or has side-effects` | 2.326 | LRZ desligado até o fim do pass |
| `CmdClearAttachments` | 1.188 | LRZ desligado até o fim do pass |
| `Depth write + blending` | 400 | sem escrita de LRZ |
| `Stencil may kill fragments` | 200 | sem escrita de LRZ |

- O trio "FS escreve profundidade + ALWAYS + stencil" no draw 1 é a cópia de
  profundidade da EDRAM (transfer) do Xenia, feita num render pass próprio. Com o
  rastreamento de direção na GPU, o pass do jogo que vem depois herda o LRZ inválido.
- Os clears de resolve usam `vkCmdClearAttachments`, que desliga o LRZ. Trocar por
  `loadOp = CLEAR` com área parcial não resolve: o `tu_lrz_init_state` marca
  `lrz_disable_for_next_rp` quando a área não cobre a imagem inteira.
- Mesmo com LRZ válido, o primeiro draw do jogo em cada pass escreve stencil com
  `passOp ≠ KEEP` (critério `tu6_stencil_written_based_on_depth_test`), o que
  desliga a escrita de LRZ pelo resto do pass. Isso é estado do jogo.
- Conclusão: não há ganho de LRZ a buscar. O early-Z por pixel já foi liberado
  pela variante sem alfa (`EARLY_Z_LATE_Z` → `EARLY_Z` em `tu6_build_depth_plane_z_mode`).

## Passo 3 — perfil de CPU

simpleperf 20 s na cena (64.323 amostras, ~3,2 núcleos ocupados):

| Thread | Núcleos |
|---|---:|
| Guest CPU 0 | 0,96 |
| Guest CPU 5 | 0,58 |
| Guest CPU 1 | 0,47 |
| GPU Commands | 0,45 |
| Guest CPU 4 / 3 / 2 | 0,22 / 0,15 / 0,15 |
| XMA Decoder | 0,11 |

- **GPU Commands, só CPU:** Xenia 58%, Turnip 23%, `memmove` 13%. Por função
  (inclusivo): `IssueDraw` 38%, `UpdateBindings` 14%, `IssueCopy` 14% (dos quais
  `ReadHostMapped`, a cópia do `readback_resolve = uma`, 9,5%), `RequestTextures` 7%,
  replay (`DeferredCommandBuffer::Execute`) ~5%.
- **GPU Commands, CPU + espera:** `ioctl` do driver (espera de fence) 38%, sono em
  `WAIT_REG_MEM` 15%, mutex global 6,5%. O "replay" de 15–25 ms/frame do log
  `VkFrameSync` inclui o `vkGetFenceStatus` bloqueante do Turnip/KGSL usado como
  limitador de frames em voo — não é trabalho de CPU.
- **Guest CPU 0:** 77% das amostras em `sub_823E91F0` e `sub_829F04A8`. O dump PPC
  (`dump_functions_at`) mostra um laço do Direct3D do jogo que espera o contador do
  processador de comandos (`**(device+0x2B10)`) chamando um passo com dicas de
  prioridade (`or r31,r31,r31`) e timeout de travamento, sem dormir. É espera
  ativa: gasta um núcleo (calor), mas não limita o FPS.
- `readback_resolve`: alternável em tempo real por `debug.xendroid.readback_resolve`
  (`none|uma|fast|all`). `none` não mudou a imagem na cena e elimina a cópia de
  ~2 ms/frame da thread GPU Commands; falta validar em outras cenas.

## Passo 4 — tiling predicado

Instrumentação `debug.xendroid.pm4_bin_trace N` (log `BinTrace`), por frame:

- 3 faixas: `bin_select` 0x80000003, 0xC e 0x30, com 459, 933 e 504 draws
  executados; antes delas ~960 draws sem tiling e depois ~225 (pós-processamento).
- ~15.000 `SET_BIN_MASK`, 188 `EVENT_WRITE_EXT`, 5 `INTERRUPT` e ~170 `WAIT_REG_MEM`
  por frame.
- O mesmo pacote `SET_BIN_MASK` muda de valor entre faixas (ex.: `FFFFFFFF` na
  faixa 0 → `8000003F` nas faixas 1 e 2): o jogo calcula as máscaras das faixas 1 e
  2 a partir das áreas que a faixa 0 reporta no `EVENT_WRITE_EXT`, e o Xenia sempre
  reporta a tela inteira.

Experimento `debug.xendroid.fake_extents` (áreas falsas; quebra a imagem de
propósito): com "só faixa 0", o jogo reescreveu as máscaras, os draws caíram de
3.082 para ~2.300 (faixa 1: 957 → 530; faixa 2: 512 → 188) e a geometria das
faixas 1 e 2 sumiu na captura. O tempo de GPU caiu de 40,0 para 35,3–37,5 ms/frame
(−6 a −12%), mas o intervalo de frame ficou em 40–41 ms.

Esse é o teto do ganho, e com o celular frio ele não vira FPS. Implementar áreas
reais exige ou (a) esperar a GPU terminar a faixa 0 antes do callback do jogo, o que
serializa CPU e GPU (~1 frame de espera), ou (b) reusar áreas do frame anterior,
sem identidade estável entre frames (185–191 eventos por frame, endereços novos a
cada frame) e com risco de geometria sumindo na borda das faixas. **Não implementado.**

## Passo 5 — thread de replay

A premissa era que o replay custava 15–17 ms de CPU por frame. O perfil off-CPU
mostrou que esse tempo é quase todo espera de fence (limitador de frames em voo). O
trabalho real de gravação (Xenia + Turnip) é ~3–5 ms/frame. **Não implementado.**

## Passo 6 — HDR 7e3 como B10G11R11_UFLOAT

Opção nova `render_target_7e3_as_r11g11b10` (seção Vulkan, padrão `false`, lida na
inicialização). Troca o formato de host do `k_2_10_10_10_FLOAT` de RGBA16F
(64 bpp) para B10G11R11_UFLOAT (32 bpp). Transferências, dumps e resolves já
empacotam 7e3 a partir de floats pelo formato do jogo, então só o mapeamento muda.

A/B com reinício, 4 braços intercalados, 45 s cada (`ab3-*`):

| Braço | Formato | FPS | GPU ms/frame |
|---|---|---:|---:|
| 1 | R11G11B10 | 22,85 | 41,6 |
| 2 | RGBA16F | 22,25 | 43,0 |
| 3 | R11G11B10 | 23,20 | 40,9 |
| 4 | RGBA16F | 22,42 | 42,5 |

Média: **+3,1% de FPS e −3,5% de GPU**. Nas capturas não há faixas no céu nem
problemas em reflexos, transparências ou HUD. As diferenças por região entre os
formatos (céu 23, sol 32, carro 10, estrada 19, escala 0–255) são do mesmo tamanho
das diferenças entre duas rodadas iguais em RGBA16F, porque o enquadramento varia.

Riscos: o alfa de 2 bits do 7e3 é descartado (lê 1,0) e a mantissa cai de 7 para 6
bits (5 no azul). Para ligar só no Forza, no config por jogo `4D5309C9.config.toml`:

```toml
[Vulkan]
render_target_7e3_as_r11g11b10 = true
```

## Extra — espera do `WAIT_REG_MEM`

Opção `wait_reg_mem_backoff` (alternável por `debug.xendroid.wait_reg_mem_backoff`)
e estatística nova no `GpuFrame` (`wait_reg_mem unmet=… waited=…`). A/B com 5
braços: neutro (24,5 vs 24,4 FPS). Só ~0,87 espera por frame fica pendente na
primeira checagem, e ela dura ~7 ms de qualquer jeito.

Os intervalos apresentados se agrupam em múltiplos de 8,33 ms (tela de 120 Hz):
~70% em 41,7 ms, ~20% em 50 ms. Com o celular frio, GPU (~38,5 ms/frame) e a thread
de comandos (~40 ms com as esperas) estão quase empatadas.

## Ferramentas novas

- `tools/forza_auto_ab.ps1` + `tools/fh_auto.sh`: roteiro que roda no celular como
  máquina de estados sobre o log do emulador. Passa por título (`pressstart.wmv`),
  Start aceito (`profileschema`), menu SINGLE PLAYER com A (`forza_tone.wmv`),
  entrada no jogo (draws/frame > 2.500) e carro parado (6 relatórios dentro de
  1,5%, após 60 s). Depois alterna a propriedade do A/B. Uma chamada do adb dispara
  e outra espera; do lançamento aos braços leva ~2,5 min, sem capturas nem esperas
  fixas.
- `tools/forza_segstats.py`: fatia o `xe.log` pelos registros de troca de
  propriedade do próprio emulador e calcula FPS, GPU e esperas por braço.
- Propriedades de debug (lidas a cada 30 frames): `debug.xendroid.pm4_bin_trace`,
  `debug.xendroid.fake_extents` (só para diagnóstico: quebra a imagem),
  `debug.xendroid.readback_resolve`, `debug.xendroid.wait_reg_mem_backoff`.

## Onde está o próximo ganho

- Com o celular frio, qualquer corte isolado de GPU (até −12%) esbarra na thread de
  comandos. O custo dela está em `IssueDraw` (~6 µs por draw, 18 ms/frame) e em
  esperas pelo jogo; reduzir o custo por draw é a frente de CPU.
- Com o celular quente, a GPU volta a mandar e a opção de 32 bpp e cortes de GPU
  rendem mais.
- A espera ativa da "Guest CPU 0" gasta um núcleo inteiro: fazê-la dormir (o JIT
  já tem `park_memory_poll_loops`, que não pega esse formato de laço com chamada)
  reduziria calor, que é o fator que mais derruba o FPS sustentado.
