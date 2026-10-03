// 15j: the Android audio buffer that grows after underruns and slowly shrinks back (X360 Mobile
// v0.6.1/v0.6.2's adaptive buffer; the latency tuning AAudio's own guidance describes). Pure, so
// the host tests run it: one call per second with the underruns the device counted in it.
#pragma once

#include <algorithm>
#include <cstdint>

namespace xe {
namespace apu {
namespace aaudio {

class AudioBufferTuner {
 public:
  // A quiet stretch this long (seconds without an underrun) takes one burst back off.
  static constexpr uint32_t kShrinkAfterSeconds = 30;
  // At most this many times the configured depth, and never past kMaxBursts.
  static constexpr uint32_t kGrowthFactor = 3;
  static constexpr uint32_t kMaxBursts = 16;

  static uint32_t MaxFor(uint32_t configured) {
    const uint32_t base = std::max<uint32_t>(configured, 1);
    return std::max(base, std::min(base * kGrowthFactor, kMaxBursts));
  }

  explicit AudioBufferTuner(uint32_t configured)
      : min_(std::max<uint32_t>(configured, 1)), max_(MaxFor(configured)), bursts_(min_) {}

  uint32_t bursts() const { return bursts_; }
  uint32_t max_bursts() const { return max_; }

  // [underruns]: new in the last second. Grows one burst at once on an underrun (the gap was
  // already heard); shrinks one burst only after kShrinkAfterSeconds without any, so the buffer
  // does not see-saw. Never below the configured depth or above max_bursts().
  uint32_t Second(uint32_t underruns) {
    if (underruns > 0) {
      quiet_ = 0;
      if (bursts_ < max_) ++bursts_;
      return bursts_;
    }
    if (++quiet_ >= kShrinkAfterSeconds) {
      quiet_ = 0;
      if (bursts_ > min_) --bursts_;
    }
    return bursts_;
  }

 private:
  uint32_t min_;
  uint32_t max_;
  uint32_t bursts_;
  uint32_t quiet_ = 0;
};

}  // namespace aaudio
}  // namespace apu
}  // namespace xe
