# Pesquisa LSFG-VK e aplicação ao XenDroid

Pesquisa em 2026-09-30; integra o objetivo de experiência em jogo.

## Conclusões que alteram a escolha de código

- O [GitHub original](https://github.com/PancakeTAS/lsfg-vk) agora contém apenas o aviso de migração. O código atual está em [git.lsfg-vk.dev](https://git.lsfg-vk.dev/lsfg-vk) e o histórico anterior em [lsfg-vk-archive](https://git.lsfg-vk.dev/lsfg-vk-archive).
- A [mudança oficial de 2026-08-27](https://lsfg-vk.dev/blog/important-changes-to-lsfg-vk/) informa: v1 originalmente **MIT**; o desenvolvimento v2 foi GPLv3 e o novo repositório passou para **CC BY-NC-ND 4.0**. A v2 atual não é uma base livre para distribuir uma adaptação ao Xenia sem permissão adicional do autor. A autorização do usuário para integrar GPL não é autorização do detentor para modificar/distribuir código ND.
- O [fork Android de FrankBarretta](https://github.com/FrankBarretta/lsfg-vk-android), branch `release`, declara-se baseado em **1.0.0** e a API GitHub informa MIT. Essa licença precisa continuar sendo conferida nos arquivos concretos, não inferida da v2 atual. Último commit consultado: `3e89e5439a` (2026-05-01, enable shaderFloat16).
- O código de referência LSFG já importado do Bannerlator possui headers **GPL-3.0-or-later**, derivados de WinNative. Preservar essa proveniência independentemente da licença MIT da antiga versão original.

## Arquitetura

LSFG-VK extrai shaders de uma DLL fornecida pelo usuário, traduz **DXBC → SPIR-V** com um subconjunto do tradutor DXVK e executa a cadeia de compute para produzir imagens intermediárias. No Linux, uma Vulkan layer intercepta swapchain/present; não exige Wine para o algoritmo. O texto de referência [Porting LSFG to native Vulkan](https://lsfg-vk.dev/blog/porting-lsfg-to-native-vulkan/) descreve tradução de shaders e reconstrução da sequência D3D11 em Vulkan.

O [port Android](https://raw.githubusercontent.com/FrankBarretta/lsfg-vk-android/release/README.md) substitui compartilhamento por file descriptors com **AHardwareBuffer**, pois o caminho de `vkGetMemoryFdKHR` não serve para os buffers AHB importados em Adreno/Mali. Adiciona `createContextFromAHB` e `waitIdle`. O app separado usa MediaProjection e overlay para operar sobre outros programas, mas isso não é necessário para XenDroid, que controla seu próprio device e apresentador Vulkan.

**Escolha no XenDroid:** usar imagens do próprio apresentador, no mesmo device/queue, e a cadeia host-side. Evitar captura de tela, segundo device, overlay de sistema, troca de AHB e sincronização `DeviceWaitIdle` entre devices. O scheduler/posse de imagens são comuns ao Win-FG e LSFG. Uma layer genérica não resolve o descarte de imagens intermediárias pelo mailbox do host.

## Melhorias interessantes da v2, como referência de projeto

A [versão 2.0.0, publicada em 2026-09-05](https://lsfg-vk.dev/blog/release-v2.0.0/), anuncia pipeline bindless, FP16, reutilização de memória, Vulkan 1.2, utilitários de benchmark/validação/healthcheck e fixes de background/present. Os resultados publicados incluem melhorias em RTX 5080 e Radeon 680M; não são estimativas de Adreno 825 nem comparação pareada com Win-FG.

Ideias aplicáveis sem transplantar código ND: cache versionado e teste de shaders, benchmark de custo GPU por resolução/multiplicador, liberar/reutilizar intermediários com vida útil não sobreposta, FP16 somente quando suportado e validado, funções de estado distinguindo solicitado/ativo/falha. O [benchmark oficial](https://lsfg-vk.dev/docs/cli/benchmark/) separa throughput e tempo por iteração.

O [documento atual de pacing](https://lsfg-vk.dev/docs/configuration/pacing-modes/) informa que só existe `vsync`: gerar/apresentar assim que disponível e exigir Vsync para evitar descarte. Isso não elimina latência nem comprova cadência no Android; FIFO/IMMEDIATE e o relógio do host exigem validação própria. Adaptive frame generation e dual-GPU são descritos pelo projeto como futuros, não funcionalidades prontas a copiar.

## Assets e testes

`Lossless.dll` e qualquer SPIR-V extraído são arquivos locais do usuário; não entram no git, APK ou fixtures públicas. A DLL extraída do arquivo em Downloads está fora do workspace, usada apenas para verificar importação/tradução. Fixtures sintéticas testam parsing malformado e limites sem distribuir conteúdo proprietário.

Continuar testes locais com Vulkan por software para criação de pipelines/saídas sintéticas. Isso valida código e sincronização exercitada no teste; não substitui scanout, custo GPU, artefatos de gameplay, latência ou comportamento do driver Adreno no aparelho.
