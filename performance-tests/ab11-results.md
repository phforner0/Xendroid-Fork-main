# AB11 — plano v6 até o fim: tetos, VRS, recortes, clears no lugar de transferências, faixas do tiling, sinais das texturas (builds 59 a 65, 2026-10-01)

POCO F7 (Adreno 825, Turnip Gen8 V37 "patched", sysmem), pacote de teste
`xendroid.compose.fork.opt`, cena de referência de sempre (carro parado na
defensa). Tudo pela rede: `adb` sobre Tailscale, sem cabo. Métrica: **GPU
ms/frame no teto de 30 fps do jogo**. Cada braço é julgado contra a média dos
vizinhos (deriva térmica). As sessões desta rodada pegaram o celular frio:
~25 ms/frame em vez dos ~31–33 ms das sessões quentes, e os níveis só se
comparam dentro da mesma sessão.

## Resumo

| # (v6) | Caminho | Resultado | Situação |
|---|---|---|---|
| — | Tetos (S33, b59): sem nenhuma transferência / resolves MSAA lendo 1 amostra | transferências **−0,36 ms**; média das amostras **~−0,7 ms** | limitam os itens 4–6 e o resolve por hardware |
| — | Custo por parte dos draws (S36, b60) | pixels **11,7 ms**, vértices 3,2 ms, custo fixo por draw 3,7 ms (~1,2 µs/draw), resto 7,7 ms | orienta os itens 8–10 |
| 4 | Recorte (*cutout*) da parte da transferência que o quad cobre (`skip_overwritten_transfers_cutout`) | 9 transferências/frame recortadas; **GPU −0,1 a −0,2 ms**; mesma imagem | **quirk do Forza** |
| 5 | Quad 4x desenhado na superfície 2x dona dos tiles (`vulkan_samples_as_pixels_2x`) | −512 tiles/frame (2826 → 2314); GPU igual (−0,04 contra −0,02 ms) | opção desligada |
| 6 | Transferência de fonte limpa por resolve vira clear no passe (`transfer_cleared_sources_as_clears`) | 4,5 transferências e 1092 tiles/frame viram clears (2826 → 1734); GPU igual | opção desligada |
| 7 | Resolve 1280x720 em 2_10_10_10 mais lento que o 8888 | a gravação na textura promovida custa ~0,08 ms por resolve e economiza 1,5 ms de uploads; sobra ~0,09 ms por resolve do formato | fechado |
| 8 | Sinais/gama das texturas como *specialization constants* do pipeline (`spirv_texture_sign_specialization`) | teto −1,9 ms (S35); a variante exata dá **GPU −1,9 ms (−7,9%)**, mesma imagem (S40) | **quirk do Forza** |
| 9 | Taxa de shading 2x1/2x2 (VRS) | 2x1 **−0,7 ms**, 2x2 −0,8 ms (blocos visíveis em 2x2) | opção desligada |
| 10 | Draws repetidos nas 3 faixas do tiling predicado | teto **−5,3 ms (−21%)** com áreas falsas (só a faixa 0; inclui pixels); ganho real estimado 2–4 ms | pesquisa concluída; não implementado |
| 11 | JIT: promoção de registradores, CR mortos | CPU não limita (threads do jogo ~2 núcleos, GPU Commands ~42%) | não implementado |
| — | Código final (b65, S41, só os quirks) | 30,00 fps, GPU 22,8 ms/frame (b63 no S39: 26,3, outra sessão); 2314 tiles transferidos por frame; 25 s dirigindo sem defeitos nas fotos | PR |

## Tetos (S33, b59)

`vulkan_debug_gpu_probe`, braços de 20 s, ordem 4 0 16 0 4 0 16 0:

| Sonda | Braços contra os vizinhos | Leitura |
|---|---|---|
| 4 — pular todas as transferências de posse | −0,40 e −0,32 ms | **as ~2800 tiles/frame restantes custam ~0,36 ms**: os itens 4–6 juntos não passam disso |
| 16 — resolve MSAA lendo só a 1ª amostra | −0,70 ms (o 2º braço caiu na subida térmica dos braços 6–8) | teto do resolve de média pelo hardware ~0,7 ms |

