package com.mxplay.adloader

import com.mxplay.interactivemedia.api.AdError
import com.mxplay.interactivemedia.api.AdErrorEvent
import com.mxplay.interactivemedia.api.toMxAdErrorCode
import com.mxplay.interactivemedia.api.toMxAdErrorType

internal class ComposedAdErrorListener : com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener, AdErrorEvent.AdErrorListener {

    var adErrorListener: AdErrorEvent.AdErrorListener? = null

    override fun onAdError(adErrorEvent: com.google.ads.interactivemedia.v3.api.AdErrorEvent?) {
        if(adErrorEvent == null || adErrorListener == null)  return
        adErrorListener!!.onAdError(AdErrorEvent(AdError(adErrorEvent.error.errorType.toMxAdErrorType(), adErrorEvent.error.errorCode.toMxAdErrorCode(), adErrorEvent.error.message), null))
    }

    override fun onAdError(adErrorEvent: AdErrorEvent) {
        if(adErrorListener == null)  return
        adErrorListener!!.onAdError(adErrorEvent)
    }
}