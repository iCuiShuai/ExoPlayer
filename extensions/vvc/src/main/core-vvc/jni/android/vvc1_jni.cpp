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

//USER INCLUDES
#include "JavaEnv.h"
#include "JNIExceptions.h"

#include <cstring>
#include <mutex>  // NOLINT
#include <new>
//#include <common/mem.h>
#include <RenderVideoOpenGL.h>

#include <cstring>
#include <iostream>

#include "h266_decoder/getopt.h"
#include "h266_decoder/mem_util.h"
#include "h266_decoder/thread_util.h"
#include "h266_decoder/util.h"
#include "api/o266dec_api.h"

using util::CountOf;
using util::VerifyCast;

#include "ColorSpaceConverter_RGBA_NEON.h"

#define SUPPORT_AV1_10BIT 0

#define LOG_TAG "vvc1_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}
#pragma GCC visibility push(hidden)

namespace jni{
enum dataRenderMode {
  kDirectRenderSurface = 0,
  kFirstConvertToBuffer = 1,
};
const dataRenderMode defaultRendeMode = kDirectRenderSurface;
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
// LINT.ThenChange(../java/com/google/android/exoplayer2/ext/vvc1/VVcDecoder.java)

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
      return "Libvvc decoded buffer has invalid number of planes.";
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
typedef struct LibdvvcFrameBuffer {
  // In the |data| and |size| arrays
  uint8_t* data;    // Pointers to the data buffers.
  size_t size;      // Sizes of the data buffers in bytes.
  void* private_data;  // Frame buffer's private data. Available for use by the
  // release frame buffer callback.
} LibdvvcFrameBuffer;

// Manages LibdvvcFrameBuffer and reference information.
class vvcJniFrameBuffer {
 public:
  explicit vvcJniFrameBuffer(int id) : id_(id), reference_count_(0) {
    vvcFrameBuffer.private_data = &id_;
  }
  ~vvcJniFrameBuffer() {
    //todo , release buffer
  }

  void SetFrameData(const O266DecOutputPicture& decoder_buffer) {
    //todo ...

  }

  void setFrameTime(int64_t ms) {
    time = ms;
  }

  int64_t getFrameTime() {
    return time;
  }

  void setFrameBpc(int _bpc) {
    bpc = _bpc;
  }

  int getFrameBpc() {
    return bpc;
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

  const LibdvvcFrameBuffer& GetVVCFrameBuffer() const {
    return vvcFrameBuffer;
  }

  // Attempts to reallocate data planes if the existing ones don't have enough
  // capacity. Returns true if the allocation was successful or wasn't needed,
  // false if the allocation failed.
  bool MaybeReallocateVVCDataPlanes(O266DecOutputPicture *const p) {
    //todo..

    return true;
  }

 private:
  int stride_[kMaxPlanes];
  uint8_t* plane_[kMaxPlanes];
  int displayed_width_[kMaxPlanes];
  int displayed_height_[kMaxPlanes];
  int id_;
  int reference_count_;
  LibdvvcFrameBuffer vvcFrameBuffer = {};
  int64_t time;
  int bpc;
};


// Manages frame buffers used by libvvc decoder and ExoPlayer.
// Handles synchronization between libvvc and ExoPlayer threads.
class vvcJniBufferManager {
 public:
  ~vvcJniBufferManager() {
    // This lock does not do anything since libvvc has released all the frame
    // buffers. It exists to merely be consistent with all other usage of
    // |all_buffers_| and |all_buffer_count_|.
    std::lock_guard<std::mutex> lock(mutex_);
    while (all_buffer_count_--) {
      delete all_buffers_[all_buffer_count_];
    }
  }

  JniStatusCode GetBuffer(O266DecOutputPicture *const p) {
    std::lock_guard<std::mutex> lock(mutex_);

    vvcJniFrameBuffer* output_buffer;
    if (free_buffer_count_) {
      output_buffer = free_buffers_[--free_buffer_count_];
    } else if (all_buffer_count_ < kMaxFrames) {
      output_buffer = new (std::nothrow) vvcJniFrameBuffer(all_buffer_count_);
      if (output_buffer == nullptr) return kJniStatusOutOfMemory;
      all_buffers_[all_buffer_count_++] = output_buffer;
    } else {
      // Maximum number of buffers is being used.
      return kJniStatusOutOfMemory;
    }
    if (!output_buffer->MaybeReallocateVVCDataPlanes(p)) {
      return kJniStatusOutOfMemory;
    }
    output_buffer->AddReference();

    return kJniStatusOk;
  }

