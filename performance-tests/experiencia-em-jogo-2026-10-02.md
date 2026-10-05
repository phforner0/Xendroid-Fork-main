# Experiência em jogo — medições de 2026-10-02 (nuvem, sem aparelho)

Medições de **Vulkan por software** (llvmpipe, Mesa/LLVM 20.1.2, container Linux da sessão
na nuvem, 4 vCPU). Nada aqui mede um telefone, o Adreno, scanout, latência ou um jogo:
são guardas de regressão e uma caracterização do motor Win-FG com conteúdo sintético.
Números de outros projetos não entram.

## Win-FG com movimento sintético (F09)

Ferramenta: `TEST_MOTION` em `emulator-core/src/test/cpp/winfg_software_test.cc`
(`motion-test <preset> <shift px> <model> <pares> <padrão> <tamanho>`). O conteúdo anda
`shift` px para a direita por quadro-fonte; o motor recebe vários pares seguidos (o fluxo
tem preditor temporal) e gera o quadro do meio (`alpha` 0,5, como o presenter). O último
quadro gerado é comparado, dentro de uma margem de 8 px, com o quadro ideal (conteúdo meio
passo adiante), com o quadro atual e com o anterior: erro absoluto médio em níveis 0–255.
Menor que os dois quadros-fonte = o quadro gerado estima o meio melhor do que repetir um
deles.

**Padrão 2 — textura em várias escalas (ruído de 48 a 6 px), 256×256, 4 pares, modelo 3**
(o do presenter):

| Preset | shift | vs ideal | vs atual | vs anterior |
|---|---|---|---|---|
| 2 (Performance) | 2 px | 0,43 | 1,24 | 1,24 |
| 2 | 4 px | 1,84 | 2,53 | 2,83 |
| 2 | 8 px | 4,20 | 4,87 | 4,93 |
| 2 | 16 px | 7,38 | 8,13 | 7,98 |
| 2 | 24 px | 9,51 | 9,39 | 9,69 |
| 0 (Quality) | 2 px | 0,68 | 1,29 | 1,32 |
| 0 | 8 px | 4,16 | 4,87 | 4,88 |
| 0 | 16 px | 7,41 | 8,09 | 8,14 |
| 0 | 24 px | 9,41 | 9,36 | 9,70 |
| 1 (Balanced) | 8 px | 4,12 | 4,82 | 4,87 |

Leitura: até 16 px por quadro (≈6% da largura) o quadro gerado é melhor estimativa do meio
do que repetir um quadro-fonte; a vantagem encolhe com o deslocamento e some em 24 px
(≈9% da largura). Os presets quase não diferem aqui.

**Padrão 1 — só detalhe fino (ruído de 6 px):** 2 px/quadro é bem interpolado (256 px,
preset 2: 1,92 contra 6,62/7,14), mas 8 e 16 px ficam **piores** que repetir o quadro atual
(23,78 contra 16,39; 30,35 contra 19,34): sem conteúdo de baixa frequência a pirâmide de
fluxo não acha movimentos grandes. Em 64×64 o comportamento é o mesmo.

**Padrão 0 — quadrado claro de 8×8 sem textura, 64×64:** o quadro gerado sai igual ao atual
para 2 px (centroide no lugar do atual) e um quadrado menor e deslocado para 4–8 px, em todos
os presets e nos modelos 3 e 4, com 1 ou 6 pares; nunca há mistura (nenhum fantasma a meio
brilho). Objetos lisos e pequenos não são interpolados por este motor nesse tamanho.

Guardas que entraram em `tools/test-presentation-host.sh` (padrão 2): imagem parada sai
igual (erro ≤ 1) e, para 2 px e 8 px (presets 2 e 0), o gerado fica mais perto do ideal do
que os dois quadros-fonte. Resultado nesta sessão: todas passaram.

**O que isto não prova:** qualidade em jogo, custo em GPU, cadência ou latência no telefone;
oclusões, HUD, cortes de cena e movimentos não uniformes. O A/B no aparelho continua sendo
o gate (roteiro da auditoria, item 8, e plano mestre §9.1).
