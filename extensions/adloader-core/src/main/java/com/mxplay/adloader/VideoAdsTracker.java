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
    public static final String PRE_ROLL_AD_LOADER = "PRE_ROLL_AD_LOADER";
    public static final String CATEGORY_DFP_ADS = "IMAVideoAds";


    public static final String EVENT_VMAP_REQUESTED = "vmapRequested";
    public static final String EVENT_VMAP_SUCCESS = "vmapSuccess";
    public static final String EVENT_VMAP_FAIL = "vmapFail";
    public static final String EVENT_VAST_REQUESTED = "vastRequested";
    public static final String EVENT_VAST_SUCCESS = "vastSuccess";
    public static final String EVENT_VAST_FAIL = "vastFail";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_AD_OPPORTUNITY = "AdOpportunity";
    public static final String EVENT_AD_SHOWN = "AdShown";



    public static final String CREATIVE_ID = "creativeId";
    public static final String ADVERTISER = "advertiser";
    public static final String AD_URI = "uri";
    public static final String SESSION_ID = "s_id";
    public static final String REASON = "reason";
    public static final String ERROR_CODE = "code";
    public static final String UNKNOWN = "unknown";
    public static final String REQUEST_TIME = "requestTime";
    public static final String AD_PODS_COUNT = "adPodsCount";
    public static final String AD_POD_INDEX = "adPodIndex";
    public static final String AD_INDEX_IN_POD = "adIndexInPod";

    public static final String AD_VAST_BITRATE = "adVastBitrate";
    public static final String AD_DURATION = "adDuration";
    public static final String LOAD_MEDIA_TIME = "loadMediaTime";
    public static final String TOTAL_COST_TIME = "totalCostTime";
    public static final String AD_INDEX_IN_AD_GROUP = "adIndexInAdGroup";
    public static final String START_TIME = "startTime";
    public static final String TIME_STAMP = "timeStamp";
    public static final String CATEGORY = "categoryName";

    public final static String AD_UNIT_ID = "adUnitId";
    public final static String AD_UNIT_NAME = "adUnitName";
    public final static String AD_PATH = "adPath";

    public static VideoAdsTracker getNoOpTracker(){
        return new VideoAdsTracker("") {
            @Override
            public boolean isVmapRequest() {
                return false;
            }

            @Override
            public void trackEvent(@NonNull String eventName, @NonNull Map<String, String> params) {
                
            }
        };
    }
    

    public  Map<String, String> buildSuccessParams(long adLoadedTime, long startRequestTime, long startLoadMediaTime, int adGroupIndex, int adGroupCount) {
        return buildSuccessParams(adLoadedTime, startRequestTime, startLoadMediaTime, adGroupIndex, -1, adGroupCount, null);
    }

    public  Map<String, String> buildSuccessParams(long adLoadedTime, long startRequestTime, long startLoadMediaTime, int adGroupIndex, int adIndexInAdGroup, int adGroupCount, Uri adUri) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(REQUEST_TIME, String.valueOf(adLoadedTime - startRequestTime));
        result.put(LOAD_MEDIA_TIME, String.valueOf(System.currentTimeMillis() - startLoadMediaTime));
        result.put(TOTAL_COST_TIME, String.valueOf(System.currentTimeMillis() - startRequestTime));
        result.put(AD_POD_INDEX, String.valueOf(adGroupIndex));
        result.put(AD_INDEX_IN_POD, String.valueOf(adIndexInAdGroup));
        result.put(AD_PODS_COUNT, String.valueOf(adGroupCount));
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        result.put(SESSION_ID, sessionId);
        if (adUri != null) {
            result.put(AD_URI, adUri.toString());
        }
        return result;
    }

    public  Map<String, String> buildFailedParams(int adGroupIndex, long startRequestTime, Exception exception, int adGroupCount) {
        return buildFailedParams(adGroupIndex, -1, startRequestTime, exception, adGroupCount);
    }

    public  Map<String, String> buildFailedParams(int adGroupIndex, int adIndexInAdGroup, long startRequestTime, Exception exception, int adGroupCount) {
        return buildFailedParams(adGroupIndex, adIndexInAdGroup, startRequestTime, exception, adGroupCount, null);
    }

    public  Map<String, String> buildFailedParams(int adGroupIndex, int adIndexInAdGroup, long startRequestTime, Exception exception, int adGroupCount,  Uri adUri) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        if (adGroupIndex >= 0) {
            result.put(AD_POD_INDEX, String.valueOf(adGroupIndex));
        }
        if (adIndexInAdGroup >= 0) {
            result.put(AD_INDEX_IN_AD_GROUP, String.valueOf(adIndexInAdGroup));
        }
        if (adGroupCount > 0) {
            result.put(AD_PODS_COUNT, String.valueOf(adGroupCount));
        }
        if (adUri != null) {
            result.put(AD_URI, adUri.toString());
        }
        result.put(REASON, exception == null ? UNKNOWN : exception.getMessage());
        result.put(TOTAL_COST_TIME, String.valueOf(System.currentTimeMillis() - startRequestTime));
        result.put(SESSION_ID, sessionId);
        result.put(START_TIME,String.valueOf(startTime));
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        return result;
    }

    public Map<String, String> buildEventParams(@Nullable String creativeId, @Nullable String advertiser, int adPodIndex, int adIndexInPod, double vastDuration,int vastBitrate) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        if (creativeId != null)
            result.put(CREATIVE_ID, creativeId);
        if (advertiser != null)
            result.put(ADVERTISER, advertiser);
        if(vastBitrate != 0)
            result.put(AD_VAST_BITRATE,String.valueOf(vastBitrate));
        if(vastDuration != 0)
            result.put(AD_DURATION,String.valueOf(vastDuration));
        result.put(SESSION_ID, sessionId);
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        result.put(AD_POD_INDEX, String.valueOf(adPodIndex));
        result.put(AD_INDEX_IN_POD, String.valueOf(adIndexInPod));
        return result;
    }

    public Map<String, String> buildErrorParams(int errorCode, @Nullable Exception exception, int adPodIndex, int adIndexInPod) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(SESSION_ID, sessionId);
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        if (adPodIndex >= 0)
            result.put(AD_POD_INDEX, String.valueOf(adPodIndex));
        if (adIndexInPod >= 0)
            result.put(AD_INDEX_IN_POD, String.valueOf(adIndexInPod));
        result.put(ERROR_CODE, String.valueOf(errorCode));
        result.put(REASON, exception == null ? UNKNOWN : exception.getMessage());
        return result;
    }

    protected String sessionId;
    protected String adLoaderName = IMA_DEFAULT_AD_LOADER;
    private final long startTime;

    public VideoAdsTracker(String adLoaderName) {
        this.adLoaderName = adLoaderName;
        this.startTime = System.currentTimeMillis();
    }

    public long getStartTime() {
        return startTime;
    }

    public void onAdManagerRequested(){
        onAdManagerRequested(null);
    }

    public void onAdManagerRequested(Map<String,String> extraParams){
        sessionId = UUID.randomUUID().toString();
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(SESSION_ID, sessionId);
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        if(extraParams != null && !extraParams.isEmpty()){
            result.putAll(extraParams);
        }
        trackEvent(EVENT_VMAP_REQUESTED, result);
    }

    public void onAdsManagerLoaded(int podsCount) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(SESSION_ID, sessionId);
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        result.put(AD_PODS_COUNT, String.valueOf(podsCount));
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        trackEvent(EVENT_VMAP_SUCCESS, result);
    }

    public void onAdsManagerRequestFailed(int errorCode, @Nullable Exception exception){
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(SESSION_ID, sessionId);
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        result.put(ERROR_CODE, String.valueOf(errorCode));
        result.put(REASON, exception == null ? UNKNOWN : exception.getMessage());
        trackEvent(EVENT_VMAP_FAIL, result);
    }
    
    public void onVastSuccess(int adPodIndex, int adIndexInPod){
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(SESSION_ID, sessionId);
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        result.put(AD_POD_INDEX, String.valueOf(adPodIndex));
        result.put(AD_INDEX_IN_POD, String.valueOf(adIndexInPod));
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        trackEvent(EVENT_VAST_SUCCESS, result);
    }

    public void onAdEvent(String name, @Nullable String creativeId, @Nullable String advertiser){
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        if (creativeId != null)
        result.put(CREATIVE_ID, creativeId);
        if (advertiser != null)
        result.put(ADVERTISER, advertiser);
        result.put(SESSION_ID, sessionId);
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        trackEvent(name, result);
    }

    public void onVastRequested(int adPodIndex) {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(SESSION_ID, sessionId);
        result.put(TIME_STAMP,String.valueOf(System.currentTimeMillis()));
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        result.put(AD_POD_INDEX, String.valueOf(adPodIndex));
        trackEvent(EVENT_VAST_REQUESTED, result);
    }
    public void sendAdOpportunity() {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(START_TIME, String.valueOf(startTime));
        result.put(TIME_STAMP, String.valueOf(System.currentTimeMillis()));
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        trackEvent(EVENT_AD_OPPORTUNITY, result);
    }

    public void adShown() {
        Map<String, String> result = new HashMap<>();
        result.put(AD_LOADER_NAME, adLoaderName);
        result.put(START_TIME, String.valueOf(startTime));
        result.put(TIME_STAMP, String.valueOf(System.currentTimeMillis()));
        result.put(CATEGORY, CATEGORY_DFP_ADS);
        trackEvent(EVENT_AD_SHOWN, result);
    }

    public abstract void trackEvent(@NonNull String eventName, @NonNull Map<String, String> params);

    public abstract boolean isVmapRequest();
}
