#include <algorithm>
#include <cassert>
#include <cstdio>
#include <functional>
#include <thread>
#include <vector>
#include "xenia/ui/presentation_runtime.h"

using namespace xe::ui;

constexpr int64_t kMs = 1000000;

// A02: the real frame-generation loop on its own thread, with a painter that records
// phases and can hold a paint until the test lets it go. Real clock and waits.
struct FakePainter {
  std::mutex mutex;
  std::condition_variable changed;
  std::vector<int> phases;
  int hold_phase = -1;           // the next paint of this phase waits for release()
  bool holding = false, released = false;
  std::function<void(int)> on_paint;

  void operator()(int phase) {
    std::unique_lock<std::mutex> lock(mutex);
    phases.push_back(phase);
    if (on_paint) on_paint(phase);
    if (phase == hold_phase) {
      hold_phase = -1;
      holding = true;
      changed.notify_all();
      changed.wait(lock, [&] { return released; });
      holding = false;
    }
    changed.notify_all();
  }
  void wait_holding() {
    std::unique_lock<std::mutex> lock(mutex);
    changed.wait(lock, [&] { return holding; });
  }
  void release() {
    std::lock_guard<std::mutex> lock(mutex);
    released = true;
    changed.notify_all();
  }
  void wait_paints(size_t count) {
    std::unique_lock<std::mutex> lock(mutex);
    changed.wait(lock, [&] { return phases.size() >= count; });
  }
  // Cycles end with the real frame, phase 0, whatever happened to their synthetic slots.
  void wait_real_frames(size_t count) {
    std::unique_lock<std::mutex> lock(mutex);
    changed.wait(lock, [&] { return size_t(std::count(phases.begin(), phases.end(), 0)) >= count; });
  }
  std::vector<int> snapshot() {
    std::lock_guard<std::mutex> lock(mutex);
    return phases;
  }
};

static void LoopPaintsSyntheticOutputsThenTheRealOne() {
  PresentationRuntime runtime;
  runtime.frame_generation_requested = true;
  runtime.display_hz = 120.0f;
  FrameGenerationQueue queue;
  FakePainter painter;
  std::thread worker([&] { RunFrameGenerationLoop(queue, runtime, std::ref(painter)); });
  for (int i = 0; i < 4; ++i) {
    queue.Notify(SteadyNowNs());
    painter.wait_paints(size_t(2 * (i + 1)));        // each cycle: one synthetic, then the real one
  }
  queue.Shutdown();
  worker.join();
  const auto phases = painter.snapshot();
  assert((phases == std::vector<int>{1, 0, 1, 0, 1, 0, 1, 0}));
  assert(runtime.synthetic_slots.load() == 4 && runtime.late_synthetic_skips.load() == 0);
  assert(runtime.dropped_guest_notifications.load() == 0);
}

static void LsfgMultiplierPaintsEveryGeneratedPhaseInOrder() {
  PresentationRuntime runtime;
  runtime.frame_generation_requested = true;
  runtime.frame_generation_engine = 1;
  runtime.frame_generation_multiplier = 3;
  runtime.display_hz = 240.0f;
  FrameGenerationQueue queue;
  FakePainter painter;
  std::thread worker([&] { RunFrameGenerationLoop(queue, runtime, std::ref(painter)); });
  queue.Notify(SteadyNowNs());
  painter.wait_real_frames(1);
  queue.Shutdown();
  worker.join();
  // A synthetic slot can only be skipped when it is over half a step late (~5 ms here).
  const auto phases = painter.snapshot();
  assert(phases.back() == 0 && runtime.synthetic_slots.load() == 2);
  assert(phases.size() + runtime.late_synthetic_skips.load() == 3);
}

