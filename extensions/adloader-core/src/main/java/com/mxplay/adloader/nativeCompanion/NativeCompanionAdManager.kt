package com.mxplay.adloader.nativeCompanion

import android.text.TextUtils
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.expandable.ExpandableNativeCompanion
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

class NativeCompanionAdManager(val adsBehaviour: AdsBehaviour?,
                               private val mxMediaSdkConfig: MxMediaSdkConfig,
                               private val listener: NativeCompanion.NativeCompanionListener,
                               private val resourceProvider: CompanionResourceProvider
):
        AdEvent.AdEventListener {

    val ioOpsScope = CoroutineScope(SupervisorJob() + mxMediaSdkConfig.ioDispatcher)
    val remoteDataSource = RemoteDataSource(mxMediaSdkConfig, UrlStitchingService(mxMediaSdkConfig))
    private var nativeCompanion : NativeCompanion? = null
    private val eventsTracker : EventsTracker by lazy {
        EventsTracker(adsBehaviour, remoteDataSource, ioOpsScope)
    }

    companion object {
        const val TAG = "NativeCompanionManager"
        const val NATIVE_AD_CONFIG = "nativeAdConfig"
        const val JSON = "{\n" +
                "    \"type\": \"expandable\",\n" +
                "    \"templateId\": \"UNI_IMAGE_TEMPLATE\",\n" +
                "  \"slot\": \"<refer table below>\", \n" +
                "    \"size\": \"320x250\", \n" +
                "    \"title\": \"Tata Punch\", \n" +
                "    \"advertiser\": \"Tata Motors\",\n" +
                "    \"logo\": \"https://www.indiantelevision.com/sites/default/files/images/tv-images/2020/12/19/tata_motors.jpg\",\n" +
                "    \"image\": \"https://cars.tatamotors.com/images/punch/gallery/punch-front-view-banner.jpg\",\n" +
                "    \"description\": \"Introducing Tata's new compact SUV\",\n" +
                "    \"CTA\": \"Learn More\",\n" +
                "    \"clickThroughUrl\": \"http://www.youtube.com/\",\n" +
                "    \"impressionTracker\": \"http://www.youtube.com/impression\",\n" +
                "    \"clickTracker\": \"http://www.youtube.com/trackclick\",\n" +
                "    \"adId\": \"<ad id>\",\n" +
                "    \"creativeId\": \"< creativeId >\",\n" +
                "    \"campaignId\": \"<campaign id>\",\n" +
                "    \"campaignName\": \"<campaignName>\"\n" +
                "}\n"
    }

    override fun onAdEvent(adEvent: AdEvent) {
        val ad = adEvent.ad
        if(adEvent.type == AdEvent.AdEventType.STARTED && ad != null) {
            checkAndLoadNativeCompanion(ad.getTraffickingParameters())
        }
        nativeCompanion?.onAdEvent(adEvent)
    }


    private fun checkAndLoadNativeCompanion(adParameters: String?) {
        val adParameterMap = extractParamsFromString(adParameters)
        if(adParameterMap != null) {
            parseNativeCompanionType(adParameterMap)?.let {
                nativeCompanion = it
                it.loadCompanion()
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
        val json = JSONObject(config)

        val companionAdSlot = pickBestCompanion(json.optString("size")) ?: return null

        return when(json.optString("type")) {
            NativeCompanion.NativeCompanionType.SURVEY_AD.value -> {
                return SurveyNativeCompanion(json, companionAdSlot, ioOpsScope, remoteDataSource, NativeCompanion.NativeCompanionType.SURVEY_AD, listener)
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

}