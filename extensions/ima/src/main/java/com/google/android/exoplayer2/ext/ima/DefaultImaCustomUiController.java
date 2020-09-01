package com.google.android.exoplayer2.ext.ima;

import androidx.annotation.Nullable;

import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsManager;

public class DefaultImaCustomUiController implements ImaCustomUiController {
    @Override
    public void onAdEvent(AdEvent adEvent) {

    }

    @Override
    public void setAdsManager(@Nullable AdsManager adsManager) {

    }

    @Override
    public boolean isCustomUiSupported() {
        return false;
    }

    @Override
    public void releaseAdManager() {

    }
}
