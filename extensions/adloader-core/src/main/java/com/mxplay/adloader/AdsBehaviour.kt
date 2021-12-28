package com.mxplay.adloader

import android.net.Uri
import android.os.Handler
import android.util.Log
import android.util.Pair
import androidx.annotation.CallSuper
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.mxplay.interactivemedia.api.AdEvent

open class AdsBehaviour private constructor(
    private val vastTimeOutInMs: Int,
    protected open val debug: Boolean = false,
    private val composedAdEventListener: ComposedAdEventListener,
    private val composedAdErrorListener: ComposedAdErrorListener
) : IAdsBehaviour,
    AdEvent.AdEventListener by composedAdEventListener,
    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener by composedAdEventListener,
    AdErrorEvent.AdErrorListener by composedAdErrorListener,
    com.mxplay.interactivemedia.api.AdErrorEvent.AdErrorListener by composedAdErrorListener{


    constructor(vastTimeOutInMs: Int,  debug : Boolean = false) : this(vastTimeOutInMs, debug, ComposedAdEventListener(), ComposedAdErrorListener())

    interface AdPlaybackStateHost {
        val adPlaybackState: AdPlaybackState
        fun updateAdPlaybackState(adPlaybackState: AdPlaybackState, notifyExo: Boolean)
        val playingAdInfo: Pair<Int, Int>?
        fun onVastCallMaxWaitingTimeOver() {}
    }

    override fun registerAdEventListener(adEventListener: AdEvent.AdEventListener?){
        composedAdEventListener.eventListener = adEventListener
    }

    override fun registerAdErrorEventListener(adErrorListener: com.mxplay.interactivemedia.api.AdErrorEvent.AdErrorListener?){
        composedAdErrorListener.adErrorListener = adErrorListener
    }

    private lateinit var adPlaybackStateHost: AdPlaybackStateHost
    private var contentDurationMs = C.TIME_UNSET
    private var isPipModeActive = false

    override fun isPipModeActive() = isPipModeActive

    @JvmField
    protected var audioAdPodIndex = C.INDEX_UNSET
    @JvmField
    protected var audioAdPosition = C.INDEX_UNSET

    private lateinit var handler: Handler


    fun getContentDurationMs() = contentDurationMs

    override fun handleAudioAdLoaded(podIndex: Int, adPosition: Int): Boolean {
        if (isPipModeActive) {
            discardAudioAd(podIndex, adPosition)
            return true
        } else {
            audioAdPodIndex = podIndex
            audioAdPosition = adPosition
        }
        return false
    }

    private fun discardAudioAd(podIndex: Int, adPosition: Int) {
        try {
            var adPlaybackState = adPlaybackStateHost.adPlaybackState
            var adGroup = adPlaybackState.adGroups[podIndex]
            if (adGroup.count == C.LENGTH_UNSET) {
                adPlaybackState = adPlaybackState.withAdCount(podIndex, Math.max(1, adGroup.states.size))
                adGroup = adPlaybackState.adGroups[podIndex]
            }
            for (i in 0 until adGroup.count) {
                if ((adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE || adGroup.states[i] == AdPlaybackState.AD_STATE_AVAILABLE) && i == adPosition) {
                    if (debug) Log.d(TAG, "Removing audio ad $i in ad group $podIndex")
                    adPlaybackState = adPlaybackState.withAdLoadError(podIndex, i)
                    break
                }
            }
            adPlaybackStateHost.updateAdPlaybackState(adPlaybackState, true)
        } catch (e: Exception) {
            if (debug) e.printStackTrace()
        }
    }

    override fun getMediaLoadTimeout(defaultTimout: Int): Int {
        return defaultTimout
    }

    override fun provideAdTagUri(actualUri: Uri?, listener: IAdTagProvider.Listener) {
        val adTagData = AdTagData(actualUri, false, -1)
        listener.onTagReceived(adTagData)
    }

    override fun setPipMode(isPip: Boolean) {
        isPipModeActive = isPip
        if (isPipModeActive && ::adPlaybackStateHost.isInitialized) {
            val playingAdInfo = adPlaybackStateHost.playingAdInfo ?: return
            if (playingAdInfo.first == audioAdPodIndex && playingAdInfo.second == audioAdPosition) {
                discardAudioAd(audioAdPodIndex, audioAdPosition)
            }
        }
    }

    override fun setContentDuration(contentDurationMs: Long) {
        this.contentDurationMs = contentDurationMs
    }

    override fun bind(adPlaybackStateHost: AdPlaybackStateHost, handler: Handler) {
        this.adPlaybackStateHost = adPlaybackStateHost
        this.handler = handler
    }



    private val vastCallWaitingRunnable = Runnable {
        if (::adPlaybackStateHost.isInitialized) {
            adPlaybackStateHost.onVastCallMaxWaitingTimeOver()
        }
    }

    @CallSuper
    override fun onAllAdsRequested() {
        val vastCallMaxWaitingTime = if (vastTimeOutInMs > 0) (vastTimeOutInMs + 1000).toLong() else 6000.toLong()
        handler.postDelayed(vastCallWaitingRunnable, vastCallMaxWaitingTime)
    }

    override fun doSetupAdsRendering(contentPositionMs: Long, contentDurationMs: Long): Boolean {
        return false
    }

    override fun handleTimelineOrPositionChanged(player: Player?, timeline: Timeline?, period: Timeline.Period?): Boolean {
        return false
    }



    override fun setPlayer(player: Player?) {}
    override fun getContentPositionMs(player: Player, timeline: Timeline, period: Timeline.Period?, contentDurationMs: Long): Long {
        return getContentPeriodPositionMs(player, timeline, period)
    }

    override fun getAdGroupIndexForAdPod(podIndex: Int, podTimeOffset: Double, player: Player?, timeline: Timeline?, period: Timeline.Period?): Int {
        val adPlaybackState = adPlaybackStateHost.adPlaybackState
        return if (podIndex == -1) {
            // This is a postroll ad.
            adPlaybackState.adGroupCount - 1
        } else getAdGroupIndexForCuePointTimeSeconds(podTimeOffset)

        // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
    }

    private fun getAdGroupIndexForCuePointTimeSeconds(cuePointTimeSeconds: Double): Int {
        val adPlaybackState = adPlaybackStateHost.adPlaybackState
        // We receive initial cue points from IMA SDK as floats. This code replicates the same
        // calculation used to populate adGroupTimesUs (having truncated input back to float, to avoid
        // failures if the behavior of the IMA SDK changes to provide greater precision).
        val cuePointTimeSecondsFloat = cuePointTimeSeconds.toFloat()
        val adPodTimeUs = Math.round(cuePointTimeSecondsFloat.toDouble() * C.MICROS_PER_SECOND)
        for (adGroupIndex in 0 until adPlaybackState.adGroupCount) {
            val adGroupTimeUs = adPlaybackState.adGroupTimesUs[adGroupIndex]
            if (adGroupTimeUs != C.TIME_END_OF_SOURCE
                    && Math.abs(adGroupTimeUs - adPodTimeUs) < THRESHOLD_AD_MATCH_US) {
                return adGroupIndex
            }
        }
        throw IllegalStateException("Failed to find cue point")
    }

    override fun onAdsManagerLoaded(groupCount: Int) {
    }

    override fun onAdLoad(adGroupIndex: Int, adIndexInGroup: Int, adUri: Uri, adPodIndex: Int) {

    }



    /**
     * Returns the index of the ad group that will preload next, or [C.INDEX_UNSET] if there is
     * no such ad group.
     */
    fun getLoadingAdGroupIndex(adPlaybackState: AdPlaybackState, playerPositionUs: Long): Int {
        var adGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs))
        if (adGroupIndex == C.INDEX_UNSET) {
            adGroupIndex = adPlaybackState.getAdGroupIndexAfterPositionUs(
                    playerPositionUs, C.msToUs(contentDurationMs))
        }
        return adGroupIndex
    }


    override fun createAdPlaybackState(adId: Any?, adGroupTimesUs: LongArray): AdPlaybackState {
        return AdPlaybackState(adId!!, *adGroupTimesUs)
    }

    override fun obtainAdPlaybackStateHost(): AdPlaybackStateHost? {
        return if(::adPlaybackStateHost.isInitialized) adPlaybackStateHost else null
    }

    override fun provideBehaviourTracker(): IBehaviourTracker {
        return IBehaviourTracker.NO_OP_TRACKER
    }


    companion object {
        private const val TAG = "AdsBehaviour"
        const val THRESHOLD_AD_MATCH_US: Long = 1000
        @JvmStatic
         fun getContentPeriodPositionMs(
                player: Player, timeline: Timeline, period: Timeline.Period?): Long {
            val contentWindowPositionMs = player.contentPosition
            return if (timeline.isEmpty) {
                contentWindowPositionMs
            } else {
                (contentWindowPositionMs
                        - timeline.getPeriod(player.currentPeriodIndex, period!!).positionInWindowMs)
            }
        }
    }
}