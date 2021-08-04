package com.mxplay.interactivemedia.internal.data.model

import com.mxplay.interactivemedia.api.player.AdMediaInfo

interface IMediaFilesProvider {
    fun getAllMedia() : List<MediaFile>
    fun setAdMediaInfo(adMediaInfo: AdMediaInfo)
    fun getAdMediaInfo() : AdMediaInfo?
}