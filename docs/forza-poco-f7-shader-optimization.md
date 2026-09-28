# Forza Horizon / POCO F7: especialização do epílogo alfa SPIR-V

## Evidência e hipótese

Dispositivo: POCO F7, Snapdragon SM8735, Adreno 825, Android 16. Jogo: Forza
Horizon, `4D5309C9`, executável `D48ABF1704CE5C4A`.

O diagnóstico do APK original mostrou aproximadamente 100 resolves, 220 aberturas
de render pass e 2800 draws por frame. Um dos grupos mais caros foi o framebuffer
1280x512, 4x MSAA, `k_2_10_10_10_FLOAT` + `kD24FS8`. Os timestamps detalhados
introduzem overhead; esses tempos servem para localizar trabalho, não como
baseline de FPS. As amostras normais ficaram perto de 13 FPS na cena inicial,
com o aparelho quente. Resultados brutos: `performance-tests/`.

No código, `GuestSpirvShaderCache::GetPixelShaderModification` força o caminho
`kNoModifiers` em vez de `kEarlyHint` devido a uma falha NVIDIA documentada ali.
Porém o tradutor usava a ausência de early fragment tests para decidir também
se deveria emitir todo o teste alfa, alpha-to-coverage, `gl_SampleMask` e o input
de posição necessário para o dithering. Assim, o código genérico contém esses
caminhos mesmo em draws que têm ambas as operações desativadas nos registradores.

A hipótese é que um shader sem esses caminhos custa menos para compilar/executar
e permite ao driver escolher suas otimizações de profundidade normalmente.

## Alteração

- Acrescenta `DepthStencilMode::kNoAlphaTests`, usando o último valor livre do
  campo existente de três bits. A chave continua com 64 bits.
- Seleciona essa variante somente no caminho de host render targets, quando
  não há conversão de profundidade/offset em shader, RT0 pode ser escrito,
  alpha-to-coverage está desligado e o teste alfa está desabilitado ou `Always`.
- Omite o epílogo alfa e a saída de sample mask nessa variante.
- Omite `FragCoord` apenas quando nenhum outro consumidor precisa dele.
  ParamGen, offset/conversão de profundidade e memexport com resolução ampliada
  continuam recebendo suas coordenadas.
- Preserva descarte e escrita de profundidade do próprio shader, blend, MSAA e
  memexport. **Não emite `EarlyFragmentTests` para a nova variante.**
- Incrementa a versão da tradução para 17 para invalidar pipelines incompatíveis.
- `[GPU] spirv_specialize_no_alpha = false` seleciona a variante genérica;
  `true` seleciona a nova quando elegível. As variantes têm chaves distintas,
  permitindo A/B no mesmo APK.

## Verificação prevista

1. Compilar o APK debug e o alvo opcional `xendroid-shader-regression` com
   `-DXENDROID_BUILD_SHADER_REGRESSION=ON` no CMake dirigido pelo Gradle.
2. Executar a ferramenta no Android com uma cópia do cache `.xsh` do usuário:

   ```sh
   xendroid-shader-regression 4D5309C9.xsh shader-check
   ```

   Ela traduz os pixel shaders que escrevem RT0 nas duas variantes, verifica as
   interfaces de cobertura/profundidade, os descartes e a redução de bytecode.
3. Trazer os `.spv` para o host e executar:

   ```sh
   python3 tools/validate_spirv_corpus.py shader-check --validator spirv-val
   ```

4. Medir `spirv_specialize_no_alpha=false/true` na mesma cena e com o mesmo
   driver, resolução e patches. Aquecer o cache das duas variantes. Conferir
   imagem, vegetação, transparências e estabilidade em movimento.

## Estado

Implementação concluída; compilação e validação ainda em andamento. Não há ganho
de FPS atribuído a esta alteração até a comparação do APK modificado.

O perfil temporário usado nos testes anteriores de configurações foi removido
do pacote `xendroid.compose.fork`, restaurando a herança do perfil global original.
