# Instruções para implementar e iterar a experiência em jogo (/goal)

**Objetivo:** executar, integrar e validar de ponta a ponta o
[plano de experiência em jogo](plano-experiencia-em-jogo.md): biblioteca e menus
navegáveis por toque/controle, configurações coerentes, HUD, diagnóstico, saves,
drivers e experimentos nativos de apresentação, incluindo Win-FG e LSFG Native.
Este é um objetivo **separado** do [cloud-goal de desempenho do Forza](cloud-goal.md);
use as medições de Forza como limite para regressões, não como promessa de FG.
Responda e documente em português. O plano é a especificação de produto e de
arquitetura; este arquivo é a sequência **operacional** para sessões sucessivas.

Em uma sessão na nuvem, normalmente não há telefone nem `adb`: implemente e
verifique o que pode ser demonstrado pelo repositório, prepare testes manuais
reprodutíveis e deixe **validação no aparelho pendente**, sem chamá-la de
concluída. Quando o usuário trouxer APK, logs, capturas ou medições, analise-os,
corrija o que falhou, repita a verificação e avance ao próximo gate. Não pare
após somente desenhar a UI ou escrever um relatório.

## 1. Início de cada sessão: estado antes da ação

1. Leia este arquivo e `docs/plano-experiencia-em-jogo.md` (especialmente as
   seções 2, 5, 6, 6.1, 6.2, 7 e 8). Leia `BUILD.md`,
   `docs/frontend-integration.md` e, para mudanças nativas/FG,
   `performance-tests/plano-proximos-passos.md` e `docs/cloud-goal.md`.
   Referências Bannerlator/DroidDeck e os commits fixos estão no final do plano;
   consulte **as implementações de referência** antes de adaptar uma feature.
2. Inspecione `git status --short`, `git log -10 --oneline` e diffs dos arquivos
   envolvidos; descubra o que já foi implementado/medido desde a última sessão.
   Trate arquivos novos ou não rastreados como trabalho a preservar. Não refaça
   uma fase porque o checklist abaixo ficou desatualizado: atualize o status
   pela evidência encontrada no código/testes.
3. Em **cloud Linux limpo**, rode `bash tools/cloud-setup.sh` a partir da raiz.
   O script configura JDK 21, SDK 35, NDK 29.0.14206865, CMake 3.30.3,
   ferramentas SPIR-V, submódulos e `local.properties`; confira a linha `done:`
   e se há itens `MISSING`/`WARN`. Ele **reescreve `local.properties`**: em
   ambiente local previamente configurado, use a instalação já existente em
   vez de sobrescrever a configuração do usuário.
4. Leia `docs/experiencia-em-jogo-status.md`, se existir; se ainda não existir,
   crie-o **ao iniciar a implementação**, registrando para cada item `pendente`,
   `implementado sem aparelho`, `validado no aparelho` ou `bloqueado`, com
   commits/diffs, testes, APK e links para resultados. Ele é um registro de
   execução, não substitui este objetivo nem o plano.
5. Escolha a próxima fatia **menor e verificável** da primeira fase pendente;
   registre hipótese, arquivos, dependências, reversão e critérios de aceite.
   Se um gate realmente impedir a fase (por exemplo, licença LSFG ou falta de
   medição), documente a evidência e avance um item **independente** sem
   habilitar uma opção fictícia.

## 2. Ambiente e comandos de verificação

O `:app` é Compose/Kotlin (`applicationId` base `xendroid.compose`) e
`:emulator-core` gera `libe.so`. A `EmulatorHostActivity` roda em processo `:emu`;
o pacote release de testes usa o sufixo `.fork.opt`. Há apenas um guest ativo por
processo. Não configure CMake nativo diretamente fora do Gradle.

```bash
# Para alterações Kotlin/testáveis:
./gradlew --no-daemon --console=plain :app:testDebugUnitTest

# APK debug e integração JNI/native:
./gradlew --no-daemon --console=plain :app:assembleDebug

# APK release de teste, quando houver mudanças de presenter/JNI ou A/B no aparelho:
./gradlew --no-daemon --console=plain -PxendroidReleaseIdSuffix=.fork.opt \
  -PxendroidReleaseDebuggable -PgitHash=cloud :app:assembleRelease

# Checagem nativa rápida após configurar a variante Release:
./gradlew ':emulator-core:configureCMakeRelease[arm64-v8a]'
tools/native_syntax_check.sh --changed
```

