package com.mxplay.adloader

import android.net.Uri
import android.os.Handler
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.mxplay.interactivemedia.api.AdErrorEvent
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.AdPodInfo


interface IAdsBehaviour : AdEvent.AdEventListener, com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener  {
    fun handleAudioAdLoaded(podIndex: Int, adPosition: Int): Boolean
    fun getMediaLoadTimeout(defaultTimout: Int): Int
    fun provideAdTagUri(actualUri: Uri?, listener: IAdTagProvider.Listener)
    fun setPipMode(isPip: Boolean)
    fun isPipModeActive() : Boolean
    fun setContentDuration(contentDurationMs: Long)
    fun bind(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost, handler: Handler)
    fun onAllAdsRequested()
    fun sendAdOpportunity() {}
    fun adShown() {}
    fun doSetupAdsRendering(contentPositionMs: Long, contentDurationMs: Long, playAdBeforeStartPosition: Boolean): Boolean
    fun onPositionDiscontinuity(player: Player?, timeline: Timeline?, period: Timeline.Period?): Boolean
    fun setPlayer(player: Player?)
    fun getContentPositionMs(player: Player, timeline: Timeline, period: Timeline.Period?, contentDurationMs: Long): Long
    fun onAdsManagerLoaded(cuePoints: List<Float>?)
    fun getAdGroupIndexForAdPod(podIndex: Int, podTimeOffset: Double, player: Player?, timeline: Timeline?, period: Timeline.Period?): Int
    fun onAdLoad(adGroupIndex: Int, adIndexInGroup: Int, adUri: Uri, adPodIndex: Int)
    fun createAdPlaybackState(adId: Any?, adGroupTimesUs: LongArray): AdPlaybackState
    fun obtainAdPlaybackStateHost() : AdsBehaviour.AdPlaybackStateHost?
    fun provideBehaviourTracker(): IBehaviourTracker
    fun registerAdEventListener(adEventListener: AdEvent.AdEventListener?)
    fun registerAdErrorEventListener(adErrorListener: AdErrorEvent.AdErrorListener?)
    fun handleTimelineOrPositionChanged(player: Player?, timeline: Timeline?, period: Timeline.Period?)
    fun setNativeCompanionAdInfo(adPodInfo: AdPodInfo?)
    fun onNativeCompanionLoaded(isLoaded: Boolean)
    fun onVideoSizeChanged(width: Int, height: Int)
    fun shouldSkipAd(adGroupIndex: Int, adIndexInGroup: Int): Boolean {
        return false
    }
    fun release(){

    }
}