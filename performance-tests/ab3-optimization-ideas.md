# Forza Horizon no POCO F7 — ideias de otimização após o AB3

**Data da análise:** 2026-09-28.

**Base:** leitura de [ab3-results.md](ab3-results.md),
[ab2-results.md](ab2-results.md), [ab3-report.json](ab3-report.json) e dos caminhos
correspondentes no código do emulador.

**Ambiente de referência:** POCO F7, Snapdragon SM8735, Adreno 825, Android 16,
Turnip Gen8 V36 com `TU_DEBUG=sysmem`, Forza Horizon 1. Os resultados de referência
foram obtidos principalmente na cena do Viper parado junto à defensa, a 2,5 mi do
destino.

Os caminhos de código mencionados abaixo são relativos a
`emulator-core/src/main/cpp/xenia/src/xenia/`.

## Conclusão principal

As melhores próximas apostas são reduzir a espera ativa que aquece o aparelho,
corrigir a política de consulta de fences e evitar reconstruir estados de desenho
que não mudaram.

Há também uma oportunidade interessante de combinar o rastreamento de leituras
da CPU com o caminho UMA — duas peças que já existem, mas atualmente não estão
integradas dessa forma.

**As ideias deste documento são hipóteses fundamentadas no código, não ganhos já
medidos.** Não foram implementadas nem submetidas a novos testes durante esta
análise. O código pode evoluir após esta revisão; antes de implementar, conferir
se os mecanismos descritos continuam correspondendo à versão em uso.

## O que os resultados realmente dizem

- **A especialização alfa funciona:** o AB2 isolou aproximadamente **12–14% de
  ganho de FPS**.
- **O clear dentro do render pass é praticamente neutro:** remove cerca de dez
  aberturas de pass, mas as barreiras seguintes continuam interrompendo o
  processamento.
- **R11G11B10 oferece ganho pequeno, mas repetível:** aproximadamente **3,1%**,
  com perda de precisão e do alfa armazenado.
- **A leitura anterior do replay estava errada:** os 15–25 ms incluem
  principalmente espera. Uma thread adicional para gravar comandos não
  recuperaria esse tempo.
- **O desempenho sustentado é muito sensível ao calor:** desperdiçar quase um
  núcleo em espera ativa pode ser mais prejudicial que alguns milissegundos de
  trabalho gráfico.

Outro cuidado: **40 ms na thread de comandos, incluindo esperas, não significam
40 ms de processamento de CPU.** O perfil mostra aproximadamente 0,45 núcleo
ocupado. Portanto, é necessário investigar dependências e sincronização junto
com o custo por draw.

---

## 1. Suspender a espera pelo contador da GPU

**Prioridade:** primeira frente de investigação, principalmente para desempenho
sustentado.

**Código relevante:**

- `cpu/compiler/passes/memory_poll_park_pass.cc`
- `cpu/ppc/ppc_emit_alu.cc`
- `cpu/backend/a64/a64_seq_memory.cc`
- `kernel/guest_scheduler.cc`

### O detalhe importante no código

O problema vai além de o `MemoryPollParkPass` não reconhecer loops com chamadas.

A sequência encontrada é:

1. `ppc_emit_alu.cc` transforma `or r31,r31,r31` em `DelayExecution`.
2. No backend ARM64, `DelayExecution` vira `isb`, que continua ocupando o núcleo.
3. `SpinBackoffParkThunk`, com o scheduler cooperativo ligado, chama um **yield da
   fiber**.
4. `GuestScheduler::YieldCurrentThread()` retorna imediatamente quando não existe
   outra fiber pronta.

**Mesmo ampliar o reconhecimento do loop pode continuar deixando o núcleo
ocupado. Yield não garante que ele durma.**

### Proposta

Criar uma espera cooperativa específica por progresso:

```text
contador não avançou
    → algumas tentativas curtas
    → registrar a fiber como esperando esse contador
    → retirar a fiber da fila de execução
    → acordar quando o produtor atualizar o contador
```

O primeiro protótipo pode reconhecer as funções do FH identificadas no relatório,
validando o hash do executável e o padrão das instruções. Depois, generalizar.

O despertar deve vir do **escritor real do contador**, identificado por
rastreamento. Não se deve assumir que todo contador do jogo corresponde
diretamente a uma fence Vulkan.

### Correção essencial

- Preservar os timeouts do jogo.
- Revalidar o valor ao registrar a espera, evitando perder um despertar ocorrido
  entre a leitura e a suspensão.
