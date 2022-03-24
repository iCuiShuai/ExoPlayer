package com.mxplay.adloader.nativeCompanion.surveyAd

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SurveyAnswerResponse(
        @SerializedName("multiChoiceAnswerIds") @Expose val multiChoiceAnswers: List<String>? = null,
        @SerializedName("paragraphAnswer") @Expose val paragraphAnswer: String? = null
)