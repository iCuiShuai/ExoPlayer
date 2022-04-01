package com.mxplay.adloader.nativeCompanion.surveyAd

import android.content.Intent
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import ccom.mxplay.adloader.R
import com.google.android.material.snackbar.Snackbar
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.adloader.nativeCompanion.NativeCompanionAdManager
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class SurveyCompanionRenderer(private val json: JSONObject, private val companionAdSlot: CompanionAdSlot, private val eventsTracker: EventsTracker, private val adsBehaviour: AdsBehaviour?, private val companionSdkScope: CoroutineScope,
                              private val remoteDataSource: RemoteDataSource): NativeCompanion.NativeCompanionRenderer {

    private val container = companionAdSlot.container
    private val context = companionAdSlot.container.context
    private val layoutInflater = LayoutInflater.from(context)

    private var selectedBtn: RadioButton? = null
    private var selectedPosition = -1
    private var submitEnable = true
    private var isResponseSubmitted = false

    private var submitBtn: TextView? = null
    private var answerView: TextView? = null

    var surveyAdsResponse: SurveyAdsResponse? = null

    override fun render() : View?{
        container.removeAllViews()
        try {
            val root = layoutInflater.inflate(R.layout.native_survey_ads, container, false) as ViewGroup
            root.clipChildren = false
            root.clipToPadding = false

            Log.d(NativeCompanionAdManager.TAG, "start binding: ")
            bindView(root)
            Log.d(NativeCompanionAdManager.TAG, "end binding: ")

            container.addView(root)
            adsBehaviour?.onVideoSizeChanged(10, 8)
            return root
        } catch (e: Exception) {
            Log.e(NativeCompanionAdManager.TAG, "fat gya render: ${e.printStackTrace()} ")
        }
        return null
    }

    override fun release() {
        container.removeAllViews()
    }

    private fun bindView(adView: View) {
        val loadingView: View? = adView.findViewById(R.id.loading_view)
        val surveyContainer: View? = adView.findViewById(R.id.survey_container)

        if(surveyAdsResponse == null) {
            loadingView?.visibility = View.VISIBLE
            surveyContainer?.visibility = View.GONE
            return
        } else {
            loadingView?.visibility = View.GONE
            surveyContainer?.visibility = View.VISIBLE
        }
        val surveyQuery = surveyAdsResponse?.getQuery()
        val surveyAnswer = surveyQuery?.answer
        val type = surveyQuery?.answer?.type

        val iconView: ImageView? = adView.findViewById(R.id.native_ad_icon)
        val titleView: TextView? = adView.findViewById(R.id.native_ad_title)
        val questionView: TextView? = adView.findViewById(R.id.survey_question)
        val optionView: TableLayout? = adView.findViewById(R.id.survey_options_grid)
        answerView = adView.findViewById(R.id.suvery_answer)
        submitBtn = adView.findViewById(R.id.survey_submit_btn)

        try {
            if (titleView != null && !TextUtils.isEmpty(json.optString("title"))) {
                Log.d(NativeCompanionAdManager.TAG, "setting title: ")
                titleView.text = json.optString("title")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (questionView != null && surveyQuery != null) {
            Log.d(NativeCompanionAdManager.TAG, "setting question: ")
            questionView.text = surveyQuery.question.value
        }

        if (type == SurveyAdsResponse.PARAGRAPH && answerView != null) {
            Log.d(NativeCompanionAdManager.TAG, "setting paragraph: ")
            answerView?.visibility = View.VISIBLE
            answerView?.setOnClickListener {
                if (!isResponseSubmitted) {
                    SurveyInputDialog(context, surveyQuery?.question?.value ?: "",
                            if (TextUtils.isEmpty(answerView?.text)) "" else answerView?.text.toString(),
                            object : SurveyInputDialogCallback {
                                override fun onAnswerSubmit(answer: String) {
                                    (it as? TextView)?.text = answer
                                    enableSubmitButton(!TextUtils.isEmpty(answer))
                                    submitSurveyResponse(surveyAnswer, surveyQuery)
                                }
                            }
                    )
                }
            }
            optionView?.visibility = View.GONE
        } else if (type == SurveyAdsResponse.MULTICHOICE && optionView != null){
            Log.d(NativeCompanionAdManager.TAG, "setting options: ")
            answerView?.visibility = View.GONE
            setUpOptionView(optionView, surveyAnswer)
        }

        submitBtn?.isEnabled = false
        enableSubmitButton(false)
        submitBtn?.setOnClickListener {
            submitSurveyResponse(surveyAnswer, surveyQuery)
        }

        try {
            if (iconView != null && !TextUtils.isEmpty(json.optString("logo"))) {
                loadIcon(iconView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadIcon(imageView: ImageView) {
        companionSdkScope.launch {
            try {
                val response = withTimeout(4000L) {
                    remoteDataSource.fetchCompanionResourceAsync(json.optString("logo"))
                }
                if (response.isSuccessful){
                    val companionContainer = companionAdSlot.container
                    val lp = imageView.layoutParams
                    val width = lp.width
                    val height = lp.height
                    val scaledBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(response.body()?.byteStream()), width, height, false)
                    val bitmapDrawable = BitmapDrawable(companionContainer.context.resources, scaledBitmap)
                    imageView.setImageDrawable(bitmapDrawable)
                    imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    imageView.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(json.optString("clickThroughUrl"))
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun setUpOptionView(optionView: TableLayout, answer: SurveyAnswer?) {
        optionView.visibility = View.VISIBLE
        optionView.removeAllViews()
        val num = answer?.options?.size ?: 0
        val colCount = 3
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
            val viewForPosition = getView(i, answer)
            rows[r].addView(viewForPosition)
            c++
        }
        rows.forEach {
            optionView.addView(it)
        }
        Log.d(NativeCompanionAdManager.TAG, "setting all options: ")
    }

    private fun getView(position: Int, answer: SurveyAnswer?): View? {
        val root = layoutInflater.inflate(R.layout.survey_grid_layout, null, false) as ViewGroup
        val radioBtn: RadioButton? = root.findViewById(R.id.survey_check_btn)
        if (radioBtn != null) {
            radioBtn.text = answer?.options?.get(position)?.name ?: ""
            radioBtn.setOnClickListener {
                radioBtn.isChecked = true
                selectedBtn?.isChecked = false
                selectedBtn = radioBtn
                selectedPosition = position
                if (submitEnable) {
                    enableSubmitButton(submitEnable)
                }
            }
        }
        Log.d(NativeCompanionAdManager.TAG, "setting grid option: ${position}")
        return root
    }

    private fun enableSubmitButton(isEnable: Boolean) {
        submitBtn?.isEnabled = isEnable
        try {
            val array: TypedArray = context.obtainStyledAttributes(R.styleable.NativeCompanionTheme)
            var textColor: Int = -1
            var background: Int = -1

            if(isEnable && array.hasValue(R.styleable.NativeCompanionTheme_survey_submit_enable_color)) {
                textColor = array.getResourceId(R.styleable.NativeCompanionTheme_survey_submit_enable_color, -1)
            } else if(!isEnable && array.hasValue(R.styleable.NativeCompanionTheme_survey_submit_disable_color)) {
                textColor = array.getResourceId(R.styleable.NativeCompanionTheme_survey_submit_disable_color, -1)
            }
            if (textColor > 0) submitBtn?.setTextColor(ContextCompat.getColor(context, textColor))

            if(isEnable && array.hasValue(R.styleable.NativeCompanionTheme_survey_submit_enable_bg)) {
                background = array.getResourceId(R.styleable.NativeCompanionTheme_survey_submit_enable_bg, -1)
            } else if (!isEnable && array.hasValue(R.styleable.NativeCompanionTheme_survey_submit_disable_bg)) {
                background = array.getResourceId(R.styleable.NativeCompanionTheme_survey_submit_disable_bg, -1)
            }
            if(background > 0 && ContextCompat.getDrawable(context, background) != null) submitBtn?.background = ContextCompat.getDrawable(context, background)

            Log.d(NativeCompanionAdManager.TAG, "setting enable btn back2: ${isEnable}")
        } catch (e: Exception){}
    }

    private fun submitSurveyResponse(answer: SurveyAnswer?, surveyQuery: SurveyQuery?) {
        var surveyAnswerResponse = SurveyAnswerResponse()
        if (answer?.type == SurveyAdsResponse.MULTICHOICE) {
            if (selectedPosition >= 0) {
                surveyAnswerResponse = SurveyAnswerResponse(listOf(answer.options[selectedPosition].id))
            } else {
                Toast.makeText(context, "Empty response", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            if (!TextUtils.isEmpty(answerView?.text)) {
                surveyAnswerResponse = SurveyAnswerResponse(paragraphAnswer = answerView?.text!!.toString())
            } else {
                Toast.makeText(context, "Empty Response", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val submitRequest = SurveyAdRequest.Builder(remoteDataSource, companionSdkScope).post()
                .url(json.optString("SurveyManagementServerURL"))
                .addRequestBody(surveyAnswerResponse).retry(2)
                .surveyId(json.optString("surveyId"))
                .addParam("advertiseId", remoteDataSource.mxMediaSdkConfig.advertiserId)
                .addParam("questionAndAnswerId", surveyQuery?.id)
                .listener(object : SurveyAdRequest.SurveyAdsListener {
                    override fun onSuccess(response: SurveyAdsResponse?) {
                        submitEnable = false
                        isResponseSubmitted = true
                        enableSubmitButton(submitEnable)
                        submitBtn?.text = "SUBMITTED"
                        showSnackBar("Thanks for your response")
                    }

                    override fun onFailed(errCode: Int) {
                        Toast.makeText(context, "Submit failed", Toast.LENGTH_SHORT).show()
                    }

                    override fun surveyAlreadyResponded() {
                        submitEnable = false
                        enableSubmitButton(submitEnable)
                        submitBtn?.text = "SUBMITTED"
                        Toast.makeText(context, "You have already responded", Toast.LENGTH_SHORT).show()
                    }
                }).build()
        submitRequest.request()
    }

    private fun showSnackBar(msg: String) {
        if (submitBtn != null) {
            val snackbar = Snackbar.make(submitBtn!!, msg, Snackbar.LENGTH_SHORT)
            setMarginForSnackBar(snackbar)
            snackbar.show()
            Log.d(NativeCompanionAdManager.TAG, "showSnackBar: ")
        }

    }

    private fun setMarginForSnackBar(snackBar: Any): Boolean {
        try {
            val snackbarClass = Class.forName("com.google.android.material.snackbar.Snackbar")
            val originalMarginsField = snackbarClass.superclass.getDeclaredField("originalMargins")
            originalMarginsField.isAccessible = true
            originalMarginsField.set(snackBar, Rect(dpToPx(8), 0, dpToPx(8), dpToPx(64)))
            return true
        } catch (e: IllegalAccessException) {
        } catch (e: NoSuchFieldException) {
        } catch (e: ClassNotFoundException) {
        }
        return false
    }

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    interface SurveyInputDialogCallback {
        fun onAnswerSubmit(answer: String)
    }
}