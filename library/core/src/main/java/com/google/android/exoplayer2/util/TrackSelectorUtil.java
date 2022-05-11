package com.google.android.exoplayer2.util;

import com.google.android.exoplayer2.C;

public class TrackSelectorUtil {
    public static int indexOf(int[] tracks, int indexInTrackGroup) {
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i] == indexInTrackGroup) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }
}
