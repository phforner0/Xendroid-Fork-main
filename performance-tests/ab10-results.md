# AB10 — reanálise v6: transferências 4x↔1x, estimador de retângulos, LOD implícito (builds 54 a 58, 2026-10-01)

POCO F7 (Adreno 825, Turnip Gen8 V37 "patched", sysmem), pacote de teste
`xendroid.compose.fork.opt`, mesma cena de referência (carro parado na
defensa). Tudo pela rede: `adb` sobre Tailscale, com o celular no carregador
de parede e sem cabo. Métrica: **GPU ms/frame no teto de 30 fps do jogo**.
Julgamento braço a braço: cada braço contra a média dos vizinhos.

## Resumo

| # | Caminho | Resultado | Situação |
|---|---|---|---|
| 1 | Draws 4x com pixel shader simples desenhados nas "amostras como pixels" (`vulkan_samples_as_pixels_simple_ps`) | transferências 8071 → 3703 tiles/frame; **GPU −1,7 ms (−5,2%)**; frames > 37 ms 4,5% → 0,9%; mesma imagem | quirk |
| 2 | Pular transferências: alfa ignorado sem pixel shader | −720 tiles/frame (o "clear" 4x do depth da iluminação) | no código |
| 3 | Pular transferências: amostras como pixels, retângulos nunca descartados por *culling*, Z com *depth clamp*, altura exata das amostras como pixels | transferidos 3703 → ~2950 tiles/frame; pulados ~4800 | no código |
| 4 | Interpretador de vertex shader: formatos compactados sem sinal | os quads de pós-processamento saíam como "retângulos vazios" (Y = cópia de X) | correção |
| 3+4 | `skip_overwritten_transfers` com os casos novos (S31, b58) | **GPU −1,4 ms** por braço contra os vizinhos (médias 31,1 / 31,7 ms, puxadas pelo 1º braço frio); mesma imagem | quirk existente |
| 5 | LOD implícito em fetch 2D (`spirv_texture_implicit_lod`) | ABBAAB: base 31,7 / LOD implícito 32,0 ms (+0,3; −1,0 sem o 1º braço, frio) | sem ganho claro; opção desligada |
| 6 | Stencil "não usado" do atlas de sombra | sem efeito: algum draw do atlas usa stencil | removido |
| — | Código final (b59, S32, só os quirks) | parado 29,73 fps, GPU 32,0 ms/frame; **transferidos 8791 → 2314 tiles/frame** desde o b54 (pulados 5152); dirigindo 25 s sem defeitos nas fotos | commits locais |

## 1. As idas e voltas 4x ↔ 1x (S26, S28)

O rastreamento de EDRAM do S26 (b54) com o motivo de cada transferência
mantida mostrou o maior bloco restante: o mesmo depth/stencil (base 0, pitch
de 16 tiles) usado alternadamente como 640x360 4x e como 1280x720 1x:

- um "clear" de depth 4x por quad no começo do frame;
- o pré-passe de depth 1x (325 draws);
- marcação de stencil em 4x (1 draw com pixel shader sem textura, mais um com
  teste de depth *never*);
- iluminação em 1x (3 draws);
- mais uma marcação em 4x e mais iluminação em 1x (8 draws).

Cada troca transferia os 720 tiles da superfície: **4320 dos 8791 tiles
transferidos por frame**, mais 720 de cor (720t, 8888). O
`vulkan_depth_4x_as_1x` só reescrevia draws de depth sem pixel shader em
superfícies nunca desenhadas com cor, e a marcação de stencil tem cor.

`vulkan_samples_as_pixels_simple_ps`:
- **Quais draws:** os 4x com pixel shader que não lê textura nem gradiente
  nem posição, sem *memexport* e sem *alpha to coverage*.
- **Como:** vão para a superfície 1x das amostras, como os de depth. A cor
  deles não faz a superfície virar "cena multisample".
- **Diferença:** o pixel shader roda por pixel 1x em vez de por pixel 4x —
  igual para cores constantes; valores interpolados tomados no centro do pixel
  1x.

| S28 (b55, braços de 25 s, ordem 1 0 1 0 1 0) | GPU ms/frame |
|---|---|
| 1 ligado | 29,73 |
| 2 desligado | 33,02 |
| 3 ligado | 31,97 |
| 4 desligado | 32,06 |
| 5 ligado | 31,13 |
| 6 desligado | 32,73 |
| média ligado / desligado | **30,9 / 32,6** (fps 29,92 / 29,75; > 37 ms 0,9% / 4,5%) |

`VkXfer` por frame: 33 transferências e 8071 tiles desligado; 32 e 3703
ligado. As fotos por braço são iguais (o carro, a defensa, as sombras), a não
ser pela luz do dia que avança.

## 2–4. Estimador de retângulos e "pular transferências"

O trace com o modo novo ligado (S28) e o do b57 (S30) mostraram o que ainda
impedia pular transferências de alvos que começam com um quad:

