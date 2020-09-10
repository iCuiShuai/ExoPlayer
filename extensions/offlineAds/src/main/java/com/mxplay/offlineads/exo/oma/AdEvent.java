//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.offlineads.exo.oma;

import java.util.Map;

public interface AdEvent {
    AdEvent.AdEventType getType();

    Ad getAd();

    Map<String, String> getAdData();

    public static enum AdEventType {
        ALL_ADS_COMPLETED,
        CLICKED,
        COMPLETED,
        CONTENT_PAUSE_REQUESTED,
        CONTENT_RESUME_REQUESTED,
        LOG,
        PAUSED,
        RESUMED,
        SKIPPED,
        STARTED,
        TAPPED,
        LOADED,
        AD_PROGRESS;

        private AdEventType() {
        }
    }

    public interface AdEventListener {
        void onAdEvent(AdEvent var1);
    }
}
