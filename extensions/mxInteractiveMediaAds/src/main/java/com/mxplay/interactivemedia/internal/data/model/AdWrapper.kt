package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.internal.tracking.ITrackersProvider

open class AdWrapper(id : String, var allowMultiple : Boolean  = false, var fallBackOnNoAd: Boolean = true) : AdData(id) , AdTagUriHost, ITrackersProvider{

    var adTagUri: String? = null
    var vastData : VASTModel? = null


    companion object{
        const val AD_TAG_URI = "VASTAdTagURI"
        const val FAlLBACK_ON_NO_AD = "fallbackOnNoAd"
    }


    fun updatePodInfo(podIndex: Int, startTimeSec: Long, adPosition: Int) : Int {
        return  if(vastData != null)vastData!!.updatePodInfo(podIndex, startTimeSec, adPosition) else 0
    }


    override fun getPendingAdTagUriHost(): AdTagUriHost? {
        return if (adTagUri != null && vastData == null) this
        else if (vastData != null) vastData!!.getPendingAdTagUriHost()
        else null
    }

    override fun getPendingAdTagUri(): String? {
        return adTagUri
    }

    override fun handleAdTagUriResult(vastModel: VASTModel?) {
        if (vastModel != null) {
            this.vastData = vastModel
            this.vastData!!.ads?.forEach { ad ->
                ad.parent = this
            }
            if (!allowMultiple) {
                this.vastData!!.disallowMultiple();
            }
            if (!isFallBackOnNoAd()) {
                this.vastData!!.disallowFallBack()
            }
        } else {
            adTagUri = null
        }
    }

    override fun isFallBackOnNoAd(): Boolean {
        return fallBackOnNoAd
    }

    fun getAdsCount() : Int{
        return if (vastData != null) vastData!!.getAdsCount() else 1
    }

    fun getAds(): List<Ad> {
        val  inlineAds = mutableListOf<Ad>()
        vastData?.let{
            inlineAds.addAll(vastData!!.getMediaAds())
        }
        return inlineAds
    }


}