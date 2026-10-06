/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2020 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */
#ifndef xendroid_XE_OPENSLES_AUDIO_DRIVER_H
#define xendroid_XE_OPENSLES_AUDIO_DRIVER_H

#include <atomic>
#include <mutex>
#include <queue>
#include <stack>
#include <vector>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "xenia/apu/audio_driver.h"
#include "xenia/base/threading.h"
#include "xe_audio_block_renderer.h"

namespace xe {
    namespace apu {
        namespace opensles {

            class OpenSLESAudioDriver : public AudioDriver {
            public:
                // channels selects the submit contract, as in AAudioAudioDriver: 6 is the
                // game path, 256 samples per channel of sequential big endian 5.1; 2 is
                // the media player, 768 frames of interleaved host endian stereo played
                // at the song's own rate. Both are 1536 floats per SubmitFrame.
                OpenSLESAudioDriver(Memory* memory, xe::threading::Semaphore* semaphore,
                                    uint32_t frequency = 48000, uint32_t channels = 6);
                ~OpenSLESAudioDriver() override;

                bool Initialize() override;
                void Pause() override;
                void Resume() override;
                void SetVolume(float volume) override;
                void SubmitFrame(float* frame) override;
                void Shutdown() override;


            protected:

                static void PlayerCallback(SLAndroidSimpleBufferQueueItf buffer_queue, void* context);
                // Renders the next output buffer and queues it: for the two buffers
                // Initialize primes before playback starts, then on the player's thread.
                void RenderAndEnqueue();
                const float* NextGuestBlock();
                void ReturnGuestBlock(const float* block);

                xe::threading::Semaphore* semaphore_ = nullptr;

                SLObjectItf sl_object_= nullptr;
                SLEngineItf sl_engine_= nullptr;
                SLObjectItf sl_output_mix_= nullptr;
                SLObjectItf sl_player_= nullptr;
                SLPlayItf sl_player_play_= nullptr;
                SLAndroidSimpleBufferQueueItf sl_player_buffer_queue_= nullptr;

                static constexpr uint32_t host_frame_channels_ = 2;
                // Two output buffers in the queue: one playing, one waiting. With a single
                // one the queue ran dry every time it was being refilled.
                static constexpr uint32_t kOutputBuffers = 2;
                const uint32_t frame_frequency_;
                const uint32_t frame_channels_;
                const uint32_t channel_samples_;
                const uint32_t submit_samples_;
                // Each driver's own. They were one static buffer, which the game's and the
                // media player's drivers both filled and queued.
                std::vector<float> output_[kOutputBuffers];
                uint32_t next_output_ = 0;
                // Conversion, gain, gap concealment (xe_audio_block_renderer.h).
                AudioBlockRenderer renderer_;
                // Per-driver volume (XMP), applied in software with the master volume.
                std::atomic<float> driver_volume_{1.0f};

                std::mutex frames_mutex_ = {};
                std::queue<float*> frames_queued_ = {};
                std::stack<float*> frames_unused_ = {};
                // A guest block has been played (run summary counts); player thread only.
                bool played_once_ = false;

            };

        }
    }
}
#endif //xendroid_XE_OPENSLES_AUDIO_DRIVER_H
