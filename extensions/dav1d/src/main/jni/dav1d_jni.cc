/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#if (defined(__arm__) || defined(_M_ARM))
#define CPU_FEATURES_ARCH_ARM
#endif
#if defined(__aarch64__)
#define CPU_FEATURES_ARCH_AARCH64
#endif

#if (defined(CPU_FEATURES_ARCH_AARCH64) || defined(CPU_FEATURES_ARCH_ARM))
#define CPU_FEATURES_ARCH_ANY_ARM
#endif

#if defined(CPU_FEATURES_ARCH_ANY_ARM)
#if defined(__ARM_NEON__)
#define CPU_FEATURES_COMPILED_ANY_ARM_NEON 1
#else
#define CPU_FEATURES_COMPILED_ANY_ARM_NEON 0
#endif  //  defined(__ARM_NEON__)
#endif  //  defined(CPU_FEATURES_ARCH_ANY_ARM)

#ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
#include <arm_neon.h>
#endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON
#include <jni.h>

#include <cstring>
#include <mutex>  // NOLINT
#include <new>
#include <common/mem.h>

#include "dav1d/dav1d.h"

#define LOG_TAG "dav1d_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...)                         \
  extern "C" {                                                       \
  JNIEXPORT RETURN_TYPE                                              \
      Java_com_google_android_exoplayer2_ext_dav1d_Dav1dDecoder_##NAME( \
          JNIEnv* env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                                  \
  JNIEXPORT RETURN_TYPE                                              \
      Java_com_google_android_exoplayer2_ext_dav1d_Dav1dDecoder_##NAME( \
          JNIEnv* env, jobject thiz, ##__VA_ARGS__)

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

namespace {
enum dataRenderMode {
  kDirectRenderSurface = 0,
  kFirstConvertToBuffer = 1,
};
const dataRenderMode defaultRendeMode = kFirstConvertToBuffer;
// YUV plane indices.
const int kPlaneY = 0;
const int kPlaneU = 1;
const int kPlaneV = 2;
const int kMaxPlanes = 3;

// Android YUV format. See:
// https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12.
const int kImageFormatYV12 = 0x32315659;

// LINT.IfChange
// Output modes.
const int kOutputModeYuv = 0;
const int kOutputModeSurfaceYuv = 1;
// LINT.ThenChange(../../../../../library/core/src/main/java/com/google/android/exoplayer2/C.java)

// LINT.IfChange
const int kColorSpaceUnknown = 0;
// LINT.ThenChange(../../../../../library/core/src/main/java/com/google/android/exoplayer2/video/VideoDecoderOutputBuffer.java)

// LINT.IfChange
// Return codes for jni methods.
const int kStatusError = 0;
const int kStatusOk = 1;
const int kStatusDecodeOnly = 2;
const int kStatusDecodeAgain = 3;
// LINT.ThenChange(../java/com/google/android/exoplayer2/ext/av1/Dav1dDecoder.java)

// Status codes specific to the JNI wrapper code.
enum JniStatusCode {
  kJniStatusOk = 0,
  kJniStatusOutOfMemory = -1,
  kJniStatusBufferAlreadyReleased = -2,
  kJniStatusInvalidNumOfPlanes = -3,
  kJniStatusBitDepth12NotSupportedWithYuv = -4,
  kJniStatusHighBitDepthNotSupportedWithSurfaceYuv = -5,
  kJniStatusANativeWindowError = -6,
  kJniStatusBufferResizeError = -7,
  kJniStatusNeonNotSupported = -8
};

const char *GetJniErrorMessage(JniStatusCode error_code) {
  switch (error_code) {
    case kJniStatusOutOfMemory:
      return "Out of memory.";
    case kJniStatusBufferAlreadyReleased:
      return "JNI buffer already released.";
    case kJniStatusBitDepth12NotSupportedWithYuv:
      return "Bit depth 12 is not supported with YUV.";
    case kJniStatusHighBitDepthNotSupportedWithSurfaceYuv:
      return "High bit depth (10 or 12 bits per pixel) output format is not "
             "supported with YUV surface.";
    case kJniStatusInvalidNumOfPlanes:
      return "Libdav1d decoded buffer has invalid number of planes.";
    case kJniStatusANativeWindowError:
      return "ANativeWindow error.";
    case kJniStatusBufferResizeError:
      return "Buffer resize failed.";
    case kJniStatusNeonNotSupported:
      return "Neon is not supported.";
    default:
      return "Unrecognized error code.";
  }
}

// This structure represents an allocated frame buffer.
typedef struct Libdav1dFrameBuffer {
  // In the |data| and |size| arrays
  uint8_t* data;    // Pointers to the data buffers.
  size_t size;      // Sizes of the data buffers in bytes.
  void* private_data;  // Frame buffer's private data. Available for use by the
  // release frame buffer callback.
} Libdav1dFrameBuffer;

// Manages Libdav1dFrameBuffer and reference information.
class JniFrameBuffer {
 public:
  explicit JniFrameBuffer(int id) : id_(id), reference_count_(0) {
    dav1DFrameBuffer.private_data = &id_;
  }
  ~JniFrameBuffer() {
    dav1d_free_aligned(dav1DFrameBuffer.data);
  }

  void SetFrameData(const Dav1dPicture& decoder_buffer) {
    int numPlanes = decoder_buffer.p.layout == DAV1D_PIXEL_LAYOUT_I400 ? 1 : 3;
    for (int plane_index = kPlaneY; plane_index < numPlanes;
         plane_index++) {
      stride_[plane_index] = plane_index == (numPlanes - 1) ? decoder_buffer.stride[plane_index - 1] : decoder_buffer.stride[plane_index];
      plane_[plane_index] = (uint8_t*)decoder_buffer.data[plane_index];
      displayed_width_[plane_index] = plane_index == 0 ?
          decoder_buffer.p.w : decoder_buffer.p.w / 2;
      displayed_height_[plane_index] = plane_index == 0 ?
          decoder_buffer.p.h : decoder_buffer.p.h / 2;
    }
  }

  int Stride(int plane_index) const { return stride_[plane_index]; }
  uint8_t* Plane(int plane_index) const { return plane_[plane_index]; }
  int DisplayedWidth(int plane_index) const {
    return displayed_width_[plane_index];
  }
  int DisplayedHeight(int plane_index) const {
    return displayed_height_[plane_index];
  }

  // Methods maintaining reference count are not thread-safe. They must be
  // called with a lock held.
  void AddReference() {
    ++reference_count_;
  }
  void RemoveReference() {
    reference_count_--;
  }
  int GetReference() {
    return reference_count_;
  }
  bool InUse() const { return reference_count_ != 0; }

  const Libdav1dFrameBuffer& GetDav1dFrameBuffer() const {
    return dav1DFrameBuffer;
  }

  // Attempts to reallocate data planes if the existing ones don't have enough
  // capacity. Returns true if the allocation was successful or wasn't needed,
  // false if the allocation failed.
  bool MaybeReallocateDav1dDataPlanes(Dav1dPicture *const p) {
    //copy from dav1d_default_picture_alloc
    const int hbd = p->p.bpc > 8;
    const int aligned_w = (p->p.w + 127) & ~127;
    const int aligned_h = (p->p.h + 127) & ~127;
    const int has_chroma = p->p.layout != DAV1D_PIXEL_LAYOUT_I400;
    const int ss_ver = p->p.layout == DAV1D_PIXEL_LAYOUT_I420;
    const int ss_hor = p->p.layout != DAV1D_PIXEL_LAYOUT_I444;
    ptrdiff_t y_stride = aligned_w << hbd;
    ptrdiff_t uv_stride = has_chroma ? y_stride >> ss_hor : 0;
    /* Due to how mapping of addresses to sets works in most L1 and L2 cache
     * implementations, strides of multiples of certain power-of-two numbers
     * may cause multiple rows of the same superblock to map to the same set,
     * causing evictions of previous rows resulting in a reduction in cache
     * hit rate. Avoid that by slightly padding the stride when necessary. */
    if (!(y_stride & 1023))
      y_stride += DAV1D_PICTURE_ALIGNMENT;
    if (!(uv_stride & 1023) && has_chroma)
      uv_stride += DAV1D_PICTURE_ALIGNMENT;
    p->stride[0] = y_stride;
    p->stride[1] = uv_stride;
    const size_t y_sz = y_stride * aligned_h;
    const size_t uv_sz = uv_stride * (aligned_h >> ss_ver);
    const size_t pic_size = y_sz + 2 * uv_sz + DAV1D_PICTURE_ALIGNMENT;

    if(dav1DFrameBuffer.size < pic_size) {
      if(dav1DFrameBuffer.data)
        dav1d_free_aligned(dav1DFrameBuffer.data);

      dav1DFrameBuffer.data = NULL;
      dav1DFrameBuffer.size = 0;

      dav1DFrameBuffer.data = (uint8_t *)dav1d_alloc_aligned(pic_size, DAV1D_PICTURE_ALIGNMENT);
      if (!dav1DFrameBuffer.data) return false;

      dav1DFrameBuffer.size = pic_size;
    }

    p->data[0] = dav1DFrameBuffer.data;
    p->data[1] = has_chroma ? dav1DFrameBuffer.data + y_sz : NULL;
    p->data[2] = has_chroma ? dav1DFrameBuffer.data + y_sz + uv_sz : NULL;

    p->allocator_data = dav1DFrameBuffer.private_data;

    return true;
  }

 private:
  int stride_[kMaxPlanes];
  uint8_t* plane_[kMaxPlanes];
  int displayed_width_[kMaxPlanes];
  int displayed_height_[kMaxPlanes];
  int id_;
  int reference_count_;
  Libdav1dFrameBuffer dav1DFrameBuffer = {};
};


// Manages frame buffers used by libdav1d decoder and ExoPlayer.
// Handles synchronization between libdav1d and ExoPlayer threads.
class JniBufferManager {
 public:
  ~JniBufferManager() {
    // This lock does not do anything since libdav1d has released all the frame
    // buffers. It exists to merely be consistent with all other usage of
    // |all_buffers_| and |all_buffer_count_|.
    std::lock_guard<std::mutex> lock(mutex_);
    while (all_buffer_count_--) {
      delete all_buffers_[all_buffer_count_];
    }
  }

  JniStatusCode GetBuffer(Dav1dPicture *const p) {
    std::lock_guard<std::mutex> lock(mutex_);

    JniFrameBuffer* output_buffer;
    if (free_buffer_count_) {
      output_buffer = free_buffers_[--free_buffer_count_];
    } else if (all_buffer_count_ < kMaxFrames) {
      output_buffer = new (std::nothrow) JniFrameBuffer(all_buffer_count_);
      if (output_buffer == nullptr) return kJniStatusOutOfMemory;
      all_buffers_[all_buffer_count_++] = output_buffer;
    } else {
      // Maximum number of buffers is being used.
      return kJniStatusOutOfMemory;
    }
    if (!output_buffer->MaybeReallocateDav1dDataPlanes(p)) {
      return kJniStatusOutOfMemory;
    }

    output_buffer->AddReference();

    return kJniStatusOk;
  }

  JniFrameBuffer* GetBuffer(int id) const { return all_buffers_[id]; }

  void AddBufferReference(int id) {
    std::lock_guard<std::mutex> lock(mutex_);
    all_buffers_[id]->AddReference();
  }

  JniStatusCode ReleaseBuffer(int id) {
    std::lock_guard<std::mutex> lock(mutex_);
    JniFrameBuffer* buffer = all_buffers_[id];
    if (!buffer->InUse()) {
      return kJniStatusBufferAlreadyReleased;
    }
    buffer->RemoveReference();
    if (!buffer->InUse()) {
      free_buffers_[free_buffer_count_++] = buffer;
    }
    return kJniStatusOk;
  }

  void flushBuffers() {
    std::lock_guard<std::mutex> lock(mutex_);
    while (all_buffer_count_--) {
      delete all_buffers_[all_buffer_count_];
    }
    all_buffer_count_ = 0;
    free_buffer_count_ = 0;
  }

 private:
  static const int kMaxFrames = 32;

  JniFrameBuffer* all_buffers_[kMaxFrames];
  int all_buffer_count_ = 0;

  JniFrameBuffer* free_buffers_[kMaxFrames];
  int free_buffer_count_ = 0;

  std::mutex mutex_;
};

typedef struct Libdav1dContext {
  Dav1dContext *c;
  int pool_size;

  Dav1dData data;
  int tile_threads;
  int frame_threads;
  int apply_grain;
  int operating_point;
  int all_layers;
} Libdav1dContext;

typedef struct bufferque {
  uint8_t *data;
  int size;
  int64_t time;
}bufferque;

struct JniContext {
  JniContext(int threads) {
    Libdav1dContext *libdav1DContext = new Libdav1dContext();
    libdav1DContext->tile_threads = threads;
    libdav1DContext->frame_threads = threads;
    priv_data = libdav1DContext;

    render_mode = defaultRendeMode;
  }
  ~JniContext() {
    if (native_window) {
      ANativeWindow_release(native_window);
    }
    if(priv_data != NULL) {
      Libdav1dContext *libdav1DContext = (Libdav1dContext *)priv_data;
      delete libdav1DContext;
      priv_data = NULL;
    }
  }

  bool MaybeAcquireNativeWindow(JNIEnv* env, jobject new_surface) {
    if (surface == new_surface) {
      return true;
    }
    if (native_window) {
      ANativeWindow_release(native_window);
      native_window = NULL;
    }
    native_window_width = 0;
    native_window_height = 0;

    if(new_surface) {
      native_window = ANativeWindow_fromSurface(env, new_surface);
      if (native_window == nullptr) {
        jni_status_code = kJniStatusANativeWindowError;
        surface = nullptr;
        return false;
      }
    }
    surface = new_surface;
    return true;
  }

  void setRenderMode(dataRenderMode mode) {
    render_mode = mode;
  }

  jfieldID decoder_private_field;
  jfieldID output_mode_field;
  jfieldID data_field;
  jfieldID width_field;
  jfieldID height_field;
  jfieldID stride_field;
  jmethodID init_for_private_frame_method;
  jmethodID init_for_yuv_frame_method;
  jmethodID init_method;

  // The libdav1d instance has to be closed before |buffer_manager| is
  // destructed. This will make sure that libdav1d releases all the frame
  // buffers that it might be holding references to.
  JniBufferManager buffer_manager;

  ANativeWindow* native_window = nullptr;
  jobject surface = nullptr;
  int native_window_width = 0;
  int native_window_height = 0;
  void *priv_data;

  int libdav1d_status_code = 0;
  JniStatusCode jni_status_code = kJniStatusOk;

  dataRenderMode render_mode;
};
int Libdav1d_picture_alloc(Dav1dPicture * p, void * cookie) {
  if(cookie == NULL) {
    return DAV1D_ERR(EINVAL);
  }

  JniContext* const context = reinterpret_cast<JniContext*>(cookie);

  context->jni_status_code = context->buffer_manager.GetBuffer(p);
  if (context->jni_status_code != kJniStatusOk) {
    LOGE("%s", GetJniErrorMessage(context->jni_status_code));
    return -1;
  }

  return 0;
}

void Libdav1d_picture_release(Dav1dPicture * p, void * cookie) {
  if(cookie == NULL) {
    return ;
  }
  JniContext* const context = reinterpret_cast<JniContext*>(cookie);
  const int buffer_id = *reinterpret_cast<int*>(p->allocator_data);
  context->jni_status_code = context->buffer_manager.ReleaseBuffer(buffer_id);
  if (context->jni_status_code != kJniStatusOk) {
    LOGE("%s", GetJniErrorMessage(context->jni_status_code));
    return ;
  }
  return ;

}

constexpr int AlignTo16(int value) { return (value + 15) & (~15); }

// return the current time in millisecond : 1/1000 second
static inline int64_t getCurrentTime()
{
  struct timeval tv;
  gettimeofday(&tv,NULL);
  return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

void CopyPlane(const uint8_t* source, int source_stride, uint8_t* destination,
               int destination_stride, int width, int height) {
  while (height--) {
    std::memcpy(destination, source, width);
    source += source_stride;
    destination += destination_stride;
  }
}

void CopyFrameToDataBuffer(const Dav1dPicture* decoder_buffer,
                           jbyte* data) {
  int numPlanes = decoder_buffer->p.layout == DAV1D_PIXEL_LAYOUT_I400 ? 1 : 3;
  for (int plane_index = kPlaneY; plane_index < numPlanes;
       plane_index++) {
    int height = plane_index == 0 ? decoder_buffer->p.h : decoder_buffer->p.h / 2;
    const uint64_t length = decoder_buffer->stride[plane_index == numPlanes - 1 ? plane_index-1 : plane_index] * height;
    memcpy(data, decoder_buffer->data[plane_index], length);
    data += length;
  }
}

void Convert10BitFrameTo8BitDataBuffer(
    const Dav1dPicture* decoder_buffer, jbyte* data) {
  int numPlanes = decoder_buffer->p.layout == DAV1D_PIXEL_LAYOUT_I400 ? 1 : 3;
  for (int plane_index = kPlaneY; plane_index < numPlanes;
       plane_index++) {
    int sample = 0;
    const uint8_t* source = (uint8_t*)decoder_buffer->data[plane_index];
    int height = plane_index == 0 ? decoder_buffer->p.h : decoder_buffer->p.h / 2;
    int width = plane_index == 0 ? decoder_buffer->p.w : decoder_buffer->p.w / 2;
    for (int i = 0; i < height; i++) {
      const uint16_t* source_16 = reinterpret_cast<const uint16_t*>(source);
      for (int j = 0; j < width; j++) {
        // Lightweight dither. Carryover the remainder of each 10->8 bit
        // conversion to the next pixel.
        sample += source_16[j];
        data[j] = sample >> 2;
        sample = 0;  // Remainder.
      }
      source += decoder_buffer->stride[plane_index == numPlanes - 1 ? plane_index-1 : plane_index];
      data += decoder_buffer->stride[plane_index == numPlanes - 1 ? plane_index-1 : plane_index];
    }
  }
}

#ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
void Convert10BitFrameTo8BitDataBufferNeon(
    const Dav1dPicture* decoder_buffer, jbyte* data) {
  uint32x2_t lcg_value = vdup_n_u32(random());
  lcg_value = vset_lane_u32(random(), lcg_value, 1);
  // LCG values recommended in "Numerical Recipes".
  const uint32x2_t LCG_MULT = vdup_n_u32(1664525);
  const uint32x2_t LCG_INCR = vdup_n_u32(1013904223);

  for (int plane_index = kPlaneY; plane_index < kMaxPlanes; plane_index++) {
    const uint8_t* source = (uint8_t*)decoder_buffer->data[plane_index];
    int height = plane_index == 0 ? decoder_buffer->p.h : decoder_buffer->p.h / 2;
    int width = plane_index == 0 ? decoder_buffer->p.w : decoder_buffer->p.w / 2;
    for (int i = 0; i < height; i++) {
      const uint16_t* source_16 = reinterpret_cast<const uint16_t*>(source);
      uint8_t* destination = reinterpret_cast<uint8_t*>(data);

      // Each read consumes 4 2-byte samples, but to reduce branches and
      // random steps we unroll to 4 rounds, so each loop consumes 16
      // samples.
      const int j_max = width & ~15;
      int j;
      for (j = 0; j < j_max; j += 16) {
        // Run a round of the RNG.
        lcg_value = vmla_u32(LCG_INCR, lcg_value, LCG_MULT);

        // Round 1.
        // The lower two bits of this LCG parameterization are garbage,
        // leaving streaks on the image. We access the upper bits of each
        // 16-bit lane by shifting. (We use this both as an 8- and 16-bit
        // vector, so the choice of which one to keep it as is arbitrary.)
        uint8x8_t randvec =
            vreinterpret_u8_u16(vshr_n_u16(vreinterpret_u16_u32(lcg_value), 8));

        // We retrieve the values and shift them so that the bits we'll
        // shift out (after biasing) are in the upper 8 bits of each 16-bit
        // lane.
        uint16x4_t values = vshl_n_u16(vld1_u16(source_16), 6);
        // We add the bias bits in the lower 8 to the shifted values to get
        // the final values in the upper 8 bits.
        uint16x4_t added_1 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        // Round 2.
        // Shifting the randvec bits left by 2 bits, as an 8-bit vector,
        // should leave us with enough bias to get the needed rounding
        // operation.
        randvec = vshl_n_u8(randvec, 2);

        // Retrieve and sum the next 4 pixels.
        values = vshl_n_u16(vld1_u16(source_16), 6);
        uint16x4_t added_2 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        // Reinterpret the two added vectors as 8x8, zip them together, and
        // discard the lower portions.
        uint8x8_t zipped =
            vuzp_u8(vreinterpret_u8_u16(added_1), vreinterpret_u8_u16(added_2))
                .val[1];
        vst1_u8(destination, zipped);
        destination += 8;

        // Run it again with the next two rounds using the remaining
        // entropy in randvec.

        // Round 3.
        randvec = vshl_n_u8(randvec, 2);
        values = vshl_n_u16(vld1_u16(source_16), 6);
        added_1 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        // Round 4.
        randvec = vshl_n_u8(randvec, 2);
        values = vshl_n_u16(vld1_u16(source_16), 6);
        added_2 = vqadd_u16(values, vreinterpret_u16_u8(randvec));
        source_16 += 4;

        zipped =
            vuzp_u8(vreinterpret_u8_u16(added_1), vreinterpret_u8_u16(added_2))
                .val[1];
        vst1_u8(destination, zipped);
        destination += 8;
      }

      uint32_t randval = 0;
      // For the remaining pixels in each row - usually none, as most
      // standard sizes are divisible by 32 - convert them "by hand".
      for (; j < width; j++) {
        if (!randval) randval = random();
        destination[j] = (source_16[j] + (randval & 3)) >> 2;
        randval >>= 2;
      }
      source += decoder_buffer->stride[plane_index == kMaxPlanes - 1 ? plane_index-1 : plane_index];
      data += decoder_buffer->stride[plane_index == kMaxPlanes - 1 ? plane_index-1 : plane_index];
    }
  }
}
#endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON

}  // namespace

static void libdav1d_log_callback(void *opaque, const char *fmt, va_list vl)
{
  JniContext *c = (JniContext *)opaque;
  __android_log_vprint(ANDROID_LOG_ERROR, "dav1d_jni_dav1d", fmt, vl);
}

static void libdav1d_data_free(const uint8_t *data, void *opaque) {
  if(data) {
    delete []data;
  }
}

DECODER_FUNC(jlong, dav1dInit, jint threads) {
  JniContext* context = new (std::nothrow) JniContext(threads);
  if (context == nullptr) {
    return kStatusError;
  }

  Libdav1dContext *dav1d = (Libdav1dContext *)(context->priv_data);
  Dav1dSettings s;
  dav1d_default_settings(&s);
  s.logger.cookie = context;
  s.logger.callback = libdav1d_log_callback;
  s.allocator.alloc_picture_callback = Libdav1d_picture_alloc;
  s.allocator.release_picture_callback = Libdav1d_picture_release;
  s.allocator.cookie = context;
  s.n_tile_threads = threads;
  s.n_frame_threads = threads;

  // Populate JNI References.
  const jclass outputBufferClass = env->FindClass(
      "com/google/android/exoplayer2/video/VideoDecoderOutputBuffer");
  context->decoder_private_field =
      env->GetFieldID(outputBufferClass, "decoderPrivate", "I");
  context->output_mode_field = env->GetFieldID(outputBufferClass, "mode", "I");
  context->data_field =
      env->GetFieldID(outputBufferClass, "data", "Ljava/nio/ByteBuffer;");
  context->width_field =
      env->GetFieldID(outputBufferClass, "width", "I");
  context->height_field =
      env->GetFieldID(outputBufferClass, "height", "I");
  context->stride_field =
      env->GetFieldID(outputBufferClass, "yuvStrides", "[I");
  context->init_for_private_frame_method =
      env->GetMethodID(outputBufferClass, "initForPrivateFrame", "(II)V");
  context->init_for_yuv_frame_method =
      env->GetMethodID(outputBufferClass, "initForYuvFrame", "(IIIII)Z");
  context->init_method =
      env->GetMethodID(outputBufferClass, "init", "(JILjava/nio/ByteBuffer;)V");

  context->libdav1d_status_code = dav1d_open(&dav1d->c, &s);
  if (context->libdav1d_status_code < 0)
    return 0;

  return reinterpret_cast<jlong>(context);
}

DECODER_FUNC(void, dav1dClose, jlong jContext) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  Libdav1dContext *dav1d = (Libdav1dContext *)(context->priv_data);

  dav1d_data_unref(&dav1d->data);
  dav1d_flush(dav1d->c);
  context->buffer_manager.flushBuffers();
  dav1d_close(&dav1d->c);

  delete context;
}

DECODER_FUNC(jint, dav1dDecode, jlong jContext, jobject encodedData,
             jint length, jlong timeUs, jboolean reset) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  const uint8_t* const buffer = reinterpret_cast<const uint8_t*>(
      env->GetDirectBufferAddress(encodedData));

  Libdav1dContext *dav1d = (Libdav1dContext *) context->priv_data;
  Dav1dData *data = &dav1d->data;
  Dav1dPicture pic = { 0 }, *p = &pic;
  int res;
  uint8_t *inputData;
  int inputLenth;
  int64_t time;

//  LOGE("dav1dDecode data length : %d, timeUs: %qd", length, timeUs);

  if(reset) {
    dav1d_data_unref(&dav1d->data);
    dav1d_flush(dav1d->c);
    context->buffer_manager.flushBuffers();
  }

  uint8_t * tmpBuffer = NULL;
  tmpBuffer = new uint8_t[length];
  memcpy(tmpBuffer, buffer, length);

  inputData = tmpBuffer;
  inputLenth = length;
  time = timeUs;

  if (!data->sz) {
    context->libdav1d_status_code = res = dav1d_data_wrap(data, inputData, inputLenth, libdav1d_data_free, inputData);
    if (res < 0) {
      LOGE("dav1d_data_wrap failed res : %d", res);
      if(tmpBuffer) {
        delete []tmpBuffer;
      }
      return kStatusError;
    }

    data->m.timestamp = time;
    data->m.offset = 0;
  }

  context->libdav1d_status_code = res = dav1d_send_data(dav1d->c, data);
//  LOGE("dav1d_send_data res : %d", res);
  if (res < 0) {
    if(res != DAV1D_ERR(EAGAIN)){
      LOGE("dav1d_send_data failed res : %d", res);
      return kStatusError;
    }
    dav1d_data_unref(&dav1d->data);
    return kStatusDecodeAgain;
  }

  return kStatusOk;
}

