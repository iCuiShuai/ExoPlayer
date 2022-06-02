package com.mxplay.adloader.nativeCompanion.surveyAd.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SurveyOption(
        @SerializedName("id") @Expose val id: String,
        @SerializedName("name") @Expose val name: String
)