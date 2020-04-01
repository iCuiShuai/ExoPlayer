package com.google.android.exoplayer2.trackselection;

import com.google.android.exoplayer2.Format;

public class FormatEntry implements Comparable<FormatEntry> {
    private Format format;
    private int groupIndex;
    private int trackIndex;

    public static boolean equals(FormatEntry first, FormatEntry second)
    {
        return first.getFormat().height == second.getFormat().height;
    }

    public static FormatEntry create(Format format, int groupIndex, int trackIndex) {
        return new FormatEntry(format, groupIndex, trackIndex);
    }

    public Format getFormat() {
        return format;
    }

    public int getGroupIndex() {
        return groupIndex;
    }

    public int getTrackIndex() {
        return trackIndex;
    }

    private FormatEntry(Format format, int groupIndex, int trackIndex) {
        this.format = format;
        this.groupIndex = groupIndex;
        this.trackIndex = trackIndex;
    }

    @Override
    public int compareTo(FormatEntry o) {
        if (format.height > o.format.height) {
            return 1;
        }
        else if (format.height < o.format.height) {
            return -1;
        }
        else if (format.bitrate > o.format.bitrate) {
            return 1;
        }
        else if ( format.bitrate < o.format.bitrate) {
            return -1;
        }
        return 0;
    }
}