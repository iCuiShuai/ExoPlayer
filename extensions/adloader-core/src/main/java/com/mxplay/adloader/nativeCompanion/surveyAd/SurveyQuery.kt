package com.mxplay.adloader.nativeCompanion.surveyAd

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SurveyQuery(
        @SerializedName("id") @Expose val id: String,
        @SerializedName("question") @Expose val question: SurveyQuestion,
        @SerializedName("answer") @Expose val answer: SurveyAnswer
)