# Compatibilidade — outros jogos (builds 89 a 98, 2026-10-02)

POCO F7 (Snapdragon 8s Gen 4, Adreno 825, Turnip Gen8 V37), pacote de teste
`xendroid.compose.fork.opt`, tudo pela rede (`adb` sobre Tailscale). Medidas
de desempenho sempre em jogo (corrida no Forza); os testes de crash do NFS
são aberturas de 75 s sem entrada (o crash acontece no boot/vídeo de
abertura, 17–60 s), cada uma só com o celular parado (`crash_ab.ps1` espera
nenhum uso desde a última entrada injetada e marca como inválida uma abertura
em que o app foi pausado).

## Resumo

| Jogo | Sintoma | Causa | Situação |
|---|---|---|---|
| Forza Horizon 2 (4D530AA4) | 24–27 fps, GPU 36–41 ms/frame na corrida | não tinha os quirks de GPU do Forza Horizon | quirks do FH1 no FH2 (b90): ~29,9 fps, GPU 20–22 ms |
| Need for Speed: Most Wanted (45410961) | crash em ~40% das aberturas: `bctrl` para 0 em 0x898D834C (às vezes 0x898C9C80/0x898C9E44) | **ABA na pilha lock-free do pool de jobs**: o `stwcx.` emulado comparava só o valor | **corrigido** (b97/b98): 0 crashes em 14 aberturas; **em jogo** (b101) com os quirks de `fmadz` e memexport, sem crash em 4 corridas de 90 s |
| Halo 4 (4D530919) | vídeos Bink do prólogo pretos (legendas empilhadas); o resto renderiza | o shader do vídeo tira a opacidade de um fetch **sem textura** (base 0) cujo swizzle é a constante 1 (`B6D`); o emulador zerava o swizzle de fetches inválidos (`924`, constante 0), o quadro saía transparente e as legendas se acumulavam | **corrigido** (b105): os componentes constantes do swizzle valem também sem textura; o prólogo aparece, a 30 fps |
| Sonic Unleashed (53450812) | um crash (SIGTRAP no thunk de resolução = chamada para endereço que não resolve) | não reproduzido depois; pode ser da mesma classe (ponteiro de função vindo de estrutura lock-free) | reavaliar no b98 |
| GTA IV / Red Dead Redemption | 22 / 25 fps, presos na thread de comandos (GTA: ~8500 draws/frame) | — | próximo item |

## Need for Speed: Most Wanted — o crash do pool de jobs

### Sintoma

`bctrl` com CTR = 0 em 0x898D834C, na função que roda um job
(0x898D82B8). Nos 7 dumps (b88–b92, reservas nativas e por software) o job
que crasha é **o job-sentinela do próprio worker** (`r25`, registrado na fila
`0xBBAC3280`), a geração no estado do job é **exatamente** a que o worker
anotou ao registrá-lo (`r18`), o bit de "pular" está limpo e o `fn` já é 0.
Já crashava no b77 (fim da v7): não é regressão da v8.

### O sistema de jobs do jogo

- job de 0x18 bytes: +0 estado de 64 bits (metade alta = geração, +2 por
  execução, bit 0 = reivindicado/pular; metade baixa = "next", usado **tanto**
  nas listas de espera **quanto** na lista livre do pool), +8 `fn`, +C ctx,
  +10 pool dono;
- rodar (0x898D82B8): CAS do estado para (geração+2, next 0), chama `fn` se o
  bit 0 estava limpo, zera `fn` e devolve o job ao pool;
- pool (0x898D7558 pop / 0x898D7538 push): pilha de Treiber **sem tag** —
  `lwarx head; next = head->next; stwcx. next`. No console é segura porque
  qualquer store na cabeça derruba a reserva de quem está no meio do pop;
- listas de espera (push 0x898D78F0, sinal 0x898D89D8 que desanexa a lista
  inteira por CAS com contador, reivindicação 0x898D6BC8 por geração): o
  protocolo garante exatamente uma execução por registro.

### Experimentos

| Experimento | Crashes | Conclusão |
|---|---|---|
| base (b88–b92) | 6 de 15 | ~40% |
| b77 (fim da v7) | 1 de 3 | anterior à v8 |
| reservas por software (`a64_native_reserved_ops = false`) | 2 de 3 | o caminho por software também não basta |
| todas as threads num despachante (`guest_scheduler_dispatch_cpus = 1`, novo) | 0 de 6 | precisa de threads de verdade em paralelo |
| ordem TSO (`a64_guest_memory_tso`, novo) | 1 de 1 | não é reordenação de loads/stores comuns — e o estado do job apareceu apontando para si mesmo: o mesmo job inserido duas vezes |
| anel de chamadas (`log_guest_calls_ring`, novo) congelado no crash | ver abaixo | **alocação dupla no pool** |
| b97: geração checada antes do `ldaxr` | 0 de 8 | |
| b98: geração checada dentro da janela exclusiva (final) | 0 de 6 | |

### A causa (anel do b96, crash clássico)

