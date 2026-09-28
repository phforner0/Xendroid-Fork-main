# Avaliação do APK combinado (OpenCode + Claude Code)

Coleta de 2026-09-27, após solicitação do usuário para avaliar o APK instalado.

- Pacote: `xendroid.compose.fork.opt`.
- Versão: `opt-local`, versionCode 1, flag DEBUGGABLE.
- Instalação/atualização reportada pelo Android: 2026-09-27 21:16:19.
- Duas saídas do processo `:emu` registradas com SIGKILL (status 9), às
  21:20:43 e 21:22:39. A causa precisa ser confrontada com logcat; isso isoladamente
  não comprova crash de GPU ou do código nativo.
- O arquivo de configuração global foi modificado às 21:23 e a pasta de
  overrides às 21:26, depois do último xe.log (21:22). A configuração atual
  pode diferir da configuração efetivamente usada na execução registrada.

## Conclusão

O APK combinado funciona até gameplay pesado e apresenta resultado promissor:
aproximadamente **20,66 FPS de frames do guest** no trecho pesado analisado.
As duas otimizações estão presentes no binário. Há evidência de uso real tanto
da especialização alfa quanto do caminho de clear dentro do guest render pass.
Os registros coletados não contêm `VK_ERROR_DEVICE_LOST` ou fatal signal nativo.

Isso ainda não demonstra um ganho percentual isolado de código: a referência
anterior foi medida por SurfaceFlinger, em outra execução e condição térmica,
e a instrumentação do APK combinado foi modificada para reduzir overhead.

## Identidade do binário e opções

O SHA-256 de `libe.so` instalado coincide exatamente com o artefato compilado em
`/home/administrator/xendroid-claude/app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/libe.so`:

```text
36ceb0662718408eba9c4aa4817e583ec348d74ae72c7dfd7ae584578820faae
```

O binário contém `spirv_specialize_no_alpha`,
`vulkan_resolve_clear_in_guest_pass` e as respectivas propriedades de teste.
Na coleta, ambas as propriedades `debug.xendroid.*` estavam em `1`.

O cache de pipelines contém **166 registros com modo 7 (`kNoAlphaTests`)** e
658 com modo 0. Isso comprova seleção/caching da variante nova; a proporção de
pipelines não representa a proporção dos draws ou do tempo de GPU.

Driver efetivamente carregado nos logs: Turnip Gen8 V36, Mesa 26.3.0-devel
`c501e1d16e`, Adreno 825, `TU_DEBUG=sysmem`. Resolução 1x e os três patches
(sem sombras, motion blur e profundidade de campo) continuam aplicados.

## Qual sessão foi analisada

O `xe.log` atual termina no frame 51, logo após a inicialização. Ele não é um
benchmark representativo. A sessão longa foi recuperada do ZIP
`session_20260927-212043.zip` e está em `previous-session/xe.log`.
Ela tem 226 amostras `GpuFrame` e 226 `VkFrameSync`, com gameplay até o frame 6828.

O script `analyze.py` calcula as estatísticas reproduzíveis em `summary.json`.
A janela pesada foi escolhida pelo número de frame (relatórios 4800–6827),
excluindo introdução, partes leves e a pausa final. Não há screenshots dessa
sessão arquivada para provar equivalência visual com a cena da referência.

| Métrica | Trecho pesado | Trecho final pesado |
|---|---:|---:|
| Amostras | 96 | 21 |
| Duração aproximada | 98,37 s | 21,68 s |
| FPS calculado dos intervalos do guest | **20,66** | **19,97** |
| Faixa das médias por amostra | 14,75–27,55 FPS | 17,57–22,22 FPS |
| Maior intervalo individual registrado | 237,5 ms | 96,2 ms |
| Execução da thread de comandos | 48,02 ms/frame | 49,85 ms/frame |
| Processamento de draws nessa thread | 23,54 ms/frame | 25,07 ms/frame |
| Replay + submissão no host | 12,86 ms/frame | 14,87 ms/frame |
| Draws por frame (`GpuFrame`) | 2770 | 2720 |
| Submissões por frame | 3,64 | 3,64 |
| Tempo GPU médio por submissão | 11,95 ms | 12,30 ms |
| Aberturas de render pass por frame | 211,42 | 218,89 |
| Clears por frame | 81,25 | 81,67 |
| Clears no caminho novo | 77,54 | 77,66 |
| Fração de clears no caminho novo | **95,43%** | **95,10%** |

