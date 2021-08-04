package com.mxplay.interactivemedia.internal.tracking

import android.util.Log
import com.mxplay.interactivemedia.internal.api.AdBreakErrorListener
import com.mxplay.interactivemedia.internal.api.AdBreakEventListener
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.*


class AdBreakEventTracker(adBreak : AdBreak, private val remoteDataSource: RemoteDataSource) : EventTracker(), AdBreakEventListener, AdBreakErrorListener {

    private val trackingEvents : Map<EventName, MutableList<TrackingEvent>>? = adBreak.provideTrackingEvent()
    private val urlStitchingService = remoteDataSource.configuration.urlStitchingService

    private fun doUrlStitching(trackingUrl: String) : String{
        val url = urlStitchingService.addCommonMacros(trackingUrl)
        return urlStitchingService.getTracker(url, urlStitchingService.getRandomNumber())
    }

    override fun onEvent(adBreakEvent: AdBreakEvent) {
        EventName.getType(adBreakEvent.event.type)?.let { name ->
            trackingEvents?.get(name)?.let {
                it.forEach { e ->
                    if (e.isTrackingAllowed()) {
                        e.onEventTracked()
                        try {
                            remoteDataSource.trackEvent(e.trackingUrl, { url -> doUrlStitching(url) }, { mutableMapOf() })
                        } catch (e: Exception) {
                            Log.e(AdEventTracker.TAG, "e ", e)
                        }
                    }
                }
            }

        }

    }

    override fun onError(adBreakErrorEvent: AdBreakErrorEvent) {
        trackingEvents?.get(EventName.ERROR)?.let {
            it.forEach { e ->
                if (e.isTrackingAllowed()) {
                    e.onEventTracked()
                    try {
                        remoteDataSource.trackEvent(e.trackingUrl, { url -> urlStitchingService.errorMacro(doUrlStitching(url), adBreakErrorEvent.errorEvent.error.errorCodeNumber)}, { mutableMapOf()})
                    } catch (e: Exception) {
                        Log.e(AdEventTracker.TAG, "error tracking event ${adBreakErrorEvent.errorEvent.error.errorCodeNumber} ", e)
                    }
                }
            }
        }
    }
}