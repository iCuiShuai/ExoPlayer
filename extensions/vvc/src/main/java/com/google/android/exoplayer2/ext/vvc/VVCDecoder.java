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
package com.google.android.exoplayer2.ext.vvc;

import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoderDav1d;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderInputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;

/** vvc decoder. */
/* package */ final class VVCDecoder
    extends SimpleDecoderDav1d<VideoDecoderInputBuffer, VideoDecoderOutputBuffer, VVCDecoderException> {

  // LINT.IfChange

  // LINT.ThenChange(../../../../../../../jni/vvc1_jni.cc)

  private final long vvcDecoderContext;

  @C.VideoOutputMode private volatile int outputMode;

  public static byte[] vvcConfigData = new byte[100];
  public static int vvcConfigLength = 0;

  public static void updateVVCConfig(byte[]vvcConfig, int length) {
    try {
      int validLength = length > 100 ? 100 : length;
      System.arraycopy(vvcConfig, 0, vvcConfigData, 0, validLength);
      vvcConfigLength = validLength;
    }catch (Exception e) {

    }
  }

  public static void nativeInit() {
    nativeClassInit();
  }

  /**
   * Creates a VVCDecoder.
   *
   * @param numInputBuffers Number of input buffers.
   * @param numOutputBuffers Number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer, in bytes.
   * @param threads Number of threads libvvc will use to decode.
   * @throws VVCDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public VVCDecoder(
      int numInputBuffers, int numOutputBuffers, int initialInputBufferSize, int threads)
      throws VVCDecoderException {
    super(
        new VideoDecoderInputBuffer[numInputBuffers],
        new VideoDecoderOutputBuffer[numOutputBuffers]);
    if (!VVCLibrary.isAvailable()) {
      throw new VVCDecoderException("Failed to load decoder native library.");
    }
    Log.d("ycptest", "VVCDecoder init  costtime thread num:  "+threads);
    vvcDecoderContext = vvcInit(threads, vvcConfigData, vvcConfigLength);
    if (vvcDecoderContext == DAV1D_ERROR || vvcCheckError(vvcDecoderContext) == DAV1D_ERROR) {
      throw new VVCDecoderException(
          "Failed to initialize decoder. Error: " + vvcGetErrorMessage(vvcDecoderContext));
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "libvvc";
  }

  /**
   * Sets the output mode for frames rendered by the decoder.
   *
   * @param outputMode The output mode.
   */
  public void setOutputMode(@C.VideoOutputMode int outputMode) {
    this.outputMode = outputMode;
    if (outputMode == C.VIDEO_OUTPUT_MODE_NONE) {
      vvcSetSurface(vvcDecoderContext, null);
    }
  }

  @Override
  protected VideoDecoderInputBuffer createInputBuffer() {
    return new VideoDecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected VideoDecoderOutputBuffer createOutputBuffer() {
    return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Nullable
  @Override
  public VVCDecoderException getDav1dErrorMessage(String type) {
    return new VVCDecoderException(type + vvcGetErrorMessage(vvcDecoderContext));
  };

  @Nullable
  @Override
  protected VVCDecoderException decode(
      VideoDecoderInputBuffer inputBuffer, VideoDecoderOutputBuffer outputBuffer, boolean reset) {
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    long costTime = System.currentTimeMillis();
    int gDecodeResult = vvcDecode(vvcDecoderContext, inputData, inputSize, inputBuffer.timeUs, reset);
    if (gDecodeResult == DAV1D_ERROR) {
      return new VVCDecoderException(
          "vvcDecode error: " + vvcGetErrorMessage(vvcDecoderContext));
    }else if(gDecodeResult == DAV1D_DECODE_ONLY) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
      return null;
    }

    boolean decodeOnly = inputBuffer.isDecodeOnly();
    if (!decodeOnly) {
      outputBuffer.init(inputBuffer.timeUs, outputMode, /* supplementalData= */ null);
    }
    // We need to dequeue the decoded frame from the decoder even when the input data is
    // decode-only.
    int getFrameResult = vvcGetFrame(vvcDecoderContext, outputBuffer, decodeOnly, false);

    if (getFrameResult == DAV1D_ERROR) {
      return new VVCDecoderException(
          "vvcGetFrame error: " + vvcGetErrorMessage(vvcDecoderContext));
    }
    if (getFrameResult == DAV1D_DECODE_ONLY) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }
//    if (!decodeOnly) {
//      outputBuffer.colorInfo = inputBuffer.colorInfo;
//    }

    return null;
  }

  private int g_send_data_count = 0;
  private int g_send_data_costTime = 0;
  private int g_getFrame_count = 0;
  private int g_getFrame_costTime = 0;
  private int g_Render_count = 0;
  private int g_Render_costTime = 0;
  private int g_statistics_count = 100;
  @Nullable
  @Override
  protected int decodeSendData(VideoDecoderInputBuffer inputBuffer, boolean reset) {
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    long costTime = System.currentTimeMillis();

    int gDecodeResult = vvcDecode(vvcDecoderContext, inputData, inputSize, inputBuffer.timeUs, reset);

    if(gDecodeResult == DAV1D_OK) {
      g_send_data_costTime += System.currentTimeMillis() - costTime;
      g_send_data_count++;
    }
//    Log.d("ycptest", "decodeSendData g_send_data_count: "+g_send_data_count);
    if(g_send_data_count == g_statistics_count) {
      float averageTime = (float) g_send_data_costTime / g_statistics_count;
      //Log.d("ycptest", "decodeSendData average send data costtime: "+averageTime);
      g_send_data_costTime = 0;
      g_send_data_count = 0;
    }

    return gDecodeResult;
  }

  @Nullable
  @Override
  protected int decodeGetFrame(VideoDecoderOutputBuffer outputBuffer, boolean decodeOnly, boolean flush) {
    long costTime = System.currentTimeMillis();
    outputBuffer.init(-1, outputMode, /* supplementalData= */ null);
    // We need to dequeue the decoded frame from the decoder even when the input data is
    // decode-only.
    int getFrameResult = vvcGetFrame(vvcDecoderContext, outputBuffer, decodeOnly, flush);

    if(getFrameResult == DAV1D_OK) {
      g_getFrame_costTime += System.currentTimeMillis() - costTime;
      g_getFrame_count++;
    }
//    Log.d("ycptest", "decodeGetFrame g_getFrame_count: "+g_getFrame_count);
    if(g_getFrame_count == g_statistics_count) {
      float averageTime = (float) g_getFrame_costTime / g_statistics_count;
     // Log.d("ycptest", "decodeGetFrame average get 1 frame costtime: "+averageTime);
      g_getFrame_costTime = 0;
      g_getFrame_count = 0;
    }
    if (getFrameResult == DAV1D_ERROR) {
      return getFrameResult;
    }
    if (getFrameResult == DAV1D_DECODE_ONLY && !flush) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }

    return getFrameResult;
  }

  @Override
  protected VVCDecoderException createUnexpectedDecodeException(Throwable error) {
    return new VVCDecoderException("Unexpected decode error", error);
  }

  @Override
  public void release() {
    super.release();
    vvcClose(vvcDecoderContext);
  }

  @Override
  protected void releaseOutputBuffer(VideoDecoderOutputBuffer buffer) {
    // Decode only frames do not acquire a reference on the internal decoder buffer and thus do not
    // require a call to vvcReleaseFrame.
    if (buffer.mode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && !buffer.isDecodeOnly()) {
      vvcReleaseFrame(vvcDecoderContext, buffer);
    }
    super.releaseOutputBuffer(buffer);
  }

  /**
   * Renders output buffer to the given surface. Must only be called when in {@link
   * C#VIDEO_OUTPUT_MODE_SURFACE_YUV} mode.
   *
   * @param outputBuffer Output buffer.
   * @param surface Output surface.
   * @throws VVCDecoderException Thrown if called with invalid output mode or frame rendering
   *     fails.
   */
  public void renderToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws VVCDecoderException {
    long costTime = System.currentTimeMillis();
    if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV) {
      throw new VVCDecoderException("Invalid output mode.");
    }
    int renderResult = vvcRenderFrame(vvcDecoderContext, surface, outputBuffer);
    if (renderResult == DAV1D_ERROR) {
      throw new VVCDecoderException(
          "Buffer render error: " + vvcGetErrorMessage(vvcDecoderContext));
    }

    if(renderResult == DAV1D_OK) {
      g_Render_costTime += System.currentTimeMillis() - costTime;
      g_Render_count++;
    }
    if(g_Render_count == g_statistics_count) {
      float averageTime = (float) g_Render_costTime / g_statistics_count;
      Log.d("ycptest", "decodeGetFrame average render 1 frame costtime: "+averageTime);
      g_Render_costTime = 0;
      g_Render_count = 0;
    }
  }

  /**
   *
   */
  private static native void nativeClassInit();

  /**
   * Initializes a libvvc decoder.
   *
   * @param threads Number of threads to be used by a libvvc decoder.
   * @return The address of the decoder context or {@link #DAV1D_ERROR} if there was an error.
   */
  private native long vvcInit(int threads, byte[]vvcConfig, int length);

  /**
   * Deallocates the decoder context.
   *
   * @param context Decoder context.
   */
  private native void vvcClose(long context);

  /**
   * Decodes the encoded data passed.
   *
   * @param context Decoder context.
   * @param encodedData Encoded data.
   * @param length Length of the data buffer.
   * @param ms frame time
   * @return {@link #DAV1D_OK} if successful, {@link #DAV1D_ERROR} if an error occurred.
   */
  private native int vvcDecode(long context, ByteBuffer encodedData, int length, long ms, boolean reset);

  /**
   * Gets the decoded frame.
   *
   * @param context Decoder context.
   * @param outputBuffer Output buffer for the decoded frame.
   * @return {@link #DAV1D_OK} if successful, {@link #DAV1D_DECODE_ONLY} if successful but the frame
   *     is decode-only, {@link #DAV1D_ERROR} if an error occurred.
   */
  private native int vvcGetFrame(
      long context, VideoDecoderOutputBuffer outputBuffer, boolean decodeOnly, boolean endFlush);

  /**
   * Initializes a libdav1d decoder.
   *
   * @param context Decoder context.
   * @param surface Output surface.
   * @return {@link #DAV1D_OK} if successful, {@link #DAV1D_ERROR} if an error occured.
   */
  private static native int vvcSetSurface(long context, Surface surface);
  /**
   * Renders the frame to the surface. Used with {@link C#VIDEO_OUTPUT_MODE_SURFACE_YUV} only.
   *
   * @param context Decoder context.
   * @param surface Output surface.
   * @param outputBuffer Output buffer with the decoded frame.
   * @return {@link #DAV1D_OK} if successful, {@link #DAV1D_ERROR} if an error occured.
   */
  private native int vvcRenderFrame(
      long context, Surface surface, VideoDecoderOutputBuffer outputBuffer);

  /**
   * Releases the frame. Used with {@link C#VIDEO_OUTPUT_MODE_SURFACE_YUV} only.
   *
   * @param context Decoder context.
   * @param outputBuffer Output buffer.
   */
  private native void vvcReleaseFrame(long context, VideoDecoderOutputBuffer outputBuffer);

  /**
   * Returns a human-readable string describing the last error encountered in the given context.
   *
   * @param context Decoder context.
   * @return A string describing the last encountered error.
   */
  private native String vvcGetErrorMessage(long context);

  /**
   * Returns whether an error occured.
   *
   * @param context Decoder context.
   * @return {@link #DAV1D_OK} if there was no error, {@link #DAV1D_ERROR} if an error occured.
   */
  private native int vvcCheckError(long context);

  /**
   * check if dav1d library have been loaded
   */
  public static native void vvcCheckLibrary();
}
