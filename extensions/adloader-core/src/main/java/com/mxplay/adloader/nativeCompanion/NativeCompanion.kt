package com.mxplay.adloader.nativeCompanion

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.CallSuper
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAd
import org.json.JSONObject

abstract class NativeCompanion( val type: NativeCompanionType,
                                val json: JSONObject
                                ) : AdEvent.AdEventListener{

    abstract fun loadCompanion()

    enum class NativeCompanionType(val value: String) {
        SURVEY_AD("survey"),
        EXPANDABLE("expandable")
    }

    interface NativeCompanionTemplate {
        val id: String
        val renderer: NativeCompanionRenderer
        fun loadCompanionTemplate() : View?
    }

    interface NativeCompanionRenderer {
        fun render() : View?
        fun release()

        fun px2dp(px: Int, context: Context): Int {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    px * 1F,
                    context.resources.displayMetrics).toInt()
        }
    }



    override fun onAdEvent(adEvent: AdEvent) {

    }

    @CallSuper
    open fun release() {

    }

}