# Plano de desenvolvimento e otimização — Forza Horizon no POCO F7 (v4, com a situação depois do AB9, a v5, a reanálise v6, o fechamento dela no AB11, a reanálise v7, o fechamento dela no AB12, a reanálise v8 e o fechamento dela no AB13)

**Data:** 2026-09-30. **Base:** reanálise de `ab2` a `ab8-results.md`, dos logs
com timestamps do build 36 (`b36-depth1x-ts`), do perfil de CPU do build 42
(`b42-final`, S14) e dos configs do app de teste. Substitui a v3 (os dez
caminhos do AB8, todos concluídos e mesclados no PR #9).

As estimativas são tetos medidos ou derivados de medições. Cada item só vira
padrão depois de A/B no aparelho.

## Onde estamos

| Medida | Valor |
|---|---|
| FPS com o celular frio | 30 — o teto do próprio jogo |
| GPU por frame no teto (S14, build 42) | 27,9 ms dos 33,3 ms |
| FPS com o celular quente | ~27–28 (a GPU perde clock e volta a limitar) |
| CPU do processo (S14) | ~3,0 núcleos: código do jogo 2,0; thread de comandos 0,5; XMA 0,18; áudio 0,1 |
| Passes por frame | ~133 |

## Mapa da GPU (timestamps, build 36, 101 resolves por frame)

| Trabalho | ms/frame | Observação |
|---|---:|---|
| Passe principal 1280x512 (cena 4x) | 15,5 | 8,5 passes, 1824 draws |
| Passes 1x de 1280 de largura ("1280x2048") | 7,3 | 58 passes, 514 draws: ~14 µs por draw (parte deles com pixel shaders de 1 instrução) |
| Pós-processamento 640x1024 | 1,6 | 6 passes, 7 draws |
| Passes 320x4096 | 1,2 | 13 passes, 245 draws |
| Cadeia 160x8192 (≤128x128) | 1,0 | 90 passes para 45 draws |
| Atlas de sombra 1040x2528 | 0,55 | |
| Resolves de faixas 4x (3 de cor, 3 de depth) | 2,9 | cor a ~13 GB/s efetivos |
| Resolves 1x de tela cheia (1280x720) | 1,6 | |
| Resolves minúsculos (~60 de ≤128x128) | ~1,7 | ~25 µs cada, quase tudo custo fixo |
| Cargas de textura | 2,6 | 0,75 com o resolve gravando na textura (ver achado 1) |

Os timestamps serializam os passes (valem as proporções), e o anel perdeu 260
pares por relatório (192 por submissão não bastam): os totais de passes estão
subestimados.

## Achados novos da reanálise

1. **O config global do app de teste tem padrões antigos.**
   - Estão salvos `vulkan_direct_host_resolve_4px = false` e
     `vulkan_direct_host_resolve_to_texture = false`, de builds anteriores ao
     PR #7. O quirk liga o segundo, mas o resolve direto na textura só usa as
     variantes de 4 px (cor rápida e depth) quando o primeiro também está ligado.
   - As sessões S10a, S10b, S11 e S12 rodaram sem o ganho do PR #7 (cargas de
     2,6 ms em vez de 0,75 ms). As comparações dentro de cada sessão continuam
     válidas; a S13 e a S14 punham os dois explicitamente.
   - Um usuário que mude o `4px` perde o ganho do PR #7 em silêncio.
2. **Três configurações medidas só existem no config de teste.** Os usuários
   não as recebem:
   - o spin park (`spin_park_guest_functions = "829F04A8"`,
     `spin_park_mode = 1`): Guest CPU 0 de 97% para 37% de um núcleo;
   - `render_target_7e3_as_r11g11b10`: +3,1% de fps e −3,5% de GPU, imagem
     igual em movimento;
   - `vulkan_resolve_clear_in_guest_pass`: é o que faz as barreiras no resolve
     juntarem os passes (205 → 133).
3. **O grupo de passes 1x de 1280 de largura custa 7,3 ms** (~14 µs por draw).
   A reanálise do build 28 viu pixel shaders de 1 instrução em parte desses
   draws: o custo tende a ser fixo por draw ou de vértices. Ninguém o separou
   em partes ainda.
4. **Os resolves de faixas 4x leem as amostras a ~13 GB/s efetivos.** Isso é
   ~20% da banda do aparelho; o custo é a leitura MSAA no compute.
5. **A cadeia de reduções gera ~60 resolves minúsculos e ~45 passes vazios por
   frame.** Cada resolve troca de gráfico para compute e volta.
6. **CPU:**
   - O código do jogo (JIT) é 2/3 do processo. As funções mais quentes são
     `82FC6CC0`, `8300D580`, `82FCEAB0` e `82FAE0B8`, na thread Guest CPU 5
     (0,65 núcleo).
   - O JIT paga duas emulações ligadas por padrão em todo o código: a
     propagação de NaN do VMX (`a64_vmx_nan_fixup`) e a sincronização de pilha
     host/guest.
   - O XMA percorre os 320 contextos a cada passada.
   - Acordar threads que esperam eventos custa ~0,14 núcleo; são esperas
     reais, e o `Signal` já pula o syscall quando ninguém espera.
7. **Descartados nesta reanálise:**
   - UBWC perdido por formato mutável: os formatos principais do Forza (7e3,
     8888, 2_10_10_10) não usam alias de transferência.
   - Futex sem ninguém esperando: já é evitado.
   - Conversão float24 no pixel shader: está desligada, então o early-Z fica
     preservado.

## Próximos caminhos, por prioridade

