# AB7 — resolves diretos, resolve gravando na textura e o passe principal (builds 20 a 25, 2026-09-29/30)

POCO F7 (Adreno 825, Turnip Gen8 V36, sysmem), pacote de teste
`xendroid.compose.fork.opt`, cena de referência (carro parado na defensa a
2,5 mi). Configuração comum: `render_target_7e3_as_r11g11b10 = true` e o spin
park direcionado (`829F04A8`, modo 1). A/B em tempo de execução: a mesma
sessão alterna a opção por propriedade `debug.xendroid.*` em braços de 30 s
(com timestamps por passe: valem as proporções) ou 40 s (sem timestamps: FPS
real), julgados braço a braço. Opções lidas na inicialização: um lançamento
por braço (`-RestartArms`).

## Resumo

| Mudança | Resultado | Decisão |
|---|---|---|
| **Resolves diretos com 4 px por thread** (`vulkan_direct_host_resolve_4px`, build 20) | faixas de depth 4x **−22 a −24%**, cor 1x +6 a +7% | só com fonte MSAA ≥ 2x ou gravando na textura; ligado por padrão (exato) |
| **Resolve grava direto na textura** (`vulkan_direct_host_resolve_to_texture`, builds 21 → 25) | cargas de textura **3,02 → 0,75 ms/frame**; GPU −1,4 ms; sem timestamps, celular frio: **+1,4% FPS** (GPU −4,5%) | ligado no Forza Horizon via `game_quirks.cc` |
| Chave de textura sem `packed_mips` quando não há mips (build 25) | a mesma superfície 1280×720 deixa de ser carregada duas vezes | geral (exato) |
| Matemática relaxada 3 (`spirv_ps_relaxed_math`) | passe principal −7% (AB5); sem diferença visível parado, em 45 s de direção e no menu | ligada no Forza Horizon via `game_quirks.cc` |
| Anomalia do `depth 512x512` | 1 resolve/frame de 1,12 ms dentro do dispatch; flush e ociosidade entre submissões descartados | sem ação |
| Laço principal sem a dica `DontUnroll` (item 1.3) | NIR perde o laço, mas o ir3 fica igual (+0,13% instruções, +0,9% ciclos de nop) | descartado |
| `precise_interpolation` (item 1.7) | inerte neste aparelho (o Turnip não expõe `fragmentShaderBarycentric`) | sem ação |
| Teto da emulação de alpha-to-coverage (item 1.6) | 55% dos draws do passe principal usam A2C; sem nenhum A2C: passe principal −14,8%, +8,1% FPS | — |
| **Alpha-to-coverage pelo hardware** (`host_alpha_to_coverage`, build 28) | passe principal **16,56 → 15,32 ms (−7,5%)**, GPU −0,9 ms, +1,4 a +1,6% FPS; folhagem igual parado e dirigindo | ligado no Forza Horizon via `game_quirks.cc` |
| Teto da matemática do vertex shader (item 4.1) | passe principal −2,7% (−0,4 ms), +1,1% FPS | sem ação (arrisca a invariância de posição) |
| Turnip Gen8 V37 "patched" contra o V36 (passo 6) | **+1,4% FPS**, GPU −2%, mesma imagem | driver recomendado (configuração do usuário) |

## 1. Resolves diretos com 4 pixels por thread (build 20)

Os resolves diretos (compute lendo os render targets do host) processavam 8
pixels por thread, gravando em `x` e `x + 4` — meia linha de cache por
instrução, o mesmo padrão que o untile antigo. As variantes de 4 pixels por
thread (`XE_RESOLVE_HOST_4PX`) gravam linhas inteiras. A/B com timestamps
(`debug.xendroid.resolve_4px`), todas as variantes 4 px contra todas 8 px:

| resolve | 8 px | 4 px |
|---|---|---|
| depth 1280×256 kD24FS8 4x | 0,805 ms | **0,609** |
| depth 1280×208 kD24FS8 4x | 0,172 | **0,134** |
| cor 1280×720 k_8_8_8_8 1x | 0,369 | 0,392 |
| cor 1280×720 k_2_10_10_10 1x | 0,204 | 0,218 |

Com fonte MSAA a leitura das amostras domina e 4 px por thread ajuda; com
fonte 1x o dobro de threads custa mais do que as linhas inteiras poupam. A
regra ficou: 4 px só com MSAA ≥ 2x (ou ao gravar na textura, cujas variantes
são de 4 px). Ganho líquido estimado ~0,25 ms/frame — abaixo do previsto no
plano (0,8–1,5 ms). O resultado é idêntico bit a bit.

## 2. Resolve gravando direto na textura (builds 21 a 25)

