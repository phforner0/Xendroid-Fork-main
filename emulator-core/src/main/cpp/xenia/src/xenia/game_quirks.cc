// SPDX-License-Identifier: WTFPL
#include "xenia/game_quirks.h"

#include <array>
#include <string>
#include <variant>

#include "xenia/base/cvar.h"
#include "xenia/base/logging.h"

namespace xe {
namespace game_quirks {

using QuirkValue = std::variant<bool, int64_t, double, const char*>;

struct Quirk {
  uint32_t title_id;
  const char* cvar;
  QuirkValue value;
  const char* note;
};

// Entries apply at per-game-config priority, so a user's own per-game config
// still overrides them.
static const Quirk kQuirks[] = {
    // Ninja Gaiden 2: the opening FMV renders as three time-skewed vertical
    // slices when the restore half of the saverest inline is active. Every
    // emitted expansion verifies correct, so the current reading is a latent
    // guest-side race between the slice decode workers that the faster
    // epilogues expose. Keep the save half inlined, call the restores.
    {0x544307D5, "inline_gprlr_saverest_parts", int64_t(1),
     "FMV slice desync with inlined restores"},
    {0x4D5307D1, "spirv_multiply_zero_test_on_bits", true,
     "ir3 cannot compile fmadz"},
    {0x494707D4, "vulkan_in_pass_resolve", true, "in-pass resolves tuned here"},
    {0x49470804, "vulkan_in_pass_resolve", true, "in-pass resolves tuned here"},
    {0x494707D4, "network_enabled", false, "hangs on a blocking recvfrom"},
    {0x49470804, "network_enabled", false, "hangs on a blocking recvfrom"},
    {0x4E4D083A, "spirv_multiply_zero_test_on_bits", true,
     "ir3 cannot compile fmadz"},
    // Forza Horizon: both exact (the same image for every input); on the POCO
    // F7 (Adreno 825) together +3.0% fps, -3.7% GPU time (AB5, 2026-09-29).
    {0x4D5309C9, "spirv_texture_sign_branch", true,
     "exact texture sign decode in a uniform branch"},
    {0x4D5309C9, "spirv_fast_precision_rounding", true,
     "exact cheaper 21-bit rounding"},
    // Exact too: ~10 full-screen textures reloaded per frame skip the copy from
    // a buffer; +4.5% fps, -5.4% GPU time on the POCO F7 (2026-09-29).
    {0x4D5309C9, "vulkan_texture_load_to_image", true,
     "texture loads straight into the image"},
    // Exact: the resolves store into the textures read back from them, whose
    // uploads are then skipped (3.0 -> 0.75 ms per frame, GPU -1.4 ms, +1.4%
    // fps with the POCO F7 cool; 2026-09-30).
    {0x4D5309C9, "vulkan_direct_host_resolve_to_texture", true,
     "resolves store straight into their textures"},
    // Main pass -7% (AB5). No visible difference parked, over a 45 s drive
    // and in the pause menu (2026-09-30); night, rain and tunnels unchecked.
    {0x4D5309C9, "spirv_ps_relaxed_math", int64_t(3),
     "no SM3 zero-multiply or 21-bit rounding emulation in pixel shaders"},
    // 55% of the main pass draws use alpha to coverage: main pass -7.5%, the
    // same foliage parked and driving (only the dither pattern differs).
    {0x4D5309C9, "host_alpha_to_coverage", true,
     "alpha to coverage by the host's fixed function"},
    // The shadow atlas is cleared by 4x depth-only quads read back as 1x: no
    // more 1x <-> 4x transfers of it, GPU time -1.1 ms (-3.4%), the same
    // shadows (depth at the double-resolution pixel centers instead of the 4x
    // sample positions; 2026-09-30).
    {0x4D5309C9, "vulkan_depth_4x_as_1x", true,
     "4x depth-only draws into the 1x surface of their samples"},
    // The Direct3D wait for the GPU (only the code with this signature - its
    // first 16 instructions - so another build is left alone) sleeps until
    // the command processor makes progress instead of spinning: Guest CPU 0
    // 97% -> 37% of a core, the same fps (AB3, AB5).
    {0x4D5309C9, "spin_park_guest_functions", "829F04A8:B864F65007F969C0",
     "the Direct3D GPU wait parks instead of spinning"},
    {0x4D5309C9, "spin_park_mode", int64_t(1),
     "the Direct3D GPU wait parks instead of spinning"},
    // Forza Horizon never reads the 7e3 alpha: +3.1% fps, -3.5% GPU time
    // (AB3), the same image in motion (AB5).
    {0x4D5309C9, "render_target_7e3_as_r11g11b10", true,
     "7e3 scene color in 32 bpp"},
    // The clear of a resolve inside the game's own render pass: with the
    // draw barriers moved to the resolve, render passes 205 -> ~133 per frame
    // (AB8), the same GPU time at the 30 fps cap (S18, 2026-10-01). Every
    // visual check since AB7 ran with it.
    {0x4D5309C9, "vulkan_resolve_clear_in_guest_pass", true,
     "resolve clears inside the game's render pass"},
    // Turnip's early preamble costs every draw ~1.6 us of GPU time when the
    // draws are small (one-primitive main pass draws 2.8 -> 1.2 us, S20); off,
    // GPU time -0.8 ms (-2.4%) per frame at the 30 fps cap (S21, 2026-10-01).
    // Only Turnip reads it; the pipeline cache file is separate per flags.
    {0x4D5309C9, "ir3_debug", "noearlypreamble",
     "no early shader preamble in the Turnip compiler"},
    // The EDRAM is reused by targets that start with a clear quad: the
    // transfers it overwrites are skipped - 11 of 49 transfers and 3.7 of 12.5
    // thousand tiles per frame, GPU time -1.1 ms (-3.1%) at the 30 fps cap,
    // the same image parked and over a drive (S24, 2026-10-01).
    {0x4D5309C9, "skip_overwritten_transfers", true,
     "no transfers into what the draw overwrites"},
    // The 1280x720 lighting marks stencil at 640x360 4x (quads with texture-
    // less pixel shaders) between 1x passes: drawn into the 1x surface of
    // their samples, transfers 8071 -> 3703 tiles a frame, GPU time -1.7 ms
    // (-5.2%) at the 30 fps cap, the same image parked (S28, 2026-10-01).
    {0x4D5309C9, "vulkan_samples_as_pixels_simple_ps", true,
     "4x stencil marking with simple pixel shaders into the 1x surface"},
    // Transfers a clear quad covers in part (whole rows of tiles claimed, less
    // drawn) copy only the rest: 9 a frame, GPU time -0.1 to -0.2 ms at the
    // 30 fps cap, the same image parked (S37, 2026-10-01).
    {0x4D5309C9, "skip_overwritten_transfers_cutout", true,
     "transfers skip the part the draw overwrites"},
    // Exact: the texture signs (mostly gamma) as specialization constants of
    // the pixel shader pipelines, no runtime branches around the samples - GPU
    // time -1.9 ms (-7.9%) at the 30 fps cap, both restart A/B pairs within
    // 0.1 ms, the same image parked (S40, 2026-10-01). The first launch after
    // the change compiles the pixel shader pipelines again.
    {0x4D5309C9, "spirv_texture_sign_specialization", true,
     "texture signs known to the host compiler per pipeline"},
};

// Same path/priority as a per-game config file.
static bool ApplyOne(const Quirk& quirk) {
  if (!cvar::ConfigVars) {
    return false;
  }
  auto it = cvar::ConfigVars->find(quirk.cvar);
  if (it == cvar::ConfigVars->end()) {
    XELOGW("Game quirk for {:08X} references unknown cvar '{}'", quirk.title_id,
           quirk.cvar);
    return false;
  }
  auto* config_var = static_cast<cvar::IConfigVar*>(it->second);
  std::visit(
      [config_var](auto&& value) {
        using V = std::decay_t<decltype(value)>;
        if constexpr (std::is_same_v<V, const char*>) {
          toml::value<std::string> node{std::string(value)};
          config_var->LoadGameConfigValue(&node);
        } else {
          toml::value<V> node{value};
          config_var->LoadGameConfigValue(&node);
        }
      },
      quirk.value);
  return true;
}

size_t Apply(uint32_t title_id) {
  size_t applied = 0;
  for (const auto& quirk : kQuirks) {
    if (quirk.title_id != title_id) {
      continue;
    }
    if (ApplyOne(quirk)) {
      XELOGI("Game quirk for {:08X}: {} ({})", title_id, quirk.cvar,
             quirk.note);
      ++applied;
    }
  }
  return applied;
}

}  // namespace game_quirks
}  // namespace xe