| # | Caminho | Evidência | Ganho esperado | Esforço |
|---|---|---|---|---|
| 1 | **Completar o perfil do Forza nos quirks**: spin park com checagem da assinatura da função em `829F04A8` (outra versão do executável não é afetada), `render_target_7e3_as_r11g11b10`; avaliar `vulkan_resolve_clear_in_guest_pass` | AB3, AB5, AB8 | −0,6 núcleo; +3% de fps; passes 205 → 133 para os usuários | pequeno |
| 2 | **Resolve na textura sem depender do `4px`**, `UPDATE_from` para padrões mudados, config de teste limpo e anel de timestamps de 512 pares | achado 1 | garante os −1,4 ms do PR #7 | pequeno |
| 3 | **Teto por draw em cada passe**: propriedade que corta os pixels (scissor 1x1), os vértices (um triângulo) ou o próprio draw, com timestamps por passe | achado 3 | decide entre shaders, busca de vértices e modelo de bindings | pequeno |
| 4 | **MSAA 2x com a superfície tratada como 4x lógica**: transferências sintetizam o layout 4x a partir da superfície 2x quando o jogo a relê com outro pitch ou como 1x | −15% de GPU medido, com artefato | ≈ −4 ms | grande |
| 5 | **Resolve MSAA de média pelo hardware** (`vkCmdResolveImage` para uma imagem 1x, depois o resolve direto 1x) nas faixas 4x e nos 256x256 2x | achado 4 | ~−0,8 ms | médio |
| 6 | **Resolves minúsculos dentro do passe**, com a conversão R11G11B10 → 7e3 no fragment shader | achado 5 | −0,5 a −1 ms (a medir: o in-pass antigo não ganhou nos resolves grandes) | médio |
| 7 | **Resolve só na textura**: não gravar a memória compartilhada quando só a textura promovida lê o destino, com cópia de volta sob demanda | cópias 1280x720, +0,85 ms de gravar nos dois | −0,5 a −1 ms | grande |
| 8 | **Transferências restantes** (12,5 mil tiles/frame): draws 1x que só leem o depth 4x da cena (halo) desenhados em 4x com "pixels como amostras"; pular a transferência quando o primeiro uso sobrescreve a área inteira (reuso de 720t, cor → depth 3×/frame) | `VkXfer` | −0,5 a −1 ms | médio/grande |
| 9 | **JIT**: A/B de `a64_vmx_nan_fixup`, `a64_enable_host_guest_stack_synchronization` e `inline_leaf_max_instructions`, medindo a CPU por thread no teto | achado 6 | energia (código do jogo: 2 núcleos) | pequeno |
| 10 | **CPU menor**: XMA só nos contextos acionados; escrita de registradores do ring e cópia de constantes na thread de comandos | perfil S14 | ~0,1 núcleo | pequeno/médio |
| 11 | **Fidelidade**: segmento de query de oclusão aberto durante as transferências no passe; validação visual ampla (cidade, noite, chuva, túneis, replays) | nuvem 2026-09-28, AB8 | — | pequeno |
| 12 | **Medir a fluidez**: percentis do tempo de frame (1% mais lentos) nas ferramentas de A/B | — | — | pequeno |

Os itens 1–3 vêm antes: são pequenos, e o 3 decide a ordem dos itens 4–8.

## Situação depois do AB9 (2026-10-01, `ab9-results.md`)

| # | Resultado |
|---|---|
| 1 | Feito: spin park por assinatura, R11G11B10 e clear no passe do guest viraram quirks do Forza |
| 2 | Feito; o config global do app de teste migrou para `4px = true` |
| 3 | Feito: pixels 43%, custo fixo por draw 23% — o "early preamble" do Turnip; `ir3_debug = "noearlypreamble"` (quirk) GPU −0,8 ms |
| 4 | Feito, sem artefato: −4,5 ms (−14,8%), opção de qualidade desligada por padrão |
| 5 | Adiado: teto −0,8 ms (resolve lendo 1 amostra), ganho líquido ≤ ~0,5 ms |
| 6 | Descartado: teto −0,8 ms |
| 7 | Descartado: teto zero |
| 8 | Feito: pular as transferências que um clear por quad sobrescreve (quirk), GPU −1,1 ms (−3,1%) |
| 9 | Sem efeito além do ruído entre lançamentos; padrões mantidos, log de longjmp |
| 10 | Feito: XMA só nos contextos habilitados |
| 11 | Feito: query de oclusão em volta das transferências no passe; padrões finais validados parado e dirigindo (cidade, noite, chuva, túneis e replays ainda sem verificação) |
| 12 | Feito: frames > 37 / 50 / 70 ms por relatório |

## Próximos passos (v5)

| # | Caminho | Evidência | Ganho esperado | Esforço |
|---|---|---|---|---|
| 1 | Pular mais transferências sobrescritas: quads de depth *always* que não passam na checagem (stencil não substituído por inteiro, faixas de triângulos) | 1140 tiles/frame (9%) no rastreamento do S24 | ~−0,3 ms | pequeno/médio |
| 2 | Decodificar as fetch constants na CPU (o preâmbulo do pixel shader típico tem ~550 instruções) | S20: sem preâmbulo o draw pequeno cai para 1,6 µs, mas o pixel paga +3,3 ms | a medir, agora sem o early preamble | grande |
| 3 | Resolve MSAA de média pelo hardware nas faixas 4x | sonda 16: −0,8 ms de teto | ≤ ~0,5 ms | médio |
| 4 | Validação visual ampla (cidade, noite, chuva, túneis, replays) com navegação manual ou automação nova | — | fidelidade | médio |
| 5 | Expor `msaa_4x_as_2x` como opção de qualidade no app (o código Kotlin é do usuário) | −4,5 ms | escolha do usuário | pequeno |

## Reanálise v6 (2026-10-01, builds 54–59, tudo pela rede; `ab10-results.md`)

Base: S26 (estatísticas de pipeline, IR dos fragment shaders, uso de shaders
por passe, rastreamento de EDRAM com o motivo de cada transferência mantida),
S27 (código PPC/HIR/a64 das funções JIT mais quentes) e as sessões S28–S32.

### Achados

1. **O mesmo depth/stencil ia e voltava entre a vista 4x e a 1x várias vezes
   por frame.** A iluminação de 1280x720 marca stencil em 640x360 4x entre
   passes 1x: 4320 dos 8791 tiles transferidos por frame, mais 720 de cor.
2. **"Pular transferências" deixava passar casos fáceis:**
   - alfa sem pixel shader;
   - amostras como pixels;
   - *culling* em listas de retângulos (que o host nunca descarta);
   - Z fora de 0..1 com *depth clamp*;
   - altura de 722 linhas em vez de 720.
   Além disso, o interpretador de vertex shader lia mal os formatos
   compactados sem sinal.
