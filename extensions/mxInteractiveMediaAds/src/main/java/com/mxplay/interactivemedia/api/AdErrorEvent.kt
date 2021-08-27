package com.mxplay.interactivemedia.api

class AdErrorEvent {
    val error: AdError
    var ad: Ad? = null
    var userRequestContext: Any? = null
        private set

    constructor(adError: AdError,  ad: Ad? = null) {
        error = adError
        this.ad = ad
    }

    constructor(adError: AdError, userRequestContext: Any?) {
        error = adError
        this.userRequestContext = userRequestContext
    }

    interface AdErrorListener {
        fun onAdError(adErrorEvent: AdErrorEvent)
    }

    interface AdErrorVerificationListener {
        fun onAdErrorVerification(adErrorEvent: AdErrorEvent)
    }
}