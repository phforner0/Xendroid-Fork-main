# AB12 — plano v7: LOD implícito, VS sem 0 × x, sinais pelas texturas usadas, resolves especializados por formato, as faixas do tiling num passe só (builds 66 a 77, 2026-10-01/02)

POCO F7 (Adreno 825, Turnip Gen8 V37 "patched", sysmem), pacote de teste
`xendroid.compose.fork.opt`, cena de referência de sempre (carro parado na
defensa). Tudo pela rede: `adb` sobre Tailscale (depuração sem fio). Métrica:
**GPU ms/frame no teto de 30 fps do jogo**. A/B com reinício (`restart_ab.ps1`,
cada variante com o seu VkPipelineCache) ou em tempo real
(`forza_auto_ab.ps1`, braços de 20 s); cada braço é julgado contra os
vizinhos. As sessões pegaram o celular frio (31–34 °C): os níveis só se
comparam dentro da mesma sessão.

## Resumo

**Validação final (S52, b77, o mesmo build, ABBA):** a configuração do fim do
plano v6 (as opções da v7 desligadas no config por jogo) **22,4 / 22,1 ms** de
GPU por frame, a da v7 **17,6 / 17,7 ms** — **−4,6 ms (−21%)** no teto de 30 fps
(29,97–30,00 fps nos dois), ~3000 → ~1980 draws por frame, a mesma imagem
parado e dirigindo, nenhum erro.

| # (v7) | Caminho | Resultado | Situação |
|---|---|---|---|
| 1 | LOD implícito nas buscas 2D (`spirv_texture_implicit_lod`) | **GPU −1,6 ms (−7%)** (22,4/22,2 → 20,7/20,7, S43); mesma imagem | **quirk do Forza** |
| 2 | VS sem a emulação de "0 × x = 0" (`spirv_vs_relaxed_math = 1`) | **GPU −0,7 ms** (S43: −0,75; S45: −0,7); sem *z-fighting* nas faixas pintadas dirigindo | **quirk do Forza** |
| 3 | Especialização dos sinais pelas texturas usadas (`spirv_texture_sign_specialization_used`) | **GPU −0,4 ms** (20,9/20,5 → 20,3/20,3, S44); mesma imagem | **padrão** |
| 4 | Formatos 12/10 no caminho direto do resolve (`vulkan_direct_host_resolve_storage_format`) | GPU −0,1 ms (S44, em tempo real) | padrão (exato) |
| 5 | Fetch constants decodificadas na CPU (`spirv_texture_fetch_constants_decoded`) | teto do experimento −0,75 ms (S44); a decodificação real **+0,2 ms** (S46): preâmbulo −24%, sem ganho | desligado; ver a seção 5 |
| 5b | *Exp adjust* zero conhecido por pipeline (`spirv_texture_exp_adjust_specialization`) | **GPU −0,2 ms** (19,5/19,2 → 19,1/19,2, S48); exato | **padrão** |
| 6 | Resolves 7e3 → 2_10_10_10 com os formatos conhecidos pelo shader (`vulkan_direct_host_resolve_7e3_variant`) | **GPU −0,8 ms** (20,5 → 19,7, S46); exato | **padrão** |
| 6b | Resolves com o formato da EDRAM conhecido (`vulkan_direct_host_resolve_format_variants`: cópias 8888/2_10_10_10, profundidade, 7e3 1x) | **GPU −0,4 ms** (19,1 → 18,7, S48); exato | **padrão** |
| 7 | Uploads de memória da GPU não servidos | 7a (profundidade lida como 8888): sem ganho, a textura ainda perde uma escrita; 7b (cube map com mips) e 7c (sombra escrita pela CPU) não servíveis | investigado |
| 8 | Resolves minúsculos no passe com a conversão 7e3 (`vulkan_in_pass_resolve_7e3`) | 73% dos resolves no passe; GPU −0,15 ms (19,8/19,9 → 19,7/19,7, S47), +68 passes/frame | desligado |
| 9 | As 3 faixas do tiling num passe só (`merge_tiling_bands`) | **GPU −1,1 ms** (S49: 19,1/19,0 → 17,9/17,9; S51: 18,7/19,0 → 17,7/17,8), 2916 → ~1960 draws/frame, mesma imagem parado e dirigindo | **quirk do Forza** |
| 10 | Validação final: v6 × v7 no mesmo build | **−4,6 ms (−21%)**, mesma imagem | feito |
| 11 | Opções de qualidade no app | documentadas para a interface Kotlin (seção 11) | documentado |
| 12 | CPU por draw (perfil com símbolos) | thread de comandos ~43% de um núcleo, ~5 µs/draw; sem ponto quente dominante | documentado |

