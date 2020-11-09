package com.google.android.exoplayer2.ext.ima;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;

public class AdLoaderInputs {
    private float thresholdBetweenAdsOnSeek = 0.70f;
    private long adPlaybackDelayDuringScrubMs = C.TIME_UNSET;
    private @NonNull ImaCustomUiController imaCustomUiController =  new DefaultImaCustomUiController();

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
}
