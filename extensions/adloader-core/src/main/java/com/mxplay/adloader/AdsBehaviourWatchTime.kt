package com.mxplay.adloader

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.PlaybackStatsListener
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.mxplay.adloader.exo.MxAdPlaybackState
import java.util.*

class AdsBehaviourWatchTime(durationSec: Long, vastTimeOutInMs: Int, videoAdsTracker: VideoAdsTracker? = null, debug : Boolean = false) : AdsBehaviourDefault(vastTimeOutInMs, videoAdsTracker, debug) {
    private val playbackStatsListener: PlaybackStatsListener = PlaybackStatsListener(true, null)
    private var player: Player? = null
    private val contentDurationMsApprox: Long = C.MICROS_PER_SECOND * durationSec
    private var totalAdLoads = 0
    private val actualAdGroupIndexByFake: MutableMap<Int, Int> = HashMap()

    override val trackerName: String
        get() = VideoAdsTracker.WATCH_TIME_BASE_AD_LOADER

    override fun setPlayer(player: Player?) {
        super.setPlayer(player)
        if (this.player != null && this.player is SimpleExoPlayer) {
            (this.player as SimpleExoPlayer?)!!.removeAnalyticsListener(playbackStatsListener)
        }
        this.player = player
        if (player is SimpleExoPlayer) {
            (this.player as SimpleExoPlayer?)!!.addAnalyticsListener(playbackStatsListener)
        }
    }

