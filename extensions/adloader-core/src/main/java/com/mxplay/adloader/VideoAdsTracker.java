package com.mxplay.adloader;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class VideoAdsTracker {
    
    public static final String AD_LOADER_NAME = "adLoader";
    
    public static final String IMA_DEFAULT_AD_LOADER = "IMA_DEFAULT_AD_LOADER";
    public static final String WATCH_TIME_BASE_AD_LOADER = "WATCH_TIME_BASE_AD_LOADER";
    public static final String OFFLINE_AD_LOADER = "OFFLINE-AD-LOADER";

    public static final String EVENT_ALL_ADS_REQUESTED = "allAdsRequested";
    public static final String EVENT_AD_REQUESTED = "adRequested";
    public static final String EVENT_AD_LOAD = "onAdLoad";


    public static final String EVENT_VIDEO_AD_PLAY_SUCCESS = "VideoAdPlaySuccess";
    public static final String EVENT_VIDEO_AD_PLAY_FAILED = "VideoAdPlayFailed";

    public static final String CREATIVE_ID = "creativeId";
    public static final String ADVERTISER = "advertiser";
    public static final String AD_URI = "uri";
    public static final String SESSION_ID = "s_id";
    public static final String REASON = "reason";
    public static final String UNKNOWN = "unknown";
    public static final String REQUEST_TIME = "requestTime";
    public static final String AD_GROUP_COUNT = "adGroupCount";
    public static final String AD_GROUP_INDEX = "adGroupIndex";
    public static final String AD_INDEX_IN_GROUP = "adIndexInGroup";
    public static final String LOAD_MEDIA_TIME = "loadMediaTime";
    public static final String TOTAL_COST_TIME = "totalCostTime";
    public static final String AD_INDEX_IN_AD_GROUP = "adIndexInAdGroup";

    public static VideoAdsTracker getNoOpTracker(){
        return new VideoAdsTracker("") {
            @Override
            public void trackEvent(@NonNull String eventName, @NonNull Map<String, String> params) {
                
            }
        };
    }
    

    public  Map<String, String> buildSuccessParams(long adLoadedTime, long startRequestTime, long startLoadMediaTime, int adGroupIndex, int adGroupCount) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(REQUEST_TIME, String.valueOf(adLoadedTime - startRequestTime));
        result.put(LOAD_MEDIA_TIME, String.valueOf(System.currentTimeMillis() - startLoadMediaTime));
        result.put(TOTAL_COST_TIME, String.valueOf(System.currentTimeMillis() - startRequestTime));
        result.put(AD_GROUP_INDEX, String.valueOf(adGroupIndex));
        result.put(AD_GROUP_COUNT, String.valueOf(adGroupCount));
        result.put(SESSION_ID, sessionId);
        return result;
    }

    public  Map<String, String> buildFailedParams(int adGroupIndex, long startRequestTime, Exception exception, int adGroupCount) {
        return buildFailedParams(adGroupIndex, -1, startRequestTime, exception, adGroupCount);
    }

    

    public  Map<String, String> buildFailedParams(int adGroupIndex, int adIndexInAdGroup, long startRequestTime, Exception exception, int adGroupCount) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        if (adGroupIndex >= 0) {
            result.put(AD_GROUP_INDEX, String.valueOf(adGroupIndex));
        }
        if (adIndexInAdGroup >= 0) {
            result.put(AD_INDEX_IN_AD_GROUP, String.valueOf(adIndexInAdGroup));
        }
        if (adGroupCount > 0) {
            result.put(AD_GROUP_COUNT, String.valueOf(adGroupCount));
        }
        result.put(REASON, exception == null ? UNKNOWN : exception.getMessage());
        result.put(TOTAL_COST_TIME, String.valueOf(System.currentTimeMillis() - startRequestTime));
        result.put(SESSION_ID, sessionId);
        return result;
    }

    private String sessionId;
    private String adLoaderName = IMA_DEFAULT_AD_LOADER;

    public VideoAdsTracker(String adLoaderName) {
        this.adLoaderName = adLoaderName;
    }

    public void onAdManagerRequested(){
        sessionId = UUID.randomUUID().toString();
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(REQUEST_TIME, String.valueOf(System.currentTimeMillis()));
        result.put(SESSION_ID, sessionId);
        trackEvent(EVENT_ALL_ADS_REQUESTED, result);
    }
    
    
    public void onAdLoad(int adGroupIndex, int adIndexInGroup, Uri adUri){
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(REQUEST_TIME, String.valueOf(System.currentTimeMillis()));
        result.put(SESSION_ID, sessionId);
        result.put(AD_GROUP_INDEX, String.valueOf(adGroupIndex));
        result.put(AD_INDEX_IN_GROUP, String.valueOf(adIndexInGroup));
        result.put(AD_URI, adUri.toString());
        trackEvent(EVENT_AD_LOAD, result);
    }

    public void onAdEvent(String name, @Nullable String creativeId, @Nullable String advertiser){
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        if (creativeId != null)
        result.put(CREATIVE_ID, creativeId);
        if (advertiser != null)
        result.put(ADVERTISER, advertiser);
        result.put(REQUEST_TIME, String.valueOf(System.currentTimeMillis()));
        result.put(SESSION_ID, sessionId);
        trackEvent(name, result);
    }

    
    public abstract void trackEvent(@NonNull String eventName, @NonNull Map<String, String> params);
}
