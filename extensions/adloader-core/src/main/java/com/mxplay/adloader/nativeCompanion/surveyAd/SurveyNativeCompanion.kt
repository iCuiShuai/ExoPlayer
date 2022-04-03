package com.mxplay.adloader.nativeCompanion.surveyAd

import android.text.TextUtils
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.adloader.nativeCompanion.expandable.ExpandableNativeCompanion
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.logger.ZenLogger
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class SurveyNativeCompanion(json: JSONObject, companionAdSlot: CompanionAdSlot, private val eventsTracker: EventsTracker, adsBehaviour: AdsBehaviour?,
                            private val companionSdkScope: CoroutineScope, private val remoteDataSource: RemoteDataSource, type: NativeCompanionType,
                            resourceProvider: CompanionResourceProvider)
    : NativeCompanion(type, json) {

    val template: NativeCompanionTemplate =
            SurveyBaseTemplate(renderer = SurveyCompanionRenderer(json, companionAdSlot, eventsTracker, adsBehaviour, companionSdkScope, remoteDataSource, resourceProvider))

    override fun loadCompanion() {
        val surveyId = json.optString("surveyId")
        val advertiserId = remoteDataSource.mxMediaSdkConfig.advertiserId
        val surveyManagementUrl = json.optString("SurveyManagementServerURL")
        if (!TextUtils.isEmpty(surveyId) && !TextUtils.isEmpty(advertiserId) && !TextUtils.isEmpty(surveyManagementUrl)) {
            val surveyAdRequest = SurveyAdRequest.Builder(remoteDataSource, companionSdkScope).get()
                    .url(surveyManagementUrl)
                    .surveyId(surveyId)
                    .addParam("advertiseId", advertiserId)
                    .listener(template as? SurveyBaseTemplate).build()
            surveyAdRequest.request()
        }
        template.loadCompanionTemplate()
    }

    override fun onAdEvent(adEvent: AdEvent) {
        super.onAdEvent(adEvent)
        val type: AdEvent.AdEventType = adEvent.type


        if (type == AdEvent.AdEventType.CONTENT_RESUME_REQUESTED || type == AdEvent.AdEventType.COMPLETED || type == AdEvent.AdEventType.ALL_ADS_COMPLETED || type == AdEvent.AdEventType.SKIPPED) {
            // #debug debug
            release()
            return
        }
    }

    fun release() {
        template.renderer.release()
    }
}