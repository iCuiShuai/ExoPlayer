package com.google.android.exoplayer2.demo;

import com.google.android.exoplayer2.seek.SeekPreprocessor;
import com.google.android.exoplayer2.source.dash.DashSeekPreprocessor;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.source.hls.HlsSeekPreprocessor;

public class ComposedSeekPreprocessor implements SeekPreprocessor {

    DashSeekPreprocessor dashSeekPreProcessor = new DashSeekPreprocessor();
    HlsSeekPreprocessor hlsSeekPreProcessor = new HlsSeekPreprocessor();

    @Override
    public long onSeekTo(Object manifest, int windowIndex, int periodIndex, long positionMs) {
        if (manifest instanceof DashManifest) {
            return dashSeekPreProcessor.onSeekTo((DashManifest) manifest, windowIndex, periodIndex, positionMs);
        }else if (manifest instanceof HlsManifest) {
            return hlsSeekPreProcessor.onSeekTo((HlsManifest) manifest, windowIndex, periodIndex, positionMs);
        }

        return positionMs;
    }
}
