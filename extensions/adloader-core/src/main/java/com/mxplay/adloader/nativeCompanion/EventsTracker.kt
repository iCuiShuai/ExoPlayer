package com.mxplay.adloader.nativeCompanion

import com.mxplay.adloader.VideoAdsTracker
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.util.UrlStitchingService
import com.mxplay.logger.ZenLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class EventsTracker(private val videoAdsTracker: VideoAdsTracker, private val urlStitchingService: UrlStitchingService, private val remoteDataSource: RemoteDataSource, private val companionSdkScope: CoroutineScope) {


    companion object{
        const val TAG = "EventsTracker"
    }


    fun trackClick(clickTracker: MutableList<String>, data: JSONObject) {
        companionSdkScope.launch {
            val trackingInfo = convertToTrackingInfo(data)
            videoAdsTracker.trackCompanionEvent("CompanionClickTracking", trackingInfo)
            clickTracker.forEach { clickTrackingUrl ->
                try {
                    val result = remoteDataSource.trackEventAsync(clickTrackingUrl, { url -> urlStitchingService.replaceMacros(null, url, emptyMap()) }, { mutableMapOf() })
                    if (result){
                        ZenLogger.dt(TAG, "Event tracked success")
                    } else ZenLogger.dt(TAG, "Event tracked failed")
                } catch (e: Exception) {
                    ZenLogger.et(TAG, e,"Event tracked failed")
                }
            }

        }
    }

    private fun convertToTrackingInfo(data: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val json = data.filter("adId", "campaignId", "campaignName", "creativeId", "extension_type", "templateId")
        json.keys().forEach { k ->
            map[k] = json.get(k).toString()
        }
        return map
    }

    /** Filter json values
     * keys of objects to keep
     * @return New json object with filtered items
     */
    fun  JSONObject.filter(vararg keys : String): JSONObject {
        val output = JSONObject()
        keys.forEach { key ->
            if (this.optString(key).isNotEmpty()) output.put(key, this.get(key))
        }
        return output
    }


    fun trackAdImpression(impressionTracker: MutableList<String>, data: JSONObject){
        companionSdkScope.launch {
            val trackingInfo = convertToTrackingInfo(data)
            videoAdsTracker.trackCompanionEvent("creativeView", trackingInfo)
            impressionTracker.forEach { impressionUrl ->
                try {
                    val result = remoteDataSource.trackEventAsync(impressionUrl, { url -> urlStitchingService.replaceMacros(null, url, emptyMap()) }, { mutableMapOf() })
                    if (result){
                        ZenLogger.dt(TAG, "Event impressionTracker success")
                    } else ZenLogger.dt(TAG, "Event impressionTracker failed")
                } catch (e: Exception) {
                    ZenLogger.et(TAG, e,"Event impressionTracker failed")
                }
            }

        }
    }

}