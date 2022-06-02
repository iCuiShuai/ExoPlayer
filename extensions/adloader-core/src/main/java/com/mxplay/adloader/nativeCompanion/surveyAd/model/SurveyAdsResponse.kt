package com.mxplay.adloader.nativeCompanion.surveyAd.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class SurveyAdsResponse(
        @SerializedName("id") @Expose val id: String,
        @SerializedName("questionAndAnswers") @Expose val queries: List<SurveyQuery>) {

    companion object {
        const val MULTICHOICE = "MULTICHOICE"
        const val PARAGRAPH = "PARAGRAPH"
    }

    fun getQuery(): SurveyQuery? {
        if (!queries.isNullOrEmpty()) {
            return queries[0]
        }
        else return null
    }

    fun isEmpty(): Boolean {
        return queries.isNullOrEmpty()
    }
}