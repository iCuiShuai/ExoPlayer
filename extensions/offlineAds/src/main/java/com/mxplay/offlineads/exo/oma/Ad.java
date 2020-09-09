//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.offlineads.exo.oma;

import java.util.List;

public interface Ad {
    String getAdId();

    String getCreativeId();

    String getCreativeAdId();

    String getUniversalAdIdValue();

    String getUniversalAdIdRegistry();

    String getAdSystem();

    String[] getAdWrapperIds();

    String[] getAdWrapperSystems();

    String[] getAdWrapperCreativeIds();

    boolean isLinear();

    boolean isSkippable();

    double getSkipTimeOffset();


    String getDescription();

    String getTitle();

    String getContentType();

    String getAdvertiserName();

    String getSurveyUrl();

    String getDealId();

    int getWidth();

    int getHeight();

    int getVastMediaWidth();

    int getVastMediaHeight();

    int getVastMediaBitrate();

    String getTraffickingParameters();

    long getDuration();

    AdPodInfo getAdPodInfo();
    String getMediaUrl();
}
