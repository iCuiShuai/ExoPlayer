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
                     private val adCompanionManager: CompanionAdManager)   : AdsManager {

    companion object{
        private const val DELAY_MILLIS = 1000L
        private const val PROXIMITY_THRESHOLD_MILLIS = DELAY_MILLIS - 200
        private const val PRELOAD_TIME_OFFSET = 8000L
        private const val DEBUG = true
        private const val TAG = "OmaAdsManager"
    }

    private val EMPTY_AD_DATA: Map<String, String> = HashMap()

    private lateinit var adsRenderingSettings: AdsRenderingSettings
    private val cuePoints: MutableList<Float> = ArrayList()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val state: MutableMap<Ad?, AdEvent.AdEventType> = HashMap()
    private var lastAdProgress: VideoProgressUpdate = VideoProgressUpdate.VIDEO_TIME_NOT_READY
    private var videoAdViewHolder: VideoAdViewHolder? = null

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
            activeAdBreak?.onError(adMediaInfo, AdError.AdErrorCode.VIDEO_PLAY_ERROR , "Player Error" )
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


        activeAdBreak = ActiveAdBreak(nextAdBreak, adBreakLoader, PRELOAD_TIME_OFFSET, PROXIMITY_THRESHOLD_MILLIS, AdBreakState.AD_BREAK_INIT, adDisplayContainer, adsRenderingSettings, handler, object : AdEvent.AdEventListener{
            override fun onAdEvent(adEvent: AdEvent) {
                this@AdsManagerImpl.onEvent(adEvent)
                if (adEvent.type == AdEvent.AdEventType.ALL_ADS_COMPLETED) {
                    AdsManagerImpl@activeAdBreak = null
                }
            }
        }, object : AdErrorEvent.AdErrorListener{
            override fun onAdError(adErrorEvent: AdErrorEvent) {
                this@AdsManagerImpl.onAdError(adErrorEvent)
            }
        }, adCompanionManager)
        return true
    }



    override fun start() {
        scheduleUpdate(DELAY_MILLIS)
    }


    override fun pause() {
        handler.removeCallbacks(updateRunnable)
        activeAdBreak?.pause()
    }


    override fun resume() {
        scheduleUpdate(DELAY_MILLIS)
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
        val iterator = adBreaks!!.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.startTimeSec != AdBreak.POST_ROLL_START_TIME && next.startTimeSec < playAdsAfterTime) {
                iterator.remove()
            }
        }
        val contentPosition: Long
        val contentProgress: VideoProgressUpdate = contentProgressProvider.contentProgress
        contentPosition = if (contentProgress !== VideoProgressUpdate.VIDEO_TIME_NOT_READY) {
            contentProgress.currentTimeMs
        } else {
            (adsRenderingSettings.playAdsAfterTime * C.MILLIS_PER_SECOND).toLong()
        }
        if (!processNextAd(contentPosition)){
            onEvent(AdEventImpl(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, null, null))
        }

    }

    override fun destroy() {
        activeAdBreak = null
        handler.removeCallbacksAndMessages(null)
        state.clear()
        adBreaks!!.clear()
        adDisplayContainer.getPlayer()?.removeCallback(videoAdPlayerCallback)
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
        scheduleUpdate(DELAY_MILLIS)
    }



    private fun scheduleUpdate(delayTime: Long) {
        handler.removeCallbacks(updateRunnable)
        handler.postDelayed(updateRunnable, delayTime)
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


    override val adCuePoints: List<Float?>
        get() = cuePoints


}