// ═══════════════════════════════════════════════════════════════
// Elysium Console — Zero-Copy Hardware Buffer Renderer
// ═══════════════════════════════════════════════════════════════
// Manages AHardwareBuffer allocation and mapping for zero-copy
// frame transfer from Libretro cores to the GPU via EGL.
// ═══════════════════════════════════════════════════════════════

#ifndef ELYSIUM_HARDWARE_BUFFER_RENDERER_H
#define ELYSIUM_HARDWARE_BUFFER_RENDERER_H

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <string.h>

#define HBR_TAG "ElysiumHBR"
#define HBR_LOGI(...) __android_log_print(ANDROID_LOG_INFO, HBR_TAG, __VA_ARGS__)
#define HBR_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, HBR_TAG, __VA_ARGS__)

namespace elysium {

/**
 * Zero-Copy renderer using AHardwareBuffer + EGL for direct frame
 * presentation from emulator cores without intermediate memcpy.
 */
class HardwareBufferRenderer {
public:
    HardwareBufferRenderer()
        : mBuffer(nullptr)
        , mEglImage(EGL_NO_IMAGE_KHR)
        , mTextureId(0)
        , mWidth(0)
        , mHeight(0)
        , mInitialized(false) {}

    ~HardwareBufferRenderer() {
        release();
    }

    /**
     * Allocates an AHardwareBuffer with the specified dimensions.
     * The buffer is configured for both CPU write and GPU sampling,
     * enabling zero-copy transfer from the emulator core to OpenGL.
     *
     * @param width   Frame width in pixels
     * @param height  Frame height in pixels
     * @return        true if allocation succeeded
     */
    bool initialize(uint32_t width, uint32_t height) {
        if (mInitialized) {
            release();
        }

        mWidth = width;
        mHeight = height;

        // Describe the hardware buffer: RGBA8888 for broad compatibility
        AHardwareBuffer_Desc desc = {};
        desc.width = width;
        desc.height = height;
        desc.layers = 1;
        desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
        desc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN
                    | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
        desc.stride = 0; // Let the system decide

        int result = AHardwareBuffer_allocate(&desc, &mBuffer);
        if (result != 0 || !mBuffer) {
            HBR_LOGE("AHardwareBuffer_allocate failed: %d", result);
            return false;
        }

        HBR_LOGI("Allocated AHardwareBuffer: %ux%u", width, height);

        // Create EGL image from the hardware buffer for GPU texture binding
        if (!createEglImage()) {
            AHardwareBuffer_release(mBuffer);
            mBuffer = nullptr;
            return false;
        }

        // Create and bind OpenGL texture
        if (!createTexture()) {
            destroyEglImage();
            AHardwareBuffer_release(mBuffer);
            mBuffer = nullptr;
            return false;
        }

        mInitialized = true;
        return true;
    }

    /**
     * Updates the hardware buffer with frame data from the Libretro core.
     * This locks the buffer for CPU write, copies the frame, then unlocks.
     * The corresponding GL texture is automatically updated via EGL image.
     *
     * @param data    Pointer to XRGB8888 frame data from the core
     * @param width   Frame width (must match initialized width)
     * @param height  Frame height (must match initialized height)
     * @param pitch   Row stride in bytes from the core
     */
    void updateFrame(const void* data, uint32_t width, uint32_t height, size_t pitch) {
        if (!mInitialized || !data || !mBuffer) {
            return;
        }

        void* mappedPtr = nullptr;
        int result = AHardwareBuffer_lock(mBuffer,
                                           AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                                           -1, // no fence
                                           nullptr, // entire region
                                           &mappedPtr);
        if (result != 0 || !mappedPtr) {
            HBR_LOGE("AHardwareBuffer_lock failed: %d", result);
            return;
        }

        // Get actual stride from the buffer description
        AHardwareBuffer_Desc desc;
        AHardwareBuffer_describe(mBuffer, &desc);
        uint32_t dstStride = desc.stride * 4; // 4 bytes per RGBA pixel

        // Copy frame data row-by-row (handles different strides)
        const uint8_t* src = static_cast<const uint8_t*>(data);
        uint8_t* dst = static_cast<uint8_t*>(mappedPtr);
        uint32_t copyWidth = width * 4; // 4 bytes per pixel

        for (uint32_t y = 0; y < height; ++y) {
            memcpy(dst, src, copyWidth);
            src += pitch;
            dst += dstStride;
        }

        AHardwareBuffer_unlock(mBuffer, nullptr);
    }