## 1. LOD implícito sobre a especialização (S43, b65)

No AB10, `spirv_texture_implicit_lod` (busca 2D com o LOD do host em vez de 4
derivadas + amostra com gradiente explícito) foi neutro: as amostras ainda
estavam presas em desvios dos sinais. Com os sinais especializados (AB11), o
pipe de textura limita, e cada busca 2D deixa de ser 5 instruções *cat5*:

| S43 (b65, ABCCBA, braços de 30 s) | GPU ms/frame |
|---|---|
| base | 22,4 / 22,2 |
| LOD implícito | 20,7 / 20,7 |
| VS sem 0 × x | 21,6 / 21,5 |

Asfalto ao longe e grama em 1:1: mesma nitidez. Quirk do Forza. Sai da lista
de becos sem saída.

## 2. VS sem a emulação de "0 × x = 0" (S43, S45)

`spirv_vs_relaxed_math` (nova opção, máscara de bits como a dos pixel
shaders): o bit 1 tira só a emulação do Shader Model 3 nas multiplicações dos
vertex shaders (~35–40% das instruções dos VS do passe principal). Sem FMA e
sem mudar arredondamento, as posições são as mesmas para operandos finitos;
só o sinal de um produto zero pode mudar, então os passes continuam batendo.
S45 (b68, ABBA, 25 s dirigindo com uma foto a cada 5 s): base 20,7/20,4,
vsz 20,2/19,5. As faixas pintadas da estrada (decalques sobre o asfalto, o
caso clássico de *z-fighting*) ficam limpas dirigindo. Quirk do Forza.

## 3. Sinais pelas texturas usadas (S44, b67)

82 dos 91 shaders grandes leem a fetch constant 13, fora das 0–7 que a
especialização cobria. Agora os 8 *slots* são as 8 primeiras fetch constants
distintas que o shader busca (`GetTextureSignClassSlots`). Base 20,9/20,5,
used 20,3/20,3 (todos com LOD implícito). Exato: vira o padrão.

## 4. Formatos 12/10 no caminho direto (S44)

A chave do render target guarda o formato de armazenamento (3/2), e a
comparação com o formato cru do resolve (12/10) falhava. Normalizado com
`GetStorageColorFormat`: −0,1 ms em tempo real (22,2 → 22,1). Exato, padrão.

## 5. Fetch constants decodificadas na CPU (S44, S46)

Teto (`spirv_ps_math_experiment = 16`, as palavras das fetch constants lidas
como 0, imagem errada): **−0,75 ms** (19,9/20,0 contra 20,9/20,5). A versão
real (`spirv_texture_fetch_constants_decoded`) anexa ao buffer das fetch
constants 2 uvec4 por fetch constant decodificados na CPU (tamanhos, 3D,
2^exp, bias de LOD; só as alteradas, por máscara) e o shader só os carrega:
**+0,2 ms** (S46: base 20,4, dec 20,6).

S48 (b74): as estatísticas do Turnip de cada pipeline (`vulkan_pipeline_statistics`,
cada braço compilando tudo do zero), ponderadas pelos draws de dois frames de
`pm4_bin_trace`:

| Pixel shader, média por draw | base | decodificado | teto (palavras em 0) |
|---|---|---|---|
| instruções do preâmbulo | 150,1 | 114,2 (−24%) | 82,8 (−45%) |
| instruções por pixel | 239,2 | 239,8 | 230,4 (−9) |
| GPU ms/frame (um braço cada) | 19,1 | 19,0 | 18,4 |