// Notifications arriving while a paint runs replace each other: only the newest is processed.
static void NotificationsDuringAPaintAreReplacedNotQueued() {
  PresentationRuntime runtime;
  runtime.frame_generation_requested = true;
  runtime.display_hz = 120.0f;
  FrameGenerationQueue queue;
  FakePainter painter;
  painter.hold_phase = 0;
  std::thread worker([&] { RunFrameGenerationLoop(queue, runtime, std::ref(painter)); });
  queue.Notify(SteadyNowNs());
  painter.wait_holding();                            // the real frame of cycle 1 is being painted
  for (int i = 0; i < 3; ++i) queue.Notify(SteadyNowNs());
  painter.release();
  painter.wait_paints(4);                            // cycle 2 (the newest notification): 1, 0
  queue.Shutdown();
  worker.join();
  assert(runtime.dropped_guest_notifications.load() == 2);
  assert((painter.snapshot() == std::vector<int>{1, 0, 1, 0}));
}

// Shutdown while a synthetic output is painted: that paint finishes, nothing follows it.
static void ShutdownDuringAPaintEndsTheLoopWithoutMorePaints() {
  PresentationRuntime runtime;
  runtime.frame_generation_requested = true;
  runtime.display_hz = 120.0f;
  FrameGenerationQueue queue;
  FakePainter painter;
  painter.hold_phase = 1;
  std::thread worker([&] { RunFrameGenerationLoop(queue, runtime, std::ref(painter)); });
  queue.Notify(SteadyNowNs());
  painter.wait_holding();
  queue.Notify(SteadyNowNs());                       // more work pending...
  queue.Shutdown();                                  // ...but the game is closing
  painter.release();
  worker.join();
  assert((painter.snapshot() == std::vector<int>{1}));  // not even this cycle's real frame
}

// Frame generation turned off mid-cycle: no more synthetic outputs, the real one still shows.
static void TurningGenerationOffStopsSyntheticOutputsOnly() {
  PresentationRuntime runtime;
  runtime.frame_generation_requested = true;
  runtime.display_hz = 120.0f;
  FrameGenerationQueue queue;
  FakePainter painter;
  painter.on_paint = [&](int phase) { if (phase == 1) runtime.frame_generation_requested = false; };
  std::thread worker([&] { RunFrameGenerationLoop(queue, runtime, std::ref(painter)); });
  queue.Notify(SteadyNowNs());
  painter.wait_paints(2);
  queue.Notify(SteadyNowNs());
  painter.wait_paints(3);
  queue.Shutdown();
  worker.join();
  assert((painter.snapshot() == std::vector<int>{1, 0, 0}));
  assert(runtime.synthetic_slots.load() == 1);
}

static void OutputRectangles() {
  auto fit = CalculateOutputRectangle(DisplayMode::kFit, 1280, 720, 16, 9, 2400, 1080);
  assert(fit.width == 1920 && fit.height == 1080 && fit.x == 240);
  auto fill = CalculateOutputRectangle(DisplayMode::kFill, 1280, 720, 16, 9, 2400, 1080);
  assert(fill.width == 2400 && fill.height == 1350 && fill.y == -135);
  auto integer = CalculateOutputRectangle(DisplayMode::kInteger, 1280, 720, 16, 9, 2400, 1080);
  assert(integer.width == 1280 && integer.height == 720 && integer.x == 560);
  auto small = CalculateOutputRectangle(DisplayMode::kInteger, 1280, 720, 16, 9, 640, 480);
  assert(small.width == 640 && small.height == 360);
}

static void Cadence() {
  FrameCadence cadence;
  cadence.Observe(1000000000);
  for (int i = 1; i < 20; ++i) cadence.Observe(1000000000ll + i * 33333333ll);
  assert(cadence.FitsRefresh(61.0f));
  assert(!cadence.FitsRefresh(45.0f));
  assert(cadence.FitsRefresh(120.0f, 4));
  assert(!cadence.FitsRefresh(60.0f, 4));
  assert(cadence.Observe(3000000000) >= 16000000);
  RuntimePresentation().frame_generation_requested = false;
  assert(!RuntimePresentation().frame_generation_requested.load());
}