- Evitar bloquear uma fiber segurando o lock global.
- Suspender a fiber pelo scheduler, permitindo que outras fibers executem, em vez
  de dormir indiscriminadamente a thread host que as hospeda.

### Como avaliar

Executar sessões de 10–15 minutos, medindo:

- ocupação da Guest CPU 0;
- consumo;
- nível de restrição térmica;
- FPS sustentado e latência de retomada.

**Potencial:** talvez pouco ganho com o aparelho frio, mas uma das melhores
chances de evitar a queda posterior de desempenho.

---

## 2. Impedir que a coleta de fences espere mais do que precisa

**Código relevante:**

- `ui/vulkan/vulkan_gpu_completion_timeline.cc`
- `ui/gpu_completion_timeline.h`

### O que chamou atenção

`AcquireFenceForSubmission()` evita consultar fences até acumular oito pendentes.
Porém, `UpdateCompletedSubmission()` percorre a fila chamando
`vkGetFenceStatus()` sucessivamente.

O próprio código e o AB3 indicam que essa consulta pode bloquear no Turnip/KGSL.
Se isso acontecer, **uma coleta que pretendia recuperar recursos antigos pode
avançar até submissões recentes e esperar por elas também**.

Além disso, `AwaitSubmissionAndUpdateCompleted()` pode consultar a conclusão antes
e depois da espera pelo índice solicitado.

### Proposta em duas etapas

1. **Protótipo pequeno:** limitar a coleta e esperar diretamente pela submissão
   necessária, sem percorrer automaticamente as posteriores.
2. **Evolução:** uma thread de conclusão espera fences e publica apenas um índice
   monotônico de progresso. A thread gráfica usa esse índice para reciclar seus
   recursos.

A gravação dos comandos pode continuar na thread atual.

### Correção essencial

- Só reciclar buffers, descritores e imagens após conclusão real.
- Manter o limite de frames em voo.
- Sincronizar a publicação dos índices e a propriedade das fences.
- Evitar que a thread de conclusão modifique diretamente caches e estruturas
  pertencentes à thread gráfica.

### Como avaliar

- Tempo por consulta de fence.
- Quantidade de fences coletadas por chamada.
- Ociosidade da GPU.
- FPS, distribuição dos intervalos entre frames e latência.

**Potencial:** recuperar sobreposição entre CPU e GPU. Isso merece investigação
antes de assumir que todo o tempo de espera é inevitável.

---

## 3. Parar de reenviar constantes float idênticas

**Código relevante:** `gpu/vulkan/vulkan_command_processor.cc`, especialmente
`WriteShaderConstantsFromMem()` e `UpdateBindings()`.

### Evidência no código

O código já evita invalidar **fetch constants e bool/loop constants** quando
recebe o mesmo valor.

Mas `WriteShaderConstantsFromMem()` invalida os buffers de constantes float quando
o intervalo toca constantes usadas pelo shader, **mesmo que os valores sejam
iguais**.

Depois, `UpdateBindings()`:

- aloca espaço no upload buffer;
- percorre o mapa de constantes;
- copia os valores novamente.

### Proposta

Durante a cópia com conversão de endianness, produzir também uma máscara dos
valores que realmente mudaram.

Só invalidar o UBO quando:

```text
constantes alteradas ∩ constantes usadas pelo shader ≠ vazio
```

A comparação deve ser por bits, preservando NaNs e zero negativo.

Uma segunda melhoria seria pré-calcular os intervalos contíguos usados por cada
shader, substituindo várias cópias de 16 bytes por poucas cópias maiores.

### Correção e custo da própria otimização

- Preservar todas as escritas observáveis de registradores.
- Evitar invalidar dados usados por outro layout de shader incorretamente.
- Medir se a comparação acrescentada custa menos que os uploads evitados.
- Aproveitar a passagem de cópia já existente, em vez de percorrer os dados
  repetidamente sem necessidade.

### Como avaliar

- Bytes enviados por frame.
- Alocações de UBO.
- Porcentagem de escritas idênticas.
- Tempo ativo em `UpdateBindings()`.

**Por que é interessante:** é localizada, preserva a imagem e pode aproveitar
estados repetidos entre as faixas do tiling.

---

## 4. Criar um plano de draw reutilizável

**Código relevante:**

- `gpu/vulkan/vulkan_command_processor.cc`
- `gpu/vulkan/vulkan_pipeline_cache.cc`

### Evidência no código

