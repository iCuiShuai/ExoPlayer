package com.mxplay.adloader.nativeCompanion.surveyAd

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import ccom.mxplay.adloader.R

class SurveyInputDialog(private val context: Context, question: String, answer: String, private val callback: SurveyCompanionRenderer.SurveyInputDialogCallback) {

    private val dialog: Dialog = Dialog(context)
    private val questionTv: TextView
    private val answerTv: TextView
    private val warningTv: TextView
    private val charLimitTv: TextView
    private val submitBtn: TextView
    private val cancelBtn: TextView

    companion object {
        private const val ANSWER_CHARACTER_LIMIT = 200
    }

    init {
        dialog.setContentView(R.layout.survey_input_dialog)
        questionTv = dialog.findViewById(R.id.survey_question)
        answerTv = dialog.findViewById(R.id.suvery_answer)
        warningTv = dialog.findViewById(R.id.survey_warning)
        charLimitTv = dialog.findViewById(R.id.survey_char_limit)
        submitBtn = dialog.findViewById(R.id.survey_submit_btn)
        cancelBtn = dialog.findViewById(R.id.survey_cancel_btn)

        questionTv.text = question
        if (!TextUtils.isEmpty(answer)) {
            answerTv.text = answer
            charLimitTv.text = getCharacterLimitString(context, R.string.character_limit, answer.length, ANSWER_CHARACTER_LIMIT)
        } else {
            charLimitTv.text = getCharacterLimitString(context, R.string.character_limit, 0, ANSWER_CHARACTER_LIMIT)
        }

        answerTv.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                val answerText = s.toString()
                if (answerText.contains(",")) warningTv.visibility = View.VISIBLE
                else warningTv.visibility = View.GONE
                charLimitTv.text = getCharacterLimitString(context, R.string.character_limit, answerText.length, ANSWER_CHARACTER_LIMIT)
            }
        })

        submitBtn.setOnClickListener {
            onSubmitResponse()
        }

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }


        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.show()
    }

    fun onSubmitResponse() {
        val answerText = answerTv.text.toString()
        if (!TextUtils.isEmpty(answerText) && (answerText.contains(",") || answerText.length > 200)) {
            Toast.makeText(context, "Invalid input", Toast.LENGTH_SHORT).show()
        } else {
            dialog.dismiss()
            callback.onAnswerSubmit(answerText)
        }
    }

    private fun getCharacterLimitString(context: Context, formatId: Int, currLength: Int, charLimit: Int): String {
        return context.resources.getString(formatId,
                currLength.toString(),
                charLimit.toString())
    }
}