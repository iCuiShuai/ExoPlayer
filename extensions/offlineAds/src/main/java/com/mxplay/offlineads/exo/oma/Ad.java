
package com.mxplay.offlineads.exo.oma;

import androidx.annotation.Nullable;

public interface Ad {
    String getAdId();
    @Nullable
    String getCreativeId();
    boolean isSkippable();
    double getSkipTimeOffset();
    String getDescription();
    String getTitle();
    @Nullable String getAdvertiserName();
    long getDuration();
    AdPodInfo getAdPodInfo();
    String getMediaUrl();
}
