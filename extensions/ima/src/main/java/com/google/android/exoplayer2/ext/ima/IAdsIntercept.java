package com.google.android.exoplayer2.ext.ima;

import android.view.ViewGroup;

public interface IAdsIntercept {
    boolean skipAd(int adGroupCounts, int adGroupIndex);

    boolean handleAdError(ImaAdsLoader imaAdsLoader, ViewGroup adContainer, Object adError, int adGroupIndex, long adPositionInSec);

    boolean isPlayingAd();

    boolean handlePlayerError(Throwable throwable);

    void release();
}
