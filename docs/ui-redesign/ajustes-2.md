# Ajustes 2: análise antes de implementar

Segundo retorno do aparelho (Xiaomi, Android 16, Adreno 825), com 12 capturas de referência (o
menu em jogo e o HUD de um front-end de PC no Android) e as telas do próprio XenDroid. Pedido:
analisar, melhorar e iterar as ideias antes de aplicar. Este documento registra o que foi achado
no código, o que mudou entre a primeira ideia e a versão aplicada, e a ordem de trabalho.

## 1. Bugs, com a causa achada

### Primeira abertura em modo console, sem controle conectado

- **Causa.** No modo Automático, o app entra em modo console quando `Gamepads.anyConnected()` acha
  um controle. A conferência aceitava qualquer dispositivo não virtual que se declarasse gamepad ou
  joystick, e telefones declaram partes de si assim (leitor de digital `uinput-goodix`/`uinput-fpc`,
  ajudantes do Game Turbo da Xiaomi, teclas laterais). Um deles basta para o app abrir em console.
- **A mesma conferência frouxa** decidia se os controles de toque começam escondidos (uma vez por
  instalação), dava o jogador 1 a esse dispositivo fantasma no jogo (um controle de verdade virava
  jogador 2) e escondia os controles de toque quando ele "apertava" algo.
- **Correção (aplicada).** Uma conferência só, estrita, em `XdInput.kt`: um dispositivo externo
  (USB ou Bluetooth) conta se tiver A e B ou um analógico; um embutido só conta se for um controle
  inteiro (A, B, X, Y, Start e analógico: o de um portátil como os da Retroid ou AYN). Usada pelo
  modo automático, pelos controles de toque, pelos jogadores e pelo esconder automático. Quem já
  tinha os controles de toque escondidos por engano os vê de novo uma vez, se nenhum controle
  estiver conectado.

### Tocar em "Jogos" volta para a configuração inicial

Duas causas, as duas no estado da biblioteca:

1. **O estado do assistente era declarado depois de um `return` antecipado.** Quando o navegador de
   pastas abre (Escolher pasta / Adicionar pasta, dentro do assistente), ele substitui a biblioteca,
   e o estado do assistente saía da composição. Ao voltar, o assistente recomeçava aberto, no passo
   1. Como o navegador mostrava o trilho das áreas, dava para ir a Ajustes no meio da configuração e
   o "Jogos" reabria o assistente do começo, sempre.
2. **Um contador que nunca zerava.** "Assistente de configuração" em Sobre somava 1 a um contador; a
   biblioteca reabria o assistente sempre que o contador era maior que zero, ou seja, em toda volta
   à biblioteca depois de usar Sobre uma vez. O mesmo padrão limpava a busca e o filtro em toda
   volta à biblioteca depois de tocar em qualquer área do trilho.

**Correção (aplicada).** O estado da biblioteca (assistente, pastas, jogos ausentes, navegador) vem
antes de qualquer `return`; os pedidos de Sobre e do trilho viraram pedidos de uma vez só, que a
biblioteca consome; o navegador de pastas é um passo modal, sem o trilho das áreas. Teste de
regressão: `FirstRunReturnTest` (o assistente volta no mesmo passo depois do navegador; o pedido
de Sobre abre uma vez só).

### Sobre: o "Relato do núcleo" transbordando

- **Causa.** O valor da linha era o texto inteiro do núcleo: uma linha por recurso da CPU (dezenas)
  e uma por extensão Vulkan (centenas), alinhado à direita numa tabela de chave e valor. Os erros do
  núcleo vinham em chinês e a leitura acontecia na thread principal, criando uma instância Vulkan.
