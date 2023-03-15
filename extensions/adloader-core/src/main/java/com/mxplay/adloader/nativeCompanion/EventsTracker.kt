package com.mxplay.adloader.nativeCompanion

import com.mxplay.adloader.VideoAdsTracker
import com.mxplay.adloader.nativeCompanion.expandable.data.CompanionTrackingInfo
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.util.UrlStitchingService
import com.mxplay.logger.ZenLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class)
class EventsTracker(private val videoAdsTracker: VideoAdsTracker, private val urlStitchingService: UrlStitchingService, private val remoteDataSource: RemoteDataSource, private val companionSdkScope: CoroutineScope) {


    companion object{
        const val TAG = "EventsTracker"
        const val EVENT_COMPANION_ITEM_CLICK_TRACKING  = "CompanionItemClickTracking"
        const val EVENT_COMPANION_CLICK_TRACKING  =  "CompanionClickTracking"
        const val EVENT_COMPANION_CLOSED_TRACKING = "CompanionClosedTracking"
        const val EVENT_COMPANION_OPENED_TRACKING = "CompanionOpenedTracking"
        const val EVENT_CREATIVE_VIEW = "creativeView"
        const val EVENT_COMPANION_ITEM_CREATIVE_VIEW = "CompanionItemCreativeView"

        const val P_AD_EXTENSION_SESSION_ID = "adExtensionSessionId"
        const val P_IS_AUTO_HIDE = "isAutoHide"
        const val P_IS_AUTO_SHOWN = "isAutoShown"
    }

    private var impressionJob : Job?  = null
    private var impressionsBatch = mutableMapOf<String, MutableList<ImpressionData>>()



    fun trackAdHide(companion : NativeCompanion, autoHide : Boolean, data: CompanionTrackingInfo){
        companionSdkScope.launch {
            withContext(Dispatchers.IO){
                val trackingInfo = data.toMap().apply {
                    put(P_AD_EXTENSION_SESSION_ID, companion.adExtensionSessionId)
                    put(P_IS_AUTO_HIDE, autoHide.toString())

                }
                videoAdsTracker.trackCompanionEvent(
                    EVENT_COMPANION_CLOSED_TRACKING,
                    trackingInfo
                )
            }

        }
    }

    fun trackAdShown(companion : NativeCompanion, autoShown : Boolean, data: CompanionTrackingInfo){
        companionSdkScope.launch {
            withContext(Dispatchers.IO){
                val trackingInfo = data.toMap().apply {
                    put(P_AD_EXTENSION_SESSION_ID, companion.adExtensionSessionId)
                    put(P_IS_AUTO_SHOWN, autoShown.toString())
                }
                videoAdsTracker.trackCompanionEvent(
                    EVENT_COMPANION_OPENED_TRACKING,
                    trackingInfo
                )
            }

        }
    }

    fun trackClick(companion : NativeCompanion, clickTracker: List<String>, data: CompanionTrackingInfo) {
        companionSdkScope.launch {
            withContext(Dispatchers.IO){
                val trackingInfo = data.toMap().apply {
                    put(P_AD_EXTENSION_SESSION_ID, companion.adExtensionSessionId)
                }
                videoAdsTracker.trackCompanionEvent(if (data is CompanionTrackingInfo.CompanionItemTrackingInfo) EVENT_COMPANION_ITEM_CLICK_TRACKING else EVENT_COMPANION_CLICK_TRACKING, trackingInfo)
            }
            clickTracker.forEach { clickTrackingUrl ->
                try {
                    val result = remoteDataSource.trackEventAsync(clickTrackingUrl, { url -> replaceMacros(urlStitchingService.replaceMacros(url), data) }, { mutableMapOf() })
                    if (result){
                        ZenLogger.dt(TAG, "Event tracked success")
                    } else ZenLogger.dt(TAG, "Event tracked failed")
                } catch (e: Exception) {
                    ZenLogger.et(TAG, "Event tracked failed ${e.message}")
                }
            }

        }
    }

    private fun replaceMacros(url : String, data: CompanionTrackingInfo) : String{
       var mutableData = url.replaceFirst("[CREATIVEID]", data.creativeId)
        mutableData = mutableData.replaceFirst("[ADID]", data.adId)
        mutableData = mutableData.replaceFirst("[TEMPLATEID]", data.templateId ?: "")
        mutableData = mutableData.replaceFirst("[CAMPAIGNID]", data.campaignId ?: "")
        mutableData = mutableData.replaceFirst("[CAMPAIGNNAME]", data.campaignName ?: "")
        return mutableData
    }









