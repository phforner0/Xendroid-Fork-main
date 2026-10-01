# AB9 — plano v4: perfil do Forza nos quirks, custo por draw, MSAA 2x lógico, transferências (builds 43 a 53, 2026-09-30 a 10-01)

POCO F7 (Adreno 825, Turnip Gen8 V37 "patched", sysmem), pacote de teste
`xendroid.compose.fork.opt`, cena de referência (carro parado na defensa a
2,5 mi). Métrica principal: **GPU ms/frame no teto de 30 fps do jogo** (fps só
com o celular quente). A/B em tempo de execução por propriedade
`debug.xendroid.*` (braços de 30 s), ou um lançamento por braço para opções
lidas na inicialização; julgamento braço a braço, cada braço contra a média
dos vizinhos (o celular esquenta durante a sessão).

## Correção de método: o "primeiro braço" era a inicialização

O `tools/forza_segstats.py` corta os braços pelas linhas de troca de
propriedade, mas o **dump do config na inicialização** também lista cada
opção como `nome = valor` e casava com o marcador. Isso abria um braço extra
desde o início do log — menus, carregamento, ~17 ms de GPU por frame e fps
baixo — somado à média do valor que o config tinha. É a origem do "primeiro
braço depois de 'scene stable' costuma sair fora" anotado desde o AB5 (às
vezes "mais frio", às vezes um travamento). Agora as linhas dentro do dump são
ignoradas. Todos os números abaixo foram recalculados; as tabelas por braço
(`arms.json`) dos AB anteriores não mudam, só as médias por valor que
incluíam esse braço.

## Resumo

| # | Caminho | Resultado | Situação |
|---|---|---|---|
| 1 | Perfil do Forza nos quirks | spin park com assinatura (Guest CPU 0: 96–100% → 39–46% de um núcleo); R11G11B10; clear no passe do guest (mesmo GPU no teto, passes 205 → ~133) | quirks |
| 2 | Resolve na textura sem o `4px`, `UPDATE_from`, anel de 512 pares | o config global antigo foi migrado para `4px = true` | feito |
| 3 | Teto por draw | pixels 43% da GPU; **custo fixo por draw 7,6 ms/frame** (2,6 µs por draw); vértices 0,6 ms | diagnóstico |
| 3b | Preâmbulo dos shaders | o "early preamble" do a7xx+ custa ~1,6 µs por draw pequeno; sem ele: **GPU −0,8 ms (−2,4%)** | quirk `ir3_debug = "noearlypreamble"` |
| 4 | MSAA 2x como 4x lógico | **GPU −4,5 ms (−14,8%)**, sem o contorno brilhante | opção, desligada por padrão |
| 5 | Resolve MSAA pelo hardware | teto (ler 1 amostra em vez de 4): −0,8 ms; um resolve pelo hardware ainda pagaria o próprio blit | adiado (≤ ~0,5 ms) |
| 6 | Resolves minúsculos no passe | teto (pular as cópias) −0,8 ms | descartado |
| 7 | Resolve só na textura | teto +0,05 ms (nenhum ganho) | descartado |
| 8 | Transferências | 41% dos tiles transferidos iam para alvos que começam com um clear por quad; pulando as sobrescritas: **GPU −1,1 ms (−3,1%)**, frames > 37 ms 23,6% → 6,1% | quirk `skip_overwritten_transfers` |
| 9 | JIT | nenhuma das três opções passou do ruído entre lançamentos (±20% de CPU entre duas bases iguais) | padrões mantidos; log de longjmp |
| 10 | CPU menor | worker do XMA só nos contextos habilitados | feito |
| 11 | Fidelidade | query de oclusão fechada em volta das transferências no passe (0 casos na cena); padrões finais parado e dirigindo sem defeitos | feito |
| 12 | Fluidez | frames > 37 / 50 / 70 ms contados no `GpuFrame` | feito |

Somados aos quirks, os padrões novos do Forza tiram ~2 ms de GPU por frame
no teto (−0,8 ms do preâmbulo e −1,1 ms das transferências, cada um medido
contra os vizinhos) e trazem para os usuários o spin park, o R11G11B10 e o
clear no passe. O MSAA 2x lógico (−4,5 ms) fica como opção de qualidade.

## 1. Quirks do Forza (S15, build 43; S18, build 46)

O app de teste tinha três configurações medidas que os usuários não recebiam.
Agora são quirks do Forza (`game_quirks.cc`, prioridade de config por jogo: o
config por jogo do usuário ainda vence):

