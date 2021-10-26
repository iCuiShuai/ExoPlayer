package com.mxplay.adloader

import android.net.Uri
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState

interface IBehaviourTracker {
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

        override fun trackEvent(
            eventName: String,
            adGroupIndex: Int,
            adIndexInAdGroup: Int,
            exception: Exception?
        ) {
        }

        override fun onAdLoad(
            adIndexInGroup: Int,
            adUri: Uri,
            adPodIndex: Int,
            realAdGroupIndexProvider: () -> Int
        ) {
        }

        override fun onAdEvent(
            name: String?,
            creativeId: String?,
            advertiser: String?,
            adPodIndex: Int,
            adIndexInAdGroup: Int
        ) {
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
    fun trackEvent(
        eventName: String,
        adGroupIndex: Int,
        adIndexInAdGroup: Int,
        exception: Exception?
    )

    fun onAdLoad(adIndexInGroup: Int, adUri: Uri, adPodIndex: Int, realAdGroupIndexProvider:() -> Int)
    fun onAdEvent(
        name: String?,
        creativeId: String?,
        advertiser: String?,
        adPodIndex: Int,
        adIndexInAdGroup: Int
    )

    fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost)
}