    fun trackAdImpression(companion : NativeCompanion, impressionTracker: List<String>, data: CompanionTrackingInfo){
        companionSdkScope.launch {
            withContext(Dispatchers.IO){
                val trackingInfo = data.toMap().apply {
                    put(P_AD_EXTENSION_SESSION_ID, companion.adExtensionSessionId)
                }
                videoAdsTracker.trackCompanionEvent(EVENT_CREATIVE_VIEW, trackingInfo)
            }
            impressionTracker.forEach { impressionUrl ->
                try {
                    val result = remoteDataSource.trackEventAsync(impressionUrl, { url -> replaceMacros(urlStitchingService.replaceMacros(url), data) }, { mutableMapOf() })
                    if (result){
                        ZenLogger.dt(TAG, "Event impressionTracker success")
                    } else ZenLogger.dt(TAG, "Event impressionTracker failed")
                } catch (e: Exception) {
                    ZenLogger.et(TAG,"Event impressionTracker failed ${e.message}")
                }
            }

        }
    }

    fun trackAdItemImpressionStream(companion : NativeCompanion, impressionData: ImpressionData){
        val impressionDataList = impressionsBatch.getOrPut(impressionData.data.creativeId) { mutableListOf<ImpressionData>() }
        impressionDataList.add(impressionData)
        impressionJob?.cancel()
        impressionJob = companionSdkScope.launch {
           kotlin.runCatching {
               delay(500)
               impressionsBatch.forEach { entry ->
                   if (entry.value.isNotEmpty()){
                       val combinedData = entry.value.filter { !it.isBiTracked }.map {it.isBiTracked = true; it.data }.filterIsInstance(CompanionTrackingInfo.CompanionItemTrackingInfo::class.java)
                           .reduce{ acc, impressionData ->
                               acc.copy(position = "${acc.position},${impressionData.position}", itemId = "${acc.itemId},${impressionData.itemId}")
                           }
                       val trackingInfo = combinedData.toMap().apply {
                           put(P_AD_EXTENSION_SESSION_ID, companion.adExtensionSessionId)
                       }
                       ZenLogger.dt(TAG, "tracking itemsViewed ${combinedData.position}")
                       videoAdsTracker.trackCompanionEvent(EVENT_COMPANION_ITEM_CREATIVE_VIEW, trackingInfo)

                       entry.value.filter { !it.isRemoteTracked }.forEach { item ->
                           item.isRemoteTracked = true
                           item.impressionTracker.forEach {
                               try {
                                   val result = remoteDataSource.trackEventAsync(it, { url -> replaceMacros(urlStitchingService.replaceMacros(url), item.data) }, { mutableMapOf() })
                                   if (result){
                                       ZenLogger.dt(TAG, "Event impressionTracker success")
                                   } else ZenLogger.dt(TAG, "Event impressionTracker failed")
                               } catch (e: Exception) {
                                   ZenLogger.et(TAG,"Event impressionTracker failed ${e.message}")
                               }
                           }
                       }
                   }

               }
           }

        }
    }

    fun trackSurveyCompanionEvent(name: String, trackers: MutableList<String> = mutableListOf(), data: MutableMap<String, String> = mutableMapOf()) {
        companionSdkScope.launch {
             videoAdsTracker.trackCompanionEvent(name, data)
            trackers.forEach { trackerUrl ->
                try {
                    val result = remoteDataSource.trackEventAsync(trackerUrl, { url -> urlStitchingService.replaceMacros(url) }, { mutableMapOf() })
                    if (result){
                        ZenLogger.dt(TAG, "Survey Event ${name} tracker success")
                    } else ZenLogger.dt(TAG, "Survey Event ${name} tracker failed")
                } catch (e: Exception) {
                    ZenLogger.et(TAG, "Survey Event ${name} tracker failed ${e.message}")
                }
            }
        }
    }

    data class ImpressionData(val impressionTracker: List<String>, val data: CompanionTrackingInfo, var isBiTracked : Boolean = false, var isRemoteTracked : Boolean = false)
}