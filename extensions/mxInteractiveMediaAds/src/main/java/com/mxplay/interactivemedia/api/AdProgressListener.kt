package com.mxplay.interactivemedia.api

import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.api.player.VideoProgressUpdate

interface AdProgressListener {
    fun onAdProgressUpdate(adMediaInfo: AdMediaInfo?, progress: VideoProgressUpdate)
    fun onAdBuffering(adMediaInfo: AdMediaInfo?)
}