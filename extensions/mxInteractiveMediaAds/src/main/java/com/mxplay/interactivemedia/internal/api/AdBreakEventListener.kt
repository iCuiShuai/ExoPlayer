package com.mxplay.interactivemedia.internal.api

import com.mxplay.interactivemedia.internal.data.model.AdBreakEvent

interface AdBreakEventListener {
    fun onEvent(adBreakEvent: AdBreakEvent)
}