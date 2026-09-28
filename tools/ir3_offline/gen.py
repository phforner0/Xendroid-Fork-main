#!/usr/bin/env python3
"""Generates build.ninja for a host (x86_64 Linux) build of the SPIR-V shader
translator plus the corpus tool, and the ir3stats driver.

Usage: gen.py [build directory]  (default: ./build next to this script)
Needs clang/clang++ and ninja; the translator sources come from this
repository, and version.h from an Android configure of emulator-core
(./gradlew :emulator-core:configureCMakeRelease[arm64-v8a]), or set
XENIA_GENERATED_DIR to a directory containing version.h. Sources are listed
explicitly; add more when the link reports undefined symbols.
"""
import glob, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
X = os.path.join(REPO, "emulator-core/src/main/cpp/xenia")
GEN = os.environ.get("XENIA_GENERATED_DIR") or next(iter(sorted(glob.glob(
    os.path.join(REPO, "emulator-core/.cxx/Release/*/arm64-v8a/version.h")))),
    "")
GEN = os.path.dirname(GEN) if GEN.endswith("version.h") else GEN
if not GEN or not os.path.isfile(os.path.join(GEN, "version.h")):
    sys.exit("version.h not found: configure emulator-core or set "
             "XENIA_GENERATED_DIR")
OUT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else
                      os.path.join(HERE, "build"))
os.makedirs(OUT, exist_ok=True)

srcs = [os.path.join(HERE, s) for s in ("corpus_tool.cc", "stubs.cc")] + [
    X + "/src/xenia/" + s for s in """
gpu/spirv_shader_translator.cc gpu/spirv_shader_translator_alu.cc
gpu/spirv_shader_translator_fetch.cc gpu/spirv_shader_translator_memexport.cc
gpu/spirv_shader_translator_rb.cc gpu/spirv_builder.cc gpu/shader.cc
gpu/shader_translator.cc gpu/shader_translator_disasm.cc gpu/ucode.cc
gpu/xenos.cc gpu/registers.cc gpu/register_file.cc gpu/spirv_shader.cc
base/logging.cc base/string_buffer.cc base/cvar.cc base/string.cc
base/memory.cc base/memory_posix.cc base/filesystem.cc base/filesystem_posix.cc
base/threading.cc base/threading_posix.cc base/threading_timer_queue.cc
base/mutex.cc base/clock.cc base/clock_posix.cc base/clock_x64.cc
base/debugging_posix.cc base/platform_amd64.cc base/ring_buffer.cc
base/utf8.cc
""".split()] + [X + "/third_party/" + s for s in """
fmt/src/format.cc fmt/src/os.cc
glslang/SPIRV/SpvBuilder.cpp glslang/SPIRV/InReadableOrder.cpp
glslang/SPIRV/SpvPostProcess.cpp glslang/SPIRV/Logger.cpp
""".split()]
inc = ["-I" + X, "-I" + X + "/src", "-I" + GEN,
       "-I" + X + "/third_party/glslang",
       "-I" + X + "/third_party/Vulkan-Headers/include",
       "-I" + X + "/third_party/fmt/include",
       "-I" + X + "/third_party/xxhash", "-I" + X + "/third_party/snappy",
       "-isystem", X + "/third_party"]
defs = ["-DNDEBUG", "-DUSE_CPP17", "-DVULKAN_HPP_NO_TO_STRING", "-D_LIB",
        "-D_NO_DEBUG_HEAP=1", "-DXE_HOST_CORPUS_TOOL=1"]
cflags = " ".join(inc + defs + [
    "-O1", "-g0", "-fno-strict-aliasing", "-Wno-switch", "-Wno-attributes",
    "-Wno-unknown-warning-option", "-Wno-deprecated-register",
    "-Wno-deprecated-volatile", "-Wno-deprecated-enum-enum-conversion",
    "-Wno-absolute-value", "-Wno-deprecated-literal-operator",
    "-Wno-nontrivial-memcall", "-Wno-character-conversion", "-msse4.1",
    "-mavx", "-mavx2", "-mfma", "-mf16c", "-mlzcnt", "-mbmi", "-mbmi2"])
with open(os.path.join(OUT, "build.ninja"), "w") as f:
    f.write("cxxflags = -std=gnu++20 %s\n" % cflags)
    # Header dependencies are tracked, so translator edits rebuild what uses
    # them.
    f.write("rule cxx\n  command = clang++ -MD -MF $out.d $cxxflags -c $in "
            "-o $out\n  depfile = $out.d\n  deps = gcc\n"
            "  description = CXX $in\n")
    f.write("rule link\n  command = clang++ -o $out $in $libs\n"
            "  description = LINK $out\n")
    f.write("rule glsl\n  command = glslangValidator -V -o $out $in\n"
            "  description = GLSL $in\n")
    objs = []
    for s in srcs:
        o = "obj/" + os.path.relpath(s, REPO).replace("/", "_") + ".o"
        objs.append(o)
        f.write("build %s: cxx %s\n" % (o, s))
    f.write("build corpus_tool: link %s\n  libs = -lpthread -ldl\n" %
            " ".join(objs))
    f.write("build obj/ir3stats.o: cxx %s\n  cxxflags = -std=c++17 -O1\n" %
            os.path.join(HERE, "ir3stats.cc"))
    f.write("build ir3stats: link obj/ir3stats.o\n  libs = -lvulkan\n")
    for s in ("partner.vert", "partner.frag"):
        f.write("build %s.spv: glsl %s\n" % (s, os.path.join(HERE, s)))
print("%s: %d translator sources, version.h from %s" %
      (os.path.join(OUT, "build.ninja"), len(srcs), GEN))
