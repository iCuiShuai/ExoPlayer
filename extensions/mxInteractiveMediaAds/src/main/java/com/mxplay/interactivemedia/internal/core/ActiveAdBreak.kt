package com.mxplay.interactivemedia.internal.core

import android.os.Handler
import com.google.android.exoplayer2.util.Log
import com.mxplay.interactivemedia.api.*
import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.api.player.VideoProgressUpdate
import com.mxplay.interactivemedia.internal.data.model.AdBreak
import com.mxplay.interactivemedia.internal.data.model.ICompanionInfoProvider
import com.mxplay.interactivemedia.internal.data.model.IMediaFilesProvider
import com.mxplay.interactivemedia.internal.data.xml.ProtocolException
import kotlinx.coroutines.TimeoutCancellationException


class ActiveAdBreak(val adBreak: AdBreak, private val loader : AdBreakLoader, private val prelodTimeOffset : Long, private val proximityThreshold : Long, @AdBreakState private var state : Int, private val displayContainer: AdDisplayContainer, private val adsRenderingSettings: AdsRenderingSettings, private val handler: Handler, private val adEventListener: AdEvent.AdEventListener, private val onErrorListener : AdErrorEvent.AdErrorListener, private val companionAdManager: CompanionAdManager) : ContentProgressListener, AdBreakLoader.AdBreakLoadingCallback {

        var viewHolder: VideoAdViewHolder? = null
        private var activeAd: ActiveAd? = null
        private var nextAd: ActiveAd? = null
        private var isActive = true
        private val mediaFileSelector = MediaFileSelector(adsRenderingSettings.mimeTypes, adsRenderingSettings.bitrateKbps)


    companion object{
        const val DEBUG = true
        const val TAG = "ActiveAdBreak"
    }
        fun getCurrentActiveAd() : ActiveAd?{
            return activeAd
        }

