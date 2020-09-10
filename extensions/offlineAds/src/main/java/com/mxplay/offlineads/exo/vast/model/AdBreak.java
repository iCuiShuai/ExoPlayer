package com.mxplay.offlineads.exo.vast.model;

import com.google.android.exoplayer2.C;
import com.mxplay.offlineads.exo.util.DateTimeUtils;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Objects;

public class AdBreak {
  private static final String TIME_OFFSET_START = "start";
  private static final String TIME_OFFSET_END = "end";
  private SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss", Locale.getDefault());
  /** Time in seconds **/
  private long startTime = C.TIME_UNSET;
  private int podIndex;
  private final VASTModel vastModel;

  public AdBreak(String startTime, VASTModel vastModel) {
    this.startTime = parseTimeOffset(startTime);
    this.vastModel = vastModel;
  }

  private long parseTimeOffset(String time) {
    long stime = C.TIME_UNSET;
    try {
      if (TIME_OFFSET_START.equals(time)){
        stime = 0L;
      }else if (TIME_OFFSET_END.equals(time)){
        stime = C.INDEX_UNSET;
      }else{
        stime = DateTimeUtils.getTimeInMillis(time)/1000;
      }
    } catch (Exception ignore) {
    }
    return stime;
  }


  public long getStartTime() {
    return startTime;
  }

  public VASTModel getVastModel() {
    return vastModel;
  }

  public int getPodIndex() {
    return podIndex;
  }

  public void setPodIndex(int podIndex) {
    this.podIndex = podIndex;
  }
}
