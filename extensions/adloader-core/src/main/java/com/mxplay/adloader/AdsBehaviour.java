package com.mxplay.adloader;

import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mxplay.adloader.exo.MxAdPlaybackState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;

public abstract class AdsBehaviour {
    private static final String TAG = "AdsBehaviour";
    public static final long THRESHOLD_AD_MATCH_US = 1000;

    public abstract String getTrackerName();


    public interface AdPlaybackStateHost{
        AdPlaybackState getAdPlaybackState();
        void updateAdPlaybackState(AdPlaybackState adPlaybackState);
        @Nullable Pair<Integer, Integer> getPlayingAdInfo();
        default void onVastCallMaxWaitingTimeOver(){}
    }

    protected boolean debug = false;
    protected @NonNull AdPlaybackStateHost adPlaybackStateHost;
    protected long contentDurationMs = C.TIME_UNSET;
    private VideoAdsTracker videoAdsTracker;
    private int vastTimeOutInMs;
    private boolean isPipModeActive = false;
    private @Nullable IAdTagProvider adTagProvider;
    protected int audioAdPodIndex = C.INDEX_UNSET;
    protected int audioAdPosition = C.INDEX_UNSET;

    protected long startLoadMediaTime;
    protected long startRequestTime = 0;
    protected int lastRealStartTime = C.INDEX_UNSET;
    protected int lastPlayAdGroupIndex = C.INDEX_UNSET;
    protected int lastStartRequestAdGroupIndex = C.INDEX_UNSET;
    private Handler handler;

    private final BiMap<Integer, Integer> adGroupIndexOpportunitySet = HashBiMap.create();
    private final Map<String, Uri> adMediaUriByAdInfo = new HashMap<>();

    public AdsBehaviour(int vastTimeOutInMs) {
        this.vastTimeOutInMs = vastTimeOutInMs;
    }

    public final void setAdTagProvider(@Nullable IAdTagProvider adTagProvider) {
        this.adTagProvider = adTagProvider;
    }

    private @Nullable Map<String, String> getAdTagProviderExtraParams() {
        if(adTagProvider != null){
            @Nullable AdTagData adTagData = adTagProvider.getAdTagData();
            if(adTagData != null){
                return adTagData.toParams();
            }
        }
        return null;
    }

    public  void setHandler(Handler handler){
        this.handler = handler;
    }


    public boolean handleAudioAdLoaded(int podIndex, int adPosition) {
        if (isPipModeActive){
            discardAudioAd(podIndex, adPosition);
            return true;
        }else {
            audioAdPodIndex = podIndex;
            audioAdPosition = adPosition;
        }
        return false;
    }

    private void discardAudioAd(int podIndex, int adPosition) {
        try {
            AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
            AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[podIndex];
            if (adGroup.count == C.LENGTH_UNSET) {
                adPlaybackState = adPlaybackState.withAdCount(podIndex, max(1, adGroup.states.length));
                adGroup = adPlaybackState.adGroups[podIndex];
            }
            for (int i = 0; i < adGroup.count; i++) {
                if ((adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE || adGroup.states[i] == AdPlaybackState.AD_STATE_AVAILABLE)  && i == adPosition) {
                    if (debug) Log.d(TAG, "Removing audio ad " + i + " in ad group " + podIndex);
                    adPlaybackState = adPlaybackState.withAdLoadError(podIndex, i);
                    break;
                }
            }
            adPlaybackStateHost.updateAdPlaybackState(adPlaybackState);
        } catch (Exception e) {
            if (debug) e.fillInStackTrace();
        }
    }

    public int getMediaLoadTimeout(int defaultTimout){
        return defaultTimout;
    }
    public final void provideAdTagUri(Uri actualUri, @NonNull IAdTagProvider.Listener listener) {
        AdTagData adTagData = new AdTagData(actualUri,false, -1);
        if (adTagProvider != null){
            adTagProvider.registerTagListener(listener);
            if (adTagProvider.getAdTagData() != null) adTagData = adTagProvider.getAdTagData();
        }
        listener.onTagReceived(adTagData);
    }

    public final void setPipMode(boolean isPip) {
        isPipModeActive = isPip;
        if (isPipModeActive && adPlaybackStateHost != null){
            Pair<Integer, Integer> playingAdInfo = adPlaybackStateHost.getPlayingAdInfo();
            if (playingAdInfo == null) return;
            if (playingAdInfo.first == audioAdPodIndex && playingAdInfo.second == audioAdPosition){
                discardAudioAd(audioAdPodIndex, audioAdPosition);
            }
        }
    }

