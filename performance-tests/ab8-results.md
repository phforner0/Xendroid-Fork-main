# AB8 — os dez caminhos da reanálise (builds 29 a 42, 2026-09-30)

POCO F7 (Adreno 825, Turnip Gen8 V37 "patched", sysmem), pacote de teste
`xendroid.compose.fork.opt`, cena de referência (carro parado na defensa a
2,5 mi). Configuração comum: `render_target_7e3_as_r11g11b10 = true` e o spin
park direcionado (`829F04A8`, modo 1). Mesma metodologia do AB7: A/B em tempo
de execução por propriedade `debug.xendroid.*` (braços de 40 s sem timestamps,
ou de 30 s com timestamps por passe), ou um lançamento por braço para opções
lidas na inicialização; julgamento braço a braço.

## O quadro mudou: com o celular frio o Forza está no teto de 30 fps

Com o celular frio (18 °C) todos os braços da sessão S7 deram **29,3 a 30,0
fps**: é o limite do próprio jogo (30 fps, como no Xbox 360). A GPU trabalha
~29 ms dos 33,3 ms do frame. Consequências:

- Com o celular frio não há fps a ganhar. O que vale é **menos trabalho e
  menos energia** — a GPU ocupada 89% do frame sobe de temperatura e, quente,
  perde clock e passa a limitar (37 → 48–52 ms/frame, ~20–25 fps).
- Métrica principal dos A/B: **GPU ms/frame** no teto (e fps quando quente).
- Economias só de CPU não viram fps; viram energia (calor) — por isso os
  consertos de CPU abaixo contam.

**Aviso sobre os builds 29–34:** a árvore de build no WSL tinha o
`game_quirks.cc` anterior ao PR #8, então esses builds rodaram o Forza **sem**
o quirk `host_alpha_to_coverage` (A2C emulado no shader). As comparações dentro
de cada sessão continuam válidas (os dois braços tinham o mesmo A2C); os
valores absolutos de GPU das sessões S7, S8 e S9 são ~0,9 ms maiores que com o
A2C pelo hardware. Corrigido a partir do build 35 (e o processo de sincronização
agora inclui o que a `main` mudou desde a última sincronização).

## Resumo

| # | Caminho | Resultado | Situação |
|---|---|---|---|
| 1 | Perfil da thread de comandos | `TimerQueue` e Logger em espera "spin" (~16% de um núcleo): −75% e −81% nessas threads | consertado (Android) |
| 2 | `submit_draws` | todos os valores no teto de 30 fps | mantido 1300 |
| 3 | GMEM do Turnip | automático: +2,4 ms de GPU | mantido sysmem |
| 4 | Estado redundante | `BindIndexBuffer` 2726 → 157 por frame | padrão |
| 5 | Preâmbulos na CPU | 480/489 estágios já com "early preamble" | descartado |
| 6 | Quebras de passe | `vulkan_resolve_draw_barriers_at_resolve`: passes 205 → ~133 por frame, replay −5%; GPU −0,3 ms morno, igual no teto | padrão |
| 7 | Escritor do `WAIT_REG_MEM` | thread `01000010`, função `829EEC48`; latência de ~0,5 ms do polling | diagnóstico |
| 8 | Resíduos (atlas de sombra) | `vulkan_depth_4x_as_1x`: GPU −1,1 ms (−3,4%), mesmas sombras | quirk do Forza |
| 9 | Qualidade | MSAA 4x → 2x: −15% de GPU com artefato; A2C → teste alfa: sem ganho | 2x experimental, desligado; teste alfa removido |
| 10 | Energia | sem medidor utilizável | proxies: GPU/frame e CPU por thread |

A validação final dos padrões novos contra os antigos (seção 11) deu passes
212 → 129 por frame, transferências 16,4 → 12,5 mil tiles por frame, GPU
36,2 → 35,4 ms e 27,3 → 27,9 fps com o celular esquentando, sem diferença de
imagem nas capturas da direção.

## 1. Thread de comandos e CPU: perfil com símbolos (S7, build 29; S10a, build 35)

`simpleperf record --call-graph fp -e cpu-clock -f 1000 --duration 15`,
simbolizado com o `libe.so` sem strip do mesmo build. Com o celular frio, no
teto de 30 fps, o processo ocupa em média **~3,4 núcleos**:

| thread | % das amostras (S7) |
|---|---:|
| Guest CPU 5 / 1 / 0 / 4 / 3 / 2 | 18,5 / 15,2 / 14,0 / 8,2 / 5,9 / 2,5 |
| GPU Commands (thread de comandos) | 15,2 |
| XMA Decoder | 5,5 |
| "Emulator" (na verdade o `TimerQueue`) | 4,0 |
| Logging Writer | 2,0 |
| AudioTrack + Audio Worker | 3,5 |

