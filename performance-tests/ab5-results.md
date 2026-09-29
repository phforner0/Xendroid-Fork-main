# AB5 — build 10 no aparelho (2026-09-29)

Build 10 = `main` depois do PR #2 (sessão na nuvem) + correção do bit 1 da
matemática relaxada (MUL/MAD vetorial). Pacote de teste
`xendroid.compose.fork.opt`, POCO F7 (Adreno 825, Turnip Gen8 V36, sysmem),
cena de referência (carro parado na defensa a 2,5 mi), braços de 40 s, A/B com
reinício e ordem espelhada (A B C D D C B A) para a deriva térmica pesar igual
em todas as variantes. Configuração comum: `render_target_7e3_as_r11g11b10 =
true` e o spin park direcionado (`829F04A8`, modo 1). Tempos com
`-Passes` (timestamps por passe: valem as proporções).

## Resumo

| Mudança | Resultado | Decisão |
|---|---|---|
| **Reenvio do controle de energia da GPU** (`adrenotools_turbo_reassert_seconds`, build 12) | depois de o app perder o foco (cortina de notificações, diálogo, outro app) o jogo rodava a **metade do FPS até reiniciar** (GPU 48 → 101 ms/frame); pedindo de novo após a retomada e a cada 5 s, fica normal | ligado por padrão (5 s) |
| `spirv_texture_sign_branch` + `spirv_fast_precision_rounding` (exatas) | **+3,0% FPS, −3,7% GPU** | ligadas no Forza Horizon via `game_quirks.cc` |
| `spirv_ps_relaxed_math = 3` (bits 1+2, sobre as exatas) | passe principal −5% a −7%, FPS +1,3% a +3,8%; fotos em movimento sem artefatos | opção por jogo para testar; não é padrão |
| Spin park direcionado (após as correções por fibra) | mesmo FPS (19,56 × 19,67), guest estacionado 66% do tempo | mantém a recomendação |
| `spirv_multiply_zero_test_on_bits` (exata) | passe principal **+7%** (pior) | não usar no Forza |
| `vulkan_in_pass_resolve` | −0,7% FPS (ruído); cobre só 9% dos resolves | manter desligado |
| Quebra de render pass (medida) | ~15 µs cada; ~3 ms/frame no total | alvo secundário |

## Fase A — diagnóstico

O primeiro lançamento depois de horas com o celular parado rodou **1,7× mais
lento em tudo** (passe principal 32 ms, resolves 15,7 ms, GPU 83 ms/frame)
com a tela de título normal (2,3 ms): a anomalia já vista antes. O lançamento
seguinte voltou ao patamar do AB4. Protocolo: descartar o primeiro lançamento
depois de uma pausa longa. As proporções da fase A valem:

- **Fins de render pass: 209 por frame, 203 por barreiras pendentes**
  (103 buffer+imagem, 84 só buffer, 16 só imagem), 3 por submissão, 2 por troca
  de render target. Quase toda quebra é o ciclo resolve → carga de textura.
- **Resolves (100/frame): a cópia domina**; o clear das faixas é ~0,3 ms de
  1,4 ms. O item 2.3 do plano (clear no `loadOp`) não compensa.
- **Cargas de textura: ~45 por frame, ~11 ms de ~52 ms de GPU (~21%)**, quase
  tudo em ~12 cargas de tela cheia (1280×720) por frame:

  | Formato (origem) | por frame | ms cada |
  |---|---:|---:|
  | `k_2_10_10_10` (GPU) | 4,0 | 0,76 |
  | `k_8_8_8_8` (GPU) | 2,9 | 0,75 |
  | `k_24_8` (GPU) | 1,9 | 1,00 |
  | `k_24_8` ("cpu": nem todas as páginas marcadas como escritas por resolve) | 1,9 | 0,84 |
  | `k_24_8_FLOAT` (GPU) | 1,0 | 0,75 |

  Cada carga faz compute (untiling) → buffer temporário → cópia buffer →
  imagem, com duas rodadas de barreiras: a textura é lida e escrita duas vezes
  (~20 GB/s efetivos, ~25% da banda).