- **Correção (aplicada).** O relato é lido fora da thread principal e resumido (`CoreReport`):
  CPU ("Cortex-X4 ×1 + Cortex-A720 ×5 + Cortex-A520 ×2 · armv9.2-a"), recursos da CPU (quantos e os
  que importam para emulação: sve2, i8mm, bf16, atomics…) e Vulkan ("1.3.284 · 212 extensões"). O
  relato completo abre numa folha, em listas que quebram como palavras, com Copiar. "Copiar tudo"
  leva o resumo e o relato completo. A versão deixou de aparecer como "v1" e cortada no meio.

### Ajustes do menu em jogo perdidos ao sair

Não é falha, é como foi feito: o menu em jogo mudava só a sessão (FPS, tela, efeito de escala,
controles de toque…), e só o FPS e a imagem tinham "guardar para o jogo" à parte. Por isso tudo
some ao sair. A mudança está na seção 2.

### Ajustes que não fazem efeito ou são sobrescritos

Cruzei cada ajuste do app com as cvars que o núcleo define (script de conferência):

- **Dois ajustes não existem no núcleo:** `APU|mute` (Mudo, no nível Essencial) e
  `GPU|readback_memexport`. Gravá-los não muda nada. Saem da lista; o Volume (que existe e funciona,
  0 = mudo) toma o lugar do Mudo no Essencial.
- **O valor do jogo vence o global sem aviso.** Mudar um ajuste global não muda um jogo que tem o
  próprio valor; o menu em jogo não dizia de onde vinha cada valor. O menu passa a mostrar "deste
  jogo" ao lado do que é próprio do jogo.
- **O menu em jogo valia só para a sessão** (acima).
- **"Nunca jogado" ao lado de números** na aba Desempenho da ficha: o histórico de jogo vinha de um
  mapa preenchido só na varredura da biblioteca, que não roda com a ficha aberta. A ficha passa a
  ler as sessões do jogo ao abrir.
- Conferido e sem problema: a configuração do jogo é aplicada antes de qualquer leitura de cvar no
  início da sessão; as opções de "Iniciar com…" valem só para aquela abertura, por desenho.

## 2. As ideias, melhoradas

### Menu em jogo: separação e organização

- **Primeira ideia:** reagrupar a lista atual em seções. **Problema:** continuam mais de 30 linhas,
  seis só para o limite de FPS e quatro para o modo de tela, e cada linha não mostra o valor atual.
- **Versão aplicada:** trilho de categorias à esquerda (**Imagem, Desempenho, HUD, Controles,
  Sessão**) e o conteúdo em grupos. Cada linha mostra o valor e muda ali mesmo:
  - escolhas curtas em pílulas numa linha só (FPS: Sem limite · 30 · 45 · 60 · 90 · 120; Tela:
    Ajustar · Preencher · Esticar · Inteira);
  - liga/desliga em interruptor;
  - listas longas com ‹ valor › (efeito de escala, suavização, nitidez);
  - volume, tamanho e opacidade em controle deslizante.
- **No controle:** ↑↓ entre as linhas, ←→ muda o valor da linha, A aciona, LB/RB trocam de
  categoria, B fecha. As dicas mostram isso.
- **HUD ganha categoria própria:** antes eram nove linhas de métricas espalhadas em Desempenho.

### Menu em jogo: guardar por jogo

- **Primeira ideia:** gravar tudo automaticamente. **Problema:** quem está só experimentando
  "estraga" o jogo sem perceber, e mistura o que é do jogo com o que é do app.
- **Versão aplicada:**
  - O que é configuração do jogo (FPS, tela, imagem, filtro de cor, controles de toque ligados,
    sticks adaptativos, giroscópio) fica guardado **para este jogo** sozinho.
  - Um rodapé diz "Guardado para Halo 3", com **Desfazer as mudanças desta sessão** e **Usar em
    todos os jogos**.
  - Nas Configurações do jogo tudo aparece como valor próprio, que volta ao global com um toque.
  - O que é do app (o HUD, o estilo dos controles) vale para todos os jogos.

### Pausar ao abrir o menu

