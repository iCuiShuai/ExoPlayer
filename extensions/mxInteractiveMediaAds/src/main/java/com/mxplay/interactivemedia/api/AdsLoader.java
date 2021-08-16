
package com.mxplay.interactivemedia.api;


public interface AdsLoader {
    void contentComplete();

    void requestAds(AdsRequest var1);


    void addAdsLoadedListener(AdsLoader.AdsLoadedListener var1);

    void removeAdsLoadedListener(AdsLoader.AdsLoadedListener var1);

    void addAdErrorListener(AdErrorEvent.AdErrorListener var1);

    void removeAdErrorListener(AdErrorEvent.AdErrorListener var1);

    void release();


    public interface AdsLoadedListener {
        void onAdsManagerLoaded(AdsManagerLoadedEvent var1);
    }
}
