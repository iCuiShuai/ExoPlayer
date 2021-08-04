package com.mxplay.interactivemedia.internal.core

import androidx.annotation.IntDef

@IntDef(AdBreakState.AD_BREAK_INIT, AdBreakState.AD_BREAK_STARTED, AdBreakState.AD_BREAK_ENDED)
    annotation class AdBreakState{
        companion object{
            const val  AD_BREAK_INIT = 99
            const val  AD_BREAK_STARTED = 100
            const val  AD_BREAK_ENDED = 101
        }
    }