O preâmbulo encolher um quarto não muda a GPU: o ganho do teto vinha das ~9
instruções por pixel que o compilador dobra com as palavras em 0, sobretudo a
multiplicação de cada componente buscado por 2^0. Daí
`spirv_texture_exp_adjust_specialization`: um bit por pipeline (descrição do
pipeline, SpecId 1010), "o *exp adjust* de todas as texturas do pixel shader é
zero" — então a multiplicação some. S48, ABBA: base 19,5/19,2, especializado
19,1/19,2 (−0,2 ms). Exato: padrão.

## 6. Resolves especializados por formato (S46, S48)

O caminho direto "full color" (4x 7e3 → 2_10_10_10, as faixas da cena e as
faces do cube map) empacota cada amostra no formato da EDRAM e desempacota,
com `switch` do formato entre as buscas. Variantes com os dois formatos
conhecidos na compilação (o mesmo código, os `switch` dobrados):
**19,7 contra 20,5 ms** em tempo real (S46). Exato: padrão.

Segunda iteração, `vulkan_direct_host_resolve_format_variants` (26 variantes
novas, criadas sob demanda): as cópias de 4 pixels de 8_8_8_8 e 2_10_10_10 (o
resolve 1280x720 1x em 2_10_10_10 custava ~0,37 ms cada, 2 por frame), a
profundidade D24S8/D24FS8 (~1,2 ms/frame no total) e o 7e3 → 2_10_10_10 em 1x
(a cadeia minúscula). S48 em tempo real: **18,7 contra 19,1 ms**. Exato: padrão.

## 7. Uploads de memória da GPU não servidos (S44, S47)

`resolve_dest_diag` mostrou três casos:

- **7a** — a textura 8888 1280x720 lida da memória que os resolves de
  profundidade (k_24_8_FLOAT) escrevem (~0,16 ms de upload). Com
  `vulkan_direct_host_resolve_depth_to_8888` os resolves de profundidade
  também gravam a palavra empacotada na textura 8888, mas ela continua
  perdendo uma escrita (outro escritor da mesma memória): sem ganho (19,8 ×
  19,8). Desligado.
- **7b** — o cube map 2_10_10_10 256x256 com 8 mips (~0,19 ms): os 6
  resolves de face gravam o nível 0, e os mips vêm da cadeia de resolves
  minúsculos (item 8). Servir exigiria views por nível e face e o mip tail
  empacotado. Não implementado.
- **7c** — o mapa de sombra k_24_8 1024x1024 (~0,28 ms): parte escrita pela
  CPU, não servível.

## 8. Resolves minúsculos no passe (S47)

`vulkan_in_pass_resolve_7e3`: o resolve no passe (fragment shader lendo o
anexo por *local read*) passa a converter 7e3 → 2_10_10_10 como o caminho
direto. 331 mil de 450 mil tentativas no passe (73%), reflexo do carro
idêntico, GPU −0,15 ms (19,8/19,9 → 19,7/19,7), mas +68 passes por frame e
todos os anexos de cor no layout de *local read*. O custo fixo dos resolves
minúsculos não é só a quebra do passe. Desligado.

## 9. As 3 faixas do tiling num passe só (S48–S51)

O estado de cada faixa (S48, primeiro draw depois de cada `SET_BIN_SELECT`,
um log novo do `pm4_bin_trace`):

| Faixa | Seleção | Deslocamento da janela | Scissor da janela (tela) | Draws |
|---|---|---|---|---|
| 0 | `80000003` | 0 | linhas 0–256 | ~455 |
| 1 | `C` | −256 | linhas 256–512 | ~845 |
| 2 | `30` | −512 | linhas 512–720 | ~510 |

O viewport é sempre o da tela inteira; os alvos são os mesmos nas três faixas
(cor 4x 7e3 no tile 0, profundidade D24FS8 no tile 1024, ambos com pitch de 32
tiles), cada faixa termina com o resolve da profundidade e o da cor com clear
das duas, e a faixa 0 começa com um quad de clear de tela inteira. O jogo
reexecuta os mesmos *command buffers* em cada faixa (só as máscaras de algumas
faixas mudam entre elas).

