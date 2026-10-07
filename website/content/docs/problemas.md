---
title: Problemas e logs
description: O que fazer quando um jogo não abre, fica lento ou desenha errado, onde ficam os logs, como compartilhar um diagnóstico sem dados pessoais e o que procurar no xe.log.
section: referencia
order: 3
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/core/SessionLogs.kt, app/src/main/java/xendroid/compose/core/SanitizedSessionExport.kt, app/src/main/java/xendroid/compose/ui/diagnostics/DiagnosticsScreen.kt, app/src/main/java/xendroid/compose/EmulatorHostActivity.kt, emulator-core/src/main/java/xendroid/compose/Utils.java, emulator-core/src/main/cpp/xenia/src/xenia/config.cc, app/src/main/res/values-pt-rBR/strings_content.xml]
---

## O jogo não abre

Quando a abertura falha, a tela **Falha ao abrir** diz o motivo numa frase, mostra as últimas linhas do log e oferece o próximo passo:

{{> print id=falha-ao-abrir legenda="Falha ao abrir: o motivo, as últimas linhas do log e o próximo passo."}}

1. **Driver personalizado**: se o jogo não abriu com um driver como o Turnip, **Tentar com o driver do sistema** abre só aquela vez com o driver do Android. Se funcionar, troque o driver do jogo em [Drivers](doc:drivers).
2. **Ajustes do jogo**: em [Iniciar com…](doc:interface#iniciar-com), **Ignorar os ajustes deste jogo** abre só com os globais. Se abrir, um ajuste próprio do jogo é o problema; **Voltar tudo ao global** na ficha desfaz.
3. **Patches**: em Iniciar com…, **Sem patches** abre sem os patches ativos.
4. **Formato**: confira se o arquivo é um dos [formatos aceitos](doc:jogos-e-arquivos#formatos-de-jogo) e se a cópia está completa.

Depois de uma sessão com falha, o app também pode sugerir uma correção (veja [sugestões ao fim da sessão](doc:desempenho#sugestoes-ao-fim-da-sessao)).

Se o app mostra “Este aparelho não tem GPU Vulkan”, nenhum jogo vai abrir nesse aparelho; veja os [requisitos](doc:requisitos#gpu-e-vulkan).

## O jogo está lento ou engasga

- Ligue o [HUD](doc:interface#hud) para ver FPS, tempo de quadro e temperaturas.
- **A primeira abertura** de um jogo é a mais lenta: os shaders estão sendo montados e as próximas sessões os reaproveitam.
- Volte a **escala de resolução** para 1×; num jogo que não chega ao limite de FPS em 1×, uma escala maior piora.
- Um **limite de 30 FPS** costuma dar um ritmo mais estável que um 60 que não se sustenta.
- Em Adreno, teste um **driver Turnip** recente.
- **Calor**: quando o telefone chega ao limite, o app avisa e o desempenho cai. Limite de FPS menor, escala 1× e não forçar os clocks máximos da GPU mantêm o aparelho mais frio.

Para saber se uma mudança ajudou de verdade, use [Comparar execuções](doc:desempenho#comparar-execucoes).

## O jogo desenha errado

- Teste o driver do sistema e um Turnip diferente.
- Com Turnip, as [flags do Turnip](doc:drivers#opcoes-do-turnip) `nolrz`, `noubwc` e `nomultipos` corrigem algumas classes de cintilação, texturas corrompidas e polígonos esticados, custando velocidade. O padrão `sysmem` evita uma classe de travamentos da GPU Adreno.
- Veja se o jogo tem [notas ou correções automáticas](doc:compatibilidade).

## Áudio com estalos

Em **Áudio** (nível Avançado), **Profundidade do buffer de áudio** maior evita estalos ao custo de latência, e o **buffer de áudio adaptativo** cresce sozinho quando há falhas. O app sugere um buffer maior quando mais de 1 % dos blocos de áudio chegam atrasados.

## Onde ficam os logs {#onde-ficam-os-logs}

| O quê | Onde |
|---|---|
| Log do emulador da execução atual | `{{app.pastaDados}}/xe.log` |
| Sessões anteriores guardadas | `{{app.pastaDados}}/logs/session_<data>.zip` |

Uma **sessão** é uma vida do processo do app. Ao abrir o app de novo, a sessão anterior vira um ZIP com o `xe.log`, o logcat do próprio app, um `context.json` com os Title IDs e a versão e, no Android 11 ou mais novo, o motivo da saída (e o rastro de um travamento nativo ou de um ANR). Ficam as últimas **{{contagens.sessoesLog}} sessões** (de 1 a 16, em **Sessões de log guardadas**, nível Tudo); de cada log entram só os últimos 64 MB.

O **Nível de log** (Erro, Aviso, Info ou Depuração) e o **Filtro de log** (por subsistema) ficam no grupo **Depuração**, no nível Tudo.

## Compartilhar um diagnóstico {#compartilhar-um-diagnostico}

**Configurações → App → Diagnóstico e testes → Diagnóstico** lista as sessões guardadas e a atual, com os jogos de cada uma, como terminou (normalmente, encerrada pelo Android, falha) e um resumo. **Compartilhar esta sessão** ou **Compartilhar todas as sessões** gera uma **cópia limpa** e abre o compartilhamento do Android; os logs no aparelho não mudam. O menu em jogo tem o mesmo, em **Sessão → Compartilhar logs de diagnóstico**.

{{> print id=diagnostico-resumo legenda="Antes de compartilhar: o que vai no arquivo e o que sai."}}

Antes de compartilhar, o app tira:

- caminhos de arquivos e pastas;
- gamertags, XUIDs e nomes de perfil;
- endereços IP e e-mails;
- senhas e tokens de acesso.

Só entram textos (`xe.log`, logcat, contexto e motivos de saída); despejos binários de falha nunca vão. A cópia fica no cache do app e é apagada depois de 24 horas.

### Exportar os logs brutos

**Exportar logs da sessão para Downloads** (em **Diagnóstico e testes** e no grupo Depuração) cria `Download/xendroid-logs-<data>.zip` com todas as sessões guardadas e a execução atual, **sem ocultar nada**. Prefira o diagnóstico acima para compartilhar em público.

### Pelo computador, com adb

Com a depuração USB ligada, dá para puxar o log do emulador direto:

```sh
adb pull /sdcard/{{app.pastaDados}}/xe.log
```

## O que procurar no xe.log

| Linha | O que diz |
|---|---|
| `Storage root: …` | a pasta de dados que o núcleo usa |
| `Extracted title_id XXXXXXXX from: …` | o Title ID do jogo aberto |
| `Game quirk for XXXXXXXX: <cvar> (<nota>)` | uma [correção automática](doc:compatibilidade#correcoes-automaticas) aplicada |
| `Loading game config: …` e `Applied N game config override(s)` | o arquivo do jogo e quantos ajustes ele trouxe |
| `Settings changed from defaults: N` | a lista de tudo que está fora do padrão, com `· this game` no que veio do jogo |
| `PatchDB: Loaded patches for N titles` e `Patcher: Applying patch for: …` | os patches carregados e os aplicados |

Um valor do arquivo de configuração com o tipo errado é descartado: o núcleo avisa que o valor “had invalid types and have been reset to defaults”.

## Relatar um problema

Inclua o aparelho (fabricante, CPU, GPU, versão do Android), a versão do app (em **Sobre**, com copiar tudo), o driver, o jogo e o Title ID, o que você fez e o que aconteceu, e o diagnóstico da sessão. O repositório tem um [modelo de issue para desempenho](repo:.github/ISSUE_TEMPLATE/-meta-issue--performance-feedback.md), e a comunidade conversa no [Discord]({{discord}}).
