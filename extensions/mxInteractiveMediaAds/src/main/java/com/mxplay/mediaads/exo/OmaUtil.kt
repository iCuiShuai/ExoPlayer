
package com.mxplay.mediaads.exo

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.source.ads.AdsLoader.OverlayInfo
import com.google.android.exoplayer2.upstream.DataSchemeDataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.mxplay.interactivemedia.api.*
import com.mxplay.interactivemedia.api.player.VideoAdPlayer
import com.mxplay.interactivemedia.api.player.VideoProgressUpdate
import java.io.IOException
import java.util.*

class OmaUtil {


    interface OmaFactory {


        /**
         * Creates [AdsRenderingSettings] for giving the [com.mxplay.offlineads.exo.oma.AdsManager] parameters that
         * control rendering of ads.
         */
        fun createAdsRenderingSettings(): AdsRenderingSettings

        /**
         * Creates an [AdDisplayContainer] to hold the player for video ads, a container for
         * non-linear ads, and slots for companion ads.
         */
        fun createAdDisplayContainer(container: ViewGroup?, player: VideoAdPlayer?, detectObstruction : Boolean): AdDisplayContainer

        /** Creates an [AdDisplayContainer] to hold the player for audio ads.  */
        fun createAudioAdDisplayContainer(context: Context?, player: VideoAdPlayer?): AdDisplayContainer

        /**
         * Creates a [FriendlyObstruction] to describe an obstruction considered "friendly" for
         * viewability measurement purposes.
         */
        fun createFriendlyObstruction(
                view: View?,
                friendlyObstructionPurpose: FriendlyObstructionPurpose?,
                reasonDetail: String?): FriendlyObstruction?

        /** Creates an [AdsRequest] to contain the data used to request ads.  */
        fun createAdsRequest(): AdsRequest

        /** Creates an [AdsLoader] for requesting ads using the specified settings.  */
        fun createAdsLoader(
                context: Context?, adDisplayContainer: AdDisplayContainer?, configuration: Configuration): AdsLoader?
    }


    companion object {
        const val TIMEOUT_UNSET = -1
        const val BITRATE_UNSET = -1

        /**
         * Returns the IMA [FriendlyObstructionPurpose] corresponding to the given [ ][AdOverlayInfo.purpose].
         */
        @JvmStatic
        fun getFriendlyObstructionPurpose(
                @OverlayInfo.Purpose purpose: Int): FriendlyObstructionPurpose {
            return when (purpose) {
                OverlayInfo.PURPOSE_CONTROLS -> FriendlyObstructionPurpose.VIDEO_CONTROLS
                OverlayInfo.PURPOSE_CLOSE_AD -> FriendlyObstructionPurpose.CLOSE_AD
                OverlayInfo.PURPOSE_NOT_VISIBLE -> FriendlyObstructionPurpose.NOT_VISIBLE
                OverlayInfo.PURPOSE_OTHER -> FriendlyObstructionPurpose.OTHER
                else -> FriendlyObstructionPurpose.OTHER
            }
        }

        /**
         * Returns the microsecond ad group timestamps corresponding to the specified cue points.
         *
         * @param cuePoints The cue points of the ads in seconds, provided by the IMA SDK.
         * @return The corresponding microsecond ad group timestamps.
         */
        @JvmStatic
        fun getAdGroupTimesUsForCuePoints(cuePoints: List<Float>): LongArray {
            if (cuePoints.isEmpty()) {
                return longArrayOf(0L)
            }
            val count = cuePoints.size
            val adGroupTimesUs = LongArray(count)
            var adGroupIndex = 0
            for (i in 0 until count) {
                val cuePoint = cuePoints[i].toDouble()
                if (cuePoint == -1.0) {
                    adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE
                } else {
                    adGroupTimesUs[adGroupIndex++] = Math.round(C.MICROS_PER_SECOND * cuePoint)
                }
            }
            // Cue points may be out of order, so sort them.
            Arrays.sort(adGroupTimesUs, 0, adGroupIndex)
            return adGroupTimesUs
        }

        /** Returns an [AdsRequest] based on the specified ad tag [DataSpec].  */
        @JvmStatic
        @Throws(IOException::class)
        fun getAdsRequestForAdTagDataSpec(
                omaFactory: OmaFactory, adTagDataSpec: DataSpec): AdsRequest {
            val request = omaFactory.createAdsRequest()
            if (DataSchemeDataSource.SCHEME_DATA == adTagDataSpec.uri.scheme) {
                val dataSchemeDataSource = DataSchemeDataSource()
                try {
                    dataSchemeDataSource.open(adTagDataSpec)
                    request.adsResponse = Util.fromUtf8Bytes(Util.readToEnd(dataSchemeDataSource))
                } finally {
                    dataSchemeDataSource.close()
                }
            } else {
                request.adTagUrl = adTagDataSpec.uri.toString()
            }
            return request
        }

        /** Returns whether the ad error indicates that an entire ad group failed to load.  */
        @JvmStatic
        fun isAdGroupLoadError(adError: AdError): Boolean {
            // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
            // a single ad, ad group or the whole timeline.
            return (adError.errorCode == AdError.AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
                    || adError.errorCode == AdError.AdErrorCode.MEDIA_DURATION_MISMATCH
                    || adError.errorCode == AdError.AdErrorCode.UNKNOWN_ERROR)
        }



        @JvmStatic
        val imaLooper: Looper
            get() =// IMA SDK callbacks occur on the main thread. This method can be used to check that the player
                    // is using the same looper, to ensure all interaction with this class is on the main thread.
                Looper.getMainLooper()

        /** Returns a human-readable representation of a video progress update.  */
        @JvmStatic
        fun getStringForVideoProgressUpdate(videoProgressUpdate: VideoProgressUpdate): String {
            return if (VideoProgressUpdate.VIDEO_TIME_NOT_READY == videoProgressUpdate) {
                "not ready"
            } else {
                Util.formatInvariant(
                        "%d ms of %d ms",
                        videoProgressUpdate.currentTimeMs, videoProgressUpdate.durationMs)
            }
        }
    }
}