package com.mxplay.adloader.nativeCompanion.expandable.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class BigBannerTemplateData(
    CTA: String?,
    adId: String,
    @SerializedName("ads")  val ads: List<Ad>,
    campaignId: String,
    campaignName: String,
    clickThroughUrl: String?,
    clickTracker: List<String>?,
    creativeId: String,
    ctaUrl: String,
    description: String,
    imageCdnUrl: String,
    impressionTracker: List<String>?,
    logo: String?,
    @SerializedName("row") val row: Int?,
    @SerializedName("size")  val size: String,
    @SerializedName("slot")  val slot: String?,
    templateId: String,
    title: String,
    @SerializedName("type")  val type: String,
) : TemplateData(imageCdnUrl, logo, title, description, CTA, clickThroughUrl, clickTracker, impressionTracker, adId, campaignId, campaignName, creativeId, templateId){






}