`tools/native_syntax_check.sh --changed` verifica fontes `.cc/.cpp` alterados
contra `HEAD`, não arquivos novos não rastreados nem apenas headers; para eles
passe os sufixos dos `.cc/.cpp` relevantes e confirme com o build completo.
Se um teste/comando não se aplicar ou falhar por toolchain, registre comando,
erro, cobertura obtida e validação faltante. Não declare o APK ou o dispositivo
testados se houve somente compilação/syntax check. Não repita toda a suíte após
cada ajuste documental; teste a fatia e rode o build apropriado antes de fechar
uma implementação.

## 3. Invariantes: preservar ao longo das fases

- O jogo continua inicializando a partir da biblioteca **e** de frontends
  externos; sair retorna à origem. Nenhuma recriação de Activity pode tentar
  inicializar o core nativo pela segunda vez no mesmo processo.
- Prompts do guest (teclado, troca de disco, message box), confirmação de saída,
  menu e jogo têm prioridade de input definida. Tecla capturada não vaza
  `down`/`up` para o guest nem deixa botão/eixo pressionado; suportar hotplug.
- Surface destruída/recriada, pausa, Home, troca de driver (próximo boot) e
  perda de foco não deixam recursos Vulkan, áudio ou sessões presos.
- Cada opção comunica `requested`, `effective`, `reason`, escopo (`sessão`,
  `jogo`, `global`) e momento de aplicação (`live`, reset da surface, próximo
  boot). Não chamar TOML editado de mudança live sem setter real. `:emu` e
  frontend não devem sobrescrever configurações um do outro; preservar chaves
  de override desconhecidas e valores já salvos.
- HUD desligado não faz trabalho periódico extra; logs compartilhados não
  expõem dados privados; saves importados não alteram DLC, jogos ou outros XUIDs.
  Frame generation começa **desligada** e desativá-la restaura o presenter base.
- Copie **ideias e fluxos** do Bannerlator/DroidDeck; ao integrar código, confira
  a licença de **cada arquivo/dependência**. O Xenia contém código BSD, Win-FG
  original é MIT, e partes do port LSFG de referência declaram GPL-3.0-or-later.

## 4. Frentes de implementação, na ordem das dependências

**Fase 0 — inventário e instrumentação mínima.** Inspecione
`EmulatorHostActivity.kt`, `GuestSidePanel.kt`, `PauseMenuPanel.kt`,
`EmulatorSession.kt`, `GameSettingsRepository.kt`, `presenter.cc` e
`vulkan_presenter.cc`. Liste cada item do menu atual com setter real e regra de
persistência. Reproduza um lançamento da biblioteca e o contrato de frontend
externo; capture baseline de tempo de quadro/HUD off quando houver aparelho.
Separe `FPS do guest`, `submissões` e `quadro realmente apresentado`: um
`vkQueuePresentKHR` bem-sucedido não prova scanout. **Gate:** matriz de opções
verificada contra o código, baseline reproduzível e nenhum comportamento
atual perdido no desenho. Sem aparelho, status da medição é `pendente`.

**Fase 1 — menu único, controle e ciclo de vida.** Criar módulos pequenos em
`app/src/main/java/xendroid/compose/ui/ingame/`; um estado por sessão para
abrir/fechar, página, foco, origem e `pausedByMenu`. Substituir **o fluxo**, não
apagar os componentes antigos antes de provar equivalência. Back abre/pausa,
gesto permite ajustes live, LB/RB troca página, D-pad/A/B navega; confirmação
de saída fica acima do menu, prompts guest acima da confirmação. Restaurar foco
por página; tratar `down/up`, repetição, dois controles e toque simultâneo.
**Gate:** navegar todas as ações sem touch e sem mandar input ao jogo; abrir,
fechar, pausar, retomar e sair preservam estado; sem `Dialog` que roube foco
do guest. Testar compacto/4:3 e leitor de tela quando houver aparelho.

