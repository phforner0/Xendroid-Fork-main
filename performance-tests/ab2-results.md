# Forza Horizon — A/B controlado no POCO F7 (APK `opt-local`)

Coleta de 2026-09-28, 00:38–00:50. Complementa `review-opt-local/README.md`.

## Método

- APK: `xendroid.compose.fork.opt` (`performance-tests/apk/XenDroid-fork-opt-test.apk`,
  SHA-256 `7CDC87FB291FDA6E8904DAB5D669A2DCE02CAB62D267670F9730BAAC52B58224`), release
  `-O3` + ThinLTO, depurável, instalado ao lado do `xendroid.compose.fork`.
- Mesmos driver (Turnip Gen8 V36, `TU_DEBUG=sysmem`), patches, save e config do app
  original. Cena de referência: Viper parado junto à defensa, 2,5 mi, sem toques.
- Uma única sessão; as duas otimizações foram alternadas em tempo real pelas
  propriedades (sem reiniciar o jogo e com os pipelines das duas variantes já em cache):
  `debug.xendroid.resolve_clear_in_guest_pass` (**rc**) e
  `debug.xendroid.spirv_specialize_no_alpha` (**na**).
- 9 braços intercalados: `00 11 10 01 00 11 01 10 00` (rc, na). Cada braço: 20 s de
  estabilização + 45 s de medição (`tools/benchmark_forza.py`, frames apresentados
  no SurfaceFlinger). Diagnóstico leve idêntico em todos os braços
  (`log_gpu_frame_time_breakdown=true`, `log_gpu_frame_time_breakdown_passes=false`).
- Reprodução: `tools/forza_ab_run.ps1 -Arms "00 11 10 01 00 11 01 10 00" -Prefix ab2`
  e `python tools/forza_ab_report.py ab2`. Dados brutos: `performance-tests/ab2-*`
  (resumo em `ab2-report.json`). Temperaturas: use `start-/end-thermalservice.txt`,
  seção "Current temperatures from HAL"; os `thermal-brief.txt` desta coleta vieram
  de uma seção em cache e não são válidos.

## Resultados por braço

| Braço | rc | na | FPS | p95 (ms) | GPU ms/frame | render passes/frame | draws na thread de comandos (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 0 | 19,13 | 66,6 | 50,6 | 219,1 | 22,2 |
| 2 | 1 | 1 | 21,37 | 58,3 | 44,1 | 209,0 | 21,2 |
| 3 | 1 | 0 | 19,12 | 66,6 | 50,3 | 209,3 | 22,2 |
| 4 | 0 | 1 | 21,35 | 58,3 | 44,4 | 219,1 | 21,2 |
| 5 | 0 | 0 | 12,95 | 83,3 | 75,5 | 219,1 | 29,4 |
| 6 | 1 | 1 | 14,86 | 83,3 | 65,5 | 208,6 | 26,1 |
| 7 | 0 | 1 | 14,80 | 83,3 | 65,7 | 218,8 | 24,7 |
| 8 | 1 | 0 | 13,17 | 83,3 | 74,4 | 209,0 | 26,9 |
| 9 | 0 | 0 | 13,05 | 83,3 | 75,0 | 218,3 | 31,0 |

FPS do SurfaceFlinger e FPS dos intervalos do guest coincidem (ex.: 19,126 vs 19,131).
Em todos os braços ~100 resolves e ~84,5 clears por frame; com rc=1, ~80,5 clears
usam o caminho novo.

## Leitura

**Gargalo: GPU.** O tempo de GPU somado por frame é 94–98% do intervalo entre
frames (50,6 ms de GPU em frames de 52,3 ms), com ~1,6–2,4 ms/frame ociosos. A
thread de comandos gasta 21–31 ms/frame processando draws; o restante do seu tempo
é espera na submissão.

**Duas fases.** A partir do braço 5 o custo de GPU subiu ~50% em todas as
configurações. No mesmo instante a câmera girou levemente (mais vegetação no
quadro) e a GPU esfriou ~8 °C enquanto ficava mais lenta. A causa é a limitação
térmica: em `dumpsys thermalservice`, seção "Current cooling devices from HAL", o
cooling device `gpu` estava no nível 5 nos braços 1–4 e no nível 8 nos braços 5–9
(o status térmico geral do Android permaneceu 0). Ou seja, mesmo a fase 1 já tinha
alguma restrição. Os nós `/sys/class/kgsl/kgsl-3d0/*` (clock, ocupação) não podem
ser lidos sem root neste aparelho; o nível desse cooling device é o indicador
disponível.
Dentro de cada fase a câmera ficou estável, então as comparações são feitas
dentro da fase. As medições antigas do APK original (12,2–13,0 FPS) foram feitas
com restrição térmica de GPU e correspondem à fase 2.

| Efeito | Fase 1 (sem limitação) | Fase 2 (limitada) |
|---|---:|---:|
| Especialização alfa (na) — FPS | **+11,7%** (19,13 → 21,36) | **+13,6%** (13,06 → 14,83) |
| Especialização alfa — GPU ms/frame | −12,3% | −12,5% |
| Especialização alfa — p95 | 66,6 → 58,3 ms | 83,3 → 83,3 ms |
| Clear no pass (rc) — FPS | −0,05% / +0,1% | +1,3% / +0,4% |
| Clear no pass — GPU ms/frame | −0,5% | −0,4% a −1,2% |
| Clear no pass — render passes/frame | 219 → 209 | 219 → 209 |

1. **Especialização alfa/cobertura (`spirv_specialize_no_alpha`)** — é a otimização
   que funciona. O shader genérico contém `OpKill` (teste alfa) e escreve
   `gl_SampleMask` (alpha-to-coverage) mesmo quando o draw tem ambos desligados; o
   compilador do driver trata isso como "pode descartar", o que impede early-Z/LRZ.
   A variante sem esses caminhos (sem forçar `EarlyFragmentTests`, preservando o
   workaround NVIDIA) reduz ~6,2 ms de GPU por frame na cena. Vegetação e
   transparências (que continuam com teste alfa ativo usam a variante genérica)
   renderizaram iguais nas capturas dos braços.
2. **Clear do resolve dentro do render pass (`vulkan_resolve_clear_in_guest_pass`)**
   — correto e usado em ~95% dos clears, mas neutro: remove só ~10 aberturas de pass
   por frame, porque o draw seguinte ao resolve normalmente encerra o pass por conta
   das próprias barreiras. Passou a ser **desligado por padrão** no código-fonte.

## Limitações

- Uma cena estática, um aparelho, um driver, `sysmem`. Outras cenas, GMEM e outros
  jogos não foram medidos.
- A correção visual foi checada por capturas estáticas, não por comparação pixel a
  pixel nem em movimento prolongado.
