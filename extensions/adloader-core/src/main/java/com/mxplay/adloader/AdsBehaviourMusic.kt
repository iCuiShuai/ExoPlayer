package com.mxplay.adloader

import android.net.Uri
import android.os.Handler
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline

class AdsBehaviourMusic(private val adsBehaviour: AdsBehaviour, private val mxTrackingBehaviour: IBehaviourTracker) : IAdsBehaviour by adsBehaviour {

    override fun bind(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost, handler: Handler) {
        adsBehaviour.bind(adPlaybackStateHost, handler)
        mxTrackingBehaviour.setAdPlaybackStateHost(adPlaybackStateHost)
    }

    override fun onAllAdsRequested() {
        mxTrackingBehaviour.onAllAdsRequested()
        adsBehaviour.onAllAdsRequested()
    }


    override fun getContentPositionMs(
        player: Player,
        timeline: Timeline,
        period: Timeline.Period?,
        contentDurationMs: Long): Long {
        mxTrackingBehaviour.onContentPositionChanged(player, timeline, period) { adPlaybackState, playerPositionUs ->
            adsBehaviour.getLoadingAdGroupIndex(adPlaybackState, playerPositionUs)
        }
        return adsBehaviour.getContentPositionMs(player, timeline, period, contentDurationMs)
    }

    override fun onAdLoad(adGroupIndex: Int, adIndexInGroup: Int, adUri: Uri, adPodIndex: Int) {
        mxTrackingBehaviour.onAdLoad(adIndexInGroup, adUri, adPodIndex)
        adsBehaviour.onAdLoad(adGroupIndex, adIndexInGroup, adUri, adPodIndex)
    }

    override fun provideBehaviourTracker(): IBehaviourTracker {
        return mxTrackingBehaviour
    }

    override fun onAdsManagerLoaded(cuePoints: List<Float>?) {
        mxTrackingBehaviour.onAdsManagerLoaded(cuePoints)
        adsBehaviour.onAdsManagerLoaded(cuePoints)
    }
}