    public final boolean isPipModeActive() {
        return isPipModeActive;
    }

    public final void setContentDurationMs(long contentDurationMs) {
        this.contentDurationMs = contentDurationMs;
    }

    public final void setAdPlaybackStateHost(@NonNull AdPlaybackStateHost adPlaybackStateHost){
        this.adPlaybackStateHost = adPlaybackStateHost;
    }

    public final void setVideoAdsTracker(VideoAdsTracker videoAdsTracker) {
        this.videoAdsTracker = videoAdsTracker;
    }

    private final Runnable vastCallWaitingRunnable = new Runnable() {
        @Override
        public void run() {
            if (adPlaybackStateHost != null){
                adPlaybackStateHost.onVastCallMaxWaitingTimeOver();
            }
        }
    };

    public final void onAllAdsRequested(){
        videoAdsTracker.onAdManagerRequested(getAdTagProviderExtraParams());
        startRequestTime = System.currentTimeMillis();
        long vastCallMaxWaitingTime = vastTimeOutInMs > 0 ? vastTimeOutInMs + 1000 : 6000;
        if (handler != null){
            handler.postDelayed(vastCallWaitingRunnable, vastCallMaxWaitingTime);
        }
    }

    public abstract AdPlaybackState createAdPlaybackState(Object objectId, long[] adGroupTimesUs);

    public boolean doSetupAdsRendering(long contentPositionMs, long contentDurationMs){
        return false;
    }

    public void handleTimelineOrPositionChanged(Player player, Timeline timeline, Timeline.Period period){

    }

