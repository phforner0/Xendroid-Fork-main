# Forza Horizon — POCO F7

Diagnóstico iniciado em 2026-09-27.

## Relatórios consolidados

- [AB2: especialização alfa e clears](ab2-results.md), com dados em
  [ab2-report.json](ab2-report.json).
- [AB3: formato HDR, esperas e gargalos](ab3-results.md), com dados em
  [ab3-report.json](ab3-report.json).
- [Propostas de otimização após o AB3](ab3-optimization-ideas.md).
- [AB4: custo da GPU por passe e experimentos de shader](ab4-results.md).
- [Plano de desenvolvimento e otimização](plano-proximos-passos.md).
- [Sessão na nuvem de 2026-09-28: bugs, opções novas e fila de A/B](cloud-2026-09-28-results.md).

O Git mantém os documentos Markdown e os relatórios `*-report.json` na raiz
desta pasta. Capturas por rodada, screenshots, logs, APKs, caches de shaders e
backups de configurações ficam apenas no ambiente local e são ignorados. Os
scripts reutilizáveis de coleta e análise ficam em `tools/`.

## Contexto do diagnóstico inicial

- Código local no início: cópia extraída em `Desktop/Xendroid-Fork-main`, antes
  da criação do repositório Git.
- Aparelho confirmado por ADB: POCO F7 / 25053PC47G, SM8735, Android 16/API 36.
- RAM reportada pelo kernel: 11.502.936 KiB (aparelho de 12 GB).
- Pacote em análise: `xendroid.compose.fork`.
- Dados do emulador: `/sdcard/Android/data/xendroid.compose.fork/files/compose/`.

Esta pasta guarda os backups de configuração e os resultados dos testes.
## Jogo e perfil encontrados

- Forza Horizon 1, Title ID `4D5309C9`, executável `D48ABF1704CE5C4A`.
- GPU Adreno 825. Driver original selecionado: Turnip Gen8 V36,
  Mesa 26.3.0-devel (`c501e1d16e`).
- O usuário já tinha os patches de remoção de sombras, motion blur e profundidade
  de campo ativados; o log confirmou a aplicação e o hash correto.
- Perfil original: resolução 1x, `sysmem`, submissão a cada 1300 draws,
  leitura UMA, cache de shaders, compilação assíncrona com 4 threads.
- Não existia configuração individual `config/4D5309C9.config.toml`.

## Método

`tools/benchmark_forza.py` captura timestamps da SurfaceView do jogo via
SurfaceFlinger, screenshots, log nativo e estado térmico antes/depois. A média
é calculada pelo número de intervalos dividido pelo tempo de apresentação.
Esses números representam frames apresentados; o HUD não é usado para calcular
a média. Cada rodada normal dura 60 segundos. Não se deve interagir com o
celular durante a coleta.

Cena de referência: Viper amarelo no início da campanha, parado junto à defensa,
a aproximadamente 2,5 milhas do destino. Os shaders foram aquecidos em sessões
anteriores. A instrumentação GPU detalhada foi desligada nas comparações, pois
introduz custo significativo. O APK instalado não emitiu as linhas `GpuFrame`
previstas no código local, mas emitiu `VkFrameSync` na rodada de diagnóstico.

## Resultados parciais

| Rodada | FPS apresentados | p95 do intervalo | Observação |
|---|---:|---:|---|
| baseline-instrumented | 7,26 | 149,9 ms | Diagnóstico, não comparável às rodadas normais |
| baseline-sysmem | 12,23 | 99,9 ms | Exploratória: carro mudou de posição |
| gmem | 7,55 | 149,9 ms | Rejeitado; override confirmado no log |
| inpass | 13,05 | 99,9 ms | Visual correto na cena; diferença pequena |
| baseline-sysmem-repeat | 12,76 | 99,9 ms | Referência com carro parado |
| balanced | 12,54 | 99,9 ms | Sem ganho claro; alteração de posição no início |

`inpass` altera apenas `vulkan_in_pass_resolve=true` sobre o perfil original.
`balanced` testa buffers uniformes dinâmicos, remoção do split de 1300 draws,
clocks não forçados e redução de logging. A diferença de `inpass` para a repetição
da referência é apenas 2,2%; não prova um ganho significativo.

As temperaturas variaram entre rodadas. A bateria chegou a 47,3 °C no final de
`balanced`; o serviço térmico reportou skin status 3 e restrição de GPU nível 8.
O emulador foi encerrado para resfriamento antes do teste do driver de sistema.
As proteções térmicas do aparelho foram mantidas.

Essas medições pertencem à etapa inicial de ajustes de configuração. As
conclusões dos testes posteriores de mudanças no código estão nos relatórios
AB2 e AB3 vinculados acima.
