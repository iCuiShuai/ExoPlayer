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
package com.google.android.exoplayer2;

import android.os.Build;
import android.os.SystemClock;

import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;

/**
 * The default {@link LoadControl} implementation.
 */
public class DynamicLoadControl implements LoadControl {

  /**
   * The default minimum duration of media that the player will attempt to ensure is buffered at all
   * times, in milliseconds.
   */
  public static final int DEFAULT_MIN_BUFFER_MS = 15000;

  /**
   * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
   */
  public static final int DEFAULT_MAX_BUFFER_MS = 30000;

  /**
   * The default duration of media that must be buffered for playback to start or resume following a
   * user action such as a seek, in milliseconds.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2000;

  /**
   * The default duration of media that must be buffered for playback to resume after a rebuffer,
   * in milliseconds. A rebuffer is defined to be caused by buffer depletion rather than a user
   * action.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS  = 2000;

  /**
   * The default target buffer size in bytes. When set to {@link C#LENGTH_UNSET}, the load control
   * automatically determines its target buffer size.
   */
  public static final int DEFAULT_TARGET_BUFFER_BYTES = C.LENGTH_UNSET;

  /** The default prioritization of buffer time constraints over size constraints. */
  public static final boolean DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS = true;
  public static final long BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_US_MIN = 500 * 1000L;
  /** A default size in bytes for a video buffer. */
  public static final int DEFAULT_VIDEO_BUFFER_SIZE = 500 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for an audio buffer. */
  public static final int DEFAULT_AUDIO_BUFFER_SIZE = 54 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a text buffer. */
  public static final int DEFAULT_TEXT_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a metadata buffer. */
  public static final int DEFAULT_METADATA_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a camera motion buffer. */
  public static final int DEFAULT_CAMERA_MOTION_BUFFER_SIZE = 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;

  /** A default size in bytes for a muxed buffer (e.g. containing video, audio and text). */
  public static final int DEFAULT_MUXED_BUFFER_SIZE =
          DEFAULT_VIDEO_BUFFER_SIZE + DEFAULT_AUDIO_BUFFER_SIZE + DEFAULT_TEXT_BUFFER_SIZE;


  private final DefaultAllocator allocator;

  private final long minBufferUs;
  private final long maxBufferUs;
  private final long bufferForPlaybackUs;
  private long bufferForPlaybackAfterRebufferUs;
  private final int targetBufferBytesOverwrite;
  private final boolean prioritizeTimeOverSizeThresholds;
  private final PriorityTaskManager priorityTaskManager;

