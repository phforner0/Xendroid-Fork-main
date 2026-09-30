# AB6 — cargas de textura e resolves (builds 13 a 17, 2026-09-29)

POCO F7 (Adreno 825, Turnip Gen8 V36, sysmem), pacote de teste
`xendroid.compose.fork.opt`, cena de referência (carro parado na defensa a
2,5 mi). Configuração comum: `render_target_7e3_as_r11g11b10 = true` e o spin
park direcionado (`829F04A8`, modo 1). A/B em tempo de execução: a mesma
sessão de jogo alterna a opção por propriedade `debug.xendroid.*` em braços
de 30 s (com timestamps por passe, `-Passes`: valem as proporções) ou 40 s
(sem timestamps: FPS real). Os braços são comparados um a um; o primeiro
braço depois de "scene stable" às vezes sai da curva (mais frio, ou com um
engasgo) e foi descartado onde indicado.

## Resumo

| Mudança | Resultado | Decisão |
|---|---|---|
| **Untile coalescido** (`vulkan_texture_load_coalesced`, build 14) | untile **−42%** (5,30 → 3,07 ms/frame); sem timestamps **+3,7% FPS**, GPU −6% (38,3 × 40,8 ms) | ligado por padrão (exato) |
| **Carga direto na imagem** (`vulkan_texture_load_to_image`, build 16) | cópia buffer → imagem 2,14 → 0,11 ms/frame, untile +0,2 ms, passes iguais; sem timestamps **+4,5% FPS**, GPU −5,4% (36,1 × 38,1 ms) | ligado no Forza Horizon via `game_quirks.cc` |
| Resolve direto × dump pela EDRAM (`debug.xendroid.direct_host_resolve`) | resolves 6,8 × 7,6 ms/frame (direto melhor) | mantém o direto |
| Juntos (sobre o build 13) | cargas de textura 7,3 → 3,3 ms/frame; ~+8% FPS | — |

Os dois são exatos: o mapeamento coalescido foi provado igual ao original
(mesmos grupos de 16 bytes, mesmo destino) em 855 combinações de largura,
altura, pitch e tiling (linear, 2D e 3D), e as capturas de tela da cena
parada não mostram diferença além do movimento natural da câmera.

## Contexto

Cada carga de textura é um compute que "destila" o tiling do Xenos para um
buffer temporário, seguido de `vkCmdCopyBufferToImage`, com duas rodadas de
barreiras. ~12 cargas de tela cheia (1280×720: `k_2_10_10_10` ×4,
`k_8_8_8_8` ×3, `k_24_8` ×4, `k_24_8_FLOAT` ×1) acontecem em todo frame,
porque a memória delas acabou de ser escrita por um resolve. O build 13
separou o tempo das duas metades (`texload` e `texcopy` no `VkMiscTime`):
untile ~5,3 ms e cópia ~2,0 ms por frame (com timestamps).

## 1. Untile coalescido (builds 14–15)

No shader de 32 bits por bloco, cada thread copiava 8 blocos seguidos
(`x = 8t`, dois acessos de 16 bytes em `x` e `x + 4`), então cada instrução
de acesso de uma wave cobria metade de cada linha de cache. A variante nova
dá aos 4 threads de cada linha do grupo os blocos `4t…4t+3` e
`16+4t…19+4t`: cada instrução cobre linhas inteiras, tanto no endereço
tilado de origem (o bit 7 do endereço é `X[4] ^ Y[3]`, por isso o salto de
16 blocos é feito com XOR) quanto no destino linear. Mesmas escritas,
mesmo número de grupos. Vale para `32bpb`, `depth_unorm` e `depth_float`
não escalados.

Com timestamps (braços alternados, 30 s):

| braço | coalescido | GPU ms | texload + texcopy ms |
|---|---|---|---|
| 1 | sim | 39,37 | 5,20 |
| 2 | não | 41,54 | 7,30 |
| 3 | sim | 39,37 | 4,97 |
| 4 | não | 41,57 | 7,36 |
| 5 | sim | 39,26 | 5,14 |

Sem timestamps (40 s; o braço 1, a 11,7 FPS com GPU normal, descartado):

| braço | coalescido | FPS | GPU ms |
|---|---|---|---|
| 2 | não | 23,48 | 40,70 |
| 3 | sim | 24,17 | 38,34 |
| 4 | não | 23,07 | 40,83 |
| 5 | sim | 24,13 | 38,24 |

## 2. Carga direto na imagem (builds 16–17)

