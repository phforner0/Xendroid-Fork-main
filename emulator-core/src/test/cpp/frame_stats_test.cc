// Guest frame stats (xenia/base/frame_stats.h): the published FPS and frame time read 0
// once a second passes without a guest frame, instead of the last rate forever.

#include <chrono>
#include <cstdio>
#include <thread>

#include "xenia/base/frame_stats.h"

namespace {

int failures = 0;

void expect(bool ok, const char* what) {
  if (!ok) {
    std::fprintf(stderr, "FAILED: %s\n", what);
    ++failures;
  }
}

void frames(int count) {
  for (int i = 0; i < count; ++i) {
    xe::RecordGuestPresent();
    std::this_thread::sleep_for(std::chrono::milliseconds(16));
  }
}

}  // namespace

int main() {
  using clock = std::chrono::steady_clock;
  float instant = -1, average = -1, fps = -1;
  xe::GetFrameStats(instant, average, fps);
  expect(instant == 0 && average == 0 && fps == 0, "nothing before the first frame");

  frames(12);
  const auto now = clock::now();
  xe::GetFrameStatsAt(now, instant, average, fps);
  std::printf("  running: %.1f FPS, %.1f ms\n", fps, instant);
  expect(fps > 5 && fps < 200 && instant > 0, "a running game reads its rate");
  xe::GetFrameStatsAt(now + std::chrono::milliseconds(900), instant, average, fps);
  expect(fps > 0, "within the window the last rate stands");
  xe::GetFrameStatsAt(now + std::chrono::milliseconds(1100), instant, average, fps);
  expect(instant == 0 && average == 0 && fps == 0, "a second without frames reads 0");

  // A real stall, then the game resumes.
  std::this_thread::sleep_for(std::chrono::milliseconds(1100));
  xe::GetFrameStats(instant, average, fps);
  expect(fps == 0 && instant == 0, "stalled: 0, not the rate before");
  frames(10);
  xe::GetFrameStats(instant, average, fps);
  std::printf("  resumed: %.1f FPS, %.1f ms\n", fps, instant);
  expect(fps > 5 && instant > 0, "frames again: a rate again");

  if (failures) {
    std::fprintf(stderr, "frame_stats_test: %d failure(s)\n", failures);
    return 1;
  }
  std::printf("frame stats: passed\n");
  return 0;
}
