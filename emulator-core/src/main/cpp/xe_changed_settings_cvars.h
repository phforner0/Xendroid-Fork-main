#pragma once
// Snapshots every config cvar for xe_changed_settings.h, once per run: right after
// the game's config (and its quirks) is applied, before the guest's threads exist.
// Later readers get that copy, never the live cvars: some are overridden at run time
// on other threads (readback_resolve, occlusion_query), and copying a std::string
// while another thread assigns it is a crash.

#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include "xe_changed_settings.h"
#include "xenia/base/cvar.h"

namespace xendroid {

inline std::vector<std::string> CollectChangedSettings() {
  std::vector<CvarSnapshot> snapshots;
  if (cvar::ConfigVars) {
    snapshots.reserve(cvar::ConfigVars->size());
    for (const auto& [name, var] : *cvar::ConfigVars) {
      // Forced by the app on every Android launch (xendroid_emu.cpp), not a choice.
      if (name == "host_present_from_non_ui_thread") continue;
      snapshots.push_back({var->category(), var->name(), var->effective_value(),
                           var->default_value(), var->is_transient(),
                           var->from_game_config()});
    }
  }
  return ChangedSettingLines(std::move(snapshots));
}

inline std::mutex boot_settings_mutex;
inline std::vector<std::string> boot_settings;

// EmulatorApp::OnInitialize, once the config is final for this run.
inline std::vector<std::string> RecordBootSettings() {
  std::vector<std::string> lines = CollectChangedSettings();
  std::lock_guard<std::mutex> lock(boot_settings_mutex);
  boot_settings = lines;
  return lines;
}

// The list as this run booted; empty before OnInitialize recorded it.
inline std::vector<std::string> BootSettings() {
  std::lock_guard<std::mutex> lock(boot_settings_mutex);
  return boot_settings;
}

}  // namespace xendroid
