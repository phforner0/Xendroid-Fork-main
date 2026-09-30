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
| Teto da emulação de alpha-to-coverage (item 1.6) | 55% dos draws do passe principal usam A2C; sem a emulação: passe principal **−14,8%**, GPU −2,6 ms, **+8,1% FPS** | alpha-to-coverage pelo hardware (`host_alpha_to_coverage`) *(medição na sessão S5)* |
| Teto da matemática do vertex shader (item 4.1) | passe principal −2,7% (−0,4 ms), +1,1% FPS | sem ação (arrisca a invariância de posição) |
| Turnip mais novo (passo 6) | só há o Gen8 V36 no aparelho | depende de um driver fornecido pelo usuário |

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
  para tirá-la sem arriscar posições diferentes entre passes — sem ação. A
  emulação do alpha-to-coverage custa 2,4 ms do passe principal (15%), muito
  mais do que as instruções justificam: a suspeita é a própria saída
  `gl_SampleMask`, que impede o teste de profundidade antecipado em 55% dos
  draws. Daí a opção `host_alpha_to_coverage`: o pipeline liga o
  alpha-to-coverage fixo do hardware (bit novo na descrição do pipeline, zero
  nas descrições já armazenadas) e os pixel shaders deixam de emulá-lo; o
  padrão de dither passa a ser o do hardware, não os deslocamentos de limiar
  do Xenos. *(medição e checagem visual na sessão S5)*

## 6. Turnip mais novo

O app de teste só tem `Turnip_Gen8_V36` em `compose/driver`. O A/B depende de
um driver mais novo fornecido pelo usuário.
