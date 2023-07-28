/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mxplay.mediaads.exo

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.util.Pair
import android.view.ViewGroup
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdsLoader.AdViewProvider
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.mxplay.adloader.AdTagData
import com.mxplay.adloader.AdsBehaviour.AdPlaybackStateHost
import com.mxplay.adloader.AdsBehaviourWatchTime
import com.mxplay.adloader.IAdsBehaviour
import com.mxplay.adloader.utils.Utils
import com.mxplay.interactivemedia.api.*
import com.mxplay.interactivemedia.api.player.*
import com.mxplay.mediaads.exo.OmaUtil.Companion.getAdGroupTimesUsForCuePoints
import com.mxplay.mediaads.exo.OmaUtil.Companion.getAdsRequestForAdTagDataSpec
import com.mxplay.mediaads.exo.OmaUtil.Companion.getFriendlyObstructionPurpose
import com.mxplay.mediaads.exo.OmaUtil.Companion.getStringForVideoProgressUpdate
import com.mxplay.mediaads.exo.OmaUtil.Companion.imaLooper
import com.mxplay.mediaads.exo.OmaUtil.Companion.isAdGroupLoadError
import com.mxplay.mediaads.exo.OmaUtil.OmaFactory
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/** Handles loading and playback of a single ad tag.  */ /* package */
internal class MxAdTagLoader(
        context: Context,
        private val configuration: Configuration,
        private val omaFactory: OmaFactory,
        private val supportedMimeTypes: List<String>,
        private val adTagDataSpec: DataSpec,
        private val adsId: Any,
        adViewGroup: ViewGroup?) : Player.EventListener {
    /** The state of ad playback.  */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(IMA_AD_STATE_NONE, IMA_AD_STATE_PLAYING, IMA_AD_STATE_PAUSED)
    private annotation class ImaAdState

    private val period: Timeline.Period
    private val handler: Handler
    private val componentListener: ComponentListener
    private val eventListeners: MutableList<com.google.android.exoplayer2.source.ads.AdsLoader.EventListener>
    private val adCallbacks: MutableList<VideoAdPlayerCallback?>
    private val updateAdProgressRunnable: Runnable
    private val adInfoByAdMediaInfo: BiMap<AdMediaInfo?, AdInfo>
    /** Returns the IMA SDK ad display container.  */
    var adDisplayContainer: AdDisplayContainer? = null
    /** Returns the underlying IMA SDK ads loader.  */
    val adsLoader: com.mxplay.interactivemedia.api.AdsLoader?
    private val adsBehaviour: IAdsBehaviour
    private val adUriMap = mutableMapOf<AdInfo, Uri>()
    private var pendingAdRequestContext: Any? = null
    private var player: Player? = null
    private var lastContentProgress: VideoProgressUpdate
    private var lastAdProgress: VideoProgressUpdate
    private var adsManager: AdsManager? = null
    private var isAdsManagerInitialized = false
    private var pendingAdLoadError: AdLoadException? = null
    private var timeline: Timeline
    private var contentDurationMs: Long
    private var adPlaybackState: AdPlaybackState
    private var released = false
    // Fields tracking IMA's state.
    /** Whether IMA has sent an ad event to pause content since the last resume content event.  */
    private var imaPausedContent = false

    /** The current ad playback state.  */
    @ImaAdState
    private var imaAdState = 0

    /** The current ad media info, or `null` if in state [.IMA_AD_STATE_NONE].  */
    private var imaAdMediaInfo: AdMediaInfo? = null

    /** The current ad info, or `null` if in state [.IMA_AD_STATE_NONE].  */
    private var imaAdInfo: AdInfo? = null

    /** Whether IMA has been notified that playback of content has finished.  */
    private var sentContentComplete = false
    // Fields tracking the player/loader state.
    /** Whether the player is playing an ad.  */
    private var playingAd = false

    /** Whether the player is buffering an ad.  */
    private var bufferingAd = false

    /**
     * If the player is playing an ad, stores the ad index in its ad group. [C.INDEX_UNSET]
     * otherwise.
     */
    private var playingAdIndexInAdGroup = 0

    /**
     * The ad info for a pending ad for which the media failed preparation, or `null` if no
     * pending ads have failed to prepare.
     */
    private var pendingAdPrepareErrorAdInfo: AdInfo? = null

    /**
     * If a content period has finished but IMA has not yet called [ ][ComponentListener.playAd], stores the value of [ ][SystemClock.elapsedRealtime] when the content stopped playing. This can be used to determine
     * a fake, increasing content position. [C.TIME_UNSET] otherwise.
     */
    private var fakeContentProgressElapsedRealtimeMs: Long

    /**
     * If [.fakeContentProgressElapsedRealtimeMs] is set, stores the offset from which the
     * content progress should increase. [C.TIME_UNSET] otherwise.
     */
    private var fakeContentProgressOffsetMs: Long

    /** Stores the pending content position when a seek operation was intercepted to play an ad.  */
    private var pendingContentPositionMs: Long

    /**
     * Whether [ComponentListener.getContentProgress] has sent [ ][.pendingContentPositionMs] to IMA.
     */
    private var sentPendingContentPositionMs = false

    /**
     * Stores the real time in milliseconds at which the player started buffering, possibly due to not
     * having preloaded an ad, or [C.TIME_UNSET] if not applicable.
     */
    private var waitingForPreloadElapsedRealtimeMs: Long

    /**
     * only tracked once even though there are multiple
     */
    private var adShownTracked = false

    fun getAdUri(adGroupIndex: Int, adIndexInAdGroup: Int): Uri? {
        val adInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        return adUriMap.get(adInfo)
    }


    private fun createAdPlaybackStateHost(): AdPlaybackStateHost {
        return object : AdPlaybackStateHost {
            override fun onVastCallMaxWaitingTimeOver() {}

            override val adPlaybackState: AdPlaybackState
                get() = this@MxAdTagLoader.adPlaybackState


            override fun updateAdPlaybackState(adPlaybackState: AdPlaybackState, notifyExo: Boolean) {
                this@MxAdTagLoader.adPlaybackState = adPlaybackState
                this@MxAdTagLoader.updateAdPlaybackState()
            }

            override val playingAdInfo: Pair<Int, Int>?
                get() = if (imaAdState == IMA_AD_STATE_PLAYING && imaAdInfo != null) Pair(
                        imaAdInfo!!.adGroupIndex, imaAdInfo!!.adIndexInAdGroup) else null
        }
    }

    /** Skips the current skippable ad, if there is one.  */
    fun skipAd() {
        if (adsManager != null) {
            adsManager!!.skip()
        }
    }



    /**
     * Starts passing events from this instance (including any pending ad playback state) and
     * registers obstructions.
     */
    fun addListenerWithAdView(eventListener: com.google.android.exoplayer2.source.ads.AdsLoader.EventListener, adViewProvider: AdViewProvider) {
        val isStarted = !eventListeners.isEmpty()
        eventListeners.add(eventListener)
        if (isStarted) {
            if (AdPlaybackState.NONE != adPlaybackState) {
                // Pass the existing ad playback state to the new listener.
                eventListener.onAdPlaybackState(adPlaybackState)
            }
            return
        }
        lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        maybeNotifyPendingAdLoadError()
        if (AdPlaybackState.NONE != adPlaybackState) {
            // Pass the ad playback state to the player, and resume ads if necessary.
            eventListener.onAdPlaybackState(adPlaybackState)
        } else if (adsManager != null && adsManager!!.adCuePoints != null) {
            adPlaybackState = AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(adsManager!!.adCuePoints!!.filterNotNull()))
            updateAdPlaybackState()
        }
        for (overlayInfo in adViewProvider.adOverlayInfos) {
            adDisplayContainer!!.registerFriendlyObstruction(
                    omaFactory.createFriendlyObstruction(
                            overlayInfo.view,
                            getFriendlyObstructionPurpose(overlayInfo.purpose),
                            overlayInfo.reasonDetail)!!)
        }
    }

    /**
     * Populates the ad playback state with loaded cue points, if available. Any preroll will be
     * paused immediately while waiting for this instance to be [activated][.activate].
     */
    fun maybePreloadAds(contentPositionMs: Long, contentDurationMs: Long) {
        maybeInitializeAdsManager(contentPositionMs, contentDurationMs)
    }

    /** Activates playback.  */
    fun activate(player: Player) {
        this.player = player
        player.addListener(this)
        adsBehaviour.setPlayer(player)
        val playWhenReady = player.playWhenReady
        onTimelineChanged(player.currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        val adsManager = adsManager
        if (AdPlaybackState.NONE != adPlaybackState && adsManager != null && imaPausedContent) {
            // Check whether the current ad break matches the expected ad break based on the current
            // position. If not, discard the current ad break so that the correct ad break can load.
            val contentPositionMs = getContentPeriodPositionMs(player, timeline, period)
            val adGroupForPositionIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                    C.msToUs(contentPositionMs), C.msToUs(contentDurationMs))
            if (adGroupForPositionIndex != C.INDEX_UNSET && imaAdInfo != null && imaAdInfo!!.adGroupIndex != adGroupForPositionIndex) {
                if (configuration.debugModeEnabled) {
                    Log.d(TAG, "Discarding preloaded ad $imaAdInfo")
                }
                adsManager.discardAdBreak() //TODO not implemented admanager
            }
            if (playWhenReady) {
                adsManager.resume()
            }
        }
        if (configuration.debugModeEnabled) {
            Log.d(TAG, " activate ad tag loader $player")
        }
    }

    /** Deactivates playback.  */
    fun deactivate() {
        val player = Assertions.checkNotNull(player)
        if (AdPlaybackState.NONE != adPlaybackState && imaPausedContent) {
            if (adsManager != null) {
                adsManager!!.pause()
            }
            adPlaybackState = adPlaybackState.withAdResumePositionUs(
                    if (playingAd) C.msToUs(player.currentPosition) else 0)
        }
        lastAdProgress = getAdVideoProgressUpdate()
        lastContentProgress = getContentVideoProgressUpdate()
        player.removeListener(this)
        this.player = null
    }

    /** Stops passing of events from this instance and unregisters obstructions.  */
    fun removeListener(eventListener: com.google.android.exoplayer2.source.ads.AdsLoader.EventListener) {
        eventListeners.remove(eventListener)
        if (eventListeners.isEmpty()) {
            adDisplayContainer!!.unregisterAllFriendlyObstructions()
        }
    }

    /** Releases all resources used by the ad tag loader.  */
    fun release() {
        if (released) {
            return
        }
        released = true
        pendingAdRequestContext = null
        destroyAdsManager()
        adsLoader!!.removeAdsLoadedListener(componentListener)
        adsLoader.removeAdErrorListener(componentListener)
        adsLoader.removeAdErrorListener(adsBehaviour)
        adsLoader.release()
        imaPausedContent = false
        imaAdState = IMA_AD_STATE_NONE
        imaAdMediaInfo = null
        stopUpdatingAdProgress()
        imaAdInfo = null
        pendingAdLoadError = null
        // No more ads will play once the loader is released, so mark all ad groups as skipped.
        for (i in 0 until adPlaybackState.adGroupCount) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
        }
        adUriMap.clear();
        updateAdPlaybackState()
    }

    /** Notifies the IMA SDK that the specified ad has been prepared for playback.  */
    fun handlePrepareComplete(adGroupIndex: Int, adIndexInAdGroup: Int) {
        val adInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "Prepared ad $adInfo")
        }
        val adMediaInfo = adInfoByAdMediaInfo.inverse()[adInfo]
        if (adMediaInfo != null) {
            for (i in adCallbacks.indices) {
                adCallbacks[i]!!.onLoaded(adMediaInfo)
            }
        } else {
            Log.w(TAG, "Unexpected prepared ad $adInfo")
        }
    }

    /** Notifies the IMA SDK that the specified ad has failed to prepare for playback.  */
    fun handlePrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException) {
        if (player == null) {
            return
        }
        try {
            handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception)
        } catch (e: RuntimeException) {
            maybeNotifyInternalError("handlePrepareError", e)
        }
    }

    // Player.EventListener implementation.
    override fun onTimelineChanged(timeline: Timeline, @TimelineChangeReason reason: Int) {
        if (timeline.isEmpty) {
            // The player is being reset or contains no media.
            return
        }
        this.timeline = timeline
        val player = Assertions.checkNotNull(player)
        val contentDurationUs = timeline.getPeriod(player.currentPeriodIndex, period).durationUs
        contentDurationMs = C.usToMs(contentDurationUs)
        adsBehaviour.setContentDuration(contentDurationMs)
        if (contentDurationUs != adPlaybackState.contentDurationUs) {
            adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs)
            updateAdPlaybackState()
        }
        val contentPositionMs = getContentPeriodPositionMs(player, timeline, period)
        maybeInitializeAdsManager(contentPositionMs, contentDurationMs)
        handleTimelineOrPositionChanged()
    }

    override fun onPositionDiscontinuity(@DiscontinuityReason reason: Int) {
        if (configuration.debugModeEnabled) Log.d(TAG, " onPositionDiscontinuity $playingAd  reason $reason")
        handleTimelineOrPositionChanged()
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            if (adsBehaviour.onPositionDiscontinuity(player, timeline, period)) {
                resetFlagsIfRequired()
            }
        }
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        val player = player
        if (adsManager == null || player == null) {
            return
        }
        if (playbackState == Player.STATE_BUFFERING && !player.isPlayingAd
                && isWaitingForAdToLoad()) {
            waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime()
        } else if (playbackState == Player.STATE_READY) {
            waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
        }
        handlePlayerStateChanged(player.playWhenReady, playbackState)
    }

    override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean, @PlayWhenReadyChangeReason reason: Int) {
        if (adsManager == null || player == null) {
            return
        }
        if (imaAdState == IMA_AD_STATE_PLAYING && !playWhenReady) {
            adsManager!!.pause()
            return
        }
        if (imaAdState == IMA_AD_STATE_PAUSED && playWhenReady) {
            adsManager!!.resume()
            return
        }
        handlePlayerStateChanged(playWhenReady, player!!.playbackState)
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        if (imaAdState != IMA_AD_STATE_NONE) {
            val adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo)
            for (i in adCallbacks.indices) {
                adCallbacks[i]!!.onError(adMediaInfo)
            }
        }
    }

    // Internal methods.
    private fun requestAds(
            context: Context?, adDisplayContainer: AdDisplayContainer): com.mxplay.interactivemedia.api.AdsLoader? {
        val adsLoader = omaFactory.createAdsLoader(context, adDisplayContainer, configuration)
        adsLoader!!.addAdErrorListener(componentListener)
        adsLoader.addAdErrorListener(adsBehaviour)
        adsLoader.addAdsLoadedListener(componentListener)
        val request: AdsRequest
        try {
            request = getAdsRequestForAdTagDataSpec(omaFactory, adTagDataSpec)
        } catch (e: IOException) {
            adPlaybackState = AdPlaybackState(adsId)
            updateAdPlaybackState()
            pendingAdLoadError = AdLoadException.createForAllAds(e)
            maybeNotifyPendingAdLoadError()
            return adsLoader
        }
        pendingAdRequestContext = Any()
        request.userRequestContext = pendingAdRequestContext
        request.contentProgressProvider = componentListener
        adsBehaviour.onAllAdsRequested()
        adsBehaviour.sendAdOpportunity();
        if (request.adTagUrl != null) {
            adsBehaviour.provideAdTagUri(Uri.parse(request.adTagUrl)) { adTagData: AdTagData ->
                request.adTagUrl = adTagData.adTag.toString()
                adsLoader.requestAds(request)
            }
        } else adsLoader.requestAds(request)
        return adsLoader
    }

    private fun maybeInitializeAdsManager(contentPositionMs: Long, contentDurationMs: Long) {
        val adsManager = adsManager
        if (!isAdsManagerInitialized && adsManager != null) {
            isAdsManagerInitialized = true
            val adsRenderingSettings = setupAdsRendering(contentPositionMs, contentDurationMs)
            if (adsRenderingSettings == null) {
                // There are no ads to play.
                destroyAdsManager()
            } else {
                adsManager.init(adsRenderingSettings)
                adsManager.start()
                if (configuration.debugModeEnabled) {
                    Log.d(TAG, "Initialized with ads rendering settings: $adsRenderingSettings")
                }
            }
            updateAdPlaybackState()
        }
    }

    /**
     * Configures ads rendering for starting playback, returning the settings for the IMA SDK or
     * `null` if no ads should play.
     */
    private fun setupAdsRendering(contentPositionMs: Long, contentDurationMs: Long): AdsRenderingSettings? {
        val adsRenderingSettings = omaFactory.createAdsRenderingSettings()
        adsRenderingSettings.setEnablePreloading(true)
        adsRenderingSettings.mimeTypes = if (configuration.mxMediaSdkConfig.adMediaMimeTypes != null) configuration.mxMediaSdkConfig.adMediaMimeTypes!! else supportedMimeTypes
        val isSetupDone = adsBehaviour.doSetupAdsRendering(contentPositionMs, contentDurationMs, configuration.playAdBeforeStartPosition)
        if (isSetupDone) {
            pendingContentPositionMs = 0
            return adsRenderingSettings
        }
        // Skip ads based on the start position as required.
        val adGroupTimesUs = adPlaybackState.adGroupTimesUs
        var adGroupForPositionIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                C.msToUs(contentPositionMs), C.msToUs(contentDurationMs))
        if (adGroupForPositionIndex != C.INDEX_UNSET) {
            val playAdWhenStartingPlayback = (configuration.playAdBeforeStartPosition
                    || adGroupTimesUs[adGroupForPositionIndex] == C.msToUs(contentPositionMs))
            if (!playAdWhenStartingPlayback) {
                adGroupForPositionIndex++
            } else if (hasMidrollAdGroups(adGroupTimesUs)) {
                // Provide the player's initial position to trigger loading and playing the ad. If there are
                // no midrolls, we are playing a preroll and any pending content position wouldn't be
                // cleared.
                pendingContentPositionMs = contentPositionMs
            }
            if (adGroupForPositionIndex > 0) {
                for (i in 0 until adGroupForPositionIndex) {
                    adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
                }
                if (adGroupForPositionIndex == adGroupTimesUs.size) {
                    // We don't need to play any ads. Because setPlayAdsAfterTime does not discard non-VMAP
                    // ads, we signal that no ads will render so the caller can destroy the ads manager.
                    return null
                }
                val adGroupForPositionTimeUs = adGroupTimesUs[adGroupForPositionIndex]
                val adGroupBeforePositionTimeUs = adGroupTimesUs[adGroupForPositionIndex - 1]
                if (adGroupForPositionTimeUs == C.TIME_END_OF_SOURCE) {
                    // Play the postroll by offsetting the start position just past the last non-postroll ad.
                    adsRenderingSettings.playAdsAfterTime = adGroupBeforePositionTimeUs.toDouble() / C.MICROS_PER_SECOND + 1.0
                } else {
                    // Play ads after the midpoint between the ad to play and the one before it, to avoid
                    // issues with rounding one of the two ad times.
                    val midpointTimeUs = (adGroupForPositionTimeUs + adGroupBeforePositionTimeUs) / 2.0
                    adsRenderingSettings.playAdsAfterTime = midpointTimeUs / C.MICROS_PER_SECOND
                }
            }
        }
        return adsRenderingSettings
    }

    private fun getContentVideoProgressUpdate(): VideoProgressUpdate {
        val hasContentDuration = contentDurationMs != C.TIME_UNSET
        val contentDurationMs = if (hasContentDuration) contentDurationMs else IMA_DURATION_UNSET
        val contentPositionMs: Long
        if (pendingContentPositionMs != C.TIME_UNSET && !sentPendingContentPositionMs) {
            sentPendingContentPositionMs = true
            contentPositionMs = pendingContentPositionMs
        } else if (player == null) {
            return lastContentProgress
        } else if (fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET) {
            val elapsedSinceEndMs = SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs
            contentPositionMs = fakeContentProgressOffsetMs + elapsedSinceEndMs
        } else if (imaAdState == IMA_AD_STATE_NONE && !playingAd && hasContentDuration) {
            contentPositionMs = adsBehaviour.getContentPositionMs(player!!, timeline, period, contentDurationMs)
        } else {
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY
        }
        return VideoProgressUpdate(contentPositionMs, contentDurationMs)
    }

    private fun resetFlagsIfRequired() {
        if (adsBehaviour is AdsBehaviourWatchTime && contentDurationMs != C.TIME_UNSET) {
            pendingContentPositionMs = C.TIME_UNSET
            fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
        }
    }

    // handle exo bug. player return last ad duration when it skip in pod
    private fun getAdVideoProgressUpdate(): VideoProgressUpdate {
        return if (player == null) {
            lastAdProgress
        } else if (imaAdState != IMA_AD_STATE_NONE && playingAd) {
            val adDuration = player!!.duration
            // handle exo bug. player return last ad duration when it skip in pod
            if (imaAdInfo != null
                    && (imaAdInfo!!.adGroupIndex != player!!.currentAdGroupIndex
                            || imaAdInfo!!.adIndexInAdGroup != player!!.currentAdIndexInAdGroup)) {
                return VideoProgressUpdate.VIDEO_TIME_NOT_READY
            }
            if (adDuration == C.TIME_UNSET || player!!.currentPosition > adDuration) VideoProgressUpdate.VIDEO_TIME_NOT_READY else VideoProgressUpdate(player!!.currentPosition, adDuration)
        } else {
            VideoProgressUpdate.VIDEO_TIME_NOT_READY
        }
    }

    private fun updateAdProgress() {
        val videoProgressUpdate = getAdVideoProgressUpdate()
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "Ad progress: $imaAdState :: " + getStringForVideoProgressUpdate(videoProgressUpdate))
        }
        if (imaAdState == IMA_AD_STATE_NONE || imaAdMediaInfo == null) return
        val adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo)
        for (i in adCallbacks.indices) {
            adCallbacks[i]!!.onAdProgress(adMediaInfo, videoProgressUpdate)
        }
        handler.removeCallbacks(updateAdProgressRunnable)
        handler.postDelayed(updateAdProgressRunnable, AD_PROGRESS_UPDATE_INTERVAL_MS.toLong())
    }

    private fun stopUpdatingAdProgress() {
        handler.removeCallbacks(updateAdProgressRunnable)
    }



    private fun handleAdEvent(adEvent: AdEvent) {
        if (adsManager == null) {
            // Drop events after release.
            return
        }
        when (adEvent.type) {
            AdEventType.AD_BREAK_FETCH_ERROR -> {
                val adGroupTimeSecondsString = Assertions.checkNotNull(adEvent.adData!!["adBreakTime"])
                if (configuration.debugModeEnabled) {
                    Log.d(TAG, "Fetch error for ad at $adGroupTimeSecondsString seconds")
                }
                val adGroupTimeSeconds = adGroupTimeSecondsString.toDouble()
                val adGroupIndex = if (adGroupTimeSeconds == -1.0) adPlaybackState.adGroupCount - 1 else getAdGroupIndexForCuePointTimeSeconds(adGroupTimeSeconds)
                markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex)
            }
            AdEventType.CONTENT_PAUSE_REQUESTED -> {
                // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
                // before sending CONTENT_RESUME_REQUESTED.
                imaPausedContent = true
                pauseContentInternal()
            }
            AdEventType.TAPPED -> {
                var i = 0
                while (i < eventListeners.size) {
                    eventListeners[i].onAdTapped()
                    i++
                }
            }
            AdEventType.CLICKED -> {
                var i = 0
                while (i < eventListeners.size) {
                    eventListeners[i].onAdClicked()
                    i++
                }
            }
            AdEventType.CONTENT_RESUME_REQUESTED -> {
                imaPausedContent = false
                resumeContentInternal()
            }
            AdEventType.LOG -> {
                val adData = adEvent.adData
                val message = "AdEvent: $adData"
                Log.i(TAG, message)
            }
            AdEventType.LOADED -> if (Objects.requireNonNull(adEvent.ad)!!.getVastMediaWidth() <= 1 && adEvent.ad!!.getVastMediaHeight() <= 1) {
                val adPodInfo = adEvent.ad!!.getAdPodInfo()
                adsBehaviour.handleAudioAdLoaded(adPodInfo.podIndex, adPodInfo.adPosition - 1)
            }
            AdEventType.STARTED -> {
                if (!adShownTracked) {
                    adShownTracked = true;
                    adsBehaviour.adShown();
                }
            }
            else -> {}
        }
    }

    private fun pauseContentInternal() {
        imaAdState = IMA_AD_STATE_NONE
        if (sentPendingContentPositionMs) {
            pendingContentPositionMs = C.TIME_UNSET
            sentPendingContentPositionMs = false
        }
    }

    private fun resumeContentInternal() {
        if (imaAdInfo != null) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(imaAdInfo!!.adGroupIndex)
            updateAdPlaybackState()
        }
    }// An ad is available already.

    /**
     * Returns whether this instance is expecting the first ad in an the upcoming ad group to load
     */
    private fun isWaitingForAdToLoad(): Boolean {
        val player = player ?: return false
        val adGroupIndex = getLoadingAdGroupIndex()
        if (adGroupIndex == C.INDEX_UNSET) {
            return false
        }
        val adGroup = adPlaybackState.adGroups[adGroupIndex]
        if (adGroup.count != C.LENGTH_UNSET && adGroup.count != 0 && adGroup.states[0] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
            // An ad is available already.
            return false
        }
        val adGroupTimeMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex])
        val contentPositionMs = getContentPeriodPositionMs(player, timeline, period)
        val timeUntilAdMs = adGroupTimeMs - contentPositionMs
        return timeUntilAdMs < configuration.adPreloadTimeoutMs
    }

    private fun handlePlayerStateChanged(playWhenReady: Boolean, @Player.State playbackState: Int) {
        if (playingAd && imaAdState == IMA_AD_STATE_PLAYING) {
            if (!bufferingAd && playbackState == Player.STATE_BUFFERING) {
                bufferingAd = true
                val adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo)
                for (i in adCallbacks.indices) {
                    adCallbacks[i]!!.onBuffering(adMediaInfo)
                }
                stopUpdatingAdProgress()
            } else if (bufferingAd && playbackState == Player.STATE_READY) {
                bufferingAd = false
                updateAdProgress()
            }
        }
        if (imaAdState == IMA_AD_STATE_NONE && playbackState == Player.STATE_BUFFERING && playWhenReady) {
            ensureSentContentCompleteIfAtEndOfStream()
        } else if (imaAdState != IMA_AD_STATE_NONE && playbackState == Player.STATE_ENDED) {
            val adMediaInfo = imaAdMediaInfo
            if (adMediaInfo == null) {
                Log.w(TAG, "onEnded without ad media info")
            } else {
                for (i in adCallbacks.indices) {
                    adCallbacks[i]!!.onEnded(adMediaInfo)
                }
            }
            if (configuration.debugModeEnabled) {
                Log.d(TAG, "VideoAdPlayerCallback.onEnded in onPlaybackStateChanged")
            }
        }
    }

    private fun handleTimelineOrPositionChanged() {
        val player = player
        if (adsManager == null || player == null) {
            return
        }
        adsBehaviour.handleTimelineOrPositionChanged(player, timeline, period)
        if (!playingAd && !player.isPlayingAd) {
            ensureSentContentCompleteIfAtEndOfStream()
            if (!sentContentComplete && !timeline.isEmpty) {
                val positionMs = getContentPeriodPositionMs(player, timeline, period)
                timeline.getPeriod(player.currentPeriodIndex, period)
                val newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs))
                if (newAdGroupIndex != C.INDEX_UNSET) {
                    sentPendingContentPositionMs = false
                    pendingContentPositionMs = positionMs
                }
            }
        }
        val wasPlayingAd = playingAd
        val oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup
        playingAd = player.isPlayingAd
        playingAdIndexInAdGroup = if (playingAd) player.currentAdIndexInAdGroup else C.INDEX_UNSET
        val adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup
        if (adFinished) {
            // IMA is waiting for the ad playback to finish so invoke the callback now.
            // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
            val adMediaInfo = imaAdMediaInfo
            if (adMediaInfo == null) {
                Log.w(TAG, "onEnded without ad media info")
            } else {
                val adInfo = adInfoByAdMediaInfo[adMediaInfo]
                if (playingAdIndexInAdGroup == C.INDEX_UNSET
                        || adInfo != null && adInfo.adIndexInAdGroup < playingAdIndexInAdGroup) {
                    for (i in adCallbacks.indices) {
                        adCallbacks[i]!!.onEnded(adMediaInfo)
                    }
                    if (configuration.debugModeEnabled) {
                        Log.d(
                                TAG, "VideoAdPlayerCallback.onEnded in onTimelineChanged/onPositionDiscontinuity")
                    }
                }
            }
        }
        if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == IMA_AD_STATE_NONE) {
            val adGroupIndex = player.currentAdGroupIndex
            if (adPlaybackState.adGroupTimesUs[adGroupIndex] == C.TIME_END_OF_SOURCE) {
                sendContentComplete()
            } else {
                // IMA hasn't called playAd yet, so fake the content position.
                fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
                fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex])
                if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                    fakeContentProgressOffsetMs = contentDurationMs
                }
            }
        } else if (!playingAd && wasPlayingAd && contentDurationMs != C.TIME_UNSET && imaAdState == IMA_AD_STATE_NONE) {
            pendingContentPositionMs = C.TIME_UNSET
            fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
        }
        if (configuration.debugModeEnabled) {
            Log.d(TAG, " handleTimelineOrPositionChanged $playingAd  fakeContentProgressElapsedRealtimeMs $fakeContentProgressElapsedRealtimeMs")
        }
    }

    private fun loadAdInternal(adMediaInfo: AdMediaInfo?, adPodInfo: AdPodInfo?) {
        if (adsManager == null) {
            // Drop events after release.
            if (configuration.debugModeEnabled) {
                Log.d(
                        TAG,
                        "loadAd after release " + getAdMediaInfoString(adMediaInfo) + ", ad pod " + adPodInfo)
            }
            return
        }
        val adGroupIndex = adsBehaviour.getAdGroupIndexForAdPod(adPodInfo!!.podIndex, adPodInfo.timeOffset.toDouble(), player, timeline, period)
        val adIndexInAdGroup = adPodInfo.adPosition - 1
        val adInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        // The ad URI may already be known, so force put to update it if needed.
        adInfoByAdMediaInfo.forcePut(adMediaInfo, adInfo)
        if (adsBehaviour.shouldSkipAd(adGroupIndex, adIndexInAdGroup)) {
            if (configuration.debugModeEnabled) {
                Log.d(TAG, "loadAdInternal: skipping, adGroupIndex: $adGroupIndex, ad pod $adPodInfo")
            }
            adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex)
            updateAdPlaybackState()
            return
        }
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "loadAd " + getAdMediaInfoString(adMediaInfo))
        }
        if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
            // We have already marked this ad as having failed to load, so ignore the request. IMA will
            // timeout after its media load timeout.
            return
        }

        // The ad count may increase on successive loads of ads in the same ad pod, for example, due to
        // separate requests for ad tags with multiple ads within the ad pod completing after an earlier
        // ad has loaded. See also https://github.com/google/ExoPlayer/issues/7477.
        var adGroup = adPlaybackState.adGroups[adInfo.adGroupIndex]
        adPlaybackState = adPlaybackState.withAdCount(
                adInfo.adGroupIndex, Math.max(adPodInfo.totalAds, adGroup.states.size))
        adGroup = adPlaybackState.adGroups[adInfo.adGroupIndex]
        for (i in 0 until adIndexInAdGroup) {
            // Any preceding ads that haven't loaded are not going to load.
            if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex,  /* adIndexInAdGroup= */i)
            }
        }
        val adUriBuilder = Uri.Builder()
            .encodedPath(adMediaInfo!!.url)

        if (configuration.initialBufferSizeForAdPlaybackMs != -1) {
            val initialBufferSizeForAdPlaybackMs = configuration.initialBufferSizeForAdPlaybackMs
            adUriBuilder.appendQueryParameter(
                Utils.INITIAL_BUFFER_FOR_AD_PLAYBACK_MS,
                Integer.toString(initialBufferSizeForAdPlaybackMs)
            )
        }

        val adUri = Uri.parse(adUriBuilder.build().toString())
        adUriMap[adInfo] = adUri


        adPlaybackState = adPlaybackState.withAdUri(adInfo.adGroupIndex, adInfo.adIndexInAdGroup, adUri)
        adsBehaviour.onAdLoad(adGroupIndex, adIndexInAdGroup, adUri, adPodInfo.podIndex)
        updateAdPlaybackState()
    }

    private fun playAdInternal(adMediaInfo: AdMediaInfo?) {
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "playAd " + getAdMediaInfoString(adMediaInfo))
        }
        if (adsManager == null) {
            // Drop events after release.
            return
        }
        val adInfo = adInfoByAdMediaInfo[adMediaInfo]
        if (adInfo != null && adsBehaviour.shouldSkipAd(adInfo.adGroupIndex, adInfo.adIndexInAdGroup)) {
            if (configuration.debugModeEnabled) {
                Log.d(TAG, "playAdInternal: skipping, adInfo: $adInfo")
            }
            adPlaybackState = adPlaybackState.withSkippedAdGroup(adInfo.adGroupIndex)
            imaAdState = IMA_AD_STATE_NONE
            for (i in adCallbacks.indices) {
                adCallbacks[i]!!.onError(adMediaInfo)
            }
            updateAdPlaybackState()
            return
        }
        if (imaAdState == IMA_AD_STATE_PLAYING) {
            // IMA does not always call stopAd before resuming content.
            // See [Internal: b/38354028].
            Log.w(TAG, "Unexpected playAd without stopAd")
        }
        if (imaAdState == IMA_AD_STATE_NONE) {
            // IMA is requesting to play the ad, so stop faking the content position.
            fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
            fakeContentProgressOffsetMs = C.TIME_UNSET
            imaAdState = IMA_AD_STATE_PLAYING
            imaAdMediaInfo = adMediaInfo
            imaAdInfo = Assertions.checkNotNull(adInfoByAdMediaInfo[adMediaInfo])
            for (i in adCallbacks.indices) {
                adCallbacks[i]!!.onPlay(adMediaInfo)
            }
            if (pendingAdPrepareErrorAdInfo != null && pendingAdPrepareErrorAdInfo == imaAdInfo) {
                pendingAdPrepareErrorAdInfo = null
                for (i in adCallbacks.indices) {
                    adCallbacks[i]!!.onError(adMediaInfo)
                }
            }
            updateAdProgress()
        } else {
            imaAdState = IMA_AD_STATE_PLAYING
            Assertions.checkState(adMediaInfo == imaAdMediaInfo)
            for (i in adCallbacks.indices) {
                adCallbacks[i]!!.onResume(adMediaInfo)
            }
        }
        if (player == null || !player!!.playWhenReady) {
            // Either this loader hasn't been activated yet, or the player is paused now.
            Assertions.checkNotNull(adsManager).pause()
        }
    }

    private fun pauseAdInternal(adMediaInfo: AdMediaInfo?) {
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "pauseAd " + getAdMediaInfoString(adMediaInfo))
        }
        if (adsManager == null) {
            // Drop event after release.
            return
        }
        if (imaAdState == IMA_AD_STATE_NONE) {
            // This method is called if loadAd has been called but the loaded ad won't play due to a seek
            // to a different position, so drop the event. See also [Internal: b/159111848].
            return
        }
        if (configuration.debugModeEnabled && adMediaInfo != imaAdMediaInfo) {
            Log.w(
                    TAG,
                    "Unexpected pauseAd for "
                            + getAdMediaInfoString(adMediaInfo)
                            + ", expected "
                            + getAdMediaInfoString(imaAdMediaInfo))
        }
        imaAdState = IMA_AD_STATE_PAUSED
        for (i in adCallbacks.indices) {
            adCallbacks[i]!!.onPause(adMediaInfo)
        }
    }

    private fun stopAdInternal(adMediaInfo: AdMediaInfo?) {
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "stopAd " + getAdMediaInfoString(adMediaInfo))
        }
        if (adsManager == null) {
            // Drop event after release.
            return
        }
        if (imaAdState == IMA_AD_STATE_NONE) {
            // This method is called if loadAd has been called but the preloaded ad won't play due to a
            // seek to a different position, so drop the event and discard the ad. See also [Internal:
            // b/159111848].
            val adInfo = adInfoByAdMediaInfo[adMediaInfo]
            if (adInfo != null) {
                adPlaybackState = adPlaybackState.withSkippedAd(adInfo.adGroupIndex, adInfo.adIndexInAdGroup)
                updateAdPlaybackState()
            }
            return
        }
        imaAdState = IMA_AD_STATE_NONE
        stopUpdatingAdProgress()
        // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
        Assertions.checkNotNull(imaAdInfo)
        val adGroupIndex = imaAdInfo!!.adGroupIndex
        val adIndexInAdGroup = imaAdInfo!!.adIndexInAdGroup
        if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
            // We have already marked this ad as having failed to load, so ignore the request.
            return
        }
        adPlaybackState = adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup).withAdResumePositionUs(0)
        updateAdPlaybackState()
        if (!playingAd) {
            imaAdMediaInfo = null
            imaAdInfo = null
        }
    }

    private fun handleAdGroupLoadError(error: Exception) {
        val adGroupIndex = getLoadingAdGroupIndex()
        if (adGroupIndex == C.INDEX_UNSET) {
            Log.w(TAG, "Unable to determine ad group index for ad group load error", error)
            return
        }
        markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex)
        if (pendingAdLoadError == null) {
            pendingAdLoadError = AdLoadException.createForAdGroup(error, adGroupIndex)
        }
    }

    private fun markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex: Int) {
        // Update the ad playback state so all ads in the ad group are in the error state.
        var adGroup = adPlaybackState.adGroups[adGroupIndex]
        if (adGroup.count == C.LENGTH_UNSET) {
            adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, Math.max(1, adGroup.states.size))
            adGroup = adPlaybackState.adGroups[adGroupIndex]
        }
        for (i in 0 until adGroup.count) {
            if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                if (configuration.debugModeEnabled) {
                    Log.d(TAG, "Removing ad $i in ad group $adGroupIndex")
                }
                adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i)
            }
        }
        updateAdPlaybackState()
        // Clear any pending content position that triggered attempting to load the ad group.
        pendingContentPositionMs = C.TIME_UNSET
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
    }

    private fun handleAdPrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: Exception) {
        if (configuration.debugModeEnabled) {
            Log.d(
                    TAG, "Prepare error for ad $adIndexInAdGroup in group $adGroupIndex", exception)
        }
        if (adsManager == null) {
            Log.w(TAG, "Ignoring ad prepare error after release")
            return
        }
        if (imaAdState == IMA_AD_STATE_NONE) {
            // Send IMA a content position at the ad group so that it will try to play it, at which point
            // we can notify that it failed to load.
            fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
            fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex])
            if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                fakeContentProgressOffsetMs = contentDurationMs
            }
            pendingAdPrepareErrorAdInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        } else {
            val adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo)
            // We're already playing an ad.
            if (adIndexInAdGroup > playingAdIndexInAdGroup) {
                // Mark the playing ad as ended so we can notify the error on the next ad and remove it,
                // which means that the ad after will load (if any).
                for (i in adCallbacks.indices) {
                    adCallbacks[i]!!.onEnded(adMediaInfo)
                }
            }
            playingAdIndexInAdGroup = adPlaybackState.adGroups[adGroupIndex].firstAdIndexToPlay
            for (i in adCallbacks.indices) {
                adCallbacks[i]!!.onError(Assertions.checkNotNull(adMediaInfo))
            }
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup)
        updateAdPlaybackState()
    }

    private fun ensureSentContentCompleteIfAtEndOfStream() {
        if (!sentContentComplete
                && contentDurationMs != C.TIME_UNSET && pendingContentPositionMs == C.TIME_UNSET && (getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period)
                        + THRESHOLD_END_OF_CONTENT_MS
                        >= contentDurationMs)) {
            //Not required since we are prefetching from the internal sdk itself.
            //sendContentComplete()
        }
    }

    private fun sendContentComplete() {
        for (i in adCallbacks.indices) {
            adCallbacks[i]!!.onContentComplete()
        }
        sentContentComplete = true
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "adsLoader.contentComplete")
        }
        for (i in 0 until adPlaybackState.adGroupCount) {
            if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup( /* adGroupIndex= */i)
            }
        }
        updateAdPlaybackState()
    }

    private fun updateAdPlaybackState() {
        for (i in eventListeners.indices) {
            eventListeners[i].onAdPlaybackState(adPlaybackState)
        }
    }

    private fun maybeNotifyPendingAdLoadError() {
        if (pendingAdLoadError != null) {
            for (i in eventListeners.indices) {
                eventListeners[i].onAdLoadError(pendingAdLoadError!!, adTagDataSpec)
            }
            pendingAdLoadError = null
        }
    }

    private fun maybeNotifyInternalError(name: String, cause: Exception) {
        val message = "Internal error in $name"
        Log.e(TAG, message, cause)
        // We can't recover from an unexpected error in general, so skip all remaining ads.
        for (i in 0 until adPlaybackState.adGroupCount) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
        }
        updateAdPlaybackState()
        for (i in eventListeners.indices) {
            eventListeners[i]
                    .onAdLoadError(
                            AdLoadException.createForUnexpected(RuntimeException(message, cause)),
                            adTagDataSpec)
        }
    }

    private fun getAdGroupIndexForAdPod(adPodInfo: AdPodInfo): Int {
        return if (adPodInfo.podIndex == -1) {
            // This is a postroll ad.
            adPlaybackState.adGroupCount - 1
        } else getAdGroupIndexForCuePointTimeSeconds(adPodInfo.timeOffset.toDouble())

        // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
    }

    /**
     * Returns the index of the ad group that will preload next, or [C.INDEX_UNSET] if there is
     * no such ad group.
     */
    private fun getLoadingAdGroupIndex(): Int {
            if (player == null) {
                return C.INDEX_UNSET
            }
            val playerPositionUs = C.msToUs(getContentPeriodPositionMs(player!!, timeline, period))
            var adGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs))
            if (adGroupIndex == C.INDEX_UNSET) {
                adGroupIndex = adPlaybackState.getAdGroupIndexAfterPositionUs(
                        playerPositionUs, C.msToUs(contentDurationMs))
            }
            return adGroupIndex
        }

    private fun getAdGroupIndexForCuePointTimeSeconds(cuePointTimeSeconds: Double): Int {
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

    private fun getAdMediaInfoString(adMediaInfo: AdMediaInfo?): String {
        val adInfo = adInfoByAdMediaInfo[adMediaInfo]
        return ("AdMediaInfo["
                + (adMediaInfo?.url ?: "null")
                + ", "
                + adInfo
                + "]")
    }

    private fun destroyAdsManager() {
        if (adsManager != null) {
            adsManager!!.removeAdErrorListener(componentListener)
            adsManager!!.removeAdErrorListener(adsBehaviour)
            adsManager!!.removeAdEventListener(componentListener)
            adsManager!!.removeAdEventListener(adsBehaviour)
            adsManager!!.destroy()
            adsManager = null
        }
    }

    private inner class ComponentListener : com.mxplay.interactivemedia.api.AdsLoader.AdsLoadedListener, ContentProgressProvider, AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, VideoAdPlayer {
        // AdsLoader.AdsLoadedListener implementation.
        override fun onAdsManagerLoaded(adsManagerLoadedEvent: AdsManagerLoadedEvent?) {
            val adsManager = adsManagerLoadedEvent!!.adsManager
            if (!com.google.android.exoplayer2.util.Util.areEqual(pendingAdRequestContext, adsManagerLoadedEvent.userRequestContext)) {
                adsManager.destroy()
                return
            }
            if (adsManager.adCuePoints != null) {
                adsBehaviour.onAdsManagerLoaded(adsManager.adCuePoints!!.filterNotNull())
            }
            pendingAdRequestContext = null
            this@MxAdTagLoader.adsManager = adsManager
            adsManager.addAdErrorListener(this)
            adsManager.addAdErrorListener(adsBehaviour)
            adsManager.addAdEventListener(this)
            adsManager.addAdEventListener(adsBehaviour)
            try {
                if (adsManager.adCuePoints != null) {
                    adPlaybackState = adsBehaviour.createAdPlaybackState(adsId, getAdGroupTimesUsForCuePoints(adsManager.adCuePoints!!.filterNotNull()))
                }
                updateAdPlaybackState()
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("onAdsManagerLoaded", e)
            }
        }

        // ContentProgressProvider implementation.
        override fun getContentProgress(): VideoProgressUpdate {
            val videoProgressUpdate = getContentVideoProgressUpdate()
            if (configuration.debugModeEnabled) {
                Log.d(
                        TAG,
                        "Content progress: " + getStringForVideoProgressUpdate(videoProgressUpdate))
            }
            if (waitingForPreloadElapsedRealtimeMs != C.TIME_UNSET) {
                // IMA is polling the player position but we are buffering for an ad to preload, so playback
                // may be stuck. Detect this case and signal an error if applicable.
                val stuckElapsedRealtimeMs = SystemClock.elapsedRealtime() - waitingForPreloadElapsedRealtimeMs
                if (stuckElapsedRealtimeMs >= THRESHOLD_AD_PRELOAD_MS) {
                    waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
                    handleAdGroupLoadError(IOException("Ad preloading timed out"))
                    maybeNotifyPendingAdLoadError()
                }
            } else if (pendingContentPositionMs != C.TIME_UNSET && player != null && player!!.playbackState == Player.STATE_BUFFERING && isWaitingForAdToLoad()) {
                // Prepare to timeout the load of an ad for the pending seek operation.
                waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime()
            }
            return videoProgressUpdate
        }

        // AdEvent.AdEventListener implementation.
        override fun onAdEvent(adEvent: AdEvent) {
            val adEventType = adEvent.type
            if (configuration.debugModeEnabled && adEventType !== AdEventType.AD_PROGRESS) {
                Log.d(TAG, "onAdEvent: $adEventType")
            }
            try {
                handleAdEvent(adEvent)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("onAdEvent", e)
            }
        }

        // AdErrorEvent.AdErrorListener implementation.
        override fun onAdError(adErrorEvent: AdErrorEvent) {
            val error = adErrorEvent.error
            if (configuration.debugModeEnabled) {
                Log.d(TAG, "onAdError", error)
            }
            if (adsManager == null) {
                // No ads were loaded, so allow playback to start without any ads.
                pendingAdRequestContext = null
                adPlaybackState = AdPlaybackState(adsId)
                updateAdPlaybackState()
                handler.post { adsBehaviour.onAdError(AdErrorEvent(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.REQUEST_ADMANAGER_FAILURE, "Fail to create ad manager"), null)) }
            } else if (isAdGroupLoadError(error)) {
                try {
                    handleAdGroupLoadError(error)
                } catch (e: RuntimeException) {
                    maybeNotifyInternalError("onAdError", e)
                }
            }
            if (pendingAdLoadError == null) {
                pendingAdLoadError = AdLoadException.createForAllAds(error)
            }
            maybeNotifyPendingAdLoadError()
        }

        // VideoAdPlayer implementation.
        override fun addCallback(videoAdPlayerCallback: VideoAdPlayerCallback) {
            adCallbacks.add(videoAdPlayerCallback)
        }

        override fun removeCallback(videoAdPlayerCallback: VideoAdPlayerCallback) {
            adCallbacks.remove(videoAdPlayerCallback)
        }

        override fun getAdProgress(): VideoProgressUpdate {
            throw IllegalStateException("Unexpected call to getAdProgress when using preloading")
        }

        override val volume: Float
            get() = 1.0F //Since player don't have volume controls so always return 1
        override val mute: Boolean
            get() = false

        override fun loadAd(adMediaInfo: AdMediaInfo?, adPodInfo: AdPodInfo?) {
            try {
                loadAdInternal(adMediaInfo, adPodInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("loadAd", e)
            }
        }

        override fun playAd(adMediaInfo: AdMediaInfo?) {
            try {
                playAdInternal(adMediaInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("playAd", e)
            }
        }

        override fun pauseAd(adMediaInfo: AdMediaInfo?) {
            try {
                pauseAdInternal(adMediaInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("pauseAd", e)
            }
        }

        override fun stopAd(adMediaInfo: AdMediaInfo?) {
            try {
                stopAdInternal(adMediaInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("stopAd", e)
            }
        }

        override fun release() {
            // Do nothing.
        }
    }

    // TODO: Consider moving this into AdPlaybackState.
    private class AdInfo(val adGroupIndex: Int, val adIndexInAdGroup: Int) {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val adInfo = o as AdInfo
            return if (adGroupIndex != adInfo.adGroupIndex) {
                false
            } else adIndexInAdGroup == adInfo.adIndexInAdGroup
        }

        override fun hashCode(): Int {
            var result = adGroupIndex
            result = 31 * result + adIndexInAdGroup
            return result
        }

        override fun toString(): String {
            return "($adGroupIndex, $adIndexInAdGroup)"
        }
    }

    companion object {
        private const val TAG = "MxAdTagLoader"

        /**
         * Interval at which ad progress updates are provided to the IMA SDK, in milliseconds. 100 ms is
         * the interval recommended by the IMA documentation.
         *
         * @see VideoAdPlayerCallback
         */
        private const val AD_PROGRESS_UPDATE_INTERVAL_MS = 100

        /** The value used in [VideoProgressUpdate]s to indicate an unset duration.  */
        private const val IMA_DURATION_UNSET = -1L

        /**
         * Threshold before the end of content at which IMA is notified that content is complete if the
         * player buffers, in milliseconds.
         */
        private const val THRESHOLD_END_OF_CONTENT_MS: Long = 5000

        /**
         * Threshold before the start of an ad at which IMA is expected to be able to preload the ad, in
         * milliseconds.
         */
        private const val THRESHOLD_AD_PRELOAD_MS: Long = 4000

        /** The threshold below which ad cue points are treated as matching, in microseconds.  */
        private const val THRESHOLD_AD_MATCH_US: Long = 1000

        /** The ad playback state when IMA is not playing an ad.  */
        private const val IMA_AD_STATE_NONE = 0

        /**
         * The ad playback state when IMA has called [ComponentListener.playAd(AdMediaInfo)] and not
         * [ComponentListener.pauseAd(AdMediaInfo)].
         */
        private const val IMA_AD_STATE_PLAYING = 1

        /**
         * The ad playback state when IMA has called [ComponentListener.pauseAd] while
         * playing an ad.
         */
        private const val IMA_AD_STATE_PAUSED = 2
        private fun getContentPeriodPositionMs(
                player: Player, timeline: Timeline, period: Timeline.Period): Long {
            val contentWindowPositionMs = player.contentPosition
            return if (timeline.isEmpty) {
                contentWindowPositionMs
            } else {
                (contentWindowPositionMs
                        - timeline.getPeriod(player.currentPeriodIndex, period).positionInWindowMs)
            }
        }

        private fun hasMidrollAdGroups(adGroupTimesUs: LongArray): Boolean {
            val count = adGroupTimesUs.size
            return if (count == 1) {
                adGroupTimesUs[0].toInt() != 0 && adGroupTimesUs[0] != C.TIME_END_OF_SOURCE
            } else if (count == 2) {
                adGroupTimesUs[0].toInt() != 0 || adGroupTimesUs[1] != C.TIME_END_OF_SOURCE
            } else {
                // There's at least one midroll ad group, as adGroupTimesUs is never empty.
                true
            }
        }
    }

    /** Creates a new ad tag loader, starting the ad request if the ad tag is valid.  */
    init {
        period = Timeline.Period()
        handler = com.google.android.exoplayer2.util.Util.createHandler(imaLooper,  /* callback= */null)
        componentListener = ComponentListener()
        eventListeners = ArrayList()
        adCallbacks = ArrayList( /* initialCapacity= */1)
        if (configuration.applicationVideoAdPlayerCallback != null) {
            adCallbacks.add(configuration.applicationVideoAdPlayerCallback)
        }
        updateAdProgressRunnable = Runnable { updateAdProgress() }
        adInfoByAdMediaInfo = HashBiMap.create()
        lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
        fakeContentProgressOffsetMs = C.TIME_UNSET
        pendingContentPositionMs = C.TIME_UNSET
        waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
        contentDurationMs = C.TIME_UNSET
        timeline = Timeline.EMPTY
        adPlaybackState = AdPlaybackState.NONE
        adDisplayContainer = if (adViewGroup != null) {
            omaFactory.createAdDisplayContainer(adViewGroup,  /* player= */componentListener)
        } else {
            omaFactory.createAudioAdDisplayContainer(context,  /* player= */componentListener)
        }
        if (configuration.mxMediaSdkConfig.companionAdSlots != null) {
            adDisplayContainer!!.setCompanionSlots(configuration.mxMediaSdkConfig.companionAdSlots)
        }
        adsBehaviour = configuration.adsBehaviour
        adsBehaviour.bind(createAdPlaybackStateHost(), handler)
        adsLoader = requestAds(context, adDisplayContainer!!)
    }
}