# Plano de desenvolvimento e otimização — Forza Horizon no POCO F7

**Data:** 2026-09-28. **Base:** `ab2-results.md`, `ab3-results.md`,
`ab3-optimization-ideas.md` e `ab4-results.md`.

As estimativas de ganho abaixo são **tetos medidos ou derivados de medições**,
não ganhos garantidos. Cada item só vira padrão depois de A/B no aparelho.

## Onde estamos

| Medida | Valor |
|---|---|
| FPS na cena de referência | ~24,5 frio; 18–21 morno |
| Ocupação da GPU | ~96% frio (4 submissões × ~9,8 ms em 40,6 ms), ~98% quente |
| Thread de render do jogo | ~70% do tempo esperando a GPU (~48 esperas/s de ~14 ms) |
| Thread de comandos | ~40 ms/frame, dos quais ~16 ms de `IssueDraw` e o resto esperas |

**A GPU é o gargalo.** Tudo que só alivia a CPU ou a thread de comandos
(coleta de fences, readback, backoff) mediu neutro em FPS. CPU só importa para
calor ou quando a GPU ficar mais rápida.

Custo da GPU por frame (celular morno, timestamps ligados, 49,6 ms no total):

| Onde | ms/frame | % |
|---|---:|---:|
| Passe principal 1280×512 (~1.880 draws) | 18,5 | 37% |
| Passe 1280×2048 (sombras, 36 instâncias) | 7,0 | 14% |
| Outros passes | 5,5 | 11% |
| Resolves (100/frame) | 8,4 | 17% |
| Fora de passes e resolves (não instrumentado) | ~10 | 20% |

Já provado e recomendado para o Forza (config por jogo): especialização sem
alfa (+12–14%), `render_target_7e3_as_r11g11b10` (+3%, validado em movimento),
espera do D3D estacionada (`spin_park_guest_functions = "829F04A8"`,
`spin_park_mode = 1`; Guest CPU 0 cai de 97% para 37%).

## Princípios de trabalho

1. **Atacar a GPU primeiro**, pelo maior teto medido.
2. Toda otimização nasce **desligada por padrão**, ligável por jogo, de
   preferência alternável em tempo real por `debug.xendroid.*` (A/B na mesma
   sessão). Opções lidas na tradução de shaders usam A/B com reinício.
3. **Medir antes de otimizar** o que ainda não está instrumentado (os ~10 ms).
4. A/B curtos e intercalados (braços de 40 s), sem teste térmico longo, sem
   protocolo de resfriamento com o jogo fechado (gerou uma execução com a GPU
   2,5× mais lenta).
5. Validação visual em movimento antes de recomendar qualquer opção que mude a
   imagem.

---

## Fase 0 — Consolidar (imediato, baixo risco)

| # | Tarefa | Esforço |
|---|---|---|
| 0.1 | Commitar `ab4-results.md` e este plano | mínimo |
| 0.2 | Aplicar a config recomendada ao Forza no app principal (R11G11B10 + spin park) | mínimo |
| 0.3 | Levar `passres.py` (tempos por passe e por resolve de um log) para `tools/forza_passres.py` | pequeno |
| 0.4 | Modo **A/B com reinício** em `tools/forza_auto_ab.ps1`: lista de configs por braço, relançando o jogo, para opções lidas na inicialização | pequeno |
| 0.5 | **Checagem de estado de energia** em `fh_auto.sh`: registrar o tempo de GPU na tela de título e marcar a execução como suspeita se passar do normal (~3–4,5 ms; a anômala tinha 8,8 ms) | pequeno |

## Fase 1 — Custo dos pixel shaders (maior teto medido)

Teto medido: remover toda a emulação de semântica nos pixel shaders deu
**−9% de GPU e +7,7% de FPS**. Esse teto inclui cores erradas: é só referência.