DECODER_FUNC(jint, dav1dGetFrame, jlong jContext, jobject jOutputBuffer,
             jboolean decodeOnly) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  Libdav1dContext *dav1d = (Libdav1dContext *) context->priv_data;
  Dav1dPicture pic = { 0 }, *p = &pic;
  int res;

  context->libdav1d_status_code = res = dav1d_get_picture(dav1d->c, p);
//  LOGE("dav1dGetFrame dav1d_get_picture res : %d", res);
  if (res < 0) {
    if(res != DAV1D_ERR(EAGAIN)){
      LOGE("dav1dGetFrame dav1d_get_picture error ret: %d", res);
      return kStatusError;
    }

  }

  if(res == DAV1D_ERR(EAGAIN)) {
    return kStatusDecodeOnly;
  }

  if(p->data[0] == NULL || p->allocator_data == NULL) {
    return kStatusError;
  }

  if(decodeOnly) {
    // This is not an error. The input data was decode-only or no displayable
    // frames are available.
    dav1d_picture_unref(p);
    return kStatusDecodeOnly;
  }

//  LOGE("dav1dGetFrame dav1d_get_picture pts : %qd, width x height : %d x %d", p->m.timestamp, p->p.w, p->p.h);

  const int output_mode =
      env->GetIntField(jOutputBuffer, context->output_mode_field);
  if (output_mode == kOutputModeYuv) {
    // Resize the buffer if required. Default color conversion will be used as
    // libdav1d::DecoderBuffer doesn't expose color space info.
    const jboolean init_result = env->CallBooleanMethod(
        jOutputBuffer, context->init_for_yuv_frame_method,
        p->p.w,
        p->p.h,
        p->stride[kPlaneY], p->stride[kPlaneU],
        kColorSpaceUnknown);
    if (env->ExceptionCheck()) {
      dav1d_picture_unref(p);
      // Exception is thrown in Java when returning from the native call.
      return kStatusError;
    }
    if (!init_result) {
      dav1d_picture_unref(p);
      context->jni_status_code = kJniStatusBufferResizeError;
      return kStatusError;
    }

    const jobject data_object =
        env->GetObjectField(jOutputBuffer, context->data_field);
    jbyte* const data =
        reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(data_object));

    switch (p->p.bpc) {
      case 8:
        CopyFrameToDataBuffer(p, data);
        break;
      case 10:
#ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
        Convert10BitFrameTo8BitDataBufferNeon(p, data);
#else
        Convert10BitFrameTo8BitDataBuffer(p, data);
#endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON
        break;
      default:
        context->jni_status_code = kJniStatusBitDepth12NotSupportedWithYuv;
        dav1d_picture_unref(p);
        return kStatusError;
    }
  } else if (output_mode == kOutputModeSurfaceYuv) {
    if(p->p.bpc != 8) {
     // "High bit depth (10 or 12 bits per pixel) output format is not supported with YUV surface."
      context->render_mode = kFirstConvertToBuffer;
    }

    if(context->render_mode == kDirectRenderSurface) {
      const int buffer_id =
          *reinterpret_cast<int*>(p->allocator_data);
      context->buffer_manager.AddBufferReference(buffer_id);
      JniFrameBuffer* const jni_buffer =
          context->buffer_manager.GetBuffer(buffer_id);
      jni_buffer->SetFrameData(*p);
      env->CallVoidMethod(jOutputBuffer, context->init_for_private_frame_method,
                          p->p.w,
                          p->p.h);
      if (env->ExceptionCheck()) {
        dav1d_picture_unref(p);
        // Exception is thrown in Java when returning from the native call.
        return kStatusError;
      }

      env->SetIntField(jOutputBuffer, context->decoder_private_field, buffer_id);
    }else if(context->render_mode == kFirstConvertToBuffer) {
      // Resize the buffer if required. Default color conversion will be used as
      // libdav1d::DecoderBuffer doesn't expose color space info.
      const jboolean init_result = env->CallBooleanMethod(
          jOutputBuffer, context->init_for_yuv_frame_method,
          p->p.w,
          p->p.h,
          p->stride[kPlaneY], p->stride[kPlaneU],
          kColorSpaceUnknown);
      if (env->ExceptionCheck()) {
        dav1d_picture_unref(p);
        // Exception is thrown in Java when returning from the native call.
        return kStatusError;
      }
      if (!init_result) {
        dav1d_picture_unref(p);
        context->jni_status_code = kJniStatusBufferResizeError;
        return kStatusError;
      }

      const jobject data_object =
          env->GetObjectField(jOutputBuffer, context->data_field);
      jbyte* const data =
          reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(data_object));

      switch (p->p.bpc) {
        case 8:
          CopyFrameToDataBuffer(p, data);
          break;
        case 10:
          {
            long costTime = getCurrentTime();
  #ifdef CPU_FEATURES_COMPILED_ANY_ARM_NEON
            Convert10BitFrameTo8BitDataBufferNeon(p, data);
  #else
            Convert10BitFrameTo8BitDataBuffer(p, data);
  #endif  // CPU_FEATURES_COMPILED_ANY_ARM_NEON
//            LOGE("Convert10BitFrameTo8BitDataBuffer costtime : %qd", getCurrentTime()-costTime);
          }
          break;
        default:
          context->jni_status_code = kJniStatusBitDepth12NotSupportedWithYuv;
          dav1d_picture_unref(p);
          return kStatusError;
      }
    }

    env->CallVoidMethod(jOutputBuffer, context->init_method,
                        p->m.timestamp,
                        output_mode,
                        NULL);
  }
  dav1d_picture_unref(p);
  return kStatusOk;
}

