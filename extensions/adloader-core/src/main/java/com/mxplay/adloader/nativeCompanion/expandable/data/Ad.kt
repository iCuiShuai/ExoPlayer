package com.mxplay.adloader.nativeCompanion.expandable.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Ad(
    @SerializedName("CTA") val CTA: String?,
    @SerializedName("advertiser") val advertiser: String,
    @SerializedName("clickThroughUrl") val clickThroughUrl: String,
    @SerializedName("clickTracker") val clickTracker: List<String>,
    @SerializedName("image") private val image: String,
    @SerializedName("itemType") val itemType: String,
    @SerializedName("price") val price: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("id") val id: String?
) {

    companion object {
        const val TYPE_BASIC = "basic"
        const val TYPE_DETAILED = "detailed"
    }

    fun bannerUrl(imageCdnUrl: String): String? {
        return TemplateData.stitchUrl(imageCdnUrl, image)
    }
}