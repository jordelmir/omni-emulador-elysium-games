// ═══════════════════════════════════════════════════════════════
// Elysium Console — Audio Engine (OpenSL ES)
// ═══════════════════════════════════════════════════════════════
// Minimal low-latency audio engine using OpenSL ES (available
// on all Android devices API 9+). Buffers audio samples from
// Libretro cores and feeds them to the audio output.
// ═══════════════════════════════════════════════════════════════

#ifndef ELYSIUM_AUDIO_ENGINE_H
#define ELYSIUM_AUDIO_ENGINE_H

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <android/log.h>
#include <cstring>
#include <cstdint>
#include <atomic>
#include <mutex>

#define AUDIO_TAG "ElysiumAudio"
#define AUDIO_LOGI(...) __android_log_print(ANDROID_LOG_INFO, AUDIO_TAG, __VA_ARGS__)
#define AUDIO_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, AUDIO_TAG, __VA_ARGS__)

namespace elysium {

/**
 * Lock-free ring buffer for audio samples.
 * Single-producer (emulation thread), single-consumer (audio callback).
 */
class AudioRingBuffer {
public:
    static constexpr int BUFFER_SIZE = 16384; // ~185ms at 44100Hz stereo

    AudioRingBuffer() : mReadPos(0), mWritePos(0) {
        memset(mBuffer, 0, sizeof(mBuffer));
    }

    /**
     * Write samples to the ring buffer.
     * @param data   Interleaved stereo int16_t samples
     * @param frames Number of frames (each frame = 2 samples L+R)
     * @return Number of frames actually written
     */
    size_t write(const int16_t* data, size_t frames) {
        size_t samplesCount = frames * 2; // stereo
        size_t written = 0;
        size_t wp = mWritePos.load(std::memory_order_relaxed);
        size_t rp = mReadPos.load(std::memory_order_acquire);

        for (size_t i = 0; i < samplesCount; ++i) {
            size_t nextWp = (wp + 1) % BUFFER_SIZE;
            if (nextWp == rp) break; // buffer full
            mBuffer[wp] = data[i];
            wp = nextWp;
            written++;
        }

        mWritePos.store(wp, std::memory_order_release);
        return written / 2; // return frames
    }

    /**
     * Read samples from the ring buffer.
     * @param out    Output buffer
     * @param frames Number of frames to read
     * @return Number of frames actually read
     */
    size_t read(int16_t* out, size_t frames) {
        size_t samplesCount = frames * 2;
        size_t readCount = 0;
        size_t rp = mReadPos.load(std::memory_order_relaxed);
        size_t wp = mWritePos.load(std::memory_order_acquire);

        for (size_t i = 0; i < samplesCount; ++i) {
            if (rp == wp) {
                // Underflow — fill remaining with silence
                for (size_t j = i; j < samplesCount; ++j) {
                    out[j] = 0;
                }
                break;
            }
            out[i] = mBuffer[rp];
            rp = (rp + 1) % BUFFER_SIZE;
            readCount++;
        }

        mReadPos.store(rp, std::memory_order_release);
        return readCount / 2;
    }

private:
    int16_t mBuffer[BUFFER_SIZE];
    std::atomic<size_t> mReadPos;
    std::atomic<size_t> mWritePos;
};

/**
 * OpenSL ES audio engine.
 * Creates an audio player that pulls samples from the ring buffer
 * via a buffer queue callback.
 */
class AudioEngine {
public:
    static constexpr int SAMPLE_RATE = 44100;
    static constexpr int CHANNELS = 2;
    static constexpr int FRAMES_PER_BUFFER = 1024;
    static constexpr int CALLBACK_BUFFER_SIZE = FRAMES_PER_BUFFER * CHANNELS;

    AudioEngine() : mEngineObj(nullptr), mEngine(nullptr),
                     mMixObj(nullptr), mPlayerObj(nullptr),
                     mPlayer(nullptr), mBufferQueue(nullptr),
                     mInitialized(false) {
        memset(mCallbackBuffer, 0, sizeof(mCallbackBuffer));
    }

    ~AudioEngine() { shutdown(); }