| # | Tarefa | Teto / evidência | Esforço | Risco |
|---|---|---|---|---|
| 1.1 | **Sinal/gama das texturas em ramo uniforme** (`if` com `DontFlatten` em `spirv_shader_translator_fetch.cc`): a GPU pula a conversão PWL quando a textura não é gama/biased. Imagem idêntica | até −11% no passe principal (~2 ms/frame) | médio | baixo |
| 1.2 | **Separar os bits da matemática relaxada** (1 = "0 × qualquer = 0", 2 = arredondamento de 21 bits, 8 = FMA) com A/B com reinício, e transformar os que pagam numa opção de verdade (`spirv_ps_relaxed_math`) | conjunto 11 = −6% no passe principal, sem artefatos | pequeno | médio (jogos que dependem de 0 × Inf = 0) |
| 1.3 | **Laço principal sem saltos**: quando o shader não tem rótulos, não emitir o laço `DontUnroll` (sair com `OpReturn`/ramo para o fim) e comparar instruções ir3 com `vulkan_pipeline_ir_dump` | a medir no dump | pequeno | baixo |
| 1.4 | **Ampliar a chave de modificação do pixel shader** (os 64 bits estão cheios; incrementar `Modification::kVersion`) para permitir variantes alternáveis em tempo real e especialização por draw | habilitador | médio | médio (cache de pipelines) |
| 1.5 | **Especialização por sinal de textura** (variante por draw, como a sem alfa), se 1.1 não capturar o ganho por causa de texturas gama frequentes | até o teto de 1.1 | médio | médio (mais pipelines) |
| 1.6 | **Variante "só teste alfa"** (ideia 8 do documento): contar draws do passe principal com teste alfa ligado e A2C desligado; implementar se forem muitos | a medir | médio | baixo |
| 1.7 | **`precise_interpolation`** (ligado no Vulkan): A/B com reinício para medir o custo | a medir | mínimo | baixo |

Método: medir instruções por pixel com `vulkan_pipeline_ir_dump` antes e depois
(offline), depois A/B com reinício no aparelho.

## Fase 2 — Resolves, passes e o que está fora deles

| # | Tarefa | Evidência | Esforço |
|---|---|---|---|
| 2.1 | **Separar o tempo de cada resolve** em cópia, clear e transferências (timestamps dentro de `Resolve`) | faixas de cor + clear custam 0,69 ms cada sem sabermos a divisão | pequeno |
| 2.2 | **Resolve de profundidade**: 512×512 custa 1,17 ms e as faixas 1280×256, 0,41 ms cada. Examinar o shader de resolve direto de profundidade (conversão float24, stencil, amostras lidas) | ~2,4 ms/frame em profundidade | médio |
| 2.3 | **Clear das faixas**: fazer o clear no `loadOp` do passe seguinte ou pular quando o passe seguinte sobrescreve tudo; reavaliar `vulkan_resolve_clear_in_guest_pass` com os novos tempos | parte dos 1,9 ms das faixas de cor | médio |
| 2.4 | **Instrumentar os ~10 ms fora de passes e resolves**: cargas/untiling de textura por formato e tamanho, uploads da memória compartilhada, transferências fora de passes, barreiras (linhas `VkMiscTime`) | 20% da GPU sem explicação | pequeno |
| 2.5 | Se 2.4 mostrar que é a recarga de texturas resolvidas: **resolve direto para a textura do host** (pular a ida e volta pela memória compartilhada quando o destino já é textura) | a medir | grande |
| 2.6 | **Contar motivos de fim de render pass** (~220 inícios por frame; 36 instâncias no passe de sombras) e remover os evitáveis, incluindo barreiras por intervalo em vez de `VK_WHOLE_SIZE` (ideia 6 do documento) | custo fixo por passe no modo `sysmem` | médio |

## Fase 3 — Estado de energia e driver

| # | Tarefa | Por quê | Esforço |
|---|---|---|---|
| 3.1 | **Robustez do turbo da GPU**: hoje `adrenotools_set_turbo` roda uma vez na criação da instância Vulkan. Investigar a execução anômala (GPU 2,5× mais lenta desde o título): reaplicar ao retomar/focar e periodicamente, manter o fd do KGSL aberto, e medir com uma **sonda de GPU no início** (trabalho fixo cronometrado) que registre aviso se o clock estiver baixo | usuários podem ter sessões lentas sem saber | pequeno |
| 3.2 | **Turnip mais novo**: A/B com reinício contra o Gen8 V36; o suporte a Adreno 8xx evolui rápido | ganho desconhecido, custo baixo | pequeno |
| 3.3 | Revisar as opções do driver (`FD_DEV_FEATURES`, `TU_DEBUG`); reavaliar GMEM só depois de reduzir passes (mediu 7,6 vs 12,8 FPS do `sysmem` nas rodadas iniciais, com ~220 passes por frame) | depende da fase 2 | pequeno |

