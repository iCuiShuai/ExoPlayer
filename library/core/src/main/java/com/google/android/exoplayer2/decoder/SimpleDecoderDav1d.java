/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.decoder;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.util.ArrayDeque;

/** Base class for {@link Decoder}s that use their own decode thread. */
@SuppressWarnings("UngroupedOverloads")
public abstract class SimpleDecoderDav1d<
        I extends DecoderInputBuffer, O extends OutputBuffer, E extends Exception>
    implements Decoder<I, O, E> {
  public static final int DAV1D_ERROR = 0;
  public static final int DAV1D_OK = 1;
  public static final int DAV1D_DECODE_ONLY = 2;
  public static final int DAV1D_DECODE_AGAIN = 3;

  private final Thread decodeThread;

  private final Object lock;
  private final ArrayDeque<I> queuedInputBuffers;
  private final ArrayDeque<O> queuedOutputBuffers;
  private final I[] availableInputBuffers;
  private final O[] availableOutputBuffers;

  private int availableInputBufferCount;
  private int availableOutputBufferCount;
  private I dequeuedInputBuffer;
  private I decodeInputBuffer;

  private E exception;
  private boolean flushed;
  private boolean released;
  private int skippedOutputBufferCount;

  private boolean flushFrames = false;
  /**
   * @param inputBuffers An array of nulls that will be used to store references to input buffers.
   * @param outputBuffers An array of nulls that will be used to store references to output buffers.
   */
  protected SimpleDecoderDav1d(I[] inputBuffers, O[] outputBuffers) {
    lock = new Object();
    decodeInputBuffer = null;
    queuedInputBuffers = new ArrayDeque<>();
    queuedOutputBuffers = new ArrayDeque<>();
    availableInputBuffers = inputBuffers;
    availableInputBufferCount = inputBuffers.length;
    for (int i = 0; i < availableInputBufferCount; i++) {
      availableInputBuffers[i] = createInputBuffer();
    }
    availableOutputBuffers = outputBuffers;
    availableOutputBufferCount = outputBuffers.length;
    for (int i = 0; i < availableOutputBufferCount; i++) {
      availableOutputBuffers[i] = createOutputBuffer();
    }
    decodeThread = new Thread() {
      @Override
      public void run() {
        SimpleDecoderDav1d.this.run();
      }
    };
    decodeThread.start();
  }

  /**
   * Sets the initial size of each input buffer.
   * <p>
   * This method should only be called before the decoder is used (i.e. before the first call to
   * {@link #dequeueInputBuffer()}.
   *
   * @param size The required input buffer size.
   */
  protected final void setInitialInputBufferSize(int size) {
    Assertions.checkState(availableInputBufferCount == availableInputBuffers.length);
    for (I inputBuffer : availableInputBuffers) {
      inputBuffer.ensureSpaceForWrite(size);
    }
  }

  @Override
  @Nullable
  public final I dequeueInputBuffer() throws E {
    synchronized (lock) {
      maybeThrowException();
      Assertions.checkState(dequeuedInputBuffer == null);
      dequeuedInputBuffer = availableInputBufferCount == 0 ? null
          : availableInputBuffers[--availableInputBufferCount];
      return dequeuedInputBuffer;
    }
  }

  @Override
  public final void queueInputBuffer(I inputBuffer) throws E {
    synchronized (lock) {
      maybeThrowException();
      Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
      queuedInputBuffers.addLast(inputBuffer);
      maybeNotifyDecodeLoop();
      dequeuedInputBuffer = null;
    }
  }

  @Override
  @Nullable
  public final O dequeueOutputBuffer() throws E {
    synchronized (lock) {
      maybeThrowException();
      if (queuedOutputBuffers.isEmpty()) {
        return null;
      }
      return queuedOutputBuffers.removeFirst();
    }
  }

  /**
   * Releases an output buffer back to the decoder.
   *
   * @param outputBuffer The output buffer being released.
   */
  @CallSuper
  protected void releaseOutputBuffer(O outputBuffer) {
    synchronized (lock) {
      releaseOutputBufferInternal(outputBuffer);
      maybeNotifyDecodeLoop();
    }
  }

  @Override
  public final void flush() {
    synchronized (lock) {
      flushFrames = false;
      flushed = true;
      skippedOutputBufferCount = 0;
      if (dequeuedInputBuffer != null) {
        releaseInputBufferInternal(dequeuedInputBuffer);
        dequeuedInputBuffer = null;
      }
      while (!queuedInputBuffers.isEmpty()) {
        releaseInputBufferInternal(queuedInputBuffers.removeFirst());
      }
      while (!queuedOutputBuffers.isEmpty()) {
        queuedOutputBuffers.removeFirst().release();
      }

      if (decodeInputBuffer != null) {
        releaseInputBufferInternal(decodeInputBuffer);
        decodeInputBuffer = null;
      }
    }
  }

  @CallSuper
  @Override
  public void release() {
    synchronized (lock) {
      released = true;
      lock.notify();
    }
    try {
      decodeThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Throws a decode exception, if there is one.
   *
   * @throws E The decode exception.
   */
  private void maybeThrowException() throws E {
    if (exception != null) {
      throw exception;
    }
  }

  /**
   * Notifies the decode loop if there exists a queued input buffer and an available output buffer
   * to decode into.
   * <p>
   * Should only be called whilst synchronized on the lock object.
   */
  private void maybeNotifyDecodeLoop() {
    if (canDecodeBuffer()) {
      lock.notify();
    }
  }

  private void run() {
    try {
      while (decode()) {
        // Do nothing.
      }
    } catch (InterruptedException e) {
      // Not expected.
      throw new IllegalStateException(e);
    }
  }

  private boolean decode() throws InterruptedException {
    I inputBuffer;
    O outputBuffer;
    boolean resetDecoder;
    int decodeResult = DAV1D_OK;
    int getFrameResult = DAV1D_OK;

    // Wait until we have an input buffer to decode, and an output buffer to decode into.
    synchronized (lock) {
      while (!released && !canDecodeBuffer()) {
        lock.wait();
      }
      if (released) {
        return false;
      }

      if(decodeInputBuffer == null && !flushFrames) {
        decodeInputBuffer = queuedInputBuffers.removeFirst();
      }

      outputBuffer = availableOutputBuffers[--availableOutputBufferCount];

      resetDecoder = flushed;
      flushed = false;
    }

    //todo dav1d may be need modified
    if (decodeInputBuffer != null && decodeInputBuffer.isEndOfStream()) {
      flushFrames = true;
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    } else {
      if (decodeInputBuffer != null && decodeInputBuffer.isDecodeOnly()) {
        outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
      }
      try {
        boolean decodeOnly = decodeInputBuffer != null ? decodeInputBuffer.isDecodeOnly() : false;
        if(flushFrames) {
          decodeResult = DAV1D_OK;
        }else {
          decodeResult = decodeSendData(decodeInputBuffer, resetDecoder);
        }
        if(decodeResult == DAV1D_ERROR) {
          exception = getDav1dErrorMessage("decodeSendData error: ");
        }else {
          getFrameResult = decodeGetFrame(outputBuffer, decodeOnly, flushFrames);
          if(getFrameResult == DAV1D_DECODE_ONLY) {
            if(flushFrames){
              outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
            }else{
              outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
            }
//            outputBuffer.addFlag(flushFrames ? C.BUFFER_FLAG_END_OF_STREAM : C.BUFFER_FLAG_DECODE_ONLY);
          }
          if(getFrameResult == DAV1D_ERROR) {
            exception = getDav1dErrorMessage("decodeSendData error: ");
          }
        }
      } catch (RuntimeException e) {
        // This can occur if a sample is malformed in a way that the decoder is not robust against.
        // We don't want the process to die in this case, but we do want to propagate the error.
        exception = createUnexpectedDecodeException(e);
      } catch (OutOfMemoryError e) {
        // This can occur if a sample is malformed in a way that causes the decoder to think it
        // needs to allocate a large amount of memory. We don't want the process to die in this
        // case, but we do want to propagate the error.
        exception = createUnexpectedDecodeException(e);
      }
      if (exception != null) {
        // Memory barrier to ensure that the decoder exception is visible from the playback thread.
        synchronized (lock) {}
        return false;
      }
    }
    synchronized (lock) {
      if (flushed) {
        outputBuffer.release();
      } else if (outputBuffer.isDecodeOnly()) {
        skippedOutputBufferCount++;
        outputBuffer.release();
      } else {
        outputBuffer.skippedOutputBufferCount = skippedOutputBufferCount;
        skippedOutputBufferCount = 0;
        queuedOutputBuffers.addLast(outputBuffer);
      }
      // Make the input buffer available again.
      if(decodeInputBuffer != null && decodeResult != DAV1D_DECODE_AGAIN) {
        releaseInputBufferInternal(decodeInputBuffer);
        decodeInputBuffer = null;
      }

    }

    return true;
  }

  private boolean canDecodeBuffer() {
    return (!queuedInputBuffers.isEmpty() || flushFrames) && availableOutputBufferCount > 0;
  }

  private void releaseInputBufferInternal(I inputBuffer) {
    inputBuffer.clear();
    availableInputBuffers[availableInputBufferCount++] = inputBuffer;
  }

  private void releaseOutputBufferInternal(O outputBuffer) {
    outputBuffer.clear();
    availableOutputBuffers[availableOutputBufferCount++] = outputBuffer;
  }

  /**
   * Creates a new input buffer.
   */
  protected abstract I createInputBuffer();

  /**
   * Creates a new output buffer.
   */
  protected abstract O createOutputBuffer();

  /**
   * Creates an exception to propagate for an unexpected decode error.
   *
   * @param error The unexpected decode error.
   * @return The exception to propagate.
   */
  protected abstract E createUnexpectedDecodeException(Throwable error);

  /**
   * Decodes the {@code inputBuffer} and stores any decoded output in {@code outputBuffer}.
   *
   * @param inputBuffer The buffer to decode.
   * @param outputBuffer The output buffer to store decoded data. The flag {@link
   *     C#BUFFER_FLAG_DECODE_ONLY} will be set if the same flag is set on {@code inputBuffer}, but
   *     may be set/unset as required. If the flag is set when the call returns then the output
   *     buffer will not be made available to dequeue. The output buffer may not have been populated
   *     in this case.
   * @param reset Whether the decoder must be reset before decoding.
   * @return A decoder exception if an error occurred, or null if decoding was successful.
   */
  @Nullable
  protected abstract E decode(I inputBuffer, O outputBuffer, boolean reset);

  /**
   * send the {@code inputBuffer} to dav1d decoder.
   *
   * @param inputBuffer The buffer to decode.
   * @param reset Whether the decoder must be reset before decoding.
   * @return
   * DAV1D_OK:             Success send data.
   * DAV1D_DECODE_AGAIN:   dav1d decoder has enough data, the inputBuffer need to send again
   * DAV1D_ERROR:          an error ocurred
   */
  @Nullable
  protected abstract int decodeSendData(I inputBuffer , boolean reset);

  /**
   * Decodes a frame output in {@code outputBuffer}.
   *
   * @param outputBuffer The output buffer to store decoded data. The flag {@link
   *     C#BUFFER_FLAG_DECODE_ONLY} will be set if the same flag is set on {@code inputBuffer}, but
   *     may be set/unset as required. If the flag is set when the call returns then the output
   *     buffer will not be made available to dequeue. The output buffer may not have been populated
   *     in this case.
   * @param decodeOnly only decode the frame, outputbuffer doesn't have actually buffer reference.
   * @return
   *    DAV1D_OK:             Success receive a frame.
   *    DAV1D_DECODE_ONLY:    get frame failed, need to get again
   *    DAV1D_ERROR:          an error ocurred
   */
  @Nullable
  protected abstract int decodeGetFrame(O outputBuffer, boolean decodeOnly, boolean flush);

  public abstract E getDav1dErrorMessage(String type);
}
