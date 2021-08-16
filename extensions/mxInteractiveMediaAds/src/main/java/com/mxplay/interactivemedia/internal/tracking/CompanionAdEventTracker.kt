package com.mxplay.interactivemedia.internal.tracking

import android.util.Log
import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.internal.api.CompanionAdEventListener
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.CompanionAdData
import com.mxplay.interactivemedia.internal.data.model.CompanionAdEvent
import com.mxplay.interactivemedia.internal.data.model.EventName
import com.mxplay.interactivemedia.internal.data.model.TrackingEvent

class CompanionAdEventTracker(companionAd : CompanionAd, private val remoteDataSource: RemoteDataSource) : EventTracker(), CompanionAdEventListener{

    private val trackingEvents : Map<EventName, MutableList<TrackingEvent>> = (companionAd as CompanionAdData).provideTrackingEvent()
    private val urlStitchingService = remoteDataSource.configuration.urlStitchingService

    private fun doUrlStitching(trackingUrl: String) : String{
        val url = urlStitchingService.addCommonMacros(trackingUrl)
        return urlStitchingService.getTracker(url, urlStitchingService.getRandomNumber())
    }

    override fun onEvent(companionAdEvent: CompanionAdEvent) {
        EventName.getType(companionAdEvent.event.type)?.let { name ->
            trackingEvents.get(name)?.let {
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

}
