#!/bin/bash
# Build environment for cloud sessions (Claude Code on the web), matching the
# CI workflow: JDK 21, Android SDK platform 35 + build-tools 35.0.0, NDK
# 29.0.14206865, CMake 3.30.3, the host SPIR-V shader tools, ninja and ccache,
# plus - when run inside the repository - the xenia submodules,
# local.properties and the Gradle distribution.
#
# Idempotent: a rerun only fills in what is missing. Never fails the session:
# problems are reported as WARN lines. Paste it as the environment's setup
# script, and run `bash tools/cloud-setup.sh` from the repository at the start
# of a session in case the setup ran before the repository was cloned.
set -u
SDK=${ANDROID_SDK_ROOT:-/opt/android-sdk}
NDK_VERSION=29.0.14206865
CMAKE_VERSION=3.30.3
CMDLINE_TOOLS_ZIP=commandlinetools-linux-13114758_latest.zip
JDK_LINK=/opt/jdk21
log() { echo "[cloud-setup] $*"; }
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  if command -v sudo >/dev/null 2>&1; then SUDO="sudo -n"; fi
fi

# 1. System packages (same as CI) and a JDK 21 at a fixed path.
need_apt=0
for tool in glslangValidator spirv-opt spirv-dis ninja ccache unzip curl python3; do
  command -v "$tool" >/dev/null 2>&1 || need_apt=1
done
ls -d /usr/lib/jvm/java-21-openjdk-* >/dev/null 2>&1 || need_apt=1
if [ $need_apt = 1 ] && command -v apt-get >/dev/null 2>&1; then
  log "installing system packages"
  $SUDO apt-get update -qq >/dev/null 2>&1
  $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    openjdk-21-jdk-headless python3 glslang-tools spirv-tools ninja-build \
    ccache unzip curl git >/dev/null 2>&1 || log "WARN: apt-get install failed"
fi
JDK_DIR=$(ls -d /usr/lib/jvm/java-21-openjdk-* 2>/dev/null | head -1)
if [ -z "$JDK_DIR" ] && [ ! -x "$JDK_LINK/bin/java" ]; then
  log "no JDK 21 package, downloading Temurin 21"
  mkdir -p /tmp/jdk21 && curl -fsSL -o /tmp/jdk21.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" &&
    tar -xzf /tmp/jdk21.tar.gz -C /tmp/jdk21 &&
    JDK_DIR=$(ls -d /tmp/jdk21/jdk-21* | head -1) ||
    log "WARN: JDK 21 download failed"
fi
if [ -n "$JDK_DIR" ]; then
  $SUDO ln -sfn "$JDK_DIR" "$JDK_LINK" 2>/dev/null || ln -sfn "$JDK_DIR" "$HOME/jdk21"
fi
export JAVA_HOME=$JDK_LINK
[ -x "$JAVA_HOME/bin/java" ] || export JAVA_HOME=$HOME/jdk21

# 2. Android SDK command line tools and packages.
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "installing Android command line tools into $SDK"
  $SUDO mkdir -p "$SDK" && $SUDO chown -R "$(id -u):$(id -g)" "$SDK"
  rm -rf /tmp/cmdline-tools /tmp/cmdline.zip
  curl -fsSL -o /tmp/cmdline.zip \
    "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" &&
    unzip -q /tmp/cmdline.zip -d /tmp &&
    mkdir -p "$SDK/cmdline-tools" &&
    rm -rf "$SDK/cmdline-tools/latest" &&
    mv /tmp/cmdline-tools "$SDK/cmdline-tools/latest" ||
    log "WARN: command line tools install failed"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
  missing=()
  [ -d "$SDK/platform-tools" ] || missing+=("platform-tools")
  [ -d "$SDK/platforms/android-35" ] || missing+=("platforms;android-35")
  [ -d "$SDK/build-tools/35.0.0" ] || missing+=("build-tools;35.0.0")
  [ -d "$SDK/ndk/$NDK_VERSION" ] || missing+=("ndk;$NDK_VERSION")
  [ -d "$SDK/cmake/$CMAKE_VERSION" ] || missing+=("cmake;$CMAKE_VERSION")
  if [ ${#missing[@]} -gt 0 ]; then
    log "installing SDK packages: ${missing[*]}"
    yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1
    "$SDKMANAGER" --sdk_root="$SDK" "${missing[@]}" >/dev/null 2>&1 ||
      log "WARN: sdkmanager failed for ${missing[*]}"
  fi
fi
command -v ccache >/dev/null 2>&1 && ccache -M 5G >/dev/null 2>&1

# 3. Repository pieces, only when run inside the checkout.
REPO=$(git rev-parse --show-toplevel 2>/dev/null)
if [ -n "$REPO" ] && [ -f "$REPO/emulator-core/build.gradle" ]; then
  cd "$REPO" || exit 0
  log "initializing submodules"
  git submodule update --init --recursive --depth 1 --jobs 8 >/dev/null 2>&1 ||
    git submodule update --init --recursive --jobs 8 >/dev/null 2>&1 ||
    log "WARN: submodule update failed"
  printf 'sdk.dir=%s\ncmake.dir=%s/cmake/%s\n' "$SDK" "$SDK" "$CMAKE_VERSION" \
    > local.properties
  chmod +x gradlew
  ./gradlew --version >/dev/null 2>&1 || log "WARN: Gradle wrapper download failed"
fi

log "done: JAVA_HOME=$JAVA_HOME ANDROID_SDK_ROOT=$SDK" \
  "java=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)" \
  "ndk=$([ -d "$SDK/ndk/$NDK_VERSION" ] && echo ok || echo MISSING)" \
  "cmake=$([ -d "$SDK/cmake/$CMAKE_VERSION" ] && echo ok || echo MISSING)" \
  "glslang=$(command -v glslangValidator >/dev/null && echo ok || echo MISSING)"
exit 0
