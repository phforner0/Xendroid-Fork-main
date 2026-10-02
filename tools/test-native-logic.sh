#!/usr/bin/env bash
# Pure-logic native tests (no Vulkan): presentation policy, frame-generation
# schedule, the pipeline cache file store and the guest vblank pacer. They need
# only a C++20 compiler.
#
# Compiler: $CXX (default c++). On a host without one (no libstdc++/glibc headers,
# as in a bare WSL), set XENDROID_NDK to an NDK root: the tests are then built as
# static x86_64 Android executables, which a Linux kernel runs directly.
set -euo pipefail
root="$(realpath "$(dirname "${BASH_SOURCE[0]}")/..")"
build="${XENDROID_HOST_TEST_BUILD:-$root/build/native-logic-tests}"
mkdir -p "$build"
cpp="$root/emulator-core/src/main/cpp"
test="$root/emulator-core/src/test/cpp"
if [[ -n "${XENDROID_NDK:-}" ]]; then
    cxx=("$XENDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android29-clang++" -static)
    export TMPDIR="${TMPDIR:-/tmp}"
else
    cxx=("${CXX:-c++}")
fi
flags=(-std=c++20 -O1 -Wall -I "$cpp" -I "$cpp/xenia" -I "$cpp/xenia/src" -I "$test/stubs")
for name in presentation_runtime_test pipeline_cache_file_test vblank_pacer_test; do
    "${cxx[@]}" "${flags[@]}" "$test/$name.cc" -o "$build/$name"
done
"$build/presentation_runtime_test"
"$build/pipeline_cache_file_test" "$build/pipeline-cache-scratch"
"$build/vblank_pacer_test"
