package com.mxplay.adloader.nativeCompanion

import android.text.TextUtils
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.VideoAdsTracker
import com.mxplay.adloader.nativeCompanion.expandable.EndCardCompanion
import com.mxplay.adloader.nativeCompanion.expandable.PlayerBottomCompanion
import com.mxplay.adloader.nativeCompanion.surveyAd.SurveyCompanion
import com.mxplay.interactivemedia.api.*
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.util.UrlStitchingService
import com.mxplay.logger.ZenLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import java.net.URLDecoder
import java.util.*

class NativeCompanionAdManager(val tracker: VideoAdsTracker, val adsBehaviour: AdsBehaviour?,
                               private val mxMediaSdkConfig: MxMediaSdkConfig,
                               private val resourceProvider: CompanionResourceProvider
):
        AdEvent.AdEventListener {

    private val companionSdkScope = CoroutineScope(SupervisorJob() +  Dispatchers.Main)
    private val urlStitchingService = UrlStitchingService(mxMediaSdkConfig)
    private val remoteDataSource = RemoteDataSource(mxMediaSdkConfig, urlStitchingService )
    private var nativeCompanions : MutableMap<Ad, LinkedList<NativeCompanion>> = mutableMapOf()
    private val eventsTracker : EventsTracker by lazy {
        EventsTracker(tracker, urlStitchingService, remoteDataSource, companionSdkScope)
    }

    companion object {
        const val TAG = "NativeCompanionManager"
        const val NATIVE_AD_CONFIG = "nativeAdConfigV2"
    }

    override fun onAdEvent(adEvent: AdEvent) {
        val ad = adEvent.ad
        if(adEvent.type == AdEvent.AdEventType.LOADED && ad != null) {
            checkAndLoadNativeCompanion(ad, ad.getTraffickingParameters(), adEvent.ad!!.getAdPodInfo())
        }else if (adEvent.type == AdEvent.AdEventType.STARTED && ad != null){
            nativeCompanions.get(ad)?.forEach { it.display()}
        }
        else if (adEvent.type == AdEvent.AdEventType.COMPLETED || adEvent.type == AdEvent.AdEventType.SKIPPED){
            adsBehaviour?.setNativeCompanionAdInfo(null)
            val iterator = nativeCompanions[ad]?.iterator()
            if (iterator != null){
                while (iterator.hasNext()){
                    val companion = iterator.next()
                    companion.onAdEvent(adEvent)
                    companion.release()
                    iterator.remove()
                }
            }

        }else if (adEvent.type == AdEvent.AdEventType.ALL_ADS_COMPLETED || adEvent.type == AdEvent.AdEventType.CONTENT_RESUME_REQUESTED){
            releaseNativeCompanions(adEvent)
            adsBehaviour?.setNativeCompanionAdInfo(null)
        }
        nativeCompanions[ad]?.forEach{it.onAdEvent(adEvent)}

    }

    private fun releaseNativeCompanions(adEvent: AdEvent?) {
        nativeCompanions.forEach { list ->
            val listIterator = list.value.listIterator()
            while (listIterator.hasNext()) {
                val nativeCompanion = listIterator.next()
                adEvent?.let {
                    nativeCompanion.onAdEvent(it)
                }
                nativeCompanion.release()
                listIterator.remove()
            }
        }
    }


    private fun checkAndLoadNativeCompanion(ad : Ad, adParameters: String?, adPodInfo: AdPodInfo) {
        try {
            val adParameterMap = extractParamsFromString(adParameters)
            adParameterMap?.get(NATIVE_AD_CONFIG)?.forEach { config ->
                parseNativeCompanionType(ad, config)?.let {
                    it.preload()
                    adsBehaviour?.setNativeCompanionAdInfo(adPodInfo)
                    nativeCompanions.getOrPut(ad) { LinkedList<NativeCompanion>() }.add(it)
                }
            }
        } catch (e: Exception) {
            ZenLogger.et(TAG, e, "error parsing native companion")
        }
    }

    private fun extractParamsFromString(input: String?): HashMap<String, LinkedList<String>>? {
        try {
            if (TextUtils.isEmpty(input)) return null
            val map = HashMap<String, LinkedList<String>>()
            val subStrs = input!!.split("&".toRegex()).toTypedArray()
            for (subStr in subStrs) {
                val kv = subStr.split("=".toRegex()).toTypedArray()
                if (kv.size == 2) {
                    map.getOrPut(kv[0]) { LinkedList<String>() }.add(URLDecoder.decode(kv[1], "UTF-8"))
                }
            }
            return map
        } catch (ignore: Exception) {
        }
        return null
    }

    private fun parseNativeCompanionType(ad : Ad, config : String?): NativeCompanion? {
        if(TextUtils.isEmpty(config)) return null
        val json = JSONObject(config!!)

        val companionAdSlot = pickBestCompanion(json.optString("size")) ?: return null

        return when(json.optString("type")) {
            NativeCompanion.NativeCompanionType.SURVEY_AD.value -> {
                return SurveyCompanion.create(json, companionAdSlot, remoteDataSource, companionSdkScope, resourceProvider, eventsTracker, adsBehaviour)
            }
            NativeCompanion.NativeCompanionType.EXPANDABLE.value -> {
                return PlayerBottomCompanion.create(json, companionAdSlot, eventsTracker, resourceProvider)
            }
            NativeCompanion.NativeCompanionType.ENDCARD.value -> {
                return EndCardCompanion.create(json, companionAdSlot, eventsTracker, resourceProvider, adsBehaviour)
            }
            NativeCompanion.NativeCompanionType.NONE.value -> {
                return PlayerBottomCompanion.create(json, companionAdSlot, eventsTracker, resourceProvider)
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
        releaseNativeCompanions(null)
    }
}