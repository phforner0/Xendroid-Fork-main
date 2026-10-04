# Protótipo B + C: o app inteiro em toque (B) e controle (C)

**Estado:** protótipo em HTML de todas as telas (lotes Base a 7), já implementado no app: veja
[`../app.md`](../app.md). Escolha registrada: **direção B como padrão (toque) e C como modo
controle**, ligado sozinho quando um controle está conectado.

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
| 3 | Controles: área própria, mapeamento, editor de toque, teste, celular como controle | Prototipado |
| 4 | Perfis; saves | Prototipado |
| 5 | Conteúdo; diagnóstico; comparar execuções | Prototipado |
| 6 | Primeira abertura; pastas; jogos que saíram; sem Vulkan; atualizador; Sobre | Prototipado |
| 7 | Painéis do jogo: mensagem, teclado, troca de disco | Prototipado |

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

### Lote 3: Controles

- **Área Controles** (hoje quatro itens no menu ⋮ da biblioteca): quem joga como P1–P4; os ajustes
  gerais do toque fora do editor (opacidade, esconder sozinho, vibração ao tocar, esconder com
  controle físico, tela dividida, deslizar, câmera por toque) e os layouts salvos; cada controle
  físico com a própria vibração; vibração padrão, giroscópio e entrada sem buffer fora do jogo; os
  ajustes do core para controles (zonas mortas, botão Guia); telefones como controle; atalhos
  para as quatro ferramentas.
- **Um interruptor só para os controles de toque:** hoje há dois, “Mostrar controle na tela”
  (config do core, também por jogo) e “Ligado” nos Gerais do editor. A proposta fica com o do
  core.
- **Mapeamento de teclas** (`KeymapScreen.kt`): o controle desenhado e a lista dos 16 botões lado
  a lado, com legenda dos estados (mudada, em dois botões, sem tecla), a captura num toque
  (teclado ou controle; trocar com outro botão avisa) e trocar A/B e X/Y. Proposta: mapa próprio
  por controle.
- **Editor de toque** (`GamepadEditorScreen.kt`): tela inteira com a grade, arrastar com
  alinhamento ao soltar, painel do controle escolhido (tamanho, zona morta, mostrar/esconder),
  desfazer, layouts com a prévia do que muda e os Gerais. Salvar muda os controles do jogo no
  protótipo. No modo controle: LB/RB escolhem, o direcional move uma casa, A abre o painel.
- **Testar controles** (`ControllerTestScreen.kt`): o controle desenhado acende junto com a lista
  dos botões; analógicos com a zona morta e o que o jogo recebe; gatilhos; giroscópio; vibração
  por controle; a zona morta do core ajustável ali mesmo. Origem na barra: exemplo animado, um
  controle real pela Gamepad API ou nenhum. Segure B por um segundo para sair.
- **Celular como controle** (`PhoneControllerScreen.kt`): endereço, código de 6 dígitos (482913
  conecta no exemplo), nome, vibração, os motivos de recusa e a tela de jogo com P2, latência e
  Sair. Proposta: achar o jogo na rede local e ler o QR code.

### Lote 4: Perfis e saves

- **Perfis** (`ProfilesScreen.kt`): cartões com avatar, gamertag, idioma, região, quem é o ativo
  (P1) e quanto cada perfil guarda, com atalho para os saves dele em cada jogo; “Quem joga” com
  P1–P4 lado a lado (trocar e “Ninguém” num toque) e “Perguntar quem joga antes de cada jogo”;
  lixeira com restaurar e remover de vez; criar e editar com a regra da gamertag. No modo
  controle, avatares grandes em linha, e A abre as opções do perfil.
- **Jogar como** (`playas_*`): com a pergunta ligada, Jogar abre a escolha do perfil; quem era
  P2–P4 passa para o P1 e deixa a vaga livre.
- **Saves do jogo** (`SaveManagerScreen.kt`, que hoje lista só o XUID): gamertag e avatar com o
  XUID como detalhe, tamanho e último save, e os saves de cada perfil pelos nomes dos
  cabeçalhos; exportar os escolhidos (com ou sem o perfil), importar; a pasta de sincronização
  numa seção própria, com os backups da pasta listados; revisão antes de restaurar, progresso e
  resultado com a pasta de recuperação.

### Lote 5: Conteúdo, diagnóstico e comparar execuções

