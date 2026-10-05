#pragma once
// The settings a run used that differ from the core's own defaults (global config,
// then the game's config, then the command line), as one line each: written at the
// head of the session log and kept with the run record, so a report says what ran
// without anyone having to send their config files.
//
// Pure (no cvar types): xe_changed_settings_cvars.h snapshots cvar::ConfigVars into
// CvarSnapshot values; emulator-core/src/test/cpp/changed_settings_test.cc tests it.

#include <algorithm>
#include <string>
#include <utility>
#include <vector>

namespace xendroid {

struct CvarSnapshot {
  std::string category;
  std::string name;
  std::string value;          // as the core renders it (strings quoted)
  std::string default_value;  // likewise
  bool transient = false;     // internal hand-offs, never a user's choice
  bool from_game_config = false;
};

// Sections whose values identify the user or the device's files: profile XUIDs and
// storage locations. Left out of the list entirely.
inline bool IsPrivateSettingCategory(const std::string& category) {
  return category == "Profiles" || category == "Storage";
}

inline bool LooksLikePath(const std::string& value) {
  return value.find('/') != std::string::npos ||
         value.find('\\') != std::string::npos;
}

// "GPU|framerate_limit = 30 (default 60) · this game", sorted by section and name.
// A path-like value only says that it is set; long values are cut.
inline std::vector<std::string> ChangedSettingLines(
    std::vector<CvarSnapshot> cvars, size_t max_lines = 200) {
  constexpr size_t kMaxValue = 80;
  auto clip = [](const std::string& text) {
    return text.size() <= kMaxValue ? text : text.substr(0, kMaxValue) + "...";
  };
  std::sort(cvars.begin(), cvars.end(),
            [](const CvarSnapshot& a, const CvarSnapshot& b) {
              return a.category != b.category ? a.category < b.category
                                              : a.name < b.name;
            });
  std::vector<std::string> lines;
  for (const CvarSnapshot& cvar : cvars) {
    if (cvar.transient || cvar.value == cvar.default_value ||
        IsPrivateSettingCategory(cvar.category)) {
      continue;
    }
    if (lines.size() == max_lines) {
      lines.push_back("(more settings changed, not listed)");
      break;
    }
    std::string line = cvar.category + "|" + cvar.name + " = ";
    if (LooksLikePath(cvar.value)) {
      line += "(a path)";
    } else {
      line += clip(cvar.value);
      if (!LooksLikePath(cvar.default_value)) {
        line += " (default " + clip(cvar.default_value) + ")";
      }
    }
    if (cvar.from_game_config) line += " · this game";
    lines.push_back(std::move(line));
  }
  return lines;
}

}  // namespace xendroid
