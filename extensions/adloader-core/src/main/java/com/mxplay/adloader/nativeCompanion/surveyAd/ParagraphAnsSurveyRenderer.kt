package com.mxplay.adloader.nativeCompanion.surveyAd

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAdsResponse
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAnswerResponse
import com.mxplay.interactivemedia.api.CompanionAdSlot

class ParagraphAnsSurveyRenderer(
        companionAdSlot: CompanionAdSlot,
        private val surveyAdsResponse: SurveyAdsResponse,
        private var listener: SurveyRendererListener? = null
) : SurveyRenderer() {

    private var context = companionAdSlot.container.context
    private var answerView: TextView? = null
    var inputDialog: SurveyInputDialog? = null

    override fun getSurveyAnswer(): SurveyAnswerResponse? {
        return if (!TextUtils.isEmpty(answerView?.text)) {
            SurveyAnswerResponse(paragraphAnswer = answerView?.text!!.toString())
        } else {
            Toast.makeText(context, "Empty Response", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun renderSurveyAnswerView(adView: View, layoutInflater: LayoutInflater) {
        context = adView.context
        val optionView: TableLayout? = adView.findViewById(R.id.survey_options_grid)
        answerView = adView.findViewById(R.id.suvery_answer)
        answerView?.let { ans ->
            ans.visibility = View.VISIBLE
            ans.setOnClickListener {
                inputDialog = SurveyInputDialog(context, surveyAdsResponse.getQuery()?.question?.value ?: "",
                        if (TextUtils.isEmpty(answerView?.text)) "" else answerView?.text.toString(),
                        object : SurveyCompanion.SurveyInputDialogCallback {
                            override fun onAnswerSubmit(answer: String) {
                                (it as? TextView)?.text = answer
                                listener?.enableSubmit(!TextUtils.isEmpty(answer))
                                listener?.submitSurvey()
                            }
                        }
                )
            }
        }
        optionView?.visibility = View.VISIBLE
    }

    override fun release() {
        inputDialog?.release()
    }
}