    public final void setDebug(boolean debug) {
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

    public long getContentPositionMs(Player player, Timeline timeline, Timeline.Period period, long contentDurationMs){
        long contentPositionMs = getContentPeriodPositionMs(player, timeline, period);
        tryUpdateStartRequestTime(contentPositionMs, contentDurationMs);
        return contentPositionMs;
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

    public final void onAdsManagerLoaded(int groupCount) {
        videoAdsTracker.onAdsManagerLoaded(groupCount);
    }

    public final void onAdLoad(int adGroupIndex, int adIndexInGroup, Uri adUri, int adPodIndex) {
        handleAdLoad(adGroupIndex, adIndexInGroup);
        int actualAdGroupIndex = getActualAdGroupIndex(adGroupIndex) != C.INDEX_UNSET ? getActualAdGroupIndex(adGroupIndex) : adGroupIndex;
        videoAdsTracker.onAdLoad(actualAdGroupIndex, adIndexInGroup, adUri);
        adMediaUriByAdInfo.put(getKeyForAdInfo(actualAdGroupIndex, adIndexInGroup), adUri);
        if (!adGroupIndexOpportunitySet.containsKey(actualAdGroupIndex)) {
            trySendOpportunity(actualAdGroupIndex);
        }
        adGroupIndexOpportunitySet.put(actualAdGroupIndex, adPodIndex);
        updateStartLoadMediaTime();
    }

    protected void handleAdLoad(int adGroupIndex, int adIndexInAdGroup) {
    }

    protected int getActualAdGroupIndex(int fakeAdGroupIndex) {
        return fakeAdGroupIndex;
    }

    public final void onAdEvent(String name, @Nullable String creativeId, @Nullable String advertiser, int adPodIndex, int adIndexInAdGroup) {
        Integer adGroupIndex = adGroupIndexOpportunitySet.inverse().get(adPodIndex);
        Uri adUri = adMediaUriByAdInfo.get(getKeyForAdInfo(adGroupIndex, adIndexInAdGroup));
        videoAdsTracker.trackEvent(name, videoAdsTracker.buildEventParams(creativeId, advertiser, adGroupIndex, adIndexInAdGroup, adUri));
    }

    public final void trackEvent(@NonNull String eventName, int adGroupIndex, Exception exception) {
        trackEvent(eventName, adGroupIndex, -1, exception);
    }

    public final void trackEvent(@NonNull String eventName, int adGroupIndex, int adIndexInAdGroup, Exception exception) {
        int actualAdGroupIndex = getActualAdGroupIndex(adGroupIndex) != C.INDEX_UNSET ? getActualAdGroupIndex(adGroupIndex) : adGroupIndex;
        trySendOpportunity(actualAdGroupIndex);
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        int adGroupCount = adPlaybackState.adGroupCount;
        if (adPlaybackState instanceof MxAdPlaybackState) {
            adGroupCount = ((MxAdPlaybackState) adPlaybackState).actualAdGroupCount;
        }
        Uri mediaUri = adMediaUriByAdInfo.get(getKeyForAdInfo(actualAdGroupIndex, adIndexInAdGroup));
        switch (eventName) {
            case VideoAdsTracker.EVENT_VIDEO_AD_PLAY_FAILED:
                videoAdsTracker.trackEvent(eventName, videoAdsTracker.buildFailedParams(actualAdGroupIndex, adIndexInAdGroup,
                        startRequestTime, exception, adGroupCount, mediaUri));
                break;
            case VideoAdsTracker.EVENT_VIDEO_AD_PLAY_SUCCESS:
                if (lastPlayAdGroupIndex != actualAdGroupIndex) {
                    lastPlayAdGroupIndex = actualAdGroupIndex;
                    videoAdsTracker.trackEvent(eventName, videoAdsTracker.buildSuccessParams(startLoadMediaTime, startRequestTime,
                            startLoadMediaTime, lastPlayAdGroupIndex, adIndexInAdGroup, adGroupCount, mediaUri));
                }
                break;
        }
    }

    private void updateStartLoadMediaTime() {
        startLoadMediaTime = System.currentTimeMillis();
    }

    protected void tryUpdateStartRequestTime(long contentPositionMs, long contentDurationMs) {
        if (contentPositionMs < 0 || contentDurationMs < 0) {
            return;
        }
        AdPlaybackState adPlaybackState = adPlaybackStateHost.getAdPlaybackState();
        int adGroupIndex = getLoadingAdGroupIndexForReporting(adPlaybackState, C.msToUs(contentPositionMs));
        if (adGroupIndex == C.INDEX_UNSET) return;
        long contentPosition = TimeUnit.MILLISECONDS.toSeconds(contentPositionMs);
        int realStartTime;
        long startTime = TimeUnit.MICROSECONDS.toSeconds(adPlaybackState.adGroupTimesUs[adGroupIndex]);
        if (adPlaybackState instanceof MxAdPlaybackState) {
            startTime = TimeUnit.MICROSECONDS.toSeconds(((MxAdPlaybackState) adPlaybackState).actualAdGroupTimeUs[adGroupIndex]);
        }
        if (startTime == -1.0) {
            realStartTime = (int) (contentPosition - 8);
        } else {
            realStartTime = (int) (startTime == 0 ? startTime : startTime - 8);
        }
        if (realStartTime == contentPosition && lastRealStartTime != realStartTime) {
            lastRealStartTime = realStartTime;
            updateStartRequestTime(adGroupIndex);
        }
    }

    private void updateStartRequestTime(int adGroupIndex) {
        if (lastStartRequestAdGroupIndex != adGroupIndex && (adGroupIndex != C.INDEX_UNSET)) {
            lastStartRequestAdGroupIndex = adGroupIndex;
            startRequestTime = System.currentTimeMillis();
            trySendOpportunity(adGroupIndex);
        }
    }

    protected int getLoadingAdGroupIndexForReporting(AdPlaybackState adPlaybackState, long playerPositionUs){
        return getLoadingAdGroupIndex(adPlaybackState, playerPositionUs);
    }
    
    /**
     * Returns the index of the ad group that will preload next, or {@link C#INDEX_UNSET} if there is
     * no such ad group.
     */
    protected final int getLoadingAdGroupIndex(AdPlaybackState adPlaybackState, long playerPositionUs) {
        int adGroupIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs));
        if (adGroupIndex == C.INDEX_UNSET) {
            adGroupIndex =
                    adPlaybackState.getAdGroupIndexAfterPositionUs(
                            playerPositionUs, C.msToUs(contentDurationMs));
        }
        return adGroupIndex;
    }

    private void trySendOpportunity(int adGroupIndex) {
        if (!adGroupIndexOpportunitySet.containsKey(adGroupIndex)) {
            adGroupIndexOpportunitySet.put(adGroupIndex, -adGroupIndex - 1);
            videoAdsTracker.onAdOpportunity(adGroupIndex);
        }
    }

    private String getKeyForAdInfo(int adGroupIndex, int adIndexInAdGroup) {
        return adGroupIndex + "_" + adIndexInAdGroup;
    }
   

}
