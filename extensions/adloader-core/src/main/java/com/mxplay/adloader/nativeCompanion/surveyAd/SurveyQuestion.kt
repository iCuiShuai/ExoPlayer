package com.mxplay.adloader.nativeCompanion.surveyAd

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SurveyQuestion (
        @SerializedName("value") @Expose val value: String
)