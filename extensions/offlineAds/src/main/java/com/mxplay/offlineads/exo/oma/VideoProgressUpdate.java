package com.mxplay.offlineads.exo.oma;

public final class VideoProgressUpdate {
  public static final VideoProgressUpdate VIDEO_TIME_NOT_READY = new VideoProgressUpdate(-1L, -1L);
  private float currentTime;
  private float duration;

  public VideoProgressUpdate(long currentTime, long duration) {
    this.currentTime = (float)currentTime / 1000.0F;
    this.duration = (float)duration / 1000.0F;
  }

  public final float getCurrentTime() {
    return this.currentTime;
  }

  public final float getDuration() {
    return this.duration;
  }

  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else if (this.getClass() != other.getClass()) {
      return false;
    } else {
      VideoProgressUpdate o = (VideoProgressUpdate)other;
      if (Float.floatToIntBits(this.currentTime) != Float.floatToIntBits(o.currentTime)) {
        return false;
      } else {
        return Float.floatToIntBits(this.duration) == Float.floatToIntBits(o.duration);
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