package com.mxplay.interactivemedia.internal.core;

import androidx.annotation.Nullable;
import com.mxplay.interactivemedia.api.Ad;
import com.mxplay.interactivemedia.api.AdEvent;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class AdEventImpl implements AdEvent {

  private final AdEvent.AdEventType adEventType;
  private final @Nullable
  Ad ad;
  private final Map<String, String> adData;

  public AdEventImpl(AdEvent.AdEventType adEventType, @Nullable Ad ad, Map<String, String> adData) {
    this.adEventType = adEventType;
    this.ad = ad;
    this.adData = adData;
  }

  @NotNull
  public final AdEvent.AdEventType getType() {
    return this.adEventType;
  }

  @Nullable
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
    return ad != null && ad.equals(adEvent.ad);
  }

  @Override
  public int hashCode() {
    int result = adEventType.hashCode();
    result = 31 * result + (ad != null ? ad.hashCode() : 0);
    return result;
  }
}