Dois desperdícios puros, no código base do xenia:

- **`TimerQueue`**: espera "spin" da disruptorplus entre timers. Cada espera
  cria um `spin_wait`, cujo `reset()` chama `std::thread::hardware_concurrency()`
  — no bionic isso abre e lê `/sys/devices/system/cpu/online` a cada chamada
  (49% da thread) — e o resto é `sched_yield` em laço (33%). ~13% de um núcleo.
- **Logger**: a thread de escrita espera sem timeout na mesma estratégia spin:
  `sched_yield` sem fim (85% da thread), ~6% de um núcleo.

Consertos (Android): o `spin_wait` guarda o número de CPUs numa estática; o
`TimerQueue` usa a estratégia bloqueante da própria disruptorplus (mutex +
variável de condição até o próximo vencimento ou um timer novo; prazo de 100
ms quando ocioso); o Logger consulta a fila sem bloquear e dorme quando vazia.
Resultado em 15 s de perfil: thread do TimerQueue **2032 → 510 amostras
(−75%)** (S10a) e 557 no build final (S14); Logger **998 → 188 (−81%)**
dormindo 4 ms (S10a) e **264 (−74%)** com os 2 ms do build final (S14, as
linhas chegam ao arquivo mais cedo): ~15% de um núcleo a menos.

Na thread de comandos (S7): replay para o Vulkan 19%, emissão de draws 13%,
`UpdateBindings` 12% (cópia das constantes por draw), `memmove` 12% (cadeias
quebradas pelo unwind por frame pointer), escrita de registradores do ring 5%.
Com o jogo no teto de 30 fps, CPU da thread de comandos vira energia, não fps.

## 2. `submit_draws` com o celular frio (S7)

Braços em tempo real 1300/600/1300/2000/1300/900: **29,3 a 30,0 fps e GPU 29,3
a 29,7 ms em todos** — no teto do jogo não há diferença. Fica 1300.

## 3. GMEM do Turnip (S8, lançamentos alternados)

| modo | fps | GPU/frame |
|---|---:|---:|
| `TU_DEBUG=sysmem` (atual) | 30,00 | 32,5 ms |
| Turnip escolhe por passe (`turnip_debug = ""`) | 28,12 | 34,9 ms |

O modo automático é pior (+2,4 ms de GPU). Fica o sysmem.

## 4. Estado redundante por draw (S7 → S10a)

Contagem de comandos reproduzidos por frame (`debug.xendroid.replay_stats`,
linhas `VkReplay`): ~2900 draws, 4618 binds de descriptor sets (só 21
repetidos), 1003 binds de pipeline, estados dinâmicos repetidos ~37 vezes cada
por frame — pouca redundância. A exceção era o **`BindIndexBuffer`: 2726 por
frame, 829 idênticos ao anterior**. Os índices DMA do guest agora ficam
ligados uma vez no início do buffer de memória compartilhada e cada draw começa
em `firstIndex` = base / tamanho do índice (a base já é alinhada ao tamanho);
os outros só são religados quando mudam. Resultado: **2726 → 157 binds por
frame** (CPU; na GPU o Turnip põe o endereço no próprio pacote de draw).

## 5. Preâmbulos por draw

Os preâmbulos de 190–890 instruções decodificam as constantes de fetch, mas
**480 dos 489 estágios** do dump de IR já usam o "early preamble" do a7xx+ (o
preâmbulo roda sobreposto ao draw anterior) e o preâmbulo é ~1–2% do trabalho
de ALU do passe principal. Reescrever a decodificação no CPU não compensa.
Descartado.

## 6. Quebras de render pass (S10a–S12, builds 35–40)

O plano previa resolves dentro do passe com conversão R11G11B10 para as ~210
quebras de passe por frame. Os diagnósticos novos mostraram outra coisa:

- `VkPassEnd` agora conta os passes que **reabrem exatamente o framebuffer que
  acabou de fechar**: ~169 dos ~205 inícios de passe por frame, só 9 deles com
  um resolve no meio.
- `VkPassEndBarriers` conta quem empurrou as barreiras que encerraram cada
  passe (o escopo `PassEndReason` aberto quando cada barreira foi empurrada):
  resolve 92, memória compartilhada 78, texturas 78, render targets 22 por frame.

O padrão: o jogo faz ~81 resolves por frame com clear, e o clear roda dentro
do passe do guest (`vulkan_resolve_clear_in_guest_pass`, ligado no config do
app de teste). A cópia encerra o passe (necessário), o clear **reabre o mesmo
framebuffer**, e o draw seguinte empurra a volta da memória compartilhada ao
uso de leitura (o compute do resolve a escreveu) e a transição da textura que
o resolve gravou — **quebrando o passe de novo** só para reabri-lo.

