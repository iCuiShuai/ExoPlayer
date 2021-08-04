
package com.mxplay.interactivemedia.api.player;

import com.mxplay.interactivemedia.api.AdPodInfo;

public interface VideoAdPlayer extends AdProgressProvider, VolumeProvider {
    void loadAd(AdMediaInfo var1, AdPodInfo var2);

    void playAd(AdMediaInfo var1);

    void pauseAd(AdMediaInfo var1);

    void stopAd(AdMediaInfo var1);

    void release();

    void addCallback(VideoAdPlayer.VideoAdPlayerCallback var1);

    void removeCallback(VideoAdPlayer.VideoAdPlayerCallback var1);

    public interface VideoAdPlayerCallback {
        void onPlay(AdMediaInfo adMediaInfo);

        void onPause(AdMediaInfo adMediaInfo);

        void onLoaded(AdMediaInfo adMediaInfo);

        void onResume(AdMediaInfo adMediaInfo);

        void onEnded(AdMediaInfo adMediaInfo);

        void onError(AdMediaInfo adMediaInfo);

        void onBuffering(AdMediaInfo var1);

        void onContentComplete();

        void onAdProgress(AdMediaInfo adMediaInfo, VideoProgressUpdate videoProgressUpdate);
    }
}
