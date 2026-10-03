# Protótipo B + C: o app inteiro em toque (B) e controle (C)

**Estado:** protótipo em HTML; nada mudou no app. Escolha registrada: **direção B como padrão
(toque) e C como modo controle**, ligado sozinho quando um controle está conectado.

## Como abrir

- `index.html` nesta pasta, direto no Chrome ou Edge (sem build). Arquivos: `base.js`
  (utilidades, ícones, gráficos e arte gerada), `data.js` (dados de exemplo), `core.js`
  (estado, telas, navegação, visor), `ui.js` (peças compartilhadas) e `screens/*.js` (um arquivo
  por lote).
- Barra do topo: **tela** (agrupada por lote), **Automático / Toque / Controle**,
  **Paisagem / Retrato / Tela cheia** e **Anotações**.
- Teclado: setas, Enter, Esc, Q/E (LB/RB), F (Y), I (X), M (Start: menu), / (buscar).
  Um controle real funciona pela Gamepad API e, em Automático, troca para o modo controle.
- Prints em `../prints/bc/<lote>/`.
- Algumas telas têm exemplos (motivo da falha, nível do HUD, ritmo do carregamento): os seletores
  aparecem na barra do topo, ao lado da tela, e ficam fora do aparelho.

## Como B e C convivem

- **B (toque):** trilho com as áreas do app (no retrato, barra inferior), telas com seções à
  esquerda e conteúdo à direita, grade com painel lateral na biblioteca.
- **C (controle):** as mesmas áreas e seções, com menu vertical grande, abas e seções por LB/RB,
  ajustes em linhas ◀ valor ▶, dicas de botão sempre visíveis, cor tirada da capa e um menu global
  no Start (≡), como o guia do console.
- **Modo controle** em Configurações → App → Interface: Automático (padrão), Sempre toque ou
  Sempre controle.
- Uma família de fontes só (Barlow e Barlow Semi Condensed, mais JetBrains Mono nos IDs) para
  pesar menos no APK que as três famílias do primeiro protótipo.

## Lotes

| Lote | Telas | Estado |
|---|---|---|
| Base | Biblioteca e ficha do jogo nos dois modos; menu do modo controle | Prototipado |
| 1 | Configurações globais; Drivers | Prototipado |
| 2 | Carregamento e falha ao abrir; menu em jogo; HUD | Prototipado |
| 3 | Controles: mapeamento, editor de toque, teste, celular como controle | Próximo |
| 4 | Perfis; saves | Pendente |
| 5 | Conteúdo; diagnóstico; comparar execuções | Pendente |
| 6 | Primeira abertura; pastas; jogos que saíram; sem Vulkan; atualizador; Sobre | Pendente |
| 7 | Painéis do jogo: mensagem, teclado, troca de disco | Pendente |

O painel de pausa (`PauseMenuPanel`) e o painel lateral (`GuestSidePanel`) não aparecem no lote 7:
nenhuma tela os chama hoje; o menu em jogo os substituiu.

### Lote 1: Configurações e Drivers

- **Configurações** (`SettingsScreen.kt`): resumo do que está fora do padrão e dos jogos com
  ajustes próprios; os mesmos grupos de ajustes da ficha no escopo global, cada linha dizendo
  quantos jogos usam outro valor; opções do app reunidas (modo controle, nível
  Essencial/Avançado/Tudo, botões nos menus, tamanho da interface e do texto, idioma,
  atualizações); dados e backup com a prévia do que muda; comunidade; diagnóstico e testes.
- **Drivers** (diálogo do `DriverActionRow` em `SettingRows.kt`): escolhido e carregado na última
  sessão lado a lado; sugestão para a GPU; jogos com driver próprio; instalados com o resultado da
  conferência do SHA-256; download com progresso; fontes do GitHub; flags do Turnip (TU_DEBUG) uma a
  uma, com sysmem e gmem se excluindo.

### Lote 2: jogo aberto

- **Carregamento** (`GameLoadingScreen.kt`, `BootStatusLabel.kt`): as quatro etapas reais do boot
  visíveis de uma vez, com o tempo de cada uma e a contagem de pipelines; o aviso de primeira
  abertura lenta; selos com o driver, o limite, a escala, os patches e quantos ajustes do jogo
  valem nesta abertura. Ao terminar, abre o jogo (Jogar na ficha passa por aqui).
- **Falha ao abrir** (mensagens `host_*` do `EmulatorHostActivity`): o motivo numa frase, as
  últimas linhas do log e o próximo passo certo: driver que não iniciou oferece tentar uma vez
  com o driver do sistema; restauração de save interrompida leva aos saves; os demais tentam de
  novo num processo novo.
- **Menu em jogo** (`InGameMenu.kt`, `InGameMenuState.kt`): painel lateral (embaixo no retrato)
  com o jogo visível atrás; linha de estado com FPS, p99, temperatura, bateria e driver; as quatro
  abas de hoje, com “Sistema” renomeada para “Desempenho”; limite de FPS da sessão com salvar para
  o jogo e usar o global na mesma linha; todos os ajustes do jogo acessíveis dali (valem na próxima
  abertura); endereço e código dos telefones como controle na própria linha; LSFG com alvo e
  multiplicador. No modo controle, LB/RB trocam de aba, B fecha e Start abre e fecha.
- **HUD** (`PerformancePanelText.kt`): compacto, completo e painel, nas três aparências de hoje,
  com o aviso térmico (perto do limite e reduzindo) numa faixa que dá para fechar.
- Navegação por teclado e controle não rola mais a página do protótipo, só as listas do aparelho.
