package com.mxplay.adloader.nativeCompanion.surveyAd

import android.text.TextUtils
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class SurveyNativeCompanion(override val json: JSONObject, private val companionAdSlot: CompanionAdSlot, private val ioOpsScope: CoroutineScope,
                            private val remoteDataSource: RemoteDataSource, override val type: NativeCompanion.NativeCompanionType,
                            listener: NativeCompanion.NativeCompanionListener)
    : NativeCompanion {

    override val template: NativeCompanion.NativeCompanionTemplate =
            SurveyBaseTemplate(renderer = SurveyCompanionRenderer(json, companionAdSlot, ioOpsScope, remoteDataSource, listener))

    override fun loadCompanion() {
        val surveyId = json.optString("surveyId")
        val advertiserId = remoteDataSource.mxMediaSdkConfig.advertiserId
        val surveyManagementUrl = json.optString("SurveyManagementServerURL")
        if (!TextUtils.isEmpty(surveyId) && !TextUtils.isEmpty(advertiserId) && !TextUtils.isEmpty(surveyManagementUrl)) {
            val surveyAdRequest = SurveyAdRequest.Builder(remoteDataSource, ioOpsScope).get()
                    .url(surveyManagementUrl)
                    .surveyId(surveyId)
                    .addParam("advertiseId", advertiserId)
                    .listener(template as? SurveyBaseTemplate).build()
            surveyAdRequest.request()
        }
    }
}