## Custo por parte dos draws (S36, b60, `-Passes`)

`vulkan_debug_draw_ceiling` (quebra a imagem), braços de 15 s, ordem
1 0 2 0 3 0 1 0 2 0 3 0:

| Braço | GPU ms/frame | Diferença |
|---|---|---|
| 0 — normal | 26,3 | — |
| 1 — sem pixels (scissor 1x1) | 14,6 | **pixels 11,7 ms** |
| 2 — também só a 1ª primitiva | 11,4 | vértices 3,2 ms |
| 3 — sem comandos de draw | 7,7 | custo fixo por draw 3,7 ms (~1,2 µs × ~3000 draws) |

No passe principal (bucket 1280x512, ~1850 draws/frame): 12,5 → 5,1 → 2,5 →
0,25 ms. Com a taxa de shading 2x2 tirando só 0,8 ms (seção 9), o custo dos
pixels está por amostra (ROP, MSAA, banda), não nas invocações do pixel
shader — coerente com os −4,5 ms do `msaa_4x_as_2x`.

## 4. Recorte da transferência coberta em parte (S37, b61)

O Xenia toma a EDRAM por fileiras inteiras de tiles, e um quad de clear que
cobre só parte delas deixava a transferência inteira. Com
`skip_overwritten_transfers_cutout`, a transferência que o retângulo do draw
(estimado na CPU, o mesmo do `skip_overwritten_transfers`) cobre em parte
copia só o que fica fora dele, como já acontecia em volta do clear de um
resolve.

Casos no rastreamento: o 1x 1280-wide → os 2x 256x256 (sobra uma faixa de
16x256), os 2x → as vistas 1x deles (sobra 80x512 de 320x512), o 7e3 →
8888 1280x368 (o draw cobre 640x360) e o 2_10_10_10 → os 64x64.

| S37 (braços de 20 s, 1 0 1 0 1 0) | GPU ms/frame | contra os vizinhos |
|---|---|---|
| 1 ligado | 24,92 | −0,03 |
| 2 desligado | 24,94 | +0,13 |
| 3 ligado | 24,71 | −0,29 |
| 4 desligado | 25,07 | +0,15 |
| 5 ligado | 25,13 | +0,02 |
| 6 desligado | 25,15 | +0,02 |
| médias | — | **ligado −0,10 / desligado +0,10** |

`VkXfer`: `cut=9.0` por frame. As fotos dos braços diferem só pelo que se
move na cena (nuvens, bordas, um carro passando); com os dois itens (4 e 6)
ligados a imagem está igual (reflexo do carro, sombras, céu, HUD). Exato e
pequeno: virou quirk do Forza.

## 5. Quad 4x na superfície 2x (S38, b62)

O reflexo 256x256 2x é limpo em duas partes: um quad 2x numa faixa de
16x256 e um quad 4x com pixel shader simples sobre os outros 240 pixels —
que ia para uma vista 1x (amostras como pixels), com 2x → 1x e 1x → 2x de
128 tiles para cor e depth (512 tiles/frame). Com
`vulkan_samples_as_pixels_2x`, o draw 4x vai para a superfície 2x dona dos
tiles com o mesmo pitch: x dobrado, y e amostras verticais iguais (escala de
desenho 2x1, contagem de oclusão com escala 1).

Funciona: no rastreamento, os 51 draws seguidos na superfície 2x e nenhuma
transferência para a vista 1x; `VkXfer` 21,5 → 17,5 transferências e 2826 →
2314 tiles/frame. Mas o tempo de GPU não mudou (médias contra os vizinhos
−0,04 ligado, −0,02 desligado). Fica como opção desligada.

## 6. Fonte limpa por resolve vira clear (S37, b61)

O cache guarda, por render target, o valor e o retângulo do último clear de
resolve, até algo desenhar nele. Uma transferência que só copia o que esse
clear deixou (as fileiras de tiles inteiras dentro do retângulo, mesmo
tamanho de pixel, sem fonte de depth de host) vira um `vkCmdClearAttachments`
no passe do draw com o mesmo valor na codificação da EDRAM, convertido para
o formato do destino. Se o draw não acontecer, os clears vão para o último
passe (ou caem com aviso se ele não puder ser reaberto).

