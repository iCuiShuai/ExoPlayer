package com.mxplay.interactivemedia.internal.tracking

import android.text.TextUtils
import android.util.Log
import android.view.View
import com.iab.omid.library.mxplayerin.adsession.AdEvents
import com.iab.omid.library.mxplayerin.adsession.AdSession
import com.iab.omid.library.mxplayerin.adsession.ErrorType
import com.iab.omid.library.mxplayerin.adsession.FriendlyObstructionPurpose
import com.iab.omid.library.mxplayerin.adsession.media.InteractionType
import com.iab.omid.library.mxplayerin.adsession.media.MediaEvents
import com.iab.omid.library.mxplayerin.adsession.media.Position
import com.iab.omid.library.mxplayerin.adsession.media.VastProperties
import com.mxplay.interactivemedia.api.AdError
import com.mxplay.interactivemedia.internal.api.FriendlyObstructionProvider
import com.mxplay.interactivemedia.internal.data.model.AdVerification

class OmidPixelTrackerImpl(private val mxOmid: MxOmid, private var adView: View?, private val adVerification: AdVerification, private val mute: Boolean) : IAdPixelTracker {
    private var adSession: AdSession? = null
    private var adEvents: AdEvents? = null
    private var mediaEvents: MediaEvents? = null
    var state = 0

    override fun startSession(friendlyObstructionProvider: FriendlyObstructionProvider) {
        adSession = mxOmid.createSession(adView!!.context, adVerification, true)
        try {
            if (adSession != null) {
                adEvents = AdEvents.createAdEvents(adSession)
                mediaEvents = MediaEvents.createMediaEvents(adSession)
                adSession!!.registerAdView(adView)
                addFriendlyObstruction(friendlyObstructionProvider, adSession)
                adSession!!.start()
            }
        } catch (e: Exception) {
            Log.e("MxOmid", "Error creating session ", e)
        }
    }

    private fun addFriendlyObstruction(friendlyObstructionProvider: FriendlyObstructionProvider?, adSession: AdSession?) {
        if (adSession != null && friendlyObstructionProvider != null) {
            val friendlyObstructions = friendlyObstructionProvider.friendlyObstructions
            if (friendlyObstructions != null && !friendlyObstructions.isEmpty()) {
                for (friendlyObstruction in friendlyObstructions) {
                    if (friendlyObstruction != null && friendlyObstruction.view != null) {
                        adSession.addFriendlyObstruction(friendlyObstruction.view, getPurpose(friendlyObstruction.purpose.name), friendlyObstruction.detailedReason)
                    }
                }
            }
        }
    }

    private fun getPurpose(purpose: String): FriendlyObstructionPurpose {
        if (TextUtils.isEmpty(purpose)) {
            return FriendlyObstructionPurpose.OTHER
        }
        if (purpose.equals(FriendlyObstructionPurpose.CLOSE_AD.name, ignoreCase = true)) {
            return FriendlyObstructionPurpose.CLOSE_AD
        } else if (purpose.equals(FriendlyObstructionPurpose.NOT_VISIBLE.name, ignoreCase = true)) {
            return FriendlyObstructionPurpose.NOT_VISIBLE
        } else if (purpose.equals(FriendlyObstructionPurpose.VIDEO_CONTROLS.name, ignoreCase = true)) {
            return FriendlyObstructionPurpose.VIDEO_CONTROLS
        }
        return FriendlyObstructionPurpose.OTHER
    }

    override fun finishSession() {
        if (adSession != null) {
            adSession!!.finish()
            adSession = null
        }
        mediaEvents = null
        adEvents = null
        adView = null
    }

    override fun videoBuffering(isBuffering: Boolean) {
        mediaEvents?.let { if (isBuffering) it.bufferStart() else it.bufferFinish() }
    }

    override fun skippedAd() {
        mediaEvents?.skipped()
    }

    override fun clickAd() {
        mediaEvents?.adUserInteraction(InteractionType.CLICK)
    }

    override fun start(duration: Float, volume: Float, skipOffset: Float) {
        val properties: VastProperties? = if (skipOffset >= 0) {
            VastProperties.createVastPropertiesForSkippableMedia(skipOffset, true, Position.STANDALONE)
        } else {
            VastProperties.createVastPropertiesForNonSkippableMedia(true, Position.STANDALONE)
        }
        adEvents?.loaded(properties!!)
        adEvents?.impressionOccurred()
        mediaEvents?.start(duration, volume)
    }

    override fun firstQuartile() {
        mediaEvents?.firstQuartile()
    }

    override fun midpoint() {
        mediaEvents?.midpoint()
    }

    override fun thirdQuartile() {
        mediaEvents?.thirdQuartile()
    }

    override fun completed() {
        mediaEvents?.complete()
    }

    override fun onError(errorCode: Int, adErrorType: AdError.AdErrorType) {
        adSession?.error(getErrorType(adErrorType), errorCode.toString())
    }
    override fun paused() {
        mediaEvents?.pause()
    }

    override fun resumed() {
        mediaEvents?.resume()
    }

    override fun loaded() {
    }

    override fun hasSession(): Boolean {
        return adSession != null
    }

    override fun getAdVerification(): AdVerification {
        return adVerification
    }

    private fun getErrorType(adErrorType: AdError.AdErrorType): ErrorType {
        return if (adErrorType == AdError.AdErrorType.PLAY) ErrorType.VIDEO
        else ErrorType.GENERIC
    }


}