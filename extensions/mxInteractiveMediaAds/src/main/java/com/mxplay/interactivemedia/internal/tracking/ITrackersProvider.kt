package com.mxplay.interactivemedia.internal.tracking

import com.mxplay.interactivemedia.internal.data.model.AdVerification
import com.mxplay.interactivemedia.internal.data.model.EventName
import com.mxplay.interactivemedia.internal.data.model.TrackingEvent

interface ITrackersProvider {
    fun provideTrackingEvent(): Map<EventName, MutableList<TrackingEvent>>?
    fun provideAdVerifiers(): List<AdVerification>?
}