package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.internal.tracking.ITrackersProvider
import java.util.*

class CompanionAdData : CompanionAd , ITrackersProvider {
    var clickUrl: String? = null
    var id : String? = null
    var _width: Int = 0
    var _height: Int = 0
    var staticResource : StaticResource? = null
    var htmlResource: HTMLResource? = null
    var resourceType: String = TAG_NO_RESOURCE
    var trackingEvents: MutableMap<EventName, TrackingEvent>? = null


    class StaticResource{
        var creativeType: String? =null
        var url: String? = null
    }

    class HTMLResource {
        var url: String? = null
    }

    companion object{
        const val TAG_STATIC_RESOURCE = "StaticResource"
        const val TAG_HTML_RESOURCE = "HTMLResource"
        const val TAG_NO_RESOURCE = "NoResouceFound"
    }

    override fun getResourceValue(): String {
        return when(resourceType) {
            TAG_STATIC_RESOURCE -> staticResource!!.url!!
            TAG_HTML_RESOURCE -> htmlResource!!.url!!
            else -> TAG_NO_RESOURCE
        }
    }

    override fun getHeight(): Int {
        return _height
    }

    override fun getWidth(): Int {
        return _width
    }

    override fun getApiFramework(): String {
        return ""
    }

    fun getClickThrough(): String {
        return clickUrl ?: ""
    }

    override fun provideTrackingEvent(): Map<EventName, MutableList<TrackingEvent>> {
        val eventsMapping =  EnumMap<EventName, MutableList<TrackingEvent>>(EventName::class.java)
        trackingEvents?.forEach{
            eventsMapping[it.key] = mutableListOf(it.value)
        }
        return eventsMapping
    }

    override fun provideAdVerifiers(): List<AdVerification>? {
        return null
    }
}