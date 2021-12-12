package com.mxplay.adloader

import com.google.ads.interactivemedia.v3.api.Ad
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.internal.core.AdCoreImpl
import com.mxplay.interactivemedia.internal.core.toMxAdEvent
import com.mxplay.interactivemedia.internal.lruCache

internal class ComposedAdEventListener : AdEvent.AdEventListener, com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener  {

    var eventListener : AdEvent.AdEventListener? = null
    private val imaAdToMxAdMap = lruCache<Ad, com.mxplay.interactivemedia.api.Ad>(3)


    override fun onAdEvent(adEvent: AdEvent) {
        eventListener ?: return
        eventListener?.onAdEvent(adEvent)
    }

    override fun onAdEvent(adEvent: com.google.ads.interactivemedia.v3.api.AdEvent?) {
        if (adEvent == null || adEvent.type == null || eventListener == null) return
        var ad = adEvent.ad?.let { imaAdToMxAdMap[adEvent.ad] }
        if (ad == null && adEvent.ad != null) {
            ad = AdCoreImpl(adEvent.ad)
            imaAdToMxAdMap[adEvent.ad] = ad
        }
        eventListener?.onAdEvent(adEvent.toMxAdEvent(ad))
    }
}

