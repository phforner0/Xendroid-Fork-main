#ifndef XENIA_UI_PRESENTATION_RUNTIME_H_
#define XENIA_UI_PRESENTATION_RUNTIME_H_

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <mutex>
#include <string>

namespace xe::ui {
enum class DisplayMode : int { kDefault = -1, kFit = 0, kFill = 1, kStretch = 2, kInteger = 3 };
enum class FrameGenerationState : int { kOff, kWarmingUp, kRunning, kUnsupported, kFailed };

struct PresentationRuntime {
  std::atomic<int> display_mode{-1};
  std::atomic<bool> frame_generation_requested{false};
  std::atomic<int> frame_generation_preset{2};
  std::atomic<int> frame_generation_state{0};
  std::atomic<int> frame_generation_error{0};
  std::atomic<int> frame_generation_engine{0}; // 0 Win-FG, 1 LSFG
  std::atomic<int> frame_generation_multiplier{2};
  std::atomic<uint64_t> configuration_epoch{0};
  std::atomic<int> scaling_effect{-1}; // inherited / bilinear / CAS / FSR
  std::atomic<int> color_filter{0};
  std::atomic<int> color_filter_error{0};
  std::atomic<int> presenter_tid{0};
  std::atomic<int64_t> presenter_work_ns{0};
  std::atomic<uint64_t> presenter_work_sequence{0};
  std::mutex configuration_mutex;
  std::string lsfg_cache;
  std::string gpu_label;
  // "key=value;..." identity of the driver in use (vendor, device, driver version,
  // API, driverID/name/info, pipelineCacheUUID), set when the presenter starts.
  std::string driver_identity;
  std::atomic<float> display_hz{60.0f};
  std::atomic<uint64_t> generated_submissions{0};
  std::atomic<uint64_t> dropped_guest_notifications{0};
  // Synthetic outputs not painted because the presenter was already past their
  // slot: they would have been presented back-to-back with the next output.
  std::atomic<uint64_t> late_synthetic_skips{0};
  std::atomic<double> generation_gpu_ms{-1.0};
};
inline PresentationRuntime& RuntimePresentation() {
  static PresentationRuntime runtime;
  return runtime;
}

struct OutputRectangle { int32_t x, y; uint32_t width, height; };
inline OutputRectangle CalculateOutputRectangle(DisplayMode mode, uint32_t source_width,
    uint32_t source_height, uint32_t aspect_x, uint32_t aspect_y,
    uint32_t host_width, uint32_t host_height) {
  if (!source_width || !source_height || !aspect_x || !aspect_y || !host_width || !host_height) return {};
  if (mode == DisplayMode::kStretch) return {0, 0, host_width, host_height};
  double w = host_width;
  double h = w * aspect_y / aspect_x;
  if ((mode == DisplayMode::kFill && h < host_height) ||
      (mode != DisplayMode::kFill && h > host_height)) {
    h = host_height; w = h * aspect_x / aspect_y;
  }
  if (mode == DisplayMode::kInteger) {
    const uint32_t factor = std::min(host_width / source_width, host_height / source_height);
    if (factor) { w = double(source_width) * factor; h = double(source_height) * factor; }
  }
  const uint32_t width = std::max(1u, uint32_t(std::llround(w)));
  const uint32_t height = std::max(1u, uint32_t(std::llround(h)));
  return {int32_t((int64_t(host_width) - width) / 2),
          int32_t((int64_t(host_height) - height) / 2), width, height};
}

// Pure cadence estimator used by the worker and host tests. Never extrapolates an
// unbounded present queue; a long pause resets history rather than generating stale frames.
class FrameCadence {
 public:
  int64_t Observe(int64_t now_ns) {
    if (last_ns_ && now_ns > last_ns_) {
      const int64_t delta = now_ns - last_ns_;
      if (delta > 250000000) period_ns_ = 33333333;
      else if (delta >= 4000000) period_ns_ = (period_ns_ * 3 + delta) / 4;
    }
    last_ns_ = now_ns;
    return std::clamp<int64_t>(period_ns_ / 2, 2000000, 50000000);
  }
  bool FitsRefresh(float hz, int multiplier = 2) const {
    return hz > 0 && multiplier >= 2 && double(period_ns_) * hz >= 975000000.0 * multiplier;
  }
  void Reset() { last_ns_ = 0; period_ns_ = 33333333; }
  int64_t period_ns() const { return period_ns_; }
 private:
  int64_t last_ns_ = 0;
  int64_t period_ns_ = 33333333;
};

// Decisions of the frame-generation presenter thread, free of Vulkan and of the
// real clock so the host tests can drive them. One cycle per processed source
// frame: outputs 1..multiplier-1 are synthetic, output `multiplier` is the real
// frame, spaced by period/multiplier from the moment the cycle starts. Only the
// newest notification is processed (older ones count as dropped), so a slow
// presenter never works through a backlog.
class FrameGenerationSchedule {
 public:
  struct Cycle {
    uint64_t dropped = 0;       // notifications replaced before being processed
    int multiplier = 2;         // outputs per source frame
    int64_t begin_ns = 0;
    int64_t step_ns = 0;        // spacing between consecutive outputs
    bool stop = false;          // cadence x multiplier exceeds the display rate
  };

  bool HasPending(uint64_t notification) const { return notification != consumed_; }

  Cycle Begin(uint64_t notification, int64_t arrival_ns, uint64_t epoch, int multiplier,
              float display_hz, int64_t now_ns) {
    Cycle cycle;
    cycle.dropped = notification > consumed_ + 1 ? notification - consumed_ - 1 : 0;
    consumed_ = notification;
    if (!has_epoch_ || epoch != epoch_) {
      // New configuration (engine, multiplier, Hz): judge the cadence afresh.
      epoch_ = epoch;
      has_epoch_ = true;
      cadence_.Reset();
      observed_ = 0;
    }
    const int64_t half_period = cadence_.Observe(arrival_ns);
    cycle.multiplier = std::clamp(multiplier, 2, 4);
    if (observed_ < kWarmupObservations) ++observed_;
    cycle.stop = observed_ >= kWarmupObservations &&
                 !cadence_.FitsRefresh(display_hz, cycle.multiplier);
    cycle.begin_ns = now_ns;
    cycle.step_ns = half_period * 2 / cycle.multiplier;
    return cycle;
  }

  // When output `index` (1-based; `multiplier` is the real frame) is due.
  static int64_t Deadline(const Cycle& cycle, int index) {
    return cycle.begin_ns + cycle.step_ns * (index - 1);
  }

  // A synthetic output whose slot passed by more than half a step is skipped
  // instead of being presented right before the next one (no catch-up burst).
  // The real frame is never skipped.
  static bool LateSynthetic(const Cycle& cycle, int index, int64_t now_ns) {
    return index < cycle.multiplier && now_ns > Deadline(cycle, index) + cycle.step_ns / 2;
  }

 private:
  static constexpr unsigned kWarmupObservations = 5;
  FrameCadence cadence_;
  uint64_t consumed_ = 0;
  unsigned observed_ = 0;
  uint64_t epoch_ = 0;
  bool has_epoch_ = false;
};
}  // namespace xe::ui
#endif
