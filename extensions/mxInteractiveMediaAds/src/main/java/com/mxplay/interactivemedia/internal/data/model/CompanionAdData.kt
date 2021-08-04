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
    var trackingEvents: MutableMap<EventName, TrackingEvent>? = null


    class StaticResource{
        var creativeType: String? =null
        var url: String? = null
    }

    companion object{
        const val TAG_STATIC_RESOURCE = "StaticResource"

    }

    override fun getResourceValue(): String {
        return staticResource!!.url!!
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