`merge_tiling_bands` (GPU, desligado por padrão):

- **Processador de comandos** (comum aos backends): cada `SET_BIN_SELECT`
  diferente de todos os bits entre seleções "todos os bits" é uma faixa.
  Cada draw (os resolves nunca) é identificado pelo endereço logo depois do seu
  pacote e só é executado na primeira faixa em que a predicação o deixa
  passar; os das faixas seguintes que repetem um já desenhado são pulados.
- **Alvos da altura da tela**: os render targets multisampled ganham as linhas
  até 720 (`GetRenderTargetHeight`); as linhas além da EDRAM nunca têm dono,
  só são desenhadas enquanto as faixas estão juntas.
- **Draws**: o scissor que cobre a faixa inteira passa a cobrir a tela; um
  draw executado pela primeira vez na faixa b vai para as linhas dela (viewport
  e scissor do host +256·b — desfaz o deslocamento da janela, mesmo com o
  viewport já cortado no quadrante positivo pelo Xenia).
- **Começo**: as linhas das faixas 1 e 2 começam como as da faixa 0 — copiadas
  delas (`vkCmdCopyImage`, comando novo no buffer adiado), salvo quando o
  primeiro draw do alvo na faixa 0 substitui a tela inteira (o quad de clear,
  reconhecido como pelo `skip_overwritten_transfers` e com o retângulo de
  1280x720 estimado na CPU) — então nada é copiado.
- **Resolves**: o da faixa b lê as linhas dela — a base da fonte nos shaders do
  resolve direto (e do *dump* da EDRAM) recua 32·b linhas de tiles, sem mudar
  shader.

| Sessão | Base | Faixas juntas | Draws/frame |
|---|---|---|---|
| S49 (b75, cópia sempre, ABBA) | 19,1 / 19,0 ms | **17,9 / 17,9 ms** | 2916 → 1980 |
| S50 (b76, sem cópia com o clear; parada à mão no braço 4) | 18,6 ms | 17,5 / 18,0 ms | — |
| S51 (b76, ABBA) | 18,7 / 19,0 ms | **17,7 / 17,8 ms** | 2912 → 1961 |

Parado e dirigindo (25 s, uma foto a cada 5 s): a imagem inteira, sem emenda
nas bordas das faixas (linhas 256 e 512), sem objetos fora do lugar, sombras e
reflexos iguais. Riscos que ficam: os pixel shaders veem a posição do pixel
da tela (não a da faixa) nos ~300 draws que só existem nas faixas 1 e 2;
consultas de oclusão contam os draws pulados como zero.

## 10. Validação (S52, b77)

O mesmo build, ABBA, 30 s de direção em cada lançamento; o braço "v6"
desliga no config por jogo (que vence os quirks) tudo o que a v7 trouxe:

| Braço | GPU ms/frame | fps | Draws/frame |
|---|---|---|---|
| v6 | 22,4 | 29,97 | 3009 |
| v7 | 17,6 | 29,97 | 1958 |
| v7 | 17,7 | 29,99 | 2000 |
| v6 | 22,1 | 30,00 | 2968 |

Parado e dirigindo: a mesma imagem; nenhum erro de dispositivo nem draw
recusado. O celular a 36–37 °C (sem teste térmico longo).

Para outros jogos: os caminhos exatos viraram padrão para todos (sinais pelas
texturas usadas — só com a especialização, que é quirk —, o *exp adjust*
zero, as variantes de resolve por formato, o formato de armazenamento no
resolve direto). LOD implícito, VS sem 0 × x e as faixas juntas ficam como
quirks do Forza: dependem do jogo (precisão de LOD nas bordas de triângulo,
operandos não finitos nos VS, estrutura do tiling). Não verificados: cidade,
noite, chuva e replays (a automação só dirige a partir do ponto de
referência).

## 11. Opções de qualidade no app (interface Kotlin do usuário)

