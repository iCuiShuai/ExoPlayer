package com.mxplay.adloader

class AdsBehaviourOffline(vastTimeOutInMs: Int, videoAdsTracker: VideoAdsTracker? = null, debug : Boolean = false) : AdsBehaviourDefault(vastTimeOutInMs, videoAdsTracker, debug) {
    override val trackerName: String
        get() = VideoAdsTracker.OFFLINE_AD_LOADER
}