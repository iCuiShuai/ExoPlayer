package com.mxplay.adloader.nativeCompanion.surveyAd

import com.mxplay.adloader.nativeCompanion.NativeCompanion

class SurveyBaseTemplate(override val id: String = "SurveyBaseTemplate", override val renderer: NativeCompanion.NativeCompanionRenderer)
    : NativeCompanion.NativeCompanionTemplate, SurveyAdRequest.SurveyAdsListener {

    override fun loadCompanionTemplate() {
        renderer.release()
        renderer.render()
    }

    override fun onSuccess(response: SurveyAdsResponse?) {
        if (response != null) {
            if(renderer is SurveyCompanionRenderer) {
                renderer.surveyAdsResponse = response
            }
            loadCompanionTemplate()
        }
    }

    override fun onFailed(errCode: Int) {
    }

    override fun surveyAlreadyResponded() {
    }

}