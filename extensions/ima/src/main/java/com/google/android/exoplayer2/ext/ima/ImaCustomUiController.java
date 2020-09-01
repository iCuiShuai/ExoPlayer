package com.google.android.exoplayer2.ext.ima;

import androidx.annotation.Nullable;

import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsManager;

public interface ImaCustomUiController {
    void onAdEvent(AdEvent adEvent);

    void setAdsManager(@Nullable AdsManager adsManager);

    boolean isCustomUiSupported();

    void releaseAdManager();
}
