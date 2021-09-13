package com.mxplay.adloader;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

public class AdTagData {
    private final Uri adTag;
    private final boolean pubmaticSuccess;
    private final long pubmaticResponseTime;


    public AdTagData(Uri adTag, boolean pubmaticSuccess, long pubmaticResponseTime) {
        this.adTag = adTag;
        this.pubmaticSuccess = pubmaticSuccess;
        this.pubmaticResponseTime = pubmaticResponseTime;
    }

    public Uri getAdTag() {
        return adTag;
    }

    public boolean isPubmaticSuccess() {
        return pubmaticSuccess;
    }

    public long getPubmaticResponseTime() {
        return pubmaticResponseTime;
    }

    public Map<String, String> toParams() {
        HashMap<String,String> hashMap = new HashMap<>();
        hashMap.put("pubmaticResponseTime",String.valueOf(pubmaticResponseTime));
        hashMap.put("pubmaticSuccess",String.valueOf(pubmaticSuccess));
        return hashMap;
    }
}