- **Spin park da espera da GPU do Direct3D.** `spin_park_guest_functions`
  aceita `ENDEREÇO:HASH`: o JIT só estaciona a função se o XXH3 dos seus
  primeiros 64 bytes (16 instruções) bater — outra versão do executável fica
  intocada. O log mostra `SpinPark: guest function 829F04A8 signature
  B864F65007F969C0 (matches)`. S15 (top por thread, dois lançamentos de cada):
  Guest CPU 0 em **39–46%** com os quirks contra **96–100%** sem.
- **`render_target_7e3_as_r11g11b10`** (+3,1% fps / −3,5% GPU no AB3, imagem
  igual): o log mostra `7e3 render targets as B10G11R11_UFLOAT enabled`.
- **`vulkan_resolve_clear_in_guest_pass`.** O A/B por reinício da S15 ficou
  dominado pelo calor; na S18 em tempo real (3 × 3 braços de 30 s, cada um
  contra os vizinhos) a GPU no teto é a mesma (ligado 29,0 / desligado 28,7
  ms, deriva térmica de 27 → 31 ms na sessão), o replay da thread de comandos
  cai 3,4 → 3,3 ms e os frames acima de 37 ms caem de 1,2% para 0,4%. É o que
  junta os passes com as barreiras no resolve (205 → ~133, AB8), e **todas as
  validações visuais desde o AB7 rodaram com ele**: o config global do app de
  teste o tinha ligado.

## 2. Resolve na textura, padrões migrados, anel de timestamps

- O resolve direto na textura não depende mais do `vulkan_direct_host_resolve_4px`:
  só a escala de resolução ou um shader de cópia o recusam, e ele sempre usa a
  variante de 4 px.
