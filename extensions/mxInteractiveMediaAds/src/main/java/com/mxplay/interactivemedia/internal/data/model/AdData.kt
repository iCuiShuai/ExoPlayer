package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.internal.tracking.ITrackersProvider
import java.util.*

open class AdData(val id : String)  : ITrackersProvider{


    var adverifications: List<AdVerification>? = null

    var parent : AdData? = null


    var errorUrls: MutableList<TrackingEvent>? = null

    var impressionUrls: MutableList<TrackingEvent>? = null


    /** list of creatives in the ad **/
    var creatives: List<Creative>? = null

    val linearCreatives : List<LinearCreative>? by lazy {
        (creatives?.filterIsInstance<LinearCreative>()) as List<LinearCreative>
    }


    val mediaCreative : LinearCreative? by lazy {
        (creatives?.find {it is LinearCreative && it.hasMedia() }) as LinearCreative
    }

    val _companionAds: List<CompanionAd>? by lazy {
        (creatives?.filterIsInstance<CompanionCreative>())?.flatMap { it.companionAds ?: emptyList() }?.also { it.plus(parent?._companionAds) }
    }



    companion object {
        // Ad xpath expression
        private const val TAG = "VastAdModel"
        const val AD = "Ad"
        const val ID = "id"
        const val SKIP_OFFSET = "skipoffset"
        const val INLINE_XML_TAG = "InLine"
        const val WRAPPER_XML_TAG = "Wrapper"

        const val CREATIVES_XML_TAG = "Creatives"
        const val CREATIVE_XML_TAG = "Creative"


        const val ERROR_XML_TAG = "Error"
        const val IMPRESSION_XML_TAG = "Impression"
        const val ADVERIFICATIONS = "AdVerifications"
        const val ID_XML_ATTR = "id"
        const val SEQUENCE_XML_ATTR = "sequence"

    }


    override fun provideAdVerifiers(): List<AdVerification>? {
        return adverifications
    }




    override fun provideTrackingEvent(): Map<EventName, MutableList<TrackingEvent>>? {
        val eventsMap = EnumMap<EventName, MutableList<TrackingEvent>>(EventName::class.java)
        eventsMap.put(EventName.ERROR, LinkedList<TrackingEvent>().apply {  if (errorUrls != null) this.addAll(errorUrls!!) }
                .apply { if (parent != null && parent!!.errorUrls != null) this.addAll(parent!!.errorUrls!!) })

        eventsMap.put(EventName.IMPRESSION, LinkedList<TrackingEvent>().apply {  if (impressionUrls != null) this.addAll(impressionUrls!!) }
                .apply { if (parent != null && parent!!.impressionUrls != null) this.addAll(parent!!.impressionUrls!!) })

        if (creatives != null) {
            linearCreatives!!.forEach {
                it.provideTrackingEvent()?.forEach { e -> eventsMap.getOrPut(e.key, { LinkedList<TrackingEvent>() }).addAll(e.value) }
            }
        }
        return eventsMap
    }


}