~10 texturas de tela cheia eram recarregadas por frame (untile da memória
compartilhada para a imagem), porque a memória delas acabara de ser escrita
por um resolve. Os resolves diretos agora também gravam os texels na
textura "promovida" (com alias `R32_UINT` de armazenamento), e a carga seguinte
é pulada (servida). Variantes de shader `*_tex_cs` (15 novas): cor rápida 4 px,
cor completa 32 bpp e depth 4 px (convertendo 24 bits unorm ou 20e4 float como
a carga faz).

### 2.1 Primeira versão (build 21) e o diagnóstico

A regra herdada do caminho in-pass exigia que resolves **deste frame**, de
largura total, cobrissem a textura. Resultado: cargas 3,36 → 1,97 ms/frame,
GPU −0,6 a −0,8 ms, FPS +1,0/+2,1/+2,0% nos três pares de braços.

O diagnóstico novo (`vulkan_resolve_dest_diag`, `debug.xendroid.resolve_dest_diag`;
linhas `VkServeMiss`/`VkResolveNoStore`) mostrou por que metade das cargas
sobrava:

1. **Entre frames**: várias texturas são amostradas antes do resolve do frame,
   mas a imagem já tinha o resolve do frame anterior — a regra "deste frame"
   as recarregava.
2. **Memória com alias**: o Forza reusa a mesma memória para superfícies de
   formatos diferentes (ex.: `1DAC5000` recebe, todo frame, um resolve de
   depth `k_24_8_FLOAT` 1280×720 em três faixas e um de cor `k_8_8_8_8`
   256×256); o mapa de texturas promovidas guardava uma por endereço, e o
   resolve achava a textura errada.
3. **Quadrantes**: o atlas de sombra 1024×1024 é escrito por resolves de
   512×512; a regra exigia largura total.

### 2.2 Validade rastreada (builds 24 e 25)

A cobertura por frame foi trocada por **rastreamento de validade** por
textura promovida: um *watch* próprio sobre a memória do nível base (o watch
da textura some quando ela fica desatualizada). A carga normal torna a imagem
válida; um resolve que grava os mesmos texels nela (mesmo formato, pitch e
endian) a mantém válida; qualquer outra escrita — CPU, resolve em outro
formato, resolve pelo caminho da EDRAM, memexport — a invalida; faixas que
reescrevem todas as linhas de cima a baixo a revalidam. A busca da textura
passou a ser por formato, pitch e endian entre várias por endereço. Isso
também fecha um buraco da regra antiga (um resolve pelo caminho da EDRAM no
mesmo frame não era visto) e acrescenta a checagem de endian que faltava. A
invalidação geral da memória (`InvalidateGpuMemory`, que não dispara watches)
zera o rastreamento.

No build 25, uma chave de textura com `packed_mips` mas sem mips (e lados
> 16 texels, onde o empacotamento não afeta o nível base) virou a chave sem
`packed_mips`: o jogo amostrava a mesma superfície 1280×720 pelas duas chaves,
e a segunda era recarregada ~2×/frame.

Com timestamps (build 25):

| braço | grava? | GPU ms | cargas | resolves | passes |
|---|---|---|---|---|---|
| 1 | não | 37,05 | 3,13 | 7,11 | 26,20 |
| 2 | sim | 35,68 | 0,84 | 7,96 | 26,72 |
| 3 | não | 36,87 | 3,12 | 7,10 | 26,22 |
| 4 | sim | 35,51 | 0,86 | 8,00 | 26,65 |

Sem timestamps (build 25, celular a 23–29 °C):

| par | não grava | grava | Δ FPS | GPU ms |
|---|---|---|---|---|
| 1 | 28,37 | 28,74 | +1,3% | 31,59 → 30,08 |
| 2 | 28,51 | 28,69 | +0,6% | 31,51 → 30,42 |
| 3 | 28,34 | 28,94 | +2,1% | 31,78 → 30,22 |

Frio, a GPU leva ~30 ms num frame de ~35 ms: ela já não é o único limite, e o
ganho de GPU (−4,5%) aparece pela metade no FPS. Quente, a GPU volta a
dominar (os +16% da carga direto na imagem com o celular quente, no AB6).

Custos que ficam: gravar na textura encarece os resolves (+0,85 ms: a
variante 4 px em fonte 1x e os `imageStore` por texel) e o passe `160x8192`
fica +0,2 ms (provavelmente a textura, carregada logo antes do uso, estava no
cache L2; servida, vem da memória). Cargas que sobram: duas `k_24_8`
1024×1024 (0,41 ms — memória reusada por resolves de outros formatos, e um
resolve de depth 520×520 que às vezes passa pela EDRAM) e uma `k_8_8_8_8`
(0,20 ms — o alias de `1DAC5000`).

Capturas 0/1/0/1 (builds 21 e 24): diferenças só de movimento (serrilhado de
borda, nuvens, trânsito), sem blocos nem texturas trocadas.

## 3. Matemática relaxada como padrão do Forza

