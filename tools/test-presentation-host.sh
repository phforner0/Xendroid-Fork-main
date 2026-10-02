#!/usr/bin/env bash
# Real compute synthesis/readback on software Vulkan; no phone or packaged DLL.
set -euo pipefail
ulimit -c 0
root="$(realpath "$(dirname "${BASH_SOURCE[0]}")/..")"
build="${XENDROID_HOST_TEST_BUILD:-$root/build/presentation-host-tests}"
mkdir -p "$build"
trap 'rm -f "$build/private-lsfg.cache"' EXIT
if [[ -n "${XENDROID_CXX_WRAPPER:-}" ]]; then cxx=(bash "$XENDROID_CXX_WRAPPER"); else cxx=("${CXX:-c++}" -std=c++20); fi
cpp="$root/emulator-core/src/main/cpp"
test="$root/emulator-core/src/test/cpp"
vk_library="${XENDROID_VULKAN_LIBRARY:--lvulkan}"
common=(-O1 -I "$cpp" -I "$cpp/xenia/src" -I "$cpp/xenia/third_party/Vulkan-Headers/include" -I "$test/stubs")
"${cxx[@]}" "${common[@]}" "$test/presentation_runtime_test.cc" -o "$build/policy-test"
"$build/policy-test"
"${cxx[@]}" "${common[@]}" "$test/winfg_software_test.cc" "$cpp/third_party/winfg/src/framegen.cpp" "$vk_library" -o "$build/winfg-test"
"$build/winfg-test"
# Motion (F09): multi-scale texture panning at 256 px, 4 consecutive pairs, the presenter's
# model 3. Args: preset shift model pairs pattern size. A still image must come out unchanged;
# moving content must be estimated better than by repeating either source frame.
"${cxx[@]}" "${common[@]}" -DTEST_MOTION "$test/winfg_software_test.cc" "$cpp/third_party/winfg/src/framegen.cpp" "$vk_library" -o "$build/winfg-motion-test"
for run in "2 0 3 4 2 64" "2 2 3 4 2 256" "2 8 3 4 2 256" "0 8 3 4 2 256"; do
    # shellcheck disable=SC2086
    "$build/winfg-motion-test" $run | grep "Motion half-way"
done
"${GLSLANG:-glslangValidator}" -V --target-env vulkan1.0 "$cpp/xenia/src/xenia/ui/vulkan/shaders/xendroid_color_filter.comp" -o "$build/color.spv"
"${cxx[@]}" "${common[@]}" -DTEST_COLOR "$test/winfg_software_test.cc" "$vk_library" -o "$build/color-test"
for mode in 1 2 3; do "$build/color-test" "$build/color.spv" "$mode"; done
dxbc="$cpp/third_party/lsfg-dxbc"
dxbc_includes=(-I "$dxbc/include/dxbc" -I "$dxbc/include/spirv" -I "$dxbc/include/util" -I "$dxbc/include/dxvk")
dxbc_sources=("$dxbc"/src/dxbc/*.cpp "$dxbc"/src/spirv/*.cpp "$dxbc"/src/util/*.cpp)
lsfg="$cpp/third_party/lsfg"
"${cxx[@]}" "${common[@]}" "${dxbc_includes[@]}" "$test/lsfg_dll_test.cc" "$lsfg/lsfg_dll.cpp" "$lsfg/lsfg_dxbc.cpp" "${dxbc_sources[@]}" -o "$build/lsfg-parser-test"
if [[ -n "${XENDROID_LSFG_DLL:-}" ]]; then
    "$build/lsfg-parser-test" "$XENDROID_LSFG_DLL" "$build/private-lsfg.cache"
    lsfg_sources=()
    for name in alpha beta gamma delta common mipmaps generate shaders chain engine pacer governor dll dxbc vkd; do lsfg_sources+=("$lsfg/lsfg_$name.cpp"); done
    "${cxx[@]}" "${common[@]}" "${dxbc_includes[@]}" -DTEST_LSFG "$test/winfg_software_test.cc" "${lsfg_sources[@]}" "${dxbc_sources[@]}" "$vk_library" -o "$build/lsfg-test"
    for multiplier in 2 3 4; do "$build/lsfg-test" "$build/private-lsfg.cache" "$multiplier"; done
    rm -f "$build/private-lsfg.cache" # proprietary runtime data is not a test artifact
else
    "$build/lsfg-parser-test"
    printf '%s\n' 'LSFG synthesis not run: provide your own DLL with XENDROID_LSFG_DLL.'
fi
