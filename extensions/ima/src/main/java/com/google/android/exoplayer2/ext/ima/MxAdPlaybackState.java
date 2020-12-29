package com.google.android.exoplayer2.ext.ima;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

public class MxAdPlaybackState extends AdPlaybackState {

    public MxAdPlaybackState(long... adGroupTimesUs) {
        super(adGroupTimesUs);
    }

    public MxAdPlaybackState(long[] adGroupTimesUs, AdGroup[] adGroups, long adResumePositionUs, long contentDurationUs) {
        super(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /** Optimise method which use binary search
     * Returns the index of the ad group at or before {@code positionUs}, if that ad group is
     * unplayed. Returns {@link C#INDEX_UNSET} if the ad group at or before {@code positionUs} has no
     * ads remaining to be played, or if there is no such ad group.
     *
     * @param positionUs The period position at or before which to find an ad group, in microseconds,
     *     or {@link C#TIME_END_OF_SOURCE} for the end of the stream (in which case the index of any
     *     unplayed postroll ad group will be returned).
     * @param periodDurationUs The duration of the containing timeline period, in microseconds, or
     *     {@link C#TIME_UNSET} if not known.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    @Override
    public int getAdGroupIndexForPositionUs(long positionUs, long periodDurationUs) {
        int index = Util.binarySearchFloor(adGroupTimesUs, positionUs, true, false);
        while (index >= 0 && isPositionBeforeAdGroup(positionUs, periodDurationUs, index)) {
            index--;
        }
        return index >= 0 && adGroups[index].hasUnplayedAds() ? index : C.INDEX_UNSET;
    }

    /** Optimise method which use binary search
     * Returns the index of the next ad group after {@code positionUs} that has ads remaining to be
     * played. Returns {@link C#INDEX_UNSET} if there is no such ad group.
     *
     * @param positionUs The period position after which to find an ad group, in microseconds, or
     *     {@link C#TIME_END_OF_SOURCE} for the end of the stream (in which case there can be no ad
     *     group after the position).
     * @param periodDurationUs The duration of the containing timeline period, in microseconds, or
     *     {@link C#TIME_UNSET} if not known.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    @Override
    public int getAdGroupIndexAfterPositionUs(long positionUs, long periodDurationUs) {
        if (positionUs == C.TIME_END_OF_SOURCE
                || (periodDurationUs != C.TIME_UNSET && positionUs >= periodDurationUs)) {
            return C.INDEX_UNSET;
        }
        int index = Util.binarySearchCeil(adGroupTimesUs, positionUs, true, false);

        while (index < adGroupTimesUs.length
                && adGroupTimesUs[index] != C.TIME_END_OF_SOURCE
                && (positionUs >= adGroupTimesUs[index] || !adGroups[index].hasUnplayedAds())) {
            index++;
        }
        return index < adGroupTimesUs.length ? index : C.INDEX_UNSET;
    }


    @Override
    public MxAdPlaybackState withAdCount(int adGroupIndex, int adCount) {
        Assertions.checkArgument(adCount > 0);
        if (adGroups[adGroupIndex].count == adCount) {
            return this;
        }
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = this.adGroups[adGroupIndex].withAdCount(adCount);
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad URI. */
    @CheckResult
    public AdPlaybackState withAdUri(int adGroupIndex, int adIndexInAdGroup, Uri uri) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdUri(uri, adIndexInAdGroup);
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad marked as played. */
    @CheckResult
    public AdPlaybackState withPlayedAd(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_PLAYED, adIndexInAdGroup);
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /**
     * Returns an instance with all ads in the specified ad group temporary enable/disable (except for those already
     * marked as played or in the error state).
     */
    @CheckResult
    public AdPlaybackState setAdGroupDisabled(int adGroupIndex, boolean disabled) {
        if (adGroupIndex == C.INDEX_UNSET) return this;
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        if (adGroupIndex >= adGroups.length) return this;
        if (!adGroups[adGroupIndex].hasUnplayedAds() && disabled) return this;
        adGroups[adGroupIndex].setDisabled(disabled);
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad marked as skipped. */
    @CheckResult
    public AdPlaybackState withSkippedAd(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_SKIPPED, adIndexInAdGroup);
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad marked as having a load error. */
    @CheckResult
    public AdPlaybackState withAdLoadError(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_ERROR, adIndexInAdGroup);
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /**
     * Returns an instance with all ads in the specified ad group skipped (except for those already
     * marked as played or in the error state).
     */
    @CheckResult
    public AdPlaybackState withSkippedAdGroup(int adGroupIndex) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAllAdsSkipped();
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad durations, in microseconds. */
    @CheckResult
    public AdPlaybackState withAdDurationsUs(long[][] adDurationUs) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        for (int adGroupIndex = 0; adGroupIndex < adGroupCount; adGroupIndex++) {
            adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdDurationsUs(adDurationUs[adGroupIndex]);
        }
        return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
    }

    /**
     * Returns an instance with the specified ad resume position, in microseconds, relative to the
     * start of the current ad.
     */
    @CheckResult
    public AdPlaybackState withAdResumePositionUs(long adResumePositionUs) {
        if (this.adResumePositionUs == adResumePositionUs) {
            return this;
        } else {
            return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
        }
    }

    /** Returns an instance with the specified content duration, in microseconds. */
    @CheckResult
    public AdPlaybackState withContentDurationUs(long contentDurationUs) {
        if (this.contentDurationUs == contentDurationUs) {
            return this;
        } else {
            return new MxAdPlaybackState(adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
        }
    }

    public static final class AdInfo {
        public final int adGroupIndex;
        public final int adIndexInAdGroup;

        public AdInfo(int adGroupIndex, int adIndexInAdGroup) {
            this.adGroupIndex = adGroupIndex;
            this.adIndexInAdGroup = adIndexInAdGroup;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AdInfo adInfo = (AdInfo) o;
            if (adGroupIndex != adInfo.adGroupIndex) {
                return false;
            }
            return adIndexInAdGroup == adInfo.adIndexInAdGroup;
        }

        @Override
        public int hashCode() {
            int result = adGroupIndex;
            result = 31 * result + adIndexInAdGroup;
            return result;
        }

        @Override
        public String toString() {
            return "(" + adGroupIndex + ", " + adIndexInAdGroup + ')';
        }
    }

}
