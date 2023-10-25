package com.mxplay.adloader;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

public class AdTagData {
    private final Uri adTag;
    private final boolean success;
    private final long responseTime;


    public AdTagData(Uri adTag, boolean success, long responseTime) {
        this.adTag = adTag;
        this.success = success;
        this.responseTime = responseTime;
    }

    public Uri getAdTag() {
        return adTag;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getResponseTime() {
        return responseTime;
    }
}