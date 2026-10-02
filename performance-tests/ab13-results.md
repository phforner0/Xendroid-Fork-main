# AB13 — plano v8: CPU por contadores, JIT, thread de comandos, opções, acima de 30 fps (builds 78 a 88, 2026-10-02)

POCO F7 (Snapdragon 8s Gen 4, Adreno 825, Turnip Gen8 V37 "patched", sysmem),
pacote de teste `xendroid.compose.fork.opt`, cena de referência de sempre
(carro parado na defensa) e, nos braços com `-DriveSeconds`, 30 s acelerando
a fundo com uma foto a cada 5 s. Tudo pela rede: `adb` sobre Tailscale.
Métricas:
- **GPU ms/frame** no teto de 30 fps do jogo;
- **instruções e ciclos por frame por thread**, pelos contadores de hardware
  da CPU (`simpleperf stat --per-thread`, últimos 10 s de cada abertura).

Cada braço é julgado contra os vizinhos da mesma sessão.

## Resumo

**Validação final (S66, ABBA na mesma sessão, cada build com os seus padrões
e quirks):** o fim da v7 (b77) contra o da v8 (b87), no teto de 30 fps:
- **Guest CPU 5 (física): 93,8 → 58,0 M instruções por frame (−38%)**, 36,3 →
  28,6 M ciclos (−21%); Guest CPU 1 −17%, Guest CPU 4 −26%;
- thread de comandos 52,6 → 49,8 M instruções (−5%);
- CPU do processo 106,2 → 98,6 ms por frame (−7%);
- GPU igual (17,7 / 17,6 ms; a v8 mirou a CPU), mesma imagem parado e
  dirigindo, nenhum erro.

E o jogo passa de 30 fps em tempo real: sem o teto de vblank chega a ~49
fps, e o **modo 40 fps** (duas cvars existentes) segura 39,7 fps com a GPU a
71% — mas o SoC fica em 94–96 °C.

| # (v8) | Caminho | Resultado | Situação |
|---|---|---|---|
| 1 | CPU por contadores de hardware nas ferramentas de A/B | instruções/frame repetem ~1% na mesma abertura e 0,1–4% entre aberturas (a Guest CPU 5 às vezes 14–20%) | **feito** (`-Stat`, `forza_cpustat.py`, `jit_dump_compare.py`) |
| 2 | Thread de comandos mais leve | 52,7 → 49,4 M instruções/frame (**−6%**, b77 → b79) | feito; o resto documentado (seção 2) |
| 3 | ADPF | a sessão da thread de comandos abre (alvo 19 ms) e não muda clock nem tempo de CPU; a do áudio passou a abrir (8 threads) | áudio consertado; thread de comandos desligada por padrão |
| 4 | Resolves | (a) ~0,05 ms; (b) sonda sem stencil −0,2 ms; (c) ≤ 0,25 ms | não implementados (teto pequeno; seção 4) |
| 5 | Picos de streaming | decompostos (seção 5); não derrubam mais frames a 30 fps | documentado |
| 6 | Opções do app | MSAA 2x −3,1 ms; AF 16x +0,5 ms (estrada mais nítida); A2C → teste −0,2 ms; VRS 2x1 inconclusivo; modo 40 fps | documentado para a interface Kotlin (seção 6) |
| 7 | JIT | Guest CPU 5 −46% de instruções e −34% de ciclos (b77 → b85, seção 7.6), tudo exato | **padrão** (checagens baratas, atalhos de `stfs`, desvios diretos) + **quirk do Forza** (NaN do host na aritmética, folhas de 32) |
| 8 | Outros títulos | FH2, Halo: Reach e RDR com o JIT novo e com os quirks exatos de GPU: imagens corretas, nenhum erro novo | quirks continuam só do Forza (falta jogo de verdade nos outros) |
| 9 | Acima de 30 fps | sem o teto de vblank o jogo roda a ~45–49 fps **em tempo real** (velocímetro igual) | modo 40 fps documentado (seção 9) |
| 10 | Validação final (S66) | v7 × v8: Guest CPU 5 −38% de instruções, CPU do processo −7%, GPU igual, mesma imagem; modo 40 fps a 39,7 fps | feito |

