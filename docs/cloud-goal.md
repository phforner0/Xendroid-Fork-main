# Instruções para sessões na nuvem (/goal)

Objetivo: avançar o desempenho e a estabilidade do XenDroid (fork do Xenia
para Android) no **Forza Horizon** (title ID `4D5309C9`) num **POCO F7**
(Snapdragon 8s Gen 4, Adreno 825, Turnip Gen8 V36 com `TU_DEBUG=sysmem`,
pacote de teste `xendroid.compose.fork.opt`). Na nuvem não há celular, adb nem
medição no aparelho: trabalhe só com o repositório. Encontre e corrija bugs,
implemente otimizações fundamentadas atrás de opções e deixe prontos os testes
A/B que o usuário roda no aparelho. Responda e documente em português;
mensagens de commit em inglês, no estilo do histórico.

## 1. Ambiente

- Primeiro comando da sessão: `bash tools/cloud-setup.sh` (idempotente; instala
  JDK 21, SDK 35, NDK 29.0.14206865, CMake 3.30.3, ferramentas SPIR-V, inicia
  os submódulos e cria `local.properties`). Confira a linha final `done:`.
- Build completo (lento na primeira vez; ccache ajuda nas seguintes):
  ```bash
  ./gradlew --no-daemon --console=plain -PxendroidReleaseIdSuffix=.fork.opt \
    -PxendroidReleaseDebuggable -PgitHash=cloud :app:assembleRelease
  ```
  `XENDROID_NINJA_JOBS` limita os jobs de compilação se faltar memória.
- Checagem rápida sem build completo (depois de um configure):
  `./gradlew :emulator-core:configureCMakeRelease[arm64-v8a]` e então
  `tools/native_syntax_check.sh --changed` ou com sufixos de arquivos
  (`gpu/command_processor.cc`).
- Se o toolchain não funcionar em ~30 min, siga com revisão linha a linha e diga
  no relatório exatamente o que foi e o que não foi compilado.

## 2. Leia antes (estado atual e medições)

- `performance-tests/plano-proximos-passos.md` (o plano, com a ordem sugerida),
  `ab4-results.md`, `ab3-results.md`, `ab3-optimization-ideas.md`,
  `ab2-results.md`, `README.md` e `review-opt-local/README.md`.
- `git log` (e `-p` dos commits recentes) para saber o que do plano já foi feito;
  continue do próximo item pendente.
- `tools/`: `forza_auto_ab.ps1` (A/B em tempo real ou `-RestartArms`),
  `fh_auto.sh` (roteiro no celular), `forza_segstats.py`, `forza_passres.py`,
  `forza_pipestats.py`, `validate_spirv_corpus.py`, `spirv_alpha_regression.cc`.

## 3. Já estabelecido (não reinvestigue sem fato novo)

- **A GPU é o gargalo**: ~96% ocupada com o celular frio, ~98% quente. Economias
  só de CPU ou da thread de comandos não viram FPS (coleta limitada de fences,
  readback `none`, backoff de `WAIT_REG_MEM`, draws por submissão ≠ 1300: todos
  neutros).
- Custo de GPU por frame (morno, com timestamps, 49,6 ms): passe principal
  1280×512 18,5 ms (~1.880 draws); 1280×2048 (sombras, 36 instâncias) 7,0 ms;
  outros passes 5,5 ms; resolves 8,4 ms (100/frame; faixas de cor + clear
  0,69 ms cada, profundidade 512×512 1,17 ms, faixas de profundidade 0,41 ms;
  mipmaps do cubemap ~20–35 µs cada); ~10 ms fora de passes e resolves.
- Pixel shaders: mediana de ~1.000 instruções ir3, quase tudo emulação do Xenos
  (gama PWL das texturas calculada sempre e escolhida com `sel`, porque o NIR
  achata o `OpSwitch` do tradutor; "0 × qualquer = 0"; arredondamento de 21 bits
  após funções transcendentes). Teto com `spirv_ps_math_experiment`: 15 = −9% de
  GPU e +7,7% de FPS (cores erradas); 11 = −6% no passe principal sem artefatos.
- Provados: especialização sem alfa (+12–14%); R11G11B10 (+3%, resolves −2,7 ms,
  validado em movimento); espera do D3D estacionada só em `sub_829F04A8`
  (Guest CPU 0 97% → 37%, sem perda de FPS).
- Mortos: LRZ; extents reais no tiling predicado; thread de replay; estacionar
  todo spin-backoff (24,5 → 1,8 FPS); GMEM (7,6 vs 12,8 FPS).

## 4. Frentes, em ordem