| Caso | Motivo | Correção |
|---|---|---|
| "clear" 4x de depth sem pixel shader, com o teste alfa ligado no registrador | sem pixel shader não existe alfa | o alfa só conta com pixel shader |
| draws "amostras como pixels" | excluídos por inteiro | retângulo e scissor dobrados para os pixels da superfície 1x |
| quads de pós-processamento com *cull* de faces traseiras | o *culling* excluía o draw | listas de retângulos não são polígonos para o host (`draw_util::IsPrimitivePolygonal`): nunca são descartadas. Uma primeira versão (b57/b58) calculava a orientação e marcou como descartado um quad que o host desenha |
| quads de "clear" com Z fora de 0..1 | o recorte de profundidade poderia descartá-los | em draws sem recorte o host faz *depth clamp* (`depthClampEnable`): aceitos |
| quads 4x de 360 linhas | contavam 722 linhas 1x e tomavam a fileira de tiles seguinte (outra superfície) | altura calculada direto na escala dobrada (720) |
| quads de pós-processamento "vazios" | o interpretador lia Y = X nos formatos compactados sem sinal | deslocamento por componente (como no tradutor SPIR-V) e offsets zerados |

Com o motivo de cada falha no trace (`EstimateRectangle` agora diz por quê,
com os vértices quando o retângulo é inválido) e os shaders do primeiro draw
de cada binding.

O interpretador de vertex shader (`ShaderInterpreter`, usado pelo estimador
de altura e de retângulos) tinha um defeito nos formatos compactados sem
sinal (k_16_16, k_8_8_8_8, k_2_10_10_10...):
- o componente era mascarado sem ser deslocado pelo seu offset, então Y
  repetia X;
- `packed_offsets[0]` ficava sem inicializar.

O trace mostrou o quad de pós-processamento com os vértices (0,360), (0,360),
(1280,360). Corrigido igual ao tradutor SPIR-V (extração no offset, offsets
zerados). Isso também afeta a estimativa de altura de draws sem recorte (a
EDRAM que eles tomam).

| S31 (b58, `debug.xendroid.skip_overwritten_transfers`, 25 s) | GPU ms/frame |
|---|---|
| 1 desligado | 29,27 (frio) |
| 2 ligado | 30,78 |
| 3 desligado | 32,53 |
| 4 ligado | 30,95 |
| 5 desligado | 33,20 |
| 6 ligado | 31,62 |

Cada braço ligado fica 1,6–1,9 ms abaixo dos vizinhos desligados (o 2º, logo
depois do 1º frio, −0,1). As fotos ligado/desligado são iguais. No trace,
64,6% dos tiles que trocariam de dono são pulados. O resto vem de:
- quads que cobrem só parte das fileiras de tiles que tomam (80x64 de
  1280x64);
- listas de 8 retângulos;
- vertex shaders que leem textura;
- o atlas de sombra, cujo stencil algum draw usa (a opção de stencil "não
  usado" não teve efeito no S30 e saiu).

## 5. LOD implícito (S29, b55, ABBAAB com reinício)

Nos fragment shaders pesados, cada fetch 2D é 4 derivadas (`dsx`/`dsy`) e uma
amostra com gradientes (`samgq`), todos no pipe de textura: 1882 `samgq` e
3498 `dsx`/`dsy` nos shaders despejados. `spirv_texture_implicit_lod` passa
os fetches 2D com LOD calculado para LOD implícito + bias (como os cubos já
faziam). Cada braço tinha o seu cache de pipelines.

| Braço | GPU ms/frame |
|---|---|
| 1 base (frio) | 29,2 |
| 2 LOD implícito | 32,0 |
| 3 LOD implícito | 32,2 |
| 4 base | 33,5 |
| 5 base | 32,4 |
| 6 LOD implícito | 31,7 |

Médias 31,7 (base) e 32,0 (LOD implícito); sem o primeiro braço, 33,0 e 32,0.
O efeito, se existe, é menor que a deriva térmica (~1 ms): o passe principal
não é limitado pelo número de instruções no pipe de textura. A opção fica,
desligada.

## Outros achados do S26/S27

- **Driver:** o Turnip Gen8 V37 expõe `VK_KHR_fragment_shading_rate`,
  `VK_EXT_multi_draw`, `VK_EXT_descriptor_buffer`,
  `VK_EXT_rasterization_order_attachment_access` e
  `VK_KHR_shader_float16_int8` (lista completa no log: "Supported Vulkan
  device extensions").
- **CPU por thread (S27):** comandos da GPU 70%; Guest CPU 5, 1 e 0 com 56%,
  44% e 41%; XMA 11%.
- **JIT:** o a64 grava cada registrador PPC no contexto a cada instrução e cada
  comparação escreve 3 bytes de CR. O prólogo de cada função faz ~15
  instruções de rastreio de pilha. É energia, não GPU.
- **Passe principal:** 3 faixas de 1280x256 4x (tiling predicado), ~650 draws
  cada.