**Fase 2 — configuração efetiva e HUD.** Introduzir metadados de escopo,
capability, apply e status; ligar opções live **somente** aos setters existentes
e mostrar “próximo lançamento” nas demais. Corrigir diferença entre limite de
FPS global, override por Title ID e valor somente da sessão, incluindo 0
ilimitado. Não perder chaves TOML externas ao schema em flush. Presets HUD
compacto/detalhado/custom usam coletores existentes e amostragem adequada;
quando houver FG, mostrar FPS real e apresentado separadamente. **Gate:** valor
persistido correto após fechar/reabrir, TOML desconhecido preservado, driver
efetivo distinto de driver solicitado, HUD off sem draws periódicos extras.

**Fase 3 — biblioteca, diagnóstico e suporte.** Navegação da grade com foco,
pesquisa/filtragem, ficha por jogo, favoritos e atalhos sem duplicar scanner;
manter agrupamento de discos/Title IDs. Exportação da sessão selecionada via
`FileProvider` e share sheet: sanitizar logs atuais **e ZIPs históricos
aninhados** sob cotas de tamanho/profundidade, preservar brutos localmente.
**Gate:** teclado, touch e controle chegam à mesma ficha; sessão iniciada por
frontend externo ainda termina corretamente; fixture com XUID/IP/caminho/token
não aparece no ZIP compartilhado, sem corrupção dos logs originais.

**Fase 4 — controles, dados e drivers.** Sticks adaptativos opt-in por título,
multitouch/CANCEL sem eixo preso. Backup e restauração **de saves** por XUID e
Title ID: descobrir tipos reais antes de exportar; manifesto/hashes,
staging, conflitos, rollback, proteção a ZIP inválido, não sobrescrever DLC/TU
ou outros perfis. Driver remoto: ler digest de fonte confiável se publicado,
verificar ZIP e ABI, permitir retorno ao driver anterior e distinguir importação
local de download verificado. **Gate:** importação cancelada/erro não muda
saves ou driver selecionado; restore preserva o XUID certo; controle virtual e
físico coexistem após abrir/fechar menu.

**Fase 5 — infraestrutura de apresentação e Win-FG.** Antes de criar botão:
instrumentar quadros guest, transferências, apresentações e GPU ms; demonstrar
`quadro real → intermediário diagnóstico → próximo real` **efetivamente na tela**
sem interpolação, com thread de apresentação desacoplada do evento do guest.
Tratar mailbox, FIFO/IMMEDIATE, semáforos, posse de duas imagens, surface
resize, device lost e restauração ao desligar. Depois adaptar motor Win-FG
MIT **isolado**, com glue Xenia original, em 2× por título e off por padrão;
mostrar status real e alerta `cap × multiplicador > Hz efetivo`. **Gate:** com
FG off, baseline preservado; em título com folga de GPU, aumento medido de
apresentações efetivas sem perda indevida de FPS real, latência/artefatos
inaceitáveis ou fila crescente. Consulte valores e protocolo nas seções 6 e 8
do plano. Forza no POCO F7 (~96–98% GPU) é primeiro controle **negativo**:
não vender 2× nesse título sem criar margem de GPU e medir de novo.

**Fase 5b — LSFG Native, motor alternativo.** Iniciar avaliação de licenças e
capabilities em paralelo, mas só ligar LSFG após o gate do presenter comum.
Verificar proveniência GPL do parser/chain/pacer; se impedir o port, documentar
o bloqueio e continuar o restante do objetivo. Para um port possível: aceitar
`Lossless.dll` própria via SAF, ler como dados (nunca executá-la), validar
recursos PE, traduzir shaders em background, cache versionado por hash e
variante, testar Vulkan/driver e erros claros no menu; **não distribuir DLL ou
shaders extraídos**. Primeiro 2×, depois avaliar 3×/4× conforme Hz e custo.
Realizar A/B **pareado** LSFG vs Win-FG no mesmo jogo/aparelho/cap/cena; qualidade,
cadência, GPU ms, latência e térmica definem se há motivo para antecipar LSFG.
As notas Bannerlator ainda não provam superioridade geral de LSFG em 2×;
preserve Win-FG funcional quando DLL, cache ou LSFG falharem. **Gate:** seção
6.1 e decisão 6.2 do plano, sem afirmar teste em hardware não disponível.

**Fase 6 — extensões independentes, uma por vez.** Investigar e implementar
quando viáveis: escala Fit/Fill/Stretch/Integer live sem restart do guest,
Hz/ADPF/segundo plano, TV/tela externa com áudio/input/desconexão, ajustes
de áudio via setter, gyro, filtros leves no pós-processamento e sincronização
**opcional** dos backups depois do backup local. Cada experimento precisa de
backend real, fallback, teste de ciclo de vida e A/B quando custar GPU.
Problemas fora da arquitetura Xenia (Wine/Proton/Wayland/Steam) não viram
substitutos de uma implementação Android/Vulkan.

