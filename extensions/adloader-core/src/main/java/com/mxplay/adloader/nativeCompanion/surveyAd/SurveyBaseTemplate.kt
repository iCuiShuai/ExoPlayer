package com.mxplay.adloader.nativeCompanion.surveyAd

import android.text.TextUtils
import android.view.View
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class SurveyBaseTemplate(private val json: JSONObject, private val adsBehaviour: AdsBehaviour?,
                         private val companionSdkScope: CoroutineScope, private val remoteDataSource: RemoteDataSource,
                         resourceProvider: CompanionResourceProvider, companionAdSlot: CompanionAdSlot,
                         eventsTracker: EventsTracker, override val id: String = "SurveyBaseTemplate")
    : NativeCompanion.NativeCompanionTemplate, SurveyAdRequest.SurveyAdsListener {

    override val renderer = SurveyCompanionRenderer(json, companionAdSlot, eventsTracker, adsBehaviour,
            companionSdkScope, remoteDataSource, resourceProvider)
    private var isAdStarted = false

    override fun loadCompanionTemplate() {
        val surveyId = json.optString(SurveyNativeCompanion.SURVEY_ID)
        val advertiserId = remoteDataSource.mxMediaSdkConfig.advertiserId
        val surveyManagementUrl = json.optString(SurveyNativeCompanion.SURVEY_MANAGEMENT_URL)
        if (!TextUtils.isEmpty(surveyId) && !TextUtils.isEmpty(advertiserId) && !TextUtils.isEmpty(surveyManagementUrl)) {
            val surveyAdRequest = SurveyAdRequest.Builder(remoteDataSource, companionSdkScope).get()
                    .url(surveyManagementUrl)
                    .surveyId(surveyId)
                    .addParam(SurveyNativeCompanion.ADVERTISER_ID, advertiserId)
                    .listener(this).build()
            surveyAdRequest.request()
        }
    }

    override fun showCompanionTemplate(): View? {
        isAdStarted = true
        renderer.release()
        return renderer.render()
    }

    override fun onSuccess(response: SurveyAdsResponse?) {
        if (response != null) {
            if(renderer is SurveyCompanionRenderer) {
                renderer.surveyAdsResponse = response
            }
            if (isAdStarted) showCompanionTemplate()
        }
    }

    override fun onFailed(errCode: Int) {
        renderer.release()
        adsBehaviour?.onNativeCompanionLoaded(false)
    }

    override fun surveyAlreadyResponded() {
    }

}