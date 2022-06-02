package com.mxplay.adloader.nativeCompanion.surveyAd.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SurveyAnswer(
        @SerializedName("type") @Expose val type: String,
        @SerializedName("options") @Expose val options: List<SurveyOption>
)