Interruptor **Pausar o jogo ao abrir o menu** na categoria Sessão, ligado por padrão, valendo para
todos os jogos. Hoje dependia de como o menu era aberto: Voltar pausava, o botão ☰ não.

### Sem o botão ☰

- **Primeira ideia:** só tirar o ícone. **Problema:** com navegação de três botões e o jogo em tela
  cheia, abrir o menu exigiria dois toques (mostrar as barras e depois Voltar).
- **Versão aplicada:** o ícone sai, mas o menu continua abrindo:
  - pelo Voltar (gesto da borda ou botão);
  - pelo botão Guia do controle;
  - arrastando a partir da borda esquerda (uma faixa invisível).
- Na primeira sessão, um aviso de uma linha diz como abrir.

### HUD horizontal

- **Formato Horizontal:** uma barra no topo ou na base, na largura da tela, com rótulos coloridos
  por métrica, os valores e um gráfico pequeno do FPS dos últimos segundos.
- **Formato Vertical:** a caixa atual, que se arrasta e se redimensiona com os dedos.
- **Categoria HUD do menu:** formato, posição (topo ou base), detalhe (só FPS, métricas ou painel),
  métricas em caixas de seleção, tamanho, opacidade do fundo, intensidade das cores, estilo (caixa,
  contorno ou texto) e o gráfico de FPS.

### Visual dos controles na tela

- **Moderno (padrão):**
  - botões de vidro escuro translúcido com contorno fino;
  - letras A/B/X/Y coloridas;
  - direcional em quatro setas;
  - gatilhos e bumpers em pílulas;
  - analógicos com anel e alavanca discretos.
- **Clássico:** o atual, de botões coloridos. A escolha fica em Controles e no menu em jogo, e se
  aplica na hora.

### Configurações: contagens, busca e links

- **Contagens:** "Essencial 24 · Avançado 61 · Tudo 143" mostra quantos ajustes cada nível exibe na
  aba aberta, ou no total no Resumo.
- **Busca no Resumo:** procura em todas as abas, com os resultados agrupados por aba.
- **Busca dentro de uma aba:**
  - mostra só os resultados da aba;
  - embaixo, "Em outras abas (N)" com até cinco sugestões, cada uma com o nome da aba;
  - tocar numa sugestão leva ao ajuste, e Voltar retorna;
  - quando o nível escolhido esconde resultados, diz quantos e oferece "Mostrar".
- **Links** como "Todos os ajustes" e "Ver os patches":
  - o trilho rola até a aba escolhida e ela aparece selecionada;
  - no topo do conteúdo aparece "‹ Voltar para Visão geral";
  - o Voltar do sistema (e o B do controle) volta para a aba anterior, num histórico curto.
- "Todos os ajustes" na ficha leva a uma visão com todos os grupos; hoje leva só a Imagem.

### Ficha do jogo: desempenho por sessão

- **Seletor no topo** da aba Desempenho, por exemplo "Hoje 21:40 · 42 min · 30 FPS ▾", com a lista
  das sessões: data, duração, como terminou, FPS mediano e baixo, p99 e driver.
- **Sessão inicial:** a última que tem números. Hoje, se a última sessão morreu cedo, a aba dizia
  "sem números" mesmo havendo sessões anteriores medidas.
- **O cartão** mostra os números, a linha do tempo e os ajustes em vigor naquela sessão. Esses
  ajustes já eram gravados mas nunca apareciam na ficha.

### Patches e conteúdo por TU

- **Primeira ideia:** agrupar pelo rótulo "(TUn)" do nome do arquivo. **Problema:** só 136 dos 1916
  arquivos de patch o têm.
- **Versão aplicada:**
  - **Agrupar pelos hashes do executável:** cada arquivo de patch lista os hashes para que vale,
    muitos com o comentário "# TU 1" ao lado. A última sessão já guarda o hash do executável que
    rodou.
  - **Grupos de patches:** "Para a sua versão", "Para outras versões" (com o rótulo da TU quando o
    arquivo diz) e "Sem versão indicada".
  - **Versões do jogo:** a TU instalada passa a mostrar sua versão, lida do cabeçalho do pacote.
  - **DLC** fica separada das TUs.