## 1. CPU por contadores de hardware (item 1)

- `tools/fh_auto.sh`: com `STAT=<s>`, conta os últimos `<s>` segundos de cada
  braço por thread (`simpleperf stat --app ... -e instructions,cpu-cycles
  --per-thread`; `perf_event_paranoid = −1` no aparelho).
- `restart_ab.ps1 -Stat 10` e `tools/forza_auto_ab.ps1 -Stat 10` puxam os
  arquivos; `tools/forza_cpustat.py` soma por grupo (Guest CPU 0–5, comandos,
  XMA, áudio, resto) e dá instruções e ciclos por frame, tempo de CPU por
  frame e o clock médio de cada grupo.
- `tools/jit_dump_compare.py` conta o código de host por função entre
  variantes de `dump_functions_at` (determinístico, independe do que o jogo
  fez na abertura).

Repetição:
- na mesma abertura, duas janelas de 10 s: ~1%;
- entre aberturas, na Guest CPU 1 e na thread de comandos: 0,1–3%; na Guest
  CPU 5, 0,5–4% na maioria dos pares, mas 14–20% em alguns (S65–S67 com
  desvios diretos, a v7 no S66) — por isso dois lançamentos por variante e
  conclusões só com o mesmo sinal nas duas sessões;
- a Guest CPU 0 varia até 2× entre aberturas (a função `sub_82438EA8`, ~100 M
  instruções/frame a mais em algumas): é trabalho do jogo, não do emulador.
  As comparações usam as threads estáveis e a Guest CPU 5.

Tempo de CPU engana: o mesmo trabalho roda de 1,4 a 1,9 GHz de média
conforme o núcleo e o momento.

## 2. Thread de comandos (item 2)

| Build (bases) | Instruções/frame | Ciclos/frame |
|---|---|---|
| b77 (4 aberturas) | 52,7 M | 34,7 M |
| b79 | 49,4 M | 32,1 M |
| b82/b83 | 49,1–49,8 M | 33,7–34,6 M |

O que rendeu (b79): o conjunto das faixas do tiling em tabela plana
(endereçamento aberto, limpa por época) no lugar de `std::unordered_set`, e
`IsResolveDestinationResident` medindo a região só até o tamanho do resolve
(`QueryRegionInfoUpTo`; antes ~100 consultas por frame andavam ~945 páginas
cada). O laço de `EnableAccessCallbacksInner` pula blocos de 64 páginas já
vigiados.

O que não rendeu e foi retirado no b86:
- **cache de `LoadShader`** por endereço e tamanho, comparando o microcódigo
  guardado: no b77, `LoadShader` 2,1% + XXH3 2,1% da thread; no b82, 3,8% +
  0,4%. A comparação vetorizada (~1,5 instruções por dword, com a troca de
  bytes) custa o mesmo que o XXH3 com NEON. Só pular a leitura renderia, com
  vigia de escrita na memória do microcódigo (teto ≤ 4% da thread);
- **cache de `QueryRegionInfo`** por geração da tabela de páginas: 0 acertos
  numa abertura inteira (~2,4 consultas por frame depois do carregamento).

Perfil restante (b82, por instruções): `ExecutePacket` 8,3%,
`WriteRegisterRangeFromRing` 6,9%, `memmove` 5,8%, `ExecutePacketType3` 5,2%,
`clock_gettime` 3,9% (só dos logs de tempo dos testes), `LoadShader` 3,8%,
`UpdateBindings` 3,6%, `IssueDraw` 3,0%, Turnip ~3,4%,
`EnableAccessCallbacksInner` 2,4%. Sem ponto quente dominante: ~25 mil
instruções por draw.

## 3. ADPF (item 3)