  vvcJniFrameBuffer* GetBuffer(int id) const { return all_buffers_[id]; }

  void AddBufferReference(int id) {
    std::lock_guard<std::mutex> lock(mutex_);
    all_buffers_[id]->AddReference();
  }

  JniStatusCode ReleaseBuffer(int id) {
    std::lock_guard<std::mutex> lock(mutex_);
    vvcJniFrameBuffer* buffer = all_buffers_[id];
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

  vvcJniFrameBuffer* all_buffers_[kMaxFrames];
  int all_buffer_count_ = 0;

  vvcJniFrameBuffer* free_buffers_[kMaxFrames];
  int free_buffer_count_ = 0;

  std::mutex mutex_;
};

typedef struct LibvvcContext {
  O266DecDecoderHandle handle;
  bool endFlushed;
  int pool_size;

  int tile_threads;
  int frame_threads;
  int apply_grain;
  int operating_point;
  int all_layers;
} LibVVCContext;

typedef struct bufferque {
    uint8_t *data;
    int size;
    int64_t time;
}bufferque;

struct vvcJniContext {
  vvcJniContext(int threads) {
    LibVVCContext *libvvcContext = new LibVVCContext();
    libvvcContext->tile_threads = threads;
    libvvcContext->frame_threads = threads;
    libvvcContext->handle = NULL;
    libvvcContext->endFlushed = false;
    priv_data = libvvcContext;

    render_mode = defaultRendeMode;

    rgbaData.time = 0;
    rgbaData.data = NULL;
    rgbaData.size = 0;
  }
  ~vvcJniContext() {
    if (native_window) {
      ANativeWindow_release(native_window);
    }
    if(priv_data != NULL) {
      LibVVCContext *libvvcContext = (LibVVCContext *)priv_data;
      delete libvvcContext;
      priv_data = NULL;
    }

    if(videoRender) {
      delete videoRender;
    }

    if(rgbaData.data != NULL) {
        util::AlignedFree(rgbaData.data);
        rgbaData.data = NULL;
    }
  }

  bool initVideoRender(JNIEnv* env)
  {
    videoRender = new RenderVideoOpenGL(NULL, env);

    return true;
  }