3. **Cada fetch de textura 2D custa 5 instruções no pipe de textura** (4
   derivadas e um `samgq`). Trocar por LOD implícito não mudou o tempo além do
   ruído: o passe principal não é limitado por isso.
4. **O que resta de transferências (~2300 tiles/frame no b59, eram 8791 no
   b54):**
   - quads que cobrem só parte das fileiras de tiles que tomam;
   - listas de 8 retângulos;
   - vertex shaders com textura;
   - o atlas de sombra (stencil em uso);
   - a superfície 2x 256x256 limpa por um quad 4x (512 tiles);
   - fontes limpas por resolve (~900 tiles).
5. **Resolves:** o 1280x720 em 2_10_10_10 vai pelo shader "full" de conversão
   (0,62 ms contra 0,38 ms do 8888 em cópia rápida), provavelmente por *exp
   bias*.
6. **Driver:** Turnip Gen8 V37 expõe `VK_KHR_fragment_shading_rate`,
   `VK_EXT_multi_draw`, `VK_EXT_descriptor_buffer` e
   `VK_EXT_rasterization_order_attachment_access`.
7. **CPU:** thread de comandos 70% de um núcleo. O JIT grava o contexto a cada
   instrução PPC e 3 bytes de CR por comparação. É energia, não GPU.
8. **Passe principal:** 3 faixas de 1280x256 4x (tiling predicado), ~650
   draws cada.

### Caminhos (v6)

| # | Caminho | Evidência | Ganho | Esforço | Situação |
|---|---|---|---|---|---|
| 1 | Draws 4x com pixel shader simples nas amostras como pixels (`vulkan_samples_as_pixels_simple_ps`) | achado 1 | **GPU −1,7 ms (−5,2%)** | médio | **feito, quirk (S28)** |
| 2 | Mais casos em `skip_overwritten_transfers` e o interpretador corrigido | achado 2 | **GPU −1,4 ms** com o quirk ligado (S31) | pequeno | **feito** |
| 3 | LOD implícito em fetch 2D (`spirv_texture_implicit_lod`) | achado 3 | nenhum além do ruído (S29) | pequeno | opção desligada |
| 4 | Transferência parcial: recortar (*cutout*) a área que o quad cobre | achado 4 | 9 recortes/frame, **GPU −0,1 a −0,2 ms** (S37) | médio | **feito, quirk** |
| 5 | Draw 4x desenhado na superfície 2x dona dos tiles (x dobrado) | achado 4 | −512 tiles/frame, GPU igual (S38) | médio | opção desligada |
| 6 | Transferência de fonte limpa vira clear com o valor convertido | achado 4 | −1092 tiles/frame, GPU igual (S37) | médio | opção desligada |
| 7 | Resolve com *exp bias* sem o caminho "full" (escala no shader rápido) | achado 5 | não era *exp bias*: a gravação na textura (~0,08 ms, economiza 1,5 ms) e o formato (~0,09 ms) por resolve (S38) | médio | fechado |
| 8 | Variante por draw para gama/sinal (chave maior) ou gama por sRGB (opção de qualidade) | teto −1,9 ms (S35) | variante exata por *specialization constants* (`spirv_texture_sign_specialization`): **GPU −1,9 ms (−7,9%)**, mesma imagem (S40) | grande | **feito, quirk** |
| 9 | Taxa de shading 2x1/2x2 (VRS) no passe principal como opção de desempenho | achado 6 | 2x1 −0,7 ms, 2x2 −0,8 ms com blocos visíveis (S34) | médio | opção desligada |
| 10 | Juntar as 3 faixas do tiling predicado num passe | achado 8 | teto −5,3 ms com áreas falsas (S38); real ~1–2 ms (áreas) ou ~3–4 ms (juntar) | grande | pesquisa concluída, próxima rodada |
| 11 | JIT: promoção de registradores, CR mortos | achado 7 | energia | grande | não implementado (CPU não limita) |

## Situação depois do AB11

O plano v6 terminou com o código validado no b63 (S39, só os quirks): 29,99
fps, GPU 26,3 ms/frame no teto, 2314 tiles transferidos por frame (eram 8791
no b54), 9 transferências recortadas, mesma imagem parado e dirigindo. Depois
veio a especialização dos sinais das texturas (S40, GPU −1,9 ms; quirk no
b65; S41: 30,00 fps, GPU 22,8 ms/frame).

## Reanálise v7 (2026-10-01, b65; S42 pela rede)

Base: S42 (b65, só os quirks) com timestamps por passe/resolve, as
estatísticas do Turnip de cada pipeline (PipeStats) e o IR dos pixel shaders
de 1000+ instruções, dois frames de `pm4_bin_trace` (PipeUse), um frame de
rastreamento da EDRAM e de novo o custo por parte dos draws
(`vulkan_debug_draw_ceiling`). Dados em `b65-diag`; comparação com o S26
(`b54-diag`).

### Achados

1. **Mapa atual** (com timestamps, que somam ~0,7 ms): GPU 23,5 ms/frame.

   | Parte | ms/frame | Antes (S36, b60) |
   |---|---|---|
   | pixels | 9,5 (40%) | 11,7 |
   | vértices | 3,3 (14%) | 3,2 |
   | custo fixo por draw (~2900 draws) | 3,5 (15%) | 3,7 |
   | resto (resolves, transferências, clears, uploads, passes) | 7,2 (31%) | 7,7 |

   Por passe: o principal (1280x512, ~1850 draws) tem pixels 5,6, vértices
   2,8 e custo por draw 2,2 ms (**1,18 µs/draw**). Os passes com draws sem
   pixel shader (1280x2048, atlas de sombra) ficam em **~0,87 µs/draw**.

2. **O que a especialização fez nos shaders.** Instruções de FS ponderadas
   pelos draws do passe principal: **−27%**. *Stalls* estimados de espera de
   textura (SY) caíram à metade nos shaders grandes (300 → 153, 426 → 176).
   O preâmbulo encolheu 30–40%.

