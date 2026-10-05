#pragma once
#include <algorithm>
#include <atomic>
#include <cstdint>
#include "xenia/apu/apu_flags.h"
namespace ae {
inline std::atomic<int>& SessionVolumeOverride() { static std::atomic<int> volume{-1}; return volume; }
inline int EffectiveVolume() {
  const int override = SessionVolumeOverride().load(std::memory_order_relaxed);
  return override >= 0 ? override : int(std::min<uint32_t>(cvars::volume, 100));
}
inline void SetSessionVolume(int value) { SessionVolumeOverride().store(std::clamp(value, 0, 100), std::memory_order_relaxed); }

// Process-wide audio counters for run summaries (C02). Only grow; readers take
// deltas. A concealed block is one the emulator had not produced in time (an
// underrun the player hears); device xruns are the output stream's own.
// The stream is paused with the guest, so paused time counts neither.
struct RunAudioStats {
  std::atomic<int> backend{0};  // 0 no output yet, 1 AAudio, 2 OpenSL ES
  std::atomic<uint64_t> blocks{0};
  std::atomic<uint64_t> concealed{0};
  std::atomic<uint64_t> device_xruns{0};
};
inline RunAudioStats& RunStats() { static RunAudioStats stats; return stats; }
}