DECODER_FUNC(jint, dav1dSetSurface, jlong jContext, jobject jSurface) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  if(!context) {
      return kStatusError;
  }

  context->MaybeAcquireNativeWindow(env, jSurface);

  return kStatusOk;
  }
DECODER_FUNC(jint, dav1dRenderFrame, jlong jContext, jobject jSurface,
             jobject jOutputBuffer) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);

  JniFrameBuffer* jni_buffer = NULL;
  jbyte* bufferData = NULL;
  jint* stride = NULL;
  int yDisplayWidth;
  int yDisplayHeight;
  int uvDisplayWidth;
  int uvDisplayHeight;
  int yStride;
  int uvStride;

  uint8_t * yuvData;

  if(context->render_mode == kDirectRenderSurface) {
    const int buffer_id =
        env->GetIntField(jOutputBuffer, context->decoder_private_field);
    jni_buffer = context->buffer_manager.GetBuffer(buffer_id);

    yDisplayWidth = jni_buffer->DisplayedWidth(kPlaneY);
    yDisplayHeight = jni_buffer->DisplayedHeight(kPlaneY);
    yStride = jni_buffer->Stride(kPlaneY);
    uvStride = jni_buffer->Stride(kPlaneU);
  }else if(context->render_mode == kFirstConvertToBuffer) {
    const jobject data_object =
        env->GetObjectField(jOutputBuffer, context->data_field);
    bufferData =
        reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(data_object));

    const jintArray stride_object =
        (jintArray)env->GetObjectField(jOutputBuffer, context->stride_field);
    jboolean isCopy = static_cast<jboolean>(false);
    stride = reinterpret_cast<jint*>(env->GetIntArrayElements(stride_object, &isCopy));

    yDisplayWidth = env->GetIntField(jOutputBuffer, context->width_field);
    yDisplayHeight = env->GetIntField(jOutputBuffer, context->height_field);
    yStride = stride[0];
    uvStride = stride[1];

    env->ReleaseIntArrayElements(stride_object, stride, isCopy);
  }

  uvDisplayWidth = yDisplayWidth / 2;
  uvDisplayHeight = yDisplayHeight / 2;

  if (!context->MaybeAcquireNativeWindow(env, jSurface)) {
    return kStatusError;
  }

  if (context->native_window_width != yDisplayWidth ||
      context->native_window_height != yDisplayHeight) {
    if (ANativeWindow_setBuffersGeometry(
            context->native_window, yDisplayWidth,
            yDisplayHeight, kImageFormatYV12)) {
      context->jni_status_code = kJniStatusANativeWindowError;
      return kStatusError;
    }
    context->native_window_width = yDisplayWidth;
    context->native_window_height = yDisplayHeight;
  }

  ANativeWindow_Buffer native_window_buffer;
  if (ANativeWindow_lock(context->native_window, &native_window_buffer,
                         /*inOutDirtyBounds=*/nullptr) ||
      native_window_buffer.bits == nullptr) {
    context->jni_status_code = kJniStatusANativeWindowError;
    return kStatusError;
  }

  yuvData = context->render_mode == kDirectRenderSurface ? jni_buffer->Plane(kPlaneY) : (uint8_t*)bufferData;
  // Y plane
  CopyPlane(yuvData, yStride,
            reinterpret_cast<uint8_t*>(native_window_buffer.bits),
            native_window_buffer.stride, yDisplayWidth,
            yDisplayHeight);

  const int y_plane_size =
      native_window_buffer.stride * native_window_buffer.height;
  const int32_t native_window_buffer_uv_height =
      (native_window_buffer.height + 1) / 2;
  const int native_window_buffer_uv_stride =
      AlignTo16(native_window_buffer.stride / 2);

  // TODO(b/140606738): Handle monochrome videos.

  // V plane
  // Since the format for ANativeWindow is YV12, V plane is being processed
  // before U plane.
  int v_plane_offset = yStride * yDisplayHeight + uvStride * uvDisplayHeight;

  const int v_plane_height = std::min(native_window_buffer_uv_height,
                                      uvDisplayHeight);

  yuvData = context->render_mode == kDirectRenderSurface ? jni_buffer->Plane(kPlaneV) : (uint8_t*)bufferData + v_plane_offset;
  CopyPlane(
      yuvData, uvStride,
      reinterpret_cast<uint8_t*>(native_window_buffer.bits) + y_plane_size,
      native_window_buffer_uv_stride, uvDisplayWidth,
      v_plane_height);

  const int v_plane_size = v_plane_height * native_window_buffer_uv_stride;

  int u_plane_offset = yStride * yDisplayHeight;
  yuvData = context->render_mode == kDirectRenderSurface ? jni_buffer->Plane(kPlaneU) : (uint8_t*)bufferData + u_plane_offset;
  // U plane
  CopyPlane(yuvData, uvStride,
            reinterpret_cast<uint8_t*>(native_window_buffer.bits) +
                y_plane_size + v_plane_size,
            native_window_buffer_uv_stride, uvDisplayWidth,
            std::min(native_window_buffer_uv_height,
                     uvDisplayHeight));

  if (ANativeWindow_unlockAndPost(context->native_window)) {
    context->jni_status_code = kJniStatusANativeWindowError;
    return kStatusError;
  }

  return kStatusOk;
}

