package com.mxplay.interactivemedia.api

import com.mxplay.interactivemedia.api.player.VideoProgressUpdate

interface ContentProgressListener {
    fun onProgressUpdate(progress : VideoProgressUpdate)
}