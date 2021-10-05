package com.mxplay.interactivemedia.internal.tracking;


import com.mxplay.interactivemedia.api.AdError;
import com.mxplay.interactivemedia.internal.api.FriendlyObstructionProvider;
import com.mxplay.interactivemedia.internal.data.model.AdVerification;

public interface IAdPixelTracker {
    void startSession(FriendlyObstructionProvider friendlyObstructionProvider);
    void finishSession();
    void videoBuffering(boolean isBuffering);
    void skippedAd();
    void clickAd();
    void onError(int errorCode, AdError.AdErrorType adErrorType);
    void start(float duration, float volume, float skipOffset);
    void firstQuartile();

    boolean hasSession();
    AdVerification getAdVerification();
    void midpoint();

    void thirdQuartile();

    void completed();

    void paused();

    void resumed();

    void loaded();

    void volumeChanged(float volume);
}
