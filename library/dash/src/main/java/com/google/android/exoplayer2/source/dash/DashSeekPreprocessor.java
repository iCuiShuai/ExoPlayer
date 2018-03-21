package com.google.android.exoplayer2.source.dash;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.seek.SeekPreprocessor;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class DashSeekPreprocessor implements SeekPreprocessor<DashManifest> {
    @Override
    public long onSeekTo(DashManifest manifest, int windowIndex, int periodIndex, long positionMs) {
        Period period = manifest.getPeriod(periodIndex);

        if (period.adaptationSets.size() <= 0) {
            return positionMs;
        }

        ArrayList<AdaptationSet> videoSets = new ArrayList<>();

        for (AdaptationSet adaptationSet : period.adaptationSets) {
            if (adaptationSet.type == C.TRACK_TYPE_VIDEO) {
                videoSets.add(adaptationSet);
            }
        }

        Collections.sort(videoSets, new Comparator<AdaptationSet>() {
            @Override
            public int compare(AdaptationSet o1, AdaptationSet o2) {
                return o1.id - o2.id;
            }
        });

        AdaptationSet videoAdaptationSet = videoSets.get(0);

        Representation videoRepresentation = videoAdaptationSet.representations.get(0);

        long positionUs = C.msToUs(positionMs);

        long position = findPosition(period, videoRepresentation, positionUs);

        if (position == -1) {
            return positionMs;
        }else {
            return position;
        }
    }

    private long findPosition(Period period, Representation representation, long positionUs) {
        DashSegmentIndex index = representation.getIndex();

        if (index == null) {
            return -1;
        }

        int segmentCount = index.getSegmentCount(C.TIME_UNSET);
        if (segmentCount == DashSegmentIndex.INDEX_UNBOUNDED) {
            return -1;
        }

        long startUs = C.msToUs(period.startMs);

        long lastLess = -1;
        long firstSegmentNum = index.getFirstSegmentNum();
        long lastSegmentNum = firstSegmentNum + segmentCount - 1;

        for (long j = firstSegmentNum; j <= lastSegmentNum; j++) {
            long startTimeUs = startUs + index.getTimeUs(j);

            if (startTimeUs == positionUs) {
                return positionUs;
            }
            if (startTimeUs < positionUs) {
                lastLess = startTimeUs;

            }else {
                if (lastLess != -1) {
                    return C.usToMs(lastLess);
                }
            }
        }

        return -1;
    }
}
