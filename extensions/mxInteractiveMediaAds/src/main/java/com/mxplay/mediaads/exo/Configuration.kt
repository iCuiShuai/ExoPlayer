package com.mxplay.mediaads.exo

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.C
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.AdsBehaviourOffline
import com.mxplay.adloader.IAdsBehaviour
import com.mxplay.interactivemedia.api.IMxAdCustomTracker
import com.mxplay.interactivemedia.api.MxMediaSdkConfig
import java.util.concurrent.ExecutorService

/** Stores configuration for ad loading and playback.  */
class Configuration(builder: Builder, appId: String) {

    companion object {
        const val DEFAULT_AD_PRELOAD_TIMEOUT_MS = 10 * C.MILLIS_PER_SECOND
    }

    val context: Context = builder.context
    val adUnitId: String
    val adPreloadTimeoutMs: Long

    val playAdBeforeStartPosition: Boolean
    val applicationVideoAdPlayerCallback: com.mxplay.interactivemedia.api.player.VideoAdPlayer.VideoAdPlayerCallback?
    val debugModeEnabled: Boolean
    val adsBehaviour: IAdsBehaviour
    val mxMediaSdkConfig: MxMediaSdkConfig
    val initialBufferSizeForAdPlaybackMs: Int
    val enableCustomTab : Boolean

    init {
        adUnitId = builder.adUnitId
        adPreloadTimeoutMs = builder.adPreloadTimeoutMs ?: DEFAULT_AD_PRELOAD_TIMEOUT_MS
        playAdBeforeStartPosition = builder.playAdBeforeStartPosition
        applicationVideoAdPlayerCallback = builder.applicationVideoAdPlayerCallback
        debugModeEnabled = builder.debugModeEnabled
        adsBehaviour = builder.adsBehaviour ?: AdsBehaviour(builder.vastLoadTimeoutMs
                ?: MxMediaSdkConfig.VAST_LOAD_TIMEOUT_MS, debugModeEnabled)
        enableCustomTab = builder.enableCustomTab
        mxMediaSdkConfig = MxMediaSdkConfig.Builder(context, builder.userInfo, builder.trackersConfig).apply {
            builder.adMediaMimeTypes?.let { this.adMediaMimeTypes = it }
            builder.vastLoadTimeoutMs?.let { this.vastLoadTimeoutMs = it }
            builder.mediaLoadTimeoutMs?.let { this.mediaLoadTimeoutMs = it }
            builder.companionAdSlots?.let { this.companionAdSlots = it }
            builder.mxAdCustomTracker?.let { this.mxAdCustomTracker = it }
            builder.adTagUri?.let { this.adTagUri = it }
            debugModeEnabled = builder.debugModeEnabled
            isOfflineAds = adsBehaviour is AdsBehaviourOffline
        }.build(appId)
        initialBufferSizeForAdPlaybackMs = builder.initialBufferSizeForAdPlaybackMs ?: -1

    }


    data class Builder(val context: Context,
                       val ioExecutor: ExecutorService, val userInfo: MxMediaSdkConfig.UserInfo) {

        var appName: String = ""
            private set

        var adUnitId: String = ""
        var adPreloadTimeoutMs: Long? = null
        var vastLoadTimeoutMs: Int? = null
        var mediaLoadTimeoutMs: Int? = null
        var playAdBeforeStartPosition: Boolean = true
        var adMediaMimeTypes: List<String>? = null
        var companionAdSlots: Collection<com.mxplay.interactivemedia.api.CompanionAdSlot>? = null
        var applicationVideoAdPlayerCallback: com.mxplay.interactivemedia.api.player.VideoAdPlayer.VideoAdPlayerCallback? = null
        var debugModeEnabled: Boolean = false
        var adsBehaviour: IAdsBehaviour? = null
        var trackersConfig: MxMediaSdkConfig.TrackersConfig? = null
        var mxAdCustomTracker: IMxAdCustomTracker? = null
        var adTagUri: Uri? = null
        var initialBufferSizeForAdPlaybackMs: Int? = null
        var enableCustomTab = false

        fun appName(appName: String) = apply { this.appName = appName }
        fun adUnitId(adUnitId: String) = apply { this.adUnitId = adUnitId }
        fun adPreloadTimeoutMs(adPreloadTimeoutMs: Long) = apply { this.adPreloadTimeoutMs = adPreloadTimeoutMs }
        fun vastLoadTimeoutMs(vastLoadTimeoutMs: Int) = apply {
            this.vastLoadTimeoutMs = if(vastLoadTimeoutMs > 0) vastLoadTimeoutMs else MxMediaSdkConfig.VAST_LOAD_TIMEOUT_MS
        }
        fun mediaLoadTimeoutMs(mediaLoadTimeoutMs: Int) = apply {
            this.mediaLoadTimeoutMs = if(mediaLoadTimeoutMs > 0) mediaLoadTimeoutMs else MxMediaSdkConfig.MEDIA_LOAD_TIMEOUT_MS
        }
        fun playAdBeforeStartPosition(playAdBeforeStartPosition: Boolean) = apply { this.playAdBeforeStartPosition = playAdBeforeStartPosition }
        fun adMediaMimeTypes(adMediaMimeTypes: List<String>?) = apply { this.adMediaMimeTypes = adMediaMimeTypes }
        fun companionAdSlots(companionAdSlots: Collection<com.mxplay.interactivemedia.api.CompanionAdSlot>?) = apply { this.companionAdSlots = companionAdSlots }
        fun applicationVideoAdPlayerCallback(applicationVideoAdPlayerCallback: com.mxplay.interactivemedia.api.player.VideoAdPlayer.VideoAdPlayerCallback?) = apply { this.applicationVideoAdPlayerCallback = applicationVideoAdPlayerCallback }
        fun debugModeEnabled(debugModeEnabled: Boolean) = apply { this.debugModeEnabled = debugModeEnabled }
        fun adsBehaviour(adsBehaviour: IAdsBehaviour) = apply { this.adsBehaviour = adsBehaviour }
        fun trackersConfig(trackersConfig: MxMediaSdkConfig.TrackersConfig) = apply { this.trackersConfig = trackersConfig }
        fun mxAdCustomTracker(mxAdCustomTracker: IMxAdCustomTracker) = apply { this.mxAdCustomTracker = mxAdCustomTracker }
        fun adTagUri(adTagUri: Uri) = apply { this.adTagUri = adTagUri }
        fun initialBufferSizeForAdPlaybackMs(initialBufferSizeForAdPlaybackMs: Int) = apply { this.initialBufferSizeForAdPlaybackMs = initialBufferSizeForAdPlaybackMs}
        fun enableCustomTab(enable : Boolean) = apply { this.enableCustomTab = enable }

        fun build(appId : String): Configuration {
            return Configuration(this, appId)
        }
    }


}