
package com.mxplay.interactivemedia.api;

public class AdsManagerLoadedEvent {

    private final AdsManager adsManager;
    private final Object userRequestContext;

    public AdsManagerLoadedEvent(AdsManager adsManager, Object userRequestContext) {
        this.adsManager = adsManager;
        this.userRequestContext = userRequestContext;
    }

    public final AdsManager getAdsManager() {
        return this.adsManager;
    }

    public final Object getUserRequestContext() {
        return this.userRequestContext;
    }


}