  private int targetBufferSize;
  private boolean isBuffering;
  private long bufferForPlaybackAfterRebufferUsMax;
  private long lastElapsedTimeNanos;

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   */
  public DynamicLoadControl() {
    this(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE));
  }

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   */
  public DynamicLoadControl(DefaultAllocator allocator) {
    this(
            allocator,
            DEFAULT_MIN_BUFFER_MS,
            DEFAULT_MAX_BUFFER_MS,
            DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            DEFAULT_TARGET_BUFFER_BYTES,
            DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS);
  }

  /**
   * Constructs a new instance.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
   *     buffered at all times, in milliseconds.
   * @param maxBufferMs The maximum duration of media that the player will attempt buffer, in
   *     milliseconds.
   * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
   *     resume following a user action such as a seek, in milliseconds.
   * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
   *     playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action.
   * @param targetBufferBytes The target buffer size in bytes. If set to {@link C#LENGTH_UNSET}, the
   *     target buffer size will be calculated using {@link #calculateTargetBufferSize(Renderer[],
   *     TrackSelectionArray)}.
   * @param prioritizeTimeOverSizeThresholds Whether the load control prioritizes buffer time
   */
  public DynamicLoadControl(
      DefaultAllocator allocator,
      int minBufferMs,
      int maxBufferMs,
      int bufferForPlaybackMs,
      int bufferForPlaybackAfterRebufferMs,
      int targetBufferBytes,
      boolean prioritizeTimeOverSizeThresholds) {
    this(
        allocator,
        minBufferMs,
        maxBufferMs,
        bufferForPlaybackMs,
        bufferForPlaybackAfterRebufferMs,
        targetBufferBytes,
        prioritizeTimeOverSizeThresholds,
        null);
  }

  /**
   * Constructs a new instance.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
   *     buffered at all times, in milliseconds.
   * @param maxBufferMs The maximum duration of media that the player will attempt buffer, in
   *     milliseconds.
   * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
   *     resume following a user action such as a seek, in milliseconds.
   * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
   *     playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action.
   * @param targetBufferBytes The target buffer size in bytes. If set to {@link C#LENGTH_UNSET}, the
   *     target buffer size will be calculated using {@link #calculateTargetBufferSize(Renderer[],
   *     TrackSelectionArray)}.
   * @param prioritizeTimeOverSizeThresholds Whether the load control prioritizes buffer time
   *     constraints over buffer size constraints.
   * @param priorityTaskManager If not null, registers itself as a task with priority {@link
   *     C#PRIORITY_PLAYBACK} during loading periods, and unregisters itself during draining
   */
  public DynamicLoadControl(
      DefaultAllocator allocator,
      int minBufferMs,
      int maxBufferMs,
      int bufferForPlaybackMs,
      int bufferForPlaybackAfterRebufferMs,
      int targetBufferBytes,
      boolean prioritizeTimeOverSizeThresholds,
      PriorityTaskManager priorityTaskManager) {
    this.allocator = allocator;
    minBufferUs = minBufferMs * 1000L;
    maxBufferUs = maxBufferMs * 1000L;
    targetBufferBytesOverwrite = targetBufferBytes;
    bufferForPlaybackUs = bufferForPlaybackMs * 1000L;
    bufferForPlaybackAfterRebufferUsMax = bufferForPlaybackAfterRebufferMs * 1000L;
    bufferForPlaybackAfterRebufferUs = BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_US_MIN;
    this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
    this.priorityTaskManager = priorityTaskManager;
  }

  @Override
  public void onPrepared() {
    reset(false);
  }

  @Override
  public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
                               TrackSelectionArray trackSelections) {
    targetBufferSize =
        targetBufferBytesOverwrite == C.LENGTH_UNSET
            ? calculateTargetBufferSize(renderers, trackSelections)
            : targetBufferBytesOverwrite;
    allocator.setTargetBufferSize(targetBufferSize);
  }

  @Override
  public void onStopped() {
    reset(true);
  }

  @Override
  public void onReleased() {
    reset(true);
  }

  @Override
  public Allocator getAllocator() {
    return allocator;
  }

  @Override
  public long getBackBufferDurationUs() {
    return 0;
  }

  @Override
  public boolean retainBackBufferFromKeyframe() {
    return false;
  }

  @Override
  public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
    boolean targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;
    boolean wasBuffering = isBuffering;
    if (prioritizeTimeOverSizeThresholds) {
      isBuffering =
          bufferedDurationUs < minBufferUs // below low watermark
              || (bufferedDurationUs <= maxBufferUs // between watermarks
                  && isBuffering
                  && !targetBufferSizeReached);
    } else {
      isBuffering =
          !targetBufferSizeReached
              && (bufferedDurationUs < minBufferUs // below low watermark
                  || (bufferedDurationUs <= maxBufferUs && isBuffering)); // between watermarks
    }
    if (priorityTaskManager != null && isBuffering != wasBuffering) {
      if (isBuffering) {
        priorityTaskManager.add(C.PRIORITY_PLAYBACK);
      } else {
        priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
      }
    }
    return isBuffering;
  }

  @Override
  public boolean shouldStartPlayback(
      long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
    if (!rebuffering)
      return true;

    bufferedDurationUs = Util.getPlayoutDurationForMediaDuration(bufferedDurationUs, playbackSpeed);
    long minBufferDurationUs = rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
    boolean ret = minBufferDurationUs <= 0
            || bufferedDurationUs >= minBufferDurationUs
            || (!prioritizeTimeOverSizeThresholds
            && allocator.getTotalBytesAllocated() >= targetBufferSize);

    if (!ret && bufferForPlaybackAfterRebufferUs < bufferForPlaybackAfterRebufferUsMax) {
      long now = 0;

      if (Build.VERSION.SDK_INT >= 17) {
        now = SystemClock.elapsedRealtimeNanos();
      }
      long step = now - lastElapsedTimeNanos;
      if (step < 1 || step > 1000 * 200) {
        step = 1000 * 10;
      }

      bufferForPlaybackAfterRebufferUs += step;
      if (bufferForPlaybackAfterRebufferUs > bufferForPlaybackAfterRebufferUsMax) {
        bufferForPlaybackAfterRebufferUs = bufferForPlaybackAfterRebufferUsMax;
      }

//            Log.e("test", "shouldStartPlayback: " + (now - lastElapsedTimeNanos));
      lastElapsedTimeNanos = now;
    }

    if (ret) {
      bufferForPlaybackAfterRebufferUs = BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_US_MIN;
    }


//    if (!ret && bufferForPlaybackAfterRebufferUs < bufferForPlaybackAfterRebufferUsMax) {
//      bufferForPlaybackAfterRebufferUs += 1000 * 10;
//    }
//
//    if (ret) {
//      bufferForPlaybackAfterRebufferUs = BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_US_MIN;
//    }

    return ret;
  }

  /**
   * Calculate target buffer size in bytes based on the selected tracks. The player will try not to
   * exceed this target buffer. Only used when {@code targetBufferBytes} is {@link C#LENGTH_UNSET}.
   *
   * @param renderers The renderers for which the track were selected.
   * @param trackSelectionArray The selected tracks.
   * @return The target buffer size in bytes.
   */
  protected int calculateTargetBufferSize(
          Renderer[] renderers, TrackSelectionArray trackSelectionArray) {
    int targetBufferSize = 0;
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelectionArray.get(i) != null) {
        targetBufferSize += getDefaultBufferSize(renderers[i].getTrackType());
      }
    }
    return targetBufferSize;
  }

  private void reset(boolean resetAllocator) {
    targetBufferSize = 0;
    if (priorityTaskManager != null && isBuffering) {
      priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
    }
    isBuffering = false;
    if (resetAllocator) {
      allocator.reset();
    }
  }

  private static int getDefaultBufferSize(int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_DEFAULT:
        return DEFAULT_MUXED_BUFFER_SIZE;
      case C.TRACK_TYPE_AUDIO:
        return DEFAULT_AUDIO_BUFFER_SIZE;
      case C.TRACK_TYPE_VIDEO:
        return DEFAULT_VIDEO_BUFFER_SIZE;
      case C.TRACK_TYPE_TEXT:
        return DEFAULT_TEXT_BUFFER_SIZE;
      case C.TRACK_TYPE_METADATA:
        return DEFAULT_METADATA_BUFFER_SIZE;
      case C.TRACK_TYPE_CAMERA_MOTION:
        return DEFAULT_CAMERA_MOTION_BUFFER_SIZE;
      case C.TRACK_TYPE_NONE:
        return 0;
      default:
        throw new IllegalArgumentException();
    }
  }


}
