package com.mxplay.interactivemedia.internal.api

import com.mxplay.interactivemedia.internal.data.model.AdBreakErrorEvent

interface AdBreakErrorListener {
    fun onError(adBreakErrorEvent : AdBreakErrorEvent)
}