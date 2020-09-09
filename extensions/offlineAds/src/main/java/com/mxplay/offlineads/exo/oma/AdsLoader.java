//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.offlineads.exo.oma;


import com.mxplay.offlineads.exo.oma.internal.AdsRequest;

public interface AdsLoader {
    void contentComplete();

    void requestAds(AdsRequest var1);

    void addAdsLoadedListener(AdsLoader.AdsLoadedListener var1);

    void removeAdsLoadedListener(AdsLoader.AdsLoadedListener var1);

    void addAdErrorListener(AdErrorEvent.AdErrorListener var1);

    void removeAdErrorListener(AdErrorEvent.AdErrorListener var1);

    public interface AdsLoadedListener {
        void onAdsManagerLoaded(AdsManagerLoadedEvent var1);
    }
}