  bool MaybeInitOpengl(JNIEnv* env, jobject new_surface, int width, int height)
  {
      bool needResetRender = (surface != new_surface) || (native_window_width != width || native_window_height != height);
      if (!needResetRender) {
          return true;
      }

      if(videoRender)
      {
          bool  ret = false;
          videoRender->detachSurface(NULL);
          if(new_surface != NULL) {
              ret = videoRender->attachSurface(env, new_surface, width, height);
          }

          native_window_width = width;
          native_window_height = height;
          surface = new_surface;
          return ret;
      }

      return false;
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
  jfieldID timeUs_field;
  jmethodID init_for_private_frame_method;
  jmethodID init_for_yuv_frame_method;
  jmethodID init_method;

  // The libvvc instance has to be closed before |buffer_manager| is
  // destructed. This will make sure that libvvc releases all the frame
  // buffers that it might be holding references to.
  vvcJniBufferManager buffer_manager;

  ANativeWindow* native_window = nullptr;
  jobject surface = nullptr;
  int native_window_width = 0;
  int native_window_height = 0;
  void *priv_data;

  int libvvc_status_code = 0;
  JniStatusCode jni_status_code = kJniStatusOk;

  dataRenderMode render_mode;

  RenderVideoOpenGL* videoRender = NULL;

  bufferque rgbaData;
};

// return the current time in millisecond : 1/1000 second
//static inline int64_t getCurrentTime()
//{
//  struct timeval tv;
//  gettimeofday(&tv,NULL);
//  return tv.tv_sec * 1000 + tv.tv_usec / 1000;
//}

void CopyFrameToDataBuffer(const O266DecOutputPicture* decoder_buffer,
                           jbyte* data) {
  int numPlanes = decoder_buffer->header.chroma_format == kO266DecChromaFormat400 ? 1 : 3;
  for (int plane_index = kPlaneY; plane_index < numPlanes;
       plane_index++) {
    int height = plane_index == 0 ? decoder_buffer->header.height : decoder_buffer->header.height / 2;
    const uint64_t length = decoder_buffer->planes[plane_index].stride * height;
    memcpy(data, decoder_buffer->planes[plane_index].pix, length);
    data += length;
  }
}

//#define TEST_CUSTOM_ALLOCATOR

#ifdef TEST_CUSTOM_ALLOCATOR

static void ReleaseOutputPicBuf(O266DecPlaneBuffers* to_release) {
  testRelease++;
  for (auto& plane : to_release->buf) {
    util::AlignedFree(plane);
    plane = nullptr;
  }
}

static bool AllocOutputPicBuf(const O266DecPictureBufInfo* info,
                              O266DecPlaneBuffers* result) {
  testAlloc++;
  *result = {{nullptr}};
  for (int i = 0; i <CountOf(info->size); ++i) {
    result->buf[i] = util::AlignedMalloc(info->alignment, info->size[i]);
    if (!result->buf[i]) {
      ReleaseOutputPicBuf(result);
      return false;
    }
  }

  return true;
}
#endif

void register_outputBuffer(vvcJniContext* context, JavaEnv j )
{
    // Populate JNI References.
    const jclass outputBufferClass = j.findClass(
    "com/google/android/exoplayer2/video/VideoDecoderOutputBuffer");

    context->decoder_private_field = j.getFieldID(outputBufferClass, "decoderPrivate", "I");
    context->output_mode_field = j.getFieldID(outputBufferClass, "mode", "I");
    context->data_field = j.getFieldID(outputBufferClass, "data", "Ljava/nio/ByteBuffer;");
    context->width_field = j.getFieldID(outputBufferClass, "width", "I");
    context->height_field = j.getFieldID(outputBufferClass, "height", "I");
    context->stride_field = j.getFieldID(outputBufferClass, "yuvStrides", "[I");
    context->timeUs_field = j.getFieldID(outputBufferClass, "timeUs", "J");

    context->init_for_private_frame_method = j.getMethodID(outputBufferClass, "initForPrivateFrame", "(II)V");
    context->init_for_yuv_frame_method = j.getMethodID(outputBufferClass, "initForYuvFrame", "(IIIII)Z");
    context->init_method = j.getMethodID(outputBufferClass, "init", "(JILjava/nio/ByteBuffer;)V");

    return;
}

long vvcInit( JNIEnv* env, jobject thiz, jint threads)
{
    vvcJniContext* context = new (std::nothrow) vvcJniContext(threads);
    if (context == nullptr) {
      return kStatusError;
    }

    JavaEnv j(env);

    try
    {
        register_outputBuffer(context, j);
    }
    transform_jni_exceptions;

    LibVVCContext *vvcContext = (LibVVCContext *)(context->priv_data);

    O266DecStatus status = kO266DecOk;
    O266DecConfig o266DecConfig;
    memset(&o266DecConfig, 0, sizeof(O266DecConfig));
    o266DecConfig.enable_multi_thread = true;
    o266DecConfig.num_threads = threads;
    o266DecConfig.num_frame_threads = threads > 3 ? 3 : threads;  //set max frame threads to 3 according to Tencent's suggestion
  //  o266DecConfig.process_frame_delay = 1;

    if (!vvcContext->handle) {
      status = O266DecCreateDecoder(&o266DecConfig, &(vvcContext->handle));
      if (status != kO266DecOk) {
        LOGE("O266CreateDecoder failed...");
        return false;
      }

  #ifdef TEST_CUSTOM_ALLOCATOR
      static const O266DecAllocatorInterface allocator_interface = {
          AllocOutputPicBuf,
          ReleaseOutputPicBuf,
      };

      status = O266DecSetAllocator(vvcContext->handle, &allocator_interface);
      if (status != kO266DecOk) {
        LOGE("O266DecSetAllocator failed...");
        return false;
      }
  #endif
    }

    context->initVideoRender(env);

    return reinterpret_cast<jlong>(context);
}

void vvcClose( JNIEnv* env, jobject thiz, jlong jContext)
{
    vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
    if (context == NULL) {
      return ;
    }

    LibVVCContext *vvcContext = (LibVVCContext *)(context->priv_data);

    context->buffer_manager.flushBuffers();
    if (vvcContext) {
      O266DecCloseDecoder(vvcContext->handle);
      vvcContext->handle = NULL;
    }

    LOGE("vvcClose ....");

    delete context;

    return;
}

void saveDecodeData(uint8_t* r, int size)
{
  FILE* decodeFile1 = fopen("/mnt/sdcard/yuvtest/vvcDecodeData.bin","a+");
  if (NULL != decodeFile1) {
    fwrite(r, sizeof(uint8_t), size, decodeFile1);
    fclose(decodeFile1);
  }
}

int vvcDecode( JNIEnv* env, jobject thiz, jlong jContext,
                  jobject encodedData, jint length,
                  jlong timeUs, jboolean reset)
{
    vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
    if (context == NULL) {
      return kStatusError;
    }

    const uint8_t* const buffer = reinterpret_cast<const uint8_t*>(
        env->GetDirectBufferAddress(encodedData));

    LibVVCContext *vvc = (LibVVCContext *) context->priv_data;
    int res;
    uint8_t *inputData;
    int inputLenth;
    int64_t time;

//    LOGE("vvcDecode data length : %d, timeUs: %qd", length, timeUs);

    if(reset) {
      LOGE("vvcDecode reset");
      O266DecNotifyFlush(vvc->handle, false);
      context->buffer_manager.flushBuffers();
      vvc->endFlushed = false;
    }
    uint8_t * tmpBuffer = NULL;
    tmpBuffer = new uint8_t[length];
    memcpy(tmpBuffer, buffer, length);

    inputData = tmpBuffer;
    inputLenth = length;
    time = timeUs;

    O266DecDataPacket packet;
    packet.has_complete_nal = true;
    packet.data_buf = inputData;
    packet.data_size = VerifyCast<decltype(packet.data_size)>(inputLenth);
    packet.pts = time;
    auto status = O266DecPushData(vvc->handle, &packet);
    if (kO266DecOk != status) {
      LOGE("O266DecPushData failed status : %d", status);
      if(tmpBuffer) {
        delete []tmpBuffer;
      }
      return kStatusError;
    }
    status = O266DecDecodeFrame(vvc->handle);
    if (status != kO266DecOk && status != kO266DecNeedMoreData) {
      LOGE("O266DecDecodeFrame failed status : %d", status);
      return kStatusError;
    }
    //todo, flush decoder?
  //  if (fin_.eof()) {
  //    status = O266DecNotifyEndOfStream(decoder_);
  //  }

  //  saveDecodeData(inputData, inputLenth);

    return kStatusOk;
}

void saveYUVData2(const uint8_t* y, const uint8_t* u, const uint8_t* v, int width, int height, int ystride, int uvstride)
{
  FILE* yFile1 = fopen("/mnt/sdcard/yuvtest/vvcyData2.bin","ab+");
  if (NULL != yFile1) {
    for(int i = 0; i < height; i++)
    {
      fwrite(y, sizeof(uint8_t), width, yFile1);
      y += ystride;
    }

    for(int i = 0; i < height / 2; i++)
    {
      fwrite(u, sizeof(uint8_t), width / 2, yFile1);
      u += uvstride;
    }

    for(int i = 0; i < height / 2; i++)
    {
      fwrite(v, sizeof(uint8_t), width / 2, yFile1);
      v += uvstride;
    }

    fclose(yFile1);
  }

}

int vvcGetFrame( JNIEnv* env, jobject thiz, jlong jContext, jobject jOutputBuffer, jboolean decodeOnly, jboolean endFlush)
{
    vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
    if (context == NULL) {
      return kStatusError;
    }
    LibVVCContext *vvc = (LibVVCContext *) context->priv_data;

    int64_t frames = 0;
    O266DecOutputPicture pic;
    O266DecOutputPicture pic_prev;
    O266DecStatus status = kO266DecOk;
    status = O266DecGetOutputPicture(vvc->handle, &pic);

 //   LOGE("vvcGetFrame O266DecGetOutputPicture status:  %d", status);

    if(status == kO266DecOutputNotReady)
    {
      status = O266DecDecodeFrame(vvc->handle);
      if (status != kO266DecOk && status != kO266DecEndOfStream) {
        LOGE("vvcGetFrame O266DecDecodeFrame failed status : %d", status);
        if(endFlush && !vvc->endFlushed)
        {
          LOGE("vvcGetFrame O266DecNotifyFlush ...");
          O266DecNotifyFlush(vvc->handle, true);

          status = O266DecDecodeFrame(vvc->handle);
          LOGE("vvcGetFrame O266DecDecodeFrame failed 2 status : %d", status);
          vvc->endFlushed = true;
        }
        else
          return kStatusDecodeOnly;
      }

      status = O266DecGetOutputPicture(vvc->handle, &pic);

      if(status == kO266DecOutputNotReady)
      {
        return kStatusDecodeOnly;
      }
    }

    if(status != kO266DecOk)
    {
      return kStatusError;
    }

    if(pic.planes[0].pix == NULL) {
      return kStatusError;
    }

    if(decodeOnly) {
      // This is not an error. The input data was decode-only or no displayable
      // frames are available.
      O266DecReleaseOutputPicture(vvc->handle, &pic);
      return kStatusDecodeOnly;
    }

  //  saveYUVData2((uint8_t*)pic.planes[kPlaneY].pix, (uint8_t*)pic.planes[kPlaneU].pix, (uint8_t*)pic.planes[kPlaneV].pix, pic.header.width, pic.header.height,pic.planes[kPlaneY].stride, pic.planes[kPlaneU].stride);

 //   LOGE("vvcGetFrame vvc_get_picture pts : %qd, width x height : %d x %d", pic.header.pts, pic.header.width, pic.header.height);
  //  LOGE("vvcGetFrame picture type: %d", pic.header.pic_type);
 //   LOGE("vvcGetFrame picture decoderOrder: %d, displayOrder: %d", pic.header.coded_picture_number,pic.header.display_picture_number);

    const int output_mode =
        env->GetIntField(jOutputBuffer, context->output_mode_field);
    if (output_mode == kOutputModeYuv) {
      // Resize the buffer if required. Default color conversion will be used as
      // libvvc::DecoderBuffer doesn't expose color space info.
      const jboolean init_result = env->CallBooleanMethod(
          jOutputBuffer, context->init_for_yuv_frame_method,
          pic.header.width,
          pic.header.width,
          pic.planes[kPlaneY].stride, pic.planes[kPlaneU].stride,
          0);
      if (env->ExceptionCheck()) {
        O266DecReleaseOutputPicture(vvc->handle, &pic);
        // Exception is thrown in Java when returning from the native call.
        return kStatusError;
      }
      if (!init_result) {
        O266DecReleaseOutputPicture(vvc->handle, &pic);
        context->jni_status_code = kJniStatusBufferResizeError;
        return kStatusError;
      }

      const jobject data_object =
          env->GetObjectField(jOutputBuffer, context->data_field);
      jbyte* const data =
          reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(data_object));

      //todo...

    } else if (output_mode == kOutputModeSurfaceYuv) {

      //todo...as tencent lib don't support buffer mangner
      context->render_mode = kFirstConvertToBuffer;

      if(pic.planes[kPlaneY].bit_depth != 8) {

       // "High bit depth (10 or 12 bits per pixel) output format is not supported with YUV surface."
  #if SUPPORT_AV1_10BIT
  #else
        O266DecReleaseOutputPicture(vvc->handle, &pic);
          return kStatusError;
  #endif

      }

      if(context->render_mode == kDirectRenderSurface) {

        //todo ..

      }else if(context->render_mode == kFirstConvertToBuffer) {
        // Resize the buffer if required. Default color conversion will be used as
        // libvvc::DecoderBuffer doesn't expose color space info.
        const jboolean init_result = env->CallBooleanMethod(
            jOutputBuffer, context->init_for_yuv_frame_method,
            pic.header.width,
            pic.header.height,
            pic.planes[kPlaneY].stride, pic.planes[kPlaneU].stride,
            kColorSpaceUnknown);
        if (env->ExceptionCheck()) {
          O266DecReleaseOutputPicture(vvc->handle, &pic);
          // Exception is thrown in Java when returning from the native call.
          return kStatusError;
        }
        if (!init_result) {
          O266DecReleaseOutputPicture(vvc->handle, &pic);
          context->jni_status_code = kJniStatusBufferResizeError;
          return kStatusError;
        }

        const jobject data_object =
            env->GetObjectField(jOutputBuffer, context->data_field);
        jbyte* const data =
            reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(data_object));

        switch (pic.planes[kPlaneY].bit_depth) {
          case 8:
            CopyFrameToDataBuffer(&pic, data);
            break;
          case 10:
            {
              //todo...
            }
            break;
          default:
            context->jni_status_code = kJniStatusBitDepth12NotSupportedWithYuv;
            O266DecReleaseOutputPicture(vvc->handle, &pic);
            return kStatusError;
        }
      }

