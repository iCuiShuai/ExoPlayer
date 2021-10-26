package com.mxplay.interactivemedia.api

import android.content.Context
import com.google.android.exoplayer2.C
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.AdsBehaviourExactTime
import com.mxplay.adloader.IAdsBehaviour
import com.mxplay.interactivemedia.api.player.VideoAdPlayer
import com.mxplay.interactivemedia.internal.util.UrlStitchingService
import com.mxplay.mediaads.exo.OmaUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

/** Stores configuration for ad loading and playback.  */
class Configuration(builder : Builder) {

    companion object{
        const val DEFAULT_AD_PRELOAD_TIMEOUT_MS = 10 * C.MILLIS_PER_SECOND
        const val VAST_LOAD_TIMEOUT_MS = (4 * C.MILLIS_PER_SECOND).toInt()
        const val MEDIA_LOAD_TIMEOUT_MS = (8 * C.MILLIS_PER_SECOND).toInt()

    }
    val context: Context = builder.context
    val ioExecutor : ExecutorService = builder.ioExecutor
    val appName :String
    val adUnitId : String
    val adPreloadTimeoutMs: Long
    val vastLoadTimeoutMs: Int
    val mediaLoadTimeoutMs: Int
    val focusSkipButtonWhenAvailable: Boolean
    val playAdBeforeStartPosition: Boolean
    val mediaBitrate: Int
    val adMediaMimeTypes: List<String>?
    val companionAdSlots: Collection<CompanionAdSlot>?
    val applicationAdErrorListener: AdErrorEvent.AdErrorListener?
    val applicationAdEventListener: AdEvent.AdEventListener?
    val applicationVideoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback?
    val imaSdkSettings: OmSdkSettings?
    val userInfo: UserInfo
    val trackersConfig: TrackersConfig
    val debugModeEnabled: Boolean
    val adsBehaviour: IAdsBehaviour

    val ioDispatcher: CoroutineDispatcher
    val mainDispatcher: MainCoroutineDispatcher = Dispatchers.Main
    val userAgent by lazy { getUserAgentFromProperty() }
    val urlStitchingService = UrlStitchingService(this)


    init {
        appName = builder.appName
         adUnitId = builder.adUnitId
         adPreloadTimeoutMs = builder.adPreloadTimeoutMs ?: DEFAULT_AD_PRELOAD_TIMEOUT_MS
         vastLoadTimeoutMs = builder.vastLoadTimeoutMs ?: VAST_LOAD_TIMEOUT_MS
         mediaLoadTimeoutMs = builder.mediaLoadTimeoutMs ?: MEDIA_LOAD_TIMEOUT_MS
         focusSkipButtonWhenAvailable = builder.focusSkipButtonWhenAvailable
         playAdBeforeStartPosition = builder.playAdBeforeStartPosition
         mediaBitrate = builder.mediaBitrate ?: OmaUtil.BITRATE_UNSET
         adMediaMimeTypes = builder.adMediaMimeTypes
         companionAdSlots = builder.companionAdSlots
         applicationAdErrorListener = builder.applicationAdErrorListener
         applicationAdEventListener = builder.applicationAdEventListener
         applicationVideoAdPlayerCallback = builder.applicationVideoAdPlayerCallback
         imaSdkSettings = builder.imaSdkSettings
         userInfo = builder.userInfo
         trackersConfig = builder.trackersConfig
         debugModeEnabled = builder.debugModeEnabled
        ioDispatcher = ioExecutor.asCoroutineDispatcher()
        adsBehaviour = builder.adsBehaviour ?: AdsBehaviour(vastLoadTimeoutMs, debugModeEnabled)
    }

    private fun getUserAgentFromProperty(): String? {
        try {
            val userAgent = System.getProperty("http.agent")
            if (userAgent != null) {
                return userAgent.replace("[^\\u001f-\\u007F]".toRegex(), "")
            }
        } catch (ignored: java.lang.Exception) {
        }
        return null
    }


