/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2020 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include "xe_opensles_audio_driver.h"

#include <algorithm>
#include <cstring>
#include "audio_runtime.h"
#include "xenia/base/logging.h"
#include "xenia/base/assert.h"
#include "xenia/apu/conversion.h"
#include "xenia/apu/apu_flags.h"
#include "xenia/base/profiling.h"

namespace xe {
namespace apu {
namespace opensles {

OpenSLESAudioDriver::OpenSLESAudioDriver(Memory* memory, xe::threading::Semaphore* semaphore,
                                         uint32_t frequency, uint32_t channels)
    : semaphore_(semaphore),
      frame_frequency_(frequency),
      frame_channels_(channels),
      channel_samples_(channels == 6 ? 256 : 768),
      submit_samples_(channels * (channels == 6 ? 256 : 768)),
      renderer_(channels == 6 ? 256 : 768, channels) {
    assert_true(channels == 6 || channels == 2);
    for (auto& output : output_) {
        output.resize(size_t(host_frame_channels_) * channel_samples_, 0.0f);
    }
}

OpenSLESAudioDriver::~OpenSLESAudioDriver() {
}

bool OpenSLESAudioDriver::Initialize() {
    SLresult r;

    r = slCreateEngine(&sl_object_, 0, nullptr, 0, nullptr, nullptr);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("slCreateEngine failed: {}", r);
        return false;
    }

    r = (*sl_object_)->Realize(sl_object_, SL_BOOLEAN_FALSE);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("Realize engine failed: {}", r);
        return false;
    }

    r = (*sl_object_)->GetInterface(sl_object_, SL_IID_ENGINE, &sl_engine_);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("GetInterface engine failed: {}", r);
        return false;
    }

    const SLInterfaceID ids[] = {SL_IID_ENVIRONMENTALREVERB};
    const SLboolean req[] = {SL_BOOLEAN_FALSE};
    r = (*sl_engine_)->CreateOutputMix(sl_engine_, &sl_output_mix_, 1, ids, req);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("CreateOutputMix failed: {}", r);
        return false;
    }

    r = (*sl_output_mix_)->Realize(sl_output_mix_, SL_BOOLEAN_FALSE);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("Realize output mix failed: {}", r);
        return false;
    }

    SLDataLocator_AndroidSimpleBufferQueue locator_bufferqueue = {
        SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};

    SLAndroidDataFormat_PCM_EX format_pcm = {
        SL_ANDROID_DATAFORMAT_PCM_EX,
        host_frame_channels_,
        // milliHertz: the song's own rate for the media player.
        frame_frequency_ * 1000,
        SL_PCMSAMPLEFORMAT_FIXED_32,
        SL_PCMSAMPLEFORMAT_FIXED_32,
        SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
        SL_BYTEORDER_LITTLEENDIAN,
        SL_ANDROID_PCM_REPRESENTATION_FLOAT
    };

    SLDataSource audioSrc = {&locator_bufferqueue, &format_pcm};

    SLDataLocator_OutputMix locator_outputmix = {SL_DATALOCATOR_OUTPUTMIX, sl_output_mix_};
    SLDataSink audioSnk = {&locator_outputmix, NULL};

    // The volume is applied to the samples (SetVolume), the player's only interface is
    // its buffer queue.
    const SLInterfaceID interfaceIds[] = {
        SL_IID_BUFFERQUEUE
    };

    const SLboolean interfaceRequired[] = {
        SL_BOOLEAN_TRUE
    };

    r = (*sl_engine_)->CreateAudioPlayer(
        sl_engine_,
        &sl_player_,
        &audioSrc,
        &audioSnk,
        sizeof(interfaceIds)/sizeof(interfaceIds[0]),
        interfaceIds,
        interfaceRequired
    );

    if (r != SL_RESULT_SUCCESS) {
        XELOGE("CreateAudioPlayer failed: {}", r);
        return false;
    }

    r = (*sl_player_)->Realize(sl_player_, SL_BOOLEAN_FALSE);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("Realize player failed: {}", r);
        return false;
    }

    r = (*sl_player_)->GetInterface(sl_player_, SL_IID_PLAY, &sl_player_play_);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("GetInterface play failed: {}", r);
        return false;
    }

    r = (*sl_player_)->GetInterface(sl_player_, SL_IID_BUFFERQUEUE, &sl_player_buffer_queue_);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("GetInterface buffer queue failed: {}", r);
        return false;
    }

    {
        std::unique_lock<std::mutex> guard(frames_mutex_);
        for (int i = 0; i < 2; i++) {
            float* buffer = new float[submit_samples_];
            frames_unused_.push(buffer);
        }
    }

    r = (*sl_player_buffer_queue_)->RegisterCallback(sl_player_buffer_queue_, PlayerCallback, this);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("RegisterCallback failed: {}", r);
        return false;
    }

    // Both buffers queued before playback starts, so from then on only the player's thread
    // renders.
    for (uint32_t i = 0; i < kOutputBuffers; ++i) {
        RenderAndEnqueue();
    }

    r = (*sl_player_play_)->SetPlayState(sl_player_play_, SL_PLAYSTATE_PLAYING);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("SetPlayState failed: {}", r);
        return false;
    }

    return true;
}