No rastreamento: 4 transferências por frame, todas de valor 0 — 2_10_10_10 →
8888 (256 e 4 tiles), D24FS8 → D24S8 (720 e 112 tiles). `VkXfer`: 21,5 →
17,0 transferências e 2826 → 1734 tiles/frame (`as clears=4.5 tiles=1092`).

| S37 (braços de 20 s, 1 0 1 0 1 0) | contra os vizinhos |
|---|---|
| ligado | −0,23, +0,01, +0,21 (média −0,00) |
| desligado | +0,15, −0,16, −0,20 (média −0,07) |

Sem ganho mensurável: o teto de todas as transferências é ~0,36 ms, e o clear
também escreve. Fica como opção desligada.

## 7. O resolve 1280x720 em 2_10_10_10 (S36, S38)

Pelos timestamps por resolve (`-Passes`), o 2_10_10_10 (com clear) custa
~0,33 ms de dispatch e o 8888 ~0,22 ms. Os dois vão pelo caminho rápido e o
*exp bias* já tinha sido descartado. No S36 a diferença continua mesmo sem
nenhum draw no frame (braço 3: 0,35 contra 0,22 ms): não é *flush* do que
foi desenhado.

S38, `debug.xendroid.resolve_to_texture` 0 1 0 1 (`-Passes`, braços de 15 s):

| | 2_10_10_10 | 8888 | GPU ms/frame |
|---|---|---|---|
| gravando também na textura promovida | 0,328 ms | 0,216 ms | 26,1 |
| só na memória | 0,235 ms | 0,145 ms | 27,6 (uploads de texturas +1,3 ms) |

Os dois resolves de 1280x720 gravam na textura promovida (com 4 pixels por
thread). Isso custa ~0,08 ms cada e economiza 1,5 ms de uploads: continua
valendo. Sobram ~0,09 ms por resolve do próprio formato (leitura e escrita em
A2B10G10R10), ~0,2 ms/frame no máximo. Fechado sem mudança.

## 8. Sinal e gama das texturas (S35, S40)

S35 (b62): A/B com reinício, ABBA, braços de 30 s.
`spirv_ps_math_experiment = 4` toma todo componente como sem sinal, sem bias
nem gama. As cores ficam erradas: é só medida. Os braços do experimento usam
o próprio VkPipelineCache.

| | fps | GPU ms/frame | > 37 ms |
|---|---|---|---|
| base (2 lançamentos) | 30,01 | 24,8 | 0,5% |
| sem sinal/gama (2 lançamentos) | 30,01 | 22,9 | 0,1% |

**Teto: −1,9 ms (−7,7%)**, o maior ganho exato que restava.

O que esse custo contém, por busca de textura:
- desvio uniforme em volta da amostra sem sinal;
- outro em volta da amostra com sinal;
- seleções por componente;
- desvio uniforme para o gama, com a conversão PWL (~17 operações por
  componente).

O `PipeUse` do passe principal mostra que a maior parte das texturas do Forza
é gama (`tex=0/0/0/388` no shader de 388 draws). Por isso a conversão PWL em
si pesa no teto. Uma variante exata ainda a calcula onde a textura é gama;
o que ela tira são os desvios, as seleções e a amostra com sinal.

`spirv_texture_sign_specialization` (b64):
- **No shader.** O pixel shader lê, para as fetch constants 0–7, uma
  *specialization constant* com a classe dos sinais: tudo sem sinal, XYZ gama
  com W sem sinal, ou tudo gama. Outros padrões ficam com o tratamento em
  tempo de execução.
- **Por draw.** O pipeline recebe as classes das texturas do draw (16 bits
  novos na descrição, zero nos pipelines guardados antes). O Turnip dobra
  tudo na compilação: sobra uma amostra e, onde é gama, a conversão.
- **Custo.** Mais pipelines onde um shader é usado com texturas de sinais
  diferentes.
