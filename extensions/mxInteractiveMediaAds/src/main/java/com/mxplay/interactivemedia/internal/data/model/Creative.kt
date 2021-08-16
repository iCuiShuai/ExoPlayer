
package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.internal.tracking.ITrackersProvider
import java.util.*

open class Creative(val id : String) : ITrackersProvider{
     var trackingEvents: MutableMap<EventName, TrackingEvent>? = null


    /** creative sequence **/
    var sequence: String? = null


    companion object {
        const val CREATIVE_XML_TAG = "Creative"
        const val LINEAR_XML_TAG = "Linear"
        const val TAG_COMPANIONS_ADS = "CompanionAds"

        const val ID_XML_ATTR = "id"
        const val SEQUENCE_XML_ATTR = "sequence"

    }

    override fun provideTrackingEvent(): Map<EventName, MutableList<TrackingEvent>>? {
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
