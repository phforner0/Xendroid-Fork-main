// AudioBlockRenderer (xe_audio_block_renderer.h): the Android drivers' block processing with
// a real signal - exact passthrough, the 5.1 fold and its byte order, the media player's
// stereo, gain bounds and NaN, gaps concealed without steps on both paths, the resampler
// across block edges, and one block or gap per callback of a block's length (OpenSL ES's
// pacing).
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

#include "xe_audio_block_renderer.h"

using xe::apu::AudioBlockRenderer;

namespace {

constexpr uint32_t kFrames = 256;

float BigEndian(float value) {
  uint32_t bits;
  std::memcpy(&bits, &value, sizeof(bits));
  bits = __builtin_bswap32(bits);
  float swapped;
  std::memcpy(&swapped, &bits, sizeof(swapped));
  return swapped;
}

// A game block: 5.1 sequential big endian, channel c's frame s set by fill(c, s).
template <typename Fill>
std::vector<float> GameBlock(Fill&& fill) {
  std::vector<float> block(size_t(kFrames) * 6, 0.0f);
  for (uint32_t c = 0; c < 6; ++c) {
    for (uint32_t s = 0; s < kFrames; ++s) block[c * kFrames + s] = BigEndian(fill(c, s));
  }
  return block;
}

// Renders guest blocks of `block_frames` (nullptr = missing) through callbacks of `callback`
// frames, until all of them have been played out.
std::vector<float> Render(AudioBlockRenderer& renderer, const std::vector<const float*>& blocks,
                          uint32_t block_frames, int32_t callback, float rate = 1.0f,
                          float gain = 1.0f, AudioBlockRenderer::Result* total = nullptr) {
  size_t next = 0;
  std::vector<float> out, buffer(size_t(callback) * 2);
  const size_t frames = size_t(double(blocks.size() * block_frames) / rate) + 8;
  while (out.size() / 2 < frames) {
    auto result = renderer.Render(buffer.data(), callback, rate, gain,
        [&]() -> const float* { return next < blocks.size() ? blocks[next++] : nullptr; },
        [](const float*) {});
    if (total) {
      total->blocks += result.blocks;
      total->gaps += result.gaps;
      total->clipped += result.clipped;
    }
    out.insert(out.end(), buffer.begin(), buffer.end());
  }
  return out;
}

float LargestStep(const std::vector<float>& out) {
  float worst = 0.0f;
  for (size_t i = 2; i < out.size(); ++i) worst = std::max(worst, std::fabs(out[i] - out[i - 2]));
  return worst;
}

// The largest step a sine of `amp` and `step` radians a frame can take through the renderer
// without a discontinuity: its own, plus the slope of the fade-in after a gap (amp over the
// ramp's frames; the concealment's cosine is gentler). Repeating the last block, a gap stepped
// 11 times the sine's own.
float SmoothBound(double amp, double step) {
  return float(amp * step + amp / AudioBlockRenderer::kFadeInFrames) * 1.001f;
}

// At rate 1 the output is the input, two frames late, bit for bit.
void PassthroughIsExact() {
  auto a = GameBlock([](uint32_t c, uint32_t s) { return c == 0 ? float(s) / kFrames : 0.0f; });
  auto b = GameBlock([](uint32_t c, uint32_t s) { return c == 0 ? -float(s) / kFrames : 0.0f; });
  AudioBlockRenderer renderer(kFrames, 6);
  AudioBlockRenderer::Result total;
  auto out = Render(renderer, {a.data(), b.data()}, kFrames, 192, 1.0f, 1.0f, &total);
  assert(total.blocks == 2 && total.gaps <= 1 && total.clipped == 0);
  for (uint32_t s = 0; s < kFrames; ++s) {
    assert(out[size_t(s + 2) * 2] == float(s) / kFrames);
    assert(out[size_t(kFrames + s + 2) * 2] == -float(s) / kFrames);
    assert(out[size_t(s + 2) * 2 + 1] == 0.0f);
  }
}

// FL FR C LFE BL BR folded at unity, -3 dB, -6 dB and -3 dB, from big endian.
void FiveOneFoldAndByteOrder() {
  const float values[6] = {0.1f, 0.2f, 0.3f, 0.4f, 0.05f, 0.06f};
  auto block = GameBlock([&](uint32_t c, uint32_t) { return values[c]; });
  AudioBlockRenderer renderer(kFrames, 6);
  auto out = Render(renderer, {block.data()}, kFrames, int32_t(kFrames));
  const float c = 0.707106781f;
  const float left = 0.1f + c * 0.3f + c * 0.05f + 0.5f * 0.4f;
  const float right = 0.2f + c * 0.3f + c * 0.06f + 0.5f * 0.4f;
  assert(std::fabs(out[100 * 2] - left) < 1e-6f && std::fabs(out[100 * 2 + 1] - right) < 1e-6f);
}

// The media player's blocks are interleaved host endian stereo, played as they are.
void MediaPlayerStereo() {
  std::vector<float> block(size_t(768) * 2);
  for (size_t i = 0; i < block.size(); ++i) block[i] = (i % 2 ? -0.25f : 0.5f) * float(i % 7) / 7.0f;
  AudioBlockRenderer renderer(768, 2);
  auto out = Render(renderer, {block.data()}, 768, 768);
  for (size_t f = 0; f + 2 < 768; ++f) {
    assert(out[(f + 2) * 2] == block[f * 2] && out[(f + 2) * 2 + 1] == block[f * 2 + 1]);
  }
}

// Gain scales; past full scale the samples are bounded and counted.
void GainIsBoundedAndCounted() {
  auto block = GameBlock([](uint32_t c, uint32_t s) { return c == 0 ? (s % 2 ? 0.8f : -0.8f) : 0.0f; });
  AudioBlockRenderer renderer(kFrames, 6);
  AudioBlockRenderer::Result total;
  auto out = Render(renderer, {block.data()}, kFrames, int32_t(kFrames), 1.0f, 2.0f, &total);
  assert(total.clipped == kFrames);  // the left channel only: the right one is silent
  for (float sample : out) assert(sample >= -1.0f && sample <= 1.0f);
  assert(out[10 * 2] == 1.0f || out[10 * 2] == -1.0f);
}

// Gaps of one and of several blocks, in a sine: no step larger than the sine's own (where
// repeating the last block made steps 11x larger), silence after the first gap block, and the
// next real block back at full level once its ramp is over.
void GapsAreConcealedWithoutSteps() {
  const int count = 48;
  const double amp = 0.5, step = 2.0 * 3.14159265358979 * 440.0 / 48000.0;
  std::vector<std::vector<float>> guest;
  for (int b = 0; b < count; ++b) {
    guest.push_back(GameBlock([&](uint32_t c, uint32_t s) {
      return c < 2 ? float(amp * std::sin(step * double(b * kFrames + s))) : 0.0f;
    }));
  }
  std::vector<const float*> blocks;
  for (int b = 0; b < count; ++b) {
    const bool missing = b == 10 || (b >= 20 && b <= 23);
    blocks.push_back(missing ? nullptr : guest[b].data());
  }
  for (int32_t callback : {96, 192, 256, 1000}) {
    AudioBlockRenderer renderer(kFrames, 6);
    AudioBlockRenderer::Result total;
    auto out = Render(renderer, blocks, kFrames, callback, 1.0f, 1.0f, &total);
    assert(total.gaps >= 5 && total.blocks == uint32_t(count - 5));
    assert(LargestStep(out) <= SmoothBound(amp, step));
    // The 2nd to 4th missing blocks of the long gap are silence.
    for (uint32_t f = 21 * kFrames + 2; f < 24 * kFrames + 2; ++f) assert(out[size_t(f) * 2] == 0.0f);
    // Block 30 plays at full level.
    float peak = 0.0f;
    for (uint32_t f = 30 * kFrames + 2; f < 31 * kFrames + 2; ++f) peak = std::max(peak, std::fabs(out[size_t(f) * 2]));
    assert(peak > float(amp) * 0.99f);
  }
}

// The rate control's resampler bends the rate without steps across block edges and gaps.
void ResamplingIsContinuous() {
  const double amp = 0.5, step = 2.0 * 3.14159265358979 * 300.0 / 48000.0;
  std::vector<std::vector<float>> guest;
  for (int b = 0; b < 12; ++b) {
    guest.push_back(GameBlock([&](uint32_t c, uint32_t s) {
      return c == 0 ? float(amp * std::sin(step * double(b * kFrames + s))) : 0.0f;
    }));
  }
  std::vector<const float*> blocks;
  for (int b = 0; b < 12; ++b) blocks.push_back(b == 6 ? nullptr : guest[b].data());
  for (float rate : {0.9f, 0.97f, 1.0f}) {
    AudioBlockRenderer renderer(kFrames, 6);
    auto out = Render(renderer, blocks, kFrames, 192, rate);
    assert(LargestStep(out) <= SmoothBound(amp, step));
  }
}

// A NaN from the guest is silenced and counted, not played.
void NanIsSilenced() {
  auto block = GameBlock([](uint32_t c, uint32_t s) { return c == 0 && s == 7 ? std::nanf("") : 0.25f; });
  AudioBlockRenderer renderer(kFrames, 6);
  AudioBlockRenderer::Result total;
  auto out = Render(renderer, {block.data(), block.data()}, kFrames, int32_t(kFrames), 1.0f, 1.0f, &total);
  for (float sample : out) assert(!std::isnan(sample));
  assert(total.clipped == 2);  // left channel, frame 7, of each block
}

// At rate 1, a callback of a block's length takes exactly one block or one gap: OpenSL ES
// returns one credit per buffer on that.
void OneBlockOrGapPerBlockLongCallback() {
  for (uint32_t channels : {6u, 2u}) {
    const uint32_t frames = channels == 6 ? kFrames : 768;
    std::vector<float> block(size_t(frames) * channels, 0.1f);
    AudioBlockRenderer renderer(frames, channels);
    std::vector<float> out(size_t(frames) * 2);
    int next = 0;
    for (int call = 0; call < 40; ++call) {
      auto result = renderer.Render(out.data(), int32_t(frames), 1.0f, 1.0f,
          [&]() -> const float* { return (next++ % 5 == 3) ? nullptr : block.data(); },
          [](const float*) {});
      assert(result.blocks + result.gaps == 1);
    }
  }
}

// The media player's path (768 frames of stereo) conceals gaps without steps too.
void PlayerGapsAreConcealedWithoutSteps() {
  const uint32_t frames = 768;
  const double amp = 0.5, step = 2.0 * 3.14159265358979 * 440.0 / 44100.0;
  std::vector<std::vector<float>> song;
  for (int b = 0; b < 16; ++b) {
    std::vector<float> block(size_t(frames) * 2);
    for (uint32_t f = 0; f < frames; ++f) {
      const float v = float(amp * std::sin(step * double(b * frames + f)));
      block[size_t(f) * 2] = v;
      block[size_t(f) * 2 + 1] = -v;
    }
    song.push_back(std::move(block));
  }
  std::vector<const float*> blocks;
  for (int b = 0; b < 16; ++b) blocks.push_back(b == 5 || b == 9 || b == 10 ? nullptr : song[b].data());
  AudioBlockRenderer renderer(frames, 2);
  auto out = Render(renderer, blocks, frames, int32_t(frames));
  assert(LargestStep(out) <= SmoothBound(amp, step));
}

}  // namespace

int main() {
  PassthroughIsExact();
  FiveOneFoldAndByteOrder();
  MediaPlayerStereo();
  GainIsBoundedAndCounted();
  GapsAreConcealedWithoutSteps();
  ResamplingIsContinuous();
  NanIsSilenced();
  OneBlockOrGapPerBlockLongCallback();
  PlayerGapsAreConcealedWithoutSteps();
  std::printf("audio_block_renderer_test: ok\n");
  return 0;
}