`spirv_ps_relaxed_math = 3` (sem a emulação do "0 × qualquer coisa = 0" do SM3
e sem o arredondamento a 21 bits depois das transcendentes, nos pixel shaders)
tirou 7% do passe principal no AB5. Validação visual automática
(`vis_relaxed.ps1`): um lançamento por braço (exato × relaxado), carro parado,
45 s acelerando com uma captura a cada 3 s e o menu de pausa — 17 capturas por
braço, comparadas lado a lado e pela fração de pixels quase pretos/brancos e
de pixels extremos isolados (a marca de NaN/infinito): 0–1 pixel isolado nos
dois braços em todas as capturas. A única captura diferente (tela toda preta,
sem HUD) é o fade do próprio jogo ao reposicionar o carro depois de sair da
pista — a trajetória diverge entre os lançamentos. A config do braço relaxado
foi conferida no aparelho. Ficou como quirk do Forza Horizon; noite, chuva e
túneis não são alcançáveis pela automação e não foram vistos.

## 4. A anomalia do `depth 512x512`

Um resolve por frame de 1,12 ms (máx. 1,5), enquanto o 1024×1024 do mesmo
render target (o atlas de sombra 1040×2528, base 720t, pitch 13t) custa
0,23 ms com 4× o trabalho. Descartados: flushes pendentes (a sonda mediu
1–3 µs) e a ociosidade na fronteira de submissão (uma submissão única deixa
pior: 0,985 × 0,857 ms). Os timestamps são `BOTTOM_OF_PIPE` gravados depois
das barreiras, então é tempo real do dispatch. Sem causa identificada; baixa
prioridade (~2% da GPU).

## 5. Passe principal e passe de sombras

- **Laço sem dica (1.3)**: com `spirv_unhinted_single_pass_main_loop` o NIR
  perde o laço nos shaders sem saltos, mas o código ir3 final fica igual
  (163 pipelines: +0,13% instruções, +0,9% ciclos de nop). Opção removida.
- **`precise_interpolation` (1.7)**: o caminho baricêntrico exige
  `fragmentShaderBarycentric`, ausente no Turnip deste aparelho — a opção só
  muda a chave do pipeline.
- **Modos alfa (1.6)**, contagem de 2 frames (`debug.xendroid.pm4_bin_trace 2`,
  linhas `AlphaModes`):

  | passe | draws | sem alfa | só teste | alpha-to-coverage |
  |---|---|---|---|---|
  | principal 1280×512 | 1793 | 30% | 15% | **55%** |
  | sombras 1280×2048 | 215 | 51% | **48%** | 0,5% |
  | atlas 1040×2528 | 38–63 | 0–3% | 0% | 97–100% |

  O Vulkan nunca liga o `alphaToCoverageEnable` do hardware: o
  alpha-to-coverage é emulado no pixel shader (limiares com dither + saída
  `gl_SampleMask`), e o epílogo com teste alfa + A2C tem 100–160 instruções
  ir3 estáticas (atrás de um desvio uniforme quando o A2C está desligado).
- **Tetos** (um lançamento por braço, 35 s com timestamps; opções só de
  medição, que mudam a imagem):

  | braço | FPS | GPU ms | passe principal | sombras |
  |---|---|---|---|---|
  | base (2 lançamentos) | 25,18 | 35,4 | 16,06 | 5,62 |
  | `spirv_vs_math_experiment = 11` | 25,46 | 34,8 | 15,62 | 5,56 |
  | `spirv_ps_no_alpha_to_coverage_experiment` | **27,23** | **32,8** | **13,68** | 5,63 |

  A matemática Xenos no vertex shader custa pouco (−0,4 ms no teto) e não dá
  para tirá-la sem arriscar posições diferentes entre passes — sem ação. Tirar
  o alpha-to-coverage inteiro poupa 2,4 ms do passe principal (15%), muito
  mais do que as instruções da emulação justificam.
- **Alpha-to-coverage pelo hardware** (`host_alpha_to_coverage`, build 28): o
  pipeline liga o `alphaToCoverageEnable` (bit novo na descrição do pipeline,
  zero nas descrições já armazenadas, então o cache de pipelines segue
  válido) e os pixel shaders deixam de escrever `gl_SampleMask`. Passes só de
  profundidade (o atlas de sombra, ~100% A2C) não têm a saída de cor 0 da qual
  o hardware tiraria o alfa e mantêm a emulação — sem isso as sombras da
  vegetação ficariam sólidas. Com timestamps (um lançamento por braço):

  | braço | passe principal | sombras | passes | GPU ms | FPS |
  |---|---|---|---|---|---|
  | emulado | 16,56 | 4,73 | 25,28 | 33,2 | 27,95 |
  | hardware | **15,32** | 4,93 | 24,32 | 32,3 | 28,41 |

  Sem timestamps (dois lançamentos por braço, celular frio): 29,49 → 29,91
  FPS, GPU 29,1 → 28,7 ms — o FPS fica preso perto de 29,9 pelo lado da CPU
  quando o celular está frio. O hardware recupera metade do teto: a outra
  metade é o próprio efeito da cobertura parcial com MSAA 4x (amostras
  diferentes no pixel, escrita de profundidade dependente da cobertura), que
  ele também paga. A folhagem fica igual parada (recorte em resolução total)
  e dirigindo; só o padrão de dither das bordas é o do hardware, não os
  deslocamentos de limiar do Xenos. Ligado no Forza Horizon.

