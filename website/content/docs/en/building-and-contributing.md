---
title: Building and contributing
description: Tools and versions, first-time setup, build and test commands, what CI checks, and how a change becomes a release.
section: projeto
order: 1
conferido: 29ef9e7c7
fontes: [BUILD.md, app/build.gradle, emulator-core/build.gradle, local.properties.example, .github/workflows/XenDroid.yml, .github/workflows/checks.yml, .github/pull_request_template.md, tools/release_notes.py, tools/check-apk.py, tools/check-jni.py]
pt: compilar-e-contribuir
---

Xendroid+ is an Android build, 64-bit ARM only, of a Xenia Canary fork with an ARM64 JIT. It builds from **Linux** or **Windows**: both cross-compile for Android through the NDK. The full guide is [BUILD.md](repo:BUILD.md); this page sums up the essentials.

## Structure

- `:emulator-core`: the native build (CMake produces `libe.so`) and the Java classes bound through JNI. The Xenia tree lives inside `emulator-core/src/main/cpp/xenia/` as regular files: there is no git submodule to initialize.
- `:app`: the interface, in Kotlin and Jetpack Compose.
- `patches/xenia-canary/patches/`: the patch catalog, copied into the APK at build time.
- `website/`: this site, built separately from the app (see [Site data](doc:site-data)).

## Tools

| Tool | Version |
|---|---|
| JDK | 21 |
| Android SDK | platform 35 (build-tools 35.0.0 for the APK checks) |
| Android NDK | 29.0.14206865 |
| CMake | 3.30.3, with Ninja |
| Gradle | 8.11.1, through the wrapper |
| Python | 3.x, to generate the shaders |
| SPIR-V shaders | `glslangValidator`, `spirv-opt` and `spirv-dis` on the PATH or in `$VULKAN_SDK/bin` |

On Linux:

```sh
sudo apt install openjdk-21-jdk python3 glslang-tools spirv-tools
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # adjust for your distro
sdkmanager "platform-tools" "platforms;android-35" "ndk;29.0.14206865" "cmake;3.30.3"
```

On Windows: JDK 21 with `JAVA_HOME`, the same packages through `sdkmanager.bat`, the LunarG **Vulkan SDK** (`winget install KhronosGroup.VulkanSDK`, which brings the three shader tools), Python 3 from python.org (not the Microsoft Store shortcut) and long paths turned on (`git config --global core.longpaths true` and the Windows policy), with the clone in a short folder such as `C:\dev\xendroid`.

::: aviso UTF-8 locale
Gradle needs `LC_ALL=C.UTF-8` (or another UTF-8 locale): with the POSIX locale, copying the patches fails on file names with accented characters.
:::

## First-time setup

```sh
cp local.properties.example local.properties
```

Set `sdk.dir` and `cmake.dir` (on Windows, with forward slashes). `cmake.dir` must point to the SDK's CMake 3.30.3: a different `cmake` on the PATH breaks the configure step. In the IDE, import the **Gradle project**; the native CMake project on its own configures with the host computer's compiler and fails.

## Build

```sh
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
./gradlew clean :app:assembleDebug    # from scratch
./gradlew :app:installDebug           # installs on the connected phone
```

The first build compiles the whole native tree (about 8 to 9 minutes from cold). The debug APK lands in `app/build/outputs/apk/debug/`, with the package `xendroid.compose.debug`, which installs alongside the released app. On Linux with little memory, use `XENDROID_NINJA_JOBS=2`.

`./gradlew :app:assembleRelease` builds the released package (`{{app.pacote}}`), but Gradle does not sign it: CI signs it with the project key.

## Test before you submit

```sh
# unit tests and lint
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug
# screen tests on the JVM; with -Pscreenshots it saves the screenshots to docs/ui-redesign/prints/app
./gradlew :app:testUitestUnitTest -Pscreenshots="$PWD/docs/ui-redesign/prints/app"
# JNI bindings between Java and C++ (no build)
python3 tools/check-jni.py
# packaging of a finished APK: arm64 only, signed, with the licenses and no forbidden files
python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk --aapt2 "$ANDROID_SDK/build-tools/35.0.0/aapt2"
# pure native logic (presentation, frame generation, pipeline cache)
bash tools/test-native-logic.sh
```

The instrumented tests run on a connected phone with `./gradlew :app:connectedUitestAndroidTest`; they install their own package, `xendroid.compose.uitest`, and do not touch the debug app's games, saves and settings.

::: nota What CI does not run
CI builds the APK and runs the quick checks, but it does not run the unit, screen, instrumented or native tests. Run them before you open a pull request.
:::

## What CI checks

- **Checks** (every pull request to `main`): syntax of the Python, shell and PowerShell scripts in `tools/`, the JNI bindings and workflow linting (actionlint).
- **Xendroid+** (pull requests and pushes to `main`, except changes only to documentation, the site (`website/`), `tools/` and the performance tests): builds the APK. On a pull request it builds the test package `{{app.pacoteTeste}}` (debuggable, installs alongside); on `main`, the released package, signed and checked by `tools/check-apk.py`, and it publishes the release.
- **Site** (`website/`): this site has its own workflow, which builds and publishes the pages (see [Site data](doc:site-data#publishing)).

## From change to release

Each push to `main` that builds the app becomes a release `XenDroid-v<build number>-<commit>`, with the APK signed by the project key (without the key configured in the repository, CI only builds the APK, with no release). The release notes are written in the pull requests: the pull request template has two blocks, in English and Portuguese, for what players will notice (short sentences, highlights as “- ” and sections with “###”). `tools/release_notes.py` gathers the blocks of every pull request since the previous release and builds the release page and the summary that the app's update card shows in the phone's language.

## Licenses

Third-party components keep their own licenses, listed in [THIRD-PARTY-NOTICES.md](repo:THIRD-PARTY-NOTICES.md): Xenia (BSD), Win-FG (MIT), the LSFG engine (GPL-3.0 or later), Snapdragon GSR (BSD-3-Clause) and the Barlow and JetBrains Mono fonts (OFL 1.1), among others. Because the build links an engine under the GPL, distributing the APK requires offering the corresponding source code. `Lossless.dll` and any data extracted from it belong to the user and never go into the code or the APK.

The repository does not yet have its own license file at the root or a contributing guide; the Xenia license is at [emulator-core/src/main/cpp/xenia/LICENSE](repo:emulator-core/src/main/cpp/xenia/LICENSE).