Os tempos de CPU são aninhados e a CPU trabalha em paralelo com a GPU; não devem
ser somados indiscriminadamente. `gpu exec avg` é por submissão, não por frame.

## Avaliação das mudanças

1. **Especialização alfa:** a variante está realmente no cache. O código conserva
   o workaround NVIDIA: não força `EarlyFragmentTests`. Também conserva os inputs
   de memexport com resolução ampliada. O ganho individual ainda requer A/B.
2. **Clears dentro do guest pass:** executa em cerca de 95% dos clears no trecho
   pesado. Isso demonstra atividade, não redução de 95% nos render passes. Ainda
   há aproximadamente 211–219 aberturas por frame; é necessário medir quais
   barreiras e trocas de estado encerram/reabrem esses passes.
3. **Instrumentação mais leve:** `log_gpu_frame_time_breakdown_passes=false`
   evita os timestamps por pass/resolve que distorciam bastante a referência.
   Consequentemente `resolve_ms=0.00` significa **não medido**, não resolve grátis.
4. **Bottleneck remanescente:** a thread de comandos ocupa aproximadamente
   48–50 ms/frame, incluindo 23–25 ms de processamento de draws e 13–15 ms de
   replay/submissão. Há trabalho relevante nos dois lados, CPU e GPU. Para 30 FPS
   o orçamento é 33,3 ms/frame; apenas aumentar o limitador não resolve isso.
5. **Cache ainda aquecendo:** aparecem compilações e criação de pipelines até o
   final do trecho pesado, por exemplo nos frames 6563–6605. Uma segunda passagem
   pela mesma cena é necessária para separar stutter de compilação do custo fixo.

## Encerramento e avisos

Os dois SIGKILLs têm stack explícita de
`android.os.Process.killProcess -> EmulatorHostActivity.onDestroy`, após o
desmonte da SurfaceView. São encerramentos pelo ciclo de vida do app, não
evidência de crash Vulkan. Referências: `logcat-current.txt:1741–1763` e
`previous-session/logcat.txt:2842–2845`.

Os avisos do watchdog de scheduler aparecem depois de `EMULATOR PAUSED`:
frame 748 (seguido de `EMULATOR RESUMED` e progresso normal) e frame 6828.
Assim, esses trechos não provam deadlock em gameplay.

`vulkan_validation=0`: a ausência de mensagens de validação não valida a
correção de sincronização. Imagem, transparências, vegetação e estabilidade
prolongada ainda precisam de inspeção durante os testes.

## Próxima comparação recomendada

O APK já suporta alternância por propriedades, sem recompilar. Comparar no
mesmo ponto, com cache aquecido e temperatura semelhante:

| Caso | `debug.xendroid.spirv_specialize_no_alpha` | `debug.xendroid.resolve_clear_in_guest_pass` |
|---|---:|---:|
| Referência do mesmo APK | 0 | 0 |
| Somente especialização alfa | 1 | 0 |
| Somente clear no pass | 0 | 1 |
| Combinado | 1 | 1 |

Após trocar a variante de shader, esperar a compilação terminar antes da coleta.
Repetir a referência no final ajuda a identificar deriva térmica.

Esta avaliação foi feita lendo os arquivos existentes, o código e o binário.
Não foi iniciada uma nova sessão de gameplay nem alterada configuração do celular
durante esta avaliação.