- `UPDATE_from_bool(vulkan_direct_host_resolve_4px, ...)`: configs salvos com o
  padrão antigo (`false`) passam ao novo. Conferido: o config global do app de
  teste agora mostra `vulkan_direct_host_resolve_4px = true` (estava `false`
  desde builds anteriores ao PR #7).
- O anel de timestamps por passe tem 512 pares por submissão (perdia ~260
  pares por relatório com 192).

## 3. Onde vai o tempo da GPU: teto por draw (S16, build 44)

`debug.xendroid.draw_ceiling` tira partes de cada draw do guest: 1 = scissor
1x1 (nada é rasterizado; vértices, estado e passes ficam), 2 = também só a
primeira primitiva, 3 = nenhum comando de draw. Um lançamento, braços de 30 s:

| modo | GPU/frame sem timestamps | passe principal com timestamps |
|---|---:|---:|
| normal (3 braços) | 32,5 ms | 14,99 ms |
| sem pixels | 18,6 ms | 6,98 ms |
| 1 primitiva, sem pixels | 17,9 ms | 6,56 ms |
| sem draws | 10,3 ms | 0,49 ms |

- **Pixels: ~14 ms (43%)** — o trabalho dos shaders.
- **Custo fixo por draw: 7,6 ms (23%)**, 2,6 µs por draw nos ~2900 draws do
  frame; no passe principal, 3,3 µs por draw.
- Vértices: 0,6 ms.
- O resto (10,3 ms): resolves, transferências, cargas de textura, abertura
  e fechamento de passes.

### O custo por draw é o preâmbulo antecipado (S20, build 46)

Um lançamento por configuração do compilador ir3 (`ir3_debug`, cache de
pipelines separado), com os modos 2 e 3 do teto e timestamps:

| `IR3_SHADER_DEBUG` | passe principal | 1 primitiva | sem draws | por draw | passes 1280x2048 | soma dos passes |
|---|---:|---:|---:|---:|---:|---:|
| (padrão) | 12,52 ms | 5,60 ms | 0,46 ms | 2,8 µs | 5,20 ms | 21,15 ms |
| `nopreamble` | 13,97 | 3,72 | 0,52 | 1,6 | 5,68 | 24,02 |
| `noearlypreamble` | 12,45 | **2,72** | 0,47 | **1,2** | 4,73 | 20,75 |
| `nouboopt` | 12,81 | 5,45 | 0,44 | 2,7 | 4,89 | 21,36 |

Os preâmbulos decodificam as fetch constants de cada textura (tamanho e seu
inverso, bias de expoente, LOD, modos de sinal; ~550 instruções no pixel
shader típico do passe principal, ~130 no vertex shader). Sem preâmbulo, o
custo por draw cai, mas cada pixel passa a refazer essa conta (+3,3 ms no
passe principal). **Sem o "early preamble" do a7xx+** o custo por draw cai
para 1,2 µs e o passe principal completo fica igual: o preâmbulo antecipado
sincroniza cada draw pequeno com o anterior. O AB8 tinha descartado a
decodificação na CPU supondo que o early preamble fosse "de graça" por rodar
sobreposto — não é com draws pequenos. A UBO→constantes (`nouboopt`) não
pesa.

## 4. `noearlypreamble` (S21, build 46)

Um lançamento por braço na ordem ABBAAB, sem timestamps, cada braço com o
próprio cache de pipelines; o celular foi de 19 °C a 36 °C (bateria 21 →
11%):

| lançamento | GPU/frame | fps |
|---|---:|---:|
| 1 base | 30,7 ms | 30,01 |
| 2 noearly | 29,7 ms | 29,82 |
| 3 noearly | 33,4 ms | 29,48 |
| 4 base | 34,7 ms | 28,45 |
| 5 base | 35,2 ms | 28,09 |
| 6 noearly | 35,1 ms | 28,14 |
| **média base / noearly** | **33,5 / 32,7 ms** | **28,83 / 29,13** |

**GPU −0,8 ms (−2,4%)**, +1% de fps; a ordem ABBAAB ainda desfavorece o
noearly (posição média 3,7 contra 3,3 numa sessão que esquentou). Virou quirk
do Forza: `ir3_debug = "noearlypreamble"` (só o Turnip lê; o quirk entra
junto com o config por jogo, antes de o driver carregar). O cache de
pipelines agora tem um arquivo por conjunto de flags do ir3
(`4D5309C9.noearlypreamble.vk.bin`), porque um driver pode devolver binários
do cache só pelo shader: o primeiro lançamento depois da atualização recompila
os pipelines.

## 5. MSAA 2x como 4x lógico (S19, build 46)

`msaa_4x_as_2x` reescrito: o layout 4x da EDRAM do guest é mantido e só as
imagens do host têm 2 amostras (bit `host_2x` na chave do render target e do
render pass). A amostra 4x do guest `s` (h | v<<1) vira a amostra do host
`(s >> 1) ^ 1`, e a do host `h` volta como `(h ^ 1) << 1` (a amostra de cima
do host é a 1, como no 2x nativo). As transferências (inclusive as que leem o
depth do host), o dump da EDRAM e os resolves diretos sintetizam o layout 4x a
partir da superfície 2x — o que faltava à versão do AB8, cujo artefato vinha
de o Forza reler a cor da cena 4x com metade do pitch. Contagens de oclusão
em dobro (o bit de meia amostragem na escala do ZPD). Liga e desliga em tempo
real pelas transferências comuns entre as chaves.

| | 4x | 2x lógico |
|---|---:|---:|
| GPU/frame (3 × 3 braços de 30 s) | 30,4 ms | **25,9 ms (−14,8%)** |
| fps | 29,38 | 29,61 |
| frames > 37 ms | 4,6% | 3,3% |

Sem o contorno brilhante em volta do carro (recortes em tamanho real do carro
parado) e sem erros de Vulkan; uma direção de 30 s no modo 2x com uma captura
a cada 5 s não mostrou defeitos (`b46-msaa2x/`). Continua **desligada por
padrão**, como o plano v3 decidiu para as opções que trocam qualidade: liga com
`msaa_4x_as_2x = true` em `[GPU]` no config por jogo.

## 6. Sondas de custo (S18, build 46; S22, build 51)

`debug.xendroid.gpu_probe` pula trabalho para medir o que ele custa (a imagem
quebra). Um braço de 30 s por sonda, cada um contra os braços sem sonda
vizinhos (30,2 / 30,8 / 31,4 / 27,6 ms, com deriva térmica):

| sonda | o que pula | GPU/frame | contra os vizinhos |
|---|---|---:|---:|
| 1 | cópias dos resolves de até 128x128 | 29,5 ms | −0,8 ms |
| 2 | gravação na memória do guest dos resolves que vão para uma textura | 30,6 ms | +0,05 ms (e um travamento de 139 ms) |
| 4 | todas as transferências de posse da EDRAM | 28,3 ms | **−2,8 ms** |
| 8 | transferências 4x ↔ 1x de mesma base | 29,4 ms | −0,1 ms |

A sonda 16 (build 51, S22, com timestamps) faz os resolves de média de
amostras MSAA lerem só a primeira amostra — o teto de um resolve pelo
hardware. Cada resolve de faixa 1280x256 4x cai de ~0,69 para ~0,33 ms e o de
1280x208 de ~0,56 para ~0,28 ms; no par de braços limpo, GPU −0,8 ms (o
terceiro braço teve um travamento de 228 ms e ficou de fora).

- **Item 5 adiado**: um resolve pelo hardware (`vkCmdResolveImage` para uma
  imagem 1x e o resolve direto 1x a partir dela) ainda pagaria o próprio blit
  e a leitura 1x; o ganho líquido fica abaixo de ~0,5 ms para um caminho novo
  de resolve.
- **Item 6 descartado**: pular as cópias dos ~70 resolves minúsculos rende no
  máximo 0,8 ms, e o caminho dentro do passe já não tinha ganhado nos resolves
  grandes (AB5).
- **Item 7 descartado**: gravar a memória do guest não custa nada perceptível.
- **Item 8**: as transferências valem até ~2,8 ms, mas as 4x ↔ 1x de mesma base
  (3600 dos 12,4 mil tiles por frame) quase nada — seção 11.

## 7. JIT (S17, build 46; S23, build 51)

Um lançamento por opção com 15 s de simpleperf no teto. No S17 o celular
estava com bateria baixa (11% no início) e **desligou a 5% no 4º
lançamento**; o S23 repetiu a base duas vezes. Amostras do código JIT (todas
as threads do guest) e de duas das funções mais quentes:

| lançamento | código JIT | `82FC6CC0` | `8300D580` |
|---|---:|---:|---:|
| S17 base | 27 430 | 1155 | 1144 |
| S17 `a64_vmx_nan_fixup = false` | 30 000 | 1169 | 1152 |
| S17 `a64_enable_host_guest_stack_synchronization = false` | 23 928 | 857 | 922 |
| S23 base | 28 418 | 1221 | 1177 |
| S23 `inline_leaf_max_instructions = 32` | 27 087 | 995 | 1141 |
| S23 base (de novo) | 23 636 | 921 | 951 |

**Duas bases iguais diferem 17%** no total e 25% numa função quente (o clock
da CPU muda de um lançamento para outro), o mesmo tamanho dos −20–30% que a
sincronização de pilha parecia ganhar no S17. Nenhuma das três opções mostra
efeito além desse ruído, e com o jogo no teto o ganho seria só de energia: os
padrões ficam. A sincronização de pilha protege jogos que usam
setjmp/longjmp; o build 49 registra cada reentrada por longjmp
(`A64Backend: longjmp re-entry ...`) e nenhum lançamento com ela ligada
registrou alguma. O lançamento sem ela no S23 teve um intervalo de 32,8 s sem
frames durante a medição (o app ficou suspenso) e foi descartado; no S17 ele
rodou normalmente (28,55 fps).

## 8. CPU menor

- **XMA**: o worker percorria os 320 contextos a cada passada; agora mantém
  uma máscara dos contextos habilitados (`xma_worker_enabled_contexts_only`,
  ligado) e só visita esses, limpando o bit só depois de reconferir. A thread
  "XMA Decoder" ficou em ~0,15 núcleo no teto (S17), contra ~0,18 na S14.
- **Thread de comandos**: as escritas de registradores do ring já estavam no
  caminho rápido; sem ganho barato.

## 9. Fidelidade

- **Query de oclusão em volta das transferências no passe**: um segmento de
  query nativa aberto durante as transferências feitas dentro do passe
  contaria os pixels delas. Agora o segmento é fechado antes e reaberto
  depois (`VkZpd` conta os casos). Na cena de referência: 0 casos.
- Validação visual dos padrões finais: seção 12.

## 10. Fluidez

O `GpuFrame` conta por relatório os intervalos de frame acima de 37, 50 e
70 ms (`intervals >37ms=… >50ms=… >70ms=…`), e o `forza_segstats.py` e o
`forza_passres.py` mostram o pior frame médio por segundo e a fração de
frames acima de 37 e 50 ms. Exemplos: MSAA 2x lógico 3,3% dos frames acima
de 37 ms contra 4,6% em 4x (S19); clear no passe 0,4% contra 1,2% (S18).

## 11. Transferências: rastreamento da EDRAM e clears por quad (S22, build 51; S24, build 52)

`edram_trace_frames` (`debug.xendroid.edram_trace N`) registra N frames de uso
da EDRAM: cada binding com seus draws e o estado do primeiro draw (teste e
escrita de depth, stencil, máscara de cor, linhas), cada transferência de
posse (e se a origem foi limpa por um resolve depois do seu último draw), cada
resolve e seu clear. `tools/edram_trace.py` classifica as transferências do
último frame pelo primeiro draw do destino. Um frame parado (S22):

| destino da transferência | tiles | transferências |
|---|---:|---:|
| depth cujo 1º draw escreve depth com teste *always* e stencil | 2401 (19%) | 14 |
| origem limpa depois do último draw (o destino também começa com um quad) | 2072 (16%) | 12 |
| depth cujo 1º draw escreve depth *always*, sem stencil (atlas de sombra) | 832 (7%) | 2 |
| para clears de resolve | 273 (2%) | 4 |
| outras (dados que o draw seguinte lê) | 7184 (56%) | 18 |

O Forza reaproveita a EDRAM entre formatos e amostragens — o 0t passa de cor
1x (2_10_10_10) para depth 4x de 640x360, depth 1x de 1280x720, cor 4x da
cena — e **começa vários alvos com um quad de clear** (depth *always* +
escrita, stencil, cor cheia): a cor e o depth da faixa da cena (1565 tiles
antes dos ~425 draws da faixa), o 720t / 0t 16t 4x (1440 tiles). Cada um
recebia antes uma transferência do conteúdo antigo que o quad apagava em
seguida.

**`skip_overwritten_transfers`** (novo, categoria GPU, desligado por padrão,
`debug.xendroid.skip_overwritten_transfers`): uma transferência para um alvo
do draw é descartada quando o draw sobrescreve tudo o que ela copiaria — um
único retângulo (lista de retângulos de 3 vértices; o estimador de extensão
roda o vertex shader na CPU, recorta pelo viewport e recusa se o clipping de
depth puder cortá-lo) cobrindo os pixels inteiros da transferência dentro do
scissor, escrevendo todo pixel e amostra sem condição: depth com teste
*always* e stencil substituído com máscara cheia, ou cor com todos os
componentes e sem blending; sem alpha test, alpha to coverage, kill no pixel
shader, culling ou modo de polígono duplo.

A/B em tempo real (S24, 3 × 3 braços de 30 s, só os quirks):

| | desligado | ligado |
|---|---:|---:|
| GPU/frame (cada braço) | 35,50 / 35,28 / 35,80 ms | **34,38 / 34,19 / 34,57 ms** |
| fps | 27,78 | 28,75 |
| frames > 37 ms | 23,6% | 6,1% |
| transferências / tiles por frame | 49 / 12,5 mil | 38 / 8,8 mil (11 / 3,7 mil puladas) |

**GPU −1,1 ms (−3,1%)** em todos os pares de braços vizinhos. A imagem é a
mesma: recortes em tamanho real do carro e do céu (sol, montanhas, árvores)
idênticos, diferença de pixels entre um braço ligado e um desligado vizinho
(2,3 de média) menor que entre dois desligados (6,6), e uma direção de 25 s
com a opção ligada sem defeitos (`b52-skipxfer/`). Virou quirk do Forza.

Sobram 1140 tiles (9%) para alvos de depth que começam com um quad *always*
mas não passam na checagem — o stencil não é substituído por inteiro, ou o
draw não é uma lista de retângulos — e o atlas de sombra (sem stencil no
quad: o stencil antigo continua lá).

## 12. Validação dos padrões finais (S22, S23, S25)

- **S22 (build 51)**: só os quirks — o log mostra todos eles, entre os novos
  `vulkan_resolve_clear_in_guest_pass` e `ir3_debug` (`Set
  IR3_SHADER_DEBUG=noearlypreamble`), e o cache de pipelines carregado de
  `4D5309C9.noearlypreamble.vk.bin`.
- **S23 (build 51)**: lançamentos parados e com 45 s de direção (capturas a
  cada 5 s): sem defeitos; nenhuma reentrada por longjmp registrada.
- **S25 (build 53, o código do PR)**: os 13 quirks do Forza aplicados,
  `SpinPark: guest function 829F04A8 signature B864F65007F969C0 (matches)`,
  11 transferências por frame puladas, nenhum erro do Vulkan, e uma direção de
  25 s até 61 mph sem defeitos (`b53-final/`). Com o celular já morno (34,6
  °C, bateria 25%) e limitado pela GPU: 27,2 fps, GPU 36,3 ms.

Cidade, noite, chuva, túneis e replays continuam sem verificação: a
automação só dirige a partir do ponto de partida.
