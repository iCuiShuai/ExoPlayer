package com.mxplay.adloader;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;

public abstract class AdsBehaviour {

    public static final long THRESHOLD_AD_MATCH_US = 1000;


    public interface AdPlaybackStateHost{
        AdPlaybackState getAdPlaybackState();
        void updateAdPlaybackState(AdPlaybackState adPlaybackState);
    }

    protected boolean debug = false;
    protected @NonNull AdPlaybackStateHost adPlaybackStateHost;
    protected long contentDurationMs = C.TIME_UNSET;


    public AdsBehaviour() {
    }

    public void setContentDurationMs(long contentDurationMs) {
        this.contentDurationMs = contentDurationMs;
    }

    public void setAdPlaybackStateHost(@NonNull AdPlaybackStateHost adPlaybackStateHost){
        this.adPlaybackStateHost = adPlaybackStateHost;
    }

    public abstract AdPlaybackState createAdPlaybackState(Object objectId, long[] adGroupTimesUs);

    public boolean doSetupAdsRendering(long contentPositionMs, long contentDurationMs){
        return false;
    }

    public void handleTimelineOrPositionChanged(Player player, Timeline timeline, Timeline.Period period){

    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setPlayer(Player player){

    }

    protected static long getContentPeriodPositionMs(
            Player player, Timeline timeline, Timeline.Period period) {
        long contentWindowPositionMs = player.getContentPosition();
        if (timeline.isEmpty()) {
            return contentWindowPositionMs;
        } else {
            return contentWindowPositionMs
                    - timeline.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs();
        }
    }

    public long getContentPositionMs(Player player, Timeline timeline, Timeline.Period period){
            return getContentPeriodPositionMs(player, timeline, period);
    }

    public int getAdGroupIndexForAdPod(int podIndex, double podTimeOffset, Player player, Timeline timeline, Timeline.Period period) {
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        if (podIndex == -1) {
            // This is a postroll ad.
            return adPlaybackState.adGroupCount - 1;
        }

        // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
        return getAdGroupIndexForCuePointTimeSeconds(podTimeOffset);
    }


    private int getAdGroupIndexForCuePointTimeSeconds(double cuePointTimeSeconds) {
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        // We receive initial cue points from IMA SDK as floats. This code replicates the same
        // calculation used to populate adGroupTimesUs (having truncated input back to float, to avoid
        // failures if the behavior of the IMA SDK changes to provide greater precision).
        float cuePointTimeSecondsFloat = (float) cuePointTimeSeconds;
        long adPodTimeUs = Math.round((double) cuePointTimeSecondsFloat * C.MICROS_PER_SECOND);
        for (int adGroupIndex = 0; adGroupIndex < adPlaybackState.adGroupCount; adGroupIndex++) {
            long adGroupTimeUs = adPlaybackState.adGroupTimesUs[adGroupIndex];
            if (adGroupTimeUs != C.TIME_END_OF_SOURCE
                    && Math.abs(adGroupTimeUs - adPodTimeUs) < THRESHOLD_AD_MATCH_US) {
                return adGroupIndex;
            }
        }
        throw new IllegalStateException("Failed to find cue point");
    }


}