```
36332240         T8 pop do pool começa: lwarx head = 19C0, next = 0E60
36332371         T7 pop -> 0E60
36332389         T7 push 0E60 (head era 19C0)
36332406         T9 pop -> 0E60            <- 0E60 é da T9 agora
36332240->409    T8 pop termina -> 19C0    <- stwcx.: head ainda "é" 19C0 (ABA),
                                              publica o next velho: head = 0E60
36332429         T7 pop -> 0E60            <- o mesmo job com dois donos
```

A thread 8 ficou desescalonada pelo Android entre o `lwarx` e o `stwcx.` do
pop (no outro crash, por ~1800 eventos do anel). O `stwcx.` nativo era um
CAS só do valor, que não vê "mudou e voltou"; o caminho por software checava a
geração do granule e **depois** fazia o CAS — a mesma janela, só menor. Dois
workers com o mesmo job: um roda, zera `fn` e devolve; o outro roda com
`fn` = 0 (ou insere o job numa lista onde ele já está, e ele aponta para si
mesmo).

### A correção (b98)

- `lwarx`: guarda a geração do granule de 128 bytes (`ldar`) junto com a
  palavra;
- `stwcx.`: compara a palavra **e** a geração entre o `ldaxr` e o `stlxr` da
  palavra — uma troca de contexto ou um store na palavra antes do store faz o
  `stlxr` falhar e tudo é checado de novo; store feito, a geração anda
  (`staddl`). Nenhum monitor exclusivo atravessa o código do guest (a causa do
  travamento antigo no Forza Horizon);
- o load da geração dentro do par exclusivo está fora da garantia de
  progresso da arquitetura: depois de 16 `stlxr` falhos seguidos cai no
  "geração, depois CAS" antigo — nunca trava;
- os helpers em C++ (caminho por software) fazem o mesmo em assembly.

### Custo (Forza Horizon, corrida, teto de 30 fps)

| Build | fps | GPU/frame | M instruções / M ciclos por frame (total) | guest 0 |
|---|---|---|---|---|
| b96 (antes), 2 aberturas | 29,99 | 18,1 ms | 394,5 / 192,0 e 247,5 / 159,5 | 219,8 e 79,8 |
| b97, 2 aberturas | 29,99 | 18,3 ms | 196,0 / 145,4 e 203,9 / 149,6 | 27,7 e 24,4 |
| b98 (final), 1 abertura | 30,00 | 18,3 ms | 232,4 / 158,0 | 39,8 |

Nada pior; uma thread do guest gira bem menos (a CAS por valor deixava algum
laço do jogo dar mais voltas).

### Em jogo (b99–b101, 2026-10-03)

Para passar da intro para a primeira cena 3D, o NFS precisou de três coisas:
- `spirv_multiply_zero_test_on_bits` (quirk): o compilador do Turnip não
  tem `fmadz`, usado pelo vertex shader 13EE4011483DC17D;
- `memexport_enable` + `readback_resolve = uma` (quirks): a CPU lê dados que
  a GPU exporta e para em `PM4_WAIT_REG_MEM` sem eles; como o driver não
  importa a RAM do guest, o export é devolvido à RAM depois de cada desenho
  que exporta (espera síncrona);
- **devolver só os bytes que o desenho mudou** (b101). A b99 copiava a
  faixa inteira de cada stream — a capacidade declarada (`index_count` ×
  elemento), não o que o shader escreve — do espelho da GPU para a RAM. Uma
  delas, `1BAC31A0` (409 600 bytes), cobre o gerenciador e o pool de jobs; a
  cópia de 3 em 3 desenhos devolvia aos jobs estados velhos (gerados no
  espelho antes de a CPU escrevê-los), e o jogo crashava em 0x898D834C com
  um job no meio de uma lista, `fn` = 0 e geração antiga — o mesmo PC do
  ABA, outra causa. Agora a GPU copia as faixas para um buffer visível ao
  host logo antes do desenho e, terminado, só os bytes diferentes vão para a
  RAM, um a um (o resto pode ter sido escrito pela CPU agora mesmo). Medido:
  dos 409 600 bytes, o desenho muda **1**; dos 112 640, até 56 320; dos
  5 632, até 3 520.

b101: **4 de 4** aberturas chegaram à cena 3D (~170 s) e dirigiram 90 s sem
crash (b99: um crash em duas), a ~19 fps, imagem correta.

### Ferramentas novas (diagnóstico)

- `log_guest_calls_ring = N`: as linhas de `log_guest_calls_at` vão para um
  anel em memória (congelado e despejado no log quando o guest crasha), lendo
  as palavras do guest sem o lock global — o log por chamada serializava as
  threads e mudava o timing;
- `guest_scheduler_dispatch_cpus`: roda as CPUs do guest em menos threads de
  despacho (1 = tudo serializado);
- `a64_guest_memory_tso`: ordem de loads/stores do guest como x86;
- `dump_functions_at` aceita qualquer endereço dentro da função; o dump de
  crash mostra o início da função do PC;
- `tools/crash_ab.ps1`: N aberturas por braço, o PC de cada crash, espera o
  celular parado antes de cada uma;
- `tools/guest_ring_stack.py`: repete os pops e pushes de uma pilha
  lock-free gravados no anel e aponta nó retirado duas vezes (o ABA acima).