**A. Bugs e erros (sempre primeiro).** Revise o código novo desta linha de
trabalho e os caminhos quentes: `gpu/pm4_command_processor_implement.h`,
`gpu/command_processor.*`, `gpu/vulkan/vulkan_command_processor.*`,
`vulkan_render_target_cache.cc`, `vulkan_texture_cache.cc`,
`vulkan_shared_memory.cc`, `vulkan_pipeline_cache.cc`,
`gpu/spirv_shader_translator*.cc`, `ui/vulkan/vulkan_gpu_completion_timeline.*`,
`vulkan_presenter.cc`, `vulkan_device.*`, `cpu/backend/a64/a64_seq_memory.cc`,
`kernel/guest_scheduler.cc`, `base/guest_gpu_progress.h`. Procure: vida útil
de recursos Vulkan, barreiras faltando ou sobrando, corridas entre threads
(comandos, fibers do guest, presenter, workers de pipeline), leituras fora dos
limites em pacotes PM4 e tamanhos de resolve/queries, overflow em tamanhos,
membros não inicializados, diagnóstico que custa algo desligado, propriedades
`debug.xendroid.*` sobrescrevendo configs. Cada bug: cenário concreto de falha,
correção mínima, commit separado. Suspeitas não demonstradas vão para o
relatório. Se houver subagentes, divida por área e verifique cada achado de
forma adversarial antes de corrigir.

**B. GPU (é onde está o FPS)** — seguir a ordem do plano: sinal/gama das texturas
em ramo uniforme com `DontFlatten` (exato; teto ~−11% no passe principal);
matemática relaxada como opção de verdade; laço principal sem saltos; separar
cópia × clear nos resolves e instrumentar os ~10 ms fora de passes (padrão
`VkPassTime`/`VkResolveTime`); resolves de profundidade e clears das faixas;
motivos de fim de render pass; teto dos vertex shaders; preâmbulo por draw. A
chave de modificação do pixel shader (64 bits) está cheia: para variantes
alternáveis em tempo real, avalie ampliá-la (incrementando
`Modification::kVersion`); se não compensar, use opção lida na tradução e A/B
com reinício.

**C. CPU e calor** (não vira FPS enquanto a GPU limitar): generalizar a espera
estacionada (reconhecer o padrão da função de espera no JIT), constantes float
por diferença e plano de draw.

## 5. Método para cada mudança

- Hipótese com números: qual custo medido ela ataca e qual o teto. Leia o
  código real, faça a mudança mínima no estilo do arquivo (densidade de
  comentários, nomes, idioma).
- Otimização nova sempre atrás de opção desligada por padrão (`DEFINE_*` com
  descrição clara); quando possível, alternável em tempo real por uma
  propriedade `debug.xendroid.*` lida junto com as existentes
  (`PollDebugPropertyOverrides` / `BinTracePoll`), registrando uma linha de log
  a cada troca (é o marcador dos braços). Nada muda outros jogos por padrão;
  só correção de bug comprovada pode mudar.
- Diagnóstico tem custo zero quando desligado.
- Não declare ganho sem medição no aparelho: registre como hipótese, com a
  medição que confirma. Mudanças no tradutor SPIR-V: valide com spirv-val
  (`tools/validate_spirv_corpus.py`, `tools/spirv_alpha_regression.cc`).
- Releia cada diff procurando regressões antes de commitar.

## 6. Entregáveis

- Branch próprio, commits pequenos (um assunto por commit), PR aberto no fim,
  sem merge.
- `performance-tests/cloud-AAAA-MM-DD-results.md` em português: bugs
  (severidade, arquivo:linha, cenário, correção), mudanças (o que muda, teto
  esperado e de qual medição vem, como ligar) e fila de A/B priorizada com os
  comandos prontos, por exemplo:
  ```powershell
  .\tools\forza_auto_ab.ps1 -Launch 1 -Property debug.xendroid.<prop> `
    -Values "1 0 1 0 1 0" -Marker "<regex da linha de log>" -Name <nome>
  .\tools\forza_auto_ab.ps1 -Name <nome> -ArmSeconds 40 -Passes -RestartArms @(
    'base=', 'nova=<linha do config por jogo>', 'base=', 'nova=<...>')
  ```
  Cena: carro parado na defensa a 2,5 mi; braços de 40 s intercalados; sem
  tocar no celular; sem teste térmico longo.

## 7. Regras

- Não faça push na `main` nem reescreva histórico; não suba binários.
- Não mude padrões globais sem prova; recomendações vão para o relatório.
- Não pergunte nada no meio: decida pelo código e pelas medições e registre.

## 8. Quando parar

Concluído quando: (a) a revisão de bugs dos arquivos listados terminou, com
correções commitadas ou justificadas; (b) os próximos itens pendentes do plano
estão implementados atrás de opções, compilando (ou com a checagem de sintaxe
documentada); (c) o relatório e a fila de A/B estão no branch; (d) o PR está
aberto. Se algo travar, documente e siga para o próximo item.
