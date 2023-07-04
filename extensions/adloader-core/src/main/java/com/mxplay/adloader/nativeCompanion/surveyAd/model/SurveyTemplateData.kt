package com.mxplay.adloader.nativeCompanion.surveyAd.model

import com.google.gson.annotations.SerializedName
import com.mxplay.adloader.nativeCompanion.expandable.data.TemplateData

class SurveyTemplateData(
        imageCdnUrl: String,
        logo: String?,
        title: String,
        description: String,
        CTA: String?,
        clickThroughUrl: String?,
        clickTracker: List<String>?,
        impressionTracker: List<String>?,
        adId: String,
        campaignId: String?,
        campaignName: String?,
        creativeId: String,
        templateId: String,
        type: String,
        @SerializedName("surveyId") val surveyId: String,
        @SerializedName("SurveyManagementServerURL") val surveyManagementServerURL: String,
        @SerializedName("size") val size: String,
        @SerializedName("slot") val slot: String?,
        advertiser: String?) :
        TemplateData(imageCdnUrl, logo, title,advertiser, description, CTA, clickThroughUrl, clickTracker, impressionTracker, adId, campaignId, campaignName, creativeId, templateId, type) {
}