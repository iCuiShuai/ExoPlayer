package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.Ad

class VASTModel() {

    var totalAds = 0
    var ads: List<AdData>? = null
    var errorUrls: MutableList<String>? = null
    var version: String? = null


    companion object {
        const val VERSION = "version"
        const val VAST = "VAST"
        const val AD_XML_TAG = "Ad"
        const val ERROR_XML_ATTR = "Error"
    }

    fun getPendingAdTagUriHost(): AdTagUriHost? {
        ads?.let {
            for (ad in ads!!) {
                if (ad is AdTagUriHost) {
                    return ad.getPendingAdTagUriHost() ?: continue
                }
            }
        }
        return null
    }


    fun getAdsCount(): Int {
        return totalAds
    }

    fun getMediaAds(): List<Ad> {
        totalAds = 0
        val inlineAds = mutableListOf<Ad>()
        var keepAdding = true
        if (ads != null){
            for (ad in ads!!){
                if (ad is AdInline && ad.hasMedia()) {
                    if (keepAdding) inlineAds.add(ad)
                    totalAds++
                } else if (ad is AdWrapper) {
                    val wrapperAds = ad.getAds()
                    if (wrapperAds.isEmpty()){
                        keepAdding = false
                    }else {
                        if (keepAdding) inlineAds.addAll(wrapperAds)
                    }
                    totalAds += ad.getAdsCount()
                }else{
                    keepAdding = false
                }
            }
        }

        inlineAds.sortWith { o1, o2 -> o1.getSequence().compareTo(o2.getSequence()) }
        return inlineAds
    }

    fun disallowMultiple() {
        if (ads != null && ads!!.isNotEmpty()){
            ads = mutableListOf(ads!![0])
        }
    }

    fun disallowFallBack() {
        if (ads != null && ads!!.isNotEmpty()){
            ads!!.forEach {
                if (it is AdWrapper) it.fallBackOnNoAd = false
            }
        }
    }

    fun updatePodInfo(podIndex: Int, startTimeSec: Long, adPosition: Int): Int {
        var count = 0
        ads?.forEach { it ->
            count += if (it is AdWrapper) it.updatePodInfo(podIndex, startTimeSec, adPosition + count) else ((it as AdInline).updatePodInfo(podIndex, startTimeSec, adPosition + count))
        }
        return count
    }


}