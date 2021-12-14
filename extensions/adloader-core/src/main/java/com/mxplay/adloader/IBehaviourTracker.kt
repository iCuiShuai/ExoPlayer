package com.mxplay.adloader

import android.net.Uri
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState

interface IBehaviourTracker: AdEvent.AdEventListener, AdErrorEvent.AdErrorListener {
    object NO_OP_TRACKER : IBehaviourTracker {
        override fun onAllAdsRequested() {}

        override fun onContentPositionPulled(
            player: Player,
            timeline: Timeline,
            period: Timeline.Period?,
            contentDurationMs: Long,
            adGroupIndexProvider: (adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int
        ) {}

        override fun onAdsManagerLoaded(groupCount: Int) {
        }

        override fun onAdLoad(
            adIndexInGroup: Int,
            adUri: Uri,
            adPodIndex: Int,
            realAdGroupIndexProvider: () -> Int
        ) {
        }

        override fun onAdEvent(adEvent: AdEvent?) {
        }

        override fun onAdError(adErrorEvent: AdErrorEvent?) {
        }

        override fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost) {
        }

    }

    fun onAllAdsRequested()
    fun onContentPositionPulled(
        player: Player,
        timeline: Timeline,
        period: Timeline.Period?,
        contentDurationMs: Long, adGroupIndexProvider:(adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int
    )

    fun onAdsManagerLoaded(groupCount: Int)

    fun onAdLoad(adIndexInGroup: Int, adUri: Uri, adPodIndex: Int, realAdGroupIndexProvider:() -> Int)

    fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost)
}