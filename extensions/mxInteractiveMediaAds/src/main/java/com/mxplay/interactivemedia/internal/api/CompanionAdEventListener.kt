package com.mxplay.interactivemedia.internal.api

import com.mxplay.interactivemedia.internal.data.model.CompanionAdEvent

interface CompanionAdEventListener {
    fun onEvent(companionAdEvent: CompanionAdEvent)
}