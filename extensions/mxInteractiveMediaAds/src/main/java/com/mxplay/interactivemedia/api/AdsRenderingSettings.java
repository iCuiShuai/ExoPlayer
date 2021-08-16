
package com.mxplay.interactivemedia.api;

import java.util.List;

public class AdsRenderingSettings {

    private double playAdsAfterTime  = 0;
    private List<String> mimeTypes;
    private boolean enablePreloading = true;
    private int mediaLoadTimeoutMs;
    private int bitrateKbps;
    private boolean focusSkipButtonWhenAvailable;

    public void setMimeTypes(List<String> mimeTypes){
        this.mimeTypes = mimeTypes;
    }


    public void setPlayAdsAfterTime(double time){
        this.playAdsAfterTime = time;
    }

    public double getPlayAdsAfterTime() {
        return playAdsAfterTime;
    }

    //TODO may not required
    public void setEnablePreloading(boolean enablePreloading) {
        this.enablePreloading = enablePreloading;
    }

    public void setLoadVideoTimeout(int mediaLoadTimeoutMs) {
        this.mediaLoadTimeoutMs = mediaLoadTimeoutMs;
    }

    public void setBitrateKbps(int bitrateKbps) {
        this.bitrateKbps = bitrateKbps;
    }

    public void setFocusSkipButtonWhenAvailable(boolean focusSkipButtonWhenAvailable) {
        this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public int getMediaLoadTimeoutMs() {
        return mediaLoadTimeoutMs;
    }

    public int getBitrateKbps() {
        return bitrateKbps;
    }

    public boolean isFocusSkipButtonWhenAvailable() {
        return focusSkipButtonWhenAvailable;
    }
}
