package com.mxplay.interactivemedia.internal.api

import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.core.AdCompanionInfo

interface ICompanionRenderer {
    fun render(companionAdInfo: List<AdCompanionInfo>?)
    fun load(companionAdInfo: List<AdCompanionInfo>?)
    fun release(companionAdSlots: List<CompanionAdSlot>?)
}