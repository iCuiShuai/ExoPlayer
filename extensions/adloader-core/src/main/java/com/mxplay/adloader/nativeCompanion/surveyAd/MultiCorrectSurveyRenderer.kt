package com.mxplay.adloader.nativeCompanion.surveyAd

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAdsResponse
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAnswer
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAnswerResponse
import com.mxplay.interactivemedia.api.CompanionAdSlot
import kotlin.math.min

class MultiCorrectSurveyRenderer(
        companionAdSlot: CompanionAdSlot,
        private val surveyAdsResponse: SurveyAdsResponse,
        private var listener: SurveyRendererListener?
): SurveyRenderer() {

    private val context = companionAdSlot.getContainer()?.context
    private var selectedPosition = mutableSetOf<Int>()

    override fun getSurveyAnswer(): SurveyAnswerResponse? {
        val answer = surveyAdsResponse.getQuery()?.answer
        return if (selectedPosition.isNotEmpty() && answer != null) {
            SurveyAnswerResponse((answer.options.filterIndexed { index, surveyOption -> selectedPosition.contains(index)}).map { it.id })
        } else {
            Toast.makeText(context, "Empty response", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun release() {
    }

    override fun renderSurveyAnswerView(adView: View, layoutInflater: LayoutInflater) {
        val optionView: TableLayout? = adView.findViewById(R.id.survey_options_grid)
        val answerView: TextView? = adView.findViewById(R.id.suvery_answer)
        val answer = surveyAdsResponse.getQuery()?.answer

        answerView?.visibility = View.GONE
        optionView?.let {
            it.visibility = View.VISIBLE
            setUpOptionView(it, answer, layoutInflater)
        }
    }

    private fun setUpOptionView(optionView: TableLayout, answer: SurveyAnswer?, layoutInflater: LayoutInflater) {
        optionView.visibility = View.VISIBLE
        optionView.removeAllViews()
        val num = min(answer?.options?.size ?: 0, 4)
        val colCount = 2
        val rows = mutableListOf<TableRow>()

        var r = 0
        var c = 0

        val emptyRow = TableRow(context)
        rows.add(emptyRow)

        for (i in 0 until num) {
            if (c == colCount) {
                r++
                c = 0
                val _emptyRow = TableRow(context)
                rows.add(_emptyRow)
            }
            val viewForPosition = getView(i, answer, layoutInflater)
            rows[r].addView(viewForPosition)
            c++
        }
        rows.forEach {
            optionView.addView(it)
        }
    }

    private fun getView(position: Int, answer: SurveyAnswer?, layoutInflater: LayoutInflater): View {
        val root = layoutInflater.inflate(R.layout.survey_grid_layout, null, false) as ViewGroup
        val radioBtn: RadioButton? = root.findViewById(R.id.survey_radio_btn)
        val checkBoxLayout: View? = root.findViewById(R.id.ll_survey_check_box_button)
        val checkBox: CheckBox? = root.findViewById(R.id.survey_check_box)
        val checkBoxTv: TextView? = root.findViewById(R.id.survey_check_box_tv)

        if (checkBoxLayout != null && checkBox != null) {
            checkBoxLayout.visibility = View.VISIBLE
            checkBoxTv?.text = answer?.options?.get(position)?.name ?: ""
            checkBoxLayout.setOnClickListener {
                val isChecked = checkBox.isChecked
                checkBox.isChecked = !isChecked
            }
            checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
                onCheckBoxClicked(isChecked, position)
            }
        }

        radioBtn?.visibility = View.GONE
        return root
    }

    private fun onCheckBoxClicked(isChecked: Boolean, position: Int) {
        if(isChecked) selectedPosition.add(position)
        else selectedPosition.remove(position)

        val isSubmitEnable = listener?.isSubmitEnable() ?: false
        listener?.enableSubmit(isSubmitEnable)
    }
}