package com.mxplay.interactivemedia.internal.api

import android.content.Context
import android.util.TypedValue
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.core.AdCompanionInfo

interface ICompanionRenderer {
    fun render(companionAdInfo: List<AdCompanionInfo>?)
    fun load(companionAdInfo: List<AdCompanionInfo>?)
    fun release(companionAdSlots: List<CompanionAdSlot>?)

    fun px2dp(px: Int, context: Context): Int {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                px * 1F,
                context.resources.displayMetrics).toInt()
    }
}