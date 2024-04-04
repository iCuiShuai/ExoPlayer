package com.mxplay.adloader;

import androidx.annotation.Nullable;

public final class AdPlaybackConfig {

    public final int initialBufferSizeForAdPlaybackMs;
    public final int initialBufferSizeForUrgentAdPlaybackMs;
    public final int thresholdForUrgentAdPlaybackMs;
    public final int totalAdBufferingThresholdMs;
    public  final int adBufferingThresholdMs;
    public final int adLoadTimeShiftMs;

    public AdPlaybackConfig(int initialBufferSizeForAdPlaybackMs,
                            int initialBufferSizeForUrgentAdPlaybackMs,
                            int thresholdForUrgentAdPlaybackMs,
                            int totalAdBufferingThresholdMs,
                            int adBufferingThresholdMs,
                            int adLoadTimeShiftMs) {
        this.initialBufferSizeForAdPlaybackMs = initialBufferSizeForAdPlaybackMs;
        this.initialBufferSizeForUrgentAdPlaybackMs = initialBufferSizeForUrgentAdPlaybackMs;
        this.thresholdForUrgentAdPlaybackMs = thresholdForUrgentAdPlaybackMs;
        this.totalAdBufferingThresholdMs = totalAdBufferingThresholdMs;
        this.adBufferingThresholdMs = adBufferingThresholdMs;
        this.adLoadTimeShiftMs = adLoadTimeShiftMs;

    }

    public static final class Builder {

        private int initialBufferSizeForAdPlaybackMs = -1;
        private int initialBufferSizeForUrgentAdPlaybackMs = -1;
        private int thresholdForUrgentAdPlaybackMs = -1;
        private int totalAdBufferingThresholdMs = -1;
        private int adBufferingThresholdMs = -1;
        private int adLoadTimeShiftMs = -1;

        public Builder setInitialBufferSizeForAdPlaybackMs(int initialBufferSizeForAdPlaybackMs) {
            this.initialBufferSizeForAdPlaybackMs = initialBufferSizeForAdPlaybackMs;
            return this;
        }

        public Builder setInitialBufferSizeForUrgentAdPlaybackMs(int initialBufferSizeForUrgentAdPlaybackMs) {
            this.initialBufferSizeForUrgentAdPlaybackMs = initialBufferSizeForUrgentAdPlaybackMs;
            return this;
        }

        public Builder setThresholdForUrgentAdPlaybackMs(int thresholdForUrgentAdPlaybackMs) {
            this.thresholdForUrgentAdPlaybackMs = thresholdForUrgentAdPlaybackMs;
            return this;
        }

        public Builder setTotalAdBufferingThresholdMs(int totalAdBufferingThresholdMs) {
            this.totalAdBufferingThresholdMs = totalAdBufferingThresholdMs;
            return this;
        }

        public Builder setAdBufferingThresholdMs(int adBufferingThresholdMs) {
            this.adBufferingThresholdMs = adBufferingThresholdMs;
            return this;
        }

        public Builder setAdLoadTimeShiftMs(int adLoadTimeShiftMs) {
            this.adLoadTimeShiftMs = adLoadTimeShiftMs;
            return this;
        }

        public AdPlaybackConfig build() {
            return new AdPlaybackConfig(
                    initialBufferSizeForAdPlaybackMs,
                    initialBufferSizeForUrgentAdPlaybackMs,
                    thresholdForUrgentAdPlaybackMs,
                    totalAdBufferingThresholdMs,
                    adBufferingThresholdMs,
                    adLoadTimeShiftMs);
        }

    }

}