    val userUIID: String?
        get() = userInfo.userUIID

    val userToken: String?
        get() = userInfo.userToken

    val nonPersonalizedAd: Boolean
    get() = userInfo.nonPersonalizedAd

    val advertiserId: String
    get() = userInfo.advertiserId

    data class Builder(val context: Context,
                       val ioExecutor : ExecutorService, val userInfo: UserInfo, val trackersConfig: TrackersConfig){

        var appName :String = ""
            private set

        var adUnitId : String = ""
        var adPreloadTimeoutMs: Long? = null
        var vastLoadTimeoutMs: Int? = null
        var mediaLoadTimeoutMs: Int? = null
        var focusSkipButtonWhenAvailable: Boolean = true
        var playAdBeforeStartPosition: Boolean = true
        var mediaBitrate: Int? = null
        var adMediaMimeTypes: List<String>? = null
        var companionAdSlots: Collection<CompanionAdSlot>? = null
        var applicationAdErrorListener: AdErrorEvent.AdErrorListener? = null
        var applicationAdEventListener: AdEvent.AdEventListener? = null
        var applicationVideoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback? = null
        var imaSdkSettings: OmSdkSettings? = null
        var debugModeEnabled: Boolean = false
        var adsBehaviour: IAdsBehaviour? = null

        fun appName(appName: String) = apply { this.appName = appName }
        fun adUnitId(adUnitId: String) = apply { this.adUnitId = adUnitId }
        fun adPreloadTimeoutMs(adPreloadTimeoutMs: Long) = apply { this.adPreloadTimeoutMs = adPreloadTimeoutMs }
        fun vastLoadTimeoutMs(vastLoadTimeoutMs: Int) = apply { this.vastLoadTimeoutMs = vastLoadTimeoutMs }
        fun mediaLoadTimeoutMs(mediaLoadTimeoutMs: Int) = apply { this.mediaLoadTimeoutMs = mediaLoadTimeoutMs }
        fun playAdBeforeStartPosition(playAdBeforeStartPosition: Boolean) = apply { this.playAdBeforeStartPosition = playAdBeforeStartPosition }
        fun focusSkipButtonWhenAvailable(focusSkipButtonWhenAvailable: Boolean) = apply { this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable }
        fun mediaBitrate(mediaBitrate: Int) = apply { this.mediaBitrate = mediaBitrate }
        fun adMediaMimeTypes(adMediaMimeTypes: List<String>?) = apply { this.adMediaMimeTypes = adMediaMimeTypes }
        fun companionAdSlots(companionAdSlots: Collection<CompanionAdSlot>?) = apply { this.companionAdSlots = companionAdSlots }
        fun applicationAdErrorListener(applicationAdErrorListener: AdErrorEvent.AdErrorListener? ) = apply { this.applicationAdErrorListener = applicationAdErrorListener }
        fun applicationAdEventListener(applicationAdEventListener: AdEvent.AdEventListener? ) = apply { this.applicationAdEventListener = applicationAdEventListener }
        fun applicationVideoAdPlayerCallback(applicationVideoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback? ) = apply { this.applicationVideoAdPlayerCallback = applicationVideoAdPlayerCallback }
        fun imaSdkSettings(imaSdkSettings: OmSdkSettings) = apply { this.imaSdkSettings = imaSdkSettings }
        fun debugModeEnabled(debugModeEnabled: Boolean) = apply { this.debugModeEnabled = debugModeEnabled }
        fun adsBehaviour(adsBehaviour: IAdsBehaviour) = apply { this.adsBehaviour = adsBehaviour }



        fun build(): Configuration {
            return Configuration(this)
        }
    }

    data class UserInfo(val userUIID: String?, val userToken: String?, val nonPersonalizedAd: Boolean,
                        val advertiserId: String)

    data class TrackersConfig(val omSdkUrl : String, val omPartnerName : String, val omContentUrl :String, val customRefrenceData :String)

}