void OpenSLESAudioDriver::Pause() {
    // Paused, not stopped: the player holds its place and its queue for Resume. (Stopped,
    // it still resumed on the POCO F7, but a stop is the end of playback.)
    SLresult r = (*sl_player_play_)->SetPlayState(sl_player_play_, SL_PLAYSTATE_PAUSED);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("SetPlayState failed: {}", r);
    }
}

void OpenSLESAudioDriver::Resume() {
    SLresult r = (*sl_player_play_)->SetPlayState(sl_player_play_, SL_PLAYSTATE_PLAYING);
    if (r != SL_RESULT_SUCCESS) {
        XELOGE("SetPlayState failed: {}", r);
    }
}

void OpenSLESAudioDriver::SetVolume(float volume) {
    // In software, like AAudio. SetVolumeLevel took volume * 100 mB (0 to +1 dB), which
    // attenuated nothing.
    driver_volume_.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_relaxed);
}

void OpenSLESAudioDriver::PlayerCallback(SLAndroidSimpleBufferQueueItf buffer_queue, void* context) {
  SCOPE_profile_cpu_f("apu");
  auto driver = static_cast<OpenSLESAudioDriver*>(context);
  driver->RenderAndEnqueue();
  // A credit per buffer played, a real block or a concealed gap, as AAudio does: the
  // worker spends one on every guest callback, also on one that submits nothing, and
  // without the gaps' credits those were lost for good. With every credit already back,
  // the release fails at the semaphore's maximum, harmlessly.
  driver->semaphore_->Release(1, nullptr);
}

void OpenSLESAudioDriver::RenderAndEnqueue() {
  std::vector<float>& output = output_[next_output_];
  next_output_ = (next_output_ + 1) % kOutputBuffers;
  const float gain = driver_volume_.load(std::memory_order_relaxed) *
                     (float(ae::EffectiveVolume()) / 100.0f);
  renderer_.Render(output.data(), int32_t(channel_samples_), 1.0f, gain,
                   [this]() { return NextGuestBlock(); },
                   [this](const float* block) { ReturnGuestBlock(block); });
  (*sl_player_buffer_queue_)->Enqueue(sl_player_buffer_queue_, output.data(),
                                      SLuint32(output.size() * sizeof(float)));
}

const float* OpenSLESAudioDriver::NextGuestBlock() {
  // Run summary (C02): startup silence before the guest's first block is not
  // an underrun; every empty queue after it is one.
  auto& run_stats = ae::RunStats();
  run_stats.backend.store(2, std::memory_order_relaxed);
  if (played_once_) {
    run_stats.blocks.fetch_add(1, std::memory_order_relaxed);
  }
  float* buffer = nullptr;
  {
    std::unique_lock<std::mutex> guard(frames_mutex_);
    if (!frames_queued_.empty()) {
      buffer = frames_queued_.front();
      frames_queued_.pop();
    }
  }
  if (!buffer) {
    if (played_once_) {
      run_stats.concealed.fetch_add(1, std::memory_order_relaxed);
    }
    return nullptr;
  }
  if (!played_once_) {
    played_once_ = true;
    run_stats.blocks.fetch_add(1, std::memory_order_relaxed);
  }
  return buffer;
}

void OpenSLESAudioDriver::ReturnGuestBlock(const float* block) {
  std::unique_lock<std::mutex> guard(frames_mutex_);
  frames_unused_.push(const_cast<float*>(block));
}

void OpenSLESAudioDriver::SubmitFrame(float* samples) {
  float* output_frame;
  {
    std::unique_lock<std::mutex> guard(frames_mutex_);
    if (frames_unused_.empty()) {
      output_frame = new float[submit_samples_];
    } else {
      output_frame = frames_unused_.top();
      frames_unused_.pop();
    }
  }

  std::memcpy(output_frame, samples, submit_samples_ * sizeof(float));

  {
    std::unique_lock<std::mutex> guard(frames_mutex_);
    frames_queued_.push(output_frame);
  }
}


void OpenSLESAudioDriver::Shutdown() {
    if (sl_player_play_) {
        (*sl_player_play_)->SetPlayState(sl_player_play_, SL_PLAYSTATE_STOPPED);
    }

    if (sl_player_) {
        (*sl_player_)->Destroy(sl_player_);
        sl_player_ = nullptr;
        sl_player_play_ = nullptr;
        sl_player_buffer_queue_ = nullptr;
    }

    if (sl_output_mix_) {
        (*sl_output_mix_)->Destroy(sl_output_mix_);
        sl_output_mix_ = nullptr;
    }

    if (sl_object_) {
        (*sl_object_)->Destroy(sl_object_);
        sl_object_ = nullptr;
        sl_engine_ = nullptr;
    }

    std::unique_lock<std::mutex> guard(frames_mutex_);
    while (!frames_unused_.empty()) {
        delete[] frames_unused_.top();
        frames_unused_.pop();
    }

    while (!frames_queued_.empty()) {
        delete[] frames_queued_.front();
        frames_queued_.pop();
    }

}

}  // namespace opensles
}  // namespace apu
}  // namespace xe
