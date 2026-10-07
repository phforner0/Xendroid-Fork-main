---
title: Ajustes e efeitos
description: Como funcionam os ajustes globais e por jogo, os níveis, quando cada valor vale, as predefinições, os efeitos de imagem e a geração de quadros.
section: usar
order: 3
conferido: e179885e7
fontes: [app/src/main/java/xendroid/compose/settings/SettingsSchema.kt, app/src/main/java/xendroid/compose/settings/SettingCatalog.kt, app/src/main/java/xendroid/compose/ui/settings/XdSettings.kt, app/src/main/res/values-pt-rBR/strings_settings.xml, emulator-core/src/main/assets/config/default_config.toml, app/src/main/java/xendroid/compose/EmulatorHostActivity.kt, app/src/main/java/xendroid/compose/core/FrameGenerationTarget.kt, app/src/main/java/xendroid/compose/core/LsfgAssets.kt]
---

O app tem **{{contagens.ajustes}} ajustes** do emulador, em oito grupos. Todos estão listados, com padrão, opções e chave do TOML, na [referência de ajustes](doc:referencia-de-ajustes). Esta página explica como eles funcionam.

{{> ajustes-resumo}}

## Globais e por jogo

- **Configurações** muda os ajustes **globais**, que valem para todos os jogos. Cada linha diz se o valor é o padrão ou se mudou e quantos jogos usam outro valor.
- A **ficha do jogo** muda os ajustes **daquele jogo**. Um valor do jogo vence o global; o que o jogo não muda segue o global. Cada linha diz de onde vem o valor (“Este jogo” ou “Global”) e tem **Voltar ao global**.

Os ajustes por jogo ficam no arquivo `config/<TITLE ID>.config.toml` da [pasta de dados](doc:jogos-e-arquivos#arquivos-de-configuracao), só com as chaves que mudam. O núcleo também aplica [correções automáticas](doc:compatibilidade#correcoes-automaticas) a alguns jogos, com a mesma prioridade de um ajuste do jogo; um valor posto por você no arquivo do jogo vence a correção.

## Níveis: Essencial, Avançado e Tudo

**Configurações → App → Interface → Ajustes mostrados** escolhe quantos ajustes aparecem:

- **Essencial** ({{contagens.essencial}}): o que muda a imagem e o desempenho;
- **Avançado** ({{contagens.avancado}}): também compatibilidade, entrada e sistema;
- **Tudo** ({{contagens.ajustes}}): todos os ajustes que o núcleo lê, depuração inclusive.

A busca de cada aba procura pelo nome, pela descrição e pela chave do TOML (por exemplo `GPU.framerate_limit`), mostra até cinco resultados de outras abas e diz quantos resultados o nível escondeu.

## Quando um valor vale

Quase todos os ajustes valem **na próxima abertura do jogo**: o núcleo lê a configuração quando o jogo começa. Alguns (limite de FPS, escala e nitidez, controles na tela e volume) também mudam **ao vivo** pelo [menu em jogo](doc:interface#menu-em-jogo). Os que dependem de outro dizem o porquê quando não valem (por exemplo, “Só vale com ‘Forçar clocks máximos da GPU’ ligado”).

## Predefinições

Na ficha do jogo, **Predefinições** aplica vários ajustes de uma vez, com Desfazer:

{{> predefinicoes}}

## Imagem

### Escala de resolução

**Escala de resolução** desenha o jogo em 1×, 2× ou 3× a resolução nativa, em largura e altura. Só passos inteiros. Cada passo custa tempo de GPU e memória: num jogo que já não chega ao limite de FPS em 1×, aumentar a escala derruba mais o FPS.

O **Modo de vídeo informado ao jogo** não é um ampliador: só diz ao jogo qual é a TV. A maioria dos jogos desenha num tamanho fixo e ignora isso.

### Escala e nitidez da saída

O efeito aplicado na imagem final, ao ampliar para a tela do telefone:

| Efeito | O que faz |
|---|---|
| Bilinear | ampliação simples, o padrão |
| AMD CAS | nitidez adaptativa; “CAS: nitidez extra” aumenta |
| FSR 1 | ampliação com nitidez da AMD; “FSR: redução de nitidez” suaviza |
| SGSR | Snapdragon Game Super Resolution, da Qualcomm; experimental |
| Lanczos | ampliação nítida, sem halos |
| CRT | visual de TV de tubo, com linhas de varredura |

**Antisserrilhado** (FXAA ou FXAA extremo) suaviza as bordas e combina com CAS ou FSR. **Reduzir faixas de cor** acrescenta um ruído fino para os degradês não mostrarem degraus em telas de 8 bits. Esses quatro também mudam pelo menu em jogo, na hora.

### Proporção

**Tela larga** informa aos jogos uma tela 16:9 em vez de 4:3. **Barras pretas** mantém a proporção do jogo em vez de esticar. No menu em jogo, o modo de tela escolhe entre Ajustar, Preencher, Esticar e Inteira.

## Desempenho

- **Limite de FPS**: Sem limite, 30, 45, 60, 90 ou 120. O console nunca passou de 60 FPS; acima da taxa da tela, os quadros a mais são só calor e bateria.
- **Limitar a atualização de tela do jogo (VSync)**: mantém os vblanks do jogo nos 50/60 Hz do console.
- **Compilação assíncrona de shaders**: compila shaders novos em segundo plano. Sem engasgos numa cena nova, mas os primeiros quadros podem perder um objeto.
- **Forçar clocks máximos da GPU** (com driver personalizado): mais velocidade, mais calor e bateria.

::: versao desde=515736309
O núcleo passou a pular, por padrão, os desenhos cujos shaders ainda estão compilando (“Shaders sem travadas” no menu em jogo): um breve pop-in de objetos novos em vez de uma travada.
:::

## Geração de quadros {#geracao-de-quadros}

A geração de quadros cria quadros intermediários entre os do jogo. É **experimental**, começa **desligada a cada abertura** e não é guardada por jogo. Fica no menu em jogo → **Imagem → Geração de quadros**:

- **Win-FG 2×**: dobra os quadros, com as predefinições Qualidade, Equilíbrio e Desempenho.
- **LSFG nativo**: exige o seu próprio `Lossless.dll` (do Lossless Scaling, que você precisa ter). **Importar meu Lossless.dll** lê o arquivo e gera localmente um cache de shaders na área privada do app; o DLL temporário é apagado e nada dele vai para o APK. O **Multiplicador** vai de 2× a 4×, e o **Alvo** (60, 90, 120 FPS ou a taxa da tela) escolhe o menor multiplicador que chega lá.

Ao ligar, o app limita o jogo à taxa da tela dividida pelo multiplicador e pede ao display a taxa necessária; ao desligar, volta tudo. A nota do próprio app avisa: cadência e latência no aparelho ainda não foram validadas.

::: versao desde=4a74c69c5
A geração de quadros aparece em todas as versões publicadas e nos dois modos de ajustes. Até a build 35 ela só funcionava em builds de depuração.
:::

## Ver e testar

O [simulador](ferramenta:settings) tem os mesmos {{contagens.ajustes}} ajustes, com níveis, busca e filtros, e mostra o arquivo `config/<TITLE ID>.config.toml` que cada mudança geraria num jogo.
