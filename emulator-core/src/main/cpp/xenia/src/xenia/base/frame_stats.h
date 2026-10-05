/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 */

#ifndef XENIA_BASE_FRAME_STATS_H_
#define XENIA_BASE_FRAME_STATS_H_

#include <atomic>
#include <chrono>
#include <cstdint>

namespace xe {

// Guest-frame present timing for the debug overlay.
//
// RecordGuestPresent() is called exactly once per presented GUEST frame (from
// the GPU command processor's IssueSwap), so the reported FPS / frame time
// reflect the actual game frame rate -- NOT the host UI repaint cadence (which
// keeps running at panel refresh even when the guest stalls). The producer is
// single-threaded (the command-processor thread); the published values are read
// from the UI thread via GetFrameStats(). A torn read across the three values
// is harmless for a debug readout.
namespace internal {
inline std::atomic<float>& frame_instant_ms() {
  static std::atomic<float> v{0.0f};
  return v;
}
inline std::atomic<float>& frame_avg_ms() {
  static std::atomic<float> v{0.0f};
  return v;
}
inline std::atomic<float>& frame_fps() {
  static std::atomic<float> v{0.0f};
  return v;
}
// steady_clock time of the last guest present (ns since its epoch); 0 = none yet.
inline std::atomic<int64_t>& frame_last_present_ns() {
  static std::atomic<int64_t> v{0};
  return v;
}
// vkQueuePresentKHR accepts host submissions asynchronously. This counter does
// NOT measure display scanout, and may include repaints without a new guest frame.
inline std::atomic<uint64_t>& host_present_submissions() {
  static std::atomic<uint64_t> v{0};
  return v;
}
}  // namespace internal

// Per-frame guest frame times for run summaries: 1 ms buckets, the last one holds
// everything from (kFrameTimeBuckets - 1) ms up. Counts only grow (a reader keeps
// its own baseline and takes deltas); a gap longer than the FPS window (pause,
// loading) restarts the timing and is not counted as a frame.
constexpr size_t kFrameTimeBuckets = 251;
inline std::atomic<uint32_t>* GuestFrameTimeHistogram() {
  static std::atomic<uint32_t> buckets[kFrameTimeBuckets] = {};
  return buckets;
}

inline void RecordHostPresentSubmission() {
  internal::host_present_submissions().fetch_add(1, std::memory_order_relaxed);
}

inline uint64_t GetHostPresentSubmissionCount() {
  return internal::host_present_submissions().load(std::memory_order_relaxed);
}

// The FPS window; also how long the published stats stay valid without a new frame.
constexpr double kFrameStatsWindowMs = 1000.0;

// Call once per presented guest frame (single producer thread).
// FPS is a RenderDoc-style average over a ~1s sliding time window (framerate
// independent); instant_ms stays the raw present-to-present delta.
inline void RecordGuestPresent() {
  using clock = std::chrono::steady_clock;
  static constexpr double kWindowMs = kFrameStatsWindowMs;
  static constexpr size_t kCap = 1024;  // covers >1000 fps within the window
  static clock::time_point ts[kCap];
  static size_t head = 0;   // oldest sample
  static size_t count = 0;  // samples in the window
  static clock::time_point last{};

  clock::time_point now = clock::now();
  internal::frame_last_present_ns().store(
      std::chrono::duration_cast<std::chrono::nanoseconds>(now.time_since_epoch())
          .count(),
      std::memory_order_relaxed);
  const bool have_last = last != clock::time_point{};
  double instant_ms =
      have_last ? std::chrono::duration<double, std::milli>(now - last).count()
                : 0.0;
  last = now;

  // A long gap (first frame / pause / load) restarts the window so the average
  // recovers within a second instead of being dragged by a stale outlier.
  if (!have_last || instant_ms > kWindowMs) {
    head = 0;
    count = 1;
    ts[0] = now;
    internal::frame_instant_ms().store(0.0f, std::memory_order_relaxed);
    internal::frame_avg_ms().store(0.0f, std::memory_order_relaxed);
    internal::frame_fps().store(0.0f, std::memory_order_relaxed);
    return;
  }

  // One relaxed increment per guest frame (single producer).
  GuestFrameTimeHistogram()[instant_ms >= double(kFrameTimeBuckets - 1)
                                ? kFrameTimeBuckets - 1
                                : size_t(instant_ms)]
      .fetch_add(1, std::memory_order_relaxed);

  const size_t tail = (head + count) % kCap;
  ts[tail] = now;
  if (count < kCap) {
    ++count;
  } else {
    head = (head + 1) % kCap;  // ring full: drop oldest
  }

  // Evict samples older than the window.
  while (count > 1 &&
         std::chrono::duration<double, std::milli>(now - ts[head]).count() >
             kWindowMs) {
    head = (head + 1) % kCap;
    --count;
  }

  const double span_ms =
      std::chrono::duration<double, std::milli>(now - ts[head]).count();
  const double fps =
      (count > 1 && span_ms > 0.0) ? double(count - 1) * 1000.0 / span_ms : 0.0;
  const double avg_ms = fps > 0.0 ? 1000.0 / fps : 0.0;

  internal::frame_instant_ms().store(float(instant_ms),
                                     std::memory_order_relaxed);
  internal::frame_avg_ms().store(float(avg_ms), std::memory_order_relaxed);
  internal::frame_fps().store(float(fps), std::memory_order_relaxed);
}

// Read the latest published stats (any thread) as of [now]. With no guest frame for
// longer than the FPS window (a stall, a load without presents, a pause) they read 0,
// not the last rate: the published values only change when a frame arrives.
inline void GetFrameStatsAt(std::chrono::steady_clock::time_point now,
                            float& instant_ms, float& avg_ms, float& fps) {
  const int64_t last_ns =
      internal::frame_last_present_ns().load(std::memory_order_relaxed);
  const int64_t now_ns =
      std::chrono::duration_cast<std::chrono::nanoseconds>(now.time_since_epoch())
          .count();
  if (last_ns == 0 || double(now_ns - last_ns) / 1e6 > kFrameStatsWindowMs) {
    instant_ms = avg_ms = fps = 0.0f;
    return;
  }
  instant_ms = internal::frame_instant_ms().load(std::memory_order_relaxed);
  avg_ms = internal::frame_avg_ms().load(std::memory_order_relaxed);
  fps = internal::frame_fps().load(std::memory_order_relaxed);
}

inline void GetFrameStats(float& instant_ms, float& avg_ms, float& fps) {
  GetFrameStatsAt(std::chrono::steady_clock::now(), instant_ms, avg_ms, fps);
}

}  // namespace xe

#endif  // XENIA_BASE_FRAME_STATS_H_
