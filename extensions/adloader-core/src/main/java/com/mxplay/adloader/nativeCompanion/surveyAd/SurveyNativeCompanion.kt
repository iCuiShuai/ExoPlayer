package com.mxplay.adloader.nativeCompanion.surveyAd

import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.NativeCompanion
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
            SurveyBaseTemplate(json, adsBehaviour, companionSdkScope, remoteDataSource, resourceProvider, companionAdSlot, eventsTracker)

    companion object {
        private const val TAG = "SurveyNativeCompanion"
        const val SURVEY_ID = "surveyId"
        const val SURVEY_MANAGEMENT_URL = "SurveyManagementServerURL"
        const val ADVERTISER_ID = "advertiseId"
    }

    override fun loadCompanion() {
        template.loadCompanionTemplate()
    }

    override fun showCompanion() {
        template.showCompanionTemplate()
    }

    override fun onAdEvent(adEvent: AdEvent) {
        super.onAdEvent(adEvent)
        val type: AdEvent.AdEventType = adEvent.type


        if (type == AdEvent.AdEventType.CONTENT_RESUME_REQUESTED || type == AdEvent.AdEventType.COMPLETED || type == AdEvent.AdEventType.ALL_ADS_COMPLETED || type == AdEvent.AdEventType.SKIPPED) {
            release()
            return
        }
    }

    override fun release() {
        super.release()
        ZenLogger.dt(TAG, " release ")
        template.renderer.release()
    }
}