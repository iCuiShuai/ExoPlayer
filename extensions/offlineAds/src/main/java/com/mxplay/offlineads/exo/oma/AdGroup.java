package com.mxplay.offlineads.exo.oma;

import androidx.annotation.NonNull;
import java.util.List;

public class AdGroup {
  private float startTime;
  private List<AdImpl> ads;

  public AdGroup(float startTime, @NonNull List<AdImpl> ads) {
    this.startTime = startTime;
    this.ads = ads;
  }

  public List<AdImpl> getAds() {
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