      env->SetLongField(jOutputBuffer, context->timeUs_field, pic.header.pts);
      env->SetIntField(jOutputBuffer, context->output_mode_field, output_mode);
  //    env->CallVoidMethod(jOutputBuffer, context->init_method,
  //                        p->m.timestamp,
  //                        output_mode,
  //                        NULL);
    }
    O266DecReleaseOutputPicture(vvc->handle, &pic);
    return kStatusOk;
}

int vvcSetSurface( JNIEnv* env, jobject thiz, jlong jContext, jobject jSurface)
{
    vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
    if(!context) {
        return kStatusError;
    }

    if(jSurface == NULL){
        //openglrender render
        context->MaybeInitOpengl(env, NULL, 0, 0);
    }

    return kStatusOk;
}

int vvcRenderFrame( JNIEnv* env, jobject thiz, jlong jContext, jobject jSurface, jobject jOutputBuffer)
{
    vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
    if (context == NULL) {
      return kStatusError;
    }

    vvcJniFrameBuffer* jni_buffer = NULL;
    jbyte* bufferData = NULL;
    jint* stride = NULL;
    int yDisplayWidth;
    int yDisplayHeight;
    int uvDisplayWidth;
    int uvDisplayHeight;
    int yStride;
    int uvStride;

    uint8_t * DataY;
    uint8_t * DataU;
    uint8_t * DataV;

    int64_t pts = 0;
    int bpc = 8;

    if(context->render_mode == kDirectRenderSurface) {
      const int buffer_id =
          env->GetIntField(jOutputBuffer, context->decoder_private_field);
      jni_buffer = context->buffer_manager.GetBuffer(buffer_id);

      yDisplayWidth = jni_buffer->DisplayedWidth(kPlaneY);
      yDisplayHeight = jni_buffer->DisplayedHeight(kPlaneY);
      yStride = jni_buffer->Stride(kPlaneY);
      uvStride = jni_buffer->Stride(kPlaneU);

      pts = jni_buffer->getFrameTime();
      bpc = jni_buffer->getFrameBpc();
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

    if (!context->MaybeInitOpengl(env, jSurface, yDisplayWidth, yDisplayHeight)) {
      return kStatusError;
    }


    DataY = context->render_mode == kDirectRenderSurface ? jni_buffer->Plane(kPlaneY) : (uint8_t*)bufferData;


    // U plane
    int u_plane_offset = yStride * yDisplayHeight;
    DataU = context->render_mode == kDirectRenderSurface ? jni_buffer->Plane(kPlaneU) : (uint8_t*)bufferData + u_plane_offset;

    // V plane
    int v_plane_offset = yStride * yDisplayHeight + uvStride * uvDisplayHeight;
    DataV = context->render_mode == kDirectRenderSurface ? jni_buffer->Plane(kPlaneV) : (uint8_t*)bufferData + v_plane_offset;

    int picSize = yDisplayWidth * yDisplayHeight *4;

    int64_t costtime = getCurrentTime();

#if defined(__TARGET_ARCH_ARM) && defined(__NEON__)
    if(context->rgbaData.data == NULL) {
        context->rgbaData.data = (uint8_t*)util::AlignedMalloc(64, picSize);
        context->rgbaData.size = picSize;
    }else if(context->rgbaData.size != picSize){
        util::AlignedFree(context->rgbaData.data);
        context->rgbaData.data = (uint8_t*)util::AlignedMalloc(64, picSize);
        context->rgbaData.size = picSize;
    }
    if(bpc == 8)
    {
        neon::yv12_studio_rgba(context->rgbaData.data,
                       DataY, DataU, DataV,
                       yDisplayWidth, yDisplayHeight,
                       yStride, uvStride,
                       yDisplayWidth*4);
    }else if (bpc == 10)
    {
        neon::yv12_10_studio_rgba(context->rgbaData.data,
                                  DataY, DataU, DataV,
                                  yDisplayWidth, yDisplayHeight,
                                  yStride, uvStride, yDisplayWidth*4 );
    }
#endif

  //  LOGE("yv12 %d bit studio_rgba : %qd", bpc, getCurrentTime()-costtime);

    if(context->videoRender)
    {
#if defined(__TARGET_ARCH_ARM) && defined(__NEON__)
        context->videoRender->rendBuffer(pts, context->rgbaData.data);
#else
        context->videoRender->renderYUVBuffer(DataY, yStride, DataU, uvStride, DataV, uvStride, yDisplayWidth, yDisplayHeight);
#endif
    }

    return kStatusOk;
}

void vvcReleaseFrame( JNIEnv* env, jobject thiz, jlong jContext, jobject jOutputBuffer)
{
  vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
  if (context == NULL) {
    return ;
  }
  if(context->render_mode != kDirectRenderSurface) {
    return;
  }
  const int buffer_id =
      env->GetIntField(jOutputBuffer, context->decoder_private_field);
  if(buffer_id < 0) {
    return;
  }
  env->SetIntField(jOutputBuffer, context->decoder_private_field, -1);
  context->jni_status_code = context->buffer_manager.ReleaseBuffer(buffer_id);
  if (context->jni_status_code != kJniStatusOk) {
    LOGE("%s", GetJniErrorMessage(context->jni_status_code));
  }
}

jstring vvcGetErrorMessage( JNIEnv* env, jobject thiz, jlong jContext)
{
  if (jContext == 0) {
    return env->NewStringUTF("Failed to initialize JNI context.");
  }

  vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
  if (context->libvvc_status_code != 0) {
    return env->NewStringUTF("libvvc unknown error");
  }
  if (context->jni_status_code != kJniStatusOk) {
    return env->NewStringUTF(GetJniErrorMessage(context->jni_status_code));
  }

  return env->NewStringUTF("None.");
}

int vvcCheckError( JNIEnv* env, jobject thiz, jlong jContext)
{
  vvcJniContext* const context = reinterpret_cast<vvcJniContext*>(jContext);
  if (context == NULL) {
    return kStatusError;
  }

  if (context->libvvc_status_code != 0 ||
      context->jni_status_code != kJniStatusOk) {
    return kStatusError;
  }
  return kStatusOk;
}

void vvcCheckLibrary()
{
  LOGE("vvcCheckLibrary returned true! ");
  return;
}

static const JNINativeMethod methods[] =
{
        { "vvcInit",              "(I)J",                                                                                       ( void* )vvcInit     },
        { "vvcClose",             "(J)V",                                                                                       ( void* )vvcClose           },
        { "vvcDecode",            "(JLjava/nio/ByteBuffer;IJZ)I",                                                               ( void* )vvcDecode       },
        { "vvcGetFrame",          "(JLcom/google/android/exoplayer2/video/VideoDecoderOutputBuffer;ZZ)I",                       ( void* )vvcGetFrame         },
        { "vvcSetSurface",        "(JLandroid/view/Surface;)I",                                                                 ( void* )vvcSetSurface       },
        { "vvcRenderFrame",       "(JLandroid/view/Surface;Lcom/google/android/exoplayer2/video/VideoDecoderOutputBuffer;)I",   ( void* )vvcRenderFrame        },
        { "vvcReleaseFrame",      "(JLcom/google/android/exoplayer2/video/VideoDecoderOutputBuffer;)V",                         ( void* )vvcReleaseFrame           },
        { "vvcGetErrorMessage",   "(J)Ljava/lang/String;",                                                                      ( void* )vvcGetErrorMessage       },
        { "vvcCheckError",        "(J)I",                                                                                       ( void* )vvcCheckError            },
        { "vvcCheckLibrary",      "()V",                                                                                       ( void* )vvcCheckLibrary            },

};

}  // namespace


extern "C" {
__attribute__((visibility("default")))
void Java_com_google_android_exoplayer2_ext_vvc_VVCDecoder_nativeClassInit( JNIEnv *env, jclass clazz ) noexcept
{
    JavaEnv j( env );
    try
    {
        j.registerNatives( clazz, jni::methods, _countof( jni::methods ) );

        j.deleteLocalRef( clazz );
    }
    transform_jni_exceptions;
}
}

#pragma GCC visibility pop
