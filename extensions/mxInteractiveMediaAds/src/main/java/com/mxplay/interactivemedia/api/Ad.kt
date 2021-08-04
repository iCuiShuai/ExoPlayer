package com.mxplay.interactivemedia.api

import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.internal.core.AdCompanionInfo
import com.mxplay.interactivemedia.internal.tracking.ITrackersProvider

interface Ad : ITrackersProvider {
    fun getAdId(): String?
    fun getSequence(): Int
    fun getCreativeId(): String?
    fun isSkippable(): Boolean
    fun getSkipTimeOffset(): Long
    fun getDescription(): String?
    fun getTitle(): String?
    fun getAdvertiserName(): String?
    fun getDuration(): Long
    fun getAdPodInfo(): AdPodInfo
    fun getMediaInfo(): AdMediaInfo?
    fun getCompanionAds(): List<CompanionAd>?
}