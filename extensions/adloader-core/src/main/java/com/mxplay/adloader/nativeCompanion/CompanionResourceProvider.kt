package com.mxplay.adloader.nativeCompanion

import android.widget.ImageView

interface CompanionResourceProvider {
    fun loadImage(url : String?, view : ImageView)
}