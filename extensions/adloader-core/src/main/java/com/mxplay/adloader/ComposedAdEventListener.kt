package com.mxplay.adloader

import com.google.ads.interactivemedia.v3.api.Ad
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.adloader.nativeCompanion.NativeCompanionAdManager
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.MxMediaSdkConfig
import com.mxplay.interactivemedia.api.toMxAd
import com.mxplay.interactivemedia.api.toMxAdEvent
import java.util.*

internal class ComposedAdEventListener : AdEvent.AdEventListener, com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener, NativeCompanion.NativeCompanionListener  {

    private val eventListeners = mutableListOf<AdEvent.AdEventListener>()
    private val imaAdToMxAdMap = lruCache<Ad, com.mxplay.interactivemedia.api.Ad>(3)
    private var nativeCompanioListener: NativeCompanion.NativeCompanionListener? = null

    fun registerNativeCompanionListener(listener: NativeCompanion.NativeCompanionListener?) {
        nativeCompanioListener = listener
    }

    fun registerMxMediaSdkConfig(mxMediaSdkConfig: MxMediaSdkConfig) {
        eventListeners.add(NativeCompanionAdManager(mxMediaSdkConfig, this))
    }

    fun registerEventListener(listener: AdEvent.AdEventListener?) {
        if(listener != null) {
            eventListeners.add(listener)
        }
    }


    override fun onAdEvent(adEvent: AdEvent) {
        eventListeners.forEach {
            it.onAdEvent(adEvent)
        }
    }

    override fun onAdEvent(adEvent: com.google.ads.interactivemedia.v3.api.AdEvent?) {
        eventListeners.forEach {
            if (adEvent == null || adEvent.type == null || it == null) return
            var ad = adEvent.ad?.let { imaAdToMxAdMap[adEvent.ad] }
            if (ad == null && adEvent.ad != null) {
                ad = adEvent.ad.toMxAd()
                imaAdToMxAdMap[adEvent.ad] = ad
            }
            it.onAdEvent(adEvent.toMxAdEvent(ad, adEvent.adData))
        }
    }

    fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> {
        return object : LinkedHashMap<K, V>(maxSize * 4 / 3, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<K, V>?): Boolean {
                return this.size > maxSize
            }
        }
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        nativeCompanioListener?.onVideoSizeChanged(width, height)
    }
}