- Áudio: a sessão falhava (`createSession failed (10 threads)`: tids
  repetidos e mortos). Agora usa um tid por nome de thread e, se falhar, só os
  vivos: `AudioPerformanceHint: session over 8 threads, target 5333us`.
- Thread de comandos (`gpu_performance_hint`, nova): aprende o intervalo dos
  frames, abre a sessão com esse alvo e informa intervalo − esperas a cada
  frame. S63 (b83, ABCDAB): a sessão abre (alvo 19,0 ms), mas o clock médio da
  thread fica igual (1,65 contra 1,70 GHz), assim como o tempo de CPU (20,3
  contra 20,4 ms/frame), a GPU (18,7 contra 18,8 ms) e os frames acima de
  37 ms (0,3% contra 0,0%). Fica desligada.

## 4. Resolves (item 4)

Medidos com timestamps (S53, S60) e não implementados:
- (a) as 3 faixas num resolve por tipo: os 6 resolves das faixas somam
  ~1,6 ms, e juntar economiza só o custo fixo de despacho (~0,05 ms);
- (b) sonda `gpu_probe` bit 32 (resolves de profundidade sem buscar o
  stencil): 17,25/17,55 contra 17,50/17,67 ms (S60) — teto **−0,2 ms**, e
  buscar o stencil só quando o destino o lê exige rastrear o leitor;
- (c) resolve direto nas faces do cube map: carga + cópia de ≤ 0,25 ms, com
  mips gerados pela GPU no meio.

## 5. Picos de streaming (item 5)

`VkSlowSubmission` (b83, envio com GPU ≥ 15 ms, decomposto por parte com
`-Passes`) mostra dois tipos:
- **parado, ~a cada 10 s**: envios com só 54–111 draws, mas 14 cargas de
  textura (4,5 M texels), 1,6 MB de uploads e resolves de 1280x720. As partes
  mais caras: cargas de texturas de 1 M texels **escritas pela própria GPU**
  (`k_8_8_8_8`, `k_2_10_10_10`, DXT; ~1,4 ms cada com os timestamps
  serializados), resolves 1280x720 (2,2–2,5 ms) e o envio dos uploads
  (1,1 ms);
- **dirigindo**: o passe principal com 270–470 draws em 14–28 ms (poeira e
  efeitos fora da estrada): trabalho real, não streaming.

Com a folga de GPU atual (18,8 ms/frame), esses picos não derrubam frames a
30 fps: máximo 35,2 ms e 0,0% acima de 37 ms (S63, base). Num modo de 40 fps
(25 ms por frame) eles passariam do orçamento; o caminho seria servir essas
texturas pelo resolve direto (formato de leitura diferente do resolvido).

## 6. Opções do app (item 6)

A interface Kotlin é do usuário; as opções existem como cvars (por jogo ou em
tempo real pelas propriedades `debug.xendroid.*` no pacote de teste).

| Opção | cvar | GPU ms/frame | Imagem |
|---|---|---|---|
| MSAA 2x lógico | `msaa_4x_as_2x = true` | **−3,1** (14,5 contra 17,6; S60) | bordas menos suaves |
| VRS 2x1 | `vulkan_shading_rate = 1` | −0,6 ± 0,6 (inconclusivo; S60) | — |
| A2C → teste alfa | `alpha_to_coverage_as_alpha_test = true` | −0,2 (S60) | igual parado; ganho pequeno demais para valer a opção |
| AF 16x | `anisotropic_override = 5` | **+0,5** (19,3 contra 18,8; S63) | estrada e faixas mais nítidas ao longe |
| 40 fps | `guest_display_refresh_cap = false` + `framerate_limit = 40` | ver a seção 9 | — |

## 7. JIT (item 7)

No b77 (S55), a Guest CPU 5 — a thread mais ocupada do jogo, física —
executava ~105 M instruções por frame, 82% delas em código JIT. Nas funções
mais quentes (`sub_82FC6CC0`, `sub_8300D580`, `sub_82FC9120`...), `lfs`/`stfs`
são 35–54% das instruções PowerPC e a aritmética de ponto flutuante ~20%; o
JIT emitia ~18 instruções de host por instrução PPC. Quase todo o excesso
vinha da emulação exata dos NaN do PowerPC.

