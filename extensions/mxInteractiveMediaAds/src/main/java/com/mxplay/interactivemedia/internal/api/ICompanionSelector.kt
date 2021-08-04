package com.mxplay.interactivemedia.internal.api

import com.mxplay.interactivemedia.api.AdDisplayContainer
import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.internal.core.AdCompanionInfo

interface ICompanionSelector {
    fun pickBestCompanions(displayContainer: AdDisplayContainer, companionAds: List<CompanionAd>?): List<AdCompanionInfo>?
}