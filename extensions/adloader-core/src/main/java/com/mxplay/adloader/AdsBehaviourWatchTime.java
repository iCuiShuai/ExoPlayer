package com.mxplay.adloader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.mxplay.adloader.exo.MxAdPlaybackState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AdsBehaviourWatchTime extends AdsBehaviourDefault{

    private static final String TAG = "AdsBehaviourWatchTime";
    private final @NonNull PlaybackStatsListener playbackStatsListener;
    private @Nullable Player player;

    private static final int FAKE_CUEPOINTS_DISTANCE = 10; // in secs
    public static final int NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD = 8000; // 8 sec
    private final long contentDurationMs;
    private int totalAdLoads;

    public AdsBehaviourWatchTime(long durationSec, int vastTimeOutInMs) {
        super(vastTimeOutInMs);
        this.contentDurationMs =  Math.round(C.MICROS_PER_SECOND * durationSec);;
        playbackStatsListener = new PlaybackStatsListener(true, null);
    }

    @Override
    public String getTrackerName() {
        return VideoAdsTracker.WATCH_TIME_BASE_AD_LOADER;
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

    @Override
    public boolean handleAudioAdLoaded(int podIndex, int adPosition) {
        audioAdPodIndex = podIndex;
        audioAdPosition = adPosition;
        return true;
    }

    private static boolean hasPrerollAdGroups(long[] adGroupTimesUs) {
        int count = adGroupTimesUs.length;
        return count > 0 &&  adGroupTimesUs[0] == 0;
    }

    @Override
    public long getContentPositionMs(Player player, Timeline timeline, Timeline.Period period, long contentDurationMs) {
        super.getContentPositionMs(player, timeline, period, contentDurationMs);
        boolean hasContentDuration = contentDurationMs != C.INDEX_UNSET;
        if (hasContentDuration) {
            cleanUnusedCuePoints(player, timeline, period);
        }
        return Objects.requireNonNull(playbackStatsListener).getContentTotalPlayTimeMs();
    }





    @Override
    public int getMediaLoadTimeout(int defaultTimout) {
        return 2 * NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD;
    }

    private long[] generateFakeAdGroupsTimesUs(long[] adGroupTimesUs) {
        List<Long> fakeCuePoints = new ArrayList<>();
        long distance = Math.round(C.MICROS_PER_SECOND * FAKE_CUEPOINTS_DISTANCE);
        long start = distance; // 10
        for (int i = 0; i < contentDurationMs / distance + 1; i++) {  // 3 < 2
            fakeCuePoints.add(start);
            start += distance;
        }
        for (float cuePoint : adGroupTimesUs) {
            if (cuePoint == C.TIME_END_OF_SOURCE) {
                fakeCuePoints.add(C.TIME_END_OF_SOURCE);
            } else if (cuePoint == 0) {
                fakeCuePoints.add(0, 0L);
            }
        }
        long[] fakeAdGroupTimeUs = new long[fakeCuePoints.size()];
        for (int j=0; j<fakeCuePoints.size(); j++) {
            fakeAdGroupTimeUs[j] = fakeCuePoints.get(j);
        }
        return fakeAdGroupTimeUs;
    }



    @Override
    public AdPlaybackState createAdPlaybackState(Object adId, long[] adGroupTimesUs) {
        long[] fakeAdGroupTimesUs = generateFakeAdGroupsTimesUs(adGroupTimesUs);
        return new MxAdPlaybackState(adId, fakeAdGroupTimesUs, adGroupTimesUs);
    }




    @Override
    public void handleTimelineOrPositionChanged(Player player, Timeline timeline, Timeline.Period period) {
        super.handleTimelineOrPositionChanged(player, timeline, period);
        skipAdOnUserSeek(player, timeline, period);
    }

    private void skipAdOnUserSeek(Player player, Timeline timeline, Timeline.Period period) {
        if (!timeline.isEmpty()) {
            AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
            boolean updateState = false;
            long positionMs = getContentPeriodPositionMs(player, timeline, period);
            timeline.getPeriod(/* periodIndex= */ 0, period);
            int newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs));
            if (newAdGroupIndex != C.INDEX_UNSET && adPlaybackState.adGroups[newAdGroupIndex].count < 0) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(newAdGroupIndex);
                updateState = true;
                if (debug) {
                    Log.d(TAG, "Ad skipped on user seek onTimelineChanged/onPositionDiscontinuity " + newAdGroupIndex);
                }
            }
            if (updateState) adPlaybackStateHost.updateAdPlaybackState(adPlaybackState);
        }
    }



    private void cleanUnusedCuePoints(Player player, Timeline timeline, Timeline.Period period) {
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        long positionUs =
                C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
        boolean shouldUpdatePlaybackState = false;
        while (true){
            int adGroupIndexAfterPositionUs = adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, C.msToUs(contentDurationMs));
            if (adGroupIndexAfterPositionUs != C.INDEX_UNSET && adPlaybackState.adGroups[adGroupIndexAfterPositionUs].count < 0) {
                long nextAdTimeOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs] - positionUs);
                if (nextAdTimeOffsetMs < NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD) {
                    adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndexAfterPositionUs);
                    shouldUpdatePlaybackState = true;
                    if (debug) Log.d(TAG, " Skipped fake cuepoint " + adGroupIndexAfterPositionUs + " -- pos: " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs]));
                }else
                    break;
            } else break;

        }
        if (shouldUpdatePlaybackState) adPlaybackStateHost.updateAdPlaybackState(adPlaybackState);
    }

    @Override
    public int getAdGroupIndexForAdPod(int podIndex, double podTimeOffset, Player player, Timeline timeline, Timeline.Period period) {
        totalAdLoads++;
        boolean isAudioAd = podIndex == audioAdPodIndex;
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        boolean allAdsDone =  (((MxAdPlaybackState)adPlaybackState).actualAdGroupCount == totalAdLoads);
        if (podIndex == -1) {
            // This is a postroll ad.
            if (allAdsDone) skipAllFakeCuePoints(adPlaybackState, adPlaybackState.adGroupCount - 1);
            if (isAudioAd)  throw new IllegalStateException("Audio ad not supported in watch-time");
            return adPlaybackState.adGroupCount - 1;
        }

        if (isAudioAd)  throw new IllegalStateException("Audio ad not supported in watch-time");
        long positionUs =
                C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
        if (debug) {
            Log.d(TAG, " Player position " + C.usToMs(positionUs));
        }
        int adGroupIndex = getFakeCuepointForLoadingAd(positionUs, adPlaybackState);
        if (allAdsDone) skipAllFakeCuePoints(adPlaybackState, adGroupIndex);
        if (adGroupIndex != C.INDEX_UNSET) return adGroupIndex;

        throw new IllegalStateException("Failed to find cue point");
    }



    private int getFakeCuepointForLoadingAd(long positionUs, AdPlaybackState adPlaybackState) {
        int adGroupIndex = getLoadingAdGroupIndex(adPlaybackState, positionUs);
        if (adGroupIndex != C.INDEX_UNSET) {
            long timeLeftMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex] - positionUs);
            if (timeLeftMs > 0 && timeLeftMs < NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD) {
                if (debug) {
                    Log.d(TAG, "Next cue-point too close  " + adGroupIndex + " time : " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]) + " time left : " + timeLeftMs);
                }
                adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex);
                return getFakeCuepointForLoadingAd(positionUs, adPlaybackState);
            }
            if (debug) {
                Log.d(TAG, " Loading  Next Ad  " + adGroupIndex + " time : " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]) + " time left : " + timeLeftMs);
            }
            return adGroupIndex;
        }
        return C.INDEX_UNSET;
    }





    private void skipAllFakeCuePoints(AdPlaybackState adPlaybackState, int keepAdGroupIndex) {
        // skip all ads
        try {
            for (int i = 0; i < adPlaybackState.adGroupTimesUs.length; i++) {
                if (keepAdGroupIndex == i) continue;
                adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
            }
            adPlaybackStateHost.updateAdPlaybackState(adPlaybackState);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (debug) {
            Log.d(TAG, " skipped all fake cue points ");
        }
    }


    @Override
    protected int getLoadingAdGroupIndexForReporting(AdPlaybackState adPlaybackState, long playerPositionUs) {
        MxAdPlaybackState mxAdPlaybackState = (MxAdPlaybackState)adPlaybackState;
        int adGroupIndex =
                mxAdPlaybackState.getActualAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs));
        if (adGroupIndex == C.INDEX_UNSET) {
            adGroupIndex =
                    mxAdPlaybackState.getActualAdGroupIndexAfterPositionUs(
                            playerPositionUs, C.msToUs(contentDurationMs));
        }
        return adGroupIndex;
    }
}
