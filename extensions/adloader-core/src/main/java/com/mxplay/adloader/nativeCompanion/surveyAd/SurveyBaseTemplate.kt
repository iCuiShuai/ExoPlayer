package com.mxplay.adloader.nativeCompanion.surveyAd

import android.view.View
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.logger.ZenLogger

class SurveyBaseTemplate(override val id: String = "SurveyBaseTemplate", override val renderer: NativeCompanion.NativeCompanionRenderer, private val adsBehaviour: AdsBehaviour?)
    : NativeCompanion.NativeCompanionTemplate, SurveyAdRequest.SurveyAdsListener {

    override fun loadCompanionTemplate(): View? {
        renderer.release()
        return renderer.render()
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
        renderer.release()
        adsBehaviour?.onNativeCompanionLoaded(false)
    }

    override fun surveyAlreadyResponded() {
    }

}