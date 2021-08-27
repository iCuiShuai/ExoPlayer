package com.mxplay.interactivemedia.internal.tracking

import android.text.TextUtils
import android.util.Log
import android.view.View
import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.api.AdErrorEvent
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.FriendlyObstruction
import com.mxplay.interactivemedia.internal.api.FriendlyObstructionProvider
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.AdVerification
import com.mxplay.interactivemedia.internal.data.model.EventName
import com.mxplay.interactivemedia.internal.data.model.TrackingEvent
import com.mxplay.mediaads.exo.OmaUtil
import java.util.*

class AdEventTracker(private val ad: Ad, mxOmid: MxOmid?, private val remoteDataSource: RemoteDataSource,
                     private val obstructionList: List<FriendlyObstruction>, adContainer: View?) :
        EventTracker(), FriendlyObstructionProvider, AdErrorEvent.AdErrorListener, AdEvent.AdEventListener,
        AdEvent.AdEventVerificationListener, AdErrorEvent.AdErrorVerificationListener{

    private var adView : View? = adContainer
    private val trackingEvents : Map<EventName, MutableList<TrackingEvent>>? = ad.provideTrackingEvent()
    private val urlStitchingService = remoteDataSource.configuration.urlStitchingService
    private val pixelTrackers : List<IAdPixelTracker> = createPixelTrackers(mxOmid, adView)

    private var isBuffering = false

    companion object{
        const val TAG = "AdEventTracker"
    }

    private fun doUrlStitching(trackingUrl: String) : String{
        val url = urlStitchingService.addMacros(ad, trackingUrl)
        return urlStitchingService.getTracker(url, urlStitchingService.getRandomNumber())
    }



    override fun onAdEvent(adEvent: AdEvent) {
        EventName.getType(adEvent.type)?.let { name ->
            trackingEvents?.get(name)?.let {
                it.forEach { e ->
                    if (e.isTrackingAllowed()) {
                        e.onEventTracked()
                        try {
                            remoteDataSource.trackEvent(e.trackingUrl, { url -> doUrlStitching(url)}, { mutableMapOf()})
                        } catch (e: Exception) {
                            Log.e(TAG, "e ", e)
                        }
                    }
                }
            }
        }
    }

    override fun onAdError(adErrorEvent: AdErrorEvent) {
        trackingEvents?.get(EventName.ERROR)?.let {
            it.forEach { e ->
                if (e.isTrackingAllowed()) {
                    e.onEventTracked()
                    try {
                        remoteDataSource.trackEvent(e.trackingUrl, { url -> urlStitchingService.errorMacro(doUrlStitching(url), adErrorEvent.error.errorCode.errorNumber)}, { mutableMapOf()})
                    } catch (e: Exception) {
                        Log.e(TAG, "error tracking event ${adErrorEvent.error.errorCode} ", e)
                    }
                }
            }
        }
    }


    private fun updateVisibilityTrackerParams(ad: Ad, adVerification: AdVerification) {
        var params: String? = adVerification.params
        var url: String? = adVerification.url
        if (!TextUtils.isEmpty(params)) {
            params = urlStitchingService.addMacros(ad, params!!)
            adVerification.params = params
        }
        if (!TextUtils.isEmpty(url)) {
            url =  urlStitchingService.addMacros(ad, url!!)
            adVerification.url = url
        }
    }


    private fun createPixelTrackers(mxOmid: MxOmid?, adView: View?): List<IAdPixelTracker> {
        val pixelTrackers: MutableList<IAdPixelTracker> = LinkedList<IAdPixelTracker>()
        if (mxOmid == null) return pixelTrackers
        ad.provideAdVerifiers()?.forEach { adVerification ->
            updateVisibilityTrackerParams(ad, adVerification)
            pixelTrackers.add(OmidPixelTrackerImpl(mxOmid, adView, adVerification, false))
        }
        return pixelTrackers
    }

    override fun release() {
        super.release()
        pixelTrackers.forEach {
            it.finishSession()
        }
    }

    override fun getFriendlyObstructions(): List<FriendlyObstruction> {
        return obstructionList
    }

    override fun onAdEventVerification(adEvent: AdEvent) {
        pixelTrackers.forEach{
            when(adEvent.type){
                AdEvent.AdEventType.STARTED -> {
                    val props = adEvent.adData!!
                    it.startSession(this);
                    it.start(props["duration"]!!.toFloat(), props["volume"]!!.toFloat(), ad.getSkipTimeOffset().toFloat())
                }
                AdEvent.AdEventType.FIRST_QUARTILE -> it.firstQuartile()
                AdEvent.AdEventType.MIDPOINT -> it.midpoint()
                AdEvent.AdEventType.THIRD_QUARTILE -> it.thirdQuartile()
                AdEvent.AdEventType.COMPLETED -> {it.completed(); it.finishSession()}
                AdEvent.AdEventType.CLICKED -> it.clickAd()
                AdEvent.AdEventType.AD_BUFFERING -> {
                    isBuffering = true
                    it.videoBuffering(true)
                }
                AdEvent.AdEventType.PAUSED -> it.paused()
                AdEvent.AdEventType.RESUMED -> it.resumed()
                AdEvent.AdEventType.LOADED -> it.loaded()
                AdEvent.AdEventType.AD_PROGRESS -> {
                    if (isBuffering) {
                        isBuffering = false
                        it.videoBuffering(false)
                    }
                }
                AdEvent.AdEventType.SKIPPED -> it.skippedAd()
                else -> {}
            }
        }
    }

    override fun onAdErrorVerification(adErrorEvent: AdErrorEvent) {
        pixelTrackers.forEach { pxTracker ->
            if (pxTracker.hasSession()) {
                pxTracker.onError(adErrorEvent.error.errorCodeNumber, adErrorEvent.error.errorType)
                pxTracker.finishSession()
            } else {
                pxTracker.adVerification.trackingEvents?.get(EventName.VERIFICATION_NOT_EXECUTED)?.let { e ->
                    if (e.isTrackingAllowed()) {
                        e.onEventTracked()
                        try {
                            remoteDataSource.trackEvent(e.trackingUrl, { url -> urlStitchingService.errorMacro(doUrlStitching(url), adErrorEvent.error.errorCode.errorNumber)}, { mutableMapOf()})
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error tracking ${e.name} ", ex)
                        }
                    }
                }
            }
        }
    }

}