    /**
     * Returns the OpenGL texture ID that is backed by the AHardwareBuffer.
     * Bind this texture to render the emulator frame.
     */
    GLuint getTextureId() const { return mTextureId; }

    /**
     * Returns the AHardwareBuffer pointer for JNI interop.
     */
    AHardwareBuffer* getBuffer() const { return mBuffer; }

    bool isInitialized() const { return mInitialized; }

    /**
     * Releases all resources: GL texture, EGL image, and AHardwareBuffer.
     */
    void release() {
        if (mTextureId != 0) {
            glDeleteTextures(1, &mTextureId);
            mTextureId = 0;
        }
        destroyEglImage();
        if (mBuffer) {
            AHardwareBuffer_release(mBuffer);
            mBuffer = nullptr;
        }
        mInitialized = false;
        HBR_LOGI("HardwareBufferRenderer released");
    }

private:
    AHardwareBuffer* mBuffer;
    EGLImageKHR mEglImage;
    GLuint mTextureId;
    uint32_t mWidth;
    uint32_t mHeight;
    bool mInitialized;

    /**
     * Creates an EGLImageKHR from the AHardwareBuffer using the
     * EGL_ANDROID_get_native_client_buffer extension.
     */
    bool createEglImage() {
        // Get the EGLClientBuffer from the AHardwareBuffer
        auto eglGetNativeClientBufferANDROID =
            reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(
                eglGetProcAddress("eglGetNativeClientBufferANDROID"));

        if (!eglGetNativeClientBufferANDROID) {
            HBR_LOGE("eglGetNativeClientBufferANDROID not available");
            return false;
        }

        EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(mBuffer);
        if (!clientBuffer) {
            HBR_LOGE("eglGetNativeClientBufferANDROID returned null");
            return false;
        }

        // Create EGL image
        EGLDisplay display = eglGetCurrentDisplay();
        if (display == EGL_NO_DISPLAY) {
            HBR_LOGE("No current EGL display");
            return false;
        }

        EGLint attrs[] = {
            EGL_IMAGE_PRESERVED_KHR, EGL_TRUE,
            EGL_NONE
        };

        auto eglCreateImageKHR =
            reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(
                eglGetProcAddress("eglCreateImageKHR"));

        if (!eglCreateImageKHR) {
            HBR_LOGE("eglCreateImageKHR not available");
            return false;
        }

        mEglImage = eglCreateImageKHR(display, EGL_NO_CONTEXT,
                                       EGL_NATIVE_BUFFER_ANDROID,
                                       clientBuffer, attrs);

        if (mEglImage == EGL_NO_IMAGE_KHR) {
            HBR_LOGE("eglCreateImageKHR failed: 0x%x", eglGetError());
            return false;
        }

        HBR_LOGI("EGL image created successfully");
        return true;
    }

    /**
     * Creates a GL texture and binds the EGL image to it using
     * glEGLImageTargetTexture2DOES.
     */
    bool createTexture() {
        auto glEGLImageTargetTexture2DOES =
            reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
                eglGetProcAddress("glEGLImageTargetTexture2DOES"));

        if (!glEGLImageTargetTexture2DOES) {
            HBR_LOGE("glEGLImageTargetTexture2DOES not available");
            return false;
        }

        glGenTextures(1, &mTextureId);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureId);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glEGLImageTargetTexture2DOES(GL_TEXTURE_EXTERNAL_OES,
                                      static_cast<GLeglImageOES>(mEglImage));

        GLenum err = glGetError();
        if (err != GL_NO_ERROR) {
            HBR_LOGE("GL error after EGLImage bind: 0x%x", err);
            return false;
        }

        HBR_LOGI("GL texture %u created and bound to EGL image", mTextureId);
        return true;
    }

    void destroyEglImage() {
        if (mEglImage != EGL_NO_IMAGE_KHR) {
            auto eglDestroyImageKHR =
                reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(
                    eglGetProcAddress("eglDestroyImageKHR"));
            if (eglDestroyImageKHR) {
                EGLDisplay display = eglGetCurrentDisplay();
                if (display != EGL_NO_DISPLAY) {
                    eglDestroyImageKHR(display, mEglImage);
                }
            }
            mEglImage = EGL_NO_IMAGE_KHR;
        }
    }
};

} // namespace elysium

#endif // ELYSIUM_HARDWARE_BUFFER_RENDERER_H