- **Conteúdo** (`ContentManagerScreen.kt`, `InstallContentScreen.kt`): a área do trilho mostra
  todo o conteúdo instalado por jogo, com o tamanho; a lixeira com a cota numa barra, restaurar,
  apagar de vez e esvaziar (e o aviso de cabeçalho em uso ao restaurar uma title update antiga);
  instalar com os pacotes encontrados em Downloads, a conferência de jogo e tipo, substituir o que
  já existe e quando passa a valer. Pela ficha, a mesma tela só com aquele jogo (DLC,
  Atualizações, Lixeira, Instalar).
- **Diagnóstico** (`DiagnosticsScreen.kt`): as sessões guardadas com como cada uma terminou
  (normal, encerrada pelo Android, falha) e o motivo; o resumo da sessão, as últimas linhas do log
  numa falha e o que vai no arquivo e o que sai antes de compartilhar.
- **Comparar execuções** (`BenchmarkScreen.kt`): execuções e resultado lado a lado; cada execução
  marcada A, – ou B; o veredito recalcula na hora com os avisos (ordem, aquecimento, execução
  curta, mais de uma mudança, valores misturados) e mostra as execuções na ordem em que rodaram,
  os dois lados e a diferença por par. “Como medir” fica num botão.

### Lote 6: Primeira abertura e app

- **Primeira abertura** (`FirstRunAssistant.kt`): as mesmas verificações e escolhas, agora em cinco
  passos com Pular e Voltar sempre à mão: o telefone (GPU Vulkan, ARM de 64 bits, Android, pasta),
  a pasta de jogos com a busca acontecendo ali (quantos jogos e as capas), idioma e região dos
  jogos, criar o perfil ali mesmo e como usar (modo controle e quantos ajustes mostrar; driver
  opcional). Exemplo com Android 10, sem pasta.
- **Pastas de jogos** (`GameFoldersDialog.kt`): tela própria com quantos jogos cada pasta tem, a
  que recebe as instalações, as indisponíveis agora, remover com Desfazer e procurar de novo.
- **Navegador de pastas** (`FolderBrowserScreen.kt`): armazenamento interno e cartão SD, caminho
  clicável, quantos jogos há em cada pasta antes de escolher; serve também para escolher o arquivo
  de um pacote.
- **Jogos que saíram** (`MissingGamesDialog.kt`): o motivo, o tempo jogado, a capa guardada e o
  caminho de antes; remover só da lista, ou apontar onde o arquivo está agora.
- **Sem Vulkan:** tela com o porquê, as verificações e copiar os dados do aparelho.
- **Atualizador:** canal, versão instalada e o estado da procura; etapas visíveis (baixar, conferir
  o SHA-256, instalar) e os erros numa frase. Estados na barra.
- **Sobre** (`AboutScreen.kt`): versão, aparelho em tabela com copiar tudo, créditos, licenças e
  atalhos para atualizações, diagnóstico e o assistente.

### Lote 7: Painéis do jogo

- **Mensagem do jogo** (`GuestMessageBoxPanel.kt`): o painel diz qual jogo está perguntando e que
  ele espera a resposta; título, texto que rola quando é longo e as opções em linhas inteiras; sem
  cancelar (B avisa que o jogo espera).
- **Teclado do jogo** (`GuestKeyboardPanel.kt`, `KeyboardGrid.kt`): o pedido do jogo, o campo com o
  limite contado como o jogo conta, a grade de letras e símbolos com as teclas de comando em
  português (hoje Shift, Space, Done e Cancel ficam em inglês) e os atalhos do Xbox 360 à vista no
  modo controle.
- **Troca de disco** (`DiscSwapPanel.kt`): o disco pedido em destaque, o de antes marcado, Cancelar
  por último com o aviso de que o jogo fica sem disco; sem disco achado, procurar o arquivo.

## O que o protótipo achou no app de hoje

- Dois interruptores para os controles de toque: “Mostrar controle na tela” (config do core,
  também por jogo) e “Ligado” nos Gerais do editor.
- Os saves de um jogo aparecem só pelo XUID, sem gamertag nem avatar.
- Giroscópio, vibração padrão, entrada sem buffer e câmera por toque só mudam pelo menu em jogo.
- As teclas de comando do teclado do jogo estão em inglês.
- O painel de pausa (`PauseMenuPanel`) e o painel lateral (`GuestSidePanel`) não são chamados por
  nenhuma tela.
- Controles, conteúdo e diagnóstico ficam espalhados no menu ⋮ da biblioteca; o protótipo os
  junta em áreas do trilho (no modo controle, no menu do Start).

## No app

Implementado em Compose, lote por lote, com prints antes/depois (Roborazzi) em
`../prints/app/<lote>/`; a validação no aparelho está pendente. O que entrou em cada lote e as
propostas daqui que ficaram de fora estão em [`../app.md`](../app.md).
