package com.mxplay.adloader.nativeCompanion.expandable.data

import android.webkit.URLUtil
import androidx.annotation.Keep
import com.google.android.gms.common.images.Size
import com.google.gson.annotations.SerializedName

@Keep
open class TemplateData(
    @SerializedName("imageCdnUrl") val imageCdnUrl: String,
    @SerializedName("logo") val logo: String?,
    @SerializedName("title") val title: String,
    @SerializedName("advertiser") val advertiser: String? = null,
    @SerializedName("description") val description: String,
    @SerializedName("CTA") val CTA: String?,
    @SerializedName("clickThroughUrl") val clickThroughUrl: String?,
    @SerializedName("clickTracker") val clickTracker: List<String>?,
    @SerializedName("impressionTracker") val impressionTracker: List<String>?,
    @SerializedName("adId") val adId: String,
    @SerializedName("campaignId") val campaignId: String?,
    @SerializedName("campaignName") val campaignName: String?,
    @SerializedName("creativeId") val creativeId: String,
    @SerializedName("templateId") val templateId: String,
    @SerializedName("type") val type: String?,
    @SerializedName("item_aspect_ratio") private val itemAspectRatio: String? = null
) {


    companion object{
        fun stitchUrl(baseUrl : String?, url : String?) : String?{
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

    fun getTrackingData() = CompanionTrackingInfo(adId, campaignId, campaignName, creativeId, templateId, type)

    fun getItemAspectRatio(): Size?{
        val result =  kotlin.runCatching { itemAspectRatio?.let {
            val split = itemAspectRatio.split("x".toRegex())
            Size(split.first().toInt(), split.last().toInt())}
        }
        return result.getOrNull()
    }
}