**`vulkan_resolve_draw_barriers_at_resolve`** (novo, ligado por padrão,
`debug.xendroid.resolve_draw_barriers`): o resolve emite essas duas barreiras
ele mesmo — a da textura logo após o dispatch, a da memória compartilhada antes
de reabrir o passe para o clear —, e elas saem junto com as do resolve.

| | desligado | ligado |
|---|---:|---:|
| inícios de render pass por frame (S12) | 205 | **131–135** |
| reaberturas do mesmo framebuffer | 170 | 98 |
| origem memória compartilhada / texturas | 78 / 78 | 2 / 12 |
| GPU/frame (celular morno, 3 × 2 braços) | 36,0 ms | 35,7 ms |
| GPU/frame (no teto de 30 fps, S14, build 42, 2 × 2 braços) | 27,95 ms | 27,94 ms |
| replay dos comandos por frame (S14) | 3,29 ms | 3,11 ms |

−35% de passes, mas só −0,3 ms de GPU com o celular morno e nada no teto (a
thread de comandos ganha ~5% no replay): sem uma barreira drenando o
pipeline, fechar e reabrir um passe custa ~4 µs no Turnip em sysmem — bem
menos que a estimativa de ~15 µs do plano, que vinha de quebras com barreira.
Fica ligada por padrão pelos passes e pela CPU, sem custo de GPU. Os resolves
dentro do passe com conversão R11G11B10 ficaram sem motivo: o caminho in-pass
atual só aceita fontes 1x de formato bit-equivalente, e as quebras que sobram
são as cópias de resolve (necessárias, ~92 por frame).

## 7. Quem escreve o flag do `WAIT_REG_MEM` (S9, build 30)

Diagnóstico novo: ao logar uma espera de memória não atendida
(`debug.xendroid.wrm_log N`), a thread de comandos arma uma vigilância de
escrita na página do flag e o handler de falha registra quem escreveu. Em 20
esperas seguidas, sempre o mesmo escritor: **thread do guest `01000010`,
função `829EEC48` (instrução `829EECD4`)**, zerando o flag em `1FCA4004` (a
thread de comandos espera ele voltar a 0). A espera dura 2–20 ms por frame
(média ~11 ms) e a thread de comandos só percebe a escrita **84–1036 µs depois
(média ~0,5 ms)**, pelo polling de 1 ms. Com o jogo no teto e a GPU limitando
quando quente, essa latência não está no caminho crítico; sem ação.

## 8. Resíduos: o atlas de sombra e o truque "amostras como pixels"

O diagnóstico novo de transferências (`VkXfer`, por par origem → destino) e
o bin trace mostraram o que havia por trás da "anomalia" do `depth 512x512` e
do `1024x1024` caindo no caminho da EDRAM: o atlas de sombra (depth D24S8,
base 720t, pitch 13t) é **limpo com um quad a 4x MSAA em meia resolução**
(`520x1264`, 1 draw) antes de cada cascata, que depois é desenhada a 1x
(`1040x2528`). É o truque do 360 de gravar 4 amostras por pixel pelo custo de
um: a EDRAM guarda as amostras 2x2 de um pixel 4x como 2x2 pixels do 1x. No
emulador cada limpeza custava duas transferências de posse (1x → 4x e 4x →
1x, ~2300 e ~1900 tiles por frame), e os resolves pegavam o atlas dividido
entre os dois render targets.

**`vulkan_depth_4x_as_1x`** (novo, categoria GPU, `debug.xendroid.depth_4x_as_1x`):
draws 4x só de depth (sem pixel shader) em superfícies nunca desenhadas com cor
são desenhados direto no render target 1x que ocupa os mesmos tiles, com o
`RB_SURFACE_INFO` reescrito só durante o draw (1x, pitch em pixels ×2) e a
escala do draw ×2 (viewport, scissor, estimativa de extensão das linhas
reivindicadas). O depth sai nos centros dos pixels da grade dobrada em vez das
posições de amostra do 4x (até 1/4 de pixel 1x de diferença).

| | desligado | ligado |
|---|---:|---:|
| GPU/frame, sem timestamps (S10a, 3 × 2 braços) | 32,3 ms | **31,2 ms** |
| transferências / tiles por frame | 55 / 16,6 mil | 48 / 12,1 mil |
| passes 520x1264 + 1040x2528 (com timestamps) | 1,48 ms | 0,55 ms |
| quebras de passe por barreira de imagem | 27/frame | 21/frame |

Sombras iguais nas capturas. **−1,1 ms de GPU (−3,4%)**.

