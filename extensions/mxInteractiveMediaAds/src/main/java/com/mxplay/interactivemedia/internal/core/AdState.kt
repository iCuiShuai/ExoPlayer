package com.mxplay.interactivemedia.internal.core

import androidx.annotation.IntDef

@IntDef(AdState.LOADED, AdState.STARTED, AdState.FIRST_QUARTILE, AdState.MIDPOINT, AdState.THIRD_QUARTILE, AdState.SKIPPED, AdState.COMPLETED, AdState.ERROR)
    annotation class AdState{
        companion object{
            const val  NONE = 199
            const val  LOADED = 200
            const val  STARTED = 201
            const val  FIRST_QUARTILE = 202
            const val  MIDPOINT = 203
            const val  THIRD_QUARTILE = 204
            const val  SKIPPED = 205
            const val  COMPLETED = 206
            const val  ERROR = 207
        }
    }