package com.mxplay.adloader.nativeCompanion.surveyAd

import android.view.LayoutInflater
import android.view.View
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAdsResponse
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAnswerResponse
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAnswerType
import com.mxplay.interactivemedia.api.CompanionAdSlot

abstract class SurveyRenderer {

    companion object {
        fun create(companionAdSlot: CompanionAdSlot, surveyAdsResponse: SurveyAdsResponse, listener: SurveyRendererListener? = null): SurveyRenderer? {
            val type = surveyAdsResponse.getQuery()?.answer?.type
            return when {
                SurveyAnswerType.MULTI_CHOICE.value == type -> {
                    MultiChoiceSurveyRenderer(companionAdSlot, surveyAdsResponse, listener)
                }
                SurveyAnswerType.PARAGRAPH.value == type -> {
                    ParagraphAnsSurveyRenderer(companionAdSlot, surveyAdsResponse, listener)
                }
                SurveyAnswerType.MULTI_CORRECT.value == type -> {
                    MultiCorrectSurveyRenderer(companionAdSlot, surveyAdsResponse, listener)
                }
                else -> {
                    null
                }
            }
        }
    }

    abstract fun renderSurveyAnswerView(adView: View, layoutInflater: LayoutInflater)
    abstract fun getSurveyAnswer(): SurveyAnswerResponse?
    abstract fun release()

    interface SurveyRendererListener {
        fun isSubmitEnable(): Boolean
        fun enableSubmit(isEnable: Boolean)
        fun submitSurvey()
    }
}