// The changed-settings lines (xe_changed_settings.h): only values away from the
// core's defaults, sorted, without profiles, storage paths or transient hand-offs.

#include <cstdio>
#include <string>
#include <vector>

#include "xe_changed_settings.h"

namespace {

int failures = 0;

void expect(bool ok, const char* what) {
  if (!ok) {
    std::fprintf(stderr, "FAILED: %s\n", what);
    ++failures;
  }
}

xendroid::CvarSnapshot cvar(const char* category, const char* name, const char* value,
                            const char* default_value, bool game = false,
                            bool transient = false) {
  return {category, name, value, default_value, transient, game};
}

}  // namespace

int main() {
  auto lines = xendroid::ChangedSettingLines({
      cvar("GPU", "framerate_limit", "30", "60", true),
      cvar("Display", "postprocess_scaling_and_sharpening", "\"fsr\"", "\"bilinear\""),
      cvar("GPU", "anisotropic_override", "-1", "-1"),               // default: left out
      cvar("Profiles", "logged_profile_slot_0_xuid", "\"E0300000ABCDEF01\"", "\"\""),
      cvar("Storage", "content_root", "\"/sdcard/x\"", "\"content\""),
      cvar("HID", "slot_bindings_passthrough", "\"1,2\"", "\"\"", false, true),
      cvar("Vulkan", "vulkan_lib_path", "\"/data/user/0/app/drivers/turnip.so\"", "\"\""),
  });
  for (const auto& line : lines) std::printf("  %s\n", line.c_str());
  expect(lines.size() == 3, "three settings away from their defaults are listed");
  expect(lines.size() == 3 && lines[0] == "Display|postprocess_scaling_and_sharpening = \"fsr\" (default \"bilinear\")",
         "sorted by section, with the default");
  expect(lines.size() == 3 && lines[1] == "GPU|framerate_limit = 30 (default 60) · this game",
         "the game's own config is marked");
  expect(lines.size() == 3 && lines[2] == "Vulkan|vulkan_lib_path = (a path)",
         "a path only says it is set");
  for (const auto& line : lines) {
    expect(line.find("E0300000") == std::string::npos, "no profile XUID");
    expect(line.find("/sdcard") == std::string::npos, "no storage path");
    expect(line.find("slot_bindings") == std::string::npos, "no transient hand-off");
  }

  std::vector<xendroid::CvarSnapshot> many;
  for (int i = 0; i < 5; ++i) many.push_back(cvar("A", ("n" + std::to_string(i)).c_str(), "1", "0"));
  auto capped = xendroid::ChangedSettingLines(many, 3);
  expect(capped.size() == 4 && capped[3] == "(more settings changed, not listed)",
         "a long list is capped and says so");

  auto clipped = xendroid::ChangedSettingLines({cvar("A", "b", std::string(200, 'x').c_str(), "\"\"")});
  expect(clipped.size() == 1 && clipped[0].size() < 120, "a long value is cut");

  if (failures) {
    std::fprintf(stderr, "changed_settings_test: %d failure(s)\n", failures);
    return 1;
  }
  std::printf("changed settings: passed\n");
  return 0;
}
