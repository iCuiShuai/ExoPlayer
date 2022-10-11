package com.mxplay.adloader.nativeCompanion.surveyAd

import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import ccom.mxplay.adloader.R
import com.google.gson.Gson
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.adloader.nativeCompanion.surveyAd.model.*
import com.mxplay.adloader.utils.SnackbarUtils
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.logger.ZenLogger
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class SurveyCompanion(
        private val payload: SurveyTemplateData,
        private val companionAdSlot: CompanionAdSlot,
        private val remoteDataSource: RemoteDataSource,
        private val companionSdkScope: CoroutineScope,
        private val resourceProvider: CompanionResourceProvider,
        private val eventsTracker: EventsTracker,
        private val adsBehaviour: AdsBehaviour?
): NativeCompanion(), SurveyAdRequest.SurveyAdsListener, SurveyRenderer.SurveyRendererListener {

    private val context = companionAdSlot.getContainer()?.context!!
    private val container = companionAdSlot.getContainer()!!
    private val layoutInflater = LayoutInflater.from(context)

    private var submitEnable = true
    private var isResponseSubmitted = false

    private var submitBtn: TextView? = null

    private var isAdStarted = false
    private var surveyAdsResponse: SurveyAdsResponse? = null
    private var surveyRenderer: SurveyRenderer? = null

    private var surveyFetchRequest: SurveyAdRequest? = null
    private var surveySubmitRequest: SurveyAdRequest? = null

    companion object {
        private const val TAG = "SurveyCompanion"
        private const val SURVEY_ID = "surveyId"
        private const val QUESTION_ANSWER_ID = "questionAndAnswerId"
        private const val ADVERTISER_ID = "advertiseId"
        private const val EVENT_SURVEY_SUBMITTED = "SurveyAdSubmitted"
        private const val EVENT_SURVEY_SHOWN = "SurveyAdShown"

        fun create(json: JSONObject, companionAdSlot: CompanionAdSlot, remoteDataSource: RemoteDataSource,
                   companionSdkScope: CoroutineScope, resourceProvider: CompanionResourceProvider,
                   eventsTracker: EventsTracker, adsBehaviour: AdsBehaviour?): SurveyCompanion {
            return try {
                val payload = Gson().fromJson(json.toString(), SurveyTemplateData::class.java)
                SurveyCompanion(payload, companionAdSlot, remoteDataSource, companionSdkScope, resourceProvider, eventsTracker, adsBehaviour)
            } catch (e: Exception) {
                throw IllegalStateException("Unrecognised ad data")
            }
        }
    }

    override fun preload() {
        val surveyId = payload.surveyId
        val advertiserId = remoteDataSource.mxMediaSdkConfig.advertiserId
        val surveyManagementUrl = payload.surveyManagementServerURL
        if (!TextUtils.isEmpty(surveyId) && !TextUtils.isEmpty(advertiserId) && !TextUtils.isEmpty(surveyManagementUrl)) {
            surveyFetchRequest = SurveyAdRequest.Builder(remoteDataSource, companionSdkScope).get()
                    .url(surveyManagementUrl)
                    .surveyId(surveyId)
                    .addParam(ADVERTISER_ID, advertiserId)
                    .listener(this).build()
            surveyFetchRequest?.request()
        }
    }

    override fun display() {
        isAdStarted = true
        container.removeAllViews()
        render()
    }

    override fun release() {
        super.release()
        surveyRenderer?.release()
        container.removeAllViews()
        surveyFetchRequest?.release()
        surveySubmitRequest?.release()
    }

    fun render() : View?{
        container.removeAllViews()
        try {
            val root = layoutInflater.inflate(R.layout.native_survey_ads, container, false) as ViewGroup
            root.clipChildren = false
            root.clipToPadding = false

            bindView(root)

            container.addView(root)
            return root
        } catch (e: Exception) {
        }
        return null
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

        val iconView: ImageView? = adView.findViewById(R.id.native_ad_icon)
        val titleView: TextView? = adView.findViewById(R.id.native_ad_title)
        val questionView: TextView? = adView.findViewById(R.id.survey_question)
        submitBtn = adView.findViewById(R.id.survey_submit_btn)

        try {
            if (titleView != null && !TextUtils.isEmpty(payload.title)) {
                titleView.text = payload.title
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (questionView != null && surveyQuery != null) {
            questionView.text = surveyQuery.question.value
        }

        surveyRenderer?.renderSurveyAnswerView(adView, layoutInflater)

        submitBtn?.isEnabled = false
        enableSubmitButton(false)
        submitBtn?.setOnClickListener {
            submitSurveyResponse()
        }

        try {
            if (iconView != null && !TextUtils.isEmpty(payload.logo)) {
                resourceProvider.loadImage(payload.logo, iconView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        trackImpression()
        adView.post {
            if("Player_top" == payload.slot) adsBehaviour?.onVideoSizeChanged(10, 9)
        }
    }

    protected fun enableSubmitButton(isEnable: Boolean) {
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

        } catch (e: Exception){}
    }

    protected fun submitSurveyResponse() {
        var surveyAnswerResponse = surveyRenderer?.getSurveyAnswer()
        if(surveyAnswerResponse != null) {
            surveySubmitRequest = SurveyAdRequest.Builder(remoteDataSource, companionSdkScope).post()
                    .url(payload.surveyManagementServerURL)
                    .addRequestBody(surveyAnswerResponse).retry(2)
                    .surveyId(payload.surveyId)
                    .addParam(ADVERTISER_ID, remoteDataSource.mxMediaSdkConfig.advertiserId)
                    .addParam(QUESTION_ANSWER_ID, surveyAdsResponse?.getQuery()?.id)
                    .listener(object : SurveyAdRequest.SurveyAdsListener {
                        override fun onSuccess(response: SurveyAdsResponse?) {
                            ZenLogger.dt(TAG, " answer submitted ")
                            submitEnable = false
                            isResponseSubmitted = true
                            enableSubmitButton(submitEnable)
                            submitBtn?.text = "SUBMITTED"
                            showSnackBar("Thanks for your response")
                            trackSurveySubmit("ok")
                        }

                        override fun onFailed(errCode: Int) {
                            ZenLogger.dt(TAG, " answer submit failed ")
                            Toast.makeText(context, "Submit failed", Toast.LENGTH_SHORT).show()
                        }

                        override fun surveyAlreadyResponded() {
                            ZenLogger.dt(TAG, " answer already submitted ")
                            submitEnable = false
                            enableSubmitButton(submitEnable)
                            submitBtn?.text = "SUBMITTED"
                            Toast.makeText(context, "You have already responded", Toast.LENGTH_SHORT).show()
                            trackSurveySubmit("alreadyResponded")
                        }
                    }).build()
            surveySubmitRequest?.request()
        }
    }

    private fun showSnackBar(msg: String) {
        if (submitBtn != null) {
            val leftRightMargin = if(context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) dpToPx(8) else dpToPx(188)
            val topBottomMargin = dpToPx(8)
            SnackbarUtils.Short(submitBtn!!, msg).margins(leftRightMargin, topBottomMargin, leftRightMargin, topBottomMargin).radius(dpToPx(4).toFloat()).show()
        }

    }

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    private fun trackSurveySubmit(status: String) {
        eventsTracker.trackSurveyCompanionEvent(EVENT_SURVEY_SUBMITTED, data = mutableMapOf(SURVEY_ID to payload.surveyId, "statusCode" to status))
    }

    private fun trackImpression() {
        val trackerUrls = payload.impressionTracker?.toMutableList()
        if(!trackerUrls.isNullOrEmpty()) {
            eventsTracker.trackSurveyCompanionEvent(EVENT_SURVEY_SHOWN, trackerUrls, mutableMapOf(SURVEY_ID to payload.surveyId))
        }
    }

    interface SurveyInputDialogCallback {
        fun onAnswerSubmit(answer: String)
    }

    override fun onAdEvent(adEvent: AdEvent) {
        super.onAdEvent(adEvent)
        val type: AdEvent.AdEventType = adEvent.type

        if (type == AdEvent.AdEventType.CONTENT_RESUME_REQUESTED || type == AdEvent.AdEventType.COMPLETED || type == AdEvent.AdEventType.ALL_ADS_COMPLETED || type == AdEvent.AdEventType.SKIPPED) {
            release()
            return
        }
    }

    override fun onSuccess(response: SurveyAdsResponse?) {
        if (response != null) {
            this.surveyAdsResponse = response
            surveyRenderer = SurveyRenderer.create(companionAdSlot, surveyAdsResponse!!, this)
            if (isAdStarted) display()
        }
    }

    override fun onFailed(errCode: Int) {
        adsBehaviour?.onNativeCompanionLoaded(false)
        release()
    }

    override fun surveyAlreadyResponded() {
    }

    override fun isSubmitEnable(): Boolean {
        return submitEnable
    }

    override fun enableSubmit(isEnable: Boolean) {
        enableSubmitButton(isEnable)
    }

    override fun submitSurvey() {
        submitSurveyResponse()
    }
}