Métrica: instruções e ciclos por frame das threads estáveis (Guest CPU 1 e 5,
seção 1), restart A/B com dois braços por variante.

### 7.1 Opções medidas no b77 (S56)

| S56 (b77) | Guest instruções/frame | Guest ciclos/frame |
|---|---|---|
| base | 212,2 | 119,5 |
| `a64_vmx_nan_fixup = false` | 205,3 (−3,3%) | 111,9 (−6,4%) |
| `a64_enable_host_guest_stack_synchronization = false` | 200,3 (−5,6%) | 112,5 (−5,9%) |
| `inline_leaf_max_instructions = 32` | 192,2 (−9,4%; Guest CPU 5 −12%) | 112,4 (−5,9%) |

Pelo tempo de CPU (AB9) essas opções pareciam ruído (±20% entre
lançamentos); por instruções, rendem.

### 7.2 `lfs`/`stfs` e aritmética exatas e baratas (b79–b82)

- S59 (b79): `ppc_single_float_keep_nan = false` (a conversão do host, como o
  backend x64) tirava 28,5% da Guest CPU 5 (98,2 → 70,2 M), mas não é exato:
  um dado de 32 bits copiado por registrador de ponto flutuante que pareça NaN
  sinalizador é alterado.
- b82, exato:
  - opcodes HIR `SINGLE_BITS_TO_DOUBLE` / `DOUBLE_TO_SINGLE_BITS`: a conversão
    do host e, só quando dá NaN, o bit *quiet* devolvido fora da linha
    (`ppc_single_float_keep_nan_fast`, padrão);
  - FPU (add, sub, mul, div, FMA): o resultado é NaN exatamente quando uma
    entrada é NaN ou a operação é inválida, então basta checar o resultado e
    escolher o NaN do PowerPC fora da linha (`a64_fpu_nan_fixup_result_check`,
    padrão).
  - S61: Guest CPU 5 **75,3 M** (b82, as duas bases a 0,6% uma da outra)
    contra 104,8 no b77 (**−28%**), com o mesmo resultado.

### 7.3 Desvios das checagens (b84)

Os desvios condicionais do JIT saem sempre na forma de longo alcance
(`b.!cond pula; b alvo; pula:`, para funções acima de 1 MiB), que custa uma
instrução a mais e um desvio tomado quando o desvio original é tomado. As
checagens de NaN faziam `b.vc fim` (tomado em quase todo caso): duas
instruções e um desvio tomado por operação. No b84:
- a checagem desvia só no NaN (`b.vs cauda`): na forma de longo alcance,
  uma instrução (`b.vc pula`) por operação;
- quando o destino é também uma entrada (acumulações), a entrada vai para
  um registrador livre (v3) e usa a mesma checagem, em vez do caminho antigo
  de cinco instruções e um desvio;
- desvios diretos (`a64_near_branches`, com nova emissão da função na forma
  de longo alcance se algum não alcançar).

| S65 (b84, ABCABC) | Guest CPU 5 instruções/frame | Guest CPU 5 ciclos/frame | Guest CPU 1 |
|---|---|---|---|
| b82 (S61, referência) | 75,3 (75,1 / 75,5) | 34,8 | 35,6 |
| longo alcance (`far`) | **67,5** (66,8 / 68,3) | 31,8 | 35,2 |
| diretos (`near`) | 63,2 (67,6 / 58,8) | 30,8 | 34,6 |
| todas as opções inexatas (`all`) | 54,7 | 28,3 | 30,2 |

- As checagens novas tiram **10%** da Guest CPU 5 com qualquer forma de
  desvio (75,3 → 67,5 M), com o mesmo resultado.