- **`TexSigns`** (passe principal 1280×512): 5.060 texturas/frame, 40% unsigned,
  **60% gama** — abaixo do empate estático do ramo (~77%), mas perto.
- **`AlphaModes`** (passe principal): 30% especializados sem alfa, 15% só teste
  alfa, **55% alpha-to-coverage** (emulado no shader com `SampleMask`); no
  passe 1280×2048: 51% sem alfa, 48% só teste.

## Fase B — opções exatas

| Variante | FPS (2 braços) | GPU/frame | Passe principal | Passe 1280×2048 |
|---|---:|---:|---:|---:|
| base | 18,13 / 18,37 | 51,9 / 51,1 ms | 18,93 ms | 7,29 ms |
| sign branch | 18,61 / 18,60 | 50,9 / 50,5 ms | 18,73 ms | 6,54 ms |
| arredondamento rápido | 18,48 / 18,60 | 50,7 / 50,4 ms | 18,08 ms | 7,25 ms |
| **as duas** | **18,82 / 18,78** | **49,4 / 49,7 ms** | 18,43 ms | 6,32 ms |

Os dois braços "as duas" ficam acima dos dois "base" em FPS e GPU. O sign
branch ganha pouco no passe principal (60% gama) e 10–13% no passe de
sombra/profundidade. Como as duas são exatas (mesma imagem para toda
entrada), a build 11 as liga para o Forza Horizon em `game_quirks.cc`
(prioridade de config por jogo: o config do usuário ainda as desliga).
**A/B futuros de base no Forza precisam desligá-las explicitamente.**

## Fase F — resolve no passe

| Variante | FPS (2 braços) | GPU/frame | Passe principal | Passe 1280×2048 |
|---|---:|---:|---:|---:|
| exatas | 19,37 / 19,34 | 48,2 / 48,6 ms | 17,97 ms | 6,23 ms |
| + `vulkan_in_pass_resolve` | 19,23 (média) | 48,5 ms | 18,10 ms | 7,21 ms |

Estável (sem falhas de GPU), mas só **9% dos resolves** (28 mil de 305 mil)
passam: 68% são recusados por "formato não equivalente bit a bit" (o HDR 7e3
guardado como R11G11B10 precisa de conversão para o formato do guest), 5% por
fonte com MSAA, 5% por não haver passe aberto.

## Fase C3 — matemática relaxada e teste de zero em bits

Bateria baixa: um lançamento por braço, com trava de 12%. O primeiro braço
(base) se perdeu numa queda momentânea do adb.

| Variante | FPS | GPU/frame | Passe principal |
|---|---:|---:|---:|
| `spirv_ps_relaxed_math = 3` | 19,50 / 19,75 | 48,2 / 47,7 ms | **16,83 / 16,73 ms** |
| `spirv_multiply_zero_test_on_bits` | 18,56 / 18,55 | 51,1 / 51,4 ms | 19,60 / 19,41 ms |
| exatas (braço final, o mais quente) | 18,91 | 50,1 ms | 18,09 ms |
| exatas na fase F (referência) | 19,37 / 19,34 | 48,2 / 48,6 ms | 17,68 / 18,26 ms |

O passe principal com os bits 1+2 fica abaixo de todos os braços "exatas" do
dia (17,7–18,7 ms): ~−7%, com o bit 1 agora cobrindo MUL/MAD. O teste de zero
em bits é pior: o Mesa transforma a forma em float em código mais barato.

## Fase E — matemática relaxada com base na mesma sessão, e fotos

| Variante | FPS | GPU/frame | Passe principal |
|---|---:|---:|---:|
| exatas | 18,74 | 50,6 ms | 18,23 ms |
| `spirv_ps_relaxed_math = 3` | 19,40 | 48,7 ms | 17,35 ms |

Com as fases C3 e F: o passe principal cai ~5–7% e o FPS sobe ~1,3–3,8%.
Seis capturas em movimento de cada (partida da cena de referência, dia,
floresta, estrada): sem pontos pretos/brancos, céu, vegetação, carro e sombras
normais; a parcela de pixels extremos é igual nas duas. Validado só nessas
cenas — noite, túneis, cidade, chuva, menus e replays ainda não. Opção por
jogo para testar; não é padrão.