    override fun doSetupAdsRendering(contentPositionMs: Long, contentDurationMs: Long): Boolean {
        var adPlaybackState = adPlaybackStateHost.adPlaybackState
        val adGroupForPositionIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                C.msToUs(contentPositionMs), C.msToUs(contentDurationMs))
        if (adGroupForPositionIndex != C.INDEX_UNSET && !hasPrerollAdGroups(adPlaybackState.adGroupTimesUs)) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupForPositionIndex)
            adPlaybackStateHost.updateAdPlaybackState(adPlaybackState)
            if (debug) {
                Log.d(TAG, "Init ad rendering settings contentPositionMs : $contentPositionMs skipped ad index $adGroupForPositionIndex")
            }
        }
        return true
    }

    override fun handleAdLoad(adGroupIndex: Int, adIndexInAdGroup: Int) {
        val contentPositionMs = playbackStatsListener.contentTotalPlayTimeMs
        val adPlaybackState = adPlaybackStateHost.adPlaybackState
        val actualAdGroupIndex = getLoadingAdGroupIndexForReporting(adPlaybackState, C.msToUs(contentPositionMs))
        if (!actualAdGroupIndexByFake.containsValue(actualAdGroupIndex)) {
            actualAdGroupIndexByFake[adGroupIndex] = actualAdGroupIndex
        }
        if (adPlaybackState is MxAdPlaybackState) {
            adPlaybackState.withActualAdGroupProcessed(actualAdGroupIndex, adIndexInAdGroup)
        }
    }

    override fun handleAudioAdLoaded(podIndex: Int, adPosition: Int): Boolean {
        audioAdPodIndex = podIndex
        audioAdPosition = adPosition
        return true
    }

    override fun getContentPositionMs(player: Player, timeline: Timeline, period: Timeline.Period?, contentDurationMs: Long): Long {
        val hasContentDuration = contentDurationMs != C.INDEX_UNSET.toLong()
        if (hasContentDuration) {
            cleanUnusedCuePoints(player, timeline, period)
        }
        val contentPositionMs = playbackStatsListener.contentTotalPlayTimeMs
        tryUpdateStartRequestTime(contentPositionMs, contentDurationMs)
        return contentPositionMs
    }

    override fun getActualAdGroupIndex(fakeAdGroupIndex: Int): Int {
        val actualAdGroupIndex = actualAdGroupIndexByFake[fakeAdGroupIndex]
        return actualAdGroupIndex ?: C.INDEX_UNSET
    }

    override fun getMediaLoadTimeout(defaultTimout: Int): Int {
        return 2 * NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD
    }

    private fun generateFakeAdGroupsTimesUs(adGroupTimesUs: LongArray): LongArray {
        val fakeCuePoints: MutableList<Long> = ArrayList()
        val distance = Math.round((C.MICROS_PER_SECOND * FAKE_CUEPOINTS_DISTANCE).toFloat()).toLong()
        var start = distance // 10
        for (i in 0 until contentDurationMsApprox / distance + 1) {  // 3 < 2
            fakeCuePoints.add(start)
            start += distance
        }
        for (cuePoint in adGroupTimesUs) {
            if (cuePoint == C.TIME_END_OF_SOURCE) {
                fakeCuePoints.add(C.TIME_END_OF_SOURCE)
            } else if (cuePoint == 0L) {
                fakeCuePoints.add(0, 0L)
            }
        }
        val fakeAdGroupTimeUs = LongArray(fakeCuePoints.size)
        for (j in fakeCuePoints.indices) {
            fakeAdGroupTimeUs[j] = fakeCuePoints[j]
        }
        return fakeAdGroupTimeUs
    }

    override fun createAdPlaybackState(adId: Any?, adGroupTimesUs: LongArray): AdPlaybackState {
        val fakeAdGroupTimesUs = generateFakeAdGroupsTimesUs(adGroupTimesUs)
        return MxAdPlaybackState(adId, fakeAdGroupTimesUs, adGroupTimesUs)
    }

    override fun handleTimelineOrPositionChanged(player: Player?, timeline: Timeline?, period: Timeline.Period?): Boolean {
        super.handleTimelineOrPositionChanged(player, timeline, period)
        skipAdOnUserSeek(player, timeline, period)
        return true
    }

    private fun skipAdOnUserSeek(player: Player?, timeline: Timeline?, period: Timeline.Period?) {
        if (!timeline!!.isEmpty) {
            var adPlaybackState = adPlaybackStateHost.adPlaybackState
            var updateState = false
            val positionMs = getContentPeriodPositionMs(player!!, timeline, period)
            timeline.getPeriod( /* periodIndex= */0, period!!)
            val newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs))
            if (newAdGroupIndex != C.INDEX_UNSET && adPlaybackState.adGroups[newAdGroupIndex].count < 0) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(newAdGroupIndex)
                updateState = true
                if (debug) {
                    Log.d(TAG, "Ad skipped on user seek onTimelineChanged/onPositionDiscontinuity $newAdGroupIndex")
                }
            }
            if (updateState) adPlaybackStateHost.updateAdPlaybackState(adPlaybackState)
        }
    }

    private fun cleanUnusedCuePoints(player: Player, timeline: Timeline, period: Timeline.Period?) {
        var adPlaybackState = adPlaybackStateHost.adPlaybackState
        val positionUs = C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period))
        var shouldUpdatePlaybackState = false
        while (true) {
            val adGroupIndexAfterPositionUs = adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, C.msToUs(contentDurationMs))
            if (adGroupIndexAfterPositionUs != C.INDEX_UNSET && adPlaybackState.adGroups[adGroupIndexAfterPositionUs].count < 0) {
                val nextAdTimeOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs] - positionUs)
                if (nextAdTimeOffsetMs < NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD) {
                    adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndexAfterPositionUs)
                    shouldUpdatePlaybackState = true
                    if (debug) Log.d(TAG, " Skipped fake cuepoint " + adGroupIndexAfterPositionUs + " -- pos: " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs]))
                } else break
            } else break
        }
        if (shouldUpdatePlaybackState) adPlaybackStateHost.updateAdPlaybackState(adPlaybackState)
    }

    override fun getAdGroupIndexForAdPod(podIndex: Int, podTimeOffset: Double, player: Player?, timeline: Timeline?, period: Timeline.Period?): Int {
        totalAdLoads++
        val isAudioAd = podIndex == audioAdPodIndex
        val adPlaybackState = adPlaybackStateHost.adPlaybackState
        val allAdsDone = (adPlaybackState as MxAdPlaybackState).actualAdGroupCount == totalAdLoads
        if (podIndex == -1) {
            // This is a postroll ad.
            if (allAdsDone) skipAllFakeCuePoints(adPlaybackState, adPlaybackState.adGroupCount - 1)
            check(!isAudioAd) { "Audio ad not supported in watch-time" }
            return adPlaybackState.adGroupCount - 1
        }
        check(!isAudioAd) { "Audio ad not supported in watch-time" }
        val positionUs = C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline!!, period))
        if (debug) {
            Log.d(TAG, " Player position " + C.usToMs(positionUs))
        }
        val adGroupIndex = getFakeCuepointForLoadingAd(positionUs, adPlaybackState)
        if (allAdsDone) skipAllFakeCuePoints(adPlaybackState, adGroupIndex)
        if (adGroupIndex != C.INDEX_UNSET) return adGroupIndex
        throw IllegalStateException("Failed to find cue point")
    }

    private fun getFakeCuepointForLoadingAd(positionUs: Long, adPlaybackState: AdPlaybackState): Int {
        var adPlaybackState = adPlaybackState
        val adGroupIndex = getLoadingAdGroupIndex(adPlaybackState, positionUs)
        if (adGroupIndex != C.INDEX_UNSET) {
            val timeLeftMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex] - positionUs)
            if (timeLeftMs > 0 && timeLeftMs < NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD) {
                if (debug) {
                    Log.d(TAG, "Next cue-point too close  " + adGroupIndex + " time : " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]) + " time left : " + timeLeftMs)
                }
                adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex)
                return getFakeCuepointForLoadingAd(positionUs, adPlaybackState)
            }
            if (debug) {
                Log.d(TAG, " Loading  Next Ad  " + adGroupIndex + " time : " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]) + " time left : " + timeLeftMs)
            }
            return adGroupIndex
        }
        return C.INDEX_UNSET
    }

    private fun skipAllFakeCuePoints(adPlaybackState: AdPlaybackState, keepAdGroupIndex: Int) {
        // skip all ads
        var adPlaybackState = adPlaybackState
        try {
            for (i in adPlaybackState.adGroupTimesUs.indices) {
                if (keepAdGroupIndex == i) continue
                adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
            }
            adPlaybackStateHost.updateAdPlaybackState(adPlaybackState)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (debug) {
            Log.d(TAG, " skipped all fake cue points ")
        }
    }

    override fun getLoadingAdGroupIndexForReporting(adPlaybackState: AdPlaybackState, playerPositionUs: Long): Int {
        val mxAdPlaybackState = adPlaybackState as MxAdPlaybackState
        var adGroupIndex = mxAdPlaybackState.getActualAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs))
        if (adGroupIndex == C.INDEX_UNSET) {
            adGroupIndex = mxAdPlaybackState.getActualAdGroupIndexAfterPositionUs(
                    playerPositionUs, C.msToUs(contentDurationMs))
        }
        return adGroupIndex
    }

    companion object {
        private const val TAG = "AdsBehaviourWatchTime"
        private const val FAKE_CUEPOINTS_DISTANCE = 10 // in secs
        const val NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD = 8000 // 8 sec
        private fun hasPrerollAdGroups(adGroupTimesUs: LongArray): Boolean {
            val count = adGroupTimesUs.size
            return count > 0 && adGroupTimesUs[0] == 0L
        }
    }

}