- **A/B em tempo real.** `vulkan_texture_sign_classes`
  (`debug.xendroid.texture_sign_classes`) liga e desliga as classes por draw.

S40 (b64): A/B com reinício, ABBA, braços de 30 s. Os braços
especializados usam o próprio VkPipelineCache. O adb rodou pela depuração
sem fio do Android, pelo Tailscale (porta aleatória achada por varredura de
portas).

| Lançamento | fps | GPU ms/frame | draws/frame |
|---|---|---|---|
| 1 base | 30,00 | 24,1 | 2914 |
| 2 especializado (compilando os pipelines) | 29,99 | 22,3 | 2935 |
| 3 especializado (com cache) | 29,99 | 22,2 | 3043 |
| 4 base | 29,68 | 24,1 | 3012 |
| médias | 29,84 / 29,99 | **24,1 / 22,2** | — |

- **GPU −1,9 ms (−7,9%)**, o mesmo valor do teto do S35, com os dois pares a
  0,1 ms um do outro. Frames > 37 ms: 0,8% → 0,2%.
- Os draws por frame são os mesmos, então nenhum draw ficou esperando
  pipeline.
- As fotos de cada lançamento têm a mesma cor e o mesmo tom, inclusive o
  carro ampliado (pintura, reflexos, lanternas). A diferença média com sinal
  é < 0,5/255 por canal; sem o gama, a imagem mudaria muito de brilho.

O ganho vai além do que os desvios e seleções custariam em instruções. Com
os desvios uniformes, cada amostra fica num bloco próprio e o compilador do
Turnip espera o resultado antes de seguir. Sem eles, as amostras do shader
inteiro podem ser emitidas juntas e a latência delas se sobrepõe.

Virou quirk do Forza. O primeiro lançamento depois da mudança compila de novo
os pipelines dos pixel shaders.

## 9. Taxa de shading (VRS, S34, b60)

`VK_KHR_fragment_shading_rate` no Turnip:
- fragmento máximo 4x4, até 4 amostras;
- com escrita de depth/stencil pelo shader, máscara de amostras e combinadores.

`vulkan_fragment_shading_rate` (na inicialização) deixa a taxa dinâmica em
todos os pipelines do jogo. `vulkan_shading_rate` (em tempo real,
`debug.xendroid.shading_rate`) aplica 2x1, 1x2 ou 2x2 só aos draws de render
targets multisample — a cena 3D, não o pós-processamento nem a interface.
Cobertura, depth e stencil continuam por amostra.

| S34 (braços de 20 s, 1 0 3 0 1 0 3 0) | GPU ms/frame (média) |
|---|---|
| 0 — por pixel | 25,03 |
| 1 — 2x1 | 24,31 (**−0,7 ms**, −2,9%) |
| 3 — 2x2 | 24,21 (−0,8 ms) |

Em 2x2 as texturas e a iluminação ficam em blocos visíveis. O ganho pequeno
confirma que o passe principal não é limitado pelas invocações do pixel
shader (seção S36). As duas opções ficam desligadas. O 2x1 pode virar uma
opção de desempenho no app, mas a interface Kotlin é do usuário.

## 10. As 3 faixas do tiling predicado (S38, b62)

O passe principal é desenhado em 3 faixas de 1280x256 4x. O jogo calcula a
máscara de faixas de cada draw a partir das áreas de tela (`EVENT_WRITE_EXT`)
que a faixa 0 reporta. O Xenia reporta sempre a tela inteira, então cada
objeto é processado nas 3 faixas.

`debug.xendroid.fake_extents 1` reporta toda área como só a faixa 0. O jogo
tira os draws das faixas 1 e 2, e a imagem quebra de propósito:

| S38 (braços de 20 s, 1 0 1 0) | GPU ms/frame | draws/frame |
|---|---|---|
| 1 — só a faixa 0 | 19,27 / 19,35 | 2288 / 2299 |
| 0 — normal | 24,54 / 24,73 | 2959 / 2983 |
| diferença | **−5,3 ms (−21%)** | −677 |