3. **O preâmbulo ainda pesa por draw.** Em média, ~170 instruções no pixel
   shader e ~130 no vertex shader, rodando em série no início de cada draw
   (sem *early preamble*, quirk do AB9).
   - É sobretudo decodificação de fetch constants (largura/altura → float e
     recíproco, *bias* de LOD → 2^bias, *exp adjust* → 2^exp, swizzle), mais
     constantes e *prefetch* de descritores.
   - No shader mais usado (388 draws/frame), o preâmbulo é 120 das 208
     instruções.
   - A diferença de custo por draw entre o passe principal e os passes sem
     pixel shader (1,18 contra 0,87 µs) sugere ~0,3 µs/draw do preâmbulo do
     FS.

4. **A fetch constant 13 escapa da especialização.** 82 dos 91 shaders
   grandes leem a fetch constant 13 (às vezes 8 ou 17). A especialização só
   cobre as fetch constants 0–7, então essas buscas ainda têm desvios em volta
   das amostras.

5. **Pipe de textura.** Cada busca 2D são 4 derivadas + 1 amostra (os 5
   *cat5* por textura). Ou seja, 80% das instruções do pipe de textura são
   derivadas. O LOD implícito foi neutro no AB10, mas na época as amostras
   ainda estavam presas em desvios.

6. **Vertex shaders: a emulação de "0 × x = 0" do Shader Model 3.**
   - Cada multiplicação vira `min(|a|,|b|)` + `cmps == 0` + `mul` + `sel`.
   - Isso é ~35–40% das instruções dos VS do passe principal (500–1145
     instruções).
   - Sem só esse bit (sem FMA e sem mudar arredondamento), as posições ficam
     idênticas para valores finitos; só o sinal do zero muda. A objeção de
     *z-fighting* do AB7 valia para o pacote 11, que incluía FMA.
   - Nos pixel shaders ela já está desligada (quirk `spirv_ps_relaxed_math`).

7. **Resolves fora do caminho direto por um detalhe de formato.**
   - Os formatos `k_2_10_10_10_FLOAT_AS_16_16_16_16` (12) e
     `k_2_10_10_10_AS_10_10_10_10` (10) chegam crus em
     `TryDirectHostResolveCopy`. A chave do render target guarda o formato
     de armazenamento (3 e 2), e a comparação falha.
   - São ~2,6 resolves por frame (faixas 4x 1280x256/1280x208 e um 1280x720)
     que vão pelo dump da EDRAM e não gravam na textura promovida. As texturas
     de destino são carregadas de novo (`texload k_2_10_10_10 2^20 gpu` 0,11
     ms, entre outras).

8. **Resolve 4x de cor: o caminho direto é o mais lento.** Por resolve
   1280x256, ele custa ~0,47 ms de dispatch, contra ~0,40 ms do dump da EDRAM
   + cópia. O shader direto lê as 4 amostras de cada pixel com pouca
   eficiência; vale um shader melhor ou o resolve por hardware.

9. **Uploads de memória escrita pela GPU que não foram servidos pelos
   resolves:** ~0,5 ms/frame (8888 com 2^20 texels 0,16; 2_10_10_10 2^20 0,11;
   2_10_10_10 2^19 com mips 0,19). Mais os `k_24_8` 2^20 "cpu" (0,28 ms; mapa
   de sombra escrito em parte pela CPU, já conhecido).

10. **A cadeia de passes e resolves minúsculos:**
    - ~72 resolves ≤ 128x128 por frame (1,23 ms) e ~50 passes de 1 draw (0,71
      ms);
    - custo fixo de ~17 µs por resolve e ~14 µs por passe;
    - teto das cópias no AB9: 0,8 ms.

11. **Faixas do tiling** (b65): 456, 904 e 500 draws, ou seja, a faixa do
    meio tem o dobro. O jogo já recorta por faixa parte dos draws (máscaras
    `A`, `8`, `28`, `20`); o resto é repetido. Juntar as faixas tiraria ~860
    execuções. Pelo custo atual (~1,2 µs fixo + ~1,5 µs de vértices por draw),
    são **~2–2,5 ms**.

12. **CPU:** a thread de comandos gasta ~3,7 µs por draw (11 ms/frame, ~44% de
    um núcleo); o código do jogo ocupa ~2 núcleos. É energia e calor, não fps
    no teto.

### Caminhos (v7)

| # | Caminho | Evidência | Ganho estimado | Esforço |
|---|---|---|---|---|
| 1 | **LOD implícito de novo**, sobre a especialização: A/B com reinício da opção existente `spirv_texture_implicit_lod` | achado 5 | até ~1 ms se o pipe de textura limita; zero se não | mínimo |
| 2 | **VS sem a emulação de "0 × x = 0"**: A/B com `spirv_vs_math_experiment = 1`; se ganhar, opção `spirv_vs_relaxed_math` + quirk, com checagem de *z-fighting* dirigindo | achado 6 | 0,3–1 ms | pequeno |
| 3 | **Especialização pelas texturas usadas**, não pelas fetch constants 0–7: as 8 primeiras fetch constants distintas do shader, cobrindo a 13 | achado 4 | 0,2–0,5 ms | pequeno |
| 4 | **Formatos 12 e 10 no caminho direto do resolve**: normalizar para o formato de armazenamento ao comparar com a chave | achado 7 | 0,2–0,4 ms (uploads e dumps) | pequeno |
| 5 | **Decodificar as fetch constants na CPU**: valores prontos por draw nas constantes do sistema, com preâmbulos encolhidos | achado 3 | 0,5–1,5 ms (e para todo jogo) | médio/grande |
| 6 | **Resolve 4x de cor mais rápido**: shader direto que leia as amostras em bloco, ou resolve por hardware + cópia 1x | achados 8 e S33 (teto 0,7 ms) | 0,2–0,6 ms | médio |
| 7 | **Uploads de memória da GPU não servidos**: descobrir quem escreve cada um (8888 2^20, 2_10_10_10 2^19 com mips) | achado 9 | ≤ 0,5 ms | médio |
| 8 | **Cadeia minúscula**: cópia no passe (com a conversão R11G11B10 → 7e3 no fragment shader) ou barreiras mais leves | achado 10 | ≤ 0,8 ms | médio |
| 9 | **Juntar as 3 faixas do tiling** ("EDRAM virtual" para essas faixas, resolves e clears por faixa nas linhas certas, draws das faixas 1 e 2 pulados) | achado 11; teto −5,3 ms (S38) | ~2–2,5 ms | muito grande |
| 10 | **Validação** quente e dirigindo (cidade, noite, chuva) e os quirks exatos como padrão para outros jogos depois de testar 2–3 títulos | — | sustentar 30 fps quente | pequeno |
| 11 | **Opções de qualidade no app** (a interface Kotlin é do usuário): MSAA 4x lógico em 2x (−4,5 ms), taxa de shading 2x1 (−0,7 ms) | AB9, S34 | escolha do usuário | pequeno |
| 12 | **CPU por draw** na thread de comandos (perfil novo com simpleperf) | achado 12 | energia | médio |

