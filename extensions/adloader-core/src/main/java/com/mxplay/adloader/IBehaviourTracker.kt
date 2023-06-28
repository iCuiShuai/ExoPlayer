package com.mxplay.adloader

import android.net.Uri
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState

interface IBehaviourTracker: AdEvent.AdEventListener, AdErrorEvent.AdErrorListener {
    val videoAdsTracker: VideoAdsTracker
    object NO_OP_TRACKER : IBehaviourTracker {
        override val videoAdsTracker: VideoAdsTracker
            get() = VideoAdsTracker.getNoOpTracker()

        override fun doSetupAdsRendering(firstPlayingAdIndex: Int) {}

        override fun onAllAdsRequested() {}

        override fun sendAdOpportunity() {}

        override fun adShown() {}

        override fun onContentPositionChanged(
            player: Player,
            timeline: Timeline,
            period: Timeline.Period?,
            adGroupIndexProvider: (adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int
        ) {}

        override fun onAdsManagerLoaded(cuePoints: List<Float>?) {
        }

        override fun onAdLoad(
            adIndexInGroup: Int,
            adUri: Uri,
            adPodIndex: Int
        ) {
        }

        override fun onAdEvent(adEvent: AdEvent?) {
        }

        override fun onAdError(adErrorEvent: AdErrorEvent?) {
        }

        override fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost) {
        }

    }
    fun doSetupAdsRendering(firstPlayingAdIndex : Int)
    fun onAllAdsRequested()
    fun sendAdOpportunity()
    fun adShown()
    fun onContentPositionChanged(
        player: Player,
        timeline: Timeline,
        period: Timeline.Period?,
        adGroupIndexProvider:(adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int
    )

    fun onAdsManagerLoaded(cuePoints: List<Float>?)

    fun onAdLoad(adIndexInGroup: Int, adUri: Uri, adPodIndex: Int)

    fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost)
}