package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.api.AdProgressListener

interface IVideoAdViewHolder : AdProgressListener {
    fun bind(activeAd: ActiveAd)
    fun unbind()
    fun hide()
    fun show()
}