## 6. Turnip mais novo

Turnip Gen8 V37 "patched" (StevenMXZ, Vulkan 1.4.363, fornecido pelo usuário;
instalado só no app de teste) contra o V36 em uso, um lançamento por braço
alternando os drivers (`vulkan_lib_path` por jogo), 35 s sem timestamps, com o
build 27:

| lançamento | driver | FPS | GPU ms |
|---|---|---|---|
| 1 | V36 | 28,91 | 30,0 |
| 2 | V37 | 29,13 | 29,5 |
| 3 | V36 | 28,81 | 29,8 |
| 4 | V37 | 29,36 | 29,1 |

O V37 ganha nos dois pares (+0,8% e +1,9% de FPS, GPU −2%) e a imagem é a
mesma nas capturas. Trocar o driver do app principal é escolha do usuário
(configuração, não código).

## 7. Reanálise: onde está o tempo agora e próximos caminhos

GPU por frame com timestamps (build 25, resolves na textura): **~35,6 ms**
(passes 26,7 — principal 16,3, sombras 5,7; resolves 8,0; cargas de textura
0,75; o resto ~0,2). Sem timestamps e com o celular frio: GPU ~30 ms num frame
de ~35 ms — **a GPU já fica ~15% ociosa**; quente, ela volta a limitar.

Caminhos, do maior para o menor ganho esperado:

1. **O resto do custo do alpha-to-coverage** (~1,2 ms do passe principal):
   inerente à cobertura parcial com MSAA 4x. Só sai trocando qualidade — por
   exemplo, uma opção de desempenho que troque o A2C por teste alfa (bordas da
   vegetação serrilhadas); é escolha de qualidade do usuário, não de código.
2. **Lado da CPU com o celular frio**: com a GPU ociosa ~5 ms por frame, o que
   limita é a espera pelo guest (`WAIT_REG_MEM` ~9 ms/frame, guest esperando
   56% do tempo) e a thread de comandos (53% de um núcleo). Medir a linha do
   tempo de um frame (quando o CP espera o guest e quando a GPU fica sem
   trabalho) antes de mexer; `submit_draws` e o estacionamento do spin são as
   alavancas conhecidas.
3. **Quebras de render pass**: ~210 passes por frame, ~164 encerrados por
   barreiras de buffer + imagem (≈15 µs cada, ~2,5–3 ms/frame). Auditar quais
   barreiras são necessárias (ex.: barreiras de faixas da memória compartilhada
   que o passe seguinte não lê) pode juntar passes.
4. **Resolves (8 ms/frame)**: (a) os resolves minúsculos da cadeia de
   downsample (8×8 a 128×128, ~50/frame, ~1,2 ms) pagam custo fixo por
   dispatch/barreira; (b) o `depth 512x512` do atlas (0,8–1,1 ms, 5× o custo
   por pixel do 1024×1024 do mesmo RT) segue sem explicação — próxima hipótese:
   estado de energia da GPU depois de uma espera do CP pelo guest (cruzar com as
   marcas `W` do `pm4_bin_trace`); (c) gravar na textura custa +0,85 ms:
   variantes de 8 px por thread para fonte 1x e gravar a memória compartilhada
   só quando alguém a lê (resolve "só textura", grande) cortariam parte disso.
5. **Cargas que sobram (0,75 ms)**: duas `k_24_8` 1024×1024 (0,41 ms — um
   resolve de depth 520×520 que às vezes vai pelo caminho da EDRAM, e memória
   reusada por resolves de cor de outros formatos) e o alias `k_8_8_8_8` de
   `1DAC5000` (0,20 ms — exigiria o resolve de depth gravar também os bits crus
   na textura de cor).
6. **Sem retorno (medido)**: matemática Xenos no vertex shader (teto −0,4 ms,
   com risco), laço sem dica, `precise_interpolation`, variante "só teste
   alfa" isolada (o teste alfa em si é barato; o custo estava no A2C).

Achado de fidelidade (não de desempenho): nos passes só de profundidade a
emulação de A2C roda com a amostra única do atlas (1x) — o hardware não tem
como fazer isso sem saída de cor, por isso a opção nova preserva a emulação lá.