    /**
     * Initializes the OpenSL ES audio engine.
     * @param sampleRate Audio sample rate (typically 44100 or 48000)
     * @return true if initialization succeeded
     */
    bool initialize(int sampleRate = SAMPLE_RATE) {
        if (mInitialized) return true;

        SLresult result;

        // Create engine
        result = slCreateEngine(&mEngineObj, 0, nullptr, 0, nullptr, nullptr);
        if (result != SL_RESULT_SUCCESS) {
            AUDIO_LOGE("Failed to create engine: %d", result);
            return false;
        }
        (*mEngineObj)->Realize(mEngineObj, SL_BOOLEAN_FALSE);
        (*mEngineObj)->GetInterface(mEngineObj, SL_IID_ENGINE, &mEngine);

        // Create output mix
        result = (*mEngine)->CreateOutputMix(mEngine, &mMixObj, 0, nullptr, nullptr);
        if (result != SL_RESULT_SUCCESS) {
            AUDIO_LOGE("Failed to create output mix: %d", result);
            return false;
        }
        (*mMixObj)->Realize(mMixObj, SL_BOOLEAN_FALSE);

        // Configure audio source (buffer queue)
        SLDataLocator_AndroidSimpleBufferQueue locBufq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
        };
        SLDataFormat_PCM formatPcm = {
            SL_DATAFORMAT_PCM,
            static_cast<SLuint32>(CHANNELS),
            static_cast<SLuint32>(sampleRate * 1000), // milliHz
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
            SL_BYTEORDER_LITTLEENDIAN
        };
        SLDataSource audioSrc = { &locBufq, &formatPcm };

        // Configure audio sink (output mix)
        SLDataLocator_OutputMix locOutMix = { SL_DATALOCATOR_OUTPUTMIX, mMixObj };
        SLDataSink audioSnk = { &locOutMix, nullptr };

        // Create player
        const SLInterfaceID ids[] = { SL_IID_BUFFERQUEUE };
        const SLboolean req[] = { SL_BOOLEAN_TRUE };
        result = (*mEngine)->CreateAudioPlayer(
            mEngine, &mPlayerObj, &audioSrc, &audioSnk, 1, ids, req);
        if (result != SL_RESULT_SUCCESS) {
            AUDIO_LOGE("Failed to create audio player: %d", result);
            return false;
        }
        (*mPlayerObj)->Realize(mPlayerObj, SL_BOOLEAN_FALSE);
        (*mPlayerObj)->GetInterface(mPlayerObj, SL_IID_PLAY, &mPlayer);
        (*mPlayerObj)->GetInterface(mPlayerObj, SL_IID_BUFFERQUEUE, &mBufferQueue);

        // Register callback
        (*mBufferQueue)->RegisterCallback(mBufferQueue, bufferQueueCallback, this);

        // Start playback
        (*mPlayer)->SetPlayState(mPlayer, SL_PLAYSTATE_PLAYING);

        // Enqueue initial silent buffer to start the callback chain
        memset(mCallbackBuffer, 0, sizeof(mCallbackBuffer));
        (*mBufferQueue)->Enqueue(mBufferQueue, mCallbackBuffer, sizeof(mCallbackBuffer));

        mInitialized = true;
        AUDIO_LOGI("Audio engine initialized: %d Hz, %d channels", sampleRate, CHANNELS);
        return true;
    }

    /**
     * Queues audio samples from the emulator core.
     * Called from the emulation thread.
     */
    size_t queueSamples(const int16_t* data, size_t frames) {
        return mRingBuffer.write(data, frames);
    }

    /**
     * Queues a single stereo sample.
     */
    void queueSample(int16_t left, int16_t right) {
        int16_t pair[2] = { left, right };
        mRingBuffer.write(pair, 1);
    }

    void shutdown() {
        if (mPlayerObj) {
            (*mPlayer)->SetPlayState(mPlayer, SL_PLAYSTATE_STOPPED);
            (*mPlayerObj)->Destroy(mPlayerObj);
            mPlayerObj = nullptr;
            mPlayer = nullptr;
            mBufferQueue = nullptr;
        }
        if (mMixObj) {
            (*mMixObj)->Destroy(mMixObj);
            mMixObj = nullptr;
        }
        if (mEngineObj) {
            (*mEngineObj)->Destroy(mEngineObj);
            mEngineObj = nullptr;
            mEngine = nullptr;
        }
        mInitialized = false;
        AUDIO_LOGI("Audio engine shutdown");
    }

    bool isInitialized() const { return mInitialized; }

private:
    SLObjectItf mEngineObj;
    SLEngineItf mEngine;
    SLObjectItf mMixObj;
    SLObjectItf mPlayerObj;
    SLPlayItf mPlayer;
    SLAndroidSimpleBufferQueueItf mBufferQueue;
    bool mInitialized;

    AudioRingBuffer mRingBuffer;
    int16_t mCallbackBuffer[CALLBACK_BUFFER_SIZE];

    /**
     * OpenSL ES buffer queue callback.
     * Called from the audio thread when a buffer is consumed.
     * Pulls samples from the ring buffer and enqueues them.
     */
    static void bufferQueueCallback(
        SLAndroidSimpleBufferQueueItf bq, void* context
    ) {
        auto* engine = static_cast<AudioEngine*>(context);
        engine->mRingBuffer.read(engine->mCallbackBuffer, FRAMES_PER_BUFFER);
        (*bq)->Enqueue(bq, engine->mCallbackBuffer, sizeof(engine->mCallbackBuffer));
    }
};

} // namespace elysium

#endif // ELYSIUM_AUDIO_ENGINE_H
