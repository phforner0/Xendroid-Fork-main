// Host test of the adaptive Android audio buffer (15j): tools/test-native-logic.sh.
#include "xe_audio_buffer_tuner.h"

#include <cstdio>
#include <cstdlib>

using xe::apu::aaudio::AudioBufferTuner;

static int failures = 0;
#define EXPECT_EQ(a, b)                                                              \
  do {                                                                               \
    const auto va = (a);                                                             \
    const auto vb = (b);                                                             \
    if (va != vb) {                                                                  \
      std::fprintf(stderr, "%s:%d: %s = %lld, expected %lld\n", __FILE__, __LINE__, \
                   #a, (long long)va, (long long)vb);                                \
      ++failures;                                                                    \
    }                                                                                \
  } while (0)

int main() {
  // Starts at the configured depth; quiet seconds never go below it.
  AudioBufferTuner t(4);
  EXPECT_EQ(t.bursts(), 4u);
  EXPECT_EQ(t.max_bursts(), 12u);
  for (int i = 0; i < 100; ++i) t.Second(0);
  EXPECT_EQ(t.bursts(), 4u);

  // One burst more per second with underruns, up to three times the configured depth.
  EXPECT_EQ(t.Second(3), 5u);
  EXPECT_EQ(t.Second(1), 6u);
  for (int i = 0; i < 20; ++i) t.Second(2);
  EXPECT_EQ(t.bursts(), 12u);

  // Back down one burst per 30 quiet seconds; an underrun restarts the count.
  for (uint32_t i = 1; i < AudioBufferTuner::kShrinkAfterSeconds; ++i) t.Second(0);
  EXPECT_EQ(t.bursts(), 12u);
  t.Second(0);
  EXPECT_EQ(t.bursts(), 11u);
  for (uint32_t i = 1; i < AudioBufferTuner::kShrinkAfterSeconds; ++i) t.Second(0);
  t.Second(1);
  EXPECT_EQ(t.bursts(), 12u);
  for (uint32_t i = 0; i < 9 * AudioBufferTuner::kShrinkAfterSeconds; ++i) t.Second(0);
  EXPECT_EQ(t.bursts(), 4u);

  // Bounds: a deep configured buffer is not tripled past the cap; zero means one.
  EXPECT_EQ(AudioBufferTuner(8).max_bursts(), 16u);
  EXPECT_EQ(AudioBufferTuner(20).max_bursts(), 20u);
  AudioBufferTuner zero(0);
  EXPECT_EQ(zero.bursts(), 1u);
  EXPECT_EQ(zero.max_bursts(), 3u);

  if (failures) {
    std::fprintf(stderr, "audio_buffer_tuner_test: %d failures\n", failures);
    return EXIT_FAILURE;
  }
  std::printf("audio_buffer_tuner_test: ok\n");
  return EXIT_SUCCESS;
}
