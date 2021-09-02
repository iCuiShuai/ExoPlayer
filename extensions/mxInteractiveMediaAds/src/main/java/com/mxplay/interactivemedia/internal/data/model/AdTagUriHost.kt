package com.mxplay.interactivemedia.internal.data.model

interface AdTagUriHost {
    fun getPendingAdTagUriHost(): AdTagUriHost?
    fun getPendingAdTagUri() : String?
    fun handleAdTagUriResult(vastModel: VASTModel?)
    fun isFallBackOnNoAd(): Boolean
}