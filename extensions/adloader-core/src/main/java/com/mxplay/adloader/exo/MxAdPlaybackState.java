package com.mxplay.adloader.exo;

import android.net.Uri;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

public class MxAdPlaybackState extends AdPlaybackState {

    /** The number of ad groups. */
    public final int actualAdGroupCount;

    /** The ad groups. */
    public  AdGroup[] actualAdGroups;
    public final long[] actualAdGroupTimeUs;


    public MxAdPlaybackState(Object adsId, long[] adGroupTimesUs, long[] actualAdGroupTimesUs) {
        this(
                adsId,
                adGroupTimesUs,
                /* adGroups= */ null,
                actualAdGroupTimesUs,
                /* adGroups= */ null,
                /* adResumePositionUs= */ 0,
                /* contentDurationUs= */ C.TIME_UNSET);
    }

    public MxAdPlaybackState(Object adsId, long[] adGroupTimesUs, @Nullable AdGroup[] adGroups, long[] actualAdGroupTimeUs, @Nullable AdGroup[] actualAdGroups,  long adResumePositionUs,
                             long contentDurationUs) {
        super(adsId, adGroupTimesUs, adGroups, adResumePositionUs, contentDurationUs);
        this.actualAdGroupTimeUs = actualAdGroupTimeUs;
        this.actualAdGroupCount = actualAdGroupTimeUs.length;
        if (actualAdGroups == null){
            actualAdGroups = new AdGroup[actualAdGroupCount];
            for (int i = 0; i < actualAdGroupCount; i++) {
                actualAdGroups[i] = new AdGroup();
            }
        }
        this.actualAdGroups = actualAdGroups;
    }

    /**
     * Returns an instance with the number of ads in {@code adGroupIndex} resolved to {@code adCount}.
     * The ad count must be greater than zero.
     */
    @CheckResult
    public AdPlaybackState withAdCount(int adGroupIndex, int adCount) {
        Assertions.checkArgument(adCount > 0);
        if (adGroups[adGroupIndex].count == adCount) {
            return this;
        }
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = this.adGroups[adGroupIndex].withAdCount(adCount);
        return new MxAdPlaybackState(
                adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad URI. */
    @CheckResult
    public AdPlaybackState withAdUri(int adGroupIndex, int adIndexInAdGroup, Uri uri) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdUri(uri, adIndexInAdGroup);
        return new MxAdPlaybackState(
                adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad marked as played. */
    @CheckResult
    public AdPlaybackState withPlayedAd(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_PLAYED, adIndexInAdGroup);
        return new MxAdPlaybackState(
                adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad marked as skipped. */
    @CheckResult
    public AdPlaybackState withSkippedAd(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_SKIPPED, adIndexInAdGroup);
        return new MxAdPlaybackState(
                adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups,  adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad marked as having a load error. */
    @CheckResult
    public AdPlaybackState withAdLoadError(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(AD_STATE_ERROR, adIndexInAdGroup);
        return new MxAdPlaybackState(
                adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
    }

    /**
     * Returns an instance with all ads in the specified ad group skipped (except for those already
     * marked as played or in the error state).
     */
    @CheckResult
    public AdPlaybackState withSkippedAdGroup(int adGroupIndex) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAllAdsSkipped();
        return new MxAdPlaybackState(
                adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
    }

    /** Returns an instance with the specified ad durations, in microseconds. */
    @CheckResult
    public AdPlaybackState withAdDurationsUs(long[][] adDurationUs) {
        AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
        for (int adGroupIndex = 0; adGroupIndex < adGroupCount; adGroupIndex++) {
            adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdDurationsUs(adDurationUs[adGroupIndex]);
        }
        return new MxAdPlaybackState(
                adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
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
            return new MxAdPlaybackState(
                    adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
        }
    }

    /** Returns an instance with the specified content duration, in microseconds. */
    @CheckResult
    public AdPlaybackState withContentDurationUs(long contentDurationUs) {
        if (this.contentDurationUs == contentDurationUs) {
            return this;
        } else {
            return new MxAdPlaybackState(
                    adsId, adGroupTimesUs, adGroups, actualAdGroupTimeUs, actualAdGroups, adResumePositionUs, contentDurationUs);
        }
    }


}
