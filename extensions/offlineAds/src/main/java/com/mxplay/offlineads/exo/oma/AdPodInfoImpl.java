package com.mxplay.offlineads.exo.oma;

public class AdPodInfoImpl implements AdPodInfo {
  public int totalAds = 1;
  public int adPosition = 1;
  public boolean isBumper = false;
  public double maxDuration = -1.0D;
  public int podIndex;
  public float timeOffset;

  public AdPodInfoImpl() {
  }

  public AdPodInfoImpl(AdPodInfoImpl adPodInfo, int adPosition) {
    this.totalAds = adPodInfo.totalAds;
    this.adPosition = adPosition;
    this.maxDuration = adPodInfo.maxDuration;
    this.podIndex = adPodInfo.podIndex;
    this.timeOffset = adPodInfo.timeOffset;
  }

  public final int getTotalAds() {
    return this.totalAds;
  }

  public final int getAdPosition() {
    return this.adPosition;
  }

  public final boolean isBumper() {
    return this.isBumper;
  }

  public final double getMaxDuration() {
    return this.maxDuration;
  }

  public final int getPodIndex() {
    return this.podIndex;
  }

  public final float getTimeOffset() {
    return this.timeOffset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdPodInfoImpl)) {
      return false;
    }

    AdPodInfoImpl adPodInfo = (AdPodInfoImpl) o;

    if (totalAds != adPodInfo.totalAds) {
      return false;
    }
    if (adPosition != adPodInfo.adPosition) {
      return false;
    }
    if (isBumper != adPodInfo.isBumper) {
      return false;
    }
    if (Double.compare(adPodInfo.maxDuration, maxDuration) != 0) {
      return false;
    }
    if (podIndex != adPodInfo.podIndex) {
      return false;
    }
    return Float.compare(adPodInfo.timeOffset, timeOffset) == 0;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = totalAds;
    result = 31 * result + adPosition;
    result = 31 * result + (isBumper ? 1 : 0);
    temp = Double.doubleToLongBits(maxDuration);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    result = 31 * result + podIndex;
    result = 31 * result + (timeOffset != +0.0f ? Float.floatToIntBits(timeOffset) : 0);
    return result;
  }

  public final String toString() {
    int var1 = this.totalAds;
    int var2 = this.adPosition;
    boolean var3 = this.isBumper;
    double var4 = this.maxDuration;
    int var6 = this.podIndex;
    double var7 = this.timeOffset;
    return (new StringBuilder(169)).append("AdPodInfo [totalAds=").append(var1).append(", adPosition=").append(var2).append(", isBumper=").append(var3).append(", maxDuration=").append(var4).append(", podIndex=").append(var6).append(", timeOffset=").append(var7).append("]").toString();
  }
}
