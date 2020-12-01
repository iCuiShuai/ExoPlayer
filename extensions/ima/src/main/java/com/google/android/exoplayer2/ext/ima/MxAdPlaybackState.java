package com.google.android.exoplayer2.ext.ima;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

public class MxAdPlaybackState extends AdPlaybackState {

    public MxAdPlaybackState(long... adGroupTimesUs) {
        super(adGroupTimesUs);
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
    public int getAdGroupIndexForPositionUs(long positionUs, long periodDurationUs) {
        int bsIndex = Util.binarySearchFloor(adGroupTimesUs, positionUs, true, false);
        int l_index = bsIndex;
        while (l_index >= 0 && isPositionBeforeAdGroup(positionUs, periodDurationUs, l_index)) {
            l_index--;
        }
        int f_index =  l_index >= 0 && adGroups[l_index].hasUnplayedAds() ? l_index : C.INDEX_UNSET;
        int o_index = super.getAdGroupIndexForPositionUs(positionUs, periodDurationUs);
        Assertions.checkState(f_index == o_index);
        return f_index;
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
    public int getAdGroupIndexAfterPositionUs(long positionUs, long periodDurationUs) {
        if (positionUs == C.TIME_END_OF_SOURCE
                || (periodDurationUs != C.TIME_UNSET && positionUs >= periodDurationUs)) {
            return C.INDEX_UNSET;
        }
        int bsIndex = Util.binarySearchCeil(adGroupTimesUs, positionUs, true, false);

        // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
        // In practice we expect there to be few ad groups so the search shouldn't be expensive.
        int index = bsIndex;
        while (index < adGroupTimesUs.length
                && adGroupTimesUs[index] != C.TIME_END_OF_SOURCE
                && (positionUs >= adGroupTimesUs[index] || !adGroups[index].hasUnplayedAds())) {
            index++;
        }
        int f_index =  index < adGroupTimesUs.length ? index : C.INDEX_UNSET;
        int o_index = super.getAdGroupIndexAfterPositionUs(positionUs, periodDurationUs);
        Assertions.checkState(f_index == o_index);
        return f_index;
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
