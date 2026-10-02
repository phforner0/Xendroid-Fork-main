#include <cassert>
#include <cstdio>
#include "xenia/ui/presentation_runtime.h"

using namespace xe::ui;

constexpr int64_t kMs = 1000000;

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

int main() {
  OutputRectangles();
  Cadence();
  SteadyCadenceSpacesOutputsEvenly();
  ReplacedNotificationsAreDroppedNotQueued();
  CadenceOverTheDisplayStopsAfterWarmup();
  PauseResetsTheCadence();
  MultiplierIsClampedAndLateSyntheticFramesAreSkipped();
  std::puts("presentation policy: passed");
}
