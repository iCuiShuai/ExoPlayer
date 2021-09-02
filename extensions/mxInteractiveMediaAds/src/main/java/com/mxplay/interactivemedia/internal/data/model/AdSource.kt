package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.Ad

class AdSource(val id : String, val allowMultiple : Boolean = true, val followRedirect : Boolean = true) : AdTagUriHost {


    var adTagUri: String? = null
    var vastData : VASTModel? = null
    var adsCount : Int = 0
    var adsList : List<Ad> = mutableListOf()

    companion object{
        const val FOLLOW_REDIRECT_ATTR = "followRedirects"
        const val MULTIPLE_ADS_ATTR = "allowMultipleAds"
        const val ID = "id"
        const val AD_TAG_URI_XML_TAG_V1 = "AdTagURI"
        const val AD_TAG_URI_XML_TAG_V2 = "vmap:AdTagURI"
    }

    override fun getPendingAdTagUriHost(): AdTagUriHost? {
        if (!followRedirect) return null
        return if (adTagUri != null && vastData == null) this
        else if (vastData != null) vastData!!.getPendingAdTagUriHost()
        else null
    }
    override fun getPendingAdTagUri(): String?{
        return adTagUri
    }

    override fun handleAdTagUriResult(vastModel: VASTModel?) {
        if (vastModel != null) {
            this.vastData = vastModel
            if (!allowMultiple) {
                this.vastData!!.disallowMultiple()
            }
            if (!isFallBackOnNoAd()) {
                this.vastData!!.disallowFallBack()
            }
        } else {
            adTagUri = null
        }
    }

    override fun isFallBackOnNoAd(): Boolean {
        return true
    }

    fun getAds(): List<Ad> {
        return adsList
    }

    fun refreshAds() {
        vastData?.let {
            adsList = it.getMediaAds()
        }

        adsCount = if (vastData != null) vastData!!.getAdsCount() else if (adTagUri != null) 1 else 0
    }

    fun updatePodInfo(podIndex: Int, startTimeSec: Long, adPosition : Int) : Int {
        return if(vastData != null) vastData!!.updatePodInfo(podIndex, startTimeSec, adPosition) else 0
    }

}