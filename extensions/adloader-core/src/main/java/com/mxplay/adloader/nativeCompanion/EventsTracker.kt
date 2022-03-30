package com.mxplay.adloader.nativeCompanion

import androidx.annotation.NonNull
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.VideoAdsTracker
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.TrackingEvent
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class EventsTracker(private val adsBehaviour: AdsBehaviour?, remoteDataSource: RemoteDataSource, ioOpsScope: CoroutineScope) {



    fun trackClick(clickTracker: String, data: JSONObject) {
        val trackingInfo = convertToTrackingInfo(data)
        adsBehaviour?.provideBehaviourTracker()?.trackCompanionEvent("creativeView", emptyMap<String, String>())

      /*  adTracker.track(listOf(clickTracker), trackingInfo)
        val params = adParameters(trackingInfo, startTime).apply {
            put("extension_type", type)
            put("templateId", template.id)
        }
        Tracker.trackAdEvent(AdEvent.EXTENSION_VIEW_CLICK, params as Map<String, String>)*/
    }

    private fun convertToTrackingInfo(data: JSONObject): JSONObject {
        return JSONObject()
    }

    fun trackAdImpression(impressionTracker: String, data: JSONObject){
        val trackingInfo = convertToTrackingInfo(data)
        adsBehaviour?.provideBehaviourTracker()?.trackCompanionEvent("CompanionClickTracking", emptyMap<String, String>())
/*
        adTracker.track(listOf(impressionTracker), trackingInfo)
        val params = adParameters(trackingInfo, startTime).apply {
            put("extension_type", type)
            put("templateId", template.id)
        }
        Tracker.trackAdEvent(AdEvent.EXTENSION_SHOWN, params as Map<String, String>)
        isImpressed = true*/
    }

}