O cache de pipelines já existe. Porém, em `ConfigurePipeline()`, o código primeiro:

1. constrói a descrição;
2. verifica requisitos;
3. deriva o estado dinâmico;
4. normaliza a descrição;
5. só então verifica se é igual ao pipeline anterior.

Isso evita recompilar pipelines, mas ainda reconstrói bastante informação por
draw.

### Proposta

Criar um `DrawPlan` com as decisões estáveis:

- variante de shader;
- layout;
- interpretação das constantes;
- partes derivadas do estado gráfico;
- metadados necessários aos bindings.

O plano seria invalidado por **gerações de grupos de registradores**, não por uma
comparação completa de todo o estado a cada chamada.

Mudar a matriz de um objeto, por exemplo, não deveria obrigar a recalcular decisões
de blend, formato dos attachments e organização dos samplers.

### Correção essencial

Mudanças de render target, recursos, layouts assíncronos e ownership da EDRAM
precisam invalidar os componentes correspondentes. O plano deve reutilizar
derivações de estado; os efeitos de memória e as dependências de recursos
continuam sendo executados conforme sua validade real.

### Como avaliar

- Taxa de reutilização do plano.
- Motivos de invalidação.
- Microssegundos ativos por draw.
- Custo de gerenciamento do próprio cache.

**Potencial:** uma otimização estrutural para os aproximadamente três mil draws
por frame.

---

## 5. Combinar readback seletivo com UMA

**Código relevante:**

- `gpu/command_processor_resolve_readwatch.inc`
- `gpu/vulkan/vulkan_command_processor.cc`
- `gpu/vulkan/vulkan_shared_memory.cc`

### Evidência no código

Já existem duas peças úteis:

- o modo `fast` acompanha páginas lidas pela CPU;
- o modo `uma` consegue copiar da memória Vulkan mapeada nos Adreno sem importação
  da RAM do guest.

Entretanto, a decisão seletiva em `DecideResolveHostCopy()` é aplicada ao modo
`fast`. O caminho direto UMA é escolhido separadamente.

### Proposta

Separar duas decisões:

```text
Política: quais resultados a CPU precisa receber?
Transporte: como entregar esses resultados?
```

Assim, teríamos **UMA seletivo**: o transporte que funciona no POCO, com a política
de evitar cópias sem consumidor.

O AB3 já mostrou que remover essas cópias elimina aproximadamente 2 ms por frame
naquele caminho. O objetivo seria recuperar parte desse custo preservando os
casos que realmente precisam de readback.

### Correção essencial

- Primeira leitura de um resultado.
- Rearmamento dos watches de memória.
- Sobreposição de intervalos.
- Coerência entre o conteúdo produzido pela GPU e a cópia visível à CPU.

Limpar simplesmente os bits de leitura pode fazer o emulador perder consumidores
futuros.

### Como avaliar

- Bytes copiados e quantidade de readbacks por frame.
- Custo de `ReadHostMapped()` e dos callbacks de leitura.
- Frequência de waits necessários para coerência.
- Correção em corrida, modo foto, menus e outros recursos que possam ler
  resultados gráficos pela CPU.

---

## 6. Atacar as barreiras que tornaram o clear otimizado neutro

**Código relevante:** `gpu/vulkan/vulkan_shared_memory.cc`, função `Use()`, e o
caminho de submissão de barreiras em `gpu/vulkan/vulkan_command_processor.cc`.

### Evidência no código

Quando o uso do buffer muda, o código pode inserir uma barreira sobre
**`VK_WHOLE_SIZE`**. A submissão dessas barreiras encerra o render pass.

### Proposta

Rastrear dependências por intervalos ou páginas da memória compartilhada.

Exemplo:

```text
Resolve escreveu a região A.
Próximo draw lê vértices e índices da região B.
Se A e B comprovadamente não se sobrepõem,
essa transição pode não exigir a dependência global atual.
```

Isso pode preservar passes que hoje são encerrados logo após um clear.

**É uma hipótese que pode dar utilidade à otimização de clear que hoje é neutra.**

### Correção essencial

Considerar aliasing, memexport e acessos dinâmicos. Quando não for possível provar
independência, usar o caminho conservador atual.

### Primeiro experimento

Contar encerramentos de render pass por motivo e quantos são causados por regiões
comprovadamente disjuntas. Só implementar o rastreamento completo se esse número
justificar o custo.

Depois, comparar a nova política de dependências com o clear no pass desligado e
ligado, para medir a interação entre as duas mudanças.