As texturas de um nível 2D sem escala, em `R8G8B8A8_UNORM`,
`A2B10G10R10_UNORM_PACK32` ou `R32_SFLOAT` (as dos shaders `32bpb`,
`depth_unorm` e `depth_float`), são criadas com um alias `R32_UINT` de
armazenamento (`MUTABLE_FORMAT` + `EXTENDED_USAGE` + `STORAGE`, a mesma
técnica da promoção de destinos de resolve). A variante `_image` dos
shaders usa o mapeamento coalescido e grava os texels com `imageStore`
direto na imagem (layout `GENERAL`, uso `kLoadStorageWrite`); não há buffer
temporário nem cópia. No Turnip do Adreno 7xx gen 3 / 8xx a textura mantém a
compressão UBWC com esses flags (lista de formatos compatível), então a
amostragem não piora — confirmado: os passes ficaram iguais. Em GPUs em que
`STORAGE` desliga a compressão isso não vale, por isso a opção é por jogo.

A opção é lida na inicialização para criar os aliases
(`VulkanTextureCache: storage aliases for texture loads straight into images
enabled` no log); `debug.xendroid.texload_to_image` troca só o caminho da
carga, para comparar sobre as mesmas imagens.

Com timestamps (coalescido ligado nos dois; braço 1 descartado — mais frio,
GPU 35,4 ms):

| braço | na imagem | GPU ms | passes ms | texload + texcopy ms | resolves ms |
|---|---|---|---|---|---|
| 2 | sim | 37,80 | 26,66 | 3,23 | 7,44 |
| 3 | não | 39,38 | 26,22 | 5,26 | 7,31 |
| 4 | sim | 37,33 | 26,36 | 3,27 | 7,27 |
| 5 | não | 39,57 | 26,45 | 5,15 | 7,28 |
| 6 | sim | 37,33 | 26,26 | 3,27 | 7,34 |

Sem timestamps (40 s):

| braço | na imagem | FPS | GPU ms |
|---|---|---|---|
| 1 | não | 23,94 | 38,86 |
| 2 | sim | 25,41 | 36,02 |
| 3 | não | 24,44 | 37,87 |
| 4 | sim | 25,48 | 36,09 |
| 5 | não | 24,41 | 38,02 |
| 6 | sim | 25,68 | 36,07 |

## 3. Resolves

O build 15 passou a rotular cada resolve com o formato e o MSAA da origem e a
cronometrar só os dispatches da cópia (`resolve dispatch` no `VkMiscTime`);
o build 17 registra uma vez por configuração o que cada resolve direto lê
(`VkDirectResolve`). Com timestamps, os resolves somam ~7 ms/frame, quase
tudo dentro dos dispatches:

| resolve | ms/frame | origem |
|---|---|---|
| `color+clear 1280x256 k_2_10_10_10_FLOAT 4x` (×2) | 1,1–1,2 | cena principal, MSAA 4x |
| `depth 1280x256 kD24FS8 4x` (×2) | 0,75–0,8 | idem |
| `depth 512x512 kD24S8 1x` | 0,7–0,8 | atlas de sombra (RT em 720 tiles, pitch 13, 1040×2528) |
| `color 1280x720 k_8_8_8_8 1x` | 0,35 | — |
| `depth 1024x1024 kD24S8 1x` | 0,25 | o mesmo atlas de sombra |

O caminho direto (compute lendo o render target) ganha do dump pela EDRAM no
total (6,8 × 7,6 ms/frame). O resolve `depth 512x512` custa ~0,7 ms nos dois
caminhos, embora o `1024x1024` leia o quádruplo do mesmo render target em
0,25 ms: o custo parece ser da primeira leitura depois de desenhar o atlas
(o Turnip emite os flushes de cache pendentes no dispatch seguinte), não do
shader. Próximo passo possível: medir com um "absorvedor" desses flushes antes
do dispatch cronometrado.

## 4. Padrões (build 18)

O coalescido passou a ser o padrão (`vulkan_texture_load_coalesced = true`),
e o Forza Horizon liga a carga direto na imagem por `game_quirks.cc`. Com a
configuração por jogo sem nenhuma das duas opções, o log mostra
`Game quirk for 4D5309C9: vulkan_texture_load_to_image` e os aliases criados
(`storage aliases ... enabled`). O FPS desse lançamento (18,4, GPU 52 ms/frame,
estável do começo ao fim) não é comparável com os anteriores: o celular já
começou quente (bateria a 42,7 °C, depois de outras três sessões) e o código é
o mesmo do braço "sim" do build 17 (36 ms/frame). A comparação que vale é a
das seções 1 e 2, com os braços alternados na mesma sessão.

## Ferramentas

- `tools/forza_passres.py` lê os rótulos novos dos resolves (formato e MSAA).
- Scripts da sessão (fora do repositório): `seg_breakdown.py` (médias por
  valor da propriedade) e `seg_arms.py` (valores por braço), `img_diff.ps1`,
  `img_diffmap.ps1` e `img_crop3.ps1` (comparação de capturas).
