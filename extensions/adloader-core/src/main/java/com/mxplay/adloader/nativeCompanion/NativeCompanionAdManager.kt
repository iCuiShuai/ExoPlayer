package com.mxplay.adloader.nativeCompanion

import android.text.TextUtils
import com.mxplay.adloader.nativeCompanion.surveyAd.SurveyNativeCompanion
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.api.MxMediaSdkConfig
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.util.UrlStitchingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import java.util.HashMap

class NativeCompanionAdManager(private val mxMediaSdkConfig: MxMediaSdkConfig,
                               private val listener: NativeCompanion.NativeCompanionListener):
        AdEvent.AdEventListener, com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener {

    val ioOpsScope = CoroutineScope(SupervisorJob() + mxMediaSdkConfig.ioDispatcher)
    val remoteDataSource = RemoteDataSource(mxMediaSdkConfig, UrlStitchingService(mxMediaSdkConfig))

    companion object {
        const val TAG = "NativeCompanionManager"
        const val NATIVE_AD_CONFIG = "nativeAdConfig"
        const val JSON = "{\n" +
                "\t\"type\": \"survey\",\n" +
                "\t\"templateId\": \"SINGLE_CHOICE\",\n" +
                "\t\"slot\": \"<refer table below>\",\n" +
                "\t\"size\": \"300x250\",\n" +
                "\t\"title\": \"Tata Punch\",\n" +
                "\t\"advertiser\": \"Tata Motors\",\n" +
                "\t\"logo\": \"https://www.indiantelevision.com/sites/default/files/images/tv-images/2020/12/19/tata_motors.jpg\",\n" +
                "\t\"image\": \"https://cars.tatamotors.com/images/punch/gallery/punch-front-view-banner.jpg\",\n" +
                "\t\"description\": \"Introducing Tata's new compact SUV\",\n" +
                "\t\"surveyId\": \"6Oeug\",\n" +
                "\t\"SurveyManagementServerURL\": \"https://surveymanagement.dev.mxplay.com/v1/\",\n" +
                "\t\"CTA\": \"Learn More\",\n" +
                "\t\"clickThroughUrl\": \"http://www.youtube.com/\",\n" +
                "\t\"impressionTracker\": \"<Impression Tracker URL>\",\n" +
                "\t\"clickTracker\": \"<Click Tracker URL>\"\n" +
                "}"
    }

    override fun onAdEvent(adEvent: AdEvent) {
        val ad = adEvent.ad
        if(adEvent.type == AdEvent.AdEventType.STARTED && ad != null) {
            checkAndLoadNativeCompanion(ad.getTraffickingParameters())
        }
    }

    override fun onAdEvent(adEvent: com.google.ads.interactivemedia.v3.api.AdEvent?) {
        val ad = adEvent?.ad
        if(adEvent?.type == com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.STARTED && ad != null) {
            checkAndLoadNativeCompanion(ad.traffickingParameters)
        }
    }

    private fun checkAndLoadNativeCompanion(adParameters: String?) {
        val adParameterMap = extractParamsFromString(adParameters)
        if(adParameterMap != null) {
            val nativeCompanion = parseNativeCompanionType(adParameterMap)
            nativeCompanion?.loadCompanion()
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
                    map[kv[0]] = kv[1]
                }
            }
            return map
        } catch (ignore: Exception) {
        }
        return null
    }

    private fun parseNativeCompanionType(adParameter: Map<String, String>): NativeCompanion? {
        val config = adParameter[NATIVE_AD_CONFIG] ?: JSON //TODO SURVEY
        if(TextUtils.isEmpty(config)) return null
        val json = JSONObject(config!!)

        val companionAdSlot = pickBestCompanion(json.optString("size"))
        if(companionAdSlot == null) return null

        return when(json.optString("type")) {
            NativeCompanion.NativeCompanionType.SURVEY_AD.value -> {
                return SurveyNativeCompanion(json, companionAdSlot, ioOpsScope, remoteDataSource, NativeCompanion.NativeCompanionType.SURVEY_AD, listener)
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

}