Ordem sugerida:
- primeiro os itens 1 a 4: pequenos, exatos (exceto o 2, que é exato para
  valores finitos) e medidos com A/B com reinício;
- depois o 5, que ataca o custo fixo por draw de todos os jogos;
- o 9 é o maior ganho que resta, mas também o maior risco.

## Situação depois do AB12 (`ab12-results.md`)

Os 12 caminhos da v7 foram medidos (S43–S52, builds 66–77, tudo pela rede).
GPU no teto de 30 fps: ~22,3 ms no fim da v6, **17,7 ms** no fim da v7:

| # (v7) | Resultado | Situação |
|---|---|---|
| 1 | LOD implícito: GPU −1,6 ms | quirk do Forza |
| 2 | VS sem "0 × x = 0": −0,7 ms, sem *z-fighting* dirigindo | quirk do Forza |
| 3 | sinais pelas texturas usadas: −0,4 ms | padrão |
| 4 | formatos 12/10 no resolve direto: −0,1 ms | padrão |
| 5 | fetch constants decodificadas na CPU: +0,2 ms (o preâmbulo não limita); a parte por pixel do teto — o *exp adjust* zero por pipeline — −0,2 ms | desligado / padrão |
| 6 | resolves com os formatos conhecidos pelo shader: 7e3 → 2_10_10_10 −0,8 ms, demais formatos −0,4 ms | padrão |
| 7 | uploads da GPU não servidos: profundidade → 8888 sem ganho; cube map com mips e sombra da CPU não servíveis | investigado |
| 8 | resolves minúsculos no passe com 7e3: −0,15 ms, +68 passes | desligado |
| 9 | as 3 faixas do tiling num passe só: −1,1 ms (2916 → ~1960 draws/frame), mesma imagem parado e dirigindo | quirk do Forza |
| 10 | validação final no mesmo build (S52): v6 22,4/22,1 ms, v7 17,6/17,7 ms — **−4,6 ms (−21%)**, mesma imagem | feito |
| 11 | opções de qualidade no app: documentadas para a interface Kotlin | documentado |
| 12 | CPU por draw: perfil com símbolos, nenhum ponto dominante | documentado |

## Reanálise v8 (2026-10-02, b77; S53–S55 pela rede)

Base: o build final da v7 (b77), com os padrões e os quirks.
- S53 (`b77-diag`, `b77-draws`):
  - timestamps por passe e por resolve;
  - estatísticas do Turnip por pipeline (PipeStats), com o uso por passe
    (PipeUse);
  - um frame de rastreamento da EDRAM;
  - o custo por parte dos draws.
- S54 (`b77-sys`):
  - estatísticas de replay (`vulkan_replay_stats`);
  - CPU e memória do sistema;
  - perfil de CPU com símbolos;
  - avisos de desempenho do Turnip (`turnip_debug = "sysmem,perf"`).
- S55 (`b77-sys`): contadores de hardware da CPU por thread
  (`simpleperf stat`).
- Os logs da validação final (S52, parado; o log é coletado antes do trecho
  dirigindo).

### Achados

1. **Mapa atual** (S53, com timestamps): GPU 19,1 ms/frame.

   | Parte | ms/frame | S42 (b65) |
   |---|---|---|
   | pixels | 8,6 (45%) | 9,5 |
   | vértices | 1,3 (7%) | 3,3 |
   | custo fixo por draw (~1960 draws, ~1,1 µs cada) | 2,2 (12%) | 3,5 |
   | resto (resolves, cargas de textura, passes) | 7,0 (37%) | 7,2 |

   A v7 tirou sobretudo vértices e custo por draw (com as faixas juntas). O
   resto não mudou e virou o segundo maior bloco.

2. **Por passe** (S53, média dos últimos 22 s; GPU 17,7 ms/frame nesse log):

   | Trabalho | ms/frame | Passes/frame | Draws/frame |
   |---|---|---|---|
   | passe principal 1280x720 4x (as 3 faixas juntas) | 6,6 | 7,1 | 869 |
   | passes 1x de 1280 de largura | 3,1 | 41 | 509, dos quais ~300 só de depth (em parte com os VS do atlas de sombra) |
   | cadeia minúscula (≤128x128) | 0,63 | 49 | 48 |
   | 6 faces 2x do cube map | 0,62 | 8 | 245 |
   | atlas de sombra 1040x2528 | 0,40 | 4 | 156 |
   | resolves | 5,3 | — | 101 resolves |
   | cargas e cópias de textura | ~0,7 | — | — |

3. **Os pixels continuam limitados por amostra**, não pelo pixel shader. A taxa
   de shading 2x2 só tirava 0,8 ms (AB11), contra −4,5 ms do MSAA 2x lógico;
   cortar ALU dos pixel shaders (FP16, por exemplo) tende a render pouco. No
   passe principal, metade dos draws com pixel shader usa *alpha to coverage* e
   17% usa teste alfa. O S54 checou:
   - **UBWC**: com `TU_DEBUG=perf`, o Turnip não desliga a compressão de
     nenhuma imagem do Forza. Pelo código do Mesa (`tu_image.cc`), o
     `D32_SFLOAT_S8_UINT` do depth float24 comprime o plano de profundidade; só
     o plano de stencil fica sem UBWC.
   - **LRZ**: 172 mil avisos, todos pelos motivos do AB3: o quad de clear com
     depth *always* que abre o passe, `CmdClearAttachments`, stencil escrito
     pelo teste de depth, pixel shader que escreve depth.
   - **Shading por amostra**: nenhum pipeline roda por amostra; só a conversão
     float24 no pixel shader faria isso, e ela está desligada.

