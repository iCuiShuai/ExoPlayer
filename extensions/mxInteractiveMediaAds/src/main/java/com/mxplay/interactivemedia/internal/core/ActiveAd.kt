package com.mxplay.interactivemedia.internal.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import ccom.mxplay.offlineads.exo.R
import com.mxplay.interactivemedia.api.*
import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.api.player.VideoAdPlayer
import com.mxplay.interactivemedia.api.player.VideoProgressUpdate
import com.mxplay.interactivemedia.internal.data.model.AdInline
import com.mxplay.interactivemedia.internal.data.model.ICompanionInfoProvider

class ActiveAd(val ad: Ad, private val player: VideoAdPlayer?, @AdState private var state : Int,
               private val stateChangeListener:(activeAd : ActiveAd, newState : Int) -> Unit,
               private val adProgressCallback:(activeAd : ActiveAd, progress : VideoProgressUpdate) -> Unit,
               private val adEventListener: AdEvent.AdEventListener, private val onErrorListener : AdErrorEvent.AdErrorListener,
               private val DEBUG: Boolean) : AdProgressListener, View.OnClickListener, ICompanionInfoProvider, Runnable{

        var waitingMediaTimeout = false
        private var lastAdProgressUpdate : VideoProgressUpdate = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        private var adCompanionsInfo : List<AdCompanionInfo>? = null


        override fun onAdBuffering(adMediaInfo: AdMediaInfo?) {
            if (adMediaInfo != this.ad.getMediaInfo()) return
            adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.AD_BUFFERING, ad, null))
        }

        override fun onAdProgressUpdate(adMediaInfo : AdMediaInfo?, progress: VideoProgressUpdate) {
            if (adMediaInfo != this.ad.getMediaInfo()) return
            lastAdProgressUpdate = progress
            adProgressCallback(this, progress)
            if(this.state == AdState.LOADED && progress.isVideoStarted){
                onAdStateChanged(AdState.STARTED)
            }else if(this.state == AdState.STARTED && progress.isFirstQuartileReached){
                onAdStateChanged(AdState.FIRST_QUARTILE)
            }else if (this.state == AdState.FIRST_QUARTILE && progress.isMidPointReached){
                onAdStateChanged(AdState.MIDPOINT)
            }else if (this.state == AdState.MIDPOINT && progress.isThirdQuartileReached){
                onAdStateChanged(AdState.THIRD_QUARTILE)
            } else {
                adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.AD_PROGRESS, ad, null))
            }
        }


       private fun onAdStateChanged(@AdState newState : Int){
           if(state == newState) return
            stateChangeListener(this, newState)
           if (DEBUG) {
               Log.d("ActiveAd", " Ad state changed $state new state $newState")
           }
            state = newState
            when (newState) {
                AdState.LOADED -> {
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.LOADED, ad, null))
                }
                AdState.STARTED -> {
                    val adExtras = mutableMapOf<String, String>().apply {
                        put("duration", (lastAdProgressUpdate.durationMs / 1000).toString())
                        put("volume", (player!!.volume /100).toString())
                    }
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.STARTED, ad, adExtras))
                }
                AdState.FIRST_QUARTILE -> {
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.FIRST_QUARTILE, ad, null))
                }
                AdState.MIDPOINT -> {
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.MIDPOINT, ad, null))
                }
                AdState.THIRD_QUARTILE -> {
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.THIRD_QUARTILE, ad, null))
                }
                AdState.COMPLETED -> {
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.COMPLETED, ad, null))
                }
                AdState.SKIPPED -> {
                    adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.SKIPPED, ad, null))
                }

            }
        }

        fun currentState() = state

        private fun onAdClicked(ctx : Context, ad: Ad){
            adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.CLICKED, ad, null))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse((ad as AdInline).mediaCreative!!.videoClicks!!.clickThrough)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        }

        override fun onClick(v: View?) {
            if (v?.tag == null) return
            when(v.id){
                R.id.skipButton -> {
                    skipAd((v.tag as ActiveAd).ad)
                }
                R.id.learnMoreButton -> {
                    onAdClicked(v.context, (v.tag as ActiveAd).ad)
                }
            }
        }


        fun onLoaded(adMediaInfo: AdMediaInfo?) {
            //Log.d(TAG, " onloaded "+ (ad.mediaInfo != adMediaInfo))
            if (ad.getMediaInfo() != adMediaInfo || state != AdState.NONE) return
            onAdStateChanged(AdState.LOADED)
        }

        fun onPlay(adMediaInfo: AdMediaInfo?) {
            if (ad.getMediaInfo() != adMediaInfo) return

        }

        fun onPause(adMediaInfo: AdMediaInfo?) {
            if (ad.getMediaInfo() != adMediaInfo) return
            adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.PAUSED, ad, null))
        }

        fun onResume(adMediaInfo: AdMediaInfo?) {
            if (ad.getMediaInfo() != adMediaInfo) return
            adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.RESUMED, ad, null))
        }


        fun onError(adMediaInfo: AdMediaInfo?, errorCode: AdError.AdErrorCode, message: String?) {
            if (ad.getMediaInfo() != adMediaInfo || player == null) return
            onAdStateChanged(AdState.ERROR)
            if (errorCode == AdError.AdErrorCode.VAST_MEDIA_LOAD_TIMEOUT){
                adEventListener.onAdEvent(AdEventImpl(AdEvent.AdEventType.LOG, ad, AdError(AdError.AdErrorType.PLAY, errorCode, message).convertToData()))
            }else{
                onErrorListener.onAdError(AdErrorEvent(AdError(AdError.AdErrorType.PLAY, errorCode , message ?: "")))
            }
        }

        fun skipAd(ad: Ad) {
            if (this.ad != ad) return
            onAdStateChanged(AdState.SKIPPED)
        }


        fun onEnded(adMediaInfo: AdMediaInfo?) {
            if (ad.getMediaInfo() != adMediaInfo) return
            if (this.state in AdState.STARTED until AdState.COMPLETED)
            onAdStateChanged(AdState.COMPLETED)
        }

        override fun run() {
            waitingMediaTimeout = false
            if (currentState() == AdState.LOADED){
                onError(ad.getMediaInfo(), AdError.AdErrorCode.VAST_MEDIA_LOAD_TIMEOUT, "VAST media file loading reached a timeout of 8 seconds.")
            }
        }

    override fun getAllCompanionAds(): List<CompanionAd>? {
        return ad.getCompanionAds()
    }

    override fun getCompanionsInfo(): List<AdCompanionInfo>? {
        return adCompanionsInfo
    }

    override fun setCompanionsInfo(adCompanionInfo: List<AdCompanionInfo>?) {
        this.adCompanionsInfo = adCompanionInfo
    }

    fun getCurrentAdDuration(): Long {
        return lastAdProgressUpdate.durationMs
    }

}