DECODER_FUNC(void, dav1dReleaseFrame, jlong jContext, jobject jOutputBuffer) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  if(context->render_mode != kDirectRenderSurface) {
    return;
  }
  const int buffer_id =
      env->GetIntField(jOutputBuffer, context->decoder_private_field);
  env->SetIntField(jOutputBuffer, context->decoder_private_field, -1);
  context->jni_status_code = context->buffer_manager.ReleaseBuffer(buffer_id);
  if (context->jni_status_code != kJniStatusOk) {
    LOGE("%s", GetJniErrorMessage(context->jni_status_code));
  }
}

DECODER_FUNC(jstring, dav1dGetErrorMessage, jlong jContext) {
  if (jContext == 0) {
    return env->NewStringUTF("Failed to initialize JNI context.");
  }

  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  if (context->libdav1d_status_code != 0) {
    return env->NewStringUTF("libdav1d unknown error");
  }
  if (context->jni_status_code != kJniStatusOk) {
    return env->NewStringUTF(GetJniErrorMessage(context->jni_status_code));
  }

  return env->NewStringUTF("None.");
}

DECODER_FUNC(jint, dav1dCheckError, jlong jContext) {
  JniContext* const context = reinterpret_cast<JniContext*>(jContext);
  if (context->libdav1d_status_code != 0 ||
      context->jni_status_code != kJniStatusOk) {
    return kStatusError;
  }
  return kStatusOk;
}

// TODO(b/139902005): Add functions for getting libdav1d version and build
// configuration once libdav1d ABI provides this information.
