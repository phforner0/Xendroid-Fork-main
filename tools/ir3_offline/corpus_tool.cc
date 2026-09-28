// Host corpus tool: translates every guest shader of a shader storage (.xsh)
// to SPIR-V with the given cvar overrides, for spirv-val and offline ir3
// compilation. Usage:
//   corpus_tool <TITLEID.xsh> <output-directory> <variant> [cvar=value ...]
// Writes ps_<hash>-<variant>.spv and vs_<hash>-<variant>.spv. Pixel shaders
// use the no-alpha-test variant (spirv_specialize_no_alpha) when they write
// color 0, all interpolators and their written color targets.
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <string>
#include <vector>

#include "third_party/tomlplusplus/toml.hpp"
#include "xenia/base/cvar.h"
#include "xenia/base/logging.h"
#include "xenia/base/string_buffer.h"
#include "xenia/gpu/shader_storage.h"
#include "xenia/gpu/spirv_shader_translator.h"

int main(int argc, char** argv) {
  if (argc < 4) {
    std::fprintf(stderr,
                 "Usage: %s <shader-cache.xsh> <output-directory> <variant> "
                 "[cvar=value ...]\n",
                 argv[0]);
    return 2;
  }
  for (int i = 4; i < argc; ++i) {
    std::string arg = argv[i];
    size_t eq = arg.find('=');
    if (eq == std::string::npos || !cvar::ConfigVars) {
      std::fprintf(stderr, "Bad cvar argument %s\n", argv[i]);
      return 2;
    }
    std::string name = arg.substr(0, eq);
    auto it = cvar::ConfigVars->find(name);
    if (it == cvar::ConfigVars->end()) {
      std::fprintf(stderr, "Unknown cvar %s\n", name.c_str());
      return 2;
    }
    toml::table table = toml::parse("v = " + arg.substr(eq + 1));
    it->second->LoadConfigValue(table.get("v"));
  }
  FILE* input = std::fopen(argv[1], "rb");
  xe::gpu::ShaderStorageFileHeader header;
  if (!input || !xe::gpu::ValidateShaderStorageHeader(input, header)) {
    if (input) std::fclose(input);
    std::fprintf(stderr, "Invalid shader cache\n");
    return 2;
  }
  const std::filesystem::path output(argv[2]);
  const std::string variant = argv[3];
  std::filesystem::create_directories(output);
  xe::InitializeLogging("corpus-tool");
  using Translator = xe::gpu::SpirvShaderTranslator;
  using Mode = Translator::Modification::DepthStencilMode;
  // Roughly Turnip on Adreno: no fragment shader interlock or barycentrics,
  // SPIR-V 1.0 (the spirv_version_override default).
  Translator::Features features(true);
  features.spirv_version = spv::Spv_1_0;
  features.fragment_shader_sample_interlock = false;
  // Turnip exposes no VK_KHR_fragment_shader_barycentric, so
  // precise_interpolation is not emitted on the device.
  features.fragment_shader_barycentric = false;
  features.max_storage_buffer_range = 128 * 1024 * 1024;
  Translator translator(features, true, true, false);
  unsigned translated[2] = {}, failed = 0;
  xe::gpu::ReadShaderEntries(
      input, [&](xe::gpu::xenos::ShaderType type, const uint32_t* ucode,
                 uint32_t count, uint64_t hash) {
        const bool is_pixel = type == xe::gpu::xenos::ShaderType::kPixel;
        xe::gpu::Shader shader(type, hash, ucode, count);
        xe::StringBuffer disassembly;
        shader.AnalyzeUcode(disassembly);
        Translator::Modification mod;
        if (is_pixel) {
          mod = Translator::Modification(
              translator.GetDefaultPixelShaderModification(
                  xe::gpu::xenos::kMaxShaderTempRegisters));
          mod.pixel.interpolator_mask = 0xFFFF;
          mod.pixel.color_targets_used = shader.writes_color_targets();
          mod.pixel.depth_stencil_mode = shader.writes_color_target(0)
                                             ? Mode::kNoAlphaTests
                                             : Mode::kNoModifiers;
        } else {
          mod = Translator::Modification(
              translator.GetDefaultVertexShaderModification(
                  xe::gpu::xenos::kMaxShaderTempRegisters));
          mod.vertex.interpolator_mask = 0xFFFF;
        }
        auto* translation = shader.GetOrCreateTranslation(mod.value);
        if (!translator.TranslateAnalyzedShader(*translation) ||
            !translation->is_valid()) {
          std::fprintf(stderr, "Translation failed: %016llX\n",
                       static_cast<unsigned long long>(hash));
          ++failed;
          return true;
        }
        const auto& binary = translation->translated_binary();
        char name[96];
        std::snprintf(name, sizeof(name), "%s_%016llX-%s.spv",
                      is_pixel ? "ps" : "vs",
                      static_cast<unsigned long long>(hash), variant.c_str());
        FILE* file = std::fopen((output / name).c_str(), "wb");
        if (!file ||
            std::fwrite(binary.data(), 1, binary.size(), file) !=
                binary.size()) {
          if (file) std::fclose(file);
          ++failed;
          return false;
        }
        std::fclose(file);
        ++translated[is_pixel];
        return true;
      });
  std::fclose(input);
  std::printf("variant=%s vertex=%u pixel=%u failed=%u\n", variant.c_str(),
              translated[0], translated[1], failed);
  xe::ShutdownLogging();
  return failed ? 1 : 0;
}