- Desvios diretos: o código fica 4–9% menor por função (desvios
  incondicionais 10760 → 5047 nas 10 funções do dump), mas só economizam
  instrução onde o desvio é tomado. A Guest CPU 5 variou muito entre
  aberturas com eles (S65: 67,6 e 58,8 M; S67: 63,5 e 52,7 M), e pouco sem
  eles (66,8 e 68,3; 64,6 e 64,8). Na média nunca ficaram atrás: ciclos −3%
  (S65) e −7% (S67), instruções −2 a −10%. **Padrão** a partir do b87; uma
  função grande demais para eles é emitida de novo na forma de longo alcance
  (e registrada no log).

### 7.4 `stfs` sem conversões inúteis (b85)

`hir_simplify_single_stores` (padrão, b85), na passagem de simplificação do
HIR:
- `stfs` do que um `lfs` carregou (cópia por registrador de ponto
  flutuante) grava os bits carregados: `double_to_single_bits(single_bits_to_double(x))`
  vira `x`, e as duas trocas de bytes (a da carga e a da gravação) se anulam;
- `stfs` de um resultado de precisão simples grava o seu arredondamento:
  `double_to_single_bits(to_single(x))` vira o `fcvt` de `x` para f32 (o mesmo
  arredondamento; um NaN sai quieto de `to_single` como do `fcvt`).

Os dois são exatos. Nas 10 funções do dump, 241 dos 718 `stfs` eram cópias e
246 gravavam um arredondamento; o b85 tira 521 conversões exatas (com as suas
caudas) e 275 trocas de bytes: código −3% a −11% por função.

| S67 (b85, ABCDABCD) | Guest CPU 5 instruções/frame | ciclos/frame |
|---|---|---|
| padrão (diretos + atalhos) | 58,1 (63,5 / 52,7) | 27,9 |
| sem os atalhos (`hir_simplify_single_stores = false`) | 67,9 (66,9 / 68,9) | 31,9 |
| longo alcance (`a64_near_branches = false`) | 64,7 (64,6 / 64,8) | 30,0 |
| quirk do Forza (seção 7.5) | 56,2 (56,4 / 56,0) | 27,2 |

Sem os atalhos, +3,4 a +9,8 M instruções e +2 a +4 M ciclos por frame na
Guest CPU 5 (~5–6%).

### 7.5 Quirk do Forza

A v8 começou mirando `ppc_single_float_keep_nan = false` (S59: −28,5% na
Guest CPU 5). Ele não é exato: um valor de 32 bits copiado por registrador de
ponto flutuante que pareça NaN sinalizador (~0,2% dos padrões aleatórios — e
cópias de estruturas com inteiros passam por FPRs) sai alterado. Com os
caminhos exatos baratos, o quirk ficou só com o que não toca dados:

- `a64_fpu_nan_fixup = false` e `a64_vmx_nan_fixup = false`: as regras de NaN
  do host na aritmética escalar e vetorial (como o backend x64; só muda *qual*
  NaN sai de uma operação com entrada NaN);
- `inline_leaf_max_instructions = 32`.

Ficaram de fora: `lfs`/`stfs` sem a emulação (corrompe dados) e a
sincronização de pilha desligada (−5,6% no S56, mas um `longjmp` do jogo
derrubaria o emulador).

S67: 56,0–56,4 M instruções/frame na Guest CPU 5 contra 52,7–63,5 sem o quirk,
Guest CPU 1 −2%, mesma imagem parado. Ganho pequeno sobre os padrões novos;
quirk do Forza (b86/b87).

### 7.6 Total

Guest CPU 5 (física), média das bases de cada sessão, no teto de 30 fps:

| Build | Mudança | Instruções/frame | Ciclos/frame |
|---|---|---|---|
| b77 (S56) | fim da v7 | 104,8 | 41,5 |
| b82 (S61) | `lfs`/`stfs` e FPU exatos e baratos | 75,3 | 34,8 |
| b84 (S65) | checagens que desviam só no NaN; destino = entrada | 67,5 | 31,8 |
| b84 (S65) | + desvios diretos | 63,2 | 30,8 |
| b85 (S67) | + atalhos de `stfs` | 58,1 | 27,9 |
| b85 (S67) | + quirk do Forza | 56,2 | 27,2 |

