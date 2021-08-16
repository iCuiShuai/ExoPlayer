package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.internal.core.AdCompanionInfo

interface ICompanionInfoProvider {
    fun getAllCompanionAds(): List<CompanionAd>?
    fun getCompanionsInfo(): List<AdCompanionInfo>?
    fun setCompanionsInfo(adCompanionInfo: List<AdCompanionInfo>?)
}