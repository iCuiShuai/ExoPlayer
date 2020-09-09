//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.offlineads.exo.oma;

public interface Ad {
    String getAdId();
    boolean isSkippable();
    double getSkipTimeOffset();
    String getDescription();
    String getTitle();
    String getAdvertiserName();
    long getDuration();
    AdPodInfo getAdPodInfo();
    String getMediaUrl();
}
