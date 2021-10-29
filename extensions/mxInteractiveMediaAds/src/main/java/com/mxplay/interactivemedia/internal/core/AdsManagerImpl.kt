package com.mxplay.interactivemedia.internal.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.exoplayer2.C
import com.mxplay.interactivemedia.api.*
import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.api.player.ContentProgressProvider
import com.mxplay.interactivemedia.api.player.VideoAdPlayer
import com.mxplay.interactivemedia.api.player.VideoProgressUpdate
import com.mxplay.interactivemedia.internal.api.AudioListener
import com.mxplay.interactivemedia.internal.data.model.AdBreak
import com.mxplay.interactivemedia.internal.tracking.ITrackersHandler
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet


class AdsManagerImpl(private val context: Context, private val adDisplayContainer: AdDisplayContainer,
                     private val adBreaks: MutableList<AdBreak>?, private val contentProgressProvider: ContentProgressProvider, private val userRequestContext: Any?,
                     trackersHandler: ITrackersHandler?,
                     private val adBreakLoader: AdBreakLoader,
                     private val adCompanionManager: CompanionAdManager,
                     private val DEBUG: Boolean)   : AdsManager, AudioListener {

    companion object{
        private const val DELAY_MILLIS = 300L
        private const val PRELOAD_TIME_OFFSET = 4000L
        private const val TAG = "OmaAdsManager"
    }

    private val EMPTY_AD_DATA: Map<String, String> = HashMap()

    private lateinit var adsRenderingSettings: AdsRenderingSettings
    private val cuePoints: MutableList<Float> = ArrayList()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val state: MutableMap<Ad?, AdEvent.AdEventType> = HashMap()
    private var lastAdProgress: VideoProgressUpdate = VideoProgressUpdate.VIDEO_TIME_NOT_READY
    private var processedAdBreaks: Int = 0
    private var videoAdViewHolder: VideoAdViewHolder? = null
    private var audioObserver: AudioSettingsContentObserver? = null

    private val adsEventListeners: MutableSet<AdEvent.AdEventListener> = Collections
            .synchronizedSet(HashSet())
    private val adErrorListeners: MutableSet<AdErrorEvent.AdErrorListener> = Collections
            .synchronizedSet(HashSet())

    private var activeAdBreak : ActiveAdBreak? = null


    init {
        trackersHandler?.let { adsEventListeners.add(it); adErrorListeners.add(it)}
    }



    private val videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback = object : VideoAdPlayer.VideoAdPlayerCallback {

        override fun onPlay(adMediaInfo: AdMediaInfo?) {
            activeAdBreak?.viewHolder = videoAdViewHolder
            activeAdBreak?.onPlay(adMediaInfo)
        }

        override  fun onPause(adMediaInfo: AdMediaInfo?) {
            activeAdBreak?.onPause(adMediaInfo)
        }
        override  fun onLoaded(adMediaInfo: AdMediaInfo?) {
            activeAdBreak?.onLoaded(adMediaInfo)
        }
        override fun onResume(adMediaInfo: AdMediaInfo?) {
            activeAdBreak?.onResume(adMediaInfo)
        }
        override  fun onEnded(adMediaInfo: AdMediaInfo?) {
            activeAdBreak?.onEnded(adMediaInfo)
        }
        override  fun onError(adMediaInfo: AdMediaInfo?) {
            activeAdBreak?.onError(adMediaInfo, AdError.AdErrorCode.VIDEO_PLAY_ERROR, "Player Error")
        }

        override  fun onBuffering(adMediaInfo: AdMediaInfo?) {
            activeAdBreak?.getCurrentActiveAd()?.onAdBuffering(adMediaInfo)
        }

        override fun onContentComplete() {
            processNextAd(C.TIME_END_OF_SOURCE)
        }

        override  fun onAdProgress(adMediaInfo: AdMediaInfo?, videoProgressUpdate: VideoProgressUpdate?) {
            lastAdProgress = videoProgressUpdate ?: lastAdProgress
            activeAdBreak?.getCurrentActiveAd()?.onAdProgressUpdate(adMediaInfo, videoProgressUpdate!!)
        }

        override fun onVolumeChanged(volume: Float) {
            onEvent(AdEventImpl(AdEvent.AdEventType.VOLUME_CHANGE, null, mutableMapOf(Pair("volume", volume.toString()))))
        }
    }


    init {
        this.adDisplayContainer.getPlayer()?.addCallback(videoAdPlayerCallback)
        initCuePoints()
        videoAdViewHolder = VideoAdViewHolder(this.context, adDisplayContainer)
    }


    private fun initCuePoints() {
        for (i in adBreaks!!.indices) {
            cuePoints.add(adBreaks[i].startTimeSec.toFloat())
        }
    }


    private fun processNextAd(currentTime: Long): Boolean {
        val nextAdBreak = getNextAdBreak(currentTime) ?: return false
        if (activeAdBreak != null){
            if (activeAdBreak!!.adBreak == nextAdBreak) return false
            else activeAdBreak!!.setInactive()
        }


        activeAdBreak = ActiveAdBreak(
            nextAdBreak,
            adBreakLoader,
            PRELOAD_TIME_OFFSET,
            AdBreakState.AD_BREAK_INIT,
            adDisplayContainer,
            adsRenderingSettings,
            handler,
            object : AdEvent.AdEventListener{
                override fun onAdEvent(adEvent: AdEvent) {
                    this@AdsManagerImpl.onEvent(adEvent)
                    if (adEvent.type == AdEvent.AdEventType.ALL_ADS_COMPLETED) {
                        AdsManagerImpl@activeAdBreak = null
                        if (!hasUnplayedAds()) {
                            stopPullingContentProgress()
                        }
                    }
                }
            },
            object : AdErrorEvent.AdErrorListener{
                override fun onAdError(adErrorEvent: AdErrorEvent) {
                    if (!hasUnplayedAds()) {
                        stopPullingContentProgress()
                    }
                    this@AdsManagerImpl.onAdError(adErrorEvent)
                }
            },
            adCompanionManager,
            DEBUG
        )
        return true
    }



    override fun start() {
        startPullingContentProgress(0)
    }


    override fun pause() {
        stopPullingContentProgress()
        activeAdBreak?.pause()
    }


    override fun resume() {
        startPullingContentProgress(DELAY_MILLIS)
        activeAdBreak?.resume()
    }

    override fun skip() {

    }

    override fun discardAdBreak() {}

    override fun requestNextAdBreak() {

    }

    override fun focus() {

    }


    override fun init(adsRenderingSettings: AdsRenderingSettings) {
        this.adsRenderingSettings = adsRenderingSettings
        if (DEBUG) {
            Log.d(TAG, " Got  AdsRenderingSettings " + adsRenderingSettings.getPlayAdsAfterTime())
        }
        val playAdsAfterTime: Double = adsRenderingSettings.getPlayAdsAfterTime()
        refreshAdBreaks(playAdsAfterTime)
    }

    private fun refreshAdBreaks(playAdsAfterTime: Double) {
        val iterator = adBreaks!!.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.startTimeSec != AdBreak.POST_ROLL_START_TIME && next.startTimeSec < playAdsAfterTime) {
                iterator.remove()
            }
        }

        var podIndex = 1
        for (adBreak in adBreaks) {
            when (adBreak.startTime) {
                AdBreak.TimeOffsetTypes.START -> {
                    adBreak.podIndex = AdsManager.PRE_ROLL_POD_INDEX
                }
                AdBreak.TimeOffsetTypes.END -> {
                    adBreak.podIndex = AdsManager.POST_ROLL_POD_INDEX
                }
                else -> {
                    adBreak.podIndex = podIndex
                    podIndex++
                }
            }

            adBreak.refreshAds()
        }
    }

    override fun destroy() {
        activeAdBreak = null
        handler.removeCallbacksAndMessages(null)
        state.clear()
        adBreaks!!.clear()
        adDisplayContainer.getPlayer()?.removeCallback(videoAdPlayerCallback)
        unregisterAudioListener()
    }


    override fun getCurrentAd(): Ad? {
        return activeAdBreak?.getCurrentActiveAd()?.ad
    }


    override fun addAdErrorListener(adErrorListener: AdErrorEvent.AdErrorListener) {
        synchronized(adErrorListeners) { adErrorListeners.add(adErrorListener) }
    }

    override fun removeAdErrorListener(adErrorListener: AdErrorEvent.AdErrorListener) {
        synchronized(adErrorListeners) { adErrorListeners.remove(adErrorListener) }
    }

    override fun addAdEventListener(adEventListener: AdEvent.AdEventListener) {
        synchronized(adsEventListeners) { adsEventListeners.add(adEventListener) }
    }

    override fun removeAdEventListener(adEventListener: AdEvent.AdEventListener) {
        synchronized(adsEventListeners) { adsEventListeners.remove(adEventListener) }
    }


    override fun getAdProgress(): VideoProgressUpdate {
        return lastAdProgress
    }


    private val updateRunnable = Runnable {
        adDisplayContainer.getPlayer() ?: return@Runnable
        val playerPosition: Long
        val contentProgress: VideoProgressUpdate = contentProgressProvider.getContentProgress()
        if (DEBUG) {
//            Log.d(TAG, " contentProgress " + contentProgress.toString() + " Ad progress " + adProgress.toString())
        }

        if (contentProgress !== VideoProgressUpdate.VIDEO_TIME_NOT_READY) {
            playerPosition = contentProgress.currentTimeMs
            processNextAd(playerPosition)
            activeAdBreak?.onProgressUpdate(contentProgress)
        }
        startPullingContentProgress(DELAY_MILLIS)
    }



    private fun startPullingContentProgress(delayTime: Long) {
        if (processedAdBreaks < adBreaks!!.size) {
            handler.removeCallbacks(updateRunnable)
            handler.postDelayed(updateRunnable, delayTime)
        }
    }

    private fun stopPullingContentProgress() {
        handler.removeCallbacks(updateRunnable)
    }

    private fun getNextAdBreak(currentTime: Long): AdBreak? {
        // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
        // In practice we expect there to be few ad groups so the search shouldn't be expensive.
        var index = adBreaks!!.size - 1
        while (index >= 0 && isPositionBeforeAdGroup(currentTime, index)) {
            index--
        }
        if (index >= 0) {
            val adGroup = adBreaks[index]
            if (adGroup.hasUnplayedAds) return adGroup
        }
        return null
    }

    private fun isPositionBeforeAdGroup(positionUs: Long, adIndex: Int): Boolean {
        if (positionUs == C.TIME_END_OF_SOURCE) {
            // The end of the content is at (but not before) any postroll ad, and after any other ads.
            return false
        }
        val adGroup = adBreaks!![adIndex]
        return if (adGroup.startTimeSec == AdBreak.POST_ROLL_START_TIME) {
            true
        } else {
            positionUs + PRELOAD_TIME_OFFSET < adGroup.startTimeSec * C.MILLIS_PER_SECOND
        }
    }


    private fun onAdError(adErrorEvent: AdErrorEvent) {
        synchronized(adErrorListeners) {
            for (listener in adErrorListeners) {
                listener.onAdError(adErrorEvent)
            }
        }
    }

    private fun onEvent(adEvent: AdEvent) {
        when (adEvent.type) {
            AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED, AdEvent.AdEventType.AD_BREAK_STARTED, AdEvent.AdEventType.RESUMED -> handler.post {
                registerAudioListener()
                stopPullingContentProgress()
            }
            AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, AdEvent.AdEventType.AD_BREAK_ENDED -> handler.post {
                unregisterAudioListener()
                startPullingContentProgress(DELAY_MILLIS)
            }
            else -> {}
        }
        synchronized(adsEventListeners) {
            for (listener in adsEventListeners) {
                if ((adEvent.type == AdEvent.AdEventType.AD_BREAK_STARTED
                                || adEvent.type == AdEvent.AdEventType.AD_BREAK_ENDED
                                || adEvent.type == AdEvent.AdEventType.AD_BREAK_READY)
                        && listener !is ITrackersHandler){ // Avoid sending ad break events to client
                    continue
                }
                listener.onAdEvent(adEvent)
            }
        }
    }

    private fun hasUnplayedAds(): Boolean {
        var unPlayedAds = 0
        adBreaks!!.forEach {
            if (it.hasUnplayedAds) unPlayedAds++
        }
        processedAdBreaks = adBreaks.size - unPlayedAds
        return unPlayedAds > 0
    }

    override val adCuePoints: List<Float?>
        get() = cuePoints

    override fun registerAudioListener() {
        if (audioObserver == null) {
            audioObserver = AudioSettingsContentObserver(context, this, handler)
            (audioObserver as? AudioListener)?.registerAudioListener()
        }
    }

    override fun unregisterAudioListener() {
        if (audioObserver != null) {
            (audioObserver as? AudioListener)?.unregisterAudioListener()
        }
    }

    override fun onVolumeChanged(volume: Float) {
        videoAdPlayerCallback.onVolumeChanged(volume)
    }

}