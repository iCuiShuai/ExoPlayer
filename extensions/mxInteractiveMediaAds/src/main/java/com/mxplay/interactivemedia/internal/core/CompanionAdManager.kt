package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.api.AdDisplayContainer
import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.api.ICompanionRenderer
import com.mxplay.interactivemedia.internal.api.ICompanionSelector
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.tracking.ITrackersHandler
import kotlinx.coroutines.CoroutineScope

class CompanionAdManager(ioOpsScope: CoroutineScope, remoteDataSource: RemoteDataSource, trackersHandler: ITrackersHandler?): ICompanionSelector, ICompanionRenderer {
    private val companionSelector = CompanionSelector()
    private val staticResRenderer = CompanionStaticResourceRenderer(ioOpsScope, remoteDataSource, trackersHandler)
    private val htmlResourceRenderer = CompanionHtmlResourceRenderer(ioOpsScope, remoteDataSource, trackersHandler)

    override fun pickBestCompanions(displayContainer: AdDisplayContainer, companionAds: List<CompanionAd>?): List<AdCompanionInfo>? {
        return companionSelector.pickBestCompanions(displayContainer, companionAds)
    }

    override fun render(companionAdInfo: List<AdCompanionInfo>?) {
        staticResRenderer.render(companionAdInfo)
        htmlResourceRenderer.render(companionAdInfo)
    }

    override fun load(companionAdInfo: List<AdCompanionInfo>?) {
        staticResRenderer.load(companionAdInfo)
        htmlResourceRenderer.load(companionAdInfo)
    }

    override fun release(companionAdSlots: List<CompanionAdSlot>?) {
        staticResRenderer.release(companionAdSlots)
    }
}