## Fase G — custo de uma quebra de render pass (build 11)

`debug.xendroid.extra_pass_breaks` em tempo real, um lançamento:

| Braço | FPS | GPU/frame |
|---|---:|---:|
| base (dois trechos) | ~19,4 / 19,0 | ~49,7 / 49,6 ms |
| +92 quebras/frame, sem barreira (32) | 18,86 | 51,2 ms |
| +92 quebras/frame, com barreira (−32) | 18,93 | 50,9 ms |

**~15 µs por quebra**, com ou sem barreira: as ~209 quebras de hoje custam
~3 ms/frame (~6% da GPU). Cortar metade renderia ~1,5 ms; a cópia extra das
cargas de textura (buffer → imagem, ~12 de tela cheia por frame) é o alvo
maior.

**Achado mais importante do dia: uma pausa deixa a GPU 2× mais lenta até
relançar.** No fim da fase G o emulador foi pausado e retomado duas vezes (o
app perde o foco — cortina de notificações, diálogo, outro app — e a atividade
pausa o emulador após 250 ms); daí em diante a GPU levou ~100 ms/frame em vez
de ~50, com os mesmos draws, passes e resolves, até o jogo ser relançado na
fase E. O primeiro lançamento da fase A (depois de horas parado) já começou
assim, sem pausa. Hipótese: o estado de energia da GPU pedido uma única vez
na criação da instância Vulkan (`adrenotools_force_max_clocks`) não sobrevive
a esses eventos. A build 12 pede de novo após cada retomada e a cada 5 s
(`adrenotools_turbo_reassert_seconds`, `debug.xendroid.turbo_reassert`);
o experimento está na seção seguinte.

## Experimento do turbo (build 12)

Um lançamento; a pausa é induzida por adb abrindo e fechando a cortina de
notificações (`cmd statusbar expand-notifications` / `collapse`), como um
jogador faria; `debug.xendroid.turbo_reassert` liga e desliga o reenvio.

| Trecho | FPS | GPU/frame |
|---|---:|---:|
| base (reenvio desligado; só o pedido do início) | 19,8 | 47,6 ms |
| após pausa/retomada, reenvio desligado | **9,6** | **100,9 ms** |
| liga o reenvio (pedido refeito na hora) | 19,5 | 48,3 ms |
| após pausa/retomada, reenvio ligado | 18,0 | 52,7 ms |
| após pausa/retomada, reenvio desligado de novo | **9,6** | **101,9 ms** |
| liga de novo | 19,3 | 48,2 ms |

A causa está provada nos dois sentidos: o estado forçado de clock da GPU se
perde quando o app perde o foco, e o governador sozinho roda esta carga a
metade da velocidade. O primeiro lançamento lento da fase A (sem pausa) é
compatível com o mesmo mecanismo; o reenvio periódico também o cobre.
Consequência: com `adrenotools_force_max_clocks` desligado (o padrão), a GPU
fica sempre no governador — a opção ligada vale ~2× nesta carga.

Spin park, na velocidade normal: desligado 19,44 / 19,89 FPS, ligado 19,56
(o `forza_segstats.py` somou um braço falso, porque a expressão também casa a
linha `spin_park_mode = 1` do config aplicado no início).

## Pendente

- Bit 2 sozinho e o teto dos vertex shaders (`spirv_vs_math_experiment = 11`).
- Validação visual dos bits 1+2 em noite, túneis, cidade, chuva e replays.

## Observações de protocolo

- O sysfs da KGSL não é legível pelo `shell` neste aparelho: as linhas de
  energia do `fh_auto.sh` saem como `?` (antes da correção, `d`, porque o
  `?` sem aspas virava curinga e casava com `/d`).
- Sob carga, a bateria cai ~0,3% por minuto mesmo na USB do PC; uma rodada de
  8 lançamentos consome ~11 pontos.
- Só 8% das esperas estacionadas do spin park são acordadas pelo progresso: é
  o esperado (cada espera de ~14,7 ms estaciona ~26 vezes e só a última termina
  com o progresso).
