package com.mxplay.offlineads.exo.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtils {


  public static long getTimeInMillis(String source) {
    int millis = 0;
    //00:00:15.000
    String[] split = source.split("\\.");
    if (split.length > 1) {
      source = split[0];
      millis = Integer.parseInt(split[1]);
    }
    String[] tokens = source.split(":");
    int secondsToMs = (tokens.length > 2) ? Integer.parseInt(tokens[2]) * 1000 : 0;
    int minutesToMs = Integer.parseInt(tokens[1]) * 60000;
    int hoursToMs = Integer.parseInt(tokens[0]) * 3600000;
    return millis + secondsToMs + minutesToMs + hoursToMs;
  }

  public static String formatTime(float time) {
    return String.format(Locale.US, "%02d:%02d", (int)(time % 3600) / 60, (int)(time % 60));
  }
}
