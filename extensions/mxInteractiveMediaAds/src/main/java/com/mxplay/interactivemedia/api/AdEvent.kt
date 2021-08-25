
package com.mxplay.interactivemedia.api

interface AdEvent {
    val type: AdEventType
    val ad: Ad?
    val adData: Map<String?, String?>?

    enum class AdEventType {
        ALL_ADS_COMPLETED, AD_BREAK_FETCH_ERROR, CLICKED, COMPLETED, CUEPOINTS_CHANGED, CONTENT_PAUSE_REQUESTED, CONTENT_RESUME_REQUESTED, FIRST_QUARTILE, LOG, AD_BREAK_READY, MIDPOINT, PAUSED, RESUMED, SKIPPABLE_STATE_CHANGED, SKIPPED, STARTED, TAPPED, ICON_TAPPED, ICON_FALLBACK_IMAGE_CLOSED, THIRD_QUARTILE, LOADED, AD_PROGRESS, AD_BUFFERING, AD_BREAK_STARTED, AD_BREAK_ENDED, AD_PERIOD_STARTED, AD_PERIOD_ENDED, CREATIVE_VIEW
    }

    interface AdEventListener {
        fun onAdEvent(adEvent: AdEvent)
    }

    interface AdEventVerificationListener {
        fun onAdEventVerification(adEvent: AdEvent)
    }
}