        private val stateCallback = fun(acAd: ActiveAd, @AdState newState: Int) {
            val player = displayContainer.getPlayer() ?: return
            when(newState){
                AdState.STARTED ->{
                    if (state == AdBreakState.AD_BREAK_INIT){
                        adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.AD_BREAK_STARTED, null, null))
                        state = AdBreakState.AD_BREAK_STARTED
                    }
                    companionAdManager.release(displayContainer.getCompanionAdSlots()?.toList())
                    companionAdManager.render(acAd.getCompanionsInfo())
                    viewHolder!!.bind(acAd)
                    nextAd = loadNextAd(activeAd)
                }
                AdState.LOADED -> {
                    (acAd as ICompanionInfoProvider).run {
                        if (getCompanionsInfo() == null) {
                            setCompanionsInfo(companionAdManager.pickBestCompanions(displayContainer, getAllCompanionAds()))
                            companionAdManager.load(getCompanionsInfo())
                        }
                    }
                }
                AdState.ERROR, AdState.SKIPPED, AdState.COMPLETED -> {
                    player.stopAd(acAd.ad.getMediaInfo())
                    viewHolder!!.unbind()
                    activeAd = if(newState == AdState.ERROR) null else nextAd // critical error discard pod
                    if (activeAd != null){
                        handler.postDelayed(activeAd!!, adsRenderingSettings.mediaLoadTimeoutMs.toLong())
                        activeAd!!.waitingMediaTimeout = true
                        player.playAd(activeAd!!.ad.getMediaInfo())

                    }
                    handler.post {
                        if (activeAd == null){
                            adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.AD_BREAK_ENDED, null, null))
                            state = AdBreakState.AD_BREAK_ENDED
                            adBreak.hasUnplayedAds = false
                            adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, null, null))
                            adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.ALL_ADS_COMPLETED, null, null))
                        }

                    }
                }
            }

        }

        private val adProgressCallback = fun(ad : ActiveAd, progress: VideoProgressUpdate){
            viewHolder!!.onAdProgressUpdate(ad.ad.getMediaInfo(), progress)
        }

        init {
            activeAd = loadNextAd(null)
        }



        fun setInactive(){
            isActive = false
        }

        override fun onProgressUpdate(progress: VideoProgressUpdate) {
            if (activeAd == null || progress == VideoProgressUpdate.VIDEO_TIME_NOT_READY || !isActive) return
            val player = displayContainer.getPlayer() ?: return
            //Log.d(TAG, " onProgressUpdate "+ (adBreak.startTimeSec * 1000 - progress.currentTimeMs))
            if (adBreak.startTimeSec * 1000 <= progress.currentTimeMs && !activeAd!!.waitingMediaTimeout
                    && activeAd!!.currentState() == AdState.LOADED){
                if (activeAd!!.ad.getAdPodInfo().adPosition == 1){
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED, activeAd!!.ad, null))
                }
                handler.postDelayed(activeAd!!, adsRenderingSettings.mediaLoadTimeoutMs.toLong())
                activeAd!!.waitingMediaTimeout = true
                player.playAd(activeAd!!.ad.getMediaInfo())
            }
        }

        private fun loadNextAd(currentActiveAd : ActiveAd?, timeLimit : Long = prelodTimeOffset): ActiveAd? {
            var nextAd : ActiveAd? = null
            var index = if (currentActiveAd != null) adBreak.adsList.indexOf(currentActiveAd.ad) else  -1
            index++
            if (adBreak.adsList.size > index){
                nextAd = ActiveAd(adBreak.adsList[index], displayContainer.getPlayer(), AdState.NONE, stateCallback, adProgressCallback, adEventListener, onErrorListener)
            }else if (adBreak.getPendingAdTagUriHost() != null && !loader.isUriResolved(adBreak.getPendingAdTagUriHost()!!)) {
                loader.loadAdBreak(adBreak, timeLimit, this)
            }else if (adBreak.totalAdsCount == 0){
                state = AdBreakState.AD_BREAK_ENDED
                adBreak.hasUnplayedAds = false
                onAdBreakFetchError(adBreak, ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.VAST_EMPTY_RESPONSE, "Empty vast")))
                return null
            }


            if (nextAd != null && nextAd.currentState() == AdState.NONE){
                val player = displayContainer.getPlayer()
                (nextAd.ad as IMediaFilesProvider).run {
                    if (getAdMediaInfo() == null)
                        setAdMediaInfo(AdMediaInfo(mediaFileSelector.selectMediaFile(this).url!!))
                }
                nextAd.onLoaded(nextAd.ad.getMediaInfo())
                player?.loadAd(nextAd.ad.getMediaInfo(), nextAd.ad.getAdPodInfo())
                return nextAd
            }
            return null
        }

        fun pause() {
            val player = displayContainer.getPlayer() ?: return
            if (activeAd == null) return
            player.pauseAd(activeAd!!.ad.getMediaInfo())
            viewHolder?.hide()
        }

        fun resume() {
            val player = displayContainer.getPlayer() ?: return
            if (activeAd == null) return
            player.playAd(activeAd!!.ad.getMediaInfo())
            viewHolder?.show()
        }

        private fun obtainTargetAd(adMediaInfo: AdMediaInfo?) : ActiveAd?{
            if (adMediaInfo == null) return null
            when(adMediaInfo){
                activeAd?.ad?.getMediaInfo() -> return activeAd
                nextAd?.ad?.getMediaInfo() -> return nextAd
            }
            return null
        }

        private fun obtainTargetAd(ad: Ad?) : ActiveAd?{
            if (ad == null) return null
            when(ad){
                activeAd?.ad -> return activeAd
                nextAd?.ad -> return nextAd
            }
            return null
        }

        fun onLoaded(adMediaInfo: AdMediaInfo?) {
            obtainTargetAd(adMediaInfo)?.let {
                if (it.currentState() != AdState.NONE) return
                it.onLoaded(adMediaInfo)
            }
        }

        fun onPlay(adMediaInfo: AdMediaInfo?) {
            obtainTargetAd(adMediaInfo)?.onPlay(adMediaInfo)
        }

        fun onPause(adMediaInfo: AdMediaInfo?) {
            obtainTargetAd(adMediaInfo)?.onPause(adMediaInfo)
        }

        fun onResume(adMediaInfo: AdMediaInfo?) {
            obtainTargetAd(adMediaInfo)?.onResume(adMediaInfo)
        }

        fun onError(adMediaInfo: AdMediaInfo?, errorCode: AdError.AdErrorCode, message: String?) {
            obtainTargetAd(adMediaInfo)?.onError(adMediaInfo, errorCode, message)
        }

        fun skipAd(ad: Ad) {
            obtainTargetAd(ad)?.skipAd(ad)
        }


        fun onEnded(adMediaInfo: AdMediaInfo?) {
            obtainTargetAd(adMediaInfo)?.onEnded(adMediaInfo)
        }



        override fun onAdBreakLoaded(adBreak: AdBreak) {
            if (isActive){
                adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.AD_BREAK_READY, null, null))
                if (DEBUG) Log.d(TAG, "onAdBreakLoaded   media ads count ${adBreak.adsList.size} :: total ads ${adBreak.totalAdsCount}" )
                if (activeAd == null)
                activeAd = loadNextAd(activeAd)
                else if (nextAd == null){
                    nextAd = loadNextAd(activeAd)
                }
            }
        }

        override fun onAdBreakFetchError(adBreak: AdBreak, e: Exception) {
            if (isActive){
                if (DEBUG) Log.e(TAG, "onAdBreakFetchError   ", e)
                if (activeAd == null){
                    var code  =  AdError.AdErrorCode.FAILED_TO_REQUEST_ADS
                    when(e){
                        is AdBreakLoader.MaxRedirectLimitReachException -> code = AdError.AdErrorCode.FAILED_TO_REQUEST_ADS
                        is TimeoutCancellationException -> code  =  AdError.AdErrorCode.VAST_LOAD_TIMEOUT
                        is ProtocolException -> code = e.error.errorCode
                    }
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.LOG, null, AdError(AdError.AdErrorType.LOAD, code, e.message).convertToData()))
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.AD_BREAK_FETCH_ERROR, null, mutableMapOf(Pair("adBreakTime", adBreak.startTimeSec.toString()))))
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, null, null))
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.ALL_ADS_COMPLETED, null, null))
                }else{
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.AD_BREAK_FETCH_ERROR, null, mutableMapOf(Pair("adBreakTime", adBreak.startTimeSec.toString()))))
                }
            }
        }


    }

