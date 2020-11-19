package com.google.android.exoplayer2.ext.ima;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;

public class AdLoaderInputs {
    /** Represents a boundry between two ads if user seek before this ad will be delayed otherwise it will be skipped */
    private float thresholdBetweenAdsOnSeek = 0.70f;
    /** Ad delay time when user seek */
    private long adPlaybackDelayDuringScrubMs = C.TIME_UNSET;
    private @NonNull ImaCustomUiController imaCustomUiController =  new DefaultImaCustomUiController();
    /** enable this flag will send fake progress to IMA for preloading ads in advance. By default IMA load ads 8 sec before reaching ad cuepoint **/
    private long adPreloadThresholdMs = C.TIME_UNSET;
    private @Nullable IAdsIntercept adsIntercept;
    private @Nullable IVideoAdTracker adTracker;

    public float getThresholdBetweenAdsOnSeek() {
        return thresholdBetweenAdsOnSeek;
    }

    public void setThresholdBetweenAdsOnSeek(float thresholdBetweenAdsOnSeek) {
        this.thresholdBetweenAdsOnSeek = thresholdBetweenAdsOnSeek;
    }

    public long getAdPlaybackDelayDuringScrubMs() {
        return adPlaybackDelayDuringScrubMs;
    }

    public void setAdPlaybackDelayDuringScrubMs(long adPlaybackDelayDuringScrubMs) {
        this.adPlaybackDelayDuringScrubMs = adPlaybackDelayDuringScrubMs;
    }

    @NonNull
    public ImaCustomUiController getImaCustomUiController() {
        return imaCustomUiController;
    }

    public void setImaCustomUiController(@NonNull ImaCustomUiController imaCustomUiController) {
        this.imaCustomUiController = imaCustomUiController;
    }

    public boolean isAdDelayOnScrubbingEnabled(long mediaLoadTimeOutInMs){
        return adPlaybackDelayDuringScrubMs != C.TIME_UNSET && mediaLoadTimeOutInMs != C.TIME_UNSET;
    }

    public long getAdPreloadFakeProgressThresholdMs() {
        return adPreloadThresholdMs;
    }

    public void setAdPreloadFakeProgressThresholdMs(long adPreloadThresholdMs) {
        this.adPreloadThresholdMs = adPreloadThresholdMs;
    }

    @Nullable
    public IAdsIntercept getAdsIntercept() {
        return adsIntercept;
    }

    public void setAdsIntercept(@Nullable IAdsIntercept adsIntercept) {
        this.adsIntercept = adsIntercept;
    }

    @Nullable
    public IVideoAdTracker getAdTracker() {
        return adTracker;
    }

    public void setAdTracker(@Nullable IVideoAdTracker adTracker) {
        this.adTracker = adTracker;
    }
}
