package com.mxplay.offlineads.exo.oma;

public final class VideoProgressUpdate {
  public static final VideoProgressUpdate VIDEO_TIME_NOT_READY = new VideoProgressUpdate(-1L, -1L);
  private float currentTime;
  private float duration;

  public VideoProgressUpdate(long var1, long var3) {
    this.currentTime = (float)var1 / 1000.0F;
    this.duration = (float)var3 / 1000.0F;
  }

  public final float getCurrentTime() {
    return this.currentTime;
  }

  public final float getDuration() {
    return this.duration;
  }

  public final boolean equals(Object var1) {
    if (this == var1) {
      return true;
    } else if (var1 == null) {
      return false;
    } else if (this.getClass() != var1.getClass()) {
      return false;
    } else {
      VideoProgressUpdate var2 = (VideoProgressUpdate)var1;
      if (Float.floatToIntBits(this.currentTime) != Float.floatToIntBits(var2.currentTime)) {
        return false;
      } else {
        return Float.floatToIntBits(this.duration) == Float.floatToIntBits(var2.duration);
      }
    }
  }

  @Override
  public int hashCode() {
    int result = (currentTime != +0.0f ? Float.floatToIntBits(currentTime) : 0);
    result = 31 * result + (duration != +0.0f ? Float.floatToIntBits(duration) : 0);
    return result;
  }

  public final String toString() {
    float var1 = this.currentTime;
    float var2 = this.duration;
    return (new StringBuilder(75)).append("VideoProgressUpdate [currentTime=").append(var1).append(", duration=").append(var2).append("]").toString();
  }
}