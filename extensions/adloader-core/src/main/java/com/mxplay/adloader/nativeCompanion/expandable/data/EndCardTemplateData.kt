package com.mxplay.adloader.nativeCompanion.expandable.data

import com.google.gson.annotations.SerializedName

class EndCardTemplateData(
    CTA: String?,
    adId: String,
    @SerializedName("endCardDuration") val endCardDuration: Int?,
    @SerializedName("ads") val ads: List<Ad>,
    campaignId: String,
    campaignName: String,
    clickThroughUrl: String?,
    clickTracker: List<String>?,
    creativeId: String,
    description: String,
    imageCdnUrl: String,
    impressionTracker: List<String>?,
    logo: String?,
    @SerializedName("row") val row: Int?,
    @SerializedName("size") val size: String,
    @SerializedName("slot") val slot: String?,
    templateId: String,
    title: String,
    type: String,
    advertiser: String?,
) : TemplateData(imageCdnUrl, logo, title, advertiser,description, CTA, clickThroughUrl, clickTracker, impressionTracker, adId, campaignId, campaignName, creativeId, templateId, type)