4. **Resolves: 5,3 ms para 101 resolves por frame.**

   | Grupo | ms/frame | Custo por pixel |
   |---|---|---|
   | 3 faixas 4x (cor e depth) | 1,60 | 0,91–0,93 ns |
   | tela cheia 1x (2× cor 2_10_10_10 com clear, 2× 8888, 1× depth) | 1,45 | 0,27 ns (depth) a 0,40 ns |
   | depth das sombras (1024², 2× 520², 512²) | 0,40 | 0,19 ns |
   | cube map e médios (6× 256x256 2x, 6× 320x192, 2× 640x360) | 0,83 | — |
   | ~60 minúsculos (≤ 160 px) | 1,02 | 15–25 µs cada, ~7 µs de dispatch |

   - Gravar a memória do guest não custa nada (sonda do AB9). O custo é ler o
     render target e gravar a textura.
   - Os resolves 4x custam por pixel 2,3–3,4× os 1x: a leitura MSAA de uma
     amostra (depth) ou a média de quatro (cor).
   - Todo resolve de depth busca também o stencil
     (`kDirectHostResolveDepthFlagHasStencil` sempre ligado), e o plano de
     stencil do D32S8 não tem UBWC.
   - As 3 faixas ainda saem em 3 resolves de cor e 3 de depth.

5. **Quebras de passe**: 122 passes por frame, 93,5 reabertos no mesmo
   framebuffer depois das barreiras dos resolves. São dependências reais (o
   resolve no passe do AB12 só tirou 0,15 ms).

6. **Estado por draw** (S54, `VkReplay`), por frame:
   - 1906 draws;
   - 2870 `BindDescriptorSets` (1,5 por draw) e 3180 sets de descritores
     escritos;
   - 610 trocas de pipeline;
   - ~1370 comandos de estado dinâmico, ~300 deles repetidos;
   - 229 barreiras, 157 dispatches, 87 `ClearAttachments`.

   As constantes ficam num UBO dinâmico, religado a cada draw que muda
   constantes. O custo fixo de ~1,1 µs por draw na GPU deve estar na carga de
   estado e de constantes, já que o preâmbulo não limita (AB12). O driver expõe
   `VK_KHR_push_descriptor`, `VK_EXT_descriptor_buffer` e `VK_EXT_multi_draw`,
   mas não `VK_KHR_performance_query`: não há contadores da GPU.

7. **CPU** (perfil de 15 s do S54 e contadores de hardware do S55, parado). O
   processo usa 3,0 núcleos.

   | Thread | Núcleos | Instruções/frame | Ciclos/frame | IPC |
   |---|---|---|---|---|
   | Guest CPU 5 (a mais ocupada do jogo) | 0,62 | 107 M | 41 M | 2,6 |
   | GPU Commands | 0,61 | 53 M | 36,5 M | 1,46 (38% de *stall* de backend) |
   | demais Guest CPU (0–4) | 1,34 | 120 M | 83 M | 1,3–2,3 |
   | XMA | 0,15 | 3,7 M | 7,6 M | 0,5 (54% de *stall* de frontend) |

   - Nas threads do jogo, 82% do tempo é código JIT e 9% são syscalls (futex
     de esperas reais).
   - A thread de comandos executa ~18 mil instruções por draw do jogo (2916
     draws do guest por frame). O tempo dela se divide em:
     - emulador, 60%;
     - libc, 19% (`memmove` 8,7%, syscall 3,3%, `mprotect` 1,9%);
     - Turnip, 17%;
     - `clock_gettime`, 3% — vem só dos logs de tempo dos testes.
   - `LoadShader` calcula o XXH3 do microcódigo a cada `IM_LOAD`
     (`LoadShader` 1,8% e XXH3 1,4% da thread).

8. **Medir CPU por tempo engana; por instruções, não.**
   - O tempo de draw da thread de comandos varia entre aberturas do mesmo
     build e da mesma config: no b76, 15,4 e 9,3 ms/frame nos dois braços
     base (5,1 e 3,2 µs por draw).
   - Do b70 para o b77, o aumento se espalha por quase todas as funções. O que
     a v7 acrescentou por draw (descarte das faixas, sinais das texturas
     usadas) custa menos de 0,3 ms/frame; o resto é núcleo e frequência.
   - Por contadores de hardware (`perf_event_paranoid = −1`), duas janelas de
     10 s na mesma abertura deram 53,1 e 53,3 M instruções/frame na thread de
     comandos e 106 e 108 M na Guest CPU 5: ~1% de diferença. Falta
     medir a repetição entre aberturas.
   - As threads rodam em média a 1,5–1,9 GHz, só nos núcleos 2–6. Durante o
     jogo, o sistema limita os clusters de 3,0 e 2,8 GHz e o X4 a ~2,7 GHz.
   - Os dois processos do app estão no cgroup `top-app`.

9. **Sistema**:
   - Fora o emulador, kernel, interrupções e serviços somam ~1 núcleo.
   - Não há recuperação de memória durante o jogo (0 páginas varridas/s, 3
     *swap-ins*/s); o swap de ~3 GB é histórico. O emulador ocupa 1,9 GB de
     RSS.
   - A sessão de *performance hint* do áudio falha (`createSession failed (10
     threads)`), então o app não usa ADPF.

10. **Fluidez** (S52, parado no ponto de referência, com o tráfego passando):
    frames acima de 37 ms são 0,1–0,3% do total. Os piores (45–50 ms, ~2 em
    40 s) vêm de um envio com 22–25 ms de GPU (o normal é ≤ 10 ms). Um deles
    coincide com o jogo lendo do disco um carro do tráfego, o que gera uma
    rajada de cargas de textura num frame só. Os logs do S52 terminam antes do
    trecho dirigindo.

