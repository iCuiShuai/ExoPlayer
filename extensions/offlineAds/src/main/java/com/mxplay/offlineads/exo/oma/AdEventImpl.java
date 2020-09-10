package com.mxplay.offlineads.exo.oma;

import java.util.Map;
import java.util.Objects;

public class AdEventImpl implements AdEvent{

  private AdEvent.AdEventType adEventType;
  private Ad ad;
  private Map<String, String> adData;

  public AdEventImpl(AdEvent.AdEventType adEventType, Ad ad, Map<String, String> adData) {
    this.adEventType = adEventType;
    this.ad = ad;
    this.adData = adData;
  }

  public final AdEvent.AdEventType getType() {
    return this.adEventType;
  }

  public final Ad getAd() {
    return this.ad;
  }

  public final Map<String, String> getAdData() {
    return this.adData;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdEventImpl)) {
      return false;
    }

    AdEventImpl adEvent = (AdEventImpl) o;

    if (adEventType != adEvent.adEventType) {
      return false;
    }
    return ad.equals(adEvent.ad);
  }

  @Override
  public int hashCode() {
    int result = adEventType.hashCode();
    result = 31 * result + ad.hashCode();
    return result;
  }
}
