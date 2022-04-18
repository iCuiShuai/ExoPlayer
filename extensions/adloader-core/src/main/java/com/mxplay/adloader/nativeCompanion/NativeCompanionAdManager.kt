package com.mxplay.adloader.nativeCompanion

import android.text.TextUtils
import com.google.android.exoplayer2.C
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.VideoAdsTracker
import com.mxplay.adloader.nativeCompanion.expandable.ExpandableNativeCompanion
import com.mxplay.adloader.nativeCompanion.surveyAd.SurveyNativeCompanion
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.AdPodInfo
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.api.MxMediaSdkConfig
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.util.UrlStitchingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import java.net.URLDecoder
import java.util.HashMap

class NativeCompanionAdManager(val tracker: VideoAdsTracker, val adsBehaviour: AdsBehaviour?,
                               private val mxMediaSdkConfig: MxMediaSdkConfig,
                               private val resourceProvider: CompanionResourceProvider
):
        AdEvent.AdEventListener {

    private val companionSdkScope = CoroutineScope(SupervisorJob() +  Dispatchers.Main)
    private val urlStitchingService = UrlStitchingService(mxMediaSdkConfig)
    private val remoteDataSource = RemoteDataSource(mxMediaSdkConfig, urlStitchingService )
    private var nativeCompanion : NativeCompanion? = null
    private val eventsTracker : EventsTracker by lazy {
        EventsTracker(tracker, urlStitchingService, remoteDataSource, companionSdkScope)
    }

    companion object {
        const val TAG = "NativeCompanionManager"
        const val NATIVE_AD_CONFIG = "nativeAdConfig"
    }

    override fun onAdEvent(adEvent: AdEvent) {
        val ad = adEvent.ad
        if(adEvent.type == AdEvent.AdEventType.LOADED && ad != null) {
            checkAndLoadNativeCompanion(ad.getTraffickingParameters(), adEvent.ad!!.getAdPodInfo())
        }else if (adEvent.type == AdEvent.AdEventType.COMPLETED || adEvent.type == AdEvent.AdEventType.ALL_ADS_COMPLETED || adEvent.type == AdEvent.AdEventType.CONTENT_RESUME_REQUESTED){
            adsBehaviour?.setNativeCompanionAdInfo(C.INDEX_UNSET, C.INDEX_UNSET)
            nativeCompanion?.onAdEvent(adEvent)
            nativeCompanion = null
        }
        nativeCompanion?.onAdEvent(adEvent)

    }


    private fun checkAndLoadNativeCompanion(adParameters: String?, adPodInfo: AdPodInfo) {
        val adParameterMap = extractParamsFromString(adParameters)
        if(adParameterMap != null) {
            parseNativeCompanionType(adParameterMap)?.let {
                nativeCompanion = it
                it.loadCompanion()
                adsBehaviour?.setNativeCompanionAdInfo(adPodInfo.podIndex, adPodInfo.adPosition - 1)
            }
        }
    }

    private fun extractParamsFromString(input: String?): HashMap<String, String>? {
        try {
            if (TextUtils.isEmpty(input)) return null
            val map = HashMap<String, String>()
            val subStrs = input!!.split("&".toRegex()).toTypedArray()
            for (subStr in subStrs) {
                val kv = subStr.split("=".toRegex()).toTypedArray()
                if (kv.size == 2) {
                    map[kv[0]] = URLDecoder.decode(kv[1], "UTF-8")
                }
            }
            return map
        } catch (ignore: Exception) {
        }
        return null
    }

    private fun parseNativeCompanionType(adParameter: Map<String, String>): NativeCompanion? {
        val config = adParameter[NATIVE_AD_CONFIG]
        if(TextUtils.isEmpty(config)) return null
        val json = JSONObject(config!!)

        val companionAdSlot = pickBestCompanion(json.optString("size")) ?: return null

        return when(json.optString("type")) {
            NativeCompanion.NativeCompanionType.SURVEY_AD.value -> {
                return SurveyNativeCompanion(json, companionAdSlot,eventsTracker, adsBehaviour, companionSdkScope, remoteDataSource, NativeCompanion.NativeCompanionType.SURVEY_AD, resourceProvider)
            }
            NativeCompanion.NativeCompanionType.EXPANDABLE.value -> {
                return ExpandableNativeCompanion(json, companionAdSlot, eventsTracker, resourceProvider, NativeCompanion.NativeCompanionType.EXPANDABLE)
            }
            else -> null
        }
    }

    private fun pickBestCompanion(size: String): CompanionAdSlot? {
        val _size = size.split("x")
        if(_size.size == 2) {
            try {
                val width = _size[0].toInt()
                val height = _size[1].toInt()
                val companionAdSlots = mxMediaSdkConfig.companionAdSlots
                companionAdSlots?.forEach {
                    if(it.width == width && it.height == height) {
                        return it
                    }
                }
            } catch (e: Exception) {}
        }
        return null
    }

    fun release(){
        companionSdkScope.cancel()
    }
}