package com.mxplay.adloader

import android.net.Uri
import android.text.TextUtils
import com.google.ads.interactivemedia.v3.api.AdError
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.util.Assertions
import com.mxplay.adloader.exo.MxAdPlaybackState
import com.mxplay.interactivemedia.api.getEventName
import java.util.concurrent.TimeUnit
import kotlin.collections.set

class BehaviourTracker(
        override val videoAdsTracker: VideoAdsTracker,
        val adTrackingEventsList : Set<String> = trackingEvents
) : IBehaviourTracker {

    private var adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost? = null


    private val adPodIndexOpportunitySet: HashSet<Int> = HashSet()
    private val adMediaUriByAdInfo: MutableMap<String, Uri> = HashMap()
    private val vastReqForAdGroudIndex: HashSet<Int> = HashSet()

    private var lastRealStartTime = C.INDEX_UNSET.toLong()
    private var lastStartRequestAdPodIndex = C.INDEX_UNSET
    private var lastRequestedAdIndexInPod = C.INDEX_UNSET
    private var firstPlayingAdIndex = 0


    companion object{
        private const val THRESHOLD_AD_MATCH_US: Long = 1000
        @JvmStatic
        val trackingEvents = setOf("loaded", "start", "firstQuartile", "midpoint", "thirdQuartile", "skip", "resume", "pause", "complete", "ClickTracking")
    }

    override fun setAdPlaybackStateHost(adPlaybackStateHost: AdsBehaviour.AdPlaybackStateHost) {
        this.adPlaybackStateHost = adPlaybackStateHost
    }

    override fun doSetupAdsRendering(firstPlayingAdIndex: Int) {
        this.firstPlayingAdIndex = firstPlayingAdIndex
    }

    override fun onAllAdsRequested() {
        videoAdsTracker.onAdManagerRequested(emptyMap())
    }

    override fun sendAdOpportunity() {
        videoAdsTracker.sendAdOpportunity()
    }

    override fun adShown() {
        videoAdsTracker.adShown()
    }

    override fun onContentPositionChanged(player: Player, timeline: Timeline, period: Timeline.Period?, adGroupIndexProvider: (adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int) {
        val contentPositionMs = AdsBehaviour.getContentPeriodPositionMs(player, timeline, period)
        tryTrackingVastRequest(contentPositionMs, adGroupIndexProvider)
    }

    override fun onAdsManagerLoaded(cuePoints: List<Float>?) {
        videoAdsTracker.onAdsManagerLoaded(cuePoints?.size ?: 0)
    }

    override fun onAdLoad(adIndexInGroup: Int, adUri: Uri, adPodIndex: Int) {
        lastRequestedAdIndexInPod = adIndexInGroup
        adMediaUriByAdInfo[getKeyForAdInfo(adPodIndex, adIndexInGroup)] = adUri
    }

    override fun onAdEvent(adEvent: AdEvent?) {
        if (adEvent != null) {
            if (adEvent.type == AdEvent.AdEventType.AD_PROGRESS) return
            val adEventName = getEventName(adEvent)
            val creativeId = if (adEvent.ad != null) adEvent.ad.creativeId else null
            val advertiser = if (adEvent.ad != null) adEvent.ad.advertiserName else null
            val adPodInfo = if (adEvent.ad != null) adEvent.ad.adPodInfo else null
            val adPodIndex = adPodInfo?.podIndex ?: -1
            val adIndexInAdGroup = if (adPodInfo != null) adPodInfo.adPosition - 1 else -1
            when(adEvent.type) {
                AdEvent.AdEventType.AD_BREAK_FETCH_ERROR -> {
                    kotlin.runCatching {
                        val adGroupTimeSecondsString = Assertions.checkNotNull(adEvent.adData["adBreakTime"])
                        val adGroupTimeSeconds = adGroupTimeSecondsString.toDouble()
                        val adBreakIndex = getAdGroupIndexForCuePointTimeSeconds(adGroupTimeSeconds)
                        videoAdsTracker.run {
                            trackEvent(
                                VideoAdsTracker.EVENT_VAST_FAIL,
                                buildErrorParams(AdError.AdErrorCode.VAST_EMPTY_RESPONSE.errorNumber, Exception("Fetch error for ad "), adBreakIndex, -1)
                            )
                        }
                    }
                    return
                }
                AdEvent.AdEventType.LOG -> {
                    if (adEvent.adData != null) {
                        val adData = adEvent.adData
                        if ("adPlayError" == adData["type"]) {
                            kotlin.runCatching {
                                val code = adData["errorCode"]!!.toInt()
                                val message = adData["errorMessage"]
                                videoAdsTracker.trackEvent(VideoAdsTracker.EVENT_ERROR, videoAdsTracker.buildErrorParams(code, Exception(message), adPodIndex, adIndexInAdGroup))
                            }
                        }
                    }
                    return
                }
                AdEvent.AdEventType.LOADED -> {
                    if (!adPodIndexOpportunitySet.contains(adPodIndex)) {
                        onVastRequested(adPodIndex)
                    }
                    adPodIndexOpportunitySet.add(adPodIndex)
                    if (!vastReqForAdGroudIndex.contains(adPodIndex)) {
                        videoAdsTracker.run { onVastSuccess(adPodIndex, adIndexInAdGroup) }
                        vastReqForAdGroudIndex.add(adPodIndex);
                    }
                }
                else -> { }
            }
            if (!TextUtils.isEmpty(adEventName) && adTrackingEventsList.contains(adEventName)) {
                videoAdsTracker.run { trackEvent(adEventName!!, buildEventParams(creativeId, advertiser, adPodIndex, adIndexInAdGroup)) }
            }
        }
    }

    override fun onAdError(adErrorEvent: AdErrorEvent?) {
        val adError = adErrorEvent?.error
        if (adError != null) {
            if (adPlaybackStateHost?.adPlaybackState == AdPlaybackState.NONE || adPlaybackStateHost?.adPlaybackState?.adGroupCount == 0){
                videoAdsTracker.run { onAdsManagerRequestFailed(adError.errorCodeNumber, Exception(adError.message)) }
            }else{
                videoAdsTracker.run { trackEvent(VideoAdsTracker.EVENT_ERROR, buildErrorParams(adError.errorCodeNumber, Exception(adError.message), lastStartRequestAdPodIndex, lastRequestedAdIndexInPod)) }
            }
        }
    }

    private fun tryTrackingVastRequest(
        contentPositionMs: Long,
        adGroupIndexProvider: (adPlaybackState: AdPlaybackState, playerPositionUs: Long) -> Int
    ) {
        if (adPlaybackStateHost == null) {
            return
        }
        val adPlaybackState = adPlaybackStateHost!!.adPlaybackState
        val adGroupIndex = adGroupIndexProvider(adPlaybackState, C.msToUs(contentPositionMs))
        if (adGroupIndex == C.INDEX_UNSET) return
        var contentPosition = if (contentPositionMs >= 0) TimeUnit.MILLISECONDS.toSeconds(contentPositionMs) else 0
        val realStartTime: Long
        var startTime = TimeUnit.MICROSECONDS.toSeconds(adPlaybackState.adGroupTimesUs[adGroupIndex])
        if (adPlaybackState is MxAdPlaybackState) {
            startTime = TimeUnit.MICROSECONDS.toSeconds(adPlaybackState.actualAdGroupTimeUs[adGroupIndex])
        }
        realStartTime = if (startTime.toDouble() == -1.0)
            (contentPosition - 8)
        else if(startTime <= contentPosition) {
            contentPosition = startTime
            startTime
        }
        else startTime - 8

        if (realStartTime == contentPosition && lastRealStartTime != realStartTime) {
            lastRealStartTime = realStartTime
            onVastRequested(getPodIndex(adGroupIndex, startTime))
        }
    }

    private fun getPodIndex(adGroupIndex : Int, adGroupTime : Long) : Int{
        return if (adGroupTime == -1L) -1 else if (adGroupTime == 0L) 0 else{
            adGroupIndex - firstPlayingAdIndex + if (firstPlayingAdIndex > 0) 1 else 0
        }
    }

    fun onVastRequested(adPodIndex: Int) {
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

    private fun getAdGroupIndexForCuePointTimeSeconds(cuePointTimeSeconds: Double): Int {

        val groupCount : Int;
        val adGroupTimesUs: LongArray

        if (adPlaybackStateHost?.adPlaybackState is MxAdPlaybackState) {
            groupCount = (adPlaybackStateHost?.adPlaybackState as MxAdPlaybackState).actualAdGroupCount
            adGroupTimesUs = (adPlaybackStateHost?.adPlaybackState as MxAdPlaybackState).actualAdGroupTimeUs
        }else{
            groupCount = adPlaybackStateHost?.adPlaybackState!!.adGroupCount
            adGroupTimesUs = adPlaybackStateHost?.adPlaybackState!!.adGroupTimesUs
        }
        if (cuePointTimeSeconds == -1.0) return groupCount - 1

        // We receive initial cue points from IMA SDK as floats. This code replicates the same
        // calculation used to populate adGroupTimesUs (having truncated input back to float, to avoid
        // failures if the behavior of the IMA SDK changes to provide greater precision).
        val cuePointTimeSecondsFloat = cuePointTimeSeconds.toFloat()
        val adPodTimeUs = Math.round(cuePointTimeSecondsFloat.toDouble() * C.MICROS_PER_SECOND)
        for (adGroupIndex in 0 until groupCount) {
            val adGroupTimeUs: Long = adGroupTimesUs.get(adGroupIndex)
            if (adGroupTimeUs != C.TIME_END_OF_SOURCE
                && Math.abs(adGroupTimeUs - adPodTimeUs) < THRESHOLD_AD_MATCH_US
            ) {
                return adGroupIndex
            }
        }
        throw IllegalStateException("Failed to find cue point")
    }

}