// A 30 fps source at 60 Hz with 2x: the synthetic output is due at once, the
// real one half a source period later; the spacing follows the measured cadence.
static void SteadyCadenceSpacesOutputsEvenly() {
  FrameGenerationSchedule schedule;
  int64_t t = 1000 * kMs;
  FrameGenerationSchedule::Cycle cycle;
  for (uint64_t n = 1; n <= 30; ++n, t += 33333333) {
    assert(schedule.HasPending(n));
    cycle = schedule.Begin(n, t, 7, 2, 60.0f, t);
    assert(!schedule.HasPending(n));
    assert(cycle.dropped == 0 && !cycle.stop && cycle.multiplier == 2);
  }
  assert(cycle.step_ns > 16 * kMs && cycle.step_ns < 17 * kMs);
  assert(FrameGenerationSchedule::Deadline(cycle, 1) == cycle.begin_ns);
  assert(FrameGenerationSchedule::Deadline(cycle, 2) == cycle.begin_ns + cycle.step_ns);
}

// Only the newest source frame is processed: no backlog is worked through.
static void ReplacedNotificationsAreDroppedNotQueued() {
  FrameGenerationSchedule schedule;
  schedule.Begin(1, 0, 1, 2, 60.0f, 0);
  auto cycle = schedule.Begin(4, 33 * kMs, 1, 2, 60.0f, 40 * kMs);
  assert(cycle.dropped == 2);
  assert(!schedule.HasPending(4));
  cycle = schedule.Begin(5, 66 * kMs, 1, 2, 60.0f, 70 * kMs);
  assert(cycle.dropped == 0);
}

// A guest faster than refresh/multiplier stops FG, but only after warm-up, and a
// new configuration epoch starts the judgement over.
static void CadenceOverTheDisplayStopsAfterWarmup() {
  FrameGenerationSchedule schedule;
  int64_t t = 0;
  for (uint64_t n = 1; n <= 4; ++n, t += 16666667) assert(!schedule.Begin(n, t, 1, 2, 60.0f, t).stop);
  assert(schedule.Begin(5, t, 1, 2, 60.0f, t).stop);
  t += 16666667;
  // New epoch (e.g. the user raised the display Hz): warm-up again.
  for (uint64_t n = 6; n <= 9; ++n, t += 16666667) assert(!schedule.Begin(n, t, 2, 2, 120.0f, t).stop);
  assert(!schedule.Begin(10, t, 2, 2, 120.0f, t).stop);  // 60 fps x2 fits 120 Hz
}

// A pause leaves a long gap: the cadence falls back to its default instead of
// treating the gap as the frame period, and nothing is generated for the gap.
static void PauseResetsTheCadence() {
  FrameGenerationSchedule schedule;
  int64_t t = 0;
  for (uint64_t n = 1; n <= 10; ++n, t += 16666667) schedule.Begin(n, t, 3, 2, 120.0f, t);
  t += 2000 * kMs;
  auto cycle = schedule.Begin(11, t, 3, 2, 120.0f, t);
  assert(cycle.dropped == 0 && !cycle.stop);
  assert(cycle.step_ns == 33333333 / 2 * 2 / 2);
}

static void MultiplierIsClampedAndLateSyntheticFramesAreSkipped() {
  FrameGenerationSchedule schedule;
  auto cycle = schedule.Begin(1, 0, 1, 7, 240.0f, 0);
  assert(cycle.multiplier == 4);
  assert(schedule.Begin(2, 33 * kMs, 1, 1, 240.0f, 33 * kMs).multiplier == 2);
  cycle = schedule.Begin(3, 66 * kMs, 1, 4, 240.0f, 100 * kMs);
  const int64_t step = cycle.step_ns;
  assert(step > 0);
  // Output 2 painted on time and slightly late is fine; past half a step it is skipped.
  assert(!FrameGenerationSchedule::LateSynthetic(cycle, 2, FrameGenerationSchedule::Deadline(cycle, 2)));
  assert(!FrameGenerationSchedule::LateSynthetic(cycle, 2, FrameGenerationSchedule::Deadline(cycle, 2) + step / 2));
  assert(FrameGenerationSchedule::LateSynthetic(cycle, 2, FrameGenerationSchedule::Deadline(cycle, 2) + step / 2 + 1));
  // The real frame (index == multiplier) is never skipped, however late.
  assert(!FrameGenerationSchedule::LateSynthetic(cycle, 4, FrameGenerationSchedule::Deadline(cycle, 4) + 100 * step));
}

