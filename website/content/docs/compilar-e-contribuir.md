---
title: Compilar e contribuir
description: Ferramentas e versões, primeira configuração, comandos de build e de teste, o que o CI confere e como uma mudança vira release.
section: projeto
order: 1
conferido: e179885e7
fontes: [BUILD.md, app/build.gradle, emulator-core/build.gradle, local.properties.example, .github/workflows/XenDroid.yml, .github/workflows/checks.yml, .github/pull_request_template.md, tools/release_notes.py, tools/check-apk.py, tools/check-jni.py]
---

O Xendroid+ é um build Android, só para ARM de 64 bits, de um fork do Xenia Canary com JIT para ARM64. Ele compila a partir de **Linux** ou **Windows**: os dois fazem cross-compile para Android pelo NDK. O guia completo é o [BUILD.md](repo:BUILD.md); esta página resume o essencial.

## Estrutura

- `:emulator-core`: o build nativo (CMake gera `libe.so`) e as classes Java ligadas por JNI. A árvore do Xenia está dentro de `emulator-core/src/main/cpp/xenia/` como arquivos comuns: não há submódulo do git para iniciar.
- `:app`: a interface em Kotlin e Jetpack Compose.
- `patches/xenia-canary/patches/`: o catálogo de patches, copiado para o APK no build.
- `website/`: este site, com o build separado do app (veja [Dados do site](doc:dados-do-site)).

## Ferramentas

| Ferramenta | Versão |
|---|---|
| JDK | 21 |
| Android SDK | plataforma 35 (build-tools 35.0.0 para as verificações do APK) |
| Android NDK | 29.0.14206865 |
| CMake | 3.30.3, com Ninja |
| Gradle | 8.11.1, pelo wrapper |
| Python | 3.x, para gerar os shaders |
| Shaders SPIR-V | `glslangValidator`, `spirv-opt` e `spirv-dis` no PATH ou em `$VULKAN_SDK/bin` |

No Linux:

```sh
sudo apt install openjdk-21-jdk python3 glslang-tools spirv-tools
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # ajuste para a sua distro
sdkmanager "platform-tools" "platforms;android-35" "ndk;29.0.14206865" "cmake;3.30.3"
```

No Windows: JDK 21 com `JAVA_HOME`, os mesmos pacotes pelo `sdkmanager.bat`, o **Vulkan SDK** da LunarG (`winget install KhronosGroup.VulkanSDK`, que traz as três ferramentas de shader), Python 3 do python.org (não o atalho da Microsoft Store) e caminhos longos ligados (`git config --global core.longpaths true` e a política do Windows), com o clone numa pasta curta como `C:\dev\xendroid`.

::: aviso Locale UTF-8
O Gradle precisa de `LC_ALL=C.UTF-8` (ou outro locale UTF-8): com o locale POSIX, a cópia dos patches falha nos nomes de arquivo com acento.
:::

## Primeira configuração

```sh
cp local.properties.example local.properties
```

Defina `sdk.dir` e `cmake.dir` (no Windows, com barras normais). O `cmake.dir` tem que apontar para o CMake 3.30.3 do SDK: um `cmake` diferente no PATH quebra o configure. Na IDE, importe o **projeto Gradle**; o projeto CMake nativo sozinho configura com o compilador do computador e falha.

## Compilar

```sh
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
./gradlew clean :app:assembleDebug    # do zero
./gradlew :app:installDebug           # instala no telefone conectado
```

O primeiro build compila a árvore nativa inteira (cerca de 8 a 9 minutos a frio). O APK de depuração sai em `app/build/outputs/apk/debug/`, com o pacote `xendroid.compose.debug`, que instala ao lado do app publicado. Com pouca memória no Linux, use `XENDROID_NINJA_JOBS=2`.

`./gradlew :app:assembleRelease` gera o pacote publicado (`{{app.pacote}}`), mas o Gradle não assina: o CI assina com a chave do projeto.

## Testar antes de mandar

```sh
# testes de unidade e lint
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug
# testes de tela no JVM; com -Pscreenshots grava os prints em docs/ui-redesign/prints/app
./gradlew :app:testUitestUnitTest -Pscreenshots="$PWD/docs/ui-redesign/prints/app"
# ligações JNI entre o Java e o C++ (sem build)
python3 tools/check-jni.py
# empacotamento de um APK pronto: só arm64, assinado, com as licenças e sem arquivos proibidos
python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk --aapt2 "$ANDROID_SDK/build-tools/35.0.0/aapt2"
# lógica nativa pura (apresentação, geração de quadros, cache de pipelines)
bash tools/test-native-logic.sh
```

Os testes instrumentados rodam num telefone conectado com `./gradlew :app:connectedUitestAndroidTest`; eles instalam o próprio pacote, `xendroid.compose.uitest`, e não tocam nos jogos, saves e ajustes do app de depuração.

::: nota O que o CI não roda
O CI compila o APK e roda as checagens rápidas, mas não roda os testes de unidade, de tela, instrumentados nem os nativos. Rode-os antes de abrir um pull request.
:::

## O que o CI confere

- **Checks** (todo pull request para o `main`): sintaxe dos scripts Python, shell e PowerShell de `tools/`, as ligações JNI e o lint dos workflows (actionlint).
- **Xendroid+** (pull requests e pushes no `main`, exceto mudanças só em documentação, `tools/` e testes de desempenho): compila o APK. Num pull request gera o pacote de teste `{{app.pacoteTeste}}` (depurável, instala ao lado); no `main`, o pacote publicado, assinado e conferido pelo `tools/check-apk.py`, e publica a release.
- **Site** (`website/`): este site tem o próprio workflow, que gera e publica as páginas (veja [Dados do site](doc:dados-do-site#publicacao)).

## Da mudança à release

Cada push no `main` que compila o app vira uma release `XenDroid-v<número do build>-<commit>`, com o APK assinado pela chave do projeto (sem a chave configurada no repositório, o CI só gera o APK, sem release). As notas da release são escritas pelos pull requests: o modelo de pull request tem dois blocos, em inglês e em português, para o que os jogadores vão notar (frases curtas, destaques em “- ” e seções com “###”). O `tools/release_notes.py` junta os blocos de todos os pull requests desde a release anterior e gera a página da release e o resumo que o cartão de atualização do app mostra no idioma do telefone.

## Licenças

Os componentes de terceiros mantêm as próprias licenças, listadas no [THIRD-PARTY-NOTICES.md](repo:THIRD-PARTY-NOTICES.md): o Xenia (BSD), o Win-FG (MIT), o motor LSFG (GPL-3.0 ou posterior), o Snapdragon GSR (BSD-3-Clause) e as fontes Barlow e JetBrains Mono (OFL 1.1), entre outros. Como o build liga um motor sob GPL, distribuir o APK exige oferecer o código-fonte correspondente. O `Lossless.dll` e qualquer dado extraído dele são do usuário e nunca entram no código nem no APK.

O repositório ainda não tem um arquivo de licença próprio na raiz nem um guia de contribuição; a licença do Xenia está em [emulator-core/src/main/cpp/xenia/LICENSE](repo:emulator-core/src/main/cpp/xenia/LICENSE).
