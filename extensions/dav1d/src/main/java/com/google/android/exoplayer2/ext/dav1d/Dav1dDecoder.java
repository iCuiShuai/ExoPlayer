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
package com.google.android.exoplayer2.ext.dav1d;

import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.SimpleDecoderDav1d;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderInputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;

/** Dav1d decoder. */
/* package */ final class Dav1dDecoder
    extends SimpleDecoderDav1d<VideoDecoderInputBuffer, VideoDecoderOutputBuffer, Dav1dDecoderException> {

  // LINT.IfChange

  // LINT.ThenChange(../../../../../../../jni/dav1d_jni.cc)

  private final long dav1dDecoderContext;

  @C.VideoOutputMode private volatile int outputMode;

  public static void nativeInit() {
      nativeClassInit();
  }

  /**
   * Creates a Dav1dDecoder.
   *
   * @param numInputBuffers Number of input buffers.
   * @param numOutputBuffers Number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer, in bytes.
   * @param threads Number of threads libgdav1d will use to decode.
   * @throws Dav1dDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public Dav1dDecoder(
      int numInputBuffers, int numOutputBuffers, int initialInputBufferSize, int threads)
      throws Dav1dDecoderException {
    super(
        new VideoDecoderInputBuffer[numInputBuffers],
        new VideoDecoderOutputBuffer[numOutputBuffers]);
    if (!Dav1dLibrary.isAvailable()) {
      throw new Dav1dDecoderException("Failed to load decoder native library.");
    }
    dav1dDecoderContext = dav1dInit(threads);
    if (dav1dDecoderContext == DAV1D_ERROR || dav1dCheckError(dav1dDecoderContext) == DAV1D_ERROR) {
      throw new Dav1dDecoderException(
          "Failed to initialize decoder. Error: " + dav1dGetErrorMessage(dav1dDecoderContext));
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "libdav1d";
  }

  /**
   * Sets the output mode for frames rendered by the decoder.
   *
   * @param outputMode The output mode.
   */
  public void setOutputMode(@C.VideoOutputMode int outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  protected VideoDecoderInputBuffer createInputBuffer() {
    return new VideoDecoderInputBuffer();
  }

  @Override
  protected VideoDecoderOutputBuffer createOutputBuffer() {
    return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Nullable
  @Override
  public Dav1dDecoderException getDav1dErrorMessage(String type) {
    return new Dav1dDecoderException(type + dav1dGetErrorMessage(dav1dDecoderContext));
  };

  @Nullable
  @Override
  protected Dav1dDecoderException decode(
      VideoDecoderInputBuffer inputBuffer, VideoDecoderOutputBuffer outputBuffer, boolean reset) {
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    long costTime = System.currentTimeMillis();
    int gDecodeResult = dav1dDecode(dav1dDecoderContext, inputData, inputSize, inputBuffer.timeUs, reset);
    if (gDecodeResult == DAV1D_ERROR) {
      return new Dav1dDecoderException(
          "dav1dDecode error: " + dav1dGetErrorMessage(dav1dDecoderContext));
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
    int getFrameResult = dav1dGetFrame(dav1dDecoderContext, outputBuffer, decodeOnly);

    if (getFrameResult == DAV1D_ERROR) {
      return new Dav1dDecoderException(
          "dav1dGetFrame error: " + dav1dGetErrorMessage(dav1dDecoderContext));
    }
    if (getFrameResult == DAV1D_DECODE_ONLY) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }
    if (!decodeOnly) {
      outputBuffer.colorInfo = inputBuffer.colorInfo;
    }

    return null;
  }

  @Nullable
  @Override
  protected int decodeSendData(VideoDecoderInputBuffer inputBuffer, boolean reset) {
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    long costTime = System.currentTimeMillis();

    int gDecodeResult = dav1dDecode(dav1dDecoderContext, inputData, inputSize, inputBuffer.timeUs, reset);

    return gDecodeResult;
  }

  @Nullable
  @Override
  protected int decodeGetFrame(VideoDecoderOutputBuffer outputBuffer, boolean decodeOnly, boolean flush) {
    long costTime = System.currentTimeMillis();
    outputBuffer.init(-1, outputMode, /* supplementalData= */ null);
    // We need to dequeue the decoded frame from the decoder even when the input data is
    // decode-only.
    int getFrameResult = dav1dGetFrame(dav1dDecoderContext, outputBuffer, decodeOnly);

    if (getFrameResult == DAV1D_ERROR) {
      return getFrameResult;
    }
    if (getFrameResult == DAV1D_DECODE_ONLY && !flush) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }

    return getFrameResult;
  }

  @Override
  protected Dav1dDecoderException createUnexpectedDecodeException(Throwable error) {
    return new Dav1dDecoderException("Unexpected decode error", error);
  }

  @Override
  public void release() {
    super.release();
    dav1dClose(dav1dDecoderContext);
  }

  @Override
  protected void releaseOutputBuffer(VideoDecoderOutputBuffer buffer) {
    // Decode only frames do not acquire a reference on the internal decoder buffer and thus do not
    // require a call to dav1dReleaseFrame.
    if (buffer.mode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && !buffer.isDecodeOnly()) {
      dav1dReleaseFrame(dav1dDecoderContext, buffer);
    }
    super.releaseOutputBuffer(buffer);
  }

  /**
   * Renders output buffer to the given surface. Must only be called when in {@link
   * C#VIDEO_OUTPUT_MODE_SURFACE_YUV} mode.
   *
   * @param outputBuffer Output buffer.
   * @param surface Output surface.
   * @throws Dav1dDecoderException Thrown if called with invalid output mode or frame rendering
   *     fails.
   */
  public void renderToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws Dav1dDecoderException {
    if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV) {
      throw new Dav1dDecoderException("Invalid output mode.");
    }
    if (dav1dRenderFrame(dav1dDecoderContext, surface, outputBuffer) == DAV1D_ERROR) {
      throw new Dav1dDecoderException(
          "Buffer render error: " + dav1dGetErrorMessage(dav1dDecoderContext));
    }
  }

  private static native void nativeClassInit();
  /**
   * Initializes a libdav1d decoder.
   *
   * @param threads Number of threads to be used by a libdav1d decoder.
   * @return The address of the decoder context or {@link #DAV1D_ERROR} if there was an error.
   */
  private native long dav1dInit(int threads);

  /**
   * Deallocates the decoder context.
   *
   * @param context Decoder context.
   */
  private native void dav1dClose(long context);

  /**
   * Decodes the encoded data passed.
   *
   * @param context Decoder context.
   * @param encodedData Encoded data.
   * @param length Length of the data buffer.
   * @param ms frame time
   * @return {@link #DAV1D_OK} if successful, {@link #DAV1D_ERROR} if an error occurred.
   */
  private native int dav1dDecode(long context, ByteBuffer encodedData, int length, long ms, boolean reset);

  /**
   * Gets the decoded frame.
   *
   * @param context Decoder context.
   * @param outputBuffer Output buffer for the decoded frame.
   * @return {@link #DAV1D_OK} if successful, {@link #DAV1D_DECODE_ONLY} if successful but the frame
   *     is decode-only, {@link #DAV1D_ERROR} if an error occurred.
   */
  private native int dav1dGetFrame(
      long context, VideoDecoderOutputBuffer outputBuffer, boolean decodeOnly);

  /**
   * Renders the frame to the surface. Used with {@link C#VIDEO_OUTPUT_MODE_SURFACE_YUV} only.
   *
   * @param context Decoder context.
   * @param surface Output surface.
   * @param outputBuffer Output buffer with the decoded frame.
   * @return {@link #DAV1D_OK} if successful, {@link #DAV1D_ERROR} if an error occured.
   */
  private native int dav1dRenderFrame(
      long context, Surface surface, VideoDecoderOutputBuffer outputBuffer);

  /**
   * Releases the frame. Used with {@link C#VIDEO_OUTPUT_MODE_SURFACE_YUV} only.
   *
   * @param context Decoder context.
   * @param outputBuffer Output buffer.
   */
  private native void dav1dReleaseFrame(long context, VideoDecoderOutputBuffer outputBuffer);

  /**
   * Returns a human-readable string describing the last error encountered in the given context.
   *
   * @param context Decoder context.
   * @return A string describing the last encountered error.
   */
  private native String dav1dGetErrorMessage(long context);

  /**
   * Returns whether an error occured.
   *
   * @param context Decoder context.
   * @return {@link #DAV1D_OK} if there was no error, {@link #DAV1D_ERROR} if an error occured.
   */
  private native int dav1dCheckError(long context);
}
