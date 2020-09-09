//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.offlineads.exo.oma;

public interface VideoAdPlayer extends AdProgressProvider, VolumeProvider {
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

        void onVolumeChanged(int var1);

        void onPause();

        void onLoaded();

        void onResume();

        void onEnded();

        void onError();

        void onBuffering();
    }
}
