
package com.mxplay.offlineads.exo.oma;

public interface VideoAdPlayer extends AdProgressProvider {
    void playAd();

    void loadAd(String var1);

    void stopAd();

    void pauseAd();

    /** @deprecated */
    @Deprecated
    void resumeAd();

    void addCallback(VideoAdPlayer.VideoAdPlayerCallback var1);

    void removeCallback(VideoAdPlayer.VideoAdPlayerCallback var1);

    public interface VideoAdPlayerCallback {
        void onPlay();

        void onPause();

        void onLoaded();

        void onResume();

        void onEnded();

        void onError();

        void onBuffering();
    }
}
