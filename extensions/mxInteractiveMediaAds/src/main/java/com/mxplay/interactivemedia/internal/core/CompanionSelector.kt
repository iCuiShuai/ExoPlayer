package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.api.AdDisplayContainer
import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.api.ICompanionSelector
import com.mxplay.interactivemedia.internal.data.model.CompanionAdData

class CompanionSelector: ICompanionSelector {

    override fun pickBestCompanions(displayContainer: AdDisplayContainer, companionAds: List<CompanionAd>?): List<AdCompanionInfo>? {
        if (companionAds.isNullOrEmpty() || displayContainer.getCompanionAdSlots().isNullOrEmpty()) {
            return null
        }
        var adCompanionInfo = mutableListOf<AdCompanionInfo>()
        var companionAdList = companionAds.toMutableList()
        val companionAdSlots = displayContainer.getCompanionAdSlots()
        for (companionSlot in companionAdSlots!!) {
            val iterator = companionAdList.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (isCompatible(companionSlot, next)) {
                    adCompanionInfo.add(AdCompanionInfo(companionSlot, next, (next as? CompanionAdData)?.resourceType ?: CompanionAdData.TAG_NO_RESOURCE))
                    iterator.remove()
                }
            }
        }
        return adCompanionInfo
    }

    private fun isCompatible(companionAdSlot: CompanionAdSlot, companionAd: CompanionAd): Boolean {
        return companionAdSlot.height == companionAd.height && companionAdSlot.width == companionAd.width
    }
}