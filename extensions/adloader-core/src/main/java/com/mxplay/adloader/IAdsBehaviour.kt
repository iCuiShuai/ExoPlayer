package com.mxplay.adloader

import android.net.Uri
import android.os.Handler
import androidx.annotation.CallSuper
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState


interface IAdsBehaviour {
    val trackerName: String?
    fun handleAudioAdLoaded(podIndex: Int, adPosition: Int): Boolean
    fun getMediaLoadTimeout(defaultTimout: Int): Int
    fun provideAdTagUri(actualUri: Uri?, listener: IAdTagProvider.Listener)
    fun setPipMode(isPip: Boolean)
    fun setContentDuration(contentDurationMs: Long)
    fun bind(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost, handler: Handler)
    fun onAllAdsRequested()
    fun doSetupAdsRendering(contentPositionMs: Long, contentDurationMs: Long): Boolean
    fun handleTimelineOrPositionChanged(player: Player?, timeline: Timeline?, period: Timeline.Period?): Boolean
    fun setPlayer(player: Player?)
    fun getContentPositionMs(player: Player, timeline: Timeline, period: Timeline.Period?, contentDurationMs: Long): Long
    fun onAdsManagerLoaded(groupCount: Int)
    fun onAdLoad(adGroupIndex: Int, adIndexInGroup: Int, adUri: Uri, adPodIndex: Int)
    fun onAdEvent(name: String?, creativeId: String?, advertiser: String?, adPodIndex: Int, adIndexInAdGroup: Int)
    fun trackEvent(eventName: String, adGroupIndex: Int, exception: Exception?)
    fun createAdPlaybackState(adId: Any?, adGroupTimesUs: LongArray): AdPlaybackState
}