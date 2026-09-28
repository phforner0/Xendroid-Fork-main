// Stubs for symbols whose real definitions pull in the rest of the emulator.
#include <cstdint>
#include <string_view>

#include "xenia/base/console.h"
#include "xenia/base/cvar.h"
#include "xenia/base/system.h"

DEFINE_bool(precise_interpolation, true, "stub", "GPU");
DEFINE_path(dump_shaders, "", "stub", "GPU");
DEFINE_bool(draw_resolution_scaled_texture_offsets, true, "stub", "GPU");
DEFINE_bool(texture_gradient_exp_bias, false, "stub", "GPU");
DEFINE_bool(texture_integer_num_format, false, "stub", "GPU");

namespace xe {
bool has_console_attached() { return true; }
void AttachConsole() {}
void ShowSimpleMessageBox(SimpleMessageBoxType, std::string_view) {}
namespace gpu {
namespace draw_util {
extern const int8_t kD3D10StandardSamplePositions2x[2][2] = {{4, 4},
                                                             {-4, -4}};
extern const int8_t kD3D10StandardSamplePositions4x[4][2] = {
    {-2, -6}, {6, -2}, {-6, 2}, {2, 6}};
}  // namespace draw_util
}  // namespace gpu
}  // namespace xe