Esse teto inclui os pixels da geometria das faixas 1 e 2. Áreas corretas
tirariam só os draws que não tocam cada faixa. Pelos custos do S36 (~2,7
µs de vértices + custo fixo por draw), isso dá **~1–2 ms**. Desenhar as
3 faixas num passe só tiraria todas as repetições, **~3–4 ms**.

Por que não foi implementado:
- **Áreas reais.** Exigem a caixa de tela de cada grupo de draws antes do
  callback do jogo. Na CPU, isso é rodar o vertex shader de todos os
  vértices; o interpretador só faz isso para quads de tela. Na GPU, é esperar
  a faixa 0 terminar, o que serializa CPU e GPU. Reusar as áreas do frame
  anterior não tem identidade estável entre frames (185–191 eventos, endereços
  novos a cada frame) e arrisca geometria sumindo na borda das faixas.
- **Juntar as faixas.** Uma superfície 1280x720 4x precisa de 2880 tiles de
  cor e outros tantos de depth, mais que os 2048 da EDRAM. Seria preciso:
  - uma "EDRAM virtual" fora do modelo de posse, só para essas faixas;
  - os resolves e clears de cada faixa lendo e limpando as linhas certas da
    imagem grande;
  - pular os draws das faixas 1 e 2.

  Grande, arriscado e específico do jogo. Fica como o primeiro item da
  próxima rodada.

## 11. JIT (energia)

Por thread no S37 (`top -H`): Guest CPU 5 ~50%, Guest CPU 0 ~40%, GPU
Commands ~42%, Guest CPU 1 ~30%, demais < 30% — ~2,2 núcleos no total, com o
jogo esperando a GPU (`guest_wait` 57%, `parked` 56%).

O que já tinha sido medido:
- as opções baratas do JIT (`a64_vmx_nan_fixup`, sincronização de pilha
  host/guest, `inline_leaf_max_instructions`) não mudaram nada além do ruído
  (AB9);
- o JIT já elimina as gravações de contexto mortas, mas só dentro do bloco:
  o passe zera a análise em cada desvio e em cada instrução volátil.

Promover registradores PPC entre blocos ou eliminar gravações de CR mortas
entre blocos exige análise de vida global no HIR. Isso é semanas de trabalho
de compilador, com risco de bugs sutis no jogo (setjmp/longjmp, preempção,
`mfcr`), e o ganho é de energia, sem fps: não implementado.

## Código desta rodada

- `skip_overwritten_transfers_cutout` (GPU, padrão desligado, quirk do
  Forza): `Transfer::draw_cutout` e `GetCutout()`, usados em todas as chamadas
  de `GetRectangles` do backend Vulkan.
- `transfer_cleared_sources_as_clears` (GPU, desligado):
  - `RecordResolveClear` e `IsTransferSourceResolveCleared`;
  - os clears pendentes do passe do draw: `EncodePendingDrawPassClears` e
    `FlushPendingDrawPassClears`.
- `vulkan_samples_as_pixels_2x` (GPU, desligado):
  - `GetEdramTileOwner`;
  - `SetDrawSamplesAsPixels(…, keep_vertical_samples)`: escala de desenho
    2x1, estimativa de altura e retângulo de "pular transferências" só com x
    dobrado.
- `vulkan_fragment_shading_rate` (Vulkan, na inicialização) e
  `vulkan_shading_rate` (GPU, em tempo real):
  - o recurso e as propriedades do `VK_KHR_fragment_shading_rate`;
  - `CmdVkSetFragmentShadingRateKHR` no buffer adiado;
  - o estado dinâmico nos pipelines.
- `spirv_texture_sign_specialization` (GPU, na tradução dos shaders; quirk
  do Forza) e `vulkan_texture_sign_classes` (GPU, por draw):
  - as *specialization constants* `xe_texture_sign_class_N` (SpecId 1000 + N)
    no pixel shader;
  - `PipelineDescription::texture_sign_classes` (16 bits, zero nos
    pipelines guardados antes);
  - o `VkSpecializationInfo` do estágio de fragmento.
- Propriedades novas: `debug.xendroid.transfer_cutout`, `cleared_transfers`,
  `samples_as_pixels_2x`, `shading_rate`, `texture_sign_classes`.
