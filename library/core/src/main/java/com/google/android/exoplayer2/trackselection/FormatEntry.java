package com.google.android.exoplayer2.trackselection;

import com.google.android.exoplayer2.Format;
import java.util.List;

public class FormatEntry implements Comparable<FormatEntry> {
    private Format format;
    private int groupIndex;
    private int trackIndex;
    private List<String> sortList;
    private boolean video = true;

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

    public void setSortList(List<String> sortList) {
        this.sortList = sortList;
        video = false;
    }

    @Override
    public int compareTo(FormatEntry o) {
        if (!video) {
            return compareAudioOrSubtitle(o);
        }
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

    private int compareAudioOrSubtitle(FormatEntry o) {
        int current = sortList.indexOf(format.language);
        int next = sortList.indexOf(o.format.language);
        if (current == next) {
            return 0;
        } else if (current > next) {
            return 1;
        } else {
            return -1;
        }
    }
}