## 5. Método de iteração e ensaios no aparelho

Para cada fatia, registre `base → mudança → verificação → resultado → próximo
passo`. Separe três estados: **compilou**, **funciona num aparelho**, **benefício
medido**. Não infira o terceiro dos dois primeiros. Unidades de risco alto
(saves, DLL, swapchain) começam com fixtures/checagem de reversão; otimizações
ou efeitos ficam off por padrão até passar A/B.

- **UI/input manual:** roteiro com abrir pelo Back e gesto, navegar LB/RB/D-pad,
  A/B, trocar abas, voltar de prompt de teclado/disco, conectar/desconectar
  controle, sair/retomar, ir Home e voltar. Registrar jogo, Android, driver,
  formato/aspecto e o que falhou. Sem dispositivo, fornecer o roteiro e marcar
  `aguardando validação`, não deixar o teste implícito.
- **Saves/drivers:** utilizar perfis e dados descartáveis nas primeiras
  validações, verificar hashes antes/depois, simular espaço insuficiente,
  cancelar no meio e confirmar rollback. DLL inválida/cache alterado não pode
  ligar LSFG nem impedir que o jogo rode com FG off.
- **Performance/FG:** mesma cena em movimento, resolução, driver, Hz, cap,
  temperatura e duração; braços intercalados (Off/On/Off/On ou Win-FG/LSFG
  pareados). Registrar FPS guest, cadência **observável** da tela, GPU ms por
  quadro gerado, FPS real depois de ativar, 1% lows, latência, potência/termal,
  artefatos. Distinguir quadro submetido ao compositor de quadro exibido;
  se o dispositivo não expuser timing confiável, usar verificação externa antes
  de dizer “mostrado”. A/B de Forza pode aproveitar `tools/forza_auto_ab.ps1`
  **apenas** para opções com property/marker já implementados; para UI e FG,
  preparar comandos/roteiros equivalentes conforme as APIs reais.
- **Revisão da decisão LSFG vs Win-FG:** LSFG só passa à frente após A/B do
  **mesmo** título em 2× demonstrar vantagem relevante sem piora injustificada
  de input, FPS real, energia ou compatibilidade, com licença/distribuição
  resolvidas. 3×/4× provam uma capacidade diferente, não melhor 2×.

Registre resultados datados em `performance-tests/experiencia-em-jogo-AAAA-MM-DD.md`
quando houver medições e resuma-os em `docs/experiencia-em-jogo-status.md`.
Proponha sempre o próximo teste executável pelo usuário no aparelho; quando
o resultado chegar, atualize hipótese, código e status. Não reutilize valores
de Bannerlator/DroidDeck como desempenho esperado no XenDroid.

## 6. Saída de cada sessão e critério de conclusão

- Deixe no status: fase/item, arquivos tocados, comportamento antes/depois,
  comandos e resultados exatos, APK/variante ou causa de build bloqueado,
  dispositivo/driver e medições **quando realmente disponíveis**, riscos
  abertos e próxima ação concreta. Não coloque dados pessoais ou DLL no repo.
- Releia o diff e as invariantes antes de considerar uma fatia pronta. Testes
  adequados à mudança devem passar; build Kotlin não substitui build nativo;
  relatório não substitui teste de dispositivo. Atualize README/changelog para
  recursos confirmados e deixe experimental rotulado como tal.
- Commits/branch/PR somente se solicitados na sessão que executa o trabalho;
  nunca faça push na `main`, force-push, ou inclua APK, ROM, saves ou DLL no git.
  Se houver PR autorizado, verifique status, diff, commits e checks do CI.
- Este objetivo **não acaba no primeiro APK**: itens de prioridade A e B do
  plano só são concluídos com implementação e validação correspondente; itens C
  precisam de protótipo e gate **ou** de bloqueio técnico/licença demonstrado,
  registrado com a alternativa/experimento restante. Sem celular, marque
  `validação no aparelho pendente`, não `validado`. Continue pelo próximo item
  independente enquanto houver trabalho realizável; retome os gates com os
  logs/medidas do usuário.
