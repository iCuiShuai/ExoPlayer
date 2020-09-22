package com.mxplay.offlineads.exo.tracking;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public interface IVideoAdTracker {
    String AD_LOADER = "adLoader";

    String EVENT_VIDEO_AD_PLAY_SUCCESS = "VideoAdPlaySuccess";
    String EVENT_VIDEO_AD_PLAY_FAILED = "VideoAdPlayFailed";
    String EVENT_INTERNAL_ERROR = "InternalError";

    String REASON = "reason";
    String ERROR_NAME = "errorName";
    String UNKNOWN = "unknown";
    String REQUEST_TIME = "requestTime";
    String AD_GROUP_COUNT = "adGroupCount";
    String AD_GROUP_INDEX = "adGroupIndex";
    String LOAD_MEDIA_TIME = "loadMediaTime";
    String TOTAL_COST_TIME = "totalCostTime";
    String AD_INDEX_IN_AD_GROUP = "adIndexInAdGroup";

    static Map<String, String> buildSuccessParams(long adLoadedTime, long startRequestTime, long startLoadMediaTime, int adGroupIndex, int adGroupCount) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER, "OFFLINE-AD-LOADER");
        result.put(REQUEST_TIME, String.valueOf(adLoadedTime - startRequestTime));
        result.put(LOAD_MEDIA_TIME, String.valueOf(System.currentTimeMillis() - startLoadMediaTime));
        result.put(TOTAL_COST_TIME, String.valueOf(System.currentTimeMillis() - startRequestTime));
        result.put(AD_GROUP_INDEX, String.valueOf(adGroupIndex));
        result.put(AD_GROUP_COUNT, String.valueOf(adGroupCount));
        return result;
    }

    static Map<String, String> buildFailedParams(int adGroupIndex, long startRequestTime, Exception exception, int adGroupCount) {
        return buildFailedParams(adGroupIndex, -1, startRequestTime, null, exception, adGroupCount);
    }

    static Map<String, String> buildFailedParams(int adGroupIndex, int adIndexInAdGroup, long startRequestTime, String errorName, Exception exception, int adGroupCount) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER, "OFFLINE-AD-LOADER");
        if (adGroupIndex >= 0) {
            result.put(AD_GROUP_INDEX, String.valueOf(adGroupIndex));
        }
        if (adIndexInAdGroup >= 0) {
            result.put(AD_INDEX_IN_AD_GROUP, String.valueOf(adIndexInAdGroup));
        }
        if (adGroupCount > 0) {
            result.put(AD_GROUP_COUNT, String.valueOf(adGroupCount));
        }
        result.put(REASON, exception == null ? UNKNOWN : exception.toString());
        result.put(ERROR_NAME, errorName == null ? UNKNOWN : errorName);

        result.put(TOTAL_COST_TIME, String.valueOf(System.currentTimeMillis() - startRequestTime));
        return result;
    }

    void trackEvent(@NonNull String eventName, @NonNull Map<String, String> params);
}
