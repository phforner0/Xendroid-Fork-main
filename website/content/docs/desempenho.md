---
title: Desempenho e medições
description: O que cada sessão grava, como ler o desempenho na ficha do jogo, comparar execuções, as sugestões ao fim da sessão e as medições publicadas pelo projeto.
section: usar
order: 6
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/sessions/Benchmark.kt, app/src/main/java/xendroid/compose/sessions/SessionAdvice.kt, app/src/main/java/xendroid/compose/ui/game/GameCards.kt, app/src/main/res/values-pt-rBR/strings_advice.xml, README.pt-BR.md]
---

## O que cada sessão grava

Toda vez que um jogo roda, o app guarda os números daquela sessão: FPS de cada segundo, tempo de quadro, quanto tempo até o primeiro quadro, pipelines criados, áudio atrasado, temperatura da bateria, o driver usado e os ajustes em vigor.

Na ficha do jogo → **Desempenho**, a sessão mostrada aparece no topo (data, duração e FPS); um toque lista todas as sessões do jogo, com como cada uma terminou, FPS mediano e baixo, o p99 do tempo de quadro e o driver. Os gráficos mostram quantos segundos o jogo passou em cada FPS e quantos quadros levaram cada tempo, com a linha do tempo da execução (engasgos, avisos de calor, pausas, controles).

{{> print id=ficha-desempenho legenda="Desempenho na ficha: a sessão mostrada e os gráficos de FPS e tempo de quadro."}}

O [HUD](doc:interface#hud) mostra os mesmos números ao vivo, durante o jogo.

## Comparar execuções

**Configurações → App → Diagnóstico e testes → Comparar execuções** compara execuções de um mesmo jogo marcadas A e B, para medir **uma mudança só** (driver, limite de FPS, geração de quadros, cap de vblank ou taxa da tela). O resultado só é dado quando a comparação é justa:

- ordem que cancela o aquecimento do aparelho: pelo menos **A B B A** (quatro execuções);
- cada execução com pelo menos 30 segundos medidos;
- temperatura inicial da bateria parecida entre as execuções (até 3 °C de diferença);
- no máximo uma diferença entre A e B, e cada lado com uma configuração só.

O veredito (“B é mais rápido em todos os pares”) só aparece quando todos os pares concordam. Quadros gerados pela geração de quadros não contam no FPS. Para alinhar o mesmo trecho do jogo, use **menu em jogo → Sessão → Marcar cena**.

{{> print id=comparar legenda="Comparar execuções: execuções A e B, o resultado por par e os avisos."}}

## Sugestões ao fim da sessão {#sugestoes-ao-fim-da-sessao}

Quando você volta ao app depois de uma sessão com algo a melhorar, a folha “Como foi” resume a sessão e traz até três sugestões, cada uma com o motivo em números e **Aplicar neste jogo** (com Desfazer):

| Sugestão | Quando aparece |
|---|---|
| Testar o driver do sistema | o jogo fechou num travamento nativo usando um driver personalizado |
| Voltar aos ajustes globais | o jogo não chegou a mostrar um quadro e tem ajustes próprios |
| Criar pipelines em mais threads | 500 ou mais pipelines criados, com quadros de 50 ms ou mais e menos de 5 threads |
| Escala de resolução 1x | FPS mediano abaixo de 80 % do limite com a escala acima de 1x |
| 30 FPS estáveis | limite de 60, mediana entre 34 e 52 e os 5 % mais lentos abaixo de 40 |
| Buffer de áudio maior | mais de 1 % dos blocos de áudio preenchidos por atraso |
| Limite de FPS menor | bateria a 45 °C ou mais, ou o telefone no limite de calor, com limite acima de 30 |

As sugestões só aparecem para sessões que falharam ou passaram de um minuto; as de desempenho, no máximo uma vez por dia por jogo. Dá para silenciar uma sugestão, um jogo ou todas, e **Configurações → App → Interface → Sugestões ao fim da sessão** escolhe entre Sempre, Só depois de erros e Nunca.

## Medições publicadas {#medicoes-publicadas}

{{> desempenho-readme}}

Essas medições são do autor do projeto, num aparelho só. Os métodos, os resultados de cada mudança e os planos de otimização ficam em [performance-tests/](repo:performance-tests/). Para medir no seu aparelho, use as sessões e o Comparar execuções acima.
