package com.mxplay.offlineads.exo.oma;

import java.util.List;

public class AdGroup {
  private float startTime;
  private List<? extends Ad> ads;

  public AdGroup(float startTime, List<Ad> ads) {
    this.startTime = startTime;
    this.ads = ads;
  }

  public List<? extends Ad> getAds() {
    return ads;
  }

  public float getStartTime() {
    return startTime;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdGroup)) {
      return false;
    }

    AdGroup adGroup = (AdGroup) o;

    return Float.compare(adGroup.startTime, startTime) == 0;
  }

  @Override
  public int hashCode() {
    return (startTime != +0.0f ? Float.floatToIntBits(startTime) : 0);
  }
}
