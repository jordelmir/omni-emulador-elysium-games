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
        , mEffectId(0)
        , mUpscaleMode(0)
        , mInitialized(false) {}

    ~HardwareBufferRenderer() {
        release();
    }

    /**
     * Allocates an AHardwareBuffer with the specified dimensions.
     */
    bool initialize(uint32_t width, uint32_t height) {
        if (mInitialized) {
            release();
        }

        mWidth = width;
        mHeight = height;

        // Omni-Scale Ultra: Determine buffer size based on upscaling mode
        uint32_t bufferWidth = width;
        uint32_t bufferHeight = height;
        if (mUpscaleMode == 1) { // Scale2x
            bufferWidth *= 2;
            bufferHeight *= 2;
        }

        // Describe the hardware buffer: RGBA8888 for broad compatibility
        AHardwareBuffer_Desc desc = {};
        desc.width = bufferWidth;
        desc.height = bufferHeight;
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

        if (!createEglImage()) {
            AHardwareBuffer_release(mBuffer);
            mBuffer = nullptr;
            return false;
        }

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
     */
    void updateFrame(const void* data, uint32_t width, uint32_t height, size_t pitch) {
        if (!mInitialized || !data || !mBuffer) {
            return;
        }

        void* mappedPtr = nullptr;
        int result = AHardwareBuffer_lock(mBuffer,
                                           AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                                           -1, nullptr, &mappedPtr);
        if (result != 0 || !mappedPtr) {
            HBR_LOGE("AHardwareBuffer_lock failed: %d", result);
            return;
        }

        AHardwareBuffer_Desc desc;
        AHardwareBuffer_describe(mBuffer, &desc);

        const uint8_t* src = static_cast<const uint8_t*>(data);
        uint8_t* dst = static_cast<uint8_t*>(mappedPtr);
        
        if (mUpscaleMode == 1) { // Omni-Scale Ultra (Scale2x)
             const uint32_t* srcPixels = reinterpret_cast<const uint32_t*>(src);
             uint32_t* dstPixels = reinterpret_cast<uint32_t*>(dst);
             uint32_t srcStride = pitch / 4;
             uint32_t rowStride = desc.stride;

             for (uint32_t y = 0; y < height; ++y) {
                 for (uint32_t x = 0; x < width; ++x) {
                     uint32_t P = srcPixels[y * srcStride + x];
                     uint32_t B = (y > 0) ? srcPixels[(y-1) * srcStride + x] : P;
                     uint32_t D = (x > 0) ? srcPixels[y * srcStride + (x-1)] : P;
                     uint32_t F = (x < width - 1) ? srcPixels[y * srcStride + (x+1)] : P;
                     uint32_t H = (y < height - 1) ? srcPixels[(y+1) * srcStride + x] : P;

                     uint32_t e0 = (B == D && B != H && D != F) ? B : P;
                     uint32_t e1 = (B == F && B != H && F != D) ? B : P;
                     uint32_t e2 = (D == H && D != F && H != B) ? D : P;
                     uint32_t e3 = (F == H && F != B && H != D) ? F : P;

                     uint32_t dy = y * 2;
                     uint32_t dx = x * 2;
                     dstPixels[dy * rowStride + dx] = e0;
                     dstPixels[dy * rowStride + (dx+1)] = e1;
                     dstPixels[(dy+1) * rowStride + dx] = e2;
                     dstPixels[(dy+1) * rowStride + (dx+1)] = e3;
                 }
             }
        } else {
            uint32_t copyWidth = width * 4;
            uint32_t bufferStride = desc.stride * 4;
            for (uint32_t y = 0; y < height; ++y) {
                if (mEffectId == 1 && (y % 2 != 0)) {
                    const uint32_t* srcRow = reinterpret_cast<const uint32_t*>(src);
                    uint32_t* dstRow = reinterpret_cast<uint32_t*>(dst);
                    for (uint32_t x = 0; x < width; ++x) {
                        uint32_t p = srcRow[x];
                        dstRow[x] = (p & 0xFF000000) | ((p & 0x00FEFEFE) >> 1);
                    }
                } else {
                    memcpy(dst, src, copyWidth);
                }
                src += pitch;
                dst += bufferStride;
            }
        }

        AHardwareBuffer_unlock(mBuffer, nullptr);
    }

    void setVisualEffect(int effectId) {
        mEffectId = effectId;
        HBR_LOGI("Visual effect set to: %d", effectId);
    }

    void setUpscaleMode(int mode) {
        mUpscaleMode = mode;
        HBR_LOGI("Upscale mode set to: %d", mode);
    }

    /**
     * Bind this texture to render the emulator frame.
     */
    GLuint getTextureId() const { return mTextureId; }

    AHardwareBuffer* getBuffer() const { return mBuffer; }

    bool isInitialized() const { return mInitialized; }

    void release() {
        if (mProgram != 0) {
            glDeleteProgram(mProgram);
            mProgram = 0;
        }
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

    /**
     * Renders the texture to the current framebuffer using the active shader.
     */
    void render(int screenWidth, int screenHeight) {
        if (!mInitialized || mTextureId == 0) return;

        if (mProgram == 0) {
            mProgram = createProgram(mVertexShader, mFragmentShader);
            if (mProgram == 0) return;
        }

        glUseProgram(mProgram);
        glViewport(0, 0, screenWidth, screenHeight);

        GLint posLoc = glGetAttribLocation(mProgram, "aPosition");
        GLint texLoc = glGetAttribLocation(mProgram, "aTexCoord");
        GLint sampLoc = glGetUniformLocation(mProgram, "sTexture");
        GLint resLoc = glGetUniformLocation(mProgram, "uResolution");
        GLint effectLoc = glGetUniformLocation(mProgram, "uEffectId");

        glEnableVertexAttribArray(posLoc);
        glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 0, mQuadCoords);

        glEnableVertexAttribArray(texLoc);
        glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 0, mTexCoords);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureId);
        glUniform1i(sampLoc, 0);
        glUniform2f(resLoc, (float)screenWidth, (float)screenHeight);
        glUniform1i(effectLoc, mEffectId);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        glDisableVertexAttribArray(posLoc);
        glDisableVertexAttribArray(texLoc);
    }