11. **60 fps**:
    - A GPU precisaria de ≤ 16,7 ms e está em 17,7; o MSAA 2x lógico
      resolveria.
    - O limite é a CPU: a Guest CPU 5 gasta 41 M ciclos por frame e a thread
      de comandos 36,5 M. A 60 fps, cada uma precisaria de 81–91% de um
      núcleo no teto atual de 2,7 GHz, sem folga para picos.
    - Também faltaria um patch da comunidade (baixá-lo exige permissão).

### Caminhos (v8)

| # | Caminho | Evidência | Ganho estimado | Esforço |
|---|---|---|---|---|
| 1 | **CPU por contadores de hardware nas ferramentas de A/B**: instruções e ciclos por frame por thread (`simpleperf stat`) no `restart_ab` e no `forza_auto_ab`; refazer com essa métrica os A/B de CPU descartados pelo ruído (AB9: `a64_vmx_nan_fixup`, sincronização de pilha, inline de folhas) | achado 8 | metodologia; destrava os itens 2, 3 e 7 | pequeno |
| 2 | **Thread de comandos mais leve**: perfil por instruções (`simpleperf record -e instructions`); shader em cache por endereço, com vigia de escrita, sem XXH3 a cada `IM_LOAD`; descobrir o que o `memmove` copia; menos `mprotect` e syscalls por frame; texturas por `VK_KHR_push_descriptor` | achados 6 e 7 | 15–30% dos 36,5 M ciclos/frame (~0,1–0,2 núcleo) | médio |
| 3 | **ADPF**: sessão de *performance hint* para as threads do frame (comandos, Guest CPU 5, 0 e 1), com alvo de 33,3 ms e a duração real de cada frame; consertar a sessão do áudio | achado 9 | energia e variância (medir por ciclos/frame e frequência média) | pequeno/médio |
| 4 | **Resolves**: (a) as 3 faixas num resolve de 1280x720 por tipo (cor e depth); (b) sonda que pula o stencil nos resolves de depth e, se render, buscá-lo só quando o destino lê o stencil; (c) resolve direto nas faces e mips do cube map (carga + cópia de 0,25 ms) | achado 4 | (a) ~0,1 ms; (b) 0–0,4 ms; (c) ≤ 0,25 ms | pequeno/médio |
| 5 | **Picos de streaming**: espalhar as cargas de textura de um frame (orçamento por frame) | achado 10 | picos de 45–50 ms abaixo de 37 ms | médio |
| 6 | **Opções no app** (a interface Kotlin é do usuário): MSAA 2x lógico (`msaa_4x_as_2x`, troca em tempo real), VRS 2x1, A2C → teste alfa; e o inverso, **AF 16x** (`anisotropic_override = 5`), gastando parte da folga da GPU em estradas mais nítidas (medir custo e imagem) | achados 1 e 3 | −4,5 / −0,7 / ~−1,2 ms; AF: a medir | pequeno |
| 7 | **JIT**: medir por instruções e reduzir as instruções emitidas por instrução PPC (registradores do guest mantidos entre blocos, CR mortos, emulação do VMX) | achado 7: 1,96 núcleo nas threads do jogo, 82% em código JIT, IPC 2,6 na Guest CPU 5 | 10–30% do código do jogo | muito grande |
| 8 | **Validação e generalização**: noite, chuva, cidade, túneis e replays; os quirks exatos como padrão depois de 2–3 outros títulos | — | fidelidade; todos os jogos | médio |
| 9 | **60 fps** (pesquisa; baixar o patch exige permissão) | achado 11 | só com os itens 2 e 7 e o MSAA 2x; hoje no limite da CPU | médio |

Ordem sugerida:
- primeiro o 1: é pequeno e torna mensuráveis os itens de CPU;
- depois o 2 e o 4 (thread de comandos e resolves), medidos com a métrica nova
  e com timestamps;
- o 6 depende da interface do usuário;
- o 7 é o maior ganho de CPU que resta e também o maior risco.

## Situação depois do AB13 (`ab13-results.md`)

Os 9 caminhos da v8 foram medidos (S56–S67 e a validação final S66,
builds 78–88, tudo pela rede). CPU medida por instruções e ciclos por
frame (contadores de hardware); GPU no teto de 30 fps.

| # (v8) | Resultado | Situação |
|---|---|---|
| 1 | CPU por contadores de hardware no `restart_ab`/`forza_auto_ab` (`-Stat`), `forza_cpustat.py`, `jit_dump_compare.py` | feito |
| 2 | thread de comandos −6% de instruções (52,7 → 49,4 M/frame); caches de `LoadShader` e de `QueryRegionInfo` sem ganho, retirados | feito |
| 3 | ADPF: sessão do áudio consertada; a da thread de comandos não muda clock nem tempo | áudio feito; comandos desligado |
| 4 | resolves (a)–(c): tetos de 0,05 / 0,2 / 0,25 ms | não implementados |
| 5 | picos: cargas de texturas escritas pela GPU, resolves 1280x720 e uploads a cada ~10 s; não derrubam frames a 30 fps | documentado |
| 6 | opções: MSAA 2x −3,1 ms, AF 16x +0,5 ms, A2C → teste −0,2 ms, VRS 2x1 inconclusivo, modo 40 fps | documentado (interface do usuário) |
| 7 | JIT exato e barato: Guest CPU 5 104,8 → 56–58 M instruções/frame (−46%), ciclos −34%; `ppc_single_float_keep_nan = false` descartado (corrompe dados) | padrão + quirk do Forza (NaN do host na aritmética, folhas de 32) |
| 8 | FH2, Halo: Reach, RDR com o JIT novo e os quirks exatos de GPU: imagens corretas, nenhum erro novo | quirks seguem do Forza |
| 9 | sem o teto de vblank: ~49 fps em tempo real, GPU e thread de comandos no limite, 94 °C; modo 40 fps (`guest_display_refresh_cap = false` + `framerate_limit = 40`) a 39,7 fps com GPU a 71%, mas SoC 94–96 °C | documentado |
| — | validação final (S66, b77 × b87, ABBA): Guest CPU 5 −38% de instruções e −21% de ciclos, comandos −5%, CPU do processo −7%, GPU igual (17,7/17,6 ms), mesma imagem | feito |

### Próximos caminhos (depois da v8)