**−46% de instruções e −34% de ciclos na thread mais ocupada do jogo**, com o
mesmo resultado (nada de inexato nos padrões; o quirk só muda qual NaN sai de
uma operação com entrada NaN). A validação final mede o conjunto contra o b77
na mesma sessão (seção 10).

## 8. Outros títulos (item 8)

S64 (b85: as checagens de NaN novas, desvios diretos e os atalhos de `stfs`
no JIT; pela rede): três títulos com cena 3D nos menus, cada um aberto duas
vezes sem entrada — os padrões do build, depois os quirks exatos de GPU do
Forza no config do título (`spirv_texture_sign_specialization`,
`spirv_fast_precision_rounding`, `skip_overwritten_transfers` e o recorte,
`vulkan_texture_load_to_image`, `vulkan_direct_host_resolve_to_texture`,
`vulkan_resolve_clear_in_guest_pass`), com fotos aos 75 e aos 110 s:

| Título | Erros no log (padrão / quirks) | Imagem |
|---|---|---|
| Forza Horizon 2 (4D530AA4) | 28 / 28 — só exports do kernel não implementados (XamVoice, XInput FF), os mesmos | correta nos dois (abertura de dia; festival à noite com luzes e partículas) |
| Halo: Reach (4D53085B) | 0 / 0 | correta nos dois (padrão parou no menu da Armory, sem o modelo do Spartan à direita; quirks na abertura da campanha) |
| Red Dead Redemption (5454082B) | 0 / 0 | as mesmas cenas, corretas (guindaste no cais, close de Marston) |

Nenhum erro do JIT (`A64:`) em nenhuma abertura. Os menus avançam sozinhos e
cada abertura para num ponto diferente, então a comparação é visual, não por
pixel. A falta do modelo na Armory do Halo só apareceu na abertura com os
padrões (sem os quirks), então não é efeito deles; não houve abertura com os
quirks parada na Armory para comparar.

Conclusão: os quirks exatos de GPU não quebraram nada nesses três títulos,
mas três aberturas curtas de menu não bastam para torná-los padrão (falta
jogo de verdade, em especial no Halo). Ficam como quirks do Forza; a lista
de candidatos a padrão está na seção "Situação depois do AB13" do plano.

## 9. Acima de 30 fps (item 9)

S63 (b83), braço `uncapped` (`guest_display_refresh_cap = false`: vblanks do
guest sem o teto de 60 Hz):

| | base | sem teto |
|---|---|---|
| fps (últimos 35 s, com 30 s dirigindo) | 30,00 | **48,9** |
| intervalo médio | 33,3 ms | 20,4 ms |
| GPU por frame | 18,8 ms | 19,1 ms (93% ocupada) |
| frames acima de 37 ms | 0,0% | 0,6% (máximo 29,5 ms nos últimos 35 s) |
| thread de comandos | 20,4 ms de CPU por frame | 19,1 ms por frame, 94% de um núcleo |
| SoC/CPU (overlay, foto aos 10 s) | 62 °C | **94 °C** |

O jogo mantém a **velocidade real** sem o teto: o velocímetro nas fotos a
cada 5 s, acelerando a fundo do mesmo ponto, segue a base (5 s: 33/33/34 mph;
10 s: 32/32/36; 15 s: 18/84/88 nas duas bases e sem teto). Se o jogo
contasse vblanks, estaria 1,6× mais rápido.

A ~49 fps a GPU e a thread de comandos estão no limite e o aparelho esquenta
demais. O meio-termo é **40 fps**: `guest_display_refresh_cap = false` com
`framerate_limit = 40` (o limite de apresentação já existente segura o
jogo), cadência exata num painel de 120 Hz (3 atualizações por frame), ~76%
de GPU.

S66 (b87, dois lançamentos com `guest_display_refresh_cap = false` e
`framerate_limit = 40`, 40 s parado e 30 s dirigindo):

