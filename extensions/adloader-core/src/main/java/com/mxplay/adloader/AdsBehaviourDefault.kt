package com.mxplay.adloader

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.mxplay.adloader.exo.MxAdPlaybackState
import java.util.HashMap
import java.util.concurrent.TimeUnit

open class AdsBehaviourDefault(vastTimeOutInMs: Int, protected val  videoAdsTracker: VideoAdsTracker? = null, public override val debug : Boolean = false) : AdsBehaviour(vastTimeOutInMs, debug) {

    private var adTagProvider: IAdTagProvider? = null
    private val adGroupIndexOpportunitySet: BiMap<Int, Int> = HashBiMap.create()
    private val adMediaUriByAdInfo: MutableMap<String, Uri> = HashMap()

    protected var startLoadMediaTime: Long = 0
    protected var startRequestTime: Long = 0
    protected var lastRealStartTime = C.INDEX_UNSET.toLong()
    protected var lastPlayAdGroupIndex = C.INDEX_UNSET
    protected var lastStartRequestAdGroupIndex = C.INDEX_UNSET


    override val trackerName: String
        get() = VideoAdsTracker.IMA_DEFAULT_AD_LOADER


    fun setAdTagProvider(adTagProvider: IAdTagProvider?) {
        this.adTagProvider = adTagProvider
    }

    private val adTagProviderExtraParams: Map<String, String>?
        get() {
            if (adTagProvider != null) {
                val adTagData = adTagProvider!!.adTagData
                if (adTagData != null) {
                    return adTagData.toParams()
                }
            }
            return null
        }


    override fun provideAdTagUri(actualUri: Uri?, listener: IAdTagProvider.Listener) {
        var adTagData: AdTagData? = AdTagData(actualUri, false, -1)
        if (adTagProvider != null) {
            adTagProvider!!.registerTagListener(listener)
            if (adTagProvider!!.adTagData != null) adTagData = adTagProvider!!.adTagData
        }
        listener.onTagReceived(adTagData!!)
    }


    override fun onAllAdsRequested() {
        super.onAllAdsRequested()
        startRequestTime = System.currentTimeMillis()
        videoAdsTracker?.onAdManagerRequested(adTagProviderExtraParams)

    }

    override fun getContentPositionMs(player: Player, timeline: Timeline, period: Timeline.Period?, contentDurationMs: Long): Long {
        val contentPositionMs = super.getContentPositionMs(player, timeline, period, contentDurationMs)
        tryUpdateStartRequestTime(contentPositionMs, contentDurationMs)
        return contentPositionMs
    }

    override fun onAdsManagerLoaded(groupCount: Int) {
        super.onAdsManagerLoaded(groupCount)
        videoAdsTracker?.onAdsManagerLoaded(groupCount)
    }

    override fun trackEvent(eventName: String, adGroupIndex: Int, adIndexInAdGroup: Int, exception: Exception?) {
        super.trackEvent(eventName, adGroupIndex, adIndexInAdGroup, exception)
        val actualAdGroupIndex = if (getActualAdGroupIndex(adGroupIndex) != C.INDEX_UNSET) getActualAdGroupIndex(adGroupIndex) else adGroupIndex
        trySendOpportunity(actualAdGroupIndex)
        val adPlaybackState = adPlaybackStateHost.adPlaybackState
        var adGroupCount = adPlaybackState.adGroupCount
        if (adPlaybackState is MxAdPlaybackState) {
            adGroupCount = adPlaybackState.actualAdGroupCount
        }
        val mediaUri = adMediaUriByAdInfo[getKeyForAdInfo(actualAdGroupIndex, adIndexInAdGroup)]
        when (eventName) {
            VideoAdsTracker.EVENT_VIDEO_AD_PLAY_FAILED -> videoAdsTracker?.run{trackEvent(eventName, buildFailedParams(actualAdGroupIndex, adIndexInAdGroup,
                    startRequestTime, exception, adGroupCount, mediaUri))}
            VideoAdsTracker.EVENT_VIDEO_AD_PLAY_SUCCESS -> if (lastPlayAdGroupIndex != actualAdGroupIndex) {
                lastPlayAdGroupIndex = actualAdGroupIndex
                videoAdsTracker?.run { trackEvent(eventName, buildSuccessParams(startLoadMediaTime, startRequestTime,
                        startLoadMediaTime, lastPlayAdGroupIndex, adIndexInAdGroup, adGroupCount, mediaUri))}
            }
        }
    }

    override fun onAdLoad(adGroupIndex: Int, adIndexInGroup: Int, adUri: Uri, adPodIndex: Int) {
        handleAdLoad(adGroupIndex, adIndexInGroup)
        val actualAdGroupIndex = if (getActualAdGroupIndex(adGroupIndex) != C.INDEX_UNSET) getActualAdGroupIndex(adGroupIndex) else adGroupIndex
        videoAdsTracker?.onAdLoad(actualAdGroupIndex, adIndexInGroup, adUri)
        adMediaUriByAdInfo[getKeyForAdInfo(actualAdGroupIndex, adIndexInGroup)] = adUri
        if (!adGroupIndexOpportunitySet.containsKey(actualAdGroupIndex)) {
            trySendOpportunity(actualAdGroupIndex)
        }
        adGroupIndexOpportunitySet[actualAdGroupIndex] = adPodIndex
        updateStartLoadMediaTime()
    }

    protected open fun handleAdLoad(adGroupIndex: Int, adIndexInAdGroup: Int) {}


    protected open fun getActualAdGroupIndex(fakeAdGroupIndex: Int): Int {
        return fakeAdGroupIndex
    }

    override fun onAdEvent(name: String?, creativeId: String?, advertiser: String?, adPodIndex: Int, adIndexInAdGroup: Int) {
        val adGroupIndex = adGroupIndexOpportunitySet.inverse()[adPodIndex]
        val adUri = adMediaUriByAdInfo[getKeyForAdInfo(adGroupIndex!!, adIndexInAdGroup)]
        videoAdsTracker?.run { trackEvent(name!!, buildEventParams(creativeId, advertiser, adGroupIndex, adIndexInAdGroup, adUri))}
    }

    private fun updateStartLoadMediaTime() {
        startLoadMediaTime = System.currentTimeMillis()
    }

    protected fun tryUpdateStartRequestTime(contentPositionMs: Long, contentDurationMs: Long) {
        if (contentPositionMs < 0 || contentDurationMs < 0) {
            return
        }
        val adPlaybackState = adPlaybackStateHost.adPlaybackState
        val adGroupIndex = getLoadingAdGroupIndexForReporting(adPlaybackState, C.msToUs(contentPositionMs))
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

    protected open fun getLoadingAdGroupIndexForReporting(adPlaybackState: AdPlaybackState, playerPositionUs: Long): Int {
        return getLoadingAdGroupIndex(adPlaybackState, playerPositionUs)
    }

    private fun trySendOpportunity(adGroupIndex: Int) {
        if (!adGroupIndexOpportunitySet.containsKey(adGroupIndex)) {
            adGroupIndexOpportunitySet[adGroupIndex] = -adGroupIndex - 1
            videoAdsTracker?.onAdOpportunity(adGroupIndex)
        }
    }

    private fun getKeyForAdInfo(adGroupIndex: Int, adIndexInAdGroup: Int): String {
        return adGroupIndex.toString() + "_" + adIndexInAdGroup
    }


}