private:
    AHardwareBuffer* mBuffer;
    EGLImageKHR mEglImage;
    GLuint mTextureId;
    GLuint mProgram = 0;
    uint32_t mWidth;
    uint32_t mHeight;
    int mEffectId;
    int mUpscaleMode;
    bool mInitialized;

    const char* mVertexShader = R"(
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    )";

    const char* mFragmentShader = R"(
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES sTexture;
        uniform vec2 uResolution;
        uniform int uEffectId;

        // CRT Curve function
        vec2 curve(vec2 uv) {
            uv = (uv - 0.5) * 2.0;
            uv *= 1.1;	
            uv.x *= 1.0 + pow((abs(uv.y) / 5.0), 2.0);
            uv.y *= 1.0 + pow((abs(uv.x) / 4.0), 2.0);
            uv  = (uv / 2.0) + 0.5;
            uv =  uv * 0.92 + 0.04;
            return uv;
        }

        void main() {
            vec2 uv = vTexCoord;
            
            if (uEffectId == 1) { // 📺 Vanguard RETRO (Professional CRT)
                vec2 crtUV = curve(uv);
                
                // Bezel / Curvature border
                if (crtUV.x < 0.0 || crtUV.x > 1.0 || crtUV.y < 0.0 || crtUV.y > 1.0) {
                    gl_FragColor = vec4(0.01, 0.01, 0.01, 1.0);
                    return;
                }

                // Subpixel RGB Shift (Chroma Bleed)
                float offset = 0.0015;
                vec3 color;
                color.r = texture2D(sTexture, crtUV + vec2(offset, 0.0)).r;
                color.g = texture2D(sTexture, crtUV).g;
                color.b = texture2D(sTexture, crtUV - vec2(offset, 0.0)).b;

                // 224p-tuned Scanlines
                float scanline = sin(crtUV.y * 224.0 * 3.14159 * 2.0);
                float scanlineWeight = 0.20;
                color -= color * clamp(scanline * scanlineWeight + scanlineWeight, 0.0, 1.0);

                // Phosphor Shadow Mask (Trinitron style)
                float mask = mod(gl_FragCoord.x, 3.0);
                if (mask < 1.0) { color.r *= 0.9; color.g *= 0.95; }
                else if (mask < 2.0) { color.g *= 0.9; color.b *= 0.95; }
                else { color.b *= 0.9; color.r *= 0.95; }

                // Dynamic Vignette Compensation
                vec2 center = crtUV - 0.5;
                float dist = dot(center, center);
                color *= 1.0 - (dist * 0.6);
                
                // Bloom compensation
                color *= 1.25;

                gl_FragColor = vec4(color, 1.0);

            } else if (uEffectId == 2) { // ✨ Vanguard MODERN (Sharp + Bloom)
                vec4 centerCol = texture2D(sTexture, uv);
                vec4 leftCol = texture2D(sTexture, uv - vec2(1.0/uResolution.x, 0.0));
                vec4 rightCol = texture2D(sTexture, uv + vec2(1.0/uResolution.x, 0.0));
                
                // Adaptive Sharpening
                vec4 sharpen = centerCol * 2.0 - (leftCol + rightCol) * 0.5;
                vec4 finalColor = mix(centerCol, sharpen, 0.4);
                
                // Subtle Bloom on bright areas
                float brightness = dot(finalColor.rgb, vec3(0.2126, 0.7152, 0.0722));
                if (brightness > 0.8) {
                    finalColor.rgb += centerCol.rgb * 0.2;
                }
                
                gl_FragColor = finalColor;

            } else if (uEffectId == 3) { // 🌑 Vanguard OLED (Deep Black + Vibrant)
                vec4 color = texture2D(sTexture, uv);
                
                // Enhance saturation and contrast for OLED
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                color.rgb = mix(vec3(gray), color.rgb, 1.2); // Saturation
                color.rgb = pow(color.rgb, vec3(1.1));       // Contrast boost
                
                // Pure black preservation: drop very low intensities
                if (length(color.rgb) < 0.05) {
                    color.rgb = vec3(0.0);
                }
                
                gl_FragColor = color;

            } else {
                gl_FragColor = texture2D(sTexture, uv);
            }
        }
    )";

    const float mQuadCoords[8] = { -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f };
    const float mTexCoords[8] = { 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f };

    GLuint createProgram(const char* vertexSource, const char* fragmentSource) {
        GLuint vertexShader = loadShader(GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;

        GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) return 0;

        GLuint program = glCreateProgram();
        if (program != 0) {
            glAttachShader(program, vertexShader);
            glAttachShader(program, fragmentShader);
            glLinkProgram(program);
            GLint linkStatus = GL_FALSE;
            glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
            if (linkStatus != GL_TRUE) {
                HBR_LOGE("Could not link program");
                glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    GLuint loadShader(GLenum shaderType, const char* source) {
        GLuint shader = glCreateShader(shaderType);
        if (shader != 0) {
            glShaderSource(shader, 1, &source, nullptr);
            glCompileShader(shader);
            GLint compiled = 0;
            glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
            if (!compiled) {
                HBR_LOGE("Could not compile shader %d", shaderType);
                glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    bool createEglImage() {
        auto eglGetNativeClientBufferANDROID =
            reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(
                eglGetProcAddress("eglGetNativeClientBufferANDROID"));

        if (!eglGetNativeClientBufferANDROID) return false;

        EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(mBuffer);
        if (!clientBuffer) return false;

        EGLDisplay display = eglGetCurrentDisplay();
        if (display == EGL_NO_DISPLAY) return false;

        EGLint attrs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE };

        auto eglCreateImageKHR =
            reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(
                eglGetProcAddress("eglCreateImageKHR"));

        if (!eglCreateImageKHR) return false;

        mEglImage = eglCreateImageKHR(display, EGL_NO_CONTEXT,
                                       EGL_NATIVE_BUFFER_ANDROID,
                                       clientBuffer, attrs);

        return (mEglImage != EGL_NO_IMAGE_KHR);
    }

    bool createTexture() {
        auto glEGLImageTargetTexture2DOES =
            reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
                eglGetProcAddress("glEGLImageTargetTexture2DOES"));

        if (!glEGLImageTargetTexture2DOES) return false;

        glGenTextures(1, &mTextureId);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureId);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glEGLImageTargetTexture2DOES(GL_TEXTURE_EXTERNAL_OES,
                                      static_cast<GLeglImageOES>(mEglImage));

        return (glGetError() == GL_NO_ERROR);
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
