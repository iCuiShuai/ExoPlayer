package com.mxplay.interactivemedia.internal.api

import com.mxplay.interactivemedia.internal.data.model.IMediaFilesProvider
import com.mxplay.interactivemedia.internal.data.model.MediaFile

interface IMediaSelector {
    fun selectMediaFile(mediaFilesProvider: IMediaFilesProvider): MediaFile
}