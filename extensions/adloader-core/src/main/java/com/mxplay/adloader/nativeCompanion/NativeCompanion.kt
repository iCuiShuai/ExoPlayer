package com.mxplay.adloader.nativeCompanion

import android.content.Context
import android.util.TypedValue
import org.json.JSONObject

interface NativeCompanion {
    val type: NativeCompanionType
    val json: JSONObject
    val template: NativeCompanionTemplate

    fun loadCompanion()

    enum class NativeCompanionType(val value: String) {
        SURVEY_AD("survey")
    }

    interface NativeCompanionTemplate {
        val id: String
        val renderer: NativeCompanionRenderer

        fun loadCompanionTemplate()
    }

    interface NativeCompanionRenderer {
        fun render()
        fun release()

        fun px2dp(px: Int, context: Context): Int {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    px * 1F,
                    context.resources.displayMetrics).toInt()
        }
    }

    interface NativeCompanionListener {
        fun onVideoSizeChanged(width: Int, height: Int)
    }
}