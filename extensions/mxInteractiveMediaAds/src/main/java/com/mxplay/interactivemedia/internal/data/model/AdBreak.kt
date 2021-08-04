package com.mxplay.interactivemedia.internal.data.model

import androidx.annotation.StringDef
import com.google.android.exoplayer2.C
import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.internal.core.AdPodInfoImpl
import com.mxplay.interactivemedia.internal.data.model.AdBreak.BreakTypes.Companion.DISPLAY
import com.mxplay.interactivemedia.internal.data.model.AdBreak.BreakTypes.Companion.LINEAR
import com.mxplay.interactivemedia.internal.data.model.AdBreak.BreakTypes.Companion.NON_LINEAR
import com.mxplay.interactivemedia.internal.data.model.AdBreak.TimeOffsetTypes.Companion.END
import com.mxplay.interactivemedia.internal.data.model.AdBreak.TimeOffsetTypes.Companion.START
import com.mxplay.interactivemedia.internal.tracking.ITrackersProvider
import com.mxplay.interactivemedia.internal.util.DateTimeUtils.getTimeInMillis

class AdBreak : ITrackersProvider{


    companion object {
        const val BREAK_TYPE = "breakType"
        const val BREAK_ID = "breakId"

        const val ATTR_TIME_OFFSET = "timeOffset"
        const val AD_SOURCE = "vmap:AdSource"
        const val REPEAT_AFTER_ATTR = "repeatAfter"
        const val TRACKING = "vmap:Tracking"
        const val TRACKING_EVENTS = "vmap:TrackingEvents"

        const val PRE_ROLL_START_TIME = 0L
        const val POST_ROLL_START_TIME = -1L

    }


    @StringDef(BreakIds.PRE_ROLL, BreakIds.POST_ROLL)
    annotation class BreakIds {
        companion object {
            // different break types
            const val PRE_ROLL = "preroll"
            const val POST_ROLL = "postroll"
        }
    }


    @StringDef(LINEAR, NON_LINEAR, DISPLAY)
    annotation class BreakTypes {
        companion object {
            // different break types
            const val LINEAR = "linear"
            const val NON_LINEAR = "nonlinear"
            const val DISPLAY = "display"
        }
    }

    @StringDef(START, END)
    annotation class TimeOffsetTypes {
        companion object {
            const val START = "start"
            const val END = "end"
        }
    }

    fun parseTimeOffset(time: String): Long {
        var stime = C.TIME_UNSET
        try {
            stime = if (START == time) {
                PRE_ROLL_START_TIME
            } else if (END == time) {
                POST_ROLL_START_TIME
            } else {
                getTimeInMillis(time) / 1000
            }
        } catch (ignore: Exception) {
        }
        return stime
    }

    var trackingEvents: MutableMap<EventName, MutableList<TrackingEvent>>?=null
    var repeatAfter: String? = null
    var breakId: String? = null
    var startTimeSec: Long = PRE_ROLL_START_TIME
    var startTime: String? = null
    set(value) {
        field = value
        startTimeSec = parseTimeOffset(value!!)
    }
    var breakType: String? = null
    var totalAdsCount = 0
    var adsList = mutableListOf<Ad>()

    var podIndex = 0
    var adSource: MutableList<AdSource>  = mutableListOf()

    var hasUnplayedAds = true

    fun refreshAds() {
        adsList.clear()
        var adPosition = 1
        totalAdsCount = 0
        adSource.forEach {
            it.refreshAds()
            adPosition += it.updatePodInfo(podIndex, this.startTimeSec, adPosition)
            totalAdsCount += it.adsCount
            adsList.addAll(it.getAds())
        }
        adsList.forEach{
            (it.getAdPodInfo() as AdPodInfoImpl).totalAds = totalAdsCount
        }

    }



    fun getPendingAdTagUriHost(): AdTagUriHost? {
        for (src in adSource){
            return src.getPendingAdTagUriHost() ?: continue
        }
        return null
    }

    override fun provideTrackingEvent(): Map<EventName, MutableList<TrackingEvent>>? {
        return trackingEvents
    }

    override fun provideAdVerifiers(): List<AdVerification>? {
        return null
    }
}