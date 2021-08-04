package com.mxplay.adloader;

import com.google.android.exoplayer2.source.ads.AdPlaybackState;

public class AdsBehaviourDefault extends AdsBehaviour{

    public AdsBehaviourDefault() {
        super();
    }

    @Override
    public AdPlaybackState createAdPlaybackState(Object adId, long[] adGroupTimesUs) {
        return new AdPlaybackState(adId, adGroupTimesUs);
    }
}
