package com.google.android.exoplayer2.source.hls;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.seek.SeekPreprocessor;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;

public class HlsSeekPreprocessor implements SeekPreprocessor<HlsManifest> {
    @Override
    public long onSeekTo(HlsManifest manifest, int windowIndex, int periodIndex, long positionMs) {

        long baseStartUs = manifest.mediaPlaylist.startTimeUs;
        long positionUs = C.msToUs(positionMs);
        long lastLess = -1;

        for (HlsMediaPlaylist.Segment segment : manifest.mediaPlaylist.segments) {
            long startTimeUs = baseStartUs + segment.relativeStartTimeUs;

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

        return positionMs;
    }
}