### Pastas de TU e DLC, com busca profunda, e criar pastas

- **Pastas de conteúdo (TU e DLC)**, em Conteúdo → Instalar:
  - recursivas como as de jogos;
  - mostram quantos pacotes cada pasta tem;
  - os pacotes achados aparecem para instalar, com os já instalados marcados.
  - Hoje só os Downloads são lidos, e só no primeiro nível.
- **Criar pastas:**
  - o navegador de pastas ganha **Nova pasta**;
  - Pastas de jogos ganha **Criar pastas padrão** (XenDroid/Jogos, XenDroid/TU, XenDroid/DLC), que
    cria e adiciona as três de uma vez.

### Sugestões ao fim da sessão

- **Primeira ideia:** um resumo com sugestões depois de toda sessão. **Problema:** cansa, que é
  justamente o que o pedido quer evitar.
- **Versão aplicada:**
  - **Quando aparece:** só quando há algo acionável. Pode ser um erro ou travamento, ou números que
    pedem um ajuste: engasgos, FPS abaixo do limite, áudio atrasado ou calor. A sessão precisa ter
    durado mais de um minuto, a menos que tenha falhado ao abrir.
  - **Folha "Como foi Halo 3":** resumo (tempo, FPS, como terminou) e até três sugestões. Cada uma
    traz o motivo ("p99 de 81 ms com 287 pipelines criados") e **Aplicar neste jogo**, com Desfazer.
  - **Silenciar sugestões:**
    - em cada sugestão: "Não sugerir isto para este jogo" e "Não sugerir isto nunca";
    - na folha: "Não mostrar até reabrir o app", "Não mostrar para este jogo" e "Nunca mostrar";
    - em Configurações → Interface → "Sugestões ao fim da sessão": Sempre, Só depois de erros ou
      Nunca.
  - **Limites:** as de desempenho aparecem no máximo uma vez por dia por jogo; as de erro, sempre.
- **Regras iniciais**, só com dados que o app já grava:

| Sinal | Sugestão |
|---|---|
| Travamento nativo com driver personalizado | Testar o driver do sistema neste jogo |
| Falha ao abrir, com ajustes próprios no jogo | Voltar os ajustes deste jogo ao global |
| Engasgos com muitos pipelines criados | Mais threads de criação de pipelines; o cache melhora nas sessões seguintes |
| FPS mediano bem abaixo do limite, com escala de resolução acima de 1× | Escala 1× |
| FPS oscilando entre 30 e 60 com limite 60 | Limite em 30, para um ritmo regular |
| Áudio atrasado acima de 1% dos blocos | Buffer de áudio maior |
| Calor no limite ou bateria acima de 45 °C | Limite de FPS menor |
| Geração de quadros com passes caros | Desligar a geração de quadros |

### Navegação por controle no app inteiro

Peças comuns no sistema de design, para valer em todas as telas:

- foco visível em tudo que é clicável;
- foco inicial ao abrir cada tela;
- o foco volta ao item de onde se saiu;
- listas rolam até o item focado;
- LB/RB trocam de abas e seções;
- LT/RT rolam uma página;
- B volta e fecha folhas, sempre;
- os atalhos X/Y aparecem na barra de dicas.

Depois disso, uma passada tela a tela.

## 3. Ordem

1. Bugs da seção 1 e um APK de teste (o assistente e o modo console impediam o uso normal).
2. Menu em jogo: organização, guardar por jogo, pausa, sem ☰, HUD horizontal e visual dos
   controles.
3. Configurações: contagens, busca com sugestões, links com Voltar, ajustes inexistentes fora.
4. Ficha do jogo (sessões, TU) e pastas de conteúdo.
5. Sugestões ao fim da sessão.
6. Navegação por controle, passada final em todas as telas.