No build atual a anomalia do `depth 512x512` também desapareceu com a opção
desligada (0,07–0,08 ms, antes ~1 ms), e o `1024x1024` sai sempre pelo
caminho direto (0,21–0,26 ms) — a combinação com o A2C pelo hardware e o
Turnip V37 mudou esse trecho; a sonda de repetição do dispatch ficou sem uso.

## 9. Opções que trocam qualidade

**MSAA 4x → 2x** (`msaa_4x_as_2x`, novo, categoria GPU, experimental,
`debug.xendroid.msaa_4x_as_2x`): o `RB_SURFACE_INFO` das cenas 4x (superfícies
desenhadas com cor) é reescrito para 2x só durante cada draw e resolve — render
targets, pipelines e resolves veem 2x; os resolves de média usam 2 amostras.

| | 4x | 2x |
|---|---:|---:|
| 1ª versão (S10b, b36, sem `depth_4x_as_1x`) | 37,7 ms | 30,5 ms |
| versão final (S11, b39, com `depth_4x_as_1x`) | 31,2 ms | **26,5 ms (−15%)** |

A imagem quebra. O Forza reinterpreta superfícies 4x de outros jeitos:
relê o depth 4x (0t, pitch 16t) como 1x de largura dobrada — halos de luz
corrompidos na 1ª versão; a versão final mantém em 4x as superfícies também
usadas como 1x e tudo o que é desenhado com elas — e relê a **cor da cena 4x
(0t, 32 tiles) como uma superfície 4x de metade do pitch (0t, 16 tiles)**, o
que com a cena em 2x vira uma transferência 2x → 4x de layout errado: um
contorno brilhante em volta do carro (captura `b39-msaa2x/fh_ab_2_1.png`).
Mantê-la correta exigiria que a própria cor da cena ficasse em 4x (o ganho
some) ou transferências que tratem a superfície 2x como uma 4x lógica. A
opção fica desligada, documentada como experimental e inadequada para o Forza.

**A2C → teste alfa** (S9, 4 lançamentos alternados, build 30 — que rodava com
A2C emulado no shader): 30,00 fps / GPU 28,8 ms com A2C contra 29,75 fps / 28,9
ms com teste alfa; imagem praticamente igual parado. Sem ganho — e o A2C pelo
hardware (quirk do PR #8) já é 0,9 ms mais barato que o emulado. Opção removida.

## 10. Energia por frame

Sem medidor utilizável no POCO F7: o `Charge counter` com o celular no USB
varia de 0 a 3100 mA entre braços idênticos de 36 s (carregador e atualização
em rajadas do gauge), não há HAL `IPowerStats` (o `dumpsys powerstats` vem
vazio) e o shell não lê a corrente do sysfs. O `fh_auto.sh` continua
registrando a carga por braço, marcada "on external power: not the load"
quando ligado. Como proxy de energia ficam o **tempo de GPU por frame no teto
de 30 fps** e as **amostras de CPU por thread** (simpleperf).

## 11. Validação final (S13, build 41)

Um lançamento por braço, alternados: os padrões novos (`vulkan_depth_4x_as_1x`
pelo quirk, `vulkan_resolve_draw_barriers_at_resolve` ligado) contra os dois
desligados pelo config por jogo, 40 s parado e 20 s de direção com uma captura
a cada 5 s (`b41-final/`).

| lançamento | fps | GPU/frame | passes/frame |
|---|---:|---:|---:|
| 1 novo | 28,06 | 35,3 ms | 130 |
| 2 antigo | 26,97 | 36,6 ms | 210 |
| 3 novo | 27,74 | 35,6 ms | 129 |
| 4 antigo | 27,64 | 35,7 ms | 214 |
| **média novo / antigo** | **27,90 / 27,30** | **35,4 / 36,2 ms** | **129 / 212** |

O celular esquentou durante a sessão (bateria 27,9 → 37,9 °C, SoC ~70–78 °C) e
todos os braços ficaram limitados pela GPU abaixo do teto, então a diferença
por par oscila com o calor (−1,3 ms no primeiro, −0,1 ms no segundo); a
referência continua sendo os A/B dentro do mesmo lançamento (−1,1 ms e −0,3
ms). As transferências caíram de 16,4 para 12,5 mil tiles por frame (as do
atlas, 720t 13t 1x ↔ 4x, sumiram) e as capturas da direção não mostram
diferença: sombras das árvores e do carro iguais.

O build 42 é o código dos commits (desde o 41 só mudaram quebras de linha e
um texto de ajuda). Na S14, com o celular a 35,9 °C, ele rodou 4 braços de
`resolve_draw_barriers` no teto de 30 fps (seção 6) e 15 s de perfil (seção
1) sem problemas (`b42-final/`).