Com a GPU a 17,6 ms no teto de 30 fps e a CPU do jogo bem mais leve, o que
resta, em ordem de valor:

| # | Caminho | Evidência | Ganho estimado |
|---|---|---|---|
| 1 | **Modo 40 fps com controle térmico**: a opção na interface (com aviso) e, se o usuário quiser, um teste longo para ver onde o clock cai | S66: 39,7 fps, GPU 71%, SoC 94–96 °C | +33% de fluidez enquanto o aparelho aguenta |
| 2 | **Texturas escritas pela GPU servidas pelo resolve direto** (as cargas de 1 M texels dos picos de ~10 s: formato de leitura ≠ do resolvido) | AB13 seção 5 | tira os picos do orçamento de 25 ms do modo 40 fps |
| 3 | **Microcódigo com vigia de escrita** (`LoadShader` sem ler o microcódigo a cada `IM_LOAD`) | AB13 seção 2: hash e comparação custam o mesmo | ≤ 4% da thread de comandos |
| 4 | **JIT**: o resto do custo exato (~14% da Guest CPU 5 contra o modo inexato), prólogo da sincronização de pilha (~16 instruções por chamada, enxugável para ~11), endereço de retorno materializado duas vezes por chamada | AB13 seção 7 | 3–8% das threads do jogo |
| 5 | **Quirks exatos de GPU como padrão** depois de jogo de verdade em 2–3 títulos (o S64 só cobriu menus) | AB13 seção 8 | todos os jogos |
| 6 | Thread de comandos: o que o `memmove` copia (5,8%), `WriteRegisterRangeFromRing` (6,9%) | perfil do b82 | ≤ 10% da thread |

## Compatibilidade com outros jogos (builds 89–98, `compat-results.md`)

| Jogo | Situação | Próximo passo |
|---|---|---|
| Forza Horizon 2 | quirks do FH1 (b90): ~29,9 fps, GPU 20–22 ms (era 24–27 fps, 36–41 ms) | — |
| Need for Speed: Most Wanted | crash de ~40% das aberturas **corrigido** (b98, ABA no pool de jobs); **em jogo** desde a b101 (quirks de `fmadz` e memexport; a devolução do memexport grava só os bytes que o desenho mudou) | desempenho em jogo (~19 fps; esperas síncronas do memexport) |
| Sonic Unleashed | um crash não reproduzido (SIGTRAP no thunk de resolução) | repetir aberturas no b98: pode ter sido o mesmo ABA |
| Halo 4 | vídeos Bink pretos (memexport `k_8_8_8_8_A`?) | captura do frame do vídeo |
| GTA IV / RDR | 22 / 25 fps, thread de comandos no limite | medir em jogo e perfilar a thread de comandos |

## Becos sem saída (não repetir sem fato novo)

LRZ (AB3); extents reais no tiling predicado; thread de replay; estacionar todo
spin-backoff; `readback_resolve = none`; `submit_draws` ≠ 1300; backoff de
`WAIT_REG_MEM`; GMEM automático do Turnip; A2C → teste alfa; MSAA 2x por
simples reescrita (quebra os truques de EDRAM do Forza); medir energia pelo
`Charge counter` com o USB ligado; laço sem `DontUnroll`;
`precise_interpolation` (inerte no Turnip); syscall de futex sem ninguém
esperando (já evitado); UBWC perdido por formato mutável (não se aplica aos
formatos do Forza). Do AB9: resolve só na textura (teto zero); resolves
minúsculos no passe (teto 0,8 ms); `nopreamble` e `nouboopt` do ir3; opções
do JIT (`a64_vmx_nan_fixup`, sincronização de pilha, folhas inline de 32 —
nada além do ruído de ±20% entre lançamentos); comparar CPU por função entre
lançamentos isolados. Do AB10: stencil "não usado" no atlas de sombra (algum draw usa); orientação
de listas de retângulos para *culling* (o host nunca as descarta). Do
AB11: quad 4x na superfície 2x e transferência de fonte limpa como clear
(exatos, sem ganho além do ruído: todas as transferências restantes custam
~0,36 ms); taxa de shading 2x2 (blocos visíveis); a diferença de formato do
resolve 2_10_10_10 (~0,09 ms por resolve); áreas reais do tiling continuam
bloqueadas (teto agora −5,3 ms, ver a v7). Do AB12: fetch constants
decodificadas na CPU (o preâmbulo encolhe 24% sem ganho — ele não limita);
resolve no passe com a conversão 7e3 (73% dos resolves no passe, −0,15 ms, +68
passes); resolves de profundidade gravando na textura 8888 que lê a mesma
memória (ela ainda perde uma escrita). O LOD implícito, neutro no AB10, rende
−1,6 ms com os sinais especializados (AB12). Da v8:
D24S8 no lugar do D32S8 para o depth float24 (a profundidade já tem UBWC; só o
stencil não tem, e os draws observados testam com `GEQUAL`, Z invertido, que
perderia precisão longe); UBWC perdido (nenhuma imagem do Forza, S54); LRZ
(as mesmas causas do AB3); memória (sem recuperação de páginas durante o
jogo); cpuset (o processo `:emu` está em `top-app`); contadores da GPU (sem
`VK_KHR_performance_query`); perfil do sistema inteiro (`simpleperf record -a`
recusado); a CPU por draw "maior" na v7 (é núcleo e frequência). Do AB13:
`ppc_single_float_keep_nan = false` (corrompe dados de 32 bits copiados por
FPR que pareçam NaN sinalizador); sincronização de pilha desligada como
quirk (risco de `longjmp`); escrever o FPCR menos vezes no JIT (~0,1 ns por
escrita nos A720); cache de `LoadShader` por comparação (custa o mesmo que o
XXH3); cache de `QueryRegionInfo` (0 acertos); ADPF na thread de comandos
(não muda o clock); resolves 4a–4c (tetos de 0,05–0,25 ms). Do NFS: para o
ABA de reservas não bastam o caminho por software, a geração checada antes
do store (nem por CAS na geração) ou ordem TSO — a checagem tem de estar
dentro da janela exclusiva do store; e o log por chamada com
`log_guest_calls_at` muda o timing (lock global): use o anel.
