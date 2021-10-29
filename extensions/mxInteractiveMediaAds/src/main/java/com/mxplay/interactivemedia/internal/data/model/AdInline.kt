package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.api.AdPodInfo
import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.internal.core.AdPodInfoImpl

class AdInline(id: String) : AdData(id), Ad, IMediaFilesProvider {


    var sequence : Int? = 1

    /** the ad system **/
    var adSystem: String? = null

    /** title of the ad **/
    var adTitle: String? = ""

    /** description of the ad **/
    var adDescription: String? = ""

    var advertiser: String? = ""

    var podInfo : AdPodInfo? = null

    var _adMediaInfo : AdMediaInfo?= null
    var _vastMediaHeight : Int? = null
    var _vastMediaWidth : Int? = null;

    override fun getDescription(): String {
        return adDescription ?: ""
    }


    companion object {
        const val AD_SYSTEM_XML_TAG = "AdSystem"
        const val AD_TITLE_XML_TAG = "AdTitle"
        const val DESCRIPTION_XML_TAG = "Description"
        const val ADVERTISER_XML_TAG = "Advertiser"
    }

    fun updatePodInfo(podIndex: Int, startTimeMs: Long, adPosition: Int) : Int {
        podInfo = AdPodInfoImpl().apply {
            this.podIndex = podIndex
            this.timeOffset = startTimeMs
            this.adPosition = adPosition
        }
        return  1
    }


    override fun isSkippable(): Boolean {
        return if (mediaCreative != null) mediaCreative!!.skipOffsetInSeconds > 0 else false
    }

    override fun getMediaInfo(): AdMediaInfo {
        return _adMediaInfo!!
    }

    override fun getDuration(): Long {
        return if (mediaCreative != null) mediaCreative!!.durationInSeconds else -1
    }

    override fun getAdvertiserName(): String? {
        return advertiser
    }

    override fun getSkipTimeOffset(): Long {
        return if (mediaCreative != null) mediaCreative!!.skipOffsetInSeconds else -1
    }

    override fun getAdId(): String {
        return id
    }

    override fun getSequence(): Int {
        return sequence!!
    }


    override fun getAdPodInfo(): AdPodInfo {
        return podInfo!!
    }

    override fun getTitle(): String {
        return adTitle ?: ""
    }

    override fun getCreativeId(): String? {
        return mediaCreative?.id
    }

    fun hasMedia(): Boolean {
        val mediaCount  = mediaCreative?.mediaFiles?.size
        return mediaCount != null && mediaCount > 0
    }

    override fun setAdMediaInfo(adMediaInfo: AdMediaInfo, width: Int?, height: Int?) {
        this._adMediaInfo = adMediaInfo
        this._vastMediaWidth = width
        this._vastMediaHeight = height
    }

    override fun getAdMediaInfo(): AdMediaInfo? {
        return _adMediaInfo
    }

    override fun getAllMedia(): List<MediaFile> {
        return mediaCreative!!.mediaFiles!!
    }

    override fun getCompanionAds(): List<CompanionAd>? {
        return _companionAds
    }

    override fun getVastMediaWidth(): Int {
        return _vastMediaWidth ?: 0
    }

    override fun getVastMediaHeight(): Int {
        return _vastMediaHeight ?: 0
    }


}


