
package com.mxplay.offlineads.exo.oma;

public class AdErrorEvent {

    private final AdError adError;
    private final Object userRequestContext;

    public AdErrorEvent(AdError adError, Object userRequestContext) {
        this.adError = adError;
        this.userRequestContext = userRequestContext;
    }

    public final AdError getError() {
        return this.adError;
    }

    public final Object getUserRequestContext() {
        return this.userRequestContext;
    }


    public interface AdErrorListener {
        void onAdError(AdErrorEvent var1);
    }
}