Duas opções medidas trocam qualidade por GPU e cabem num seletor por jogo:

| Opção (cvar) | Quando vale | Ganho medido | Custo visual |
|---|---|---|---|
| MSAA 4x lógico em 2x (`msaa_4x_as_2x`, GPU, alternável em tempo real) | jogos com 4x MSAA | −4,5 ms (AB9) | antisserrilhado 2x |
| Taxa de shading (`vulkan_fragment_shading_rate` na inicialização + `vulkan_shading_rate`, GPU, em tempo real) | cena multisampled | 2x1: −0,7 ms; 2x2: −0,8 ms (AB11) | 2x1 quase imperceptível; 2x2 com blocos visíveis |

Sugestão: "Qualidade da cena" (Alta = padrão; Equilibrada = shading 2x1;
Desempenho = MSAA 2x + shading 2x1), gravada no config por jogo.

## 12. CPU por draw (S46, b70, simpleperf com símbolos)

41,5 mil amostras em 15 s parado: ~2,8 núcleos. "GPU Commands" 15,6%
(~43% de um núcleo, ~14 ms de CPU por frame, ~5 µs por draw). Por função
(próprio): `memmove` 11% (o desenrolar por *frame pointer* perde o chamador;
23% dele vem do `DeferredCommandBuffer::Execute`), `WriteRegisterRangeFromRing`
5,6%, `clock_gettime` 4,9% (só a instrumentação dos testes,
`log_gpu_frame_time_breakdown`), Turnip ~6,7%, busca de shader/pipeline em
tabelas hash ~7,7%, `UpdateBindings` 3%, vigilância de memória
(`mprotect`, `QueryRegionInfo`, `FireWatches`) ~4,7%. Nenhum ponto domina; o
jogo (threads do guest) ocupa ~2 núcleos. Energia, não fps no teto: nada
implementado nesta rodada.

## Código desta rodada

- Tradutor SPIR-V (GPU, na tradução dos shaders):
  - `spirv_texture_sign_specialization_used` (ligado): os *slots* de sinal são
    as 8 primeiras fetch constants distintas do shader
    (`GetTextureSignClassSlots`);
  - `spirv_texture_exp_adjust_specialization` (ligado): SpecId 1010, bit
    `texture_exp_adjust_zero` da descrição do pipeline;
  - `spirv_vs_relaxed_math` (0; quirk do Forza = 1);
  - `spirv_texture_fetch_constants_decoded` (desligado):
    `DecodeTextureFetchConstant`, 2 uvec4 por fetch constant depois das 48 do
    buffer;
  - bit 16 de `spirv_ps_math_experiment` (diagnóstico).
- Resolve direto (Vulkan, por resolve):
  - `vulkan_direct_host_resolve_7e3_variant` e
    `vulkan_direct_host_resolve_format_variants` (ligados): 32 variantes de
    shader novas (`gen_android_spirv.py`), criadas sob demanda;
  - `vulkan_direct_host_resolve_storage_format` (ligado);
  - `vulkan_direct_host_resolve_depth_to_8888` e `vulkan_in_pass_resolve_7e3`
    (desligados).
- `merge_tiling_bands` (GPU; quirk do Forza): `OnBinSelectWritten` e
  `PrepareTilingBandDraw` no processador de comandos, alvos MSAA de 720 linhas
  (`GetRenderTargetHeight`), `NoteTilingBandDraw`,
  `ReplicateTilingBandRows` e `DrawReplacesArea` no cache de render targets,
  `vkCmdCopyImage` no buffer adiado.
- Diagnóstico: `pm4_bin_trace` registra o estado do primeiro draw de cada
  seleção de bins (deslocamento e scissor da janela, superfícies, viewport).
- Propriedades novas: `debug.xendroid.resolve_storage_format`,
  `resolve_7e3_variant`, `resolve_format_variants`, `in_pass_resolve_7e3`,
  `resolve_depth_to_8888`.
- `tools/fh_auto.sh`: piso de 1500 draws por frame para "no jogo" e "parado".
