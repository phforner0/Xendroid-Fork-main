// Translate real cached guest shaders through the generic and no-alpha paths.
// Run on Android: xendroid-shader-regression <TITLEID.xsh> <output-directory>
// Then run spirv-val on the emitted .spv files on the build host.
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <vector>

#include "xenia/base/logging.h"
#include "xenia/base/string_buffer.h"
#include "xenia/gpu/shader_storage.h"
#include "xenia/gpu/spirv_shader_translator.h"

namespace {
struct Interface {
  unsigned sample_mask_outputs = 0;
  unsigned early_tests = 0;
  unsigned depth_replacing = 0;
  unsigned kills = 0;
};

bool Inspect(const std::vector<uint8_t>& binary, Interface& out) {
  if (binary.size() < 20 || binary.size() % 4) {
    return false;
  }
  std::vector<uint32_t> words(binary.size() / 4);
  std::memcpy(words.data(), binary.data(), binary.size());
  if (words[0] != spv::MagicNumber) {
    return false;
  }
  for (size_t i = 5; i < words.size();) {
    const uint32_t length = words[i] >> 16;
    const uint32_t op = words[i] & 0xFFFF;
    if (!length || length > words.size() - i) {
      return false;
    }
    if (op == spv::OpDecorate && length == 4 &&
        words[i + 2] == spv::DecorationBuiltIn &&
        words[i + 3] == uint32_t(spv::BuiltIn::SampleMask)) {
      ++out.sample_mask_outputs;  // FBO has no sample-mask input.
    }
    if (op == spv::OpExecutionMode && length >= 3) {
      out.early_tests += words[i + 2] == spv::ExecutionModeEarlyFragmentTests;
      out.depth_replacing += words[i + 2] == spv::ExecutionModeDepthReplacing;
    }
    out.kills += op == spv::OpKill || op == spv::OpDemoteToHelperInvocationEXT;
    i += length;
  }
  return true;
}
}  // namespace

int main(int argc, char** argv) {
  if (argc != 3) {
    std::fprintf(stderr, "Usage: %s <shader-cache.xsh> <output-directory>\n",
                 argv[0]);
    return 2;
  }
  FILE* input = std::fopen(argv[1], "rb");
  xe::gpu::ShaderStorageFileHeader header;
  if (!input || !xe::gpu::ValidateShaderStorageHeader(input, header)) {
    if (input) std::fclose(input);
    std::fprintf(stderr, "Invalid shader cache\n");
    return 2;
  }
  const std::filesystem::path output(argv[2]);
  std::filesystem::create_directories(output);
  xe::InitializeLogging("spirv-alpha-regression");
  using Translator = xe::gpu::SpirvShaderTranslator;
  using Mode = Translator::Modification::DepthStencilMode;
  Translator::Features features(false);
  Translator translator(features, true, true, false);
  unsigned tested = 0, with_kill = 0, with_depth = 0, failed = 0;
  size_t generic_bytes = 0, specialized_bytes = 0;
  const uint64_t valid_bytes = xe::gpu::ReadShaderEntries(
      input, [&](xe::gpu::xenos::ShaderType type, const uint32_t* ucode,
                 uint32_t count, uint64_t hash) {
        if (type != xe::gpu::xenos::ShaderType::kPixel) return true;
        xe::gpu::Shader shader(type, hash, ucode, count);
        xe::StringBuffer disassembly;
        shader.AnalyzeUcode(disassembly);
        if (!shader.writes_color_target(0)) return true;
        Interface interfaces[2];
        size_t sizes[2] = {};
        for (unsigned variant = 0; variant < 2; ++variant) {
          Translator::Modification mod(
              translator.GetDefaultPixelShaderModification(
                  xe::gpu::xenos::kMaxShaderTempRegisters));
          mod.pixel.depth_stencil_mode =
              variant ? Mode::kNoAlphaTests : Mode::kNoModifiers;
          auto* translation = shader.GetOrCreateTranslation(mod.value);
          if (!translator.TranslateAnalyzedShader(*translation) ||
              !translation->is_valid() ||
              !Inspect(translation->translated_binary(), interfaces[variant])) {
            std::fprintf(stderr, "Translation failed: %016llX variant=%u\n",
                         static_cast<unsigned long long>(hash), variant);
            ++failed;
            return true;
          }
          const auto& binary = translation->translated_binary();
          sizes[variant] = binary.size();
          char name[64];
          std::snprintf(name, sizeof(name), "%016llX-%s.spv",
                        static_cast<unsigned long long>(hash),
                        variant ? "opaque" : "generic");
          FILE* file = std::fopen((output / name).c_str(), "wb");
          if (!file) {
            ++failed;
            return false;
          }
          const bool written = std::fwrite(binary.data(), 1, binary.size(),
                                           file) == binary.size();
          const bool closed = std::fclose(file) == 0;
          if (!written || !closed) {
            ++failed;
            return false;
          }
        }
        // Specialization must remove coverage output and the epilogue's kill,
        // without changing depth execution order or removing a guest kill.
        const auto& generic = interfaces[0];
        const auto& opaque = interfaces[1];
        if (generic.sample_mask_outputs != 1 || opaque.sample_mask_outputs ||
            opaque.early_tests != generic.early_tests || opaque.early_tests ||
            opaque.depth_replacing != generic.depth_replacing ||
            (!shader.kills_pixels() && opaque.kills) ||
            (shader.kills_pixels() && !opaque.kills) || sizes[1] >= sizes[0]) {
          std::fprintf(stderr, "Interface/size regression: %016llX\n",
                       static_cast<unsigned long long>(hash));
          ++failed;
        }
        ++tested;
        with_kill += shader.kills_pixels();
        with_depth += shader.writes_depth();
        generic_bytes += sizes[0];
        specialized_bytes += sizes[1];
        return true;
      });
  std::fclose(input);
  if (valid_bytes != std::filesystem::file_size(argv[1])) ++failed;
  std::printf(
      "pixel_shaders=%u guest_kill=%u guest_depth=%u failed=%u "
      "generic_bytes=%zu specialized_bytes=%zu\n",
      tested, with_kill, with_depth, failed, generic_bytes, specialized_bytes);
  xe::ShutdownLogging();
  return tested && !failed ? 0 : 1;
}
