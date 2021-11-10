package com.mxplay.interactivemedia.api

import com.mxplay.interactivemedia.internal.core.AdBreakLoader
import com.mxplay.interactivemedia.internal.data.xml.ProtocolException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.util.*

class AdError(val errorType: AdErrorType, val errorCode: AdErrorCode, message: String?) :
    Exception(message) {
    val errorCodeNumber: Int
        get() = errorCode.errorNumber
    override val message: String
        get() = super.message!!

    fun convertToData(): Map<String, String> {
        val data: MutableMap<String, String> = HashMap()
        data["errorMessage"] = message
        data["errorCode"] = errorCode.errorNumber.toString()
        data["type"] = errorType.name.toLowerCase()
        return data
    }

    override fun toString(): String {
        val var1 = errorType.toString()
        val var2 = errorCode.toString()
        val var3 = message
        return StringBuilder(45 + var1.length + var2.length + var3.length).append("AdError [errorType: ")
            .append(var1).append(", errorCode: ").append(var2).append(", message: ").append(var3)
            .append("]").toString()
    }

    fun mapper(e: java.lang.Exception): AdError {
        var code  =  AdErrorCode.UNKNOWN_ERROR
        var message = e.message ?: ""
        when(e){
            is AdBreakLoader.MaxRedirectLimitReachException -> code = AdErrorCode.VAST_TOO_MANY_REDIRECTS
            is TimeoutCancellationException -> code  =  AdErrorCode.VAST_LOAD_TIMEOUT
            is ProtocolException -> code = e.error.errorCode
            is IOException -> {
                code = AdErrorCode.FAILED_TO_REQUEST_ADS
                message = "There was a problem requesting ads from the server"
            }
        }
        return AdError(AdErrorType.LOAD, code, message)
    }

    /*

    00 XML parsing error.
101 VAST schema validation error.
102 VAST version of response not supported.
200 Trafficking error. Video player received an Ad type that it was not expecting and/or
cannot display.
201 Video player expecting different linearity.
202 Video player expecting different duration.
203 Video player expecting different size.
204 Ad category was required but not provided.
300 General Wrapper error.
301 Timeout of VAST URI provided in Wrapper element, or of VAST URI provided in a
subsequent Wrapper element. (URI was either unavailable or reached a timeout as
defined by the video player.)
302 Wrapper limit reached, as defined by the video player. Too many Wrapper
responses have been received with no InLine response.
303 No VAST response after one or more Wrappers.
304 InLine response returned ad unit that failed to result in ad display within defined time
limit.
400 General Linear error. Video player is unable to display the Linear Ad.
401 File not found. Unable to find Linear/MediaFile from URI.
402 Timeout of MediaFile URI.
403 Couldn’t find MediaFile that is supported by this video player, based on the
attributes of the MediaFile element.
405 Problem displaying MediaFile. Video player found a MediaFile with supported type
but couldn’t display it. MediaFile may include: unsupported codecs, different MIME
type than MediaFile@type, unsupported delivery method, etc.
406 Mezzanine was required but not provided. Ad not served.
407 Mezzanine is in the process of being downloaded for the first time. Download may
take several hours. Ad will not be served until mezzanine is downloaded and
transcoded.
408 Conditional ad rejected.
409 Interactive unit in the InteractiveCreativeFile node was not executed.
410 Verification unit in the Verification node was not executed.
411 Mezzanine was provided as required, but file did not meet required specification. Ad
not served.
500 General NonLinearAds error.
501 Unable to display NonLinear Ad because creative dimensions do not align with
creative display area (i.e. creative dimension too large).
502 Unable to fetch NonLinearAds/NonLinear resource.
503 Couldn’t find NonLinear resource with supported type.

600 General CompanionAds error.
601 Unable to display Companion because creative dimensions do not fit within
Companion display area (i.e., no available space).
602 Unable to display required Companion.
603 Unable to fetch CompanionAds/Companion resource.
604 Couldn’t find Companion resource with supported type.
900 Undefined Error.
901 General VPAID error


     */
    enum class AdErrorCode(val errorNumber: Int) {
        INTERNAL_ERROR(-1), VAST_MALFORMED_RESPONSE(100), VMAP_MALFORMED_RESPONSE(1002), UNKNOWN_AD_RESPONSE(
            1010
        ),
        VAST_TRAFFICKING_ERROR(200), VAST_LOAD_TIMEOUT(301), VAST_TOO_MANY_REDIRECTS(302), VIDEO_PLAY_ERROR(
            400
        ),
        VAST_MEDIA_LOAD_TIMEOUT(402), VAST_LINEAR_ASSET_MISMATCH(403), OVERLAY_AD_PLAYING_FAILED(500), OVERLAY_AD_LOADING_FAILED(
            502
        ),
        VAST_NONLINEAR_ASSET_MISMATCH(503), COMPANION_GENERAL_ERROR(600), COMPANION_AD_LOADING_FAILED(603), UNKNOWN_ERROR(900), VAST_EMPTY_RESPONSE(
            1009
        ),
        FAILED_TO_REQUEST_ADS(1005), VAST_ASSET_NOT_FOUND(1007), ADS_REQUEST_NETWORK_ERROR(1012), INVALID_ARGUMENTS(
            1101
        ),
        PLAYLIST_NO_CONTENT_TRACKING(1205), UNEXPECTED_ADS_LOADED_EVENT(1206);

        fun equals(code: Int): Boolean {
            return errorNumber == code
        }

        override fun toString(): String {
            return "AdErrorCode{" +
                    "a=" + errorNumber +
                    '}'
        }
    }

    enum class AdErrorType {
        LOAD, PLAY
    }
}