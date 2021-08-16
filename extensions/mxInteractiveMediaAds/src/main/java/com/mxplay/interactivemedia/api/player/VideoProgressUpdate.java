package com.mxplay.interactivemedia.api.player;

import java.util.Arrays;

public final class VideoProgressUpdate {
  public static final VideoProgressUpdate VIDEO_TIME_NOT_READY;
  private final long currentTimeMs;
  private final long durationMs;

  public VideoProgressUpdate(long var1, long var3) {
    this.currentTimeMs = var1;
    this.durationMs = var3;
  }

  public long getCurrentTimeMs() {
    return this.currentTimeMs;
  }

  public long getDurationMs() {
    return this.durationMs;
  }



  public boolean equals(Object var1) {
    if (this == var1) {
      return true;
    } else if (var1 == null) {
      return false;
    } else if (this.getClass() != var1.getClass()) {
      return false;
    } else {
      VideoProgressUpdate var2 = (VideoProgressUpdate)var1;
      if (this.currentTimeMs != var2.currentTimeMs) {
        return false;
      } else {
        return this.durationMs == var2.durationMs;
      }
    }
  }

  public int hashCode() {
    Object[] var1 = new Object[]{this.currentTimeMs, this.durationMs};
    return Arrays.hashCode(var1);
  }

  private float getCurrentTimeSecondsFloat() {
    return (float)this.currentTimeMs / 1000.0F;
  }

  private float getDurationSecondsFloat() {
    return (float)this.durationMs / 1000.0F;
  }

  public boolean isVideoStarted(){
    return durationMs > 0 && currentTimeMs > 0;
  }
  public boolean isFirstQuartileReached(){
    return durationMs > 0 && currentTimeMs > 0 && ((currentTimeMs * 100)/durationMs) >= 25;
  }

  public boolean isMidPointReached(){
    return durationMs > 0 && currentTimeMs > 0 && ((currentTimeMs * 100)/durationMs) >= 50;
  }

  public boolean isThirdQuartileReached(){
    return durationMs > 0 && currentTimeMs > 0 && ((currentTimeMs * 100)/durationMs) >= 75;
  }

  public boolean isCompleted(){
    return durationMs > 0 && currentTimeMs > 0 && ((currentTimeMs * 100)/durationMs) >= 99;
  }

  static {
    VideoProgressUpdate var0 = new VideoProgressUpdate(-1L, -1L);
    VIDEO_TIME_NOT_READY = var0;
  }
}