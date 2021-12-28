package com.mxplay.adloader

import com.google.ads.interactivemedia.v3.api.Ad
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.toMxAd
import com.mxplay.interactivemedia.api.toMxAdEvent
import java.util.*

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
            ad = adEvent.ad.toMxAd()
            imaAdToMxAdMap[adEvent.ad] = ad
        }
        eventListener?.onAdEvent(adEvent.toMxAdEvent(ad, adEvent.adData))
    }

    fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> {
        return object : LinkedHashMap<K, V>(maxSize * 4 / 3, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<K, V>?): Boolean {
                return this.size > maxSize
            }
        }
    }
}

