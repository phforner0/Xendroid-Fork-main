# Plano de desenvolvimento e otimização — Forza Horizon no POCO F7 (v3)

**Data:** 2026-09-30. **Base:** `ab5` a `ab8-results.md`. Substitui o plano de
2026-09-28 (v2), cujas fases 0–3 foram feitas quase por inteiro (AB5–AB7).

As estimativas são tetos medidos ou derivados de medições. Cada item só vira
padrão depois de A/B no aparelho.

## Onde estamos

| Medida | Valor |
|---|---|
| FPS com o celular frio | **30 — o teto do próprio jogo** (S7, S10) |
| GPU por frame no teto | ~29 ms dos 33,3 ms (~89% ocupada) |
| FPS com o celular quente | ~25–28 (a GPU perde clock e volta a limitar) |
| CPU do processo | ~3,2 núcleos em média (6 threads do guest ~2,2; comandos ~0,5) |
| Passes por frame | ~133 (eram ~210) |

**O objetivo mudou.** Com o celular frio não há fps a ganhar: o que vale é
reduzir trabalho e energia da GPU (e da CPU) para segurar os 30 fps por mais
tempo quando o aparelho esquenta, e subir o fps quente. Métrica principal dos
A/B: GPU ms/frame no teto; fps quando quente.

## Os dez caminhos da reanálise (2026-09-30) — resultado

| # | Caminho | Resultado | Situação |
|---|---|---|---|
| 1 | Perfilar a thread de comandos (simpleperf com símbolos) | `TimerQueue` e Logger em espera "spin": ~16% de um núcleo. Consertados (espera bloqueante / polling com sleep de 2 ms; CPU count em cache): −75% e −74% nessas threads | feito, padrão (Android) |
| 2 | Reajustar `submit_draws` com o celular frio | todos os valores no teto de 30 fps | mantido 1300 |
| 3 | GMEM do Turnip (automático × sysmem) | automático +2,4 ms de GPU | mantido sysmem |
| 4 | Estado redundante por draw | pouca redundância; `BindIndexBuffer` 2726 → 157/frame (índice DMA ligado uma vez, `firstIndex`) | feito, padrão |
| 5 | Decodificar fetch constants na CPU | 480/489 estágios já usam "early preamble"; preâmbulo ~1–2% da ALU | descartado |
| 6 | Resolves in-pass com R11G11B10 | as quebras eram barreiras que o draw seguinte emitia após um resolve com clear no passe; `vulkan_resolve_draw_barriers_at_resolve`: passes 205 → 133/frame, replay −5%, GPU −0,8% morno e igual no teto | feito, padrão |
| 7 | Quem escreve o flag do `WAIT_REG_MEM` | thread do guest `01000010`, função `829EEC48`; espera ~11 ms/frame, latência de ~0,5 ms do polling | diagnosticado, sem ação |
| 8 | Resíduos (depth 512x512 etc.) | o atlas de sombra é limpo com quads 4x relidos como 1x: `vulkan_depth_4x_as_1x`, GPU −1,1 ms (−3,4%); a anomalia do 512x512 sumiu | feito, quirk do Forza |
| 9 | Opções que trocam qualidade | A2C → teste alfa: sem ganho (removida). MSAA 4x → 2x: −15% de GPU, mas contorno brilhante no carro (o jogo relê a cor 4x com outro pitch) | 2x experimental, desligado |
| 10 | Energia por frame | sem medidor utilizável (USB ligado, sem `IPowerStats`); proxies: GPU ms/frame e amostras de CPU | sem medidor |

Ferramentas novas desta rodada: `VkReplay` (comandos reproduzidos e
redundâncias), `VkXfer` (transferências de posse por par), `VkPassEnd` com
reaberturas e `VkPassEndBarriers` (origem das barreiras que encerram passes),
`VkDirectRefused`, escritor do `WAIT_REG_MEM` (`debug.xendroid.wrm_log`),
capturas por braço e marcação de carga "on external power" no `fh_auto.sh`.

## Próximos caminhos, por prioridade

| # | Caminho | Evidência | Ganho esperado | Esforço |
|---|---|---|---|---|
| 1 | **MSAA 2x correto no Forza**: transferências que tratem uma superfície reescrita para 2x como uma 4x lógica (amostras duplicadas) quando o jogo a relê com outro pitch ou como 1x | −15% de GPU medido, com artefato | grande (≈ −4,5 ms) | grande |
| 2 | **Passe 1280x2048** (cor 1x em 0t): 58 instâncias e ~7 ms/frame; entender o que o separa (resolves pequenos em cadeia?) e o custo fixo por draw (~10 µs com shaders vazios) | `VkPassTime`, AB7 | médio | médio |
| 3 | **Superfícies 4x relidas com outro pitch** (`VkXfer`: depth 0t/16t 4x↔1x, cor 720t/16t 4x↔1x, cor 0t/32t→16t): ~12 mil tiles/frame de transferências restantes; estender a ideia do `depth_4x_as_1x` onde o 4x só limpa | `VkXfer` | ~1 ms | médio |
| 4 | **Resolves pequenos em cadeia** (~60/frame ≤128x128, ~20 µs cada, quase tudo custo fixo) | `VkResolveTime` | ~0,5–1 ms | médio |
| 5 | **Validação visual ampla** do que ficou padrão (cidade, noite, chuva, túneis, replays) | só a cena de referência validada | — | pequeno |
| 6 | Thread de comandos: cópia de constantes por draw (`UpdateBindings`/`memmove` ~24% da thread), `LoadShader` com hash por draw | perfil S7/S10 | energia | médio |

## Becos sem saída (não repetir sem fato novo)

LRZ; extents reais no tiling predicado; thread de replay; estacionar todo
spin-backoff; `readback_resolve = none`; `submit_draws` ≠ 1300; backoff de
`WAIT_REG_MEM`; GMEM automático do Turnip; decodificar fetch constants na CPU
(early preamble); A2C → teste alfa; MSAA 2x por simples reescrita (quebra os
truques de EDRAM do Forza); medir energia pelo `Charge counter` com o USB
ligado; laço sem `DontUnroll`; `precise_interpolation` (inerte no Turnip).
