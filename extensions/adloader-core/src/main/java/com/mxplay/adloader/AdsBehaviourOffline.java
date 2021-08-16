package com.mxplay.adloader;

public class AdsBehaviourOffline extends AdsBehaviourDefault {
    public AdsBehaviourOffline(int vastTimeOutInMs) {
        super(vastTimeOutInMs);
    }

    @Override
    public String getTrackerName() {
        return VideoAdsTracker.OFFLINE_AD_LOADER;
    }
}