---

## 7. A aposta mais ambiciosa: compilar blocos PM4 frequentes

**Código relevante:** `gpu/pm4_command_processor_implement.h`.

### Evidência

O FH executa aproximadamente **15 mil `SET_BIN_MASK` por frame**, além das
escritas de registradores e dos draws. O interpretador percorre esses pacotes por
cabeçalho, opcode e dispatch.

### Proposta

Transformar sequências frequentes em pequenas operações internas especializadas:

```text
ler máscara atual
→ avaliar predicado
→ atualizar um grupo conhecido de registradores
→ executar draw com plano conhecido
```

Seria um pequeno compilador do fluxo de comandos gráficos.

O relatório oferece uma restrição importante: **o conteúdo dos pacotes muda entre
as faixas**. Portanto, o cache deve reutilizar a estrutura validada do bloco e
continuar lendo os valores mutáveis, inclusive máscaras e constantes.

### Correção essencial

- Invalidação de memória modificada.
- Buffers indiretos aninhados.
- Preservação exata de predicados, eventos, interrupções e esperas.
- Tratamento de cabeçalhos alterados, com retorno ao interpretador quando a
  estrutura deixa de corresponder ao bloco especializado.

### Como avaliar

- Tempo ativo no interpretador PM4.
- Custo por pacote.
- Frequência de reutilização e invalidação dos blocos.
- Comparação da sequência de efeitos observáveis com o interpretador original.

É uma aposta de maior engenharia, indicada se as otimizações menores ainda
deixarem o frontend gráfico caro.

---

## 8. Extensão da otimização de shaders: teste alfa sem alpha-to-coverage

**Código relevante:**

- `gpu/guest_spirv_shader_cache.cc`
- `gpu/spirv_shader_translator.h`
- `gpu/spirv_shader_translator.cc`
- `gpu/spirv_shader_translator_rb.cc`

Investigar uma variante **teste alfa ativo, alpha-to-coverage desligado**.

Hoje, a especialização principal remove os dois mecanismos quando ambos estão
desligados. Vegetação e outros materiais que precisam de teste alfa podem
continuar recebendo um caminho genérico de cobertura desnecessário.

A nova variante preservaria o descarte necessário e omitiria apenas a cobertura
não utilizada.

Não se deve esperar repetir automaticamente os 12–14% anteriores: o `OpKill`
desses materiais continua necessário. Além disso, a chave de tradução atual já
ocupa seus campos; a nova variante exige uma extensão explícita e versionada do
cache.

### Como avaliar

- Frequência de draws com teste alfa ativo e cobertura desativada.
- Diferença no SPIR-V e no custo de GPU desses shaders.
- Correção da vegetação, transparências e bordas sob MSAA.
- Aumento no número de variantes, memória de cache e stutter de compilação.

---

## Ordem recomendada de execução

| Ordem | Experimento | Principal objetivo |
|---|---|---|
| **1** | Espera cooperativa pelo contador | FPS sustentado e menor calor |
| **2** | Coleta limitada de fences | Evitar sincronização excessiva |
| **3** | Constantes float por diferença | Redução localizada de CPU e cópias |
| **4** | UMA seletivo | Eliminar readbacks sem consumidor |
| **5** | Plano de draw | Reduzir preparação repetida |
| **6** | Dependências por intervalo | Preservar render passes |
| **7** | Blocos PM4 especializados | Redesenho mais ambicioso do frontend |

A variante de teste alfa sem cobertura é uma frente adicional de shaders, a
priorizar se a contagem de draws mostrar uso suficiente para justificar mais
variantes.

## Critério geral de validação

- Comparar a mesma cena, save, driver, resolução e patches.
- Aquecer os caches relevantes antes da coleta.
- Usar braços intercalados e repetir a referência.
- Medir separadamente CPU ativa, CPU esperando, execução de GPU e apresentação.
- Registrar o estado térmico e os cooling devices, incluindo testes prolongados.
- Avaliar imagem e estabilidade em mais de uma cena.
- Tratar objetivos de economia de CPU, energia e banda como métricas próprias;
  uma redução local não garante aumento imediato de FPS.

## Aposta principal

**Combinar economia térmica com sincronização mais precisa.** O AB3 indica que
economizar trabalho gráfico isoladamente pode não aparecer no FPS frio. Fazer a
CPU parar de gastar energia esperando e impedir esperas além da submissão
necessária pode criar espaço para que as outras otimizações finalmente se
traduzam em desempenho.
