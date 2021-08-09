package com.mxplay.adloader;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.mxplay.adloader.exo.MxAdPlaybackState;

import java.util.ArrayList;
import java.util.List;

public class AdsBehaviourFakeCuepoints extends AdsBehaviourDefault {
    private static final String TAG = "AdsBehaviourFakeCuepoints";

    private static final int FAKE_CUEPOINTS_DISTANCE = 10; // in secs
    public static final int NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD = 8000; // 8 sec
    public static final int NEXT_AD_DISTANCE_THRESHOLD = 3000; // 3 sec
    private final long contentDurationMs;


    public AdsBehaviourFakeCuepoints(long contentDurationSec) {
        super();
        this.contentDurationMs =  Math.round(C.MICROS_PER_SECOND * contentDurationSec);;
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
        return new MxAdPlaybackState(adId, adGroupTimesUs, fakeAdGroupTimesUs);
    }

    @Override
    public boolean doSetupAdsRendering(long contentPositionMs, long contentDurationMs) {
        return super.doSetupAdsRendering(contentPositionMs, contentDurationMs);

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
            int newFakeAdGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(positionMs), C.msToUs(contentDurationMs));
            if (newFakeAdGroupIndex != C.INDEX_UNSET &&  newFakeAdGroupIndex > newAdGroupIndex && adPlaybackState.adGroups[newFakeAdGroupIndex].count < 0) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(newFakeAdGroupIndex);
                updateState = true;
                if (debug) {
                    Log.d(TAG, "Re-enabled cuepoint Ad skipped on user seek onTimelineChanged/onPositionDiscontinuity " + newFakeAdGroupIndex);
                }
            }

            if (updateState) adPlaybackStateHost.updateAdPlaybackState(adPlaybackState);
        }
    }

    @Override
    public long getContentPositionMs(Player player, Timeline timeline, Timeline.Period period) {
        boolean hasContentDuration = contentDurationMs != C.INDEX_UNSET;
        if (hasContentDuration) {
            AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
            long positionUs =
                    C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
            int adGroupIndexAfterPositionUs = adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, C.msToUs(contentDurationMs));
            if (adGroupIndexAfterPositionUs != C.INDEX_UNSET && adPlaybackState.adGroups[adGroupIndexAfterPositionUs].count < 0) {
                long nextAdTimeOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs] - positionUs);
                if (nextAdTimeOffsetMs < NEXT_AD_DISTANCE_THRESHOLD) {
                    adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndexAfterPositionUs);
                    adPlaybackStateHost.updateAdPlaybackState(adPlaybackState);
                    if (debug)
                        Log.d(TAG, " Skipped fake cuepoint " + adGroupIndexAfterPositionUs + " -- pos: " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs]));
                }
            }

        }
        return super.getContentPositionMs(player, timeline, period);

    }

    @Override
    public int getAdGroupIndexForAdPod(int podIndex, double podTimeOffset, Player player, Timeline timeline, Timeline.Period period) {
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        if (podIndex == -1) {
            // This is a postroll ad.
            return adPlaybackState.adGroupCount - 1;
        }

        long positionUs =
                C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
        if (debug) {
            Log.d(TAG, " Player position " + C.usToMs(positionUs));
        }
        int adGroupIndex = getFakeCuepointForLoadingAd(positionUs, adPlaybackState);
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




    /**
     * Returns the index of the ad group that will preload next, or {@link C#INDEX_UNSET} if there is
     * no such ad group.
     */
    private int getLoadingAdGroupIndex(AdPlaybackState adPlaybackState, long playerPositionUs) {
        int adGroupIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs));
        if (adGroupIndex == C.INDEX_UNSET) {
            adGroupIndex =
                    adPlaybackState.getAdGroupIndexAfterPositionUs(
                            playerPositionUs, C.msToUs(contentDurationMs));
        }
        return adGroupIndex;
    }


}
