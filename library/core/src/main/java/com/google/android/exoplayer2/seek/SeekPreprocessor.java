package com.google.android.exoplayer2.seek;

public interface SeekPreprocessor<T> {
    long onSeekTo(T manifest, int windowIndex, int periodIndex, long position);
}