// F08: GPU timestamps count only timestampValidBits and may wrap between two writes.
static void TimestampsHonourValidBitsAndWrap() {
  // Adreno-like 19.2 MHz counter: 52.083 ns per tick.
  const double period = 1e9 / 19.2e6;
  assert(TimestampElapsedNs(1000, 1000 + 96000, 64, period) == int64_t(96000 * period));  // 5 ms
  assert(TimestampElapsedNs(5, 5, 64, 1.0) == 0);
  // A 36-bit counter wrapping between the two writes: 100 ticks before the top, 50 after.
  const uint64_t top = (uint64_t(1) << 36) - 100;
  assert(TimestampElapsedNs(top, 50, 36, 1.0) == 150);
  // Bits above timestampValidBits are undefined: they must not count.
  assert(TimestampElapsedNs(0xFFFF000000000010ull, 0x0000000000000030ull, 36, 1.0) == 0x20);
  // End before begin with no wrap possible, no valid bits, no period: no measurement.
  assert(TimestampElapsedNs(2000, 1000, 64, 1.0) == -1);
  assert(TimestampElapsedNs(0, 100, 0, 1.0) == -1);
  assert(TimestampElapsedNs(0, 100, 64, 0.0) == -1);
  assert(TimestampElapsedNs(0, 100, 64, std::nan("")) == -1);
  // Over a second for one pass is not believable (unwritten or reset query).
  assert(TimestampElapsedNs(0, 2000000000ull, 64, 1.0) == -1);
}

static void GenerationGpuTimesAreBucketedAndFailuresAreNotStale() {
  PresentationRuntime runtime;
  RecordGenerationGpu(runtime, 3100000);        // 3.1 ms -> bucket 12 (3.00..3.25 ms)
  assert(runtime.generation_gpu_histogram[12].load() == 1);
  assert(runtime.generation_gpu_ms.load() > 3.09 && runtime.generation_gpu_ms.load() < 3.11);
  RecordGenerationGpu(runtime, 0);
  assert(runtime.generation_gpu_histogram[0].load() == 1);
  RecordGenerationGpu(runtime, 40000000);       // 40 ms -> the open last bucket
  assert(runtime.generation_gpu_histogram[kGenerationGpuBuckets - 1].load() == 1);
  // An untimed pass leaves no old figure behind and is counted as unavailable.
  RecordGenerationGpu(runtime, -1);
  assert(runtime.generation_gpu_ms.load() == -1.0);
  assert(runtime.generation_gpu_unavailable.load() == 1);
  uint64_t timed = 0;
  for (const auto& bucket : runtime.generation_gpu_histogram) timed += bucket.load();
  assert(timed == 3);
}

int main() {
  OutputRectangles();
  Cadence();
  SteadyCadenceSpacesOutputsEvenly();
  ReplacedNotificationsAreDroppedNotQueued();
  CadenceOverTheDisplayStopsAfterWarmup();
  PauseResetsTheCadence();
  MultiplierIsClampedAndLateSyntheticFramesAreSkipped();
  TimestampsHonourValidBitsAndWrap();
  GenerationGpuTimesAreBucketedAndFailuresAreNotStale();
  LoopPaintsSyntheticOutputsThenTheRealOne();
  LsfgMultiplierPaintsEveryGeneratedPhaseInOrder();
  NotificationsDuringAPaintAreReplacedNotQueued();
  ShutdownDuringAPaintEndsTheLoopWithoutMorePaints();
  TurningGenerationOffStopsSyntheticOutputsOnly();
  std::puts("presentation policy: passed");
}
