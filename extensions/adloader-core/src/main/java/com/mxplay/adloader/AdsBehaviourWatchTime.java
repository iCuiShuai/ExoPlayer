package com.mxplay.adloader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Log;

import java.util.Objects;

public class AdsBehaviourWatchTime extends AdsBehaviourFakeCuepoints{

    private static final String TAG = "AdsBehaviourWatchTime";
    private final @NonNull PlaybackStatsListener playbackStatsListener;
    private @Nullable Player player;

    public AdsBehaviourWatchTime(long durationSec) {
        super(durationSec);
        playbackStatsListener = new PlaybackStatsListener(true, null);
    }

    public void setPlayer(Player player){
        if (this.player != null && this.player instanceof SimpleExoPlayer) {
            ((SimpleExoPlayer) this.player).removeAnalyticsListener(playbackStatsListener);
        }
        this.player = player;
        if (player instanceof SimpleExoPlayer) {
            ((SimpleExoPlayer) this.player).addAnalyticsListener(playbackStatsListener);
        }
    }

    @Override
    public boolean doSetupAdsRendering(long contentPositionMs, long contentDurationMs) {
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        int adGroupForPositionIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(
                        C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
        if (adGroupForPositionIndex != C.INDEX_UNSET && !hasPrerollAdGroups(adPlaybackState.adGroupTimesUs)) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupForPositionIndex);
            adPlaybackStateHost.updateAdPlaybackState(adPlaybackState);
            if (debug) {
                Log.d(TAG, "Init ad rendering settings contentPositionMs : " + contentPositionMs + " skipped ad index " + adGroupForPositionIndex);
            }
        }
        return true;
    }

    private static boolean hasPrerollAdGroups(long[] adGroupTimesUs) {
        int count = adGroupTimesUs.length;
        return count > 0 &&  adGroupTimesUs[0] == 0;
    }

    @Override
    public long getContentPositionMs(Player player, Timeline timeline, Timeline.Period period, long contentDurationMs) {
        super.getContentPositionMs(player, timeline, period, contentDurationMs);
        return Objects.requireNonNull(playbackStatsListener).getContentTotalPlayTimeMs();
    }
}