| | v8 a 30 fps | v8 a 40 fps |
|---|---|---|
| fps (últimos 35 s) | 30,00 | **39,66** |
| intervalo médio | 33,3 ms | 25,2 ms |
| GPU por frame | 17,6 ms | 17,9 ms (71% ocupada) |
| frames acima de 37 ms | 0,3% | 0,7% (máximo por segundo 32,6 ms) |
| thread de comandos | 49,8 M instruções/frame | 50,0 M por frame real |
| ciclos de CPU por segundo (processo) | 5,1 G | 7,2 G (+42%) |
| SoC/CPU (overlay) | 55–76 °C | **94–96 °C** |

A física (Guest CPU 5) faz o mesmo trabalho por segundo nos dois (passo
fixo, por isso o jogo mantém a velocidade); o render e a thread de comandos
crescem com os frames. O modo funciona e fica estável nos minutos medidos,
mas esquenta o aparelho ao nível do braço sem teto: em sessões longas a GPU
deve perder clock. Vale como opção na interface (com aviso), não como
padrão.

O patch de 60 fps da comunidade não foi baixado (exige permissão): sem o teto
o jogo já passa de 30 fps sozinho, e 60 fps não cabem na GPU nem na thread de
comandos deste aparelho.

## 10. Validação final

S66 (b77 contra b87, ABBA com reinício, o APK instalado antes de cada braço,
cada um com os seus padrões e quirks; depois dois braços do modo 40 fps; os
contadores de hardware nos últimos 10 s parado e 30 s dirigindo com uma foto
a cada 5 s):

| S66 | v7 (b77) | v8 (b87) |
|---|---|---|
| fps | 29,99 | 30,00 |
| GPU por frame | 17,7 ms | 17,6 ms |
| frames acima de 37 ms | 0,1% | 0,3% |
| Guest CPU 5, instruções / ciclos por frame | 93,8 / 36,3 M | **58,0 / 28,6 M** (−38% / −21%) |
| Guest CPU 1 | 39,5 / 24,5 M | 32,9 / 20,4 M (−17%) |
| Guest CPU 4 | 20,6 / 15,6 M | 15,3 / 13,0 M (−26%) |
| thread de comandos | 52,6 / 33,7 M | 49,8 / 32,2 M (−5%) |
| CPU do processo por frame | 106,2 ms | 98,6 ms (−7%) |

(A Guest CPU 0 variou de 38 a 143 M entre aberturas nos dois builds: trabalho
do jogo, fora da comparação.)

Imagem: a mesma parado e nas fotos dirigindo (velocidades na mesma faixa a
cada 5 s; o traçado muda de abertura para abertura). Nenhuma função do JIT
precisou ser emitida de novo na forma de longo alcance (sem a linha de log
`too large for direct branches`).

O build final do PR (b88) é o b87 com dois textos de ajuda de cvars
atualizados e um comentário; o mesmo código.

## Becos sem saída (v8, não repetir sem fato novo)

- **Escrita do FPCR por bloco no JIT**: microbenchmark no aparelho (entre o S63 e o S65):
  `ldr` + `msr FPCR` custam ~0,1 ns por escrita nos A720 (onde rodam as
  threads do jogo) e +0,75 ns no X4. Não compensa mudar o protocolo de modos.
- **Cache de `LoadShader`** e **cache de `QueryRegionInfo`** (seção 2).
- **ADPF na thread de comandos** (seção 3).
- **`ppc_single_float_keep_nan = false` como quirk**: corromperia dados de 32
  bits copiados por registradores de ponto flutuante que pareçam NaN
  sinalizador (~0,2% dos padrões aleatórios); o modo exato custa pouco
  depois da v8 (seção 7).
- Resolves 4a–4c (seção 4); push descriptors (um conjunto por layout, ≤ 5% da
  thread de comandos); memória compartilhada sem cópia (o driver não expõe
  `VK_EXT_external_memory_host`); A2C → teste alfa (−0,2 ms);
  reafirmar o turbo da GPU a cada 5 s (sem efeito, S60).
