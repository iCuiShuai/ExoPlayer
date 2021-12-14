package com.mxplay.adloader

import android.net.Uri
import android.text.TextUtils
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.mxplay.adloader.exo.MxAdPlaybackState
import com.mxplay.interactivemedia.internal.core.toMxAdEventType
import com.mxplay.interactivemedia.internal.data.model.EventName
import java.util.concurrent.TimeUnit
import kotlin.collections.HashSet
import kotlin.collections.set
import kotlin.math.abs

class BehaviourTracker(
    val videoAdsTracker: VideoAdsTracker,
) : IBehaviourTracker {

    private var adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost? = null


    private val adPodIndexOpportunitySet: HashSet<Int> = HashSet()
    private val adMediaUriByAdInfo: MutableMap<String, Uri> = HashMap()
    private val vastReqForAdGroudIndex: HashSet<Int> = HashSet()

    private var lastRealStartTime = C.INDEX_UNSET.toLong()
    private var lastStartRequestAdPodIndex = C.INDEX_UNSET
    private var lastRequestedAdIndexInPod = C.INDEX_UNSET
    private var skippedAdGroups = 0

    override fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost) {
        this.adPlaybackStateHost = adPlaybackStateHost
    }

    override fun onAllAdsRequested() {
        videoAdsTracker.onAdManagerRequested(emptyMap())
    }

    override fun onContentPositionPulled(player: Player, timeline: Timeline, period: Timeline.Period?, contentDurationMs: Long, adGroupIndexProvider: (adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int) {
        val contentPositionMs = AdsBehaviour.getContentPeriodPositionMs(player, timeline, period)
        tryTrackingVastRequest(contentPositionMs, contentDurationMs, adGroupIndexProvider)
    }

    override fun onAdsManagerLoaded(groupCount: Int) {
        videoAdsTracker.onAdsManagerLoaded(groupCount)
    }

    override fun onAdLoad(adIndexInGroup: Int, adUri: Uri, adPodIndex: Int, realAdGroupIndexProvider: () -> Int) {
        val actualAdGroupIndex = realAdGroupIndexProvider()
        skippedAdGroups = abs(actualAdGroupIndex - adPodIndex)
        lastRequestedAdIndexInPod = adIndexInGroup
        if (!adPodIndexOpportunitySet.contains(adPodIndex)) {
            onVastRequested(adPodIndex)
        }
        adPodIndexOpportunitySet.add(adPodIndex)
        if (!vastReqForAdGroudIndex.contains(adPodIndex)) {
            videoAdsTracker.run { onAdLoad(adPodIndex, adIndexInGroup, adUri) }
            vastReqForAdGroudIndex.add(adPodIndex);
        }
        videoAdsTracker.run { trackEvent(EventName.LOADED.value, buildEventParams(null, null, adPodIndex, adIndexInGroup, adUri))}
        adMediaUriByAdInfo[getKeyForAdInfo(adPodIndex, adIndexInGroup)] = adUri
    }

    override fun onAdEvent(adEvent: AdEvent?) {
        if (adEvent != null) {
            var adEventName = EventName.getType(adEvent.type.toMxAdEventType())?.value
            val creativeId = if (adEvent.ad != null) adEvent.ad.creativeId else null
            val advertiser = if (adEvent.ad != null) adEvent.ad.advertiserName else null
            val adPodInfo = if (adEvent.ad != null) adEvent.ad.adPodInfo else null
            val adPodIndex = adPodInfo?.podIndex ?: -1
            val adIndexInAdGroup = if (adPodInfo != null) adPodInfo.adPosition - 1 else -1
            val adUri = adMediaUriByAdInfo[getKeyForAdInfo(adPodIndex, adIndexInAdGroup)]
            when(adEvent.type) {
                AdEvent.AdEventType.AD_BREAK_FETCH_ERROR -> {
                    videoAdsTracker.run { trackEvent(VideoAdsTracker.EVENT_VAST_FAIL, buildErrorParams(-1, Exception("Fetch error for ad "), adPodIndex, adIndexInAdGroup)) }
                    return
                }
                AdEvent.AdEventType.LOG -> {
                    val adData = adEvent.adData
                    val message = "AdEvent: $adData"
                    if ("adLoadError" == adData["type"] || "adPlayError" == adData["type"] && "403" == adData["errorCode"]) {
                        videoAdsTracker.run { trackEvent(VideoAdsTracker.EVENT_ERROR, buildErrorParams(-1, Exception(message), adPodIndex, adIndexInAdGroup)) }
                    }
                    return
                }
                AdEvent.AdEventType.LOADED -> {
                    return
                }
                else -> { }
            }
            if (!TextUtils.isEmpty(adEventName)) {
                videoAdsTracker.run { trackEvent(adEventName!!, buildEventParams(creativeId, advertiser, adPodIndex, adIndexInAdGroup, adUri)) }
            }
        }
    }

    override fun onAdError(adErrorEvent: AdErrorEvent?) {
        val adError = adErrorEvent?.error
        if (adError != null) {
            videoAdsTracker.run { trackEvent(VideoAdsTracker.EVENT_ERROR, buildErrorParams(adError.errorCodeNumber, adError, lastStartRequestAdPodIndex, lastRequestedAdIndexInPod)) }
        }
    }

    private fun tryTrackingVastRequest(
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
            val adPodIndex = adGroupIndex - skippedAdGroups
            onVastRequested(adPodIndex)
        }
    }

    private fun onVastRequested(adPodIndex: Int) {
        if (lastStartRequestAdPodIndex != adPodIndex) {
            lastStartRequestAdPodIndex = adPodIndex
            if (!adPodIndexOpportunitySet.contains(adPodIndex)) {
                adPodIndexOpportunitySet.add(adPodIndex)
                videoAdsTracker.onVastRequested(adPodIndex)
            }
        }
    }

    private fun getKeyForAdInfo(adGroupIndex: Int, adIndexInAdGroup: Int): String {
        return adGroupIndex.toString() + "_" + adIndexInAdGroup
    }


}