package com.mxplay.adloader.nativeCompanion.expandable.data

import android.webkit.URLUtil
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
open class TemplateData(
    @SerializedName("imageCdnUrl") val imageCdnUrl: String,
    @SerializedName("logo") val logo: String?,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("CTA") val CTA: String?,
    @SerializedName("clickThroughUrl") val clickThroughUrl: String?,
    @SerializedName("clickTracker") val clickTracker: List<String>?,
    @SerializedName("impressionTracker") val impressionTracker: List<String>?,
    @SerializedName("adId") val adId: String,
    @SerializedName("campaignId") val campaignId: String?,
    @SerializedName("campaignName") val campaignName: String?,
    @SerializedName("creativeId") val creativeId: String,
    @SerializedName("templateId") val templateId: String
) {
    companion object{
        fun stitchUrl(baseUrl : String, url : String?) : String?{
            if (url != null){
                if (!URLUtil.isNetworkUrl(url)){
                    return baseUrl + url
                }
            }
            return url
        }
    }

    fun logoUrl(): String? {
        return stitchUrl(imageCdnUrl, logo)
    }

    fun getTrackingData() = CompanionTrackingInfo(adId, campaignId, campaignName, creativeId, templateId)

}