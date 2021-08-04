package com.mxplay.interactivemedia.internal.data.model

import android.text.TextUtils
import java.io.Serializable

class AdVerification : Serializable {
    val isEnabled = false
        get() = field && !TextUtils.isEmpty(url)
    var vendorKey: String? = null
    var url: String? = null
    var params: String? = null
    val type: String? = null
    var trackingEvents: MutableMap<EventName, TrackingEvent>? = null


    companion object{
        const val TAG_VERIFICATION = "Verification"
        const val TAG_JAVASCRIPT_RESOURCE= "JavaScriptResource"
        const val TAG_VERIFICATION_PARAMETERS = "VerificationParameters"
        const val ATTR_VENDOR = "vendor"
    }
}