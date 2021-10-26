package com.mxplay.adloader

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.util.Log
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.mxplay.adloader.exo.MxAdPlaybackState
import java.util.HashMap
import java.util.concurrent.TimeUnit

class BehaviourTracker(
    val videoAdsTracker: VideoAdsTracker,
) : IBehaviourTracker {

    private var adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost? = null


    private val adGroupIndexOpportunitySet: BiMap<Int, Int> = HashBiMap.create()
    private val adMediaUriByAdInfo: MutableMap<String, Uri> = HashMap()

    private var startLoadMediaTime: Long = 0
    private var startRequestTime: Long = 0
    private var lastRealStartTime = C.INDEX_UNSET.toLong()
    private var lastPlayAdGroupIndex = C.INDEX_UNSET
    private var lastStartRequestAdGroupIndex = C.INDEX_UNSET

    override fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost) {
        this.adPlaybackStateHost = adPlaybackStateHost
    }

    override fun onAllAdsRequested() {
        startRequestTime = System.currentTimeMillis()
        videoAdsTracker.onAdManagerRequested(emptyMap())

    }

    override fun onContentPositionPulled(player: Player, timeline: Timeline, period: Timeline.Period?, contentDurationMs: Long, adGroupIndexProvider:(adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int) {
        val contentPositionMs = AdsBehaviour.getContentPeriodPositionMs(player, timeline, period)
        tryUpdateStartRequestTime(contentPositionMs, contentDurationMs, adGroupIndexProvider)
    }

    override fun onAdsManagerLoaded(groupCount: Int) {
        videoAdsTracker.onAdsManagerLoaded(groupCount)
    }

    override fun trackEvent(eventName: String, adGroupIndex: Int, adIndexInAdGroup: Int, exception: Exception?) {
        trySendOpportunity(adGroupIndex)
        val adPlaybackStateHost = adPlaybackStateHost ?: return
        val adPlaybackState = adPlaybackStateHost.adPlaybackState
        var adGroupCount = adPlaybackState.adGroupCount
        if (adPlaybackState is MxAdPlaybackState) {
            adGroupCount = adPlaybackState.actualAdGroupCount
        }
        val mediaUri = adMediaUriByAdInfo[getKeyForAdInfo(adGroupIndex, adIndexInAdGroup)]
        when (eventName) {
            VideoAdsTracker.EVENT_VIDEO_AD_PLAY_FAILED -> videoAdsTracker.run{trackEvent(eventName, buildFailedParams(adGroupIndex, adIndexInAdGroup,
                startRequestTime, exception, adGroupCount, mediaUri))}
            VideoAdsTracker.EVENT_VIDEO_AD_PLAY_SUCCESS -> if (lastPlayAdGroupIndex != adGroupIndex) {
                lastPlayAdGroupIndex = adGroupIndex
                videoAdsTracker.run { trackEvent(eventName, buildSuccessParams(startLoadMediaTime, startRequestTime,
                    startLoadMediaTime, lastPlayAdGroupIndex, adIndexInAdGroup, adGroupCount, mediaUri))}
            }
        }
    }

    override fun onAdLoad(adIndexInGroup: Int, adUri: Uri, adPodIndex: Int, realAdGroupIndexProvider:() -> Int) {
        val actualAdGroupIndex = realAdGroupIndexProvider()
        videoAdsTracker.onAdLoad(actualAdGroupIndex, adIndexInGroup, adUri)
        adMediaUriByAdInfo[getKeyForAdInfo(actualAdGroupIndex, adIndexInGroup)] = adUri
        if (!adGroupIndexOpportunitySet.containsKey(actualAdGroupIndex)) {
            trySendOpportunity(actualAdGroupIndex)
        }
        adGroupIndexOpportunitySet[actualAdGroupIndex] = adPodIndex
        updateStartLoadMediaTime()
    }

    override fun onAdEvent(name: String?, creativeId: String?, advertiser: String?, adPodIndex: Int, adIndexInAdGroup: Int) {
        val adGroupIndex = adGroupIndexOpportunitySet.inverse()[adPodIndex]
        val adUri = adMediaUriByAdInfo[getKeyForAdInfo(adGroupIndex!!, adIndexInAdGroup)]
        videoAdsTracker.run { trackEvent(name!!, buildEventParams(creativeId, advertiser, adGroupIndex, adIndexInAdGroup, adUri))}
    }

    private fun updateStartLoadMediaTime() {
        startLoadMediaTime = System.currentTimeMillis()
    }

    private fun tryUpdateStartRequestTime(
        contentPositionMs: Long,
        contentDurationMs: Long,
        adGroupIndexProvider: (adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int
    ) {
        if (contentPositionMs < 0 || contentDurationMs < 0 || adPlaybackStateHost == null) {
            return
        }
        val adPlaybackState = adPlaybackStateHost!!.adPlaybackState
        val adGroupIndex = adGroupIndexProvider(adPlaybackState, C.msToUs(contentPositionMs))
        if (adGroupIndex == C.INDEX_UNSET) return
        val contentPosition = TimeUnit.MILLISECONDS.toSeconds(contentPositionMs)
        val realStartTime: Long
        var startTime = TimeUnit.MICROSECONDS.toSeconds(adPlaybackState.adGroupTimesUs[adGroupIndex])
        if (adPlaybackState is MxAdPlaybackState) {
            startTime = TimeUnit.MICROSECONDS.toSeconds(adPlaybackState.actualAdGroupTimeUs[adGroupIndex])
        }
        realStartTime = if (startTime.toDouble() == -1.0) {
            (contentPosition - 8)
        } else {
            (if (startTime == 0L) startTime else startTime - 8)
        }

        if (realStartTime == contentPosition && lastRealStartTime != realStartTime) {
            lastRealStartTime = realStartTime
            updateStartRequestTime(adGroupIndex)
        }
    }

    private fun updateStartRequestTime(adGroupIndex: Int) {
        if (lastStartRequestAdGroupIndex != adGroupIndex && adGroupIndex != C.INDEX_UNSET) {
            lastStartRequestAdGroupIndex = adGroupIndex
            startRequestTime = System.currentTimeMillis()
            trySendOpportunity(adGroupIndex)
        }
    }



    private fun trySendOpportunity(adGroupIndex: Int) {
        if (!adGroupIndexOpportunitySet.containsKey(adGroupIndex)) {
            adGroupIndexOpportunitySet[adGroupIndex] = -adGroupIndex - 1
            videoAdsTracker.onAdOpportunity(adGroupIndex)
        }
    }

    private fun getKeyForAdInfo(adGroupIndex: Int, adIndexInAdGroup: Int): String {
        return adGroupIndex.toString() + "_" + adIndexInAdGroup
    }


}