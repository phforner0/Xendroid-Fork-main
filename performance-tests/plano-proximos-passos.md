# Plano de desenvolvimento e otimização — Forza Horizon no POCO F7 (v4, com a situação depois do AB9 e a v5)

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
lançamentos isolados.