## Fase 4 — Vertex shaders e custo fixo por draw na GPU

| # | Tarefa | Evidência | Esforço |
|---|---|---|---|
| 4.1 | **Teto do VS**: estender o experimento de matemática aos vertex shaders, só para medição (posição precisa ficar invariante entre passes) | VS do passe principal com 560–960 instruções | pequeno |
| 4.2 | **Fetch de vértice**: medir a parte do VS gasta em fetch/troca de endianness/conversão de formato; avaliar atributos de vértice nativos para formatos comuns | ~500 mil vértices/frame no passe principal | grande |
| 4.3 | **Preâmbulo por draw**: o FS mais usado roda ~180 instruções de preâmbulo por draw decodificando fetch constants. Medir; se pesar, decodificar na CPU (que está sobrando) e enviar valores prontos | passes com milhares de draws | médio |

## Fase 5 — CPU e calor (depois da GPU)

| # | Tarefa | Nota |
|---|---|---|
| 5.1 | Generalizar o estacionamento da espera do D3D: reconhecer o padrão da função (dicas de prioridade + leitura do contador + timeout) no JIT em vez de endereço fixo | mesma técnica para outros jogos |
| 5.2 | Constantes float por diferença e plano de draw reutilizável (ideias 3 e 4) | reduz CPU/calor; vira FPS só quando a GPU deixar de limitar |
| 5.3 | Coleta limitada de fences (já implementada, desligada) | reavaliar quando a thread de comandos virar o limite |

## Fase 6 — Qualidade e estabilidade (contínua)

- Revisar o código novo desta linha de trabalho atrás de bugs: espera
  estacionada, futex de progresso, coleta de fences, dump de IR, chaves de
  timing de resolve, experimento do tradutor (o prompt de `/goal` para a nuvem
  cobre isso).
- Garantir custo zero dos diagnósticos quando desligados.
- Validação visual em outras cenas antes de recomendar opções que mudam a imagem:
  cidade, noite, túneis, menus, modo foto e replays.
- Manter as opções novas desligadas por padrão para não afetar outros jogos.

---

## Investigações abertas

| Hipótese | Experimento | Decisão |
|---|---|---|
| A maior parte das texturas do Forza é gama | Contar sinais de textura por draw (estatística na vinculação de texturas) | Se sim: 1.5 em vez de 1.1 |
| Os ~10 ms fora de passes são recarga de texturas resolvidas | 2.4 | Se sim: 2.5 |
| O custo das faixas de resolve está no clear, não na cópia | 2.1 | Se sim: 2.3 |
| O passe de sombras é limitado por custo fixo por draw/passe, não por ALU | IR dump + 2.6 + 4.1 | Direciona entre 2.6 e 4.x |
| A execução lenta foi o turbo não aplicado | 3.1 com a sonda de GPU | Correção de robustez |
| Um Turnip mais novo muda o quadro | 3.2 | Troca de driver recomendada ou não |

## Becos sem saída (não repetir sem fato novo)

LRZ (invalidado pelo Xenia e pelo próprio jogo); extents reais no tiling
predicado; thread de replay (o tempo é espera de fence); estacionar todo
spin-backoff (24,5 → 1,8 FPS); `readback_resolve = none`; draws por submissão
diferentes de 1300; backoff de `WAIT_REG_MEM`; clear no passe (como foi
implementado); coleta limitada de fences enquanto a GPU limitar.

## Ordem sugerida

1. **Fase 0** inteira (ferramentas e config).
2. **1.1** e **1.2** (maior teto medido, esforço pequeno a médio).
3. **2.4** e **2.1** (instrumentação barata que decide o resto da fase 2).
4. **3.1** e **3.2** (robustez e driver, baratos).
5. **2.2 / 2.3**, depois **1.3–1.7** conforme as medições.
6. Fase 4, depois fase 5.

Ganho plausível somando as fases 1 e 2, se os tetos se confirmarem
parcialmente: **+8 a +15% de FPS** na cena de referência, além do que já foi
provado. O teto só